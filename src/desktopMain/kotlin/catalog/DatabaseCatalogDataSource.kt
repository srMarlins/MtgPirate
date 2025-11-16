package catalog

import model.Catalog
import model.CardVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Template implementation for a database-backed catalog data source.
 * 
 * This is an EXAMPLE/TEMPLATE showing the interface for implementing CatalogDataSource
 * with a database backend. This template implementation returns empty results.
 * 
 * To use this with a real database:
 * 1. Uncomment and adapt the database connection code in loadVariantsFromDatabase()
 * 2. Replace the sample SQL query with your actual schema
 * 3. Add the appropriate JDBC or database driver dependencies
 * 4. Configure and inject this into CatalogFetcher
 * 
 * Example usage:
 * ```kotlin
 * val dbSource = DatabaseCatalogDataSource(
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/mtgdb",
 *     username = "mtg_user",
 *     password = "your_password"
 * )
 * CatalogFetcher.setDataSource(dbSource)
 * val catalog = CatalogFetcher.load()
 * ```
 * 
 * See docs/CATALOG_DATA_SOURCE.md and docs/QUICK_START_DATABASE.md for detailed
 * implementation guidance.
 */
class DatabaseCatalogDataSource(
    private val jdbcUrl: String,
    @Suppress("UnusedPrivateProperty")
    private val username: String,
    @Suppress("UnusedPrivateProperty")
    private val password: String
) : CatalogDataSource {
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? = 
        withContext(Dispatchers.IO) {
            try {
                log("Loading catalog from database: $jdbcUrl")
                
                // TEMPLATE: Replace with actual database connection
                // 
                // Examples for different database libraries:
                // 
                // JDBC:
                //   val connection = DriverManager.getConnection(jdbcUrl, username, password)
                // 
                // Exposed (Kotlin SQL DSL):
                //   Database.connect(jdbcUrl, user = username, password = password)
                // 
                // SQLDelight (for consistent API across platforms):
                //   val driver = JdbcSqliteDriver(jdbcUrl)
                //   Database(driver)
                
                val variants = loadVariantsFromDatabase(log)
                
                val catalog = Catalog(variants = variants)
                log("Loaded ${catalog.variants.size} variants from database")
                
                catalog
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    
    /**
     * Template method for loading card variants from database.
     * 
     * IMPLEMENTATION NOTE: This is a template that returns an empty list.
     * Uncomment and adapt the example code below for your actual database schema.
     * 
     * Example implementation with JDBC:
     */
    private fun loadVariantsFromDatabase(log: (String) -> Unit): List<CardVariant> {
        /*
         * TEMPLATE CODE - Uncomment and adapt for production use:
         * 
         * val connection = DriverManager.getConnection(jdbcUrl, username, password)
         * val statement = connection.prepareStatement("""
         *     SELECT 
         *         name_original, 
         *         name_normalized, 
         *         set_code, 
         *         sku, 
         *         variant_type, 
         *         price_cents, 
         *         collector_number, 
         *         image_url
         *     FROM card_variants
         *     WHERE active = true
         *     ORDER BY name_normalized, set_code
         * """)
         * 
         * val results = mutableListOf<CardVariant>()
         * val resultSet = statement.executeQuery()
         * 
         * while (resultSet.next()) {
         *     results.add(CardVariant(
         *         nameOriginal = resultSet.getString("name_original"),
         *         nameNormalized = resultSet.getString("name_normalized"),
         *         setCode = resultSet.getString("set_code"),
         *         sku = resultSet.getString("sku"),
         *         variantType = resultSet.getString("variant_type"),
         *         priceInCents = resultSet.getInt("price_cents"),
         *         collectorNumber = resultSet.getString("collector_number").takeIf { !resultSet.wasNull() },
         *         imageUrl = resultSet.getString("image_url").takeIf { !resultSet.wasNull() }
         *     ))
         * }
         * 
         * resultSet.close()
         * statement.close()
         * connection.close()
         * 
         * return results
         */
        
        // Template implementation - returns empty list
        log("WARNING: Using template DatabaseCatalogDataSource (returns empty catalog)")
        log("See class documentation for implementation instructions")
        return emptyList()
    }
}

/**
 * Example of a hybrid data source that tries database first, then falls back to remote.
 * This is useful during migration or for redundancy.
 */
class HybridCatalogDataSource(
    private val databaseSource: DatabaseCatalogDataSource,
    private val remoteSource: RemoteCatalogDataSource = RemoteCatalogDataSource()
) : CatalogDataSource {
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? {
        // Try database first
        log("Attempting to load catalog from database...")
        val dbCatalog = databaseSource.load(forceRefresh, log)
        
        if (dbCatalog != null && dbCatalog.variants.isNotEmpty()) {
            log("Successfully loaded from database")
            return dbCatalog
        }
        
        // Fallback to remote
        log("Database unavailable or empty, falling back to remote source...")
        return remoteSource.load(forceRefresh, log)
    }
}
