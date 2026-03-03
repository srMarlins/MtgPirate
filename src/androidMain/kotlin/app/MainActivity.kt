package app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import database.Database
import database.DatabaseDriverFactory
import platform.AndroidMviPlatformServices
import purchase.AndroidPurchaseManager
import ui.UseaWebViewCheckoutActivity

class MainActivity : ComponentActivity() {
    private var platformServices: AndroidMviPlatformServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.init(this)

        val database = Database(DatabaseDriverFactory())
        val services = AndroidMviPlatformServices(database, this)
        platformServices = services
        val purchaseManager = AndroidPurchaseManager()

        setContent {
            MobileApp(
                database = database,
                platformServices = services,
                purchaseManager = purchaseManager,
                onOpenUseaCheckout = { itemsJson, couponCode ->
                    startActivity(Intent(this, UseaWebViewCheckoutActivity::class.java).apply {
                        putExtra(UseaWebViewCheckoutActivity.EXTRA_ITEMS_JSON, itemsJson)
                        putExtra(UseaWebViewCheckoutActivity.EXTRA_COUPON_CODE, couponCode)
                    })
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        platformServices?.close()
    }
}
