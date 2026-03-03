package state

import catalog.AgamecardshopProductMapper
import catalog.BootlegMageCatalogSource
import catalog.CatalogDataSource
import catalog.CatalogSource
import catalog.ImagePrefetchService
import catalog.ManaPoolCatalogSource
import catalog.ScryfallApi
import catalog.ScryfallPricingSource
import catalog.UseaCatalogSource
import database.CatalogStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.CardVariant
import model.Catalog
import model.Seller

/**
 * Registry that holds all available [CatalogSource] instances.
 * Coordinates loading catalogs from all sources with independent error handling.
 */
class CatalogSourceRegistry(
    private val sources: List<CatalogSource>
) {
    /** All registered sources. */
    val allSources: List<CatalogSource> get() = sources

    /**
     * Load catalogs from all sources in parallel.
     * Each source loads independently -- a failure in one does not block others.
     *
     * @param log callback for progress/error messages
     * @return map of successfully loaded catalogs keyed by [Seller]
     */
    suspend fun loadAll(
        log: (String, String) -> Unit
    ): Map<Seller, List<CardVariant>> = coroutineScope {
        val results = sources.map { source ->
            async {
                try {
                    log("Loading catalog from ${source.seller.displayName}...", "INFO")
                    val variants = source.fetchCatalog { msg -> log(msg, "INFO") }
                    if (variants.isNotEmpty()) {
                        log("${source.seller.displayName}: loaded ${variants.size} variants", "INFO")
                        source.seller to variants
                    } else {
                        log("${source.seller.displayName}: returned empty catalog, skipping", "WARNING")
                        null
                    }
                } catch (e: Exception) {
                    log("${source.seller.displayName}: failed to load catalog -- ${e.message}", "ERROR")
                    null
                }
            }
        }
        results.awaitAll().filterNotNull().toMap()
    }

    /**
     * Stream catalogs from all sources in parallel directly into the database.
     * Uses [CatalogSource.streamCatalog] so memory-intensive sources (like ManaPool)
     * never hold the full catalog in memory.
     *
     * @param storeBatch callback to persist a batch of variants for a seller
     * @param log callback for progress/error messages
     * @return list of [Seller]s that successfully loaded
     */
    suspend fun streamAll(
        storeBatch: suspend (Seller, List<CardVariant>) -> Unit,
        log: (String, String) -> Unit,
    ): List<Seller> = coroutineScope {
        val results = sources.map { source ->
            async {
                try {
                    log("Streaming catalog from ${source.seller.displayName}...", "INFO")
                    var count = 0
                    source.streamCatalog(
                        onBatch = { batch ->
                            count += batch.size
                            storeBatch(source.seller, batch)
                        },
                        log = { msg -> log(msg, "INFO") },
                    )
                    if (count > 0) {
                        log("${source.seller.displayName}: streamed $count variants", "INFO")
                        source.seller
                    } else {
                        log("${source.seller.displayName}: returned empty catalog, skipping", "WARNING")
                        null
                    }
                } catch (e: Exception) {
                    log("${source.seller.displayName}: failed to stream catalog -- ${e.message}", "ERROR")
                    null
                }
            }
        }
        results.awaitAll().filterNotNull()
    }
}

/**
 * Use case for catalog loading, caching, and image enrichment.
 *
 * Encapsulates all interactions with the remote catalog data source
 * and the local catalog store so the ViewModel stays thin.
 */
