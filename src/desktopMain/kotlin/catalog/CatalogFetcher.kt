package catalog

import model.Catalog

/**
 * Facade for loading the catalog using a pluggable data source.
 * This maintains backward compatibility while allowing different implementations.
 */
object CatalogFetcher {
    // Default data source - can be swapped for database implementation or other sources
    private var dataSource: CatalogDataSource = RemoteCatalogDataSource()

    /**
     * Set a custom data source for catalog loading.
     * This allows swapping between remote, database, or mock implementations.
     */
    fun setDataSource(source: CatalogDataSource) {
        dataSource = source
    }

    /**
     * Load the catalog using the configured data source.
     * Force refresh bypasses cache if supported by the data source.
     */
    suspend fun load(forceRefresh: Boolean = false, log: (String) -> Unit = {}): Catalog? {
        return dataSource.load(forceRefresh, log)
    }
}
