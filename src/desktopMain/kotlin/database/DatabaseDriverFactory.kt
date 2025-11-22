package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.srmarlins.mtgpirate.db.MtgPirateDatabase
import java.util.*

actual open class DatabaseDriverFactory {
    actual open fun createDriver(): SqlDriver {
        val dbPath = platform.AppDirectories.dataDir.resolve("pirate.db").toString()
        return JdbcSqliteDriver("jdbc:sqlite:$dbPath", Properties(), MtgPirateDatabase.Schema)
    }
}
