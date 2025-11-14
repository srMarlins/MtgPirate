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
 * Get maximum of two Int values.
 */
expect fun maxOf(a: Int, b: Int): Int

/**
 * Get minimum of two Int values.
 */
expect fun minOf(a: Int, b: Int): Int

/**
 * Get absolute value of an Int.
 */
expect fun abs(value: Int): Int

/**
 * Copy text to system clipboard.
 */
expect suspend fun copyToClipboard(text: String)
