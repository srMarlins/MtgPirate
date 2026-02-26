package export

import model.DeckEntryMatch
import model.VariantType

/**
 * Shared CSV content generation for found and unfound card exports.
 *
 * Both Desktop and iOS delegate here so that aggregation logic,
 * price formatting, and field escaping are identical on every platform.
 * Platform code is responsible only for I/O (file writing, clipboard, share sheet).
 */
object CsvGenerator {

    private const val FOUND_HEADER = "Card Name,Set,SKU,Card Type,Quantity,Base Price"
    private val FORMULA_PREFIX_CHARS = charArrayOf('=', '+', '-', '@')

    /**
     * Escape a value for safe inclusion in a CSV cell.
     *
     * - Doubles internal quotes (`"` -> `""`)
     * - Prefixes formula-trigger characters with a tab to prevent spreadsheet injection
     * - Wraps the result in double-quotes
     */
    fun escapeCsvField(field: String): String {
        val escaped = field.replace("\"", "\"\"")
        val safe = if (escaped.isNotEmpty() && escaped.first() in FORMULA_PREFIX_CHARS) {
            "\t$escaped"
        } else {
            escaped
        }
        return "\"$safe\""
    }

    /**
     * Locale-independent price formatting.
     *
     * Always uses "." as the decimal separator so CSV output is safe
     * regardless of the device locale (fixes European-locale bug on iOS).
     */
    fun formatPriceCents(cents: Int): String {
        val dollars = cents / 100
        val remainder = cents % 100
        return "$${dollars}.${remainder.toString().padStart(2, '0')}"
    }

    // ------------------------------------------------------------------
    // Found-cards CSV
    // ------------------------------------------------------------------

    /**
     * Generate CSV content for all matched (found) cards.
     *
     * Identical variants (same name + set + SKU) are aggregated into a
     * single row with their quantities summed, matching the desktop
     * behaviour that was previously missing on iOS.
     */
    fun generateFoundCardsCsv(matches: List<DeckEntryMatch>): String {
        val resolved = matches.filter { it.selectedVariant != null }
        if (resolved.isEmpty()) return ""

        val lines = mutableListOf<String>()
        lines += FOUND_HEADER

        // Aggregate identical selected variants
        val grouped = resolved.groupBy { match ->
            val v = match.selectedVariant!!
            Triple(v.nameOriginal, v.setCode, v.sku)
        }

        grouped.values.forEach { group ->
            val first = group.first().selectedVariant!!
            val qtyTotal = group.sumOf { it.deckEntry.qty }
            lines += listOf(
                first.nameOriginal,
                first.setCode,
                first.sku,
                first.variantType.displayName,
                qtyTotal.toString(),
                formatPriceCents(first.priceInCents)
            ).joinToString(",") { escapeCsvField(it) }
        }

        // Summary section
        lines += ""
        val regular = resolved.count { it.selectedVariant!!.variantType == VariantType.REGULAR }
        val holo = resolved.count { it.selectedVariant!!.variantType == VariantType.HOLO }
        val foil = resolved.count { it.selectedVariant!!.variantType == VariantType.FOIL }
        val totalCents = resolved.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }

        lines += "${escapeCsvField("--- Summary ---")}"
        lines += "${escapeCsvField("Regular Cards")},${escapeCsvField(regular.toString())}"
        lines += "${escapeCsvField("Holo Cards")},${escapeCsvField(holo.toString())}"
        lines += "${escapeCsvField("Foil Cards")},${escapeCsvField(foil.toString())}"
        lines += "${escapeCsvField("Total Price")},${escapeCsvField(formatPriceCents(totalCents))}"

        return lines.joinToString("\n")
    }

    // ------------------------------------------------------------------
    // Unfound-cards text
    // ------------------------------------------------------------------

    /**
     * Generate plain-text content listing cards that could not be matched.
     *
     * Format: `<qty> <cardName>` per line (simple text, not CSV).
     */
    fun generateUnfoundCardsTxt(matches: List<DeckEntryMatch>): String {
        val unfound = matches.filter { it.selectedVariant == null && it.deckEntry.include }
        if (unfound.isEmpty()) return ""

        return unfound.joinToString("\n") { match ->
            "${match.deckEntry.qty} ${match.deckEntry.cardName}"
        }
    }
}
