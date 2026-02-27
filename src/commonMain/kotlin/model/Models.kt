package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VariantType(val displayName: String, val defaultPriceInCents: Int) {
    @SerialName("Regular") REGULAR("Regular", 220),
    @SerialName("Foil") FOIL("Foil", 350),
    @SerialName("Holo") HOLO("Holo", 300);

    companion object {
        fun fromString(value: String): VariantType = when {
            value.equals("Foil", ignoreCase = true) -> FOIL
            value.equals("Holo", ignoreCase = true) ||
            value.contains("Holo", ignoreCase = true) -> HOLO
            else -> REGULAR
        }
    }
}

enum class Seller(val displayName: String, val isProxy: Boolean) {
    USEA("USEA MTG Proxy", true),
    BOOTLEG_MAGE("Bootleg Mage", true),
    @Deprecated("Use MANAPOOL instead")
    TCGPLAYER("TCGPlayer", false),
    MANAPOOL("ManaPool", false);
}

@Serializable
data class CardVariant(
    val nameOriginal: String,
    val nameNormalized: String,
    val setCode: String,
    val sku: String,
    val variantType: VariantType,
    val priceInCents: Int,
    val collectorNumber: String? = null,
    val imageUrl: String? = null,
    val seller: Seller = Seller.USEA,
    val purchaseUri: String? = null,
) {
    val uniqueIdentifier: String get() = sku
}

@Serializable
data class Catalog(
    val variants: List<CardVariant>
) {
    /** Lazy index for O(1) lookup by normalized name. */
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
    val id: String,
    val originalLine: String,
    val qty: Int,
    val cardName: String,
    val section: Section,
    val include: Boolean,
    val setCodeHint: String? = null,
    val collectorNumberHint: String? = null,
    val rawSetHint: String? = null
) {
    val uniqueIdentifier: String get() = id
}

enum class MatchStatus { UNRESOLVED, AUTO_MATCHED, AMBIGUOUS, NOT_FOUND, MANUAL_SELECTED, FUZZY_RECHECK }

data class MatchCandidate(
    val variant: CardVariant,
    val score: Int,
    val reason: String
) {
    val uniqueIdentifier: String get() = variant.sku
}

data class DeckEntryMatch(
    val deckEntry: DeckEntry,
    val status: MatchStatus,
    val selectedVariant: CardVariant? = null,
    val candidates: List<MatchCandidate> = emptyList(),
    val notes: String = ""
) {
    val uniqueIdentifier: String get() = deckEntry.id
}

@Serializable
data class Preferences(
    val includeSideboard: Boolean = true,
    val includeCommanders: Boolean = true,
    val includeTokens: Boolean = true,
    val variantPriority: List<String> = listOf("Foil", "Holo", "Regular"),
    val setPriority: List<String> = emptyList(),
    val fuzzyEnabled: Boolean = true,
    val cacheMaxAgeHours: Int = 24
)

@Serializable
data class LogEntry(val level: String, val message: String, val timestamp: String)

@Serializable
data class SavedImport(
    val id: String,
    val name: String,
    val deckText: String,
    val timestamp: String,
    val cardCount: Int,
    val includeSideboard: Boolean = false,
    val includeCommanders: Boolean = false,
    val includeTokens: Boolean = false
) {
    val uniqueIdentifier: String get() = id
}

data class MatchOption(
    val variant: CardVariant,
    val seller: Seller,
    val priceCents: Int,
    val isProxy: Boolean,
    val matchScore: Int,
)

data class MultiMatch(
    val deckEntry: DeckEntry,
    val bestOption: MatchOption?,
    val alternatives: List<MatchOption>,
    val realCardFallback: MatchOption?,
)

data class OrderItem(
    val variant: CardVariant,
    val qty: Int,
    val isProxy: Boolean,
)

data class SellerOrder(
    val seller: Seller,
    val items: List<OrderItem>,
    val subtotalCents: Int,
    val discountPercent: Int,
    val shippingCents: Int,
    val totalCents: Int,
)

data class ShoppingPlan(
    val orders: List<SellerOrder>,
    val totalPriceCents: Int,
    val savingsVsSingleSeller: Int,
)
