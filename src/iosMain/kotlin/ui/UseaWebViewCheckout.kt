package ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Creates a WKWebView configured for USEA cart building.
 * Call loadCart() after the view is added to the hierarchy.
 */
@OptIn(ExperimentalForeignApi::class)
class UseaWebViewCheckoutController(
    private val itemsJson: String,
    private val couponCode: String,
    private val onProgress: (current: Int, total: Int) -> Unit,
    private val onComplete: () -> Unit,
) {
    private val messageHandler = DeckLootMessageHandler(onProgress, onComplete)
    private var hasInjected = false

    val webView: WKWebView by lazy {
        val config = WKWebViewConfiguration()
        config.userContentController.addScriptMessageHandler(messageHandler, "DeckLoot")
        WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
            navigationDelegate = NavigationDelegate { view ->
                if (!hasInjected) {
                    hasInjected = true
                    val js = buildCartJs(itemsJson, couponCode)
                    view.evaluateJavaScript(js, null)
                }
            }
        }
    }

    fun loadCart() {
        val url = NSURL(string = "https://www.agamecardshop.com/cart/")
        val request = NSMutableURLRequest(uRL = url)
        webView.loadRequest(request)
    }

    private fun buildCartJs(items: String, coupon: String): String = """
        (async () => {
            const items = $items;
            const couponCode = '$coupon';
            let added = 0;
            let failed = 0;
            window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'total', count:items.length}));

            for (const item of items) {
                try {
                    const formData = new FormData();
                    formData.append('product_id', item.id);
                    formData.append('quantity', item.qty);
                    const resp = await fetch('/?wc-ajax=add_to_cart', { method: 'POST', body: formData });
                    if (!resp.ok) failed++;
                } catch (e) {
                    failed++;
                }
                added++;
                window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'progress', count:added}));
            }

            if (couponCode) {
                try {
                    const couponData = new FormData();
                    couponData.append('coupon_code', couponCode);
                    await fetch('/?wc-ajax=apply_coupon', { method: 'POST', body: couponData });
                } catch (e) { /* coupon is best-effort */ }
            }

            window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'complete'}));
            window.location.reload();
        })();
    """.trimIndent()
}

private class DeckLootMessageHandler(
    private val onProgress: (Int, Int) -> Unit,
    private val onComplete: () -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    private var total = 0

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? String ?: return
        when {
            body.contains("\"total\"") -> {
                total = body.substringAfter("count\":").substringBefore("}").trim().toIntOrNull() ?: 0
            }
            body.contains("\"progress\"") -> {
                val current = body.substringAfter("count\":").substringBefore("}").trim().toIntOrNull() ?: 0
                onProgress(current, total)
            }
            body.contains("\"complete\"") -> onComplete()
        }
    }
}

private class NavigationDelegate(
    private val onPageFinished: (WKWebView) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onPageFinished(webView)
    }
}
