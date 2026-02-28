package platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.AndroidApp

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatDecimal(value: Double, decimalPlaces: Int): String {
    return "$" + "%.${decimalPlaces}f".format(value)
}

actual suspend fun copyToClipboard(text: String) {
    try {
        val ctx = AndroidApp.appContext ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("DeckLoot", text)
        clipboard.setPrimaryClip(clip)
    } catch (_: Exception) {
        // Silently fail - not critical
    }
}
