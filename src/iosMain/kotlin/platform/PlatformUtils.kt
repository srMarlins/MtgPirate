package platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.abs as kAbs

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val formatter = platform.Foundation.NSNumberFormatter()
    formatter.numberStyle = platform.Foundation.NSNumberFormatterDecimalStyle
    formatter.minimumFractionDigits = decimalPlaces.toULong()
    formatter.maximumFractionDigits = decimalPlaces.toULong()
    val formatted = formatter.stringFromNumber(platform.Foundation.NSNumber(value))
    return "$$formatted"
}

actual fun maxOf(a: Int, b: Int): Int = if (a > b) a else b

actual fun minOf(a: Int, b: Int): Int = if (a < b) a else b

actual fun abs(value: Int): Int = kAbs(value)

actual suspend fun copyToClipboard(text: String) {
    platform.UIKit.UIPasteboard.generalPasteboard.string = text
}
