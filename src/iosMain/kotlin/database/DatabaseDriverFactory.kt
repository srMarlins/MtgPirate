package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.deckloot.db.DeckLootDatabase

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        return NativeSqliteDriver(DeckLootDatabase.Schema, "deckloot.db")
    }
}
