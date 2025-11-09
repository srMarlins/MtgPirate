package export

import model.DeckEntryMatch
import platform.AppDirectories
import util.formatPrice
import java.nio.file.Path
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ExportResult(
    val foundCardsPath: Path?,
    val unfoundCardsPath: Path?
)

object CsvExporter {
    private val header = "Card Name,Set,SKU,Card Type,Quantity,Base Price"

    fun export(matches: List<DeckEntryMatch>, target: Path? = null): Path {
        val file = target ?: AppDirectories.exportsDir.resolve("export-${timestamp()}.csv")
        val resolved = matches.filter { it.selectedVariant != null }
        // Aggregate identical selected variants
        val grouped = resolved.groupBy {
            val variant = it.selectedVariant!!
            Triple(variant.nameOriginal, variant.setCode, variant.sku)
        }
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

    fun exportWizardResults(matches: List<DeckEntryMatch>): ExportResult {
        val timestamp = timestamp()

        // Create found cards file
        val resolved = matches.filter { it.selectedVariant != null }
        val foundCardsPath = if (resolved.isNotEmpty()) {
            val file = AppDirectories.exportsDir.resolve("found-cards-${timestamp}.csv")
            val lines = mutableListOf<String>()
            lines += header

            // Aggregate identical selected variants
            val grouped = resolved.groupBy {
                val variant = it.selectedVariant!!
                Triple(variant.nameOriginal, variant.setCode, variant.sku)
            }
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
            lines += "--- Summary ---"
            val regular = resolved.count { it.selectedVariant!!.variantType.equals("Regular", true) }
            val holo = resolved.count { it.selectedVariant!!.variantType.equals("Holo", true) }
            val foil = resolved.count { it.selectedVariant!!.variantType.equals("Foil", true) }
            val totalCents = resolved.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }
            lines += "Regular Cards,$regular"
            lines += "Holo Cards,$holo"
            lines += "Foil Cards,$foil"
            lines += "Total Price,${formatPrice(totalCents)}"

            Files.write(file, lines)
            file
        } else {
            null
        }

        // Create unfound cards file
        val unfound = matches.filter { it.selectedVariant == null && it.deckEntry.include }
        val unfoundCardsPath = if (unfound.isNotEmpty()) {
            val file = AppDirectories.exportsDir.resolve("unfound-cards-${timestamp}.txt")
            val lines = mutableListOf<String>()
            unfound.forEach { match ->
                lines += "${match.deckEntry.qty} ${match.deckEntry.cardName}"
            }

            Files.write(file, lines)
            file
        } else {
            null
        }

        return ExportResult(foundCardsPath, unfoundCardsPath)
    }

    private fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
}

