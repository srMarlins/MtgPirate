package export

import model.DeckEntryMatch
import model.VariantType
import platform.AppDirectories
import java.nio.file.Path
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ExportResult(
    val foundCardsPath: Path?,
    val unfoundCardsPath: Path?
)

/**
 * Desktop-only file I/O wrapper around the shared [CsvGenerator].
 *
 * All CSV content generation (aggregation, escaping, formatting) lives in
 * commonMain [CsvGenerator]. This class is responsible only for writing
 * that content to disk and opening files.
 */
object CsvExporter {

    fun export(matches: List<DeckEntryMatch>, target: Path? = null): Path {
        val file = target ?: AppDirectories.exportsDir.resolve("export-${timestamp()}.csv")
        val content = CsvGenerator.generateFoundCardsCsv(matches)
        Files.write(file, content.toByteArray())
        return file
    }

    fun exportWizardResults(matches: List<DeckEntryMatch>): ExportResult {
        val timestamp = timestamp()

        // Found cards
        val foundCsv = CsvGenerator.generateFoundCardsCsv(matches)
        val foundCardsPath = if (foundCsv.isNotEmpty()) {
            val file = AppDirectories.exportsDir.resolve("found-cards-${timestamp}.csv")
            Files.write(file, foundCsv.toByteArray())
            file
        } else {
            null
        }

        // Unfound cards
        val unfoundTxt = CsvGenerator.generateUnfoundCardsTxt(matches)
        val unfoundCardsPath = if (unfoundTxt.isNotEmpty()) {
            val file = AppDirectories.exportsDir.resolve("unfound-cards-${timestamp}.txt")
            Files.write(file, unfoundTxt.toByteArray())
            file
        } else {
            null
        }

        return ExportResult(foundCardsPath, unfoundCardsPath)
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
}
