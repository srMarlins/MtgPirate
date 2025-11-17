package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.srmarlins.mtgpirate.db.MtgPirateDatabase

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        return NativeSqliteDriver(MtgPirateDatabase.Schema, "mtg_pirate.db")
    }
}
