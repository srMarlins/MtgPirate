package catalog

import model.*
import match.NameNormalizer

/**
 * Catalog source that fetches pricing and card data from Scryfall API.
 * Primarily used as the source of truth for "Real Card" prices (via TCGPlayer mapping).
 */
class ScryfallPricingSource(
    private val scryfallApi: ScryfallApi,
) : CatalogSource {
    override val seller = Seller.TCGPLAYER

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        // Scryfall is on-demand only — no full catalog fetch
        return emptyList()
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        val cards = scryfallApi.getCollection(
            listOf(mapOf("name" to cardName))
        )
        return cards.flatMap { card -> cardToVariants(card) }
    }

    /**
     * Efficiently fetch multiple cards by name in bulk.
     * Maps the results back to normalized card names.
     */
    suspend fun searchBulk(cardNames: List<String>): Map<String, List<CardVariant>> {
        val identifiers = cardNames.distinct().map { mapOf("name" to it) }
        val cards = scryfallApi.getCollection(identifiers)
        return cards.groupBy(
            keySelector = { NameNormalizer.normalize(it.name) },
            valueTransform = { card -> cardToVariants(card) }
        ).mapValues { it.value.flatten() }
    }

    private fun cardToVariants(card: ScryfallApi.ScryfallCard): List<CardVariant> {
        val variants = mutableListOf<CardVariant>()
        val usdPrice = card.prices?.usd?.toDoubleOrNull()
        val usdFoilPrice = card.prices?.usdFoil?.toDoubleOrNull()

        if (usdPrice != null) {
            variants.add(CardVariant(
                nameOriginal = card.name,
                nameNormalized = NameNormalizer.normalize(card.name),
                setCode = card.set.uppercase(),
                sku = "SCRY-${card.set}-${card.collectorNumber}",
                variantType = VariantType.REGULAR,
                priceInCents = (usdPrice * 100).toInt(),
                collectorNumber = card.collectorNumber,
                imageUrl = extractImageUrl(card),
                seller = Seller.TCGPLAYER,
                purchaseUri = card.purchaseUris?.tcgplayer,
            ))
        }
        if (usdFoilPrice != null) {
            variants.add(CardVariant(
                nameOriginal = card.name,
                nameNormalized = NameNormalizer.normalize(card.name),
                setCode = card.set.uppercase(),
                sku = "SCRY-${card.set}-${card.collectorNumber}-FOIL",
                variantType = VariantType.FOIL,
                priceInCents = (usdFoilPrice * 100).toInt(),
                collectorNumber = card.collectorNumber,
                imageUrl = extractImageUrl(card),
                seller = Seller.TCGPLAYER,
                purchaseUri = card.purchaseUris?.tcgplayer,
            ))
        }
        return variants
    }

    override fun checkoutUrl(items: List<OrderItem>): String? {
        // Return first purchase URI if available (TCGPlayer mass entry isn't easily linkable via API)
        return items.firstOrNull { it.variant.purchaseUri != null }?.variant?.purchaseUri
    }

    override fun formatForExport(items: List<OrderItem>): String {
        // TCGPlayer mass entry format: "1 Lightning Bolt [M11]"
        return items.joinToString("\n") {
            "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
        }
    }

    private fun extractImageUrl(card: ScryfallApi.ScryfallCard): String? {
        return card.imageUris?.normal ?: card.cardFaces?.firstOrNull()?.imageUris?.normal
    }
}
