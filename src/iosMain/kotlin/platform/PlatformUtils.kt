package platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.UIKit.UIPasteboard
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * Get current time in milliseconds since epoch.
 * Uses POSIX gettimeofday for accurate timestamps on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = memScoped {
    val timeVal = alloc<timeval>()
    gettimeofday(timeVal.ptr, null)
    timeVal.tv_sec * 1000L + timeVal.tv_usec / 1000L
}

/**
 * Format a double value with specified decimal places.
 * Uses NSNumberFormatter for proper iOS locale formatting.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = decimalPlaces.toULong()
        maximumFractionDigits = decimalPlaces.toULong()
    }
    return formatter.stringFromNumber(NSNumber(value)) ?: value.toString()
}

actual fun maxOf(a: Int, b: Int): Int = max(a, b)

actual fun minOf(a: Int, b: Int): Int = min(a, b)

actual fun abs(value: Int): Int = value.absoluteValue

/**
 * Copy text to the iOS system clipboard.
 * Uses UIPasteboard for native clipboard integration.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
