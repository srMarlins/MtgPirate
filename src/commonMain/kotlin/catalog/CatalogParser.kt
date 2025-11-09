package catalog

import com.fleeksoft.ksoup.Ksoup
import model.CardVariant
import model.Catalog
import match.NameNormalizer

object CatalogParser {
    private val headerAliases = mapOf(
        "cardname" to "name",
        "name" to "name",
        "set" to "set",
        "sku" to "sku",
        "cardtype" to "type",
        "type" to "type",
        "price" to "price",
        "baseprice" to "price"
    )

    private val typePriceMap = mapOf(
        "Regular" to 220,
        "Holo" to 300,
        "Foil" to 350
    )

    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
    }

    fun parse(html: String): Catalog {
        val doc = Ksoup.parse(html)
        val cardDivs = doc.select("div.bg-gray-50")
        if (cardDivs.isNotEmpty()) {
            val variants = cardDivs.mapNotNull { div ->
                fun extractValue(label: String): String? {
                    val p = div.selectFirst("p:has(strong:containsOwn($label))") ?: return null
                    val strong = p.selectFirst("strong:containsOwn($label)") ?: return null
                    // Find the text node or element immediately after <strong>
                    val siblings = strong.parent()?.childNodes() ?: return null
                    val idx = siblings.indexOf(strong)
                    // Look for the next text node or element after <strong>
                    for (i in idx + 1 until siblings.size) {
                        val node = siblings[i]
                        val text = node.outerHtml().trim()
                        if (text.isNotEmpty() && text != ":") {
                            return text.removePrefix(":").trim()
                        }
                    }
                    // Fallback: try to extract from p.text() as before
                    val allText = p.text().trim()
                    val labelText = strong.text().trim()
                    return allText.removePrefix(labelText).removePrefix(":").trim()
                }
                val sku = extractValue("SKU:") ?: return@mapNotNull null
                val name = extractValue("Card Name:") ?: return@mapNotNull null
                val set = extractValue("Set:") ?: return@mapNotNull null
                val typeRaw = extractValue("Card Type:") ?: return@mapNotNull null
                val type = canonicalType(typeRaw)
                val priceRaw = extractValue("Base Price:") ?: ""
                var priceCents = parsePrice(priceRaw)
                if (priceCents == 0) priceCents = typePriceMap[type] ?: 0
                val normalized = NameNormalizer.normalize(name)
                CardVariant(
                    nameOriginal = name,
                    nameNormalized = normalized,
                    setCode = set,
                    sku = sku,
                    variantType = type,
                    priceInCents = priceCents
                )
            }
            return Catalog(variants)
        }
        val tables = doc.getElementsByTag("table")
        val table = tables.maxByOrNull { it.getElementsByTag("tr").size }
            ?: throw IllegalStateException("No table found in catalog HTML")
        val rows = table.getElementsByTag("tr")
        if (rows.isEmpty()) return Catalog(emptyList())
        val headerRow = rows.firstOrNull() ?: return Catalog(emptyList())
        val headerCells = headerRow.children()
        val headers = headerCells.map { normalizeHeader(it.text()) }
        val nameIdx = headers.indexOf("name")
        val setIdx = headers.indexOf("set")
        val skuIdx = headers.indexOf("sku")
        val typeIdx = headers.indexOf("type")
        val priceIdx = headers.indexOf("price")
        if (listOf(nameIdx, setIdx, skuIdx, typeIdx, priceIdx).any { it < 0 }) {
            throw IllegalStateException("Missing required headers in catalog table: $headers")
        }
        val variants = mutableListOf<CardVariant>()
        rows.drop(1).forEach { row ->
            val cells = row.children()
            if (cells.size < headers.size) return@forEach
            val name = cells[nameIdx].text().trim()
            val set = cells[setIdx].text().trim()
            val sku = cells[skuIdx].text().trim()
            val typeRaw = cells[typeIdx].text().trim()
            val type = canonicalType(typeRaw)
            val priceRaw = cells[priceIdx].text()
            if (name.isEmpty() || sku.isEmpty()) return@forEach
            var priceCents = parsePrice(priceRaw)
            if (priceCents == 0) priceCents = typePriceMap[type] ?: 0
            val normalized = NameNormalizer.normalize(name)
            variants += CardVariant(
                nameOriginal = name,
                nameNormalized = normalized,
                setCode = set,
                sku = sku,
                variantType = type,
                priceInCents = priceCents
            )
        }
        val dedup = variants.groupBy { Triple(it.nameNormalized, it.setCode, it.variantType) }
            .values.map { group ->
                val nonZero = group.filter { it.priceInCents > 0 }
                nonZero.minByOrNull { it.priceInCents } ?: group.first()
            }
        return Catalog(dedup)
    }

    private fun normalizeHeader(h: String): String {
        val compact = h.lowercase().replace("[^a-z]".toRegex(), "")
        return headerAliases[compact] ?: compact
    }

    private fun parsePrice(raw: String): Int {
        val cleaned = raw.replace("[^0-9.]".toRegex(), "")
        if (cleaned.isBlank()) return 0
        return try {
            (cleaned.toDouble() * 100).toInt()
        } catch (_: NumberFormatException) {
            0
        }
    }
}
