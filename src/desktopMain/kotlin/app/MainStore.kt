package app

import catalog.CatalogFetcher
import deck.DecklistParser
import export.CsvExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import match.Matcher
import match.Matcher.MatchConfig
import model.AppState
import model.CardVariant
import model.MatchStatus
import util.Logging
import java.nio.file.Path

// Minimal MVI setup for the main screen.

data class MainState(
    val app: AppState = AppState(),
    val deckText: String = "",
    val includeSideboard: Boolean = false,
    val includeCommanders: Boolean = false,
    val loadingCatalog: Boolean = false,
    val catalogError: String? = null,
    val showExportResult: Path? = null,
    val showCandidatesFor: Int? = null,
    val showPreferences: Boolean = false,
    val showCatalogWindow: Boolean = false,
    val showMatchesWindow: Boolean = false
)

sealed class MainIntent {
    data object Init : MainIntent()
    data class UpdateDeckText(val text: String) : MainIntent()
    data class ToggleIncludeSideboard(val value: Boolean) : MainIntent()
    data class ToggleIncludeCommanders(val value: Boolean) : MainIntent()
    data class LoadCatalog(val force: Boolean = true) : MainIntent()
    data object ParseAndMatch : MainIntent()
    data class OpenResolve(val index: Int) : MainIntent()
    data object CloseResolve : MainIntent()
    data class ResolveCandidate(val index: Int, val variant: CardVariant) : MainIntent()
    data object ExportCsv : MainIntent()
    data class SetShowPreferences(val show: Boolean) : MainIntent()
    data class SetShowCatalogWindow(val show: Boolean) : MainIntent()
    data class SetShowMatchesWindow(val show: Boolean) : MainIntent()
    data class SavePreferences(
        val variantPriority: List<String>,
        val setPriority: List<String>,
        val fuzzyEnabled: Boolean
    ) : MainIntent()
    data class Log(val message: String, val level: String = "INFO") : MainIntent()
}

class MainStore(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state

    fun dispatch(intent: MainIntent) {
        when (intent) {
            is MainIntent.Init -> init()
            is MainIntent.UpdateDeckText -> _state.value = _state.value.copy(deckText = intent.text)
            is MainIntent.ToggleIncludeSideboard -> _state.value = _state.value.copy(includeSideboard = intent.value)
            is MainIntent.ToggleIncludeCommanders -> _state.value = _state.value.copy(includeCommanders = intent.value)
            is MainIntent.LoadCatalog -> loadCatalog(intent.force)
            MainIntent.ParseAndMatch -> parseAndMatch()
            is MainIntent.OpenResolve -> _state.value = _state.value.copy(showCandidatesFor = intent.index)
            MainIntent.CloseResolve -> _state.value = _state.value.copy(showCandidatesFor = null)
            is MainIntent.ResolveCandidate -> resolveCandidate(intent.index, intent.variant)
            MainIntent.ExportCsv -> exportCsv()
            is MainIntent.SetShowPreferences -> _state.value = _state.value.copy(showPreferences = intent.show)
            is MainIntent.SetShowCatalogWindow -> _state.value = _state.value.copy(showCatalogWindow = intent.show)
            is MainIntent.SetShowMatchesWindow -> _state.value = _state.value.copy(showMatchesWindow = intent.show)
            is MainIntent.SavePreferences -> savePreferences(intent.variantPriority, intent.setPriority, intent.fuzzyEnabled)
            is MainIntent.Log -> log(intent.message, intent.level)
        }
    }

    private fun init() {
        scope.launch {
            // Load preferences
            val loaded = withContext(Dispatchers.IO) { persistence.PreferencesStore.load() }
            if (loaded != null) {
                val current = _state.value
                _state.value = current.copy(app = current.app.copy(preferences = loaded, logs = Logging.log(current.app.logs, "INFO", "Preferences loaded")))
            }
            // Auto-load catalog (remote-first) on startup
            log("Catalog auto-load starting...", level = "INFO")
            val catalog = withContext(Dispatchers.IO) { CatalogFetcher.load(forceRefresh = true, log = { msg -> log(msg) }) }
            if (catalog != null) {
                val s = _state.value
                _state.value = s.copy(app = s.app.copy(catalog = catalog, logs = Logging.log(s.app.logs, "INFO", "Catalog auto-loaded (remote): ${catalog.variants.size} variants")))
            } else {
                val s = _state.value
                _state.value = s.copy(app = s.app.copy(logs = Logging.log(s.app.logs, "ERROR", "Catalog auto-load failed (remote + fallback)")))
            }
        }
    }

    private fun loadCatalog(force: Boolean) {
        scope.launch {
            _state.value = _state.value.copy(loadingCatalog = true, catalogError = null)
            try {
                log("Manual catalog load starting...", level = "INFO")
                val catalog = withContext(Dispatchers.IO) { CatalogFetcher.load(forceRefresh = force, log = { msg -> log(msg) }) }
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
                                MatchConfig(newApp.preferences.variantPriority, newApp.preferences.setPriority, newApp.preferences.fuzzyEnabled)
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
            var newApp = s.app.copy(deckEntries = entries)
            val matches = withContext(Dispatchers.Default) {
                Matcher.matchAll(
                    entries,
                    catalog,
                    MatchConfig(newApp.preferences.variantPriority, newApp.preferences.setPriority, newApp.preferences.fuzzyEnabled)
                )
            }
            newApp = newApp.copy(matches = matches)
            _state.value = _state.value.copy(app = newApp)
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
                val path = withContext(Dispatchers.IO) { CsvExporter.export(matches) }
                _state.value = _state.value.copy(showExportResult = path)
            }
        }
    }

    private fun savePreferences(variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean) {
        scope.launch {
            val current = _state.value
            val prefs = current.app.preferences.copy(
                variantPriority = variantPriority,
                setPriority = setPriority,
                fuzzyEnabled = fuzzyEnabled
            )
            val newApp = current.app.copy(preferences = prefs, logs = Logging.log(current.app.logs, "INFO", "Preferences updated"))
            _state.value = current.copy(app = newApp, showPreferences = false)
            withContext(Dispatchers.IO) { persistence.PreferencesStore.save(prefs) }
        }
    }

    fun log(message: String, level: String = "INFO") {
        val s = _state.value
        _state.value = s.copy(app = s.app.copy(logs = Logging.log(s.app.logs, level, message)))
    }
}
