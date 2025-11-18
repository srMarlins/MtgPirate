package platform

import catalog.CatalogCsvParser
import catalog.KtorRemoteCatalogDataSource
import catalog.ScryfallImageEnricher
import database.Database
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import model.Catalog
import model.CardVariant
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
 * - Live Activity integration for Dynamic Island support
 */
class IosMviPlatformServices(
    private val database: Database
) : MviPlatformServices {

    private val httpClient = HttpClient(Darwin) {
        install(Logging) { level = LogLevel.INFO }
    }
    
    // Live Activity service for Dynamic Island integration
    val liveActivityService = LiveActivityService()

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
                // Generate CSV content
                val csvContent = generateCsvContent(matches)

                // Copy to clipboard for sharing
                // In a production iOS app, this would use the UIActivityViewController
                // to present sharing options (Files app, email, etc.)
                copyToClipboard(csvContent)

                onComplete("CSV copied to clipboard (${matches.size} cards)")
            } catch (e: Exception) {
                onComplete("Export failed: ${e.message}")
            }
        }
    }

    override suspend fun copyToClipboard(text: String) {
        withContext(Dispatchers.Default) {
            platform.copyToClipboard(text)
        }
    }

    // Helper functions

    private fun generateCsvContent(matches: List<DeckEntryMatch>): String {
        val sb = StringBuilder()
        sb.appendLine("Card Name,Set,SKU,Card Type,Quantity,Base Price")

        var regularCount = 0
        var holoCount = 0
        var foilCount = 0
        var totalPriceCents = 0

        matches.forEach { match ->
            val variant = match.selectedVariant
            if (variant != null) {
                val qty = match.deckEntry.qty
                val priceCents = variant.priceInCents
                val priceStr = platform.formatDecimal(priceCents / 100.0, 2)

                sb.appendLine("${variant.nameOriginal},${variant.setCode},${variant.sku},${variant.variantType},$qty,$priceStr")

                when (variant.variantType) {
                    "Regular" -> regularCount += qty
                    "Holo" -> holoCount += qty
                    "Foil" -> foilCount += qty
                }
                totalPriceCents += priceCents * qty
            }
        }

        sb.appendLine()
        sb.appendLine("--- Summary ---")
        sb.appendLine("Regular Cards,$regularCount")
        sb.appendLine("Holo Cards,$holoCount")
        sb.appendLine("Foil Cards,$foilCount")
        sb.appendLine("Total Price,${platform.formatDecimal(totalPriceCents / 100.0, 2)}")

        return sb.toString()
    }
}
