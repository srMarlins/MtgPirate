package platform

import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * Format a decimal value with a fixed US locale so that CSV export always uses
 * '.' as the decimal separator regardless of the device locale.
 */
actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    // Use fixed US locale to guarantee '.' decimal separator in CSV output
    formatter.locale = NSLocale("en_US")
    formatter.minimumFractionDigits = decimalPlaces.toULong()
    formatter.maximumFractionDigits = decimalPlaces.toULong()
    val formatted = formatter.stringFromNumber(NSNumber(value))
    return "$$formatted"
}

actual suspend fun copyToClipboard(text: String) {
    platform.UIKit.UIPasteboard.generalPasteboard.string = text
}
