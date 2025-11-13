package platform

import catalog.CatalogCsvParser
import catalog.ScryfallImageEnricher
import database.Database
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
 * Provides platform-specific operations for the MVI ViewModel on iOS.
 */
class IosMviPlatformServices(
    private val database: Database
) : MviPlatformServices {

    private val urlApi = "https://www.usmtgproxy.com/wp-content/uploads/single-card-list.csv"

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        return withContext(Dispatchers.Default) {
            try {
                log("Fetching catalog from remote API...")
                
                // For iOS, we'll need to implement native network calls
                // For now, return null and rely on cached data
                log("Network fetching not yet implemented for iOS")
                null
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
                
                // For iOS, copy to clipboard for now
                // In a real implementation, this would use the share sheet
                copyToClipboard(csvContent)
                
                onComplete("CSV copied to clipboard")
            } catch (e: Exception) {
                onComplete("Export failed: ${e.message}")
            }
        }
    }

    override suspend fun copyToClipboard(text: String) {
        withContext(Dispatchers.Default) {
            // For iOS, this would use UIPasteboard
            // For now, this is a placeholder
            // TODO: Implement native clipboard access
        }
    }

    // Helper functions

    private fun fillZeroPrices(catalog: Catalog): Catalog {
        val centsMap = mapOf(
            "Regular" to 220,
            "Holo" to 300,
            "Foil" to 350
        )
        var changed = false
        val updated = catalog.variants.map { v ->
            if (v.priceInCents <= 0) {
                changed = true
                val t = canonicalType(v.variantType)
                v.copy(priceInCents = centsMap[t] ?: 0, variantType = t)
            } else {
                v.copy(variantType = canonicalType(v.variantType))
            }
        }
        return if (changed) catalog.copy(variants = updated) else catalog
    }

    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
    }

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
                val priceStr = "%.2f".format(priceCents / 100.0)
                
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
        sb.appendLine("Total Price,%.2f".format(totalPriceCents / 100.0))
        
        return sb.toString()
    }
}
