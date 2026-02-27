package state

import catalog.BootlegMageCatalogSource
import catalog.CatalogSource
import catalog.ManaPoolCatalogSource
import catalog.ScryfallImageEnricher
import database.CatalogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
}

/**
 * Use case for catalog loading, caching, and image enrichment.
 *
 * Encapsulates all interactions with the remote catalog data source
 * and the local catalog store so the ViewModel stays thin.
 */
class CatalogUseCase(
    private val catalogStore: CatalogStore,
    private val platformServices: MviPlatformServices
) {
    /**
     * Registry of multi-seller catalog sources.
     * BootlegMageCatalogSource and ScryfallPricingSource are pure Ktor-based and
     * can be instantiated directly. UseaCatalogSource uses the existing
     * platformServices.fetchCatalogFromRemote() path and is handled separately.
     */
    val sourceRegistry = CatalogSourceRegistry(
        listOf(
            BootlegMageCatalogSource(),
            ManaPoolCatalogSource(),
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

            // Load USEA via existing platform path
            try {
                log("Loading USEA catalog via platform services...", "INFO")
                val useaCatalog = platformServices.fetchCatalogFromRemote { msg -> log(msg, "INFO") }
                if (useaCatalog != null && useaCatalog.variants.isNotEmpty()) {
                    val taggedVariants = useaCatalog.variants.map { it.copy(seller = Seller.USEA) }
                    catalogStore.replaceCatalogForSeller(Seller.USEA, taggedVariants)
                    log("USEA: stored ${taggedVariants.size} variants", "INFO")
                    loadedSellers.add(Seller.USEA)
                } else {
                    log("USEA: returned empty catalog, skipping", "WARNING")
                }
            } catch (e: Exception) {
                log("USEA: failed to load catalog -- ${e.message}", "ERROR")
            }

            // Load all registry sources in parallel
            val registryResults = sourceRegistry.loadAll(log)
            for ((seller, variants) in registryResults) {
                try {
                    catalogStore.replaceCatalogForSeller(seller, variants)
                    log("${seller.displayName}: stored ${variants.size} variants in database", "INFO")
                    loadedSellers.add(seller)
                } catch (e: Exception) {
                    log("${seller.displayName}: failed to store catalog -- ${e.message}", "ERROR")
                }
            }

            log("Loaded catalogs from ${loadedSellers.size} seller(s): ${loadedSellers.joinToString { it.displayName }}", "INFO")
            loadedSellers
        }

    /**
     * Fetch an image URL from Scryfall for the given variant and persist it.
     *
     * @return the enriched [CardVariant], or the original if enrichment failed
     */
    suspend fun enrichVariantWithImage(
        variant: CardVariant,
        log: (String, String) -> Unit
    ): CardVariant {
        if (variant.imageUrl != null) return variant

        return try {
            log("Fetching image for ${variant.nameOriginal} (${variant.setCode})...", "DEBUG")
            val enrichedVariant = ScryfallImageEnricher.enrichVariant(
                variant = variant,
                imageSize = "normal",
                log = { msg -> log(msg, "DEBUG") }
            )
            if (enrichedVariant.imageUrl != null) {
                catalogStore.updateVariantImageUrl(variant.sku, enrichedVariant.imageUrl)
                log("Updated image URL for ${variant.nameOriginal}", "DEBUG")
            }
            enrichedVariant
        } catch (e: Exception) {
            log("Failed to enrich variant ${variant.nameOriginal}: ${e.message}", "DEBUG")
            variant
        }
    }

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
}