class CatalogUseCase(
    private val catalogStore: CatalogStore,
    private val platformServices: MviPlatformServices,
    private val httpClient: HttpClient? = null,
    private val scope: CoroutineScope? = null,
) {
    /**
     * Registry of all catalog sources.
     * UseaCatalogSource wraps platformServices for the USEA remote catalog.
     * ManaPoolCatalogSource is the primary real card source (bulk pricing).
     * ScryfallPricingSource provides TCGPlayer pricing via Scryfall API.
     */
    val sourceRegistry = CatalogSourceRegistry(
        listOf(
            UseaCatalogSource(object : CatalogDataSource {
                override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? =
                    platformServices.fetchCatalogFromRemote(log)
            }),
            BootlegMageCatalogSource(),
            ManaPoolCatalogSource(),
            ScryfallPricingSource(ScryfallApi),
        )
    )
    /**
     * Check how many variants are currently stored in the database.
     */
    suspend fun getVariantCount(): Long =
        withContext(Dispatchers.IO) { catalogStore.getVariantCount() }

    /**
     * Load the catalog from the remote API and store it in the database.
     *
     * @param log callback for progress messages
     * @return a [Result] indicating success or the exception that occurred
     */
    suspend fun loadCatalog(log: (String, String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                log("Loading catalog from remote API...", "INFO")
                val catalog = platformServices.fetchCatalogFromRemote { msg -> log(msg, "INFO") }
                if (catalog != null) {
                    catalogStore.replaceCatalog(catalog)
                    log("Catalog stored in database: ${catalog.variants.size} variants", "INFO")
                    Result.success(Unit)
                } else {
                    log("Failed to load catalog from remote", "ERROR")
                    Result.failure(IllegalStateException("Failed to load catalog from remote"))
                }
            } catch (e: Exception) {
                log("Catalog load exception: ${e.message}", "ERROR")
                Result.failure(e)
            }
        }

    /**
     * Load catalogs from all registered sources in parallel and persist per-seller.
     *
     * The USEA catalog is loaded via the existing platformServices path.
     * Other sources (BootlegMage, ScryfallPricing) are loaded through the registry.
     * Each source is independent -- failures are logged and skipped.
     *
     * @param log callback for progress/error messages
     * @return list of [Seller]s that successfully loaded catalogs
     */
    suspend fun loadAllCatalogs(log: (String, String) -> Unit): List<Seller> =
        withContext(Dispatchers.IO) {
            val loadedSellers = mutableListOf<Seller>()

            // Load USEA and registry sources in parallel
            coroutineScope {
                val useaDeferred = async {
                    try {
                        log("Loading USEA catalog via platform services...", "INFO")
                        val useaCatalog = platformServices.fetchCatalogFromRemote { msg -> log(msg, "INFO") }
                        if (useaCatalog != null && useaCatalog.variants.isNotEmpty()) {
                            val taggedVariants = useaCatalog.variants.map { it.copy(seller = Seller.USEA) }
                            Seller.USEA to taggedVariants
                        } else {
                            log("USEA: returned empty catalog, skipping", "WARNING")
                            null
                        }
                    } catch (e: Exception) {
                        log("USEA: failed to load catalog -- ${e.message}", "ERROR")
                        null
                    }
                }

                val registryDeferred = async {
                    // Clear seller data upfront, then stream batches directly into DB
                    val sellersToStream = sourceRegistry.allSources.map { it.seller }
                    for (seller in sellersToStream) {
                        catalogStore.replaceCatalogForSeller(seller, emptyList())
                    }
                    sourceRegistry.streamAll(
                        storeBatch = { _, batch ->
                            catalogStore.insertVariantBatch(batch)
                        },
                        log = log,
                    )
                }

                // Await USEA and store
                val useaResult = useaDeferred.await()
                if (useaResult != null) {
                    val (seller, variants) = useaResult
                    catalogStore.replaceCatalogForSeller(seller, variants)
                    log("USEA: stored ${variants.size} variants", "INFO")
                    loadedSellers.add(seller)

                    // Fire-and-forget: map WooCommerce product IDs in background
                    if (httpClient != null && scope != null) {
                        scope.launch {
                            try {
                                AgamecardshopProductMapper.mapAll(httpClient, catalogStore, log)
                            } catch (e: Exception) {
                                log("WC product mapping failed: ${e.message}", "WARNING")
                            }
                        }
                    }
                }

                // Await registry streaming results
                val streamedSellers = registryDeferred.await()
                for (seller in streamedSellers) {
                    log("${seller.displayName}: stored variants in database via streaming", "INFO")
                    loadedSellers.add(seller)
                }
            }

            log("Loaded catalogs from ${loadedSellers.size} seller(s): ${loadedSellers.joinToString { it.displayName }}", "INFO")
            loadedSellers
        }

    private var _activePrefetchService: ImagePrefetchService? = null

    /**
     * Start the batch image prefetch pipeline.
     * Should be called once when the ViewModel scope is available.
     */
    fun startImagePrefetch(scope: CoroutineScope, log: (String, String) -> Unit) {
        val service = ImagePrefetchService(
            catalogStore = catalogStore,
            log = log,
        )
        service.start(scope)
        _activePrefetchService = service
    }

    /**
     * Request batch image prefetch for a list of variants.
     * Variants that already have images are automatically skipped by the service.
     */
    suspend fun prefetchImages(variants: List<CardVariant>) {
        _activePrefetchService?.requestPrefetch(variants)
    }

    companion object {
        /**
         * Refresh match data with the latest variant information from the catalog.
         *
         * When variants are updated in the database (e.g. image URLs enriched),
         * this ensures the match list reflects those changes.
         */
        fun refreshMatchesFromCatalog(
            matches: List<model.DeckEntryMatch>,
            catalog: Catalog
        ): List<model.DeckEntryMatch> {
            if (matches.isEmpty() || catalog.variants.isEmpty()) return matches

            val variantsBySku = catalog.variants.associateBy { it.sku }

            return matches.map { match ->
                val refreshedSelectedVariant = match.selectedVariant?.let { oldVariant ->
                    variantsBySku[oldVariant.sku] ?: oldVariant
                }

                val refreshedCandidates = match.candidates.map { candidate ->
                    val refreshedVariant = variantsBySku[candidate.variant.sku] ?: candidate.variant
                    candidate.copy(variant = refreshedVariant)
                }

                match.copy(
                    selectedVariant = refreshedSelectedVariant,
                    candidates = refreshedCandidates
                )
            }
        }

        /**
         * Refresh multi-match data with the latest variant information from the catalog.
         *
         * Same purpose as [refreshMatchesFromCatalog] but for [model.MultiMatch] objects
         * used by the alt detail screen and shopping optimizer.
         */
        fun refreshMultiMatchesFromCatalog(
            multiMatches: List<model.MultiMatch>,
            catalog: Catalog
        ): List<model.MultiMatch> {
            if (multiMatches.isEmpty() || catalog.variants.isEmpty()) return multiMatches

            val variantsBySku = catalog.variants.associateBy { it.sku }

            return multiMatches.map { mm ->
                val refreshedBest = mm.bestOption?.let { refreshMatchOption(it, variantsBySku) }
                val refreshedAlts = mm.alternatives.map { refreshMatchOption(it, variantsBySku) }
                val refreshedFallback = mm.realCardFallback?.let { refreshMatchOption(it, variantsBySku) }
                mm.copy(
                    bestOption = refreshedBest,
                    alternatives = refreshedAlts,
                    realCardFallback = refreshedFallback,
                )
            }
        }

        private fun refreshMatchOption(
            option: model.MatchOption,
            variantsBySku: Map<String, model.CardVariant>,
        ): model.MatchOption {
            val refreshed = variantsBySku[option.variant.sku] ?: option.variant
            return option.copy(variant = refreshed, priceCents = refreshed.priceInCents)
        }
    }
}
