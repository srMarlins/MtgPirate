package catalog

import model.CardVariant
import model.OrderItem
import model.Seller

/**
 * Abstraction for multi-seller catalog sources.
 * Each implementation represents a different card seller.
 */
interface CatalogSource {
    val seller: Seller
    suspend fun fetchCatalog(log: (String) -> Unit = {}): List<CardVariant>
    suspend fun search(cardName: String): List<CardVariant>
    fun checkoutUrl(items: List<OrderItem>): String?
    fun formatForExport(items: List<OrderItem>): String
}
