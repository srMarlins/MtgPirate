@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)
package state

import deck.DecklistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import match.Matcher
import match.Matcher.MatchConfig
import model.CardVariant
import model.MatchStatus
import model.Catalog
import model.Preferences
import util.Logging
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Platform-agnostic state store for the main application.
 * Uses dependency injection for platform-specific operations.
 */
class MainStore(
    private val scope: CoroutineScope,
    private val platformServices: PlatformServices
) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    // --- Helper functions for code reuse ---
    // Logs are now handled as side effects and don't update state to prevent recompositions
    private fun log(level: String, message: String) {
        // In a production app, this would write to a logging service or console
        // For now, logs are simply not stored in state to avoid recompositions
        println("[$level] $message")
    }

    private fun updatePreferencesAndPersist(update: (Preferences) -> Preferences, alsoUpdateState: (MainState) -> MainState = { it }) {
        _state.update { current ->
            val prefs = update(current.app.preferences)
            val newApp = current.app.copy(preferences = prefs)
            alsoUpdateState(current.copy(app = newApp))
        }
        log("INFO", "Preferences updated")
        scope.launch {
            platformServices.savePreferences(_state.value.app.preferences)
        }
    }

    private suspend fun parseDeckEntries(deckText: String, includeSideboard: Boolean, includeCommanders: Boolean): List<model.DeckEntry> {
        val entries = withContext(Dispatchers.Default) {
            DecklistParser.parse(deckText, includeSideboard, includeCommanders)
        }
        entries.forEach { e ->
            if (e.setCodeHint != null) {
                val collectorHint = e.collectorNumberHint?.let { ", #$it" } ?: ""
                log("DEBUG", "Parsed entry: ${e.qty} ${e.cardName} (set=${e.setCodeHint}$collectorHint)")
            } else {
                log("DEBUG", "Parsed entry: ${e.qty} ${e.cardName}")
            }
        }
        return entries
    }

    private fun reloadSavedImportsWithLog(imports: List<model.SavedImport>) {
        _state.update { s ->
            s.copy(app = s.app.copy(savedImports = imports))
        }
        log("INFO", "Loaded ${imports.size} saved imports")
    }

    fun dispatch(intent: MainIntent) {
        when (intent) {
            is MainIntent.Init -> init()
            is MainIntent.UpdateDeckText -> _state.update { it.copy(deckText = intent.text) }
            is MainIntent.ToggleIncludeSideboard -> toggleIncludeSideboard(intent.value)
            is MainIntent.ToggleIncludeCommanders -> toggleIncludeCommanders(intent.value)
            is MainIntent.ToggleIncludeTokens -> toggleIncludeTokens(intent.value)
            is MainIntent.LoadCatalog -> loadCatalog(intent.force)
            MainIntent.ParseDeck -> parseDeck()
            MainIntent.RunMatch -> runMatch()
            MainIntent.ParseAndMatch -> parseAndMatch()
            is MainIntent.OpenResolve -> _state.update { it.copy(showCandidatesFor = intent.index) }
            MainIntent.CloseResolve -> _state.update { it.copy(showCandidatesFor = null) }
            is MainIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
            MainIntent.ExportCsv -> exportCsv()
            MainIntent.ExportWizardResults -> exportWizardResults()
            is MainIntent.SetShowPreferences -> _state.update { it.copy(showPreferences = intent.show) }
            is MainIntent.SetShowCatalogWindow -> _state.update { it.copy(showCatalogWindow = intent.show) }
            is MainIntent.SetShowMatchesWindow -> _state.update { it.copy(showMatchesWindow = intent.show) }
            is MainIntent.SetShowResultsWindow -> _state.update { it.copy(showResultsWindow = intent.show) }
            is MainIntent.SavePreferences -> savePreferences(intent.variantPriority, intent.setPriority, intent.fuzzyEnabled)
            is MainIntent.Log -> log(intent.message, intent.level)
            is MainIntent.UpdateVariantPriority -> updateVariantPriority(intent.value)
            is MainIntent.CompleteWizardStep -> {
                _state.update {
                    val completed = it.wizardCompletedSteps.toMutableSet()
                    completed.add(intent.step)
                    it.copy(wizardCompletedSteps = completed)
                }
            }
            MainIntent.ToggleTheme -> _state.update { it.copy(isDarkTheme = !it.isDarkTheme) }
            is MainIntent.SetShowSavedImportsWindow -> _state.update { it.copy(showSavedImportsWindow = intent.show) }
            MainIntent.LoadSavedImports -> loadSavedImports()
            is MainIntent.SaveCurrentImport -> saveCurrentImport(intent.name)
            is MainIntent.LoadSavedImport -> loadSavedImport(intent.importId)
            is MainIntent.DeleteSavedImport -> deleteSavedImport(intent.importId)
        }
    }

    private fun init() {
        scope.launch {
            // Load preferences
            val loaded = platformServices.loadPreferences()
            _state.update { current ->
                current.copy(
                    app = current.app.copy(preferences = loaded),
                    includeSideboard = loaded.includeSideboard,
                    includeCommanders = loaded.includeCommanders,
                    includeTokens = loaded.includeTokens
                )
            }
            log("INFO", "Preferences loaded")

            // Load saved imports
            val imports = platformServices.loadSavedImports()
            reloadSavedImportsWithLog(imports)

            // Auto-load catalog (remote-first) on startup
            log("INFO", "Catalog auto-load starting...")
            val catalog = platformServices.loadCatalog(forceRefresh = true) { msg -> log("INFO", msg) }
            if (catalog != null) {
                _state.update { s ->
                    s.copy(app = s.app.copy(catalog = catalog))
                }
                log("INFO", "Catalog auto-loaded (remote): ${catalog.variants.size} variants")
            } else {
                log("ERROR", "Catalog auto-load failed (remote + fallback)")
            }
        }
    }

    private fun loadCatalog(force: Boolean) {
        scope.launch {
            _state.update { it.copy(loadingCatalog = true, catalogError = null) }
            try {
                log("INFO", "Manual catalog load starting...")
                val catalog = platformServices.loadCatalog(forceRefresh = force) { msg -> log("INFO", msg) }
                if (catalog == null) {
                    _state.update { it.copy(catalogError = "Failed to load catalog") }
                } else {
                    _state.update { s ->
                        var newApp = s.app.copy(catalog = catalog)
                        if (newApp.deckEntries.isNotEmpty()) {
                            val matches = Matcher.matchAll(
                                newApp.deckEntries,
                                catalog,
                                MatchConfig(
                                    newApp.preferences.variantPriority,
                                    newApp.preferences.setPriority,
                                    newApp.preferences.fuzzyEnabled
                                )
                            )
                            newApp = newApp.copy(matches = matches)
                        }
                        s.copy(app = newApp)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(catalogError = e.message) }
                log("ERROR", "Catalog load exception: ${e.message}")
            } finally {
                _state.update { it.copy(loadingCatalog = false) }
            }
        }
    }

    private fun parseAndMatch() {
        val s = _state.value
        val catalog = s.app.catalog ?: return
        scope.launch {
            val entries = parseDeckEntries(s.deckText, s.includeSideboard, s.includeCommanders)
            _state.update { prev ->
                var newApp = prev.app.copy(deckEntries = entries)
                val matches = Matcher.matchAll(
                    entries,
                    catalog,
                    MatchConfig(
                        newApp.preferences.variantPriority,
                        newApp.preferences.setPriority,
                        newApp.preferences.fuzzyEnabled
                    )
                )
                newApp = newApp.copy(matches = matches)
                prev.copy(app = newApp, showResultsWindow = entries.isNotEmpty())
            }
        }
    }

    private fun parseDeck() {
        val s = _state.value
        scope.launch {
            val entries = parseDeckEntries(s.deckText, s.includeSideboard, s.includeCommanders)
            _state.update { prev ->
                val app = prev.app
                val newApp = app.copy(deckEntries = entries, matches = emptyList())
                prev.copy(app = newApp)
            }
        }
    }

    private fun runMatch() {
        val s = _state.value
        val catalog = s.app.catalog ?: return
        val entries = s.app.deckEntries
        if (entries.isEmpty()) return
        scope.launch {
            val matches = withContext(Dispatchers.Default) {
                Matcher.matchAll(
                    entries,
                    catalog,
                    MatchConfig(
                        s.app.preferences.variantPriority,
                        s.app.preferences.setPriority,
                        s.app.preferences.fuzzyEnabled
                    )
                )
            }
            _state.update { prev ->
                val app = prev.app
                prev.copy(app = app.copy(matches = matches), showResultsWindow = true)
            }
        }
    }

    private fun resolveCandidate(index: Int, variant: CardVariant) {
        _state.update { s ->
            if (index !in s.app.matches.indices) return@update s
            val match = s.app.matches[index]
            val updated = match.copy(status = MatchStatus.MANUAL_SELECTED, selectedVariant = variant)
            val newMatches = s.app.matches.toMutableList()
            newMatches[index] = updated
            s.copy(app = s.app.copy(matches = newMatches), showCandidatesFor = null)
        }
    }

    private fun exportCsv() {
        val s = _state.value
        scope.launch {
            val matches = s.app.matches
            if (matches.isNotEmpty()) {
                platformServices.exportCsv(matches) { path ->
                    log("INFO", "CSV exported to: $path")
                }
            }
        }
    }

    private fun exportWizardResults() {
        val s = _state.value
        scope.launch {
            val matches = s.app.matches
            if (matches.isNotEmpty()) {
                platformServices.exportWizardResults(matches) { foundPath, unfoundPath ->
                    foundPath?.let { log("INFO", "Found cards exported to: $it") }
                    unfoundPath?.let { log("INFO", "Unfound cards exported to: $it") }
                }
            }
        }
    }

    private fun savePreferences(variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean) {
        updatePreferencesAndPersist(
            update = { it.copy(variantPriority = variantPriority, setPriority = setPriority, fuzzyEnabled = fuzzyEnabled) },
            alsoUpdateState = { it.copy(showPreferences = false) }
        )
    }

    private fun updateVariantPriority(newPriority: List<String>) {
        updatePreferencesAndPersist(
            update = { it.copy(variantPriority = newPriority) }
        )
    }

    private fun toggleIncludeSideboard(value: Boolean) {
        updatePreferencesAndPersist(
            update = { it.copy(includeSideboard = value) },
            alsoUpdateState = { it.copy(includeSideboard = value) }
        )
    }

    private fun toggleIncludeCommanders(value: Boolean) {
        updatePreferencesAndPersist(
            update = { it.copy(includeCommanders = value) },
            alsoUpdateState = { it.copy(includeCommanders = value) }
        )
    }

    private fun toggleIncludeTokens(value: Boolean) {
        updatePreferencesAndPersist(
            update = { it.copy(includeTokens = value) },
            alsoUpdateState = { it.copy(includeTokens = value) }
        )
    }

    private fun loadSavedImports() {
        scope.launch {
            val imports = platformServices.loadSavedImports()
            reloadSavedImportsWithLog(imports)
        }
    }

    private fun saveCurrentImport(name: String) {
        scope.launch {
            val s = _state.value
            if (s.deckText.isBlank()) {
                log("Cannot save empty import", "ERROR")
                return@launch
            }

            // Check for duplicate deck text (deduplicate identical imports)
            val existingImports = s.app.savedImports
            val isDuplicate = existingImports.any { it.deckText.trim() == s.deckText.trim() }
            if (isDuplicate) {
                log("Import already exists (duplicate deck text), skipping save", "INFO")
                return@launch
            }

            // Parse the deck to get card count and commander name
            val entries = s.app.deckEntries.ifEmpty {
                withContext(Dispatchers.Default) {
                    DecklistParser.parse(s.deckText, s.includeSideboard, s.includeCommanders)
                }
            }

            val cardCount = entries.size

            // Extract commander name for the import name
            val commanderEntry = entries.firstOrNull { it.section == model.Section.COMMANDER }
            val autoName = if (commanderEntry != null) {
                // Use commander name
                commanderEntry.cardName
            } else {
                // Fallback to timestamp-based name using current time
                val now = Clock.System.now()
                val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
                val timestamp = "${localDateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${localDateTime.day}, ${localDateTime.year} ${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
                "Import - $timestamp"
            }

            val import = model.SavedImport(
                id = kotlin.uuid.Uuid.random().toString(),
                name = autoName,
                deckText = s.deckText,
                timestamp = Clock.System.now().toString(),
                cardCount = cardCount,
                includeSideboard = s.includeSideboard,
                includeCommanders = s.includeCommanders,
                includeTokens = s.includeTokens
            )

            platformServices.saveSavedImport(import)
            log("Saved import: $autoName", "INFO")

            // Reload the list
            loadSavedImports()
        }
    }

    private fun loadSavedImport(importId: String) {
        val s = _state.value
        val import = s.app.savedImports.find { it.id == importId }
        if (import != null) {
            _state.update { prev ->
                val updatedPreferences = prev.app.preferences.copy(
                    includeSideboard = import.includeSideboard,
                    includeCommanders = import.includeCommanders,
                    includeTokens = import.includeTokens
                )
                prev.copy(
                    deckText = import.deckText,
                    includeSideboard = import.includeSideboard,
                    includeCommanders = import.includeCommanders,
                    includeTokens = import.includeTokens,
                    app = prev.app.copy(preferences = updatedPreferences),
                    showSavedImportsWindow = false,
                    showResultsWindow = true
                )
            }
            log("INFO", "Loaded import: ${import.name}")
        }
    }

    private fun deleteSavedImport(importId: String) {
        scope.launch {
            platformServices.deleteSavedImport(importId)
            log("INFO", "Deleted import")

            // Reload the list
            loadSavedImports()
        }
    }
}

/**
 * Platform-specific services interface.
 * Each platform (Desktop, iOS, Android) implements this interface.
 */
interface PlatformServices {
    suspend fun loadPreferences(): Preferences
    suspend fun savePreferences(preferences: Preferences)
    suspend fun loadCatalog(forceRefresh: Boolean, log: (String) -> Unit): Catalog?
    suspend fun exportCsv(matches: List<model.DeckEntryMatch>, onComplete: (String) -> Unit)
    suspend fun exportWizardResults(
        matches: List<model.DeckEntryMatch>,
        onComplete: (foundPath: String?, unfoundPath: String?) -> Unit
    )
    suspend fun loadSavedImports(): List<model.SavedImport>
    suspend fun saveSavedImport(import: model.SavedImport)
    suspend fun deleteSavedImport(importId: String)
}
