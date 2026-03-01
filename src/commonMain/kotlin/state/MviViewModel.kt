@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package state

import database.CatalogStore
import database.Database
import database.ImportsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import model.ProFeature
import model.ProStatus
import model.PurchaseResult
import model.SavedImport
import model.Seller
import model.ShoppingPlan
import optimizer.ShoppingOptimizer
import purchase.PurchaseManager
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
    private val platformServices: MviPlatformServices,
    private val purchaseManager: PurchaseManager? = null
) {
    // -- Use cases ----------------------------------------------------------
    private val catalogUseCase = CatalogUseCase(catalogStore, platformServices)
    private val matchingUseCase = MatchingUseCase()
    private val importExportUseCase = ImportExportUseCase(importsStore, platformServices)
    private val preferencesUseCase = PreferencesUseCase(platformServices)
    private val deckSearchUseCase = DeckSearchUseCase(
        sources = catalogUseCase.sourceRegistry.allSources,
        catalogStore = catalogStore,
    )

    // -- State --------------------------------------------------------------
    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _viewEffects = MutableSharedFlow<ViewEffect>()
    val viewEffects: SharedFlow<ViewEffect> = _viewEffects.asSharedFlow()

    // Local transient UI state (not from database)
    private val _localState = MutableStateFlow(LocalUiState())

    init {
        // Subscribe to database flows and combine into ViewState
        // No outer scope.launch needed — launchIn(scope) handles collection
        val refreshedMatchesFlow = combine(
            database.observeCatalog().distinctUntilChanged(),
            _localState.map { it.matches }.distinctUntilChanged()
        ) { catalog, matches ->
            catalogUseCase.refreshMatchesFromCatalog(matches, catalog)
        }

        // Derive match counts reactively from matches
        val matchCountsFlow = _localState
            .map { it.matches }
            .distinctUntilChanged()
            .map { matchingUseCase.calculateMatchCounts(it) }

        // Group refreshedMatches + counts to stay within combine's 5-param limit
        val derivedFlow = combine(refreshedMatchesFlow, matchCountsFlow) { refreshed, counts ->
            refreshed to counts
        }

        combine(
            database.observeCatalog(),
            database.observePreferences().map { it ?: Preferences() },
            database.observeSavedImports(),
            _localState,
            derivedFlow
        ) { catalog, preferences, savedImports, localState, (refreshedMatches, counts) ->
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
                matchedCount = counts.matched,
                unmatchedCount = counts.unmatched,
                ambiguousCount = counts.ambiguous,
                totalPriceCents = counts.totalPrice,
                multiMatches = localState.multiMatches,
                shoppingPlan = localState.shoppingPlan,
                availableSellers = localState.availableSellers,
                loadingMultiCatalogs = localState.searchProgress?.isSearching ?: localState.loadingMultiCatalogs,
                searchProgress = localState.searchProgress,
                proStatus = localState.proStatus,
                showUpgradePrompt = localState.showUpgradePrompt,
            )
        }.onEach { newState ->
            _viewState.update { newState }
        }.launchIn(scope)
    }

    /**
     * Process user intents.
     * Synchronous intents are handled inline. Async intents are launched
     * in a single coroutine so that sequential calls within a handler
     * (e.g. loadAllCatalogs → runMultiMatch) share the same coroutine
     * and cannot race.
     */
    fun processIntent(intent: ViewIntent) {
        // Synchronous intents — no coroutine needed
        when (intent) {
            is ViewIntent.UpdateDeckText -> { updateDeckText(intent.text); return }
            is ViewIntent.OpenResolve -> { openResolve(intent.index); return }
            is ViewIntent.CloseResolve -> { closeResolve(); return }
            is ViewIntent.SetShowPreferences -> { setShowPreferences(intent.show); return }
            is ViewIntent.SetShowCatalogWindow -> { setShowCatalogWindow(intent.show); return }
            is ViewIntent.SetShowMatchesWindow -> { setShowMatchesWindow(intent.show); return }
            is ViewIntent.SetShowResultsWindow -> { setShowResultsWindow(intent.show); return }
            is ViewIntent.SetShowSavedImportsWindow -> { setShowSavedImportsWindow(intent.show); return }
            is ViewIntent.CompleteWizardStep -> { completeWizardStep(intent.step); return }
            is ViewIntent.ToggleTheme -> { toggleTheme(); return }
            is ViewIntent.Log -> { log(intent.message, intent.level); return }
            is ViewIntent.DismissUpgradePrompt -> { dismissUpgradePrompt(); return }
            is ViewIntent.ShowUpgradePrompt -> { showUpgradePrompt(intent.feature); return }
            else -> {} // fall through to async
        }
        // Async intents — single launch per intent
        scope.launch {
            when (intent) {
                is ViewIntent.Init -> initHandler()
                is ViewIntent.ToggleIncludeSideboard -> toggleIncludeSideboard(intent.value)
                is ViewIntent.ToggleIncludeCommanders -> toggleIncludeCommanders(intent.value)
                is ViewIntent.ToggleIncludeTokens -> toggleIncludeTokens(intent.value)
                is ViewIntent.LoadCatalog -> loadCatalog()
                ViewIntent.ParseDeck -> parseDeck()
                ViewIntent.RunMatch -> runMatch()
                ViewIntent.ParseAndMatch -> parseAndMatch()
                is ViewIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
                ViewIntent.ExportCsv -> exportCsv()
                ViewIntent.ExportWizardResults -> exportWizardResults()
                is ViewIntent.SavePreferences -> savePreferences(
                    intent.variantPriority, intent.setPriority, intent.fuzzyEnabled
                )
                is ViewIntent.UpdateVariantPriority -> updateVariantPriority(intent.value)
                is ViewIntent.SaveCurrentImport -> saveCurrentImport()
                is ViewIntent.LoadSavedImport -> loadSavedImport(intent.importId)
                is ViewIntent.DeleteSavedImport -> deleteSavedImport(intent.importId)
                is ViewIntent.EnrichVariantWithImage -> enrichVariantWithImage(intent.variant)
                is ViewIntent.UpdateEnabledSellers -> updateEnabledSellers(intent.sellers)
                is ViewIntent.UpdateProxyFirst -> updateProxyFirst(intent.value)
                ViewIntent.LoadAllCatalogs -> loadAllCatalogs()
                ViewIntent.SearchDeck -> searchDeck()
                ViewIntent.RunMultiMatch -> runMultiMatch()
                ViewIntent.OptimizeShoppingPlan -> optimizeShoppingPlan()
                is ViewIntent.OverrideCardSeller -> overrideCardSeller(intent.matchIndex, intent.seller)
                // Composite wizard intents — sequential steps in one coroutine
                ViewIntent.WizardImportToPreferences -> wizardImportToPreferences()
                ViewIntent.WizardPreferencesToResults -> wizardPreferencesToResults()
                ViewIntent.WizardResultsToExport -> wizardResultsToExport()
                // Pro purchase intents
                ViewIntent.PurchasePro -> purchasePro()
                ViewIntent.RestorePurchases -> restorePurchases()
                ViewIntent.CheckProStatus -> checkProStatus()
                // Sync intents already handled above — exhaustive match requires listing them
                is ViewIntent.UpdateDeckText,
                is ViewIntent.OpenResolve,
                is ViewIntent.CloseResolve,
                is ViewIntent.SetShowPreferences,
                is ViewIntent.SetShowCatalogWindow,
                is ViewIntent.SetShowMatchesWindow,
                is ViewIntent.SetShowResultsWindow,
                is ViewIntent.SetShowSavedImportsWindow,
                is ViewIntent.CompleteWizardStep,
                is ViewIntent.ToggleTheme,
                is ViewIntent.Log,
                is ViewIntent.UpdateEnabledSellers,
                is ViewIntent.UpdateProxyFirst,
                is ViewIntent.DismissUpgradePrompt,
                is ViewIntent.ShowUpgradePrompt -> {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Intent handlers — suspend functions called from processIntent's
    // single coroutine launch site
    // -----------------------------------------------------------------------

    private suspend fun initHandler() {
        withContext(Dispatchers.IO) {
            log("Initializing MVI ViewModel...", "INFO")
            try {
                val variantCount = catalogUseCase.getVariantCount()
                if (variantCount == 0L) {
                    log("Catalog is empty — will fetch when search is triggered", "INFO")
                } else {
                    log("Catalog already loaded: $variantCount variants", "INFO")
                }
            } catch (e: Exception) {
                log("Failed to check catalog: ${e.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to initialize catalog"))
            }
        }
        checkProStatus()
    }

    private fun updateDeckText(text: String) {
        _localState.update { it.copy(deckText = text) }
    }

    private suspend fun toggleIncludeSideboard(value: Boolean) {
        preferencesUseCase.setIncludeSideboard(value).onFailure {
            log("Failed to save sideboard preference: ${it.message}", "ERROR")
            _viewEffects.emit(ViewEffect.ShowError("Failed to save sideboard preference"))
        }.onSuccess {
            log("Include sideboard: $value", "INFO")
        }
    }

    private suspend fun toggleIncludeCommanders(value: Boolean) {
        preferencesUseCase.setIncludeCommanders(value).onFailure {
            log("Failed to save commanders preference: ${it.message}", "ERROR")
            _viewEffects.emit(ViewEffect.ShowError("Failed to save commanders preference"))
        }.onSuccess {
            log("Include commanders: $value", "INFO")
        }
    }

    private suspend fun toggleIncludeTokens(value: Boolean) {
        preferencesUseCase.setIncludeTokens(value).onFailure {
            log("Failed to save tokens preference: ${it.message}", "ERROR")
            _viewEffects.emit(ViewEffect.ShowError("Failed to save tokens preference"))
        }.onSuccess {
            log("Include tokens: $value", "INFO")
        }
    }

    private suspend fun loadCatalog() {
        withContext(Dispatchers.IO) {
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

    private suspend fun parseDeck() {
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

    private suspend fun runMatch() {
        val state = _localState.value
        val catalog = _viewState.value.catalog
        val preferences = _viewState.value.preferences

        if (catalog == null) {
            log("No catalog available for matching", "ERROR")
            return
        }
        if (state.deckEntries.isEmpty()) {
            log("No deck entries to match", "WARNING")
            return
        }

        runMatchInternal(state.deckEntries, catalog, preferences)
    }

    private suspend fun runMatchInternal(entries: List<DeckEntry>, catalog: Catalog, preferences: Preferences) {
        _localState.update { it.copy(isMatching = true) }

        val matches = matchingUseCase.matchEntries(
            entries,
            catalog,
            Matcher.MatchConfig(
                variantPriority = preferences.variantPriority,
                setPriority = preferences.setPriority,
                fuzzyEnabled = preferences.fuzzyEnabled,
                proxyFirst = preferences.proxyFirst,
            )
        )

        // Counts are derived reactively via matchCountsFlow in init
        _localState.update {
            it.copy(
                matches = matches,
                showResultsWindow = true,
                isMatching = false
            )
        }
        log("Matched ${matches.size} entries", "INFO")
    }

    private suspend fun parseAndMatch() {
        val state = _localState.value
        val preferences = _viewState.value.preferences
        val catalog = _viewState.value.catalog

        if (catalog == null) {
            log("No catalog available for matching", "ERROR")
            return
        }

        val entries = matchingUseCase.parseDeck(
            state.deckText,
            preferences.includeSideboard,
            preferences.includeCommanders
        )

        logParsedEntries(entries)
        _localState.update { it.copy(deckEntries = entries) }
        runMatchInternal(entries, catalog, preferences)
    }

    private fun openResolve(index: Int) {
        _localState.update { it.copy(showCandidatesFor = index) }
    }

    private fun closeResolve() {
        _localState.update { it.copy(showCandidatesFor = null) }
    }

    private suspend fun resolveCandidate(index: Int, variant: CardVariant) {
        // Counts are derived reactively via matchCountsFlow in init
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

    private suspend fun exportCsv(
        matches: List<DeckEntryMatch> = _localState.value.matches
    ) {
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

    private suspend fun exportWizardResults(
        matches: List<DeckEntryMatch> = _localState.value.matches
    ) {
        if (matches.isNotEmpty()) {
            importExportUseCase.exportWizardResults(matches) { foundPath, unfoundPath ->
                foundPath?.let { log("Found cards exported to: $it", "INFO") }
                unfoundPath?.let { log("Unfound cards exported to: $it", "INFO") }
            }
        } else {
            log("No matches to export", "WARNING")
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

    private suspend fun savePreferences(variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean) {
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

    private suspend fun updateVariantPriority(newPriority: List<String>) {
        preferencesUseCase.updateVariantPriority(newPriority)
            .onSuccess { log("Variant priority updated", "INFO") }
            .onFailure {
                log("Failed to update variant priority: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to update variant priority"))
            }
    }

    private suspend fun updateEnabledSellers(sellers: List<String>) {
        // Allow if only USEA is selected (free tier), empty list (unchecking all), or if Pro
        val isOnlyUsea = sellers.size == 1 && sellers.first() == Seller.USEA.name
        if (!isOnlyUsea && sellers.isNotEmpty() && !_localState.value.proStatus.isPro) {
            _localState.update { it.copy(showUpgradePrompt = ProFeature.MULTI_SELLER) }
            return
        }
        preferencesUseCase.updateEnabledSellers(sellers)
            .onSuccess { log("Enabled sellers updated: ${sellers.joinToString()}", "INFO") }
            .onFailure {
                log("Failed to update enabled sellers: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to update enabled sellers"))
            }
    }

    private suspend fun updateProxyFirst(value: Boolean) {
        preferencesUseCase.updateProxyFirst(value)
            .onSuccess { log("Proxy-first preference: $value", "INFO") }
            .onFailure {
                log("Failed to update proxy-first: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to update proxy-first preference"))
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
        if (!requirePro(ProFeature.THEME_CUSTOMIZATION)) return
        _localState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun setShowSavedImportsWindow(show: Boolean) {
        _localState.update { it.copy(showSavedImportsWindow = show) }
    }

    private suspend fun saveCurrentImport(
        deckText: String = _localState.value.deckText,
        deckEntries: List<DeckEntry> = _localState.value.deckEntries,
        preferences: Preferences = _viewState.value.preferences,
        savedImports: List<SavedImport> = _viewState.value.savedImports
    ) {
        if (!requirePro(ProFeature.IMPORT_HISTORY)) return
        importExportUseCase.saveCurrentImport(
            deckText, deckEntries, preferences, savedImports
        ).onSuccess { msg -> log(msg, "INFO") }
            .onFailure {
                log("Failed to save import: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to save import"))
            }
    }

    private suspend fun loadSavedImport(
        importId: String,
        savedImports: List<SavedImport> = _viewState.value.savedImports
    ) {
        importExportUseCase.loadSavedImport(importId, savedImports)
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

    private suspend fun deleteSavedImport(importId: String) {
        importExportUseCase.deleteSavedImport(importId)
            .onSuccess { log("Import deleted", "INFO") }
            .onFailure {
                log("Failed to delete import: ${it.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to delete import"))
            }
    }

    private suspend fun enrichVariantWithImage(variant: CardVariant) {
        if (variant.imageUrl != null) return
        catalogUseCase.enrichVariantWithImage(variant) { msg, level -> log(msg, level) }
    }

    // -----------------------------------------------------------------------
    // Multi-catalog intent handlers
    // -----------------------------------------------------------------------

    private suspend fun loadAllCatalogs() {
        // Atomic check-and-set guard against concurrent loads
        var wasAlreadyLoading = false
        _localState.update { state ->
            if (state.loadingMultiCatalogs) {
                wasAlreadyLoading = true
                state
            } else {
                state.copy(loadingMultiCatalogs = true)
            }
        }
        if (wasAlreadyLoading) {
            log("loadAllCatalogs already in progress, skipping", "WARNING")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val loadedSellers = catalogUseCase.loadAllCatalogs { msg, level -> log(msg, level) }
                _localState.update { it.copy(availableSellers = loadedSellers.distinct()) }
                // Auto-trigger matching after catalogs load
                if (loadedSellers.isNotEmpty()) {
                    runAllMatching()
                }
            } catch (e: Exception) {
                log("Failed to load multi-catalogs: ${e.message}", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Failed to load catalogs from sellers"))
            } finally {
                _localState.update { it.copy(loadingMultiCatalogs = false, catalogsLoadedThisSession = true) }
            }
        }
    }

    private suspend fun searchDeck() {
        val preferences = _viewState.value.preferences
        val enabledSellerNames = if (_localState.value.proStatus.isPro) {
            preferences.enabledSellers.toSet()
        } else {
            setOf(Seller.USEA.name)
        }
        val filteredSources = catalogUseCase.sourceRegistry.allSources
            .filter { it.seller.name in enabledSellerNames }

        if (filteredSources.isEmpty()) {
            log("No sellers enabled — enable at least one seller in preferences", "WARNING")
            _viewEffects.emit(ViewEffect.ShowError("No sellers enabled"))
            return
        }

        // Atomic check-and-set guard against concurrent searches
        var wasAlreadySearching = false
        _localState.update { state ->
            if (state.searchProgress?.isSearching == true) {
                wasAlreadySearching = true
                state
            } else {
                // Set initial progress immediately so UI shows the loading panel
                state.copy(
                    searchProgress = SearchProgress(
                        totalCards = 0,
                        cardsWithResults = 0,
                        sellerStatuses = filteredSources.associate { source ->
                            source.seller to SellerSearchStatus(
                                seller = source.seller,
                                state = SearchState.PENDING,
                            )
                        },
                        multiMatches = emptyList(),
                        isComplete = false,
                    ),
                    showResultsWindow = true,
                )
            }
        }
        if (wasAlreadySearching) {
            log("searchDeck already in progress, skipping", "WARNING")
            return
        }

        val entries = _localState.value.deckEntries
        if (entries.isEmpty()) {
            log("No deck entries to search", "WARNING")
            return
        }

        val config = MultiCatalogMatcher.Config(
            variantPriority = preferences.variantPriority,
            setPriority = preferences.setPriority,
            fuzzyEnabled = preferences.fuzzyEnabled,
            proxyFirst = preferences.proxyFirst,
        )

        // Create a DeckSearchUseCase with only the enabled sources
        val filteredSearchUseCase = DeckSearchUseCase(
            sources = filteredSources,
            catalogStore = catalogStore,
        )

        withContext(Dispatchers.IO) {
            filteredSearchUseCase.search(entries, config)
                .collect { progress ->
                    _localState.update { state ->
                        state.copy(
                            searchProgress = progress,
                            multiMatches = progress.multiMatches,
                            availableSellers = progress.sellerStatuses
                                .filter { it.value.state == SearchState.DONE }
                                .map { it.key },
                        )
                    }

                    if (progress.isComplete) {
                        val catalog = database.observeCatalog().first()
                        if (catalog.variants.isNotEmpty() && entries.isNotEmpty()) {
                            runMatchInternal(entries, catalog, _viewState.value.preferences)
                        }
                        _localState.update { it.copy(catalogsLoadedThisSession = true) }
                    }
                }
        }
    }

    /**
     * Read fresh catalog/preferences from DB and run both multi-seller and single-seller matching.
     * Shared by loadAllCatalogs (after loading) and wizardPreferencesToResults (cached path).
     */
    private suspend fun runAllMatching() {
        val catalog = withContext(Dispatchers.IO) { database.observeCatalog().first() }
        val preferences = withContext(Dispatchers.IO) { database.observePreferences().first() } ?: Preferences()
        val entries = _localState.value.deckEntries
        if (catalog.variants.isNotEmpty() && entries.isNotEmpty()) {
            runMultiMatch(entries, catalog, preferences)
            runMatchInternal(entries, catalog, preferences)
        }
    }

    private suspend fun runMultiMatch(
        entries: List<DeckEntry> = _localState.value.deckEntries,
        catalog: Catalog = _viewState.value.catalog ?: Catalog(emptyList()),
        preferences: Preferences = _viewState.value.preferences
    ) {
        if (catalog.variants.isEmpty()) {
            log("No catalog available for multi-matching", "ERROR")
            return
        }
        if (entries.isEmpty()) {
            log("No deck entries to multi-match", "WARNING")
            return
        }

        _localState.update { it.copy(isMatching = true) }
        try {
            val perSellerCatalogs = catalog.variants
                .groupBy { it.seller }
                .mapValues { (_, variants) -> Catalog(variants) }

            val config = MultiCatalogMatcher.Config(
                variantPriority = preferences.variantPriority,
                setPriority = preferences.setPriority,
                fuzzyEnabled = preferences.fuzzyEnabled,
                proxyFirst = preferences.proxyFirst,
            )

            val multiMatches = matchingUseCase.matchEntriesMulti(
                entries,
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

    private suspend fun optimizeShoppingPlan(
        multiMatches: List<MultiMatch> = _localState.value.multiMatches
    ) {
        if (!requirePro(ProFeature.SHOPPING_OPTIMIZER)) return
        withContext(Dispatchers.IO) {
            if (multiMatches.isEmpty()) {
                log("No multi-matches available for optimization", "WARNING")
                return@withContext
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

    private suspend fun overrideCardSeller(matchIndex: Int, seller: Seller) {
        if (!requirePro(ProFeature.SELLER_OVERRIDE)) return
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

    // -----------------------------------------------------------------------
    // Composite wizard handlers — sequential steps in one coroutine
    // -----------------------------------------------------------------------

    /** Step 1 → 2: Save import, parse deck, mark step complete */
    private suspend fun wizardImportToPreferences() {
        if (_localState.value.proStatus.isPro) {
            saveCurrentImport()
        }
        parseDeck()
        completeWizardStep(1)
    }

    /** Step 2 → 3: Mark complete, search deck across all sellers, then match */
    private suspend fun wizardPreferencesToResults() {
        completeWizardStep(2)
        parseDeck()

        if (!_localState.value.catalogsLoadedThisSession) {
            searchDeck()
        } else {
            log("Catalogs already loaded this session, running match only", "INFO")
            runAllMatching()
        }
    }

    /** Step 3 → 4: Mark step complete, optimize shopping plan */
    private suspend fun wizardResultsToExport() {
        completeWizardStep(3)
        if (_localState.value.proStatus.isPro) {
            optimizeShoppingPlan()
        }
    }

    // -----------------------------------------------------------------------
    // Pro purchase handlers
    // -----------------------------------------------------------------------

    private fun requirePro(feature: ProFeature): Boolean {
        if (_localState.value.proStatus.isPro) return true
        _localState.update { it.copy(showUpgradePrompt = feature) }
        return false
    }

    private fun showUpgradePrompt(feature: ProFeature) {
        _localState.update { it.copy(showUpgradePrompt = feature) }
    }

    private fun dismissUpgradePrompt() {
        _localState.update { it.copy(showUpgradePrompt = null) }
    }

    private suspend fun purchasePro() {
        val manager = purchaseManager ?: return
        val result = manager.purchase()
        when (result) {
            PurchaseResult.SUCCESS -> {
                _localState.update { it.copy(proStatus = ProStatus.Pro, showUpgradePrompt = null) }
                database.updateIsPro(true)
                log("Pro unlocked!", "INFO")
            }
            PurchaseResult.ERROR -> {
                log("Purchase failed", "ERROR")
                _viewEffects.emit(ViewEffect.ShowError("Purchase failed. Please try again."))
            }
            PurchaseResult.CANCELLED -> {
                // User cancelled — no action needed
            }
        }
    }

    private suspend fun restorePurchases() {
        val manager = purchaseManager ?: return
        val status = manager.restorePurchases()
        _localState.update { it.copy(proStatus = status, showUpgradePrompt = null) }
        database.updateIsPro(status.isPro)
        if (status.isPro) {
            log("Pro restored!", "INFO")
        }
    }

    private suspend fun checkProStatus() {
        // First load from cache
        val cached = withContext(Dispatchers.IO) {
            database.observePreferences().first()
        }
        if (cached?.isPro == true) {
            _localState.update { it.copy(proStatus = ProStatus.Pro) }
        }
        // Then verify with RevenueCat
        val manager = purchaseManager ?: return
        val status = withContext(Dispatchers.IO) {
            manager.checkEntitlement()
        }
        _localState.update { it.copy(proStatus = status) }
        database.updateIsPro(status.isPro)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Fire-and-forget logging — intentionally launches independently.
     *  Catches exceptions so a logging failure never crashes the app. */
    private fun log(message: String, level: String = "INFO") {
        scope.launch {
            try {
                platformServices.addLog(LogEntry(level, message, Clock.System.now().toString()))
            } catch (_: Exception) {
                // Logging is best-effort — never crash the app for a log write failure
            }
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
    val loadingMultiCatalogs: Boolean = false,
    val searchProgress: SearchProgress? = null,
    val proStatus: ProStatus = ProStatus.Free,
    val showUpgradePrompt: ProFeature? = null,
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
    // Match counts are derived reactively in init via matchCountsFlow
    // Multi-catalog matching and shopping optimization
    val multiMatches: List<MultiMatch> = emptyList(),
    val shoppingPlan: ShoppingPlan? = null,
    val availableSellers: List<Seller> = emptyList(),
    val loadingMultiCatalogs: Boolean = false,
    val catalogsLoadedThisSession: Boolean = false,
    val searchProgress: SearchProgress? = null,
    val proStatus: ProStatus = ProStatus.Free,
    val showUpgradePrompt: ProFeature? = null,
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
    data class UpdateEnabledSellers(val sellers: List<String>) : ViewIntent()
    data class UpdateProxyFirst(val value: Boolean) : ViewIntent()

    // Multi-catalog and shopping optimization intents
    data object LoadAllCatalogs : ViewIntent()
    data object SearchDeck : ViewIntent()
    data object RunMultiMatch : ViewIntent()
    data object OptimizeShoppingPlan : ViewIntent()
    data class OverrideCardSeller(val matchIndex: Int, val seller: Seller) : ViewIntent()

    // Composite wizard intents — atomic multi-step transitions
    data object WizardImportToPreferences : ViewIntent()
    data object WizardPreferencesToResults : ViewIntent()
    data object WizardResultsToExport : ViewIntent()

    // Pro purchase intents
    data object PurchasePro : ViewIntent()
    data object RestorePurchases : ViewIntent()
    data object CheckProStatus : ViewIntent()
    data class ShowUpgradePrompt(val feature: ProFeature) : ViewIntent()
    data object DismissUpgradePrompt : ViewIntent()
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
