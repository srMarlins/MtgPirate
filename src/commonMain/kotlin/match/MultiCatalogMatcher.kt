package match

import model.Catalog
import model.DeckEntry
import model.MatchOption
import model.MatchStatus
import model.MultiMatch
import model.Seller

object MultiCatalogMatcher {

    data class Config(
        val variantPriority: List<String> = listOf("Foil", "Holo", "Regular"),
        val setPriority: List<String> = emptyList(),
        val fuzzyEnabled: Boolean = true,
        val proxyFirst: Boolean = true,
    )

    fun match(
        entries: List<DeckEntry>,
        catalogs: Map<Seller, Catalog>,
        config: Config,
    ): List<MultiMatch> {
        val matchConfig = Matcher.MatchConfig(
            variantPriority = config.variantPriority,
            setPriority = config.setPriority,
            fuzzyEnabled = config.fuzzyEnabled,
            proxyFirst = config.proxyFirst,
        )

        return entries.map { entry ->
            val allOptions = mutableListOf<MatchOption>()

            // Match against each catalog
            for ((seller, catalog) in catalogs) {
                if (catalog.variants.isEmpty()) continue
                val results = Matcher.matchAll(listOf(entry), catalog, matchConfig)
                val result = results.firstOrNull() ?: continue

                if (result.status == MatchStatus.NOT_FOUND && result.candidates.isEmpty()) continue

                // Add the selected variant if available
                if (result.selectedVariant != null) {
                    allOptions.add(
                        MatchOption(
                            variant = result.selectedVariant.copy(seller = seller),
                            seller = seller,
                            priceCents = result.selectedVariant.priceInCents,
                            isProxy = seller.isProxy,
                            matchScore = 0,
                        )
                    )
                }

                // Add candidates that aren't already covered by the selected variant
                result.candidates.forEach { candidate ->
                    val alreadyAdded = allOptions.any { it.variant.sku == candidate.variant.sku }
                    if (!alreadyAdded) {
                        allOptions.add(
                            MatchOption(
                                variant = candidate.variant.copy(seller = seller),
                                seller = seller,
                                priceCents = candidate.variant.priceInCents,
                                isProxy = seller.isProxy,
                                matchScore = candidate.score,
                            )
                        )
                    }
                }
            }

            // Sort using shared comparator
            val sorted = allOptions.sortedWith(
                MatchSorting.matchOptionComparator(proxyFirst = config.proxyFirst)
            )

            val bestOption = sorted.firstOrNull()
            val realCardFallback = sorted.firstOrNull { !it.isProxy }

            MultiMatch(
                deckEntry = entry,
                bestOption = bestOption,
                alternatives = sorted,
                realCardFallback = if (bestOption?.isProxy == true) realCardFallback else null,
            )
        }
    }
}
