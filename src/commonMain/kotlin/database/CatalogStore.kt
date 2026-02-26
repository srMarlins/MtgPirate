package database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.CardVariant
import model.Catalog
import model.Seller

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
    suspend fun updateVariantImageUrl(sku: String, imageUrl: String) {
        database.updateVariantImageUrl(sku, imageUrl)
    }

    /**
     * Replace catalog data for a specific seller.
     * Deletes all existing variants for the seller, then inserts the new ones.
     */
    suspend fun replaceCatalogForSeller(seller: Seller, variants: List<CardVariant>) {
        database.replaceCatalogForSellerTransaction(seller.name, variants.map { it.copy(seller = seller) })
    }
}
