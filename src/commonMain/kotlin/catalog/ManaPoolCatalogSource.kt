package catalog

import match.NameNormalizer
import model.CardVariant
import model.OrderItem
import model.Seller
import model.VariantType

/**
 * Catalog source for ManaPool (manapool.com).
 *
 * Uses the public bulk pricing endpoint /api/v1/prices/singles
 * which returns ~95K singles with real-time pricing.
 * Prices are already in cents (integer).
 */
class ManaPoolCatalogSource(
    private val api: ManaPoolApi = ManaPoolApi,
) : CatalogSource {
    override val seller = Seller.MANAPOOL

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        log("Fetching ManaPool catalog...")
        val singles = api.fetchAllSingles()
        log("ManaPool: received ${singles.size} singles")
        val variants = singles.flatMap { it.toCardVariants() }
        log("ManaPool: ${variants.size} variants after expansion")
        return variants
    }

    override suspend fun streamCatalog(
        onBatch: suspend (List<CardVariant>) -> Unit,
        log: (String) -> Unit,
    ) {
        log("Fetching ManaPool catalog (gzip)...")

        // Stream singles from the API with lazy JSON parsing — each batch of
        // ManaPoolSingle objects is parsed individually from the JSON body string,
        // avoiding the ~511MB peak from deserializing all 95K objects at once.
        var totalVariants = 0
        api.streamSingles(batchSize = 500) { singlesBatch ->
            val variants = singlesBatch.flatMap { it.toCardVariants() }
            totalVariants += variants.size
            if (variants.isNotEmpty()) {
                onBatch(variants)
            }
        }
        log("ManaPool: $totalVariants variants total")
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        // No public search endpoint — catalog-only
        return emptyList()
    }

    override fun checkoutUrl(items: List<OrderItem>): String = "https://manapool.com"

    override fun formatForExport(items: List<OrderItem>): String {
        return items.joinToString("\n") {
            "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
        }
    }
}

internal fun ManaPoolSingle.toCardVariants(): List<CardVariant> {
    val results = mutableListOf<CardVariant>()
    val normalized = NameNormalizer.normalize(name)
    val setUpper = setCode.uppercase()
    val num = number ?: "0"

    if (priceCents != null && priceCents > 0) {
        results += CardVariant(
            nameOriginal = name,
            nameNormalized = normalized,
            setCode = setUpper,
            sku = "MP-$setUpper-$num-R",
            variantType = VariantType.REGULAR,
            priceInCents = priceCents,
            collectorNumber = number,
            seller = Seller.MANAPOOL,
            purchaseUri = url,
        )
    }
    if (priceCentsFoil != null && priceCentsFoil > 0) {
        results += CardVariant(
            nameOriginal = name,
            nameNormalized = normalized,
            setCode = setUpper,
            sku = "MP-$setUpper-$num-F",
            variantType = VariantType.FOIL,
            priceInCents = priceCentsFoil,
            collectorNumber = number,
            seller = Seller.MANAPOOL,
            purchaseUri = url,
        )
    }
    if (priceCentsEtched != null && priceCentsEtched > 0) {
        results += CardVariant(
            nameOriginal = name,
            nameNormalized = normalized,
            setCode = setUpper,
            sku = "MP-$setUpper-$num-E",
            variantType = VariantType.HOLO,
            priceInCents = priceCentsEtched,
            collectorNumber = number,
            seller = Seller.MANAPOOL,
            purchaseUri = url,
        )
    }
    return results
}
