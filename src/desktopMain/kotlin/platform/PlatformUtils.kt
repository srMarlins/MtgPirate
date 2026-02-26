package platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    return "$" + "%.${decimalPlaces}f".format(value)
}

actual suspend fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = StringSelection(text)
        clipboard.setContents(stringSelection, null)
    } catch (e: Exception) {
        // Silently fail - not critical
    }
}
