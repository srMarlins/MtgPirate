package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import database.Database
import database.DatabaseDriverFactory
import platform.AndroidMviPlatformServices

class MainActivity : ComponentActivity() {
    private var platformServices: AndroidMviPlatformServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.init(this)

        val database = Database(DatabaseDriverFactory())
        val services = AndroidMviPlatformServices(database, this)
        platformServices = services

        setContent {
            MobileApp(database, services)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        platformServices?.close()
    }
}
