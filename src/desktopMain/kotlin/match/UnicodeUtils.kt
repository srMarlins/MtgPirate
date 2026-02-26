package match

import java.text.Normalizer

/**
 * JVM implementation: use NFD decomposition to split base characters from
 * combining marks, then strip all combining (Mn category) characters.
 */
actual fun stripDiacritics(input: String): String {
    val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
    return decomposed.replace("\\p{Mn}".toRegex(), "")
}
