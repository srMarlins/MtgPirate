package state

import catalog.ScryfallImageEnricher
import database.CatalogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import model.CardVariant
import model.Catalog

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
