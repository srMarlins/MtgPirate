package catalog

import model.CardVariant
import model.OrderItem
import model.Seller

/**
 * Scryfall-based pricing source for TCGPlayer.
 * (Stub implementation for TDD scaffold)
 */
class ScryfallPricingSource : CatalogSource {
    override val seller: Seller = Seller.TCGPLAYER

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        return emptyList()
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        return emptyList()
    }

    override fun checkoutUrl(items: List<OrderItem>): String? {
        return null
    }

    override fun formatForExport(items: List<OrderItem>): String {
        return ""
    }

    /**
     * Internal helper to convert Scryfall card data to variants.
     * Exposed for testing in this scaffold.
     */
    fun cardToVariants(card: ScryfallApi.ScryfallCard): List<CardVariant> {
        return emptyList()
    }
}
