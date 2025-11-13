package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // You can change the DB path if you want persistence
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }
}
