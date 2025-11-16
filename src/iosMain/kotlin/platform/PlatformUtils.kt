package platform

import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

actual fun currentTimeMillis(): Long {
    // Note: This is a simplified implementation for iOS.
    // For a production app, use platform.Foundation.NSDate.timeIntervalSince1970() * 1000
    return 0L
}

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    // Simple decimal formatting for iOS
    val multiplier = when (decimalPlaces) {
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 100.0
    }
    val rounded = (value * multiplier).toLong().toDouble() / multiplier
    
    // Format with proper decimal places
    val intPart = rounded.toLong()
    val fracPart = (rounded - intPart) * multiplier
    return when (decimalPlaces) {
        1 -> "$intPart.${fracPart.toLong()}"
        2 -> "$intPart.${fracPart.toLong().toString().padStart(2, '0')}"
        else -> rounded.toString()
    }
}

actual fun maxOf(a: Int, b: Int): Int = max(a, b)

actual fun minOf(a: Int, b: Int): Int = min(a, b)

actual fun abs(value: Int): Int = value.absoluteValue

actual suspend fun copyToClipboard(text: String) {
    // Note: For a production iOS app, implement with:
    // platform.UIKit.UIPasteboard.generalPasteboard.string = text
    // This requires the proper iOS interop setup
}
