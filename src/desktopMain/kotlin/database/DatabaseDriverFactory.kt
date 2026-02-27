package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.srmarlins.mtgpirate.db.MtgPirateDatabase
import java.util.*

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        val dbPath = platform.AppDirectories.dataDir.resolve("pirate.db").toString()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath", Properties(), MtgPirateDatabase.Schema)
        // Enable WAL mode and set busy timeout to prevent SQLITE_BUSY under concurrent writes
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA busy_timeout=5000", 0)
        return driver
    }
}
