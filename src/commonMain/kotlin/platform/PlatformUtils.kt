package platform

/**
 * Platform-specific utilities.
 * Uses expect/actual pattern for multiplatform support.
 */

/**
 * Get current time in milliseconds.
 */
expect fun currentTimeMillis(): Long

/**
 * Format a double value as a string with specified number of decimal places.
 */
expect fun formatDecimal(value: Double, decimalPlaces: Int): String

/**
 * Copy text to system clipboard.
 */
expect suspend fun copyToClipboard(text: String)
