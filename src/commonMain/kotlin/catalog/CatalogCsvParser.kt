package catalog

import match.NameNormalizer
import model.CardVariant
import model.Catalog

/**
 * Parse the upstream CSV format used by the React inventory app (headers: SKU, Card Name, Set, Card Type, ...).
 * Handles a collapsed single-line format by inserting newlines before each SKU token.
 */
object CatalogCsvParser {
    private val headerAliases = mapOf(
        "card name" to "name",
        "name" to "name",
        "sku" to "sku",
        "set" to "set",
        "card type" to "type",
        "type" to "type",
        "base price" to "price",
        "price" to "price"
    )

    private val typePriceMapDefault = mapOf(
        "Regular" to 2.2,
        "Holo" to 3.0,
        "Foil" to 3.5
    )

    /** Canonicalize various representations of type into one of Regular/Holo/Foil */
    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
    }

    /**
     * @param typePriceMap mapping from variant type (e.g., Regular/Holo/Foil) to a dollar price used when
     *                      the CSV does not include a Price/Base Price column.
     */
    fun parse(csv: String, typePriceMap: Map<String, Double> = emptyMap()): Catalog {
        val preprocessed = preprocess(csv)
        val lines = preprocessed.trim().split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return Catalog(emptyList())
        val headerLine = lines.first()
        val headers = headerLine.split(',').map { normalizeHeader(it) }
        val idxSku = headers.indexOf("sku")
        val idxName = headers.indexOf("name")
        val idxSet = headers.indexOf("set")
        val idxType = headers.indexOf("type")
        val idxPrice = headers.indexOf("price")
        val required = listOf(idxSku, idxName, idxSet, idxType)
        if (required.any { it < 0 }) return Catalog(emptyList())
        val variants = mutableListOf<CardVariant>()
        val mapLower = typePriceMap.mapKeys { it.key.trim().lowercase() }
        val defaultsLower = typePriceMapDefault.mapKeys { it.key.trim().lowercase() }
        lines.drop(1).forEach { raw ->
            val cells = raw.split(',')
            if (cells.size < headers.size) return@forEach
            val sku = cells[idxSku].trim()
            val name = cells[idxName].trim()
            val set = cells[idxSet].trim()
            val typeRaw = cells[idxType].trim()
            val type = canonicalType(typeRaw)
            if (sku.isBlank() || name.isBlank()) return@forEach
            val dollarsFromCell = if (idxPrice >= 0) parsePriceCell(cells[idxPrice]) else 0.0
            val priceDollars = if (dollarsFromCell > 0.0) dollarsFromCell else {
                val key = type.lowercase()
                mapLower[key] ?: defaultsLower[key] ?: 0.0
            }
            val priceCents = (priceDollars * 100.0).toInt()
            variants += CardVariant(
                nameOriginal = name,
                nameNormalized = NameNormalizer.normalize(name),
                setCode = set,
                sku = sku,
                variantType = type,
                priceInCents = priceCents
            )
        }
        return Catalog(variants)
    }

    private fun preprocess(raw: String): String {
        var s = raw.replace("\r", "")
        val headerJoin = Regex("""(Card Type)\s+(SKU\d+,)""")
        s = headerJoin.replace(s) { m -> "${m.groupValues[1]}\n${m.groupValues[2]}" }
        val rowRegex = Regex("""\s+(SKU\d+,)""")
        s = rowRegex.replace(s) { m -> "\n${m.groupValues[1]}" }
        return s
    }

    private fun normalizeHeader(h: String): String {
        val compact = h.lowercase().trim()
        return headerAliases[compact] ?: compact
    }

    private fun parsePriceCell(raw: String): Double {
        val cleaned = raw.replace("[^0-9.]".toRegex(), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
