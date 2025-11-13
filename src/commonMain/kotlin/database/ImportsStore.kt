package database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.SavedImport

class ImportsStore(private val database: Database) {
    fun loadAll(): Flow<List<SavedImport>> = database.observeSavedImports()

    suspend fun add(import: SavedImport) {
        database.insertImport(import)
    }

    suspend fun delete(id: String) {
        database.deleteImportById(id)
    }
}
