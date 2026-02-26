package catalog

import model.Catalog
import model.VariantType

/**
 * Shared utility functions for catalog parsing and data normalization.
 *
 * These utilities were previously duplicated across KtorRemoteCatalogDataSource
 * and the desktop RemoteCatalogDataSource. Consolidated here to eliminate DRY violations.
 */
object CatalogUtils {

    /**
     * Replace zero/negative prices in a catalog with the default price for each variant type.
     */
    fun fillZeroPrices(catalog: Catalog): Catalog {
        val updated = catalog.variants.map { v ->
            if (v.priceInCents <= 0) {
                v.copy(priceInCents = v.variantType.defaultPriceInCents)
            } else {
                v
            }
        }
        return Catalog(updated)
    }

    /**
     * Normalize a raw price map so that keys are canonical VariantType display names.
     * When multiple raw keys resolve to the same VariantType, the highest price wins.
     */
    fun canonicalizePriceMap(map: Map<String, Double>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for ((k, v) in map) {
            val t = VariantType.fromString(k).displayName
            val existing = out[t]
            if (existing == null || v > existing) out[t] = v
        }
        return out
    }

    /**
     * Build a fallback price map from VariantType defaults (dollars, not cents).
     */
    fun jsPriceMapFallback(): Map<String, Double> = VariantType.entries.associate {
        it.displayName to it.defaultPriceInCents / 100.0
    }

    /**
     * Extract the CARD_TYPE_PRICES JavaScript object literal from an HTML page.
     * Returns a mapping of type name to dollar price.
     */
    fun extractTypePrices(html: String): Map<String, Double> {
        val regex = Regex("CARD_TYPE_PRICES\\s*=\\s*\\{([^}]+)\\}")
        val match = regex.find(html) ?: return emptyMap()
        val body = match.groupValues[1]
        return body.split(',').mapNotNull { entry ->
            val parts = entry.split(':').map { it.trim() }
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"', '\'')
                val value = parts[1].replace("[^0-9.]".toRegex(), "").toDoubleOrNull()
                if (key.isNotBlank() && value != null) key to value else null
            } else null
        }.toMap()
    }

    /**
     * Extract an EXAMPLE_CSV multi-line JavaScript string assignment from HTML, if present.
     */
    fun extractExampleCsv(html: String): String? {
        val regex = Regex("EXAMPLE_CSV\\s*=\\s*`([\\s\\S]*?)`")
        val match = regex.find(html) ?: return null
        return match.groupValues[1].trim()
    }
}
