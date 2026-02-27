package match

import model.CardVariant
import model.MatchOption

/**
 * Shared sorting utilities for matching logic.
 * Ensures consistent ordering between Matcher (single-catalog) and MultiCatalogMatcher.
 */
object MatchSorting {

    /**
     * Standard comparator for CardVariant selection priority.
     * Order: proxy first (if enabled) -> cheapest -> variant priority -> set priority.
     */
    fun variantComparator(
        proxyFirst: Boolean,
        variantPriority: List<String>,
        setPriority: List<String>,
    ): Comparator<CardVariant> =
        compareBy<CardVariant> { if (proxyFirst && it.seller.isProxy) 0 else if (!proxyFirst && !it.seller.isProxy) 0 else 1 }
            .thenBy { it.priceInCents }
            .thenBy { idxOrEnd(variantPriority, it.variantType.displayName) }
            .thenBy { idxOrEnd(setPriority, it.setCode) }

    /**
     * Comparator for MatchOption selection in multi-catalog matching.
     * Order: proxy first (if enabled) -> match score -> cheapest.
     */
    fun matchOptionComparator(
        proxyFirst: Boolean,
    ): Comparator<MatchOption> =
        compareBy<MatchOption> { if (proxyFirst && it.isProxy) 0 else if (!proxyFirst && !it.isProxy) 0 else 1 }
            .thenBy { it.matchScore }
            .thenBy { it.priceCents }

    /**
     * Fuzzy match threshold based on name length.
     * Short names (<=15 chars) get threshold 2, longer names get 3.
     */
    fun fuzzyThreshold(nameLength: Int, relaxed: Boolean = false): Int {
        val base = if (nameLength <= 15) 2 else 3
        return if (relaxed) base + 2 else base
    }

    private fun idxOrEnd(list: List<String>, value: String): Int {
        val idx = list.indexOf(value)
        return if (idx >= 0) idx else list.size
    }
}
