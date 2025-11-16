package platform

import database.Database
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
 * - Catalog fetching from cached database
 * - Preferences management via SQLDelight database
 * - Logging operations with automatic cleanup
 * - CSV export with clipboard integration
 */
class IosMviPlatformServices(
    private val database: Database
) : MviPlatformServices {

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.Default) {
            try {
                log("Loading catalog from database...")
                
                // Get catalog from database
                val catalog = database.observeCatalog().first()
                if (catalog.variants.isNotEmpty()) {
                    log("Loaded catalog with ${catalog.variants.size} variants")
                    return@withContext catalog
                }
                
                log("No catalog found in database. Use desktop version to fetch and cache catalog data.")
                null
            } catch (e: Exception) {
                log("Error loading catalog: ${e.message}")
                null
            }
        }
    }

    override suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        withContext(Dispatchers.Default) {
            val currentPrefs = database.observePreferences().first() ?: Preferences()
            val newPrefs = update(currentPrefs)
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
                val csvContent = generateCsvContent(matches)
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

    /**
     * Generate CSV content from matched deck entries.
     * Format includes card details and summary statistics.
     */
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
                val priceStr = formatDecimal(priceCents / 100.0, 2)
                
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
        sb.appendLine("Total Price,${formatDecimal(totalPriceCents / 100.0, 2)}")
        
        return sb.toString()
    }
}


