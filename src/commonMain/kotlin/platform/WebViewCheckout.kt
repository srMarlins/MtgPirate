package platform

import model.OrderItem

/**
 * Data needed to build a cart on agamecardshop.com via WebView.
 */
data class UseaCheckoutRequest(
    val items: List<CheckoutItem>,
    val couponCode: String?,
    val unmatchedItems: List<OrderItem>,
)

data class CheckoutItem(
    val wcProductId: Int,
    val quantity: Int,
    val cardName: String,
)

/**
 * Platform-specific WebView checkout launcher.
 * Returns true if WebView was opened, false if platform doesn't support it (desktop).
 */
expect class WebViewCheckoutLauncher {
    fun isSupported(): Boolean
}
