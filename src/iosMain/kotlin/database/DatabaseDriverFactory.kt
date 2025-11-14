package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.srmarlins.mtgpirate.db.MtgPirateDatabase

<<<<<<< HEAD
actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        // Use native SQLite driver for iOS
        return NativeSqliteDriver(MtgPirateDatabase.Schema, "pirate.db")
=======
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(MtgPirateDatabase.Schema, "mtg_pirate.db")
>>>>>>> ec045f3 (Base ios implementation)
    }
}
