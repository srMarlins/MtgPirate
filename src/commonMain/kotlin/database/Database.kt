package database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.srmarlins.mtgpirate.MtgPirateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.CardVariant
import model.LogEntry
import model.Preferences
import model.SavedImport

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val driver = databaseDriverFactory.createDriver()
    private val db = MtgPirateDatabase(driver)

    fun observeCardVariants(): Flow<List<CardVariant>> =
        db.cardVariantQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }

    fun observeSavedImports(): Flow<List<SavedImport>> =
        db.savedImportQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }

    fun observePreferences(): Flow<Preferences?> =
        db.preferencesQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { entities -> entities.firstOrNull()?.toDomain() }

    fun observeLogs(): Flow<List<LogEntry>> =
        db.logEntryQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }

    suspend fun insertImport(import: SavedImport) {
        db.savedImportQueries.insertImport(
            id = import.id,
            name = import.name,
            deckText = import.deckText,
            timestamp = import.timestamp,
            cardCount = import.cardCount.toLong(),
            includeSideboard = if (import.includeSideboard) 1L else 0L,
            includeCommanders = if (import.includeCommanders) 1L else 0L,
            includeTokens = if (import.includeTokens) 1L else 0L
        )
    }

    suspend fun deleteImportById(id: String) {
        db.savedImportQueries.deleteById(id)
    }
}
