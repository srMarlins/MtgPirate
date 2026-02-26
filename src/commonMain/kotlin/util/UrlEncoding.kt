package util

/**
 * Percent-encode a string for use in URL query parameters (RFC 3986).
 * Unreserved characters (A-Z, a-z, 0-9, -, _, ., ~) are left as-is.
 * Spaces are encoded as %20 (not +).
 */
fun encodeUrlParameter(value: String): String = buildString {
    for (char in value) {
        when {
            char.isLetterOrDigit() || char in "-_.~" -> append(char)
            else -> {
                val bytes = char.toString().encodeToByteArray()
                for (byte in bytes) {
                    append('%')
                    append(byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
