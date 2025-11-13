package database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import model.Catalog
import model.CardVariant

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
        // Clear existing variants
        database.clearAllVariants()
        
        // Insert all new variants
        catalog.variants.forEach { variant ->
            database.insertVariant(variant)
        }
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
}
