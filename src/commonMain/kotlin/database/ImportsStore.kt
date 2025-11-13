package database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.SavedImport

class ImportsStore(private val database: Database) {
    fun loadAll(): Flow<List<SavedImport>> = database
        .db
        .savedImportQueries
        .selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun add(import: SavedImport) {
        database.db.savedImportQueries.insertImport(
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

    suspend fun delete(id: String) {
        database.db.savedImportQueries.deleteById(id)
    }
}
