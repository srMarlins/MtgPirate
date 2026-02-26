@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package state

import database.CatalogStore
import database.Database
import database.ImportsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import match.Matcher
import match.MultiCatalogMatcher
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.DeckEntryMatch
import model.LogEntry
import model.MatchStatus
import model.MultiMatch
import model.Preferences
import model.SavedImport
import model.Seller
import model.ShoppingPlan
import optimizer.ShoppingOptimizer
import kotlin.time.Clock

/**
 * MVI (Model-View-Intent) ViewModel implementation.
 *
 * This viewmodel follows the unidirectional data flow pattern where:
 * 1. UI sends Intents to the ViewModel
 * 2. ViewModel processes Intents and updates the Database (source of truth)
 * 3. Database changes flow reactively back to the UI via ViewState
 * 4. Side effects are emitted separately via ViewEffect
 *
 * The Database is the single source of truth - all state derives from database Flows.
 *
 * Business logic is delegated to focused use-case classes:
 * - [CatalogUseCase]  - catalog loading, caching, image enrichment
 * - [MatchingUseCase]  - deck parsing and card matching
 * - [ImportExportUseCase] - save/load/delete imports and CSV export
 * - [PreferencesUseCase]  - preference management
 */
class MviViewModel(
    private val scope: CoroutineScope,
    private val database: Database,
    private val catalogStore: CatalogStore,
    private val importsStore: ImportsStore,
    private val platformServices: MviPlatformServices
) {
    // -- Use cases ----------------------------------------------------------
    private val catalogUseCase = CatalogUseCase(catalogStore, platformServices)
    private val matchingUseCase = MatchingUseCase()
    private val importExportUseCase = ImportExportUseCase(importsStore, platformServices)
    private val preferencesUseCase = PreferencesUseCase(platformServices)

    // -- State --------------------------------------------------------------
    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _viewEffects = MutableSharedFlow<ViewEffect>()
    val viewEffects: SharedFlow<ViewEffect> = _viewEffects.asSharedFlow()

    // Local transient UI state (not from database)
    private val _localState = MutableStateFlow(LocalUiState())

    init {
        // Subscribe to database flows and combine into ViewState
        scope.launch(Dispatchers.IO) {
            // Create a flow that only refreshes matches when catalog or matches change
            val refreshedMatchesFlow = combine(
                database.observeCatalog().distinctUntilChanged(),
                _localState.map { it.matches }.distinctUntilChanged()
            ) { catalog, matches ->
                catalogUseCase.refreshMatchesFromCatalog(matches, catalog)
            }

            combine(
                database.observeCatalog(),
                database.observePreferences().map { it ?: Preferences() },
                database.observeSavedImports(),
                _localState,
                refreshedMatchesFlow
            ) { catalog, preferences, savedImports, localState, refreshedMatches ->
                ViewState(
                    catalog = if (catalog.variants.isEmpty()) null else catalog,
                    preferences = preferences,
                    savedImports = savedImports,
                    deckText = localState.deckText,
                    deckEntries = localState.deckEntries,
                    matches = refreshedMatches,
                    includeSideboard = preferences.includeSideboard,
                    includeCommanders = preferences.includeCommanders,
                    includeTokens = preferences.includeTokens,
                    loadingCatalog = localState.loadingCatalog,
                    catalogError = localState.catalogError,
                    showCandidatesFor = localState.showCandidatesFor,
                    showPreferences = localState.showPreferences,
                    showCatalogWindow = localState.showCatalogWindow,
                    showMatchesWindow = localState.showMatchesWindow,
                    showResultsWindow = localState.showResultsWindow,
                    showSavedImportsWindow = localState.showSavedImportsWindow,
                    wizardCompletedSteps = localState.wizardCompletedSteps,
                    isDarkTheme = localState.isDarkTheme,
                    isMatching = localState.isMatching,
                    matchedCount = localState.matchedCount,
                    unmatchedCount = localState.unmatchedCount,
                    ambiguousCount = localState.ambiguousCount,
                    totalPriceCents = localState.totalPriceCents,
                    multiMatches = localState.multiMatches,
                    shoppingPlan = localState.shoppingPlan,
                    availableSellers = localState.availableSellers,
                    loadingMultiCatalogs = localState.loadingMultiCatalogs
                )
            }.onEach { newState ->
                _viewState.update { newState }
            }.launchIn(scope)
        }
    }

    /**
     * Process user intents.
     * Intents trigger state changes and side effects.
     */
    fun processIntent(intent: ViewIntent) {
        when (intent) {
            is ViewIntent.Init -> init()
            is ViewIntent.UpdateDeckText -> updateDeckText(intent.text)
            is ViewIntent.ToggleIncludeSideboard -> toggleIncludeSideboard(intent.value)
            is ViewIntent.ToggleIncludeCommanders -> toggleIncludeCommanders(intent.value)
            is ViewIntent.ToggleIncludeTokens -> toggleIncludeTokens(intent.value)
            is ViewIntent.LoadCatalog -> loadCatalog()
            ViewIntent.ParseDeck -> parseDeck()
            ViewIntent.RunMatch -> runMatch()
            ViewIntent.ParseAndMatch -> parseAndMatch()
            is ViewIntent.OpenResolve -> openResolve(intent.index)
            ViewIntent.CloseResolve -> closeResolve()
            is ViewIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
            ViewIntent.ExportCsv -> exportCsv()
            ViewIntent.ExportWizardResults -> exportWizardResults()
            is ViewIntent.SetShowPreferences -> setShowPreferences(intent.show)
            is ViewIntent.SetShowCatalogWindow -> setShowCatalogWindow(intent.show)
            is ViewIntent.SetShowMatchesWindow -> setShowMatchesWindow(intent.show)
            is ViewIntent.SetShowResultsWindow -> setShowResultsWindow(intent.show)
            is ViewIntent.SavePreferences -> savePreferences(
                intent.variantPriority,
                intent.setPriority,
                intent.fuzzyEnabled
            )
            is ViewIntent.Log -> log(intent.message, intent.level)
            is ViewIntent.UpdateVariantPriority -> updateVariantPriority(intent.value)
            is ViewIntent.CompleteWizardStep -> completeWizardStep(intent.step)
            ViewIntent.ToggleTheme -> toggleTheme()
            is ViewIntent.SetShowSavedImportsWindow -> setShowSavedImportsWindow(intent.show)
            is ViewIntent.SaveCurrentImport -> saveCurrentImport()
            is ViewIntent.LoadSavedImport -> loadSavedImport(intent.importId)
            is ViewIntent.DeleteSavedImport -> deleteSavedImport(intent.importId)
            is ViewIntent.EnrichVariantWithImage -> enrichVariantWithImage(intent.variant)
            ViewIntent.LoadAllCatalogs -> loadAllCatalogs()
            ViewIntent.RunMultiMatch -> runMultiMatch()
            ViewIntent.OptimizeShoppingPlan -> optimizeShoppingPlan()
            is ViewIntent.OverrideCardSeller -> overrideCardSeller(intent.matchIndex, intent.seller)
        }
    }

    // -----------------------------------------------------------------------
    // Intent handlers — thin wrappers that delegate to use cases
    // -----------------------------------------------------------------------

    private fun init() {
        scope.launch(Dispatchers.IO) {
            log("Initializing MVI ViewModel...", "INFO")
            try {
                val variantCount = catalogUseCase.getVariantCount()
                if (variantCount == 0L) {
                    log("Catalog is empty, loading from remote...", "INFO")
                    loadCatalog()
                } else {
                    log("Catalog already loaded: $variantCount variants", "INFO")
                }
            } catch (e: Exception) {
                log("Failed to check catalog: ${e.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to initialize catalog"))
            }
        }
    }

    private fun updateDeckText(text: String) {
        _localState.update { it.copy(deckText = text) }
    }

    private fun toggleIncludeSideboard(value: Boolean) {
        scope.launch {
            preferencesUseCase.setIncludeSideboard(value).onFailure {
                log("Failed to save sideboard preference: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to save sideboard preference"))
            }.onSuccess {
                log("Include sideboard: $value", "INFO")
            }
        }
    }

    private fun toggleIncludeCommanders(value: Boolean) {
        scope.launch {
            preferencesUseCase.setIncludeCommanders(value).onFailure {
                log("Failed to save commanders preference: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to save commanders preference"))
            }.onSuccess {
                log("Include commanders: $value", "INFO")
            }
        }
    }

    private fun toggleIncludeTokens(value: Boolean) {
        scope.launch {
            preferencesUseCase.setIncludeTokens(value).onFailure {
                log("Failed to save tokens preference: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to save tokens preference"))
            }.onSuccess {
                log("Include tokens: $value", "INFO")
            }
        }
    }

    private fun loadCatalog() {
        scope.launch(Dispatchers.IO) {
            _localState.update { it.copy(loadingCatalog = true, catalogError = null) }
            try {
                val result = catalogUseCase.loadCatalog { msg, level -> log(msg, level) }
                result.onFailure { e ->
                    _localState.update { it.copy(catalogError = e.message) }
                    _viewEffects.emit(ViewEffect.ShowError("Failed to load catalog"))
                }
            } finally {
                _localState.update { it.copy(loadingCatalog = false) }
            }
        }
    }

    private fun parseDeck() {
        scope.launch {
            val state = _localState.value
            val preferences = _viewState.value.preferences

            val entries = matchingUseCase.parseDeck(
                state.deckText,
                preferences.includeSideboard,
                preferences.includeCommanders
            )

            logParsedEntries(entries)
            _localState.update { it.copy(deckEntries = entries, matches = emptyList()) }
        }
    }

    private fun runMatch() {
        scope.launch {
            val state = _localState.value
            val catalog = _viewState.value.catalog

            if (catalog == null) {
                log("No catalog available for matching", "ERROR")
                return@launch
            }
            if (state.deckEntries.isEmpty()) {
                log("No deck entries to match", "WARNING")
                return@launch
            }

            runMatchInternal(state.deckEntries, catalog)
        }
    }

    private suspend fun runMatchInternal(entries: List<DeckEntry>, catalog: Catalog) {
        _localState.update { it.copy(isMatching = true) }
        val preferences = _viewState.value.preferences

        val matches = matchingUseCase.matchEntries(
            entries,
            catalog,
            Matcher.MatchConfig(
                preferences.variantPriority,
                preferences.setPriority,
                preferences.fuzzyEnabled
            )
        )

        val counts = matchingUseCase.calculateMatchCounts(matches)

        _localState.update {
            it.copy(
                matches = matches,
                showResultsWindow = true,
                isMatching = false,
                matchedCount = counts.matched,
                unmatchedCount = counts.unmatched,
                ambiguousCount = counts.ambiguous,
                totalPriceCents = counts.totalPrice
            )
        }
        log("Matched ${matches.size} entries", "INFO")
    }

    private fun parseAndMatch() {
        scope.launch {
            val state = _localState.value
            val preferences = _viewState.value.preferences
            val catalog = _viewState.value.catalog

            if (catalog == null) {
                log("No catalog available for matching", "ERROR")
                return@launch
            }

            val entries = matchingUseCase.parseDeck(
                state.deckText,
                preferences.includeSideboard,
                preferences.includeCommanders
            )

            logParsedEntries(entries)
            _localState.update { it.copy(deckEntries = entries) }
            runMatchInternal(entries, catalog)
        }
    }

    private fun openResolve(index: Int) {
        _localState.update { it.copy(showCandidatesFor = index) }
    }

    private fun closeResolve() {
        _localState.update { it.copy(showCandidatesFor = null) }
    }

    private fun resolveCandidate(index: Int, variant: CardVariant) {
        scope.launch {
            _localState.update { state ->
                if (index !in state.matches.indices) return@update state

                val match = state.matches[index]
                val updated = match.copy(
                    status = MatchStatus.MANUAL_SELECTED,
                    selectedVariant = variant
                )
                val newMatches = state.matches.toMutableList()
                newMatches[index] = updated
                state.copy(matches = newMatches, showCandidatesFor = null)
            }

            val counts = matchingUseCase.calculateMatchCounts(_localState.value.matches)
            _localState.update {
                it.copy(
                    matchedCount = counts.matched,
                    unmatchedCount = counts.unmatched,
                    ambiguousCount = counts.ambiguous,
                    totalPriceCents = counts.totalPrice
                )
            }
            log("Resolved card variant at index $index", "INFO")
        }
    }

    private fun exportCsv() {
        scope.launch {
            val matches = _localState.value.matches
            if (matches.isNotEmpty()) {
                importExportUseCase.exportCsv(matches) { path ->
                    log("CSV exported to: $path", "INFO")
                    scope.launch {
                        _viewEffects.emit(ViewEffect.ShowMessage("CSV exported to: $path"))
                    }
                }
            } else {
                log("No matches to export", "WARNING")
            }
        }
    }

    private fun exportWizardResults() {
        scope.launch {
            val matches = _localState.value.matches
            if (matches.isNotEmpty()) {
                importExportUseCase.exportWizardResults(matches) { foundPath, unfoundPath ->
                    foundPath?.let { log("Found cards exported to: $it", "INFO") }
                    unfoundPath?.let { log("Unfound cards exported to: $it", "INFO") }
                }
            } else {
                log("No matches to export", "WARNING")
            }
        }
    }

    private fun setShowPreferences(show: Boolean) {
        _localState.update { it.copy(showPreferences = show) }
    }

    private fun setShowCatalogWindow(show: Boolean) {
        _localState.update { it.copy(showCatalogWindow = show) }
    }

    private fun setShowMatchesWindow(show: Boolean) {
        _localState.update { it.copy(showMatchesWindow = show) }
    }

    private fun setShowResultsWindow(show: Boolean) {
        _localState.update { it.copy(showResultsWindow = show) }
    }

    private fun savePreferences(variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean) {
        scope.launch {
            preferencesUseCase.savePreferences(variantPriority, setPriority, fuzzyEnabled)
                .onSuccess {
                    log("Preferences saved", "INFO")
                    _localState.update { it.copy(showPreferences = false) }
                }
                .onFailure {
                    log("Failed to save preferences: ${it.message}", "ERROR")
                    _viewEffects.emit(ViewEffect.ShowError("Failed to save preferences"))
                }
        }
    }

    private fun updateVariantPriority(newPriority: List<String>) {
        scope.launch {
            preferencesUseCase.updateVariantPriority(newPriority)
                .onSuccess { log("Variant priority updated", "INFO") }
                .onFailure {
                    log("Failed to update variant priority: ${it.message}", "ERROR")
                    _viewEffects.emit(ViewEffect.ShowError("Failed to update variant priority"))
                }
        }
    }

    private fun completeWizardStep(step: Int) {
        _localState.update { state ->
            val completed = state.wizardCompletedSteps.toMutableSet()
            completed.add(step)
            state.copy(wizardCompletedSteps = completed)
        }
    }

    private fun toggleTheme() {
        _localState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun setShowSavedImportsWindow(show: Boolean) {
        _localState.update { it.copy(showSavedImportsWindow = show) }
    }

    private fun saveCurrentImport() {
        scope.launch {
            val state = _localState.value
            val preferences = _viewState.value.preferences
            val savedImports = _viewState.value.savedImports

            importExportUseCase.saveCurrentImport(
                state.deckText, state.deckEntries, preferences, savedImports
            ).onSuccess { msg -> log(msg, "INFO") }
                .onFailure {
                    log("Failed to save import: ${it.message}", "ERROR")
                    _viewEffects.emit(ViewEffect.ShowError("Failed to save import"))
                }
        }
    }

    private fun loadSavedImport(importId: String) {
        scope.launch {
            importExportUseCase.loadSavedImport(importId, _viewState.value.savedImports)
                .onSuccess { import ->
                    _localState.update {
                        it.copy(
                            deckText = import.deckText,
                            showSavedImportsWindow = false,
                            showResultsWindow = true
                        )
                    }
                    log("Loaded import: ${import.name}", "INFO")
                }
                .onFailure {
                    log("Failed to load import: ${it.message}", "ERROR")
                    _viewEffects.emit(ViewEffect.ShowError("Failed to load saved import"))
                }
        }
    }

    private fun deleteSavedImport(importId: String) {
        scope.launch {
            importExportUseCase.deleteSavedImport(importId)
                .onSuccess { log("Import deleted", "INFO") }
                .onFailure {
                    log("Failed to delete import: ${it.message}", "ERROR")
                    _viewEffects.emit(ViewEffect.ShowError("Failed to delete import"))
                }
        }
    }

    private fun enrichVariantWithImage(variant: CardVariant) {
        if (variant.imageUrl != null) return
        scope.launch {
            catalogUseCase.enrichVariantWithImage(variant) { msg, level -> log(msg, level) }
        }
    }

    // -----------------------------------------------------------------------
    // Multi-catalog intent handlers
    // -----------------------------------------------------------------------

    private fun loadAllCatalogs() {
        scope.launch(Dispatchers.IO) {
            _localState.update { it.copy(loadingMultiCatalogs = true) }
            try {
                val loadedSellers = catalogUseCase.loadAllCatalogs { msg, level -> log(msg, level) }
                _localState.update { it.copy(availableSellers = loadedSellers) }
                // Auto-trigger multi-match after catalogs load
                if (loadedSellers.isNotEmpty()) {
                    runMultiMatch()
                }
            } catch (e: Exception) {
                log("Failed to load multi-catalogs: ${e.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to load catalogs from sellers"))
            } finally {
                _localState.update { it.copy(loadingMultiCatalogs = false) }
            }
        }
    }

    private fun runMultiMatch() {
        scope.launch(Dispatchers.IO) {
            val state = _localState.value
            val catalog = _viewState.value.catalog
            val preferences = _viewState.value.preferences

            if (catalog == null || catalog.variants.isEmpty()) {
                log("No catalog available for multi-matching", "ERROR")
                return@launch
            }
            if (state.deckEntries.isEmpty()) {
                log("No deck entries to multi-match", "WARNING")
                return@launch
            }

            _localState.update { it.copy(isMatching = true) }
            try {
                // Build per-seller catalogs from all variants in the database
                val perSellerCatalogs = catalog.variants
                    .groupBy { it.seller }
                    .mapValues { (_, variants) -> Catalog(variants) }

                val config = MultiCatalogMatcher.Config(
                    variantPriority = preferences.variantPriority,
                    setPriority = preferences.setPriority,
                    fuzzyEnabled = preferences.fuzzyEnabled,
                )

                val multiMatches = matchingUseCase.matchEntriesMulti(
                    state.deckEntries,
                    perSellerCatalogs,
                    config
                )

                _localState.update {
                    it.copy(
                        multiMatches = multiMatches,
                        isMatching = false,
                        showResultsWindow = true,
                    )
                }
                log("Multi-matched ${multiMatches.size} entries across ${perSellerCatalogs.size} seller(s)", "INFO")
            } catch (e: Exception) {
                log("Multi-match failed: ${e.message}", "ERROR")
                _localState.update { it.copy(isMatching = false) }
                _viewEffects.emit(ViewEffect.ShowError("Multi-catalog matching failed"))
            }
        }
    }

    private fun optimizeShoppingPlan() {
        scope.launch(Dispatchers.IO) {
            val multiMatches = _localState.value.multiMatches
            if (multiMatches.isEmpty()) {
                log("No multi-matches available for optimization", "WARNING")
                return@launch
            }

            try {
                val plan = ShoppingOptimizer.optimize(multiMatches)
                _localState.update { it.copy(shoppingPlan = plan) }
                log(
                    "Shopping plan optimized: ${plan.orders.size} seller(s), " +
                        "total ${plan.totalPriceCents} cents, savings ${plan.savingsVsSingleSeller} cents",
                    "INFO"
                )
            } catch (e: Exception) {
                log("Shopping plan optimization failed: ${e.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to optimize shopping plan"))
            }
        }
    }

    private fun overrideCardSeller(matchIndex: Int, seller: Seller) {
        scope.launch {
            _localState.update { state ->
                if (matchIndex !in state.multiMatches.indices) return@update state

                val match = state.multiMatches[matchIndex]
                val newBest = match.alternatives.firstOrNull { it.seller == seller }
                    ?: return@update state

                val updated = match.copy(bestOption = newBest)
                val newMatches = state.multiMatches.toMutableList()
                newMatches[matchIndex] = updated
                state.copy(multiMatches = newMatches)
            }
            log("Overrode seller for match at index $matchIndex to ${seller.displayName}", "INFO")
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun log(message: String, level: String = "INFO") {
        scope.launch {
            platformServices.addLog(LogEntry(level, message, Clock.System.now().toString()))
        }
    }

    private fun logParsedEntries(entries: List<DeckEntry>) {
        entries.forEach { e ->
            if (e.setCodeHint != null) {
                val collectorSuffix = e.collectorNumberHint?.let { " #$it" } ?: ""
                log("Parsed: ${e.qty} ${e.cardName} (${e.setCodeHint}$collectorSuffix)", "DEBUG")
            } else {
                log("Parsed: ${e.qty} ${e.cardName}", "DEBUG")
            }
        }
    }
}

/**
 * Immutable view state derived from database and local UI state.
 */
data class ViewState(
    val catalog: Catalog? = null,
    val preferences: Preferences = Preferences(),
    val savedImports: List<SavedImport> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val deckText: String = "",
    val deckEntries: List<DeckEntry> = emptyList(),
    val matches: List<DeckEntryMatch> = emptyList(),
    val includeSideboard: Boolean = true,
    val includeCommanders: Boolean = true,
    val includeTokens: Boolean = true,
    val loadingCatalog: Boolean = false,
    val catalogError: String? = null,
    val showCandidatesFor: Int? = null,
    val showPreferences: Boolean = false,
    val showCatalogWindow: Boolean = false,
    val showMatchesWindow: Boolean = false,
    val showResultsWindow: Boolean = false,
    val showSavedImportsWindow: Boolean = false,
    val wizardCompletedSteps: Set<Int> = emptySet(),
    val isDarkTheme: Boolean = true,
    val isMatching: Boolean = false,
    // Pre-calculated counts for performance (calculated on background threads)
    val matchedCount: Int = 0,
    val unmatchedCount: Int = 0,
    val ambiguousCount: Int = 0,
    val totalPriceCents: Int = 0,
    // Multi-catalog matching and shopping optimization
    val multiMatches: List<MultiMatch> = emptyList(),
    val shoppingPlan: ShoppingPlan? = null,
    val availableSellers: List<Seller> = emptyList(),
    val loadingMultiCatalogs: Boolean = false
)

/**
 * Local transient UI state that doesn't belong in the database.
 */
private data class LocalUiState(
    val deckText: String = "",
    val deckEntries: List<DeckEntry> = emptyList(),
    val matches: List<DeckEntryMatch> = emptyList(),
    val loadingCatalog: Boolean = false,
    val catalogError: String? = null,
    val showCandidatesFor: Int? = null,
    val showPreferences: Boolean = false,
    val showCatalogWindow: Boolean = false,
    val showMatchesWindow: Boolean = false,
    val showResultsWindow: Boolean = false,
    val showSavedImportsWindow: Boolean = false,
    val wizardCompletedSteps: Set<Int> = emptySet(),
    val isDarkTheme: Boolean = true,
    val isMatching: Boolean = false,
    // Pre-calculated counts for performance (calculated on background threads)
    val matchedCount: Int = 0,
    val unmatchedCount: Int = 0,
    val ambiguousCount: Int = 0,
    val totalPriceCents: Int = 0,
    // Multi-catalog matching and shopping optimization
    val multiMatches: List<MultiMatch> = emptyList(),
    val shoppingPlan: ShoppingPlan? = null,
    val availableSellers: List<Seller> = emptyList(),
    val loadingMultiCatalogs: Boolean = false
)

/**
 * User intents - actions that trigger state changes.
 */
sealed class ViewIntent {
    data object Init : ViewIntent()
    data class UpdateDeckText(val text: String) : ViewIntent()
    data class ToggleIncludeSideboard(val value: Boolean) : ViewIntent()
    data class ToggleIncludeCommanders(val value: Boolean) : ViewIntent()
    data class ToggleIncludeTokens(val value: Boolean) : ViewIntent()
    data object LoadCatalog : ViewIntent()
    data object ParseDeck : ViewIntent()
    data object RunMatch : ViewIntent()
    data object ParseAndMatch : ViewIntent()
    data class OpenResolve(val index: Int) : ViewIntent()
    data object CloseResolve : ViewIntent()
    data class ResolveCandidate(val index: Int, val variant: CardVariant) : ViewIntent()
    data object ExportCsv : ViewIntent()
    data object ExportWizardResults : ViewIntent()
    data class SetShowPreferences(val show: Boolean) : ViewIntent()
    data class SetShowCatalogWindow(val show: Boolean) : ViewIntent()
    data class SetShowMatchesWindow(val show: Boolean) : ViewIntent()
    data class SetShowResultsWindow(val show: Boolean) : ViewIntent()
    data class SavePreferences(
        val variantPriority: List<String>,
        val setPriority: List<String>,
        val fuzzyEnabled: Boolean
    ) : ViewIntent()

    data class Log(val message: String, val level: String = "INFO") : ViewIntent()
    data class UpdateVariantPriority(val value: List<String>) : ViewIntent()
    data class CompleteWizardStep(val step: Int) : ViewIntent()
    data object ToggleTheme : ViewIntent()
    data class SetShowSavedImportsWindow(val show: Boolean) : ViewIntent()
    data object SaveCurrentImport : ViewIntent()
    data class LoadSavedImport(val importId: String) : ViewIntent()
    data class DeleteSavedImport(val importId: String) : ViewIntent()
    data class EnrichVariantWithImage(val variant: CardVariant) : ViewIntent()

    // Multi-catalog and shopping optimization intents
    data object LoadAllCatalogs : ViewIntent()
    data object RunMultiMatch : ViewIntent()
    data object OptimizeShoppingPlan : ViewIntent()
    data class OverrideCardSeller(val matchIndex: Int, val seller: Seller) : ViewIntent()
}

/**
 * One-time side effects (navigation, toasts, etc.)
 */
sealed class ViewEffect {
    data class ShowMessage(val message: String) : ViewEffect()
    data class ShowError(val message: String) : ViewEffect()
}

/**
 * Platform-specific services for Mvi ViewModel.
 */
interface MviPlatformServices {
    suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog?
    suspend fun updatePreferences(update: (Preferences) -> Preferences)
    suspend fun addLog(log: LogEntry)
    suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit)
    suspend fun exportWizardResults(
        matches: List<DeckEntryMatch>,
        onComplete: (foundPath: String?, unfoundPath: String?) -> Unit
    )

    /**
     * Copy text to clipboard. Used for mobile platforms where file opening is not supported.
     */
    suspend fun copyToClipboard(text: String)

    /**
     * Open a URL in the platform's default browser.
     */
    suspend fun openUrl(url: String)
}
