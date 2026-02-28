package platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import catalog.KtorRemoteCatalogDataSource
import database.Database
import export.CsvGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import model.Catalog
import model.DeckEntryMatch
import model.LogEntry
import model.Preferences
import state.MviPlatformServices

/**
 * Android implementation of MVI platform services.
 *
 * Provides platform-specific operations for the MVI ViewModel on Android, including:
 * - Catalog fetching via Ktor with OkHttp engine
 * - Preferences management via SQLDelight database
 * - Logging operations
 * - CSV export (copies to clipboard for sharing)
 * - URL opening via Intent.ACTION_VIEW
 */
class AndroidMviPlatformServices(
    private val database: Database,
    private val context: Context
) : MviPlatformServices {

    private val httpClient = HttpClient(OkHttp) {
        install(Logging) { level = LogLevel.INFO }
    }

    fun close() {
        httpClient.close()
    }

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.IO) {
            try {
                log("Fetching catalog from remote API (Android)...")
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
        withContext(Dispatchers.IO) {
            val currentPrefs = database.observePreferences().first() ?: Preferences()
            val newPrefs = update(currentPrefs)
            database.insertPreferences(newPrefs)
        }
    }

    override suspend fun addLog(log: LogEntry) {
        withContext(Dispatchers.IO) {
            database.insertLog(log)
            database.deleteOldLogs(keepCount = 1000L)
        }
    }

    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            try {
                val foundCsv = CsvGenerator.generateFoundCardsCsv(matches)
                val unfoundTxt = CsvGenerator.generateUnfoundCardsTxt(matches)

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
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                // URL opening failed
            }
        }
    }
}
