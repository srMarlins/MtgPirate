package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.srmarlins.mtgpirate.MtgPirateDatabase
import java.util.*

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // You can change the DB path if you want persistence
        return JdbcSqliteDriver("jdbc:sqlite:pirate.db", Properties(), MtgPirateDatabase.Schema)
    }
}
