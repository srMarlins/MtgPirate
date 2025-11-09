package deck

import model.DeckEntry
import model.Section

object DecklistParser {
    private val qtyRegex = Regex("""^(?:SB:)?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val setParenRegex = Regex("""\(([^()]+)\)\s*$""")
    private val collectorRegex = Regex("""^(?<set>[A-Za-z0-9]{2,5})(?:[\s-]+(?<num>\d+[a-zA-Z]?))?$""")

    fun parse(text: String, includeSideboard: Boolean, includeCommanders: Boolean): List<DeckEntry> {
        val lines = text.lines()
        var section: Section = Section.MAIN
        var sawSideboard = false
        var blankAfterSideboard = false
        val entries = mutableListOf<DeckEntry>()
        for (raw in lines) {
            val lineOriginal = raw
            val line = raw.trim()
            if (line.isEmpty()) {
                if (sawSideboard) blankAfterSideboard = true
                continue
            }
            if (line.equals("SIDEBOARD:", true)) {
                section = Section.SIDEBOARD
                sawSideboard = true
                blankAfterSideboard = false
                continue
            }
            // Heuristic: if we saw a blank line after sideboard marker and now a new card line, treat as commander section
            if (sawSideboard && blankAfterSideboard && section == Section.SIDEBOARD) {
                section = Section.COMMANDER
            }
            val qtyMatch = qtyRegex.find(line) ?: continue
            val qty = qtyMatch.groupValues[1].toInt()
            var remainder = line.substring(qtyMatch.range.last + 1).trim()
            // Extract trailing parenthetical for set code / collector number
            var setCodeHint: String? = null
            var collectorHint: String? = null
            var rawSetHint: String? = null
            val parenMatch = setParenRegex.find(remainder)
            if (parenMatch != null) {
                rawSetHint = parenMatch.groupValues[1].trim()
                val collectorMatch = collectorRegex.matchEntire(rawSetHint)
                if (collectorMatch != null) {
                    setCodeHint = collectorMatch.groups["set"]?.value?.uppercase()
                    collectorHint = collectorMatch.groups["num"]?.value
                } else {
                    // If not matching code+number pattern, attempt simple code extraction if looks like code
                    val token = rawSetHint.uppercase()
                    if (token.length in 2..5 && token.matches(Regex("[A-Z0-9]+"))) {
                        setCodeHint = token
                    }
                }
                // Remove the parenthetical segment from remainder for card name extraction
                remainder = remainder.removeRange(parenMatch.range).trim()
            }
            // Remove inline separators or variant markers like " - Foil" that user might type
            remainder = remainder.substringBefore(" - ").trim()
            val cardName = remainder.ifEmpty { continue }
            val include = when (section) {
                Section.MAIN -> true
                Section.SIDEBOARD -> includeSideboard
                Section.COMMANDER -> includeCommanders
            }
            entries += DeckEntry(
                originalLine = lineOriginal,
                qty = qty,
                cardName = cardName,
                section = section,
                include = include,
                setCodeHint = setCodeHint,
                collectorNumberHint = collectorHint,
                rawSetHint = rawSetHint
            )
        }
        return entries
    }
}
