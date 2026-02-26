package state

import deck.DecklistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import match.Matcher
import match.MultiCatalogMatcher
import model.Catalog
import model.DeckEntry
import model.DeckEntryMatch
import model.MatchStatus
import model.MultiMatch
import model.Seller

/**
 * Use case for deck parsing and card matching.
 *
 * Handles the parse-then-match pipeline including preference-based
 * configuration, count calculation, and candidate resolution.
 */
class MatchingUseCase {

    /**
     * Parse raw decklist text into structured [DeckEntry] items.
     */
    suspend fun parseDeck(
        deckText: String,
        includeSideboard: Boolean,
        includeCommanders: Boolean
    ): List<DeckEntry> = withContext(Dispatchers.Default) {
        DecklistParser.parse(deckText, includeSideboard, includeCommanders)
    }

    /**
     * Match a list of deck entries against the catalog using the given config.
     */
    suspend fun matchEntries(
        entries: List<DeckEntry>,
        catalog: Catalog,
        config: Matcher.MatchConfig
    ): List<DeckEntryMatch> = withContext(Dispatchers.Default) {
        Matcher.matchAll(entries, catalog, config)
    }

    /**
     * Match deck entries against catalogs from multiple sellers.
     *
     * Builds per-seller [Catalog] objects from the full catalog (all variants
     * grouped by their [Seller] tag) and delegates to [MultiCatalogMatcher].
     *
     * @param entries parsed deck entries to match
     * @param catalogs per-seller catalogs to match against
     * @param config multi-catalog matching configuration
     * @return list of [MultiMatch] results with options from all sellers
     */
    suspend fun matchEntriesMulti(
        entries: List<DeckEntry>,
        catalogs: Map<Seller, Catalog>,
        config: MultiCatalogMatcher.Config
    ): List<MultiMatch> = withContext(Dispatchers.Default) {
        MultiCatalogMatcher.match(entries, catalogs, config)
    }

    /**
     * Calculate aggregated match statistics.
     */
    suspend fun calculateMatchCounts(matches: List<DeckEntryMatch>): MatchCounts =
        withContext(Dispatchers.Default) {
            val matched = matches.count { it.selectedVariant != null }
            val unmatched = matches.count { it.selectedVariant == null && it.deckEntry.include }
            val ambiguous = matches.count { it.status == MatchStatus.AMBIGUOUS }
            val totalPrice = matches
                .filter { it.selectedVariant != null }
                .sumOf { match ->
                    match.selectedVariant!!.priceInCents * match.deckEntry.qty
                }

            MatchCounts(
                matched = matched,
                unmatched = unmatched,
                ambiguous = ambiguous,
                totalPrice = totalPrice
            )
        }
}

/**
 * Pre-calculated match statistics for performance.
 */
data class MatchCounts(
    val matched: Int,
    val unmatched: Int,
    val ambiguous: Int,
    val totalPrice: Int
)
