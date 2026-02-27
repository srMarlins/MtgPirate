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

    /** Whether this source supports bulk search by card names (instead of full catalog fetch). */
    val supportsBulkSearch: Boolean get() = false

    suspend fun fetchCatalog(log: (String) -> Unit = {}): List<CardVariant>
    suspend fun search(cardName: String): List<CardVariant>

    /**
     * Bulk search for multiple card names at once.
     * Sources that support this should override [supportsBulkSearch] to return true.
     */
    suspend fun searchBulk(cardNames: List<String>, log: (String) -> Unit = {}): List<CardVariant> = emptyList()

    fun checkoutUrl(items: List<OrderItem>): String?
    fun formatForExport(items: List<OrderItem>): String
}
