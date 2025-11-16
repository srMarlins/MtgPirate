package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.srmarlins.mtgpirate.MtgPirateDatabase

/**
 * Apple platform (iOS and macOS) implementation of DatabaseDriverFactory.
 * Uses the native SQLite driver for both platforms.
 */
actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        return NativeSqliteDriver(MtgPirateDatabase.Schema, "pirate.db")
    }
}
