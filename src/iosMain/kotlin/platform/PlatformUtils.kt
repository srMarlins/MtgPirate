package platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.abs as kAbs

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val factor = 10.0.pow(decimalPlaces)
    val rounded = round(value * factor) / factor
    return "$$rounded"
}

actual fun maxOf(a: Int, b: Int): Int = if (a > b) a else b

actual fun minOf(a: Int, b: Int): Int = if (a < b) a else b

actual fun abs(value: Int): Int = kAbs(value)

actual suspend fun copyToClipboard(text: String) {
    platform.UIKit.UIPasteboard.generalPasteboard.string = text
}
