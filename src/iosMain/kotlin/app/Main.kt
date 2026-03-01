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

    MobileApp(database, platformServices, purchaseManager)
}
