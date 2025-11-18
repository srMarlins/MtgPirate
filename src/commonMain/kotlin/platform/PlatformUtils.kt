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

/**
 * Trigger haptic feedback for UI interactions.
 * @param style The type of haptic feedback to trigger
 */
expect fun triggerHapticFeedback(style: HapticFeedbackStyle)

/**
 * Haptic feedback styles for different UI interactions.
 */
enum class HapticFeedbackStyle {
    /** Light impact for subtle interactions */
    LIGHT,
    /** Medium impact for standard interactions */
    MEDIUM,
    /** Heavy impact for significant interactions */
    HEAVY,
    /** Selection changed feedback (iOS UISelectionFeedbackGenerator) */
    SELECTION,
    /** Success notification feedback */
    SUCCESS,
    /** Warning notification feedback */
    WARNING,
    /** Error notification feedback */
    ERROR
}
