package deck

import model.DeckEntry
import model.Section

object DecklistParser {
    private val qtyRegex = Regex("""^(?:SB:)?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val setParenRegex = Regex("""\(([^()]+)\)\s*$""")
    private val collectorRegex = Regex("""^(?<set>[A-Za-z0-9]{2,5})(?:[\s-]+(?<num>\d+[a-zA-Z]?))?$""")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val setLabelRegex = Regex("(?i)\\bSet: *([A-Za-z0-9]{2,5})\\b")
    private val cardNameLabelRegex = Regex("(?i)^Card Name: *")

    fun parse(text: String, includeSideboard: Boolean, includeCommanders: Boolean): List<DeckEntry> {
        val lines = text.lines()
        var section: Section = Section.MAIN
        var sawSideboard = false
        var blankAfterSideboard = false
        val entries = mutableListOf<DeckEntry>()
        for (raw in lines) {
            val lineOriginal = raw
            var line = raw.trim()
            if (line.isEmpty()) {
                if (sawSideboard) blankAfterSideboard = true
                continue
            }
            // Strip HTML tags globally
            line = htmlTagRegex.replace(line, " ")
            line = line.replace("&nbsp;", " ", ignoreCase = true)
            line = line.replace("&amp;", "&", ignoreCase = true)
            line = line.replace("\u00A0", " ") // non-breaking space
            line = line.replace("\\s+".toRegex(), " ").trim()

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

            // Remove leading Card Name: label if present
            remainder = cardNameLabelRegex.replace(remainder, "")

            // First extract explicit Set: label anywhere (store, then remove)
            var setCodeHint: String? = null
            val setLabelMatch = setLabelRegex.find(remainder)
            if (setLabelMatch != null) {
                setCodeHint = setLabelMatch.groupValues[1].uppercase()
                // Remove the label segment so it doesn't pollute card name
                val range = setLabelMatch.range
                remainder = (remainder.substring(0, range.first) + remainder.substring(range.last + 1)).trim()
            }

            // Extract trailing parenthetical for set code / collector number (takes precedence if present)
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
                    val token = rawSetHint.uppercase()
                    if (token.length in 2..5 && token.matches(Regex("[A-Z0-9]+"))) {
                        setCodeHint = token
                    }
                }
                remainder = remainder.removeRange(parenMatch.range).trim()
            }

            remainder = remainder.substringBefore(" - ").trim()
            // Collapse extra spaces & stray commas spacing introduced by sanitization
            remainder = remainder.replace("\\s+".toRegex(), " ").trim().trim(',').trim()
            // If remainder still contains an embedded " Set " fragment (without colon) at end due to partial copy, attempt to remove
            remainder = remainder.replace(Regex("(?i) Set$"), "").trim()

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
