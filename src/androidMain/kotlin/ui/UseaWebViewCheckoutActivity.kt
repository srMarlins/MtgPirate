package ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class UseaWebViewCheckoutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEMS_JSON = "items_json"
        const val EXTRA_COUPON_CODE = "coupon_code"
    }

    private val progress = mutableIntStateOf(0)
    private val total = mutableIntStateOf(0)
    private val isBuilding = mutableStateOf(true)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemsJson = intent.getStringExtra(EXTRA_ITEMS_JSON) ?: "[]"
        val couponCode = intent.getStringExtra(EXTRA_COUPON_CODE) ?: ""

        setContent {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            addJavascriptInterface(DeckLootBridge(), "DeckLoot")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (isBuilding.value) {
                                        val js = buildCartJs(itemsJson, couponCode)
                                        view?.evaluateJavascript(js, null)
                                    }
                                }
                            }
                            loadUrl("https://www.agamecardshop.com/cart/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isBuilding.value) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Text(
                            "Building your cart... (${progress.intValue}/${total.intValue})",
                            color = Color.White,
                        )
                        if (total.intValue > 0) {
                            LinearProgressIndicator(
                                progress = progress.intValue.toFloat() / total.intValue,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildCartJs(itemsJson: String, couponCode: String): String = """
        (async () => {
            const items = $itemsJson;
            const couponCode = '$couponCode';
            let added = 0;
            let failed = 0;
            DeckLoot.onTotal(items.length);

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
                DeckLoot.onProgress(added);
            }

            if (couponCode) {
                try {
                    const couponData = new FormData();
                    couponData.append('coupon_code', couponCode);
                    await fetch('/?wc-ajax=apply_coupon', { method: 'POST', body: couponData });
                } catch (e) { /* coupon is best-effort */ }
            }

            DeckLoot.onComplete();
            window.location.reload();
        })();
    """.trimIndent()

    inner class DeckLootBridge {
        @JavascriptInterface
        fun onTotal(count: Int) { runOnUiThread { total.intValue = count } }

        @JavascriptInterface
        fun onProgress(count: Int) { runOnUiThread { progress.intValue = count } }

        @JavascriptInterface
        fun onComplete() { runOnUiThread { isBuilding.value = false } }
    }
}
