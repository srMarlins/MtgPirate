package state

import deck.DecklistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import match.Matcher
import match.Matcher.MatchConfig
import model.CardVariant
import model.MatchStatus
import model.Catalog
import model.Preferences
import util.Logging

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

    fun dispatch(intent: MainIntent) {
        when (intent) {
            is MainIntent.Init -> init()
            is MainIntent.UpdateDeckText -> _state.value = _state.value.copy(deckText = intent.text)
            is MainIntent.ToggleIncludeSideboard -> toggleIncludeSideboard(intent.value)
            is MainIntent.ToggleIncludeCommanders -> toggleIncludeCommanders(intent.value)
            is MainIntent.ToggleIncludeTokens -> toggleIncludeTokens(intent.value)
            is MainIntent.LoadCatalog -> loadCatalog(intent.force)
            MainIntent.ParseDeck -> parseDeck()
            MainIntent.RunMatch -> runMatch()
            MainIntent.ParseAndMatch -> parseAndMatch()
            is MainIntent.OpenResolve -> _state.value = _state.value.copy(showCandidatesFor = intent.index)
            MainIntent.CloseResolve -> _state.value = _state.value.copy(showCandidatesFor = null)
            is MainIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
            MainIntent.ExportCsv -> exportCsv()
            MainIntent.ExportWizardResults -> exportWizardResults()
            is MainIntent.SetShowPreferences -> _state.value = _state.value.copy(showPreferences = intent.show)
            is MainIntent.SetShowCatalogWindow -> _state.value = _state.value.copy(showCatalogWindow = intent.show)
            is MainIntent.SetShowMatchesWindow -> _state.value = _state.value.copy(showMatchesWindow = intent.show)
            is MainIntent.SetShowResultsWindow -> _state.value = _state.value.copy(showResultsWindow = intent.show)
            is MainIntent.SavePreferences -> savePreferences(intent.variantPriority, intent.setPriority, intent.fuzzyEnabled)
            is MainIntent.Log -> log(intent.message, intent.level)
            is MainIntent.UpdateVariantPriority -> updateVariantPriority(intent.value)
            is MainIntent.CompleteWizardStep -> {
                val completed = _state.value.wizardCompletedSteps.toMutableSet()
                completed.add(intent.step)
                _state.value = _state.value.copy(wizardCompletedSteps = completed)
            }
            MainIntent.ToggleTheme -> _state.value = _state.value.copy(isDarkTheme = !_state.value.isDarkTheme)
            is MainIntent.SetShowSavedImportsWindow -> _state.value = _state.value.copy(showSavedImportsWindow = intent.show)
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
            if (loaded != null) {
                val current = _state.value
                _state.value = current.copy(
                    app = current.app.copy(
                        preferences = loaded,
                        logs = Logging.log(current.app.logs, "INFO", "Preferences loaded")
                    ),
                    includeSideboard = loaded.includeSideboard,
                    includeCommanders = loaded.includeCommanders,
                    includeTokens = loaded.includeTokens
                )
            }

            // Load saved imports
            val imports = platformServices.loadSavedImports()
            val s = _state.value
            _state.value = s.copy(
                app = s.app.copy(
                    savedImports = imports,
                    logs = Logging.log(s.app.logs, "INFO", "Loaded ${imports.size} saved imports")
                )
            )

            // Auto-load catalog (remote-first) on startup
            log("Catalog auto-load starting...", level = "INFO")
            val catalog = platformServices.loadCatalog(forceRefresh = true) { msg -> log(msg) }
            if (catalog != null) {
                val s = _state.value
                _state.value = s.copy(
                    app = s.app.copy(
                        catalog = catalog,
                        logs = Logging.log(s.app.logs, "INFO", "Catalog auto-loaded (remote): ${catalog.variants.size} variants")
                    )
                )
            } else {
                val s = _state.value
                _state.value = s.copy(
                    app = s.app.copy(
                        logs = Logging.log(s.app.logs, "ERROR", "Catalog auto-load failed (remote + fallback)")
                    )
                )
            }
        }
    }

    private fun loadCatalog(force: Boolean) {
        scope.launch {
            _state.value = _state.value.copy(loadingCatalog = true, catalogError = null)
            try {
                log("Manual catalog load starting...", level = "INFO")
                val catalog = platformServices.loadCatalog(forceRefresh = force) { msg -> log(msg) }
                if (catalog == null) {
                    _state.value = _state.value.copy(catalogError = "Failed to load catalog")
                } else {
                    val s = _state.value
                    var newApp = s.app.copy(catalog = catalog)
                    // Re-run matching if deck already parsed
                    if (newApp.deckEntries.isNotEmpty()) {
                        val matches = withContext(Dispatchers.Default) {
                            Matcher.matchAll(
                                newApp.deckEntries,
                                catalog,
                                MatchConfig(
                                    newApp.preferences.variantPriority,
                                    newApp.preferences.setPriority,
                                    newApp.preferences.fuzzyEnabled
                                )
                            )
                        }
                        newApp = newApp.copy(matches = matches)
                    }
                    _state.value = s.copy(app = newApp)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(catalogError = e.message)
                log("Catalog load exception: ${e.message}", level = "ERROR")
            } finally {
                _state.value = _state.value.copy(loadingCatalog = false)
            }
        }
    }

    private fun parseAndMatch() {
        val s = _state.value
        val catalog = s.app.catalog ?: return
        scope.launch {
            val entries = withContext(Dispatchers.Default) {
                DecklistParser.parse(s.deckText, s.includeSideboard, s.includeCommanders)
            }
            // Log parsing summary including set hints for verification
            entries.forEach { e ->
                if (e.setCodeHint != null) {
                    log("Parsed entry: ${e.qty} ${e.cardName} (set=${e.setCodeHint}${e.collectorNumberHint?.let { ", #$it" } ?: ""})", level = "DEBUG")
                } else {
                    log("Parsed entry: ${e.qty} ${e.cardName}", level = "DEBUG")
                }
            }
            var newApp = s.app.copy(deckEntries = entries)
            val matches = withContext(Dispatchers.Default) {
                Matcher.matchAll(
                    entries,
                    catalog,
                    MatchConfig(
                        newApp.preferences.variantPriority,
                        newApp.preferences.setPriority,
                        newApp.preferences.fuzzyEnabled
                    )
                )
            }
            newApp = newApp.copy(matches = matches)
            _state.value = _state.value.copy(app = newApp, showResultsWindow = entries.isNotEmpty())
        }
    }

    private fun parseDeck() {
        val s = _state.value
        scope.launch {
            val entries = withContext(Dispatchers.Default) {
                DecklistParser.parse(s.deckText, s.includeSideboard, s.includeCommanders)
            }
            entries.forEach { e ->
                if (e.setCodeHint != null) {
                    log("Parsed entry: ${e.qty} ${e.cardName} (set=${e.setCodeHint}${e.collectorNumberHint?.let { ", #$it" } ?: ""})", level = "DEBUG")
                } else {
                    log("Parsed entry: ${e.qty} ${e.cardName}", level = "DEBUG")
                }
            }
            val app = _state.value.app
            val newApp = app.copy(deckEntries = entries, matches = emptyList())
            _state.value = _state.value.copy(app = newApp)
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
            val app = _state.value.app
            _state.value = _state.value.copy(app = app.copy(matches = matches), showResultsWindow = true)
        }
    }

    private fun resolveCandidate(index: Int, variant: CardVariant) {
        val s = _state.value
        if (index !in s.app.matches.indices) return
        val match = s.app.matches[index]
        val updated = match.copy(status = MatchStatus.MANUAL_SELECTED, selectedVariant = variant)
        val newMatches = s.app.matches.toMutableList()
        newMatches[index] = updated
        _state.value = s.copy(app = s.app.copy(matches = newMatches), showCandidatesFor = null)
    }

    private fun exportCsv() {
        val s = _state.value
        scope.launch {
            val matches = s.app.matches
            if (matches.isNotEmpty()) {
                platformServices.exportCsv(matches) { path ->
                    log("CSV exported to: $path", level = "INFO")
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
                    foundPath?.let { log("Found cards exported to: $it", level = "INFO") }
                    unfoundPath?.let { log("Unfound cards exported to: $it", level = "INFO") }
                }
            }
        }
    }

    private fun savePreferences(variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean) {
        // Update state immediately so subsequent actions (like RunMatch) see new prefs
        val current = _state.value
        val prefs = current.app.preferences.copy(
            variantPriority = variantPriority,
            setPriority = setPriority,
            fuzzyEnabled = fuzzyEnabled
        )
        val newApp = current.app.copy(
            preferences = prefs,
            logs = Logging.log(current.app.logs, "INFO", "Preferences updated")
        )
        _state.value = current.copy(app = newApp, showPreferences = false)
        // Persist asynchronously
        scope.launch {
            platformServices.savePreferences(prefs)
        }
    }

    private fun updateVariantPriority(newPriority: List<String>) {
        val current = _state.value
        val prefs = current.app.preferences.copy(variantPriority = newPriority)
        val newApp = current.app.copy(
            preferences = prefs,
            logs = Logging.log(current.app.logs, "INFO", "Variant priority updated")
        )
        _state.value = current.copy(app = newApp)
        // Persist asynchronously
        scope.launch {
            platformServices.savePreferences(prefs)
        }
    }

    private fun toggleIncludeSideboard(value: Boolean) {
        val current = _state.value
        val prefs = current.app.preferences.copy(includeSideboard = value)
        val newApp = current.app.copy(
            preferences = prefs,
            logs = Logging.log(current.app.logs, "INFO", "Include sideboard updated: $value")
        )
        _state.value = current.copy(app = newApp, includeSideboard = value)
        // Persist asynchronously
        scope.launch {
            platformServices.savePreferences(prefs)
        }
    }

    private fun toggleIncludeCommanders(value: Boolean) {
        val current = _state.value
        val prefs = current.app.preferences.copy(includeCommanders = value)
        val newApp = current.app.copy(
            preferences = prefs,
            logs = Logging.log(current.app.logs, "INFO", "Include commanders updated: $value")
        )
        _state.value = current.copy(app = newApp, includeCommanders = value)
        // Persist asynchronously
        scope.launch {
            platformServices.savePreferences(prefs)
        }
    }

    private fun toggleIncludeTokens(value: Boolean) {
        val current = _state.value
        val prefs = current.app.preferences.copy(includeTokens = value)
        val newApp = current.app.copy(
            preferences = prefs,
            logs = Logging.log(current.app.logs, "INFO", "Include tokens updated: $value")
        )
        _state.value = current.copy(app = newApp, includeTokens = value)
        // Persist asynchronously
        scope.launch {
            platformServices.savePreferences(prefs)
        }
    }

    private fun loadSavedImports() {
        scope.launch {
            val imports = platformServices.loadSavedImports()
            val s = _state.value
            _state.value = s.copy(
                app = s.app.copy(
                    savedImports = imports,
                    logs = Logging.log(s.app.logs, "INFO", "Loaded ${imports.size} saved imports")
                )
            )
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
            val entries = if (s.app.deckEntries.isNotEmpty()) {
                s.app.deckEntries
            } else {
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
                // Fallback to timestamp-based name
                val timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
                "Import - $timestamp"
            }

            val import = model.SavedImport(
                id = java.util.UUID.randomUUID().toString(),
                name = autoName,
                deckText = s.deckText,
                timestamp = java.time.Instant.now().toString(),
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
            _state.value = s.copy(
                deckText = import.deckText,
                includeSideboard = import.includeSideboard,
                includeCommanders = import.includeCommanders,
                includeTokens = import.includeTokens,
                app = s.app.copy(
                    logs = Logging.log(s.app.logs, "INFO", "Loaded import: ${import.name}")
                ),
                showSavedImportsWindow = false,
                showResultsWindow = true // Open wizard/results window as if a new import was made
            )
        }
    }

    private fun deleteSavedImport(importId: String) {
        scope.launch {
            platformServices.deleteSavedImport(importId)
            log("Deleted import", "INFO")

            // Reload the list
            loadSavedImports()
        }
    }

    fun log(message: String, level: String = "INFO") {
        val s = _state.value
        _state.value = s.copy(app = s.app.copy(logs = Logging.log(s.app.logs, level, message)))
    }
}

/**
 * Platform-specific services interface.
 * Each platform (Desktop, iOS, Android) implements this interface.
 */
interface PlatformServices {
    suspend fun loadPreferences(): Preferences?
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
