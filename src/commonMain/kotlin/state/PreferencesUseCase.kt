package state

import model.Preferences

/**
 * Use case for reading and writing user preferences.
 *
 * Wraps [MviPlatformServices.updatePreferences] with error handling
 * so the ViewModel does not deal with persistence details.
 */
class PreferencesUseCase(
    private val platformServices: MviPlatformServices
) {
    /**
     * Toggle the "include sideboard" preference.
     */
    suspend fun setIncludeSideboard(value: Boolean): Result<Unit> = runUpdate {
        it.copy(includeSideboard = value)
    }

    /**
     * Toggle the "include commanders" preference.
     */
    suspend fun setIncludeCommanders(value: Boolean): Result<Unit> = runUpdate {
        it.copy(includeCommanders = value)
    }

    /**
     * Toggle the "include tokens" preference.
     */
    suspend fun setIncludeTokens(value: Boolean): Result<Unit> = runUpdate {
        it.copy(includeTokens = value)
    }

    /**
     * Save full preference overrides (variant priority, set priority, fuzzy).
     */
    suspend fun savePreferences(
        variantPriority: List<String>,
        setPriority: List<String>,
        fuzzyEnabled: Boolean
    ): Result<Unit> = runUpdate {
        it.copy(
            variantPriority = variantPriority,
            setPriority = setPriority,
            fuzzyEnabled = fuzzyEnabled
        )
    }

    /**
     * Update only the variant priority list.
     */
    suspend fun updateVariantPriority(newPriority: List<String>): Result<Unit> = runUpdate {
        it.copy(variantPriority = newPriority)
    }

    /**
     * Convenience wrapper that catches exceptions and returns a [Result].
     */
    private suspend fun runUpdate(
        transform: (Preferences) -> Preferences
    ): Result<Unit> = try {
        platformServices.updatePreferences(transform)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
