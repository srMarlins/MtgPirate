package app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import database.Database
import database.DatabaseDriverFactory
import platform.IosMviPlatformServices
import purchase.IosPurchaseManager

/**
 * Entry point for iOS app to create the main UIViewController.
 * Call this function from Swift to launch the Compose UI.
 */
fun MainViewController() = androidx.compose.ui.window.ComposeUIViewController {
    val database = remember { Database(DatabaseDriverFactory()) }
    val platformServices = remember { IosMviPlatformServices(database) }
    val purchaseManager = remember { IosPurchaseManager() }

    DisposableEffect(Unit) {
        onDispose {
            platformServices.close()
        }
    }

    MobileApp(
        database = database,
        platformServices = platformServices,
        purchaseManager = purchaseManager,
        onOpenUseaCheckout = { itemsJson, couponCode ->
            // iOS WebView checkout will be presented via UIKit from Swift side
            // For now, the UseaWebViewCheckoutController is available for Swift integration
            // TODO: Wire iOS WebView presentation from Swift host
        },
    )
}
