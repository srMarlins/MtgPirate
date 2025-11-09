package deck

import model.DeckEntry
import model.Section
import match.NameNormalizer

object DecklistParser {
    fun parse(text: String, includeSideboard: Boolean, includeCommanders: Boolean): List<DeckEntry> {
        val lines = text.lines()
        var section: Section = Section.MAIN
        val entries = mutableListOf<DeckEntry>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.equals("SIDEBOARD:", true)) {
                section = Section.SIDEBOARD
                continue
            }
            // Commander heuristic: if after sideboard blank separation? We'll treat named commanders later.
            val parts = line.split(" ", limit = 2)
            val qty = parts.firstOrNull()?.toIntOrNull()
            val name = if (parts.size > 1) parts[1].trim() else null
            if (qty == null || name.isNullOrEmpty()) continue
            val include = when (section) {
                Section.MAIN -> true
                Section.SIDEBOARD -> includeSideboard
                Section.COMMANDER -> includeCommanders
            }
            entries += DeckEntry(
                originalLine = line,
                qty = qty,
                cardName = name,
                section = section,
                include = include
            )
        }
        return entries
    }
}

