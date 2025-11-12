package catalog

import match.NameNormalizer
import model.CardVariant
import model.Catalog

/**
 * Parse the upstream CSV format used by the React inventory app (headers: SKU, Card Name, Set, Card Type, ...).
 * Handles a collapsed single-line format by inserting newlines before each SKU token.
 * Now supports:
 *  - Proper handling of commas inside quoted card names
 *  - Heuristic merging when names contain unquoted commas (legacy export) causing column shifts
 *  - Basic HTML tag stripping inside cells
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
        "price" to "price",
        "collector number" to "collectornumber",
        "collector" to "collectornumber",
        "number" to "collectornumber"
    )

    private val typePriceMapDefault = mapOf(
        "Regular" to 2.2,
        "Holo" to 3.0,
        "Foil" to 3.5
    )

    private val htmlTagRegex = Regex("<[^>]+>")
    private val setCodeRegex = Regex("^[A-Z0-9]{2,5}$")

    /** Canonicalize various representations of type into one of Regular/Holo/Foil */
    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
    }

    private fun isTypeCell(raw: String): Boolean {
        val t = raw.lowercase()
        return t.contains("foil") || t.contains("holo") || t.contains("regular")
    }

    /**
     * @param typePriceMap mapping from variant type (e.g., Regular/Holo/Foil) to a dollar price used when
     *                      the CSV does not include a Price/Base Price column.
     */
    fun parse(csv: String, typePriceMap: Map<String, Double> = emptyMap()): Catalog {
            // DEBUG: Print values before skip
            // (moved inside forEach below)
        val preprocessed = preprocess(csv)
        val rawLines = preprocessed.trim().split('\n').filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return Catalog(emptyList())
        val headerLine = rawLines.first()
        val headerCells = parseCsvLine(headerLine).map { normalizeHeader(it) }
        val idxSku = headerCells.indexOf("sku")
        val idxName = headerCells.indexOf("name")
        val idxSet = headerCells.indexOf("set")
        val idxType = headerCells.indexOf("type")
        val idxPrice = headerCells.indexOf("price")
        val idxCollectorNumber = headerCells.indexOf("collectornumber")
        val required = listOf(idxSku, idxName, idxSet, idxType)
        if (required.any { it < 0 }) return Catalog(emptyList())
        val variants = mutableListOf<CardVariant>()
        val mapLower = typePriceMap.mapKeys { it.key.trim().lowercase() }
        val defaultsLower = typePriceMapDefault.mapKeys { it.key.trim().lowercase() }
        val collectorNumberFromNameRegex = Regex(" #([0-9A-Za-z]+)$")
    rawLines.drop(1).forEach { raw ->
            if (raw.isBlank()) return@forEach
            val parsedCells = parseCsvLine(raw)
            val cells = sanitizeCells(parsedCells)
            val aligned = alignRow(cells, headerCells, idxSku, idxName, idxSet, idxType, idxPrice)
            if (aligned.size < headerCells.size) return@forEach
            val sku = aligned[idxSku].trim()
            var name = aligned[idxName].trim()
            var set = aligned[idxSet].trim()
            val typeRaw = aligned[idxType].trim()
            val type = canonicalType(typeRaw)
            // Extract set code from name if present at end (e.g., 'An Offer You Can't Refuse SLP')
            var setCodeFromName: String? = null
            val setCodeMatch = Regex(" ([A-Z0-9]{2,5})$").find(name)
            if (setCodeMatch != null) {
                val possibleSet = setCodeMatch.groupValues[1]
                if (set.isBlank() || set.equals(possibleSet, ignoreCase = true)) {
                    set = possibleSet
                    name = name.removeSuffix(" $possibleSet").trim()
                }
                setCodeFromName = possibleSet
            }
            // DEBUG: Print values before skip
            println("DEBUG: sku='$sku', name='$name', set='$set', setCodeFromName='$setCodeFromName'")
            // Extract collector number from name if present
            var collectorNumberFromName: String? = null
            val match = collectorNumberFromNameRegex.find(name)
            if (match != null) {
                collectorNumberFromName = match.groupValues[1]
                name = name.removeSuffix(" #${collectorNumberFromName}").trim()
            }
            val collectorNumber = if (idxCollectorNumber >= 0 && idxCollectorNumber < aligned.size) {
                aligned[idxCollectorNumber].trim().takeIf { it.isNotBlank() }
            } else collectorNumberFromName
            // Fallback: if set is still blank but setCodeFromName is available, use it
            if (set.isBlank() && !setCodeFromName.isNullOrBlank()) {
                set = setCodeFromName
            }
            // DEBUG: Print values before skip
            println("DEBUG: sku='$sku', name='$name', set='$set', setCodeFromName='$setCodeFromName'")
            // Only skip if SKU or name are blank after all extraction
            if (sku.isBlank() || name.isBlank() || set.isBlank()) return@forEach
            val dollarsFromCell = if (idxPrice >= 0 && idxPrice < aligned.size) parsePriceCell(aligned[idxPrice]) else 0.0
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
                priceInCents = priceCents,
                collectorNumber = collectorNumber
            )
        }
        return Catalog(variants)
    }

    // Attempt to realign a row if commas inside name caused extra cells (unquoted)
    private fun alignRow(cells: List<String>, headers: List<String>, idxSku: Int, idxName: Int, idxSet: Int, idxType: Int, idxPrice: Int): List<String> {
        if (cells.size == headers.size) return cells
        // Heuristic only if we have more cells than headers and indexes are in expected order
        if (cells.size > headers.size && idxSku >= 0 && idxName >= 0 && idxSet >= 0 && idxType >= 0) {
            val sku = cells[idxSku]
            // Start scanning after supposed name start to find set code followed by type
            val startNameIdx = idxName
            val searchStart = startNameIdx
            var foundSetIdx: Int? = null
            for (i in searchStart until cells.size) {
                val candidate = cells[i].trim()
                if (candidate.isNotEmpty() && setCodeRegex.matches(candidate)) {
                    val next = cells.getOrNull(i + 1)?.trim().orEmpty()
                    if (isTypeCell(next)) {
                        foundSetIdx = i
                        break
                    }
                }
            }
            if (foundSetIdx != null) {
                val nameParts = cells.subList(startNameIdx, foundSetIdx)
                val name = nameParts.joinToString(",").replace("\\s+".toRegex(), " ").trim()
                val set = cells[foundSetIdx].trim()
                val type = cells.getOrNull(foundSetIdx + 1)?.trim().orEmpty()
                val price = if (idxPrice >= 0) cells.getOrNull(foundSetIdx + 2)?.trim().orEmpty() else ""
                // Build aligned list preserving header order
                val aligned = MutableList(headers.size) { "" }
                aligned[idxSku] = sku
                aligned[idxName] = name
                aligned[idxSet] = set
                aligned[idxType] = type
                if (idxPrice >= 0) aligned[idxPrice] = price
                return aligned
            }
        }
        // Fallback: return original cells (may lead to incorrect parsing but avoids crash)
        return cells
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

    // Basic CSV line parser supporting quoted fields
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString()
        return result
    }

    private fun sanitizeCells(cells: List<String>): List<String> = cells.map { cell ->
        var c = cell.trim()
        c = htmlTagRegex.replace(c, " ")
        c = c.replace("&nbsp;", " ", ignoreCase = true)
        c = c.replace("&amp;", "&", ignoreCase = true)
        c = c.replace("\\s+".toRegex(), " ").trim()
        c
    }
}
