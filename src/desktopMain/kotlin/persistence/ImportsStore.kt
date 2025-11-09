package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.SavedImport
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ImportsStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dataDir = Path.of("data")
    private val file = dataDir.resolve("saved-imports.json")

    fun loadAll(): List<SavedImport> {
        return try {
            if (!file.exists()) emptyList()
            else json.decodeFromString<List<SavedImport>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(imports: List<SavedImport>) {
        try {
            if (!dataDir.exists()) java.nio.file.Files.createDirectories(dataDir)
            file.writeText(json.encodeToString(imports))
        } catch (_: Exception) { /* ignore */ }
    }

    fun add(import: SavedImport) {
        val imports = loadAll().toMutableList()
        imports.add(0, import) // Add to beginning
        saveAll(imports)
    }

    fun delete(id: String) {
        val imports = loadAll().filterNot { it.id == id }
        saveAll(imports)
    }
}

