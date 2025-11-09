package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString // used in load()
import kotlinx.serialization.json.Json
import model.Preferences
import platform.AppDirectories
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object PreferencesStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = AppDirectories.dataDir.resolve("preferences.json")

    fun load(): Preferences? {
        return try {
            if (!file.exists()) null else json.decodeFromString<Preferences>(file.readText())
        } catch (_: Exception) { null }
    }

    fun save(prefs: Preferences) {
        try {
            file.writeText(json.encodeToString(prefs))
        } catch (_: Exception) { /* ignore */ }
    }
}
