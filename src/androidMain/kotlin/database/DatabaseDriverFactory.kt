package database

import app.AndroidApp
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.srmarlins.mtgpirate.db.MtgPirateDatabase

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        val context = AndroidApp.appContext
            ?: error("AndroidApp.init() must be called before creating the database driver")
        return AndroidSqliteDriver(MtgPirateDatabase.Schema, context, "mtg_pirate.db")
    }
}
