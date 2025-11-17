@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
package state

import database.CatalogStore
import database.Database
import database.ImportsStore
import deck.DecklistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import match.Matcher
import model.*
import util.Logging
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
 */
class MviViewModel(
    private val scope: CoroutineScope,
    private val database: Database,
    private val catalogStore: CatalogStore,
    private val importsStore: ImportsStore,
    private val platformServices: MviPlatformServices
) {
    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _viewEffects = MutableSharedFlow<ViewEffect>()
    val viewEffects: SharedFlow<ViewEffect> = _viewEffects.asSharedFlow()

    // Local transient UI state (not from database)
    private val _localState = MutableStateFlow(LocalUiState())

    init {
        // Subscribe to database flows and combine into ViewState
        scope.launch {
            combine(
                database.observeCatalog(),
                database.observePreferences().map { it ?: Preferences() },
                database.observeSavedImports(),
                database.observeLogs(),
                _localState
            ) { flows: Array<Any> ->
                val catalog = flows[0] as Catalog
                val preferences = flows[1] as Preferences
                val savedImports = flows[2] as List<*>
                val logs = flows[3] as List<*>
                val localState = flows[4] as LocalUiState
                
                ViewState(
                    catalog = if (catalog.variants.isEmpty()) null else catalog,
                    preferences = preferences,
                    savedImports = savedImports.filterIsInstance<SavedImport>(),
                    logs = logs.filterIsInstance<LogEntry>(),
                    deckText = localState.deckText,
                    deckEntries = localState.deckEntries,
                    matches = localState.matches,
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
                    isDarkTheme = localState.isDarkTheme
                )
            }.onEach { newState ->
                _viewState.value = newState
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
            is ViewIntent.LoadCatalog -> loadCatalog(intent.force)
            ViewIntent.ParseDeck -> parseDeck()
            ViewIntent.RunMatch -> runMatch()
            ViewIntent.ParseAndMatch -> parseAndMatch()
            is ViewIntent.OpenResolve -> openResolve(intent.index)
            ViewIntent.CloseResolve -> closeResolve()
            is ViewIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
            ViewIntent.ExportCsv -> exportCsv()
            is ViewIntent.SetShowPreferences -> setShowPreferences(intent.show)
            is ViewIntent.SetShowCatalogWindow -> setShowCatalogWindow(intent.show)
            is ViewIntent.SetShowMatchesWindow -> setShowMatchesWindow(intent.show)
            is ViewIntent.SetShowResultsWindow -> setShowResultsWindow(intent.show)
            is ViewIntent.SavePreferences -> savePreferences(intent.variantPriority, intent.setPriority, intent.fuzzyEnabled)
            is ViewIntent.Log -> log(intent.message, intent.level)
            is ViewIntent.UpdateVariantPriority -> updateVariantPriority(intent.value)
            is ViewIntent.CompleteWizardStep -> completeWizardStep(intent.step)
            ViewIntent.ToggleTheme -> toggleTheme()
            is ViewIntent.SetShowSavedImportsWindow -> setShowSavedImportsWindow(intent.show)
            ViewIntent.LoadSavedImports -> loadSavedImports()
            is ViewIntent.SaveCurrentImport -> saveCurrentImport(intent.name)
            is ViewIntent.LoadSavedImport -> loadSavedImport(intent.importId)
            is ViewIntent.DeleteSavedImport -> deleteSavedImport(intent.importId)
            is ViewIntent.EnrichVariantWithImage -> enrichVariantWithImage(intent.variant)
        }
    }

    // Intent handlers

    private fun init() {
        scope.launch {
            log("Initializing MVI ViewModel...", "INFO")
            // Load catalog from remote API and store in database
            loadCatalog(force = true)
        }
    }

    private fun updateDeckText(text: String) {
        _localState.update { it.copy(deckText = text) }
    }

    private fun toggleIncludeSideboard(value: Boolean) {
        scope.launch {
            platformServices.updatePreferences { it.copy(includeSideboard = value) }
            log("Include sideboard: $value", "INFO")
        }
    }

    private fun toggleIncludeCommanders(value: Boolean) {
        scope.launch {
            platformServices.updatePreferences { it.copy(includeCommanders = value) }
            log("Include commanders: $value", "INFO")
        }
    }

    private fun toggleIncludeTokens(value: Boolean) {
        scope.launch {
            platformServices.updatePreferences { it.copy(includeTokens = value) }
            log("Include tokens: $value", "INFO")
        }
    }

    private fun loadCatalog(force: Boolean) {
        scope.launch {
            _localState.update { it.copy(loadingCatalog = true, catalogError = null) }
            
            try {
                log("Loading catalog from remote API...", "INFO")
                
                // Fetch from remote API
                val catalog = platformServices.fetchCatalogFromRemote { msg -> log(msg, "INFO") }
                
                if (catalog != null) {
                    // Store in database (becomes source of truth)
                    catalogStore.replaceCatalog(catalog)
                    log("Catalog stored in database: ${catalog.variants.size} variants", "INFO")
                    
                    // Note: Database flow will update ViewState with the new catalog.
                    // If there are deck entries, the user can re-run matching,
                    // or we wait for the ViewState to be updated via the database flow
                    // and then trigger matching with the catalog from ViewState (database).
                } else {
                    _localState.update { it.copy(catalogError = "Failed to load catalog from remote") }
                    log("Failed to load catalog from remote", "ERROR")
                }
            } catch (e: Exception) {
                _localState.update { it.copy(catalogError = e.message) }
                log("Catalog load exception: ${e.message}", "ERROR")
            } finally {
                _localState.update { it.copy(loadingCatalog = false) }
            }
        }
    }

    private fun parseDeck() {
        scope.launch {
            val state = _localState.value
            val preferences = _viewState.value.preferences
            
            val entries = withContext(Dispatchers.Default) {
                DecklistParser.parse(
                    state.deckText,
                    preferences.includeSideboard,
                    preferences.includeCommanders
                )
            }
            
            entries.forEach { e ->
                if (e.setCodeHint != null) {
                    log("Parsed: ${e.qty} ${e.cardName} (${e.setCodeHint}${e.collectorNumberHint?.let { " #$it" } ?: ""})", "DEBUG")
                } else {
                    log("Parsed: ${e.qty} ${e.cardName}", "DEBUG")
                }
            }
            
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
        val preferences = _viewState.value.preferences
        
        val matches = withContext(Dispatchers.Default) {
            Matcher.matchAll(
                entries,
                catalog,
                Matcher.MatchConfig(
                    preferences.variantPriority,
                    preferences.setPriority,
                    preferences.fuzzyEnabled
                )
            )
        }
        
        _localState.update { it.copy(matches = matches, showResultsWindow = true) }
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
            
            val entries = withContext(Dispatchers.Default) {
                DecklistParser.parse(
                    state.deckText,
                    preferences.includeSideboard,
                    preferences.includeCommanders
                )
            }
            
            entries.forEach { e ->
                if (e.setCodeHint != null) {
                    log("Parsed: ${e.qty} ${e.cardName} (${e.setCodeHint}${e.collectorNumberHint?.let { " #$it" } ?: ""})", "DEBUG")
                } else {
                    log("Parsed: ${e.qty} ${e.cardName}", "DEBUG")
                }
            }
            
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
        
        log("Resolved card variant at index $index", "INFO")
    }

    private fun exportCsv() {
        scope.launch {
            val matches = _localState.value.matches
            if (matches.isNotEmpty()) {
                platformServices.exportCsv(matches) { path ->
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
            platformServices.updatePreferences {
                it.copy(
                    variantPriority = variantPriority,
                    setPriority = setPriority,
                    fuzzyEnabled = fuzzyEnabled
                )
            }
            log("Preferences saved", "INFO")
            _localState.update { it.copy(showPreferences = false) }
        }
    }

    private fun updateVariantPriority(newPriority: List<String>) {
        scope.launch {
            platformServices.updatePreferences { it.copy(variantPriority = newPriority) }
            log("Variant priority updated", "INFO")
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

    private fun loadSavedImports() {
        // Saved imports are automatically loaded from database via Flow
        log("Saved imports loaded from database", "INFO")
    }

    private fun saveCurrentImport(name: String) {
        scope.launch {
            val state = _localState.value
            val preferences = _viewState.value.preferences
            
            if (state.deckText.isBlank()) {
                log("Cannot save empty import", "ERROR")
                return@launch
            }
            
            val entries = state.deckEntries.ifEmpty {
                withContext(Dispatchers.Default) {
                    DecklistParser.parse(
                        state.deckText,
                        preferences.includeSideboard,
                        preferences.includeCommanders
                    )
                }
            }
            
            val import = SavedImport(
                id = kotlin.uuid.Uuid.random().toString(),
                name = name,
                deckText = state.deckText,
                timestamp = Clock.System.now().toString(),
                cardCount = entries.size,
                includeSideboard = preferences.includeSideboard,
                includeCommanders = preferences.includeCommanders,
                includeTokens = preferences.includeTokens
            )
            
            importsStore.add(import)
            log("Import saved: $name", "INFO")
        }
    }

    private fun loadSavedImport(importId: String) {
        scope.launch {
            val import = _viewState.value.savedImports.find { it.id == importId }
            if (import != null) {
                platformServices.updatePreferences {
                    it.copy(
                        includeSideboard = import.includeSideboard,
                        includeCommanders = import.includeCommanders,
                        includeTokens = import.includeTokens
                    )
                }
                
                _localState.update {
                    it.copy(
                        deckText = import.deckText,
                        showSavedImportsWindow = false,
                        showResultsWindow = true
                    )
                }
                
                log("Loaded import: ${import.name}", "INFO")
            }
        }
    }

    private fun deleteSavedImport(importId: String) {
        scope.launch {
            importsStore.delete(importId)
            log("Import deleted", "INFO")
        }
    }

    private fun enrichVariantWithImage(variant: CardVariant) {
        // Skip if already has an image URL
        if (variant.imageUrl != null) return
        
        scope.launch {
            try {
                log("Fetching image for ${variant.nameOriginal} (${variant.setCode})...", "DEBUG")
                
                // Use ScryfallImageEnricher to fetch the image URL
                val enrichedVariant = catalog.ScryfallImageEnricher.enrichVariant(
                    variant = variant,
                    imageSize = "normal",
                    log = { msg -> log(msg, "DEBUG") }
                )
                
                // If we got an image URL, update the database
                if (enrichedVariant.imageUrl != null) {
                    catalogStore.updateVariantImageUrl(variant.sku, enrichedVariant.imageUrl)
                    log("Updated image URL for ${variant.nameOriginal}", "DEBUG")
                }
            } catch (e: Exception) {
                log("Failed to enrich variant ${variant.nameOriginal}: ${e.message}", "DEBUG")
            }
        }
    }

    private fun log(message: String, level: String = "INFO") {
        scope.launch {
            platformServices.addLog(LogEntry(level, message, Clock.System.now().toString()))
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
    val isDarkTheme: Boolean = true
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
    val isDarkTheme: Boolean = true
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
    data class LoadCatalog(val force: Boolean = true) : ViewIntent()
    data object ParseDeck : ViewIntent()
    data object RunMatch : ViewIntent()
    data object ParseAndMatch : ViewIntent()
    data class OpenResolve(val index: Int) : ViewIntent()
    data object CloseResolve : ViewIntent()
    data class ResolveCandidate(val index: Int, val variant: CardVariant) : ViewIntent()
    data object ExportCsv : ViewIntent()
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
    data object LoadSavedImports : ViewIntent()
    data class SaveCurrentImport(val name: String) : ViewIntent()
    data class LoadSavedImport(val importId: String) : ViewIntent()
    data class DeleteSavedImport(val importId: String) : ViewIntent()
    data class EnrichVariantWithImage(val variant: CardVariant) : ViewIntent()
}

/**
 * One-time side effects (navigation, toasts, etc.)
 */
sealed class ViewEffect {
    data class ShowMessage(val message: String) : ViewEffect()
    data class NavigateTo(val route: String) : ViewEffect()
    data class ShowError(val error: String) : ViewEffect()
}

/**
 * Platform-specific services for MVI ViewModel.
 */
interface MviPlatformServices {
    suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog?
    suspend fun updatePreferences(update: (Preferences) -> Preferences)
    suspend fun addLog(log: LogEntry)
    suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit)
    
    /**
     * Copy text to clipboard. Used for mobile platforms where file opening is not supported.
     */
    suspend fun copyToClipboard(text: String)
}
