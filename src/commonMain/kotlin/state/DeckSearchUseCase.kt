package state

import catalog.CatalogSource
import database.CatalogStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.Seller

/**
 * Orchestrates parallel searches across all catalog suppliers for a given deck.
 *
 * Uses functional dependencies for testability — can be constructed with either
 * real CatalogStore methods or fake lambdas in tests.
 */
class DeckSearchUseCase(
    private val sources: List<CatalogSource>,
    private val isSellerCacheFresh: (Seller) -> Boolean,
    private val replaceCatalogForSeller: suspend (Seller, List<CardVariant>) -> Unit,
    private val markSellerFetched: (Seller) -> Unit,
    private val getAllVariants: () -> List<CardVariant>,
) {

    /** Convenience constructor that wires directly to a CatalogStore. */
    constructor(
        sources: List<CatalogSource>,
        catalogStore: CatalogStore,
    ) : this(
        sources = sources,
        isSellerCacheFresh = catalogStore::isSellerCacheFresh,
        replaceCatalogForSeller = catalogStore::replaceCatalogForSeller,
        markSellerFetched = catalogStore::markSellerFetched,
        getAllVariants = catalogStore::getAllVariants,
    )

    /**
     * Searches all catalog suppliers in parallel for cards matching the given deck entries.
     *
     * Emits [SearchProgress] after each supplier completes (or fails), with incrementally
     * updated [MultiMatch] results. The final emission has [SearchProgress.isComplete] = true.
     */
    fun search(
        entries: List<DeckEntry>,
        config: MultiCatalogMatcher.Config,
    ): Flow<SearchProgress> = channelFlow {
        val includedEntries = entries.filter { it.include }

        // Fast path: nothing to search
        if (includedEntries.isEmpty()) {
            send(
                SearchProgress(
                    totalCards = 0,
                    cardsWithResults = 0,
                    sellerStatuses = emptyMap(),
                    multiMatches = emptyList(),
                    isComplete = true,
                )
            )
            return@channelFlow
        }

        // Normalized names of cards we're looking for — used to filter bulk catalogs
        val deckNormalizedNames = includedEntries
            .map { NameNormalizer.normalize(it.cardName) }
            .toSet()

        // Mutable shared state protected by mutex
        val mutex = Mutex()
        val sellerStatuses = mutableMapOf<Seller, SellerSearchStatus>()

        // Initialize all sellers as PENDING
        for (source in sources) {
            sellerStatuses[source.seller] = SellerSearchStatus(
                seller = source.seller,
                state = SearchState.PENDING,
            )
        }

        // Emit initial state
        send(buildProgress(includedEntries, sellerStatuses, config))

        // Launch parallel searches
        coroutineScope {
            for (source in sources) {
                launch {
                    try {
                        // Mark SEARCHING
                        mutex.withLock {
                            sellerStatuses[source.seller] = SellerSearchStatus(
                                seller = source.seller,
                                state = SearchState.SEARCHING,
                            )
                        }

                        if (isSellerCacheFresh(source.seller)) {
                            // Cache is fresh — skip API call
                            val existingVariants = getAllVariants()
                                .filter { it.seller == source.seller }
                            mutex.withLock {
                                sellerStatuses[source.seller] = SellerSearchStatus(
                                    seller = source.seller,
                                    state = SearchState.DONE,
                                    cardsFound = existingVariants.count { it.nameNormalized in deckNormalizedNames },
                                )
                            }
                        } else {
                            // Fetch from API
                            val allVariants = source.fetchCatalog()

                            // Filter to only cards matching deck entries
                            val matchingVariants = allVariants.filter { variant ->
                                variant.nameNormalized in deckNormalizedNames
                            }

                            // Store in DB
                            replaceCatalogForSeller(source.seller, matchingVariants)
                            markSellerFetched(source.seller)

                            mutex.withLock {
                                sellerStatuses[source.seller] = SellerSearchStatus(
                                    seller = source.seller,
                                    state = SearchState.DONE,
                                    cardsFound = matchingVariants.size,
                                )
                            }
                        }
                    } catch (e: Exception) {
                        mutex.withLock {
                            sellerStatuses[source.seller] = SellerSearchStatus(
                                seller = source.seller,
                                state = SearchState.ERROR,
                                message = e.message,
                            )
                        }
                    }

                    // After this supplier completes (success or failure),
                    // re-run matching with all available variants and emit progress
                    val progress = mutex.withLock {
                        buildProgress(includedEntries, sellerStatuses, config)
                    }
                    send(progress)
                }
            }
        }

        // Emit final complete state
        val finalProgress = mutex.withLock {
            buildProgress(includedEntries, sellerStatuses, config).copy(isComplete = true)
        }
        send(finalProgress)
    }

    /**
     * Builds a [SearchProgress] snapshot from the current state.
     */
    private fun buildProgress(
        includedEntries: List<DeckEntry>,
        sellerStatuses: Map<Seller, SellerSearchStatus>,
        config: MultiCatalogMatcher.Config,
    ): SearchProgress {
        // Build per-seller catalogs from all stored variants
        val allVariants = getAllVariants()
        val catalogs: Map<Seller, Catalog> = allVariants
            .groupBy { it.seller }
            .mapValues { (_, variants) -> Catalog(variants) }

        val multiMatches = MultiCatalogMatcher.match(
            entries = includedEntries,
            catalogs = catalogs,
            config = config,
        )

        val cardsWithResults = multiMatches.count { it.bestOption != null }

        return SearchProgress(
            totalCards = includedEntries.size,
            cardsWithResults = cardsWithResults,
            sellerStatuses = sellerStatuses.toMap(),
            multiMatches = multiMatches,
            isComplete = false,
        )
    }
}
