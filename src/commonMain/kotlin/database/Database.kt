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
import model.Catalog
import model.LogEntry
import model.Preferences
import model.SavedImport

expect open class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val driver = databaseDriverFactory.createDriver()
    private val db = MtgPirateDatabase(driver)

    fun observeCardVariants(): Flow<List<CardVariant>> =
        db.cardVariantQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }

    fun observeCatalog(): Flow<Catalog> =
        observeCardVariants().map { variants -> Catalog(variants) }

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

    suspend fun insertVariant(variant: CardVariant) {
        db.cardVariantQueries.insertVariant(
            nameOriginal = variant.nameOriginal,
            nameNormalized = variant.nameNormalized,
            setCode = variant.setCode,
            sku = variant.sku,
            variantType = variant.variantType,
            priceInCents = variant.priceInCents.toLong(),
            collectorNumber = variant.collectorNumber,
            imageUrl = variant.imageUrl
        )
    }

    suspend fun clearAllVariants() {
        db.cardVariantQueries.deleteAll()
    }

    suspend fun getVariantCount(): Long {
        return db.cardVariantQueries.countAll().executeAsOne()
    }

    suspend fun insertPreferences(preferences: Preferences) {
        db.preferencesQueries.insertPreferences(
            includeSideboard = if (preferences.includeSideboard) 1L else 0L,
            includeCommanders = if (preferences.includeCommanders) 1L else 0L,
            includeTokens = if (preferences.includeTokens) 1L else 0L,
            variantPriority = preferences.variantPriority.joinToString(","),
            setPriority = preferences.setPriority.joinToString(","),
            fuzzyEnabled = if (preferences.fuzzyEnabled) 1L else 0L,
            cacheMaxAgeHours = preferences.cacheMaxAgeHours.toLong()
        )
    }

    suspend fun insertLog(log: LogEntry) {
        db.logEntryQueries.insertLog(
            level = log.level,
            message = log.message,
            timestamp = log.timestamp
        )
    }

    suspend fun deleteOldLogs(keepCount: Long = 1000L) {
        db.logEntryQueries.deleteOldLogs(keepCount)
    }
}
