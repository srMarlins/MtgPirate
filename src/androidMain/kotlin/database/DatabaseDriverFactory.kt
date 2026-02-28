package database

import app.AndroidApp
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.deckloot.db.DeckLootDatabase

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        val context = AndroidApp.appContext
            ?: error("AndroidApp.init() must be called before creating the database driver")
        return AndroidSqliteDriver(DeckLootDatabase.Schema, context, "deckloot.db")
    }
}
