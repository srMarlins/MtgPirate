package state

import database.ImportsStore
import deck.DecklistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import model.DeckEntry
import model.DeckEntryMatch
import model.Preferences
import model.SavedImport
import model.Section
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Use case for saving, loading, and deleting deck imports,
 * as well as CSV / wizard-results export.
 *
 * All database access is wrapped in [Dispatchers.IO].
 */
@OptIn(ExperimentalTime::class)
class ImportExportUseCase(
    private val importsStore: ImportsStore,
    private val platformServices: MviPlatformServices
) {
    /**
     * Build and persist a [SavedImport] from the current deck state.
     *
     * If an import with the same name, deck text, and preference flags
     * already exists it will be updated rather than duplicated.
     *
     * @return the generated deck name for logging purposes
     */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    suspend fun saveCurrentImport(
        deckText: String,
        deckEntries: List<DeckEntry>,
        preferences: Preferences,
        savedImports: List<SavedImport>
    ): Result<String> {
        if (deckText.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot save empty import"))
        }

        val entries = deckEntries.ifEmpty {
            withContext(Dispatchers.Default) {
                DecklistParser.parse(
                    deckText,
                    preferences.includeSideboard,
                    preferences.includeCommanders
                )
            }
        }

        val commanderNames = entries.filter { it.section == Section.COMMANDER }.map { it.cardName }
        val generatedName = if (commanderNames.isNotEmpty()) commanderNames.joinToString(" + ") else deckText

        val existing = savedImports.find {
            it.name == generatedName &&
                it.deckText == deckText &&
                it.includeSideboard == preferences.includeSideboard &&
                it.includeCommanders == preferences.includeCommanders &&
                it.includeTokens == preferences.includeTokens
        }

        val import = SavedImport(
            id = existing?.id ?: kotlin.uuid.Uuid.random().toString(),
            name = generatedName,
            deckText = deckText,
            timestamp = Clock.System.now().toString(),
            cardCount = entries.size,
            includeSideboard = preferences.includeSideboard,
            includeCommanders = preferences.includeCommanders,
            includeTokens = preferences.includeTokens
        )

        return try {
            withContext(Dispatchers.IO) {
                if (existing != null) {
                    importsStore.delete(existing.id)
                    importsStore.add(import)
                } else {
                    importsStore.add(import)
                }
            }
            val verb = if (existing != null) "updated" else "saved"
            Result.success("Import $verb: $generatedName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a saved import by its id, applying its preference overrides.
     *
     * @return the [SavedImport] if found and loaded successfully
     */
    suspend fun loadSavedImport(
        importId: String,
        savedImports: List<SavedImport>
    ): Result<SavedImport> {
        val import = savedImports.find { it.id == importId }
            ?: return Result.failure(IllegalArgumentException("Import not found: $importId"))

        return try {
            platformServices.updatePreferences {
                it.copy(
                    includeSideboard = import.includeSideboard,
                    includeCommanders = import.includeCommanders,
                    includeTokens = import.includeTokens
                )
            }
            Result.success(import)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a saved import by its id.
     */
    suspend fun deleteSavedImport(importId: String): Result<Unit> = try {
        withContext(Dispatchers.IO) { importsStore.delete(importId) }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Export matched cards as a CSV file via platform services.
     */
    suspend fun exportCsv(
        matches: List<DeckEntryMatch>,
        onComplete: (String) -> Unit
    ) {
        if (matches.isNotEmpty()) {
            platformServices.exportCsv(matches, onComplete)
        }
    }

    /**
     * Export wizard-style results (found + unfound) via platform services.
     */
    suspend fun exportWizardResults(
        matches: List<DeckEntryMatch>,
        onComplete: (foundPath: String?, unfoundPath: String?) -> Unit
    ) {
        if (matches.isNotEmpty()) {
            platformServices.exportWizardResults(matches, onComplete)
        }
    }
}
