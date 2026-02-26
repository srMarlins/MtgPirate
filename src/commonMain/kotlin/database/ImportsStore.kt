package database

import kotlinx.coroutines.flow.Flow
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
