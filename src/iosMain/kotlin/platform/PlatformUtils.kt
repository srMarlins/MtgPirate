package platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val formatter = platform.Foundation.NSNumberFormatter()
    formatter.numberStyle = platform.Foundation.NSNumberFormatterDecimalStyle
    formatter.minimumFractionDigits = decimalPlaces.toULong()
    formatter.maximumFractionDigits = decimalPlaces.toULong()
    val formatted = formatter.stringFromNumber(platform.Foundation.NSNumber(value))
    return "$$formatted"
}

actual suspend fun copyToClipboard(text: String) {
    platform.UIKit.UIPasteboard.generalPasteboard.string = text
}
