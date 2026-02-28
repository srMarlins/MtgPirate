package database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.CardVariant
import model.Catalog
import model.Seller
import platform.currentTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Utility functions for seller cache TTL calculations.
 * Proxy sellers (BM, USEA) use a 7-day TTL; real card sellers (ManaPool, TCGPlayer) use 24 hours.
 */
object CacheUtils {
    fun ttlForSeller(seller: Seller): Duration =
        if (seller.isProxy) 7.days else 24.hours

    fun isFresh(
        fetchedAtMillis: Long,
        ttl: Duration,
        nowMillis: Long = currentTimeMillis(),
    ): Boolean = (nowMillis - fetchedAtMillis) < ttl.inWholeMilliseconds
}

/**
 * Database store for catalog card variants.
 * Provides reactive Flow-based access to the catalog data stored in the database.
 * This makes the database the single source of truth for catalog data.
 */
class CatalogStore(private val database: Database) {
    
    /**
     * Observe all card variants as a Catalog from the database.
     * This Flow emits whenever the database catalog changes.
     */
    fun observeCatalog(): Flow<Catalog> = 
        database.observeCardVariants()
            .map { variants -> Catalog(variants) }
    
    /**
     * Insert or replace the entire catalog in the database.
     * This is typically called after fetching from a remote source.
     */
    suspend fun replaceCatalog(catalog: Catalog) {
        database.replaceCatalogTransaction(catalog.variants)
    }
    
    /**
     * Get the current count of variants in the database.
     */
    suspend fun getVariantCount(): Long {
        return database.getVariantCount()
    }
    
    /**
     * Clear all variants from the database.
     */
    suspend fun clearCatalog() {
        database.clearAllVariants()
    }
    
    /**
     * Update the image URL for a specific card variant by SKU.
     * This is used to lazily enrich variants with Scryfall image URLs.
     */
    suspend fun updateVariantImageUrl(sku: String, imageUrl: String, seller: String) {
        database.updateVariantImageUrl(sku, imageUrl, seller)
    }

    /**
     * Get all card variants currently stored in the database (blocking read).
     */
    fun getAllVariants(): List<CardVariant> {
        return database.getAllVariants()
    }

    /**
     * Replace catalog data for a specific seller.
     * Deletes all existing variants for the seller, then inserts the new ones.
     */
    suspend fun replaceCatalogForSeller(seller: Seller, variants: List<CardVariant>) {
        database.replaceCatalogForSellerTransaction(seller.name, variants.map { it.copy(seller = seller) })
    }

    /**
     * Insert a batch of variants in a single transaction.
     * Used by streaming catalog loads to persist incrementally.
     */
    fun insertVariantBatch(variants: List<CardVariant>) {
        database.insertVariantBatch(variants)
    }

    /**
     * Replace catalog data for a seller using a streaming function.
     * Clears existing variants first, then calls [streamFn] which should invoke
     * the batch callback to insert variants incrementally.
     */
    suspend fun replaceSellerStreaming(
        seller: Seller,
        streamFn: suspend (onBatch: suspend (List<CardVariant>) -> Unit) -> Unit,
    ) {
        database.clearVariantsBySeller(seller.name)
        streamFn { batch ->
            val tagged = batch.map { it.copy(seller = seller) }
            database.insertVariantBatch(tagged)
        }
    }

    /**
     * Check whether the cached data for a seller is still fresh based on its TTL.
     * Returns false if no cache entry exists for the seller.
     */
    fun isSellerCacheFresh(seller: Seller): Boolean {
        val fetchedAt = database.getSellerCacheTimestamp(seller.name) ?: return false
        return CacheUtils.isFresh(fetchedAt, CacheUtils.ttlForSeller(seller))
    }

    /**
     * Record the current time as the last-fetched timestamp for a seller.
     */
    fun markSellerFetched(seller: Seller) {
        database.upsertSellerCache(seller.name, currentTimeMillis())
    }
}
