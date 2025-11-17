package platform

import catalog.RemoteCatalogDataSource
import database.Database
import export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import model.Catalog
import model.DeckEntryMatch
import model.LogEntry
import model.Preferences
import state.MviPlatformServices
import java.awt.Desktop

/**
 * Desktop implementation of MVI platform services.
 * Provides platform-specific operations for the MVI ViewModel.
 */
class DesktopMviPlatformServices(
    private val database: Database
) : MviPlatformServices {

    private val remoteCatalogDataSource = RemoteCatalogDataSource()

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.IO) {
            remoteCatalogDataSource.load(forceRefresh = true, log = log)
        }
    }

    override suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        withContext(Dispatchers.IO) {
            // Get current preferences from database
            val currentPrefs = database.observePreferences().first() ?: Preferences()
            
            // Apply update
            val newPrefs = update(currentPrefs)
            
            // Save back to database
            database.insertPreferences(newPrefs)
        }
    }

    override suspend fun addLog(log: LogEntry) {
        withContext(Dispatchers.IO) {
            database.insertLog(log)
            
            // Clean up old logs to prevent database bloat
            database.deleteOldLogs(keepCount = 1000L)
        }
    }

    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val path = CsvExporter.export(matches)
            onComplete(path.toString())

            // Open the file with the default application on desktop
            // On mobile, the caller should use copyToClipboard() instead
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(path.toFile())
                }
            } catch (e: Exception) {
                // Log handled by caller
            }
        }
    }

    override suspend fun exportWizardResults(
        matches: List<DeckEntryMatch>,
        onComplete: (foundPath: String?, unfoundPath: String?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val result = CsvExporter.exportWizardResults(matches)
            onComplete(
                result.foundCardsPath?.toString(),
                result.unfoundCardsPath?.toString()
            )

            // Open both files with the default application
            try {
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    result.foundCardsPath?.let { path ->
                        desktop.open(path.toFile())
                    }
                    result.unfoundCardsPath?.let { path ->
                        desktop.open(path.toFile())
                    }
                }
            } catch (e: Exception) {
                // Log handled by caller
            }
        }
    }

    override suspend fun copyToClipboard(text: String) {
        withContext(Dispatchers.IO) {
            // On desktop, we can use AWT Toolkit
            try {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val stringSelection = java.awt.datatransfer.StringSelection(text)
                clipboard.setContents(stringSelection, null)
            } catch (e: Exception) {
                // Log handled by caller
            }
        }
    }
}
