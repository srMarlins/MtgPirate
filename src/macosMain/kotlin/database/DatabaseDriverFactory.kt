package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.srmarlins.mtgpirate.MtgPirateDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Use native SQLite driver for macOS
        return NativeSqliteDriver(MtgPirateDatabase.Schema, "pirate.db")
    }
}
