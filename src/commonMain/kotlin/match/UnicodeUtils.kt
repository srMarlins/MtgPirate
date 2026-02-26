package match

/**
 * Strip diacritical marks from the input string so that accented characters
 * compare equal to their ASCII base letters.
 *
 * Platform-specific implementations use the best available API:
 * - JVM: [java.text.Normalizer] NFD decomposition + regex strip
 * - iOS/Native: manual replacement map covering MTG card names
 */
expect fun stripDiacritics(input: String): String
