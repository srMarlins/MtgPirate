package platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    return "%.${decimalPlaces}f".format(value)
}

actual fun maxOf(a: Int, b: Int): Int = Math.max(a, b)

actual fun minOf(a: Int, b: Int): Int = Math.min(a, b)

actual fun abs(value: Int): Int = Math.abs(value)

actual suspend fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = StringSelection(text)
        clipboard.setContents(stringSelection, null)
    } catch (e: Exception) {
        // Silently fail - not critical
    }
}

actual fun triggerHapticFeedback(style: HapticFeedbackStyle) {
    // Desktop does not support haptic feedback
    // This is a no-op on desktop
}
