package platform

import catalog.CatalogFetcher
import export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Catalog
import model.DeckEntryMatch
import model.Preferences
import model.SavedImport
import persistence.PreferencesStore
import persistence.ImportsStore
import state.PlatformServices
import java.awt.Desktop

/**
 * Desktop-specific implementation of platform services.
 * Handles file I/O, catalog loading, and system integrations.
 */
class DesktopPlatformServices : PlatformServices {

    override suspend fun loadPreferences(): Preferences {
        return withContext(Dispatchers.IO) {
            PreferencesStore.load() ?: Preferences()
        }
    }

    override suspend fun savePreferences(preferences: Preferences) {
        withContext(Dispatchers.IO) {
            PreferencesStore.save(preferences)
        }
    }

    override suspend fun loadCatalog(forceRefresh: Boolean, log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.IO) {
            CatalogFetcher.load(forceRefresh = forceRefresh, log = log)
        }
    }

    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val path = CsvExporter.export(matches)
            onComplete(path.toString())

            // Open the file with the default application
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

    override suspend fun loadSavedImports(): List<SavedImport> {
        return withContext(Dispatchers.IO) {
            ImportsStore.loadAll()
        }
    }

    override suspend fun saveSavedImport(import: SavedImport) {
        withContext(Dispatchers.IO) {
            ImportsStore.add(import)
        }
    }

    override suspend fun deleteSavedImport(importId: String) {
        withContext(Dispatchers.IO) {
            ImportsStore.delete(importId)
        }
    }
}

