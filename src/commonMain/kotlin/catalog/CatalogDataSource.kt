package catalog

import model.Catalog

/**
 * Abstraction for catalog data sources.
 * This interface enables different implementations (remote HTML/CSV, database, mock, etc.)
 * without changing the core application logic.
 */
interface CatalogDataSource {
    /**
     * Load the catalog from this data source.
     * 
     * @param forceRefresh if true, bypass any caching and fetch fresh data
     * @param log callback for logging messages during the load operation
     * @return the loaded Catalog, or null if loading failed
     */
    suspend fun load(forceRefresh: Boolean = false, log: (String) -> Unit = {}): Catalog?
}
