package export

import model.DeckEntryMatch
import util.formatPrice
import java.nio.file.Path
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val header = "Card Name,Set,SKU,Card Type,Quantity,Base Price"

    fun export(matches: List<DeckEntryMatch>, target: Path? = null): Path {
        val file = target ?: Path.of("export-${timestamp()}.csv")
        val resolved = matches.filter { it.selectedVariant != null }
        // Aggregate identical selected variants
        val grouped = resolved.groupBy { Triple(it.selectedVariant!!.nameOriginal, it.selectedVariant!!.setCode, it.selectedVariant!!.sku) }
        val lines = mutableListOf<String>()
        lines += header
        grouped.values.forEach { group ->
            val first = group.first().selectedVariant!!
            val qtyTotal = group.sumOf { it.deckEntry.qty }
            lines += listOf(
                first.nameOriginal,
                first.setCode,
                first.sku,
                first.variantType,
                qtyTotal.toString(),
                formatPrice(first.priceInCents)
            ).joinToString(",")
        }
        lines += ""
        // Summary
        val regular = resolved.count { it.selectedVariant!!.variantType.equals("Regular", true) }
        val holo = resolved.count { it.selectedVariant!!.variantType.equals("Holo", true) }
        val foil = resolved.count { it.selectedVariant!!.variantType.equals("Foil", true) }
        val totalCents = resolved.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }
        lines += "--- Summary ---"
        lines += "Regular Cards,$regular"
        lines += "Holo Cards,$holo"
        lines += "Foil Cards,$foil"
        lines += "Total Price,${formatPrice(totalCents)}"
        Files.write(file, lines)
        return file
    }

    private fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
}

