package match

// Multiplatform-safe name normalization with Unicode diacritic stripping
object NameNormalizer {
    private val PUNCTUATION = "[,'`\u2019]".toRegex()
    private val DASHES = "[-\u2013\u2014]".toRegex()
    private val QUOTES = "[\"]".toRegex()
    private val NON_ALNUM = "[^a-z0-9 ]".toRegex()
    private val WHITESPACE = "\\s+".toRegex()

    fun normalize(raw: String): String {
        // Strip diacritics first so accented characters become ASCII base letters
        val stripped = stripDiacritics(raw)
        val lower = stripped.lowercase()
        val replaced = lower
            .replace(PUNCTUATION, "")
            .replace(DASHES, " ")
            .replace(QUOTES, "")
        val primary = replaced.split(" // ").first()
        return primary.replace(NON_ALNUM, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }
}
