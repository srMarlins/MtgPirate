package state

import model.AppState

/**
 * Main application state for the UI.
 * This is platform-agnostic and contains only presentation state.
 */
data class MainState(
    val app: AppState = AppState(),
    val deckText: String = "",
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
    // Wizard step tracking
    val wizardCompletedSteps: Set<Int> = emptySet(),
    // Theme
    val isDarkTheme: Boolean = true // Default to dark theme
)

/**
 * UI intents - user actions that trigger state changes.
 * All intents are platform-agnostic.
 */
sealed class MainIntent {
    data object Init : MainIntent()
    data class UpdateDeckText(val text: String) : MainIntent()
    data class ToggleIncludeSideboard(val value: Boolean) : MainIntent()
    data class ToggleIncludeCommanders(val value: Boolean) : MainIntent()
    data class ToggleIncludeTokens(val value: Boolean) : MainIntent()
    data class LoadCatalog(val force: Boolean = true) : MainIntent()
    data object ParseDeck : MainIntent()
    data object RunMatch : MainIntent()
    data object ParseAndMatch : MainIntent()
    data class OpenResolve(val index: Int) : MainIntent()
    data object CloseResolve : MainIntent()
    data class ResolveCandidate(val index: Int, val variant: model.CardVariant) : MainIntent()
    data object ExportCsv : MainIntent()
    data object ExportWizardResults : MainIntent()
    data class SetShowPreferences(val show: Boolean) : MainIntent()
    data class SetShowCatalogWindow(val show: Boolean) : MainIntent()
    data class SetShowMatchesWindow(val show: Boolean) : MainIntent()
    data class SetShowResultsWindow(val show: Boolean) : MainIntent()
    data class SavePreferences(
        val variantPriority: List<String>,
        val setPriority: List<String>,
        val fuzzyEnabled: Boolean
    ) : MainIntent()
    data class Log(val message: String, val level: String = "INFO") : MainIntent()
    data class UpdateVariantPriority(val value: List<String>) : MainIntent()
    data class CompleteWizardStep(val step: Int) : MainIntent()
    data object ToggleTheme : MainIntent()
    data class SetShowSavedImportsWindow(val show: Boolean) : MainIntent()
    data object LoadSavedImports : MainIntent()
    data class SaveCurrentImport(val name: String) : MainIntent()
    data class LoadSavedImport(val importId: String) : MainIntent()
    data class DeleteSavedImport(val importId: String) : MainIntent()
}

