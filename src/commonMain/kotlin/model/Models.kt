package model

import kotlinx.serialization.Serializable

@Serializable
data class CardVariant(
    val nameOriginal: String,
    val nameNormalized: String,
    val setCode: String,
    val sku: String,
    val variantType: String, // Regular, Foil, Holo
    val priceInCents: Int
)

@Serializable
data class Catalog(
    val variants: List<CardVariant>
) {
    // Simple index built on demand
    val indexByName: Map<String, List<CardVariant>> by lazy {
        variants.groupBy { it.nameNormalized }
    }
}

enum class Section { MAIN, SIDEBOARD, COMMANDER }

// Added optional parsed set code and collector number from deck list line for improved matching
// setCodeHint: user-provided set code (uppercase) if present in the list line e.g. "4 Brainstorm (MMQ)"
// collectorNumberHint: optional collector number token if supplied after set code e.g. "(MMQ 123)"
// rawSetHint: raw parenthetical contents before normalization
// These are not serialized currently (runtime only) so exclude from @Serializable

data class DeckEntry(
    val originalLine: String,
    val qty: Int,
    val cardName: String,
    val section: Section,
    val include: Boolean,
    val setCodeHint: String? = null,
    val collectorNumberHint: String? = null,
    val rawSetHint: String? = null
)

enum class MatchStatus { UNRESOLVED, AUTO_MATCHED, AMBIGUOUS, NOT_FOUND, MANUAL_SELECTED }

data class MatchCandidate(
    val variant: CardVariant,
    val score: Int,
    val reason: String
)

data class DeckEntryMatch(
    val deckEntry: DeckEntry,
    val status: MatchStatus,
    val selectedVariant: CardVariant? = null,
    val candidates: List<MatchCandidate> = emptyList(),
    val notes: String = ""
)

@Serializable
data class Preferences(
    val includeSideboard: Boolean = false,
    val includeCommanders: Boolean = false,
    val variantPriority: List<String> = listOf("Regular", "Foil", "Holo"),
    val setPriority: List<String> = emptyList(),
    val fuzzyEnabled: Boolean = true,
    val cacheMaxAgeHours: Int = 24
)

@kotlinx.serialization.Serializable
data class LogEntry(val level: String, val message: String, val timestamp: String)

data class AppState(
    var catalog: Catalog? = null,
    var deckEntries: List<DeckEntry> = emptyList(),
    var matches: List<DeckEntryMatch> = emptyList(),
    var preferences: Preferences = Preferences(),
    var logs: List<LogEntry> = emptyList()
)
