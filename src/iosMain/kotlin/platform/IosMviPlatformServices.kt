package platform

import catalog.KtorRemoteCatalogDataSource
import database.Database
import export.CsvGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import model.Catalog
import model.DeckEntryMatch
import model.LogEntry
import model.Preferences
import state.MviPlatformServices

/**
 * iOS implementation of MVI platform services.
 * 
 * Provides platform-specific operations for the MVI ViewModel on iOS, including:
 * - Catalog fetching via Ktor-based remote data source
 * - Preferences management via SQLDelight database
 * - Logging operations
 * - CSV export (copies to clipboard for sharing)
 */
class IosMviPlatformServices(
    private val database: Database
) : MviPlatformServices {

    private val httpClient = HttpClient(Darwin) {
        install(Logging) { level = LogLevel.INFO }
    }

    /**
     * Close the underlying HttpClient to release resources.
     * Should be called when the services are no longer needed.
     */
    fun close() {
        httpClient.close()
    }

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.Default) {
            try {
                log("Fetching catalog from remote API (iOS)...")
                val ds = KtorRemoteCatalogDataSource(httpClient)
                val catalog = ds.load(forceRefresh = true, log = log)
                if (catalog == null) log("Catalog fetch returned null")
                catalog
            } catch (e: Exception) {
                log("Error loading catalog: ${e.message}")
                null
            }
        }
    }

    override suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        withContext(Dispatchers.Default) {
            // Get current preferences from database
            val currentPrefs = database.observePreferences().first() ?: Preferences()

            // Apply update
            val newPrefs = update(currentPrefs)

            // Save back to database
            database.insertPreferences(newPrefs)
        }
    }

    override suspend fun addLog(log: LogEntry) {
        withContext(Dispatchers.Default) {
            database.insertLog(log)

            // Clean up old logs to prevent database bloat
            database.deleteOldLogs(keepCount = 1000L)
        }
    }

    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        withContext(Dispatchers.Default) {
            try {
                val csvContent = CsvGenerator.generateFoundCardsCsv(matches)
                copyToClipboard(csvContent)
                onComplete("CSV copied to clipboard (${matches.size} cards)")
            } catch (e: Exception) {
                onComplete("Export failed: ${e.message}")
            }
        }
    }

    override suspend fun exportWizardResults(
        matches: List<DeckEntryMatch>,
        onComplete: (foundPath: String?, unfoundPath: String?) -> Unit
    ) {
        withContext(Dispatchers.Default) {
            try {
                val foundCsv = CsvGenerator.generateFoundCardsCsv(matches)
                val unfoundTxt = CsvGenerator.generateUnfoundCardsTxt(matches)

                // Copy found cards to clipboard
                if (foundCsv.isNotEmpty()) {
                    copyToClipboard(foundCsv)
                }

                onComplete(
                    if (foundCsv.isNotEmpty()) "Found cards CSV copied to clipboard" else null,
                    if (unfoundTxt.isNotEmpty()) "Unfound cards available" else null
                )
            } catch (e: Exception) {
                onComplete(null, "Export failed: ${e.message}")
            }
        }
    }

    override suspend fun copyToClipboard(text: String) {
        withContext(Dispatchers.Main) {
            platform.copyToClipboard(text)
        }
    }

    override suspend fun openUrl(url: String) {
        withContext(Dispatchers.Main) {
            try {
                val nsUrl = platform.Foundation.NSURL(string = url) ?: return@withContext
                platform.UIKit.UIApplication.sharedApplication.openURL(nsUrl)
            } catch (e: Exception) {
                // Log handled by caller
            }
        }
    }

}
