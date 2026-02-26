package match

/**
 * iOS/Native implementation: manual replacement of common accented characters
 * found in Magic: The Gathering card names. This avoids reliance on JVM-only
 * regex Unicode categories (\p{Mn}) which are unavailable on Kotlin/Native.
 */
actual fun stripDiacritics(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        sb.append(DIACRITIC_MAP.getOrDefault(ch, ch))
    }
    return sb.toString()
}

private val DIACRITIC_MAP: Map<Char, Char> = mapOf(
    '\u00C0' to 'A', // À
    '\u00C1' to 'A', // Á
    '\u00C2' to 'A', // Â
    '\u00C3' to 'A', // Ã
    '\u00C4' to 'A', // Ä
    '\u00C5' to 'A', // Å
    '\u00C7' to 'C', // Ç
    '\u00C8' to 'E', // È
    '\u00C9' to 'E', // É
    '\u00CA' to 'E', // Ê
    '\u00CB' to 'E', // Ë
    '\u00CC' to 'I', // Ì
    '\u00CD' to 'I', // Í
    '\u00CE' to 'I', // Î
    '\u00CF' to 'I', // Ï
    '\u00D1' to 'N', // Ñ
    '\u00D2' to 'O', // Ò
    '\u00D3' to 'O', // Ó
    '\u00D4' to 'O', // Ô
    '\u00D5' to 'O', // Õ
    '\u00D6' to 'O', // Ö
    '\u00D9' to 'U', // Ù
    '\u00DA' to 'U', // Ú
    '\u00DB' to 'U', // Û
    '\u00DC' to 'U', // Ü
    '\u00DD' to 'Y', // Ý
    '\u00E0' to 'a', // à
    '\u00E1' to 'a', // á
    '\u00E2' to 'a', // â
    '\u00E3' to 'a', // ã
    '\u00E4' to 'a', // ä
    '\u00E5' to 'a', // å
    '\u00E7' to 'c', // ç
    '\u00E8' to 'e', // è
    '\u00E9' to 'e', // é
    '\u00EA' to 'e', // ê
    '\u00EB' to 'e', // ë
    '\u00EC' to 'i', // ì
    '\u00ED' to 'i', // í
    '\u00EE' to 'i', // î
    '\u00EF' to 'i', // ï
    '\u00F1' to 'n', // ñ
    '\u00F2' to 'o', // ò
    '\u00F3' to 'o', // ó
    '\u00F4' to 'o', // ô
    '\u00F5' to 'o', // õ
    '\u00F6' to 'o', // ö
    '\u00F9' to 'u', // ù
    '\u00FA' to 'u', // ú
    '\u00FB' to 'u', // û
    '\u00FC' to 'u', // ü
    '\u00FD' to 'y', // ý
    '\u00FF' to 'y', // ÿ
)
