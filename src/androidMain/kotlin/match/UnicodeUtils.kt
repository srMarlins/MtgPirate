package match

import java.text.Normalizer

/**
 * Android (JVM) implementation: use NFD decomposition to split base characters from
 * combining marks, then strip all combining (Mn category) characters.
 * Identical to desktop implementation.
 */
actual fun stripDiacritics(input: String): String {
    val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
    return decomposed.replace("\\p{Mn}".toRegex(), "")
}
