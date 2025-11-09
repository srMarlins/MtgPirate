package match

// Multiplatform-safe name normalization (simplified, no Unicode decomposition)
object NameNormalizer {
    fun normalize(raw: String): String {
        val lower = raw.lowercase()
        val replaced = lower
            .replace("[,'`’]".toRegex(), "")
            .replace("[-–—]".toRegex(), " ")
            .replace("[\"]".toRegex(), "")
        val primary = replaced.split(" // ").first()
        return primary.replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
