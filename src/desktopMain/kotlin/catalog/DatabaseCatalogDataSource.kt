package catalog

import model.Catalog
import model.CardVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Example implementation of a database-backed catalog data source.
 * 
 * This is a template/example showing how to implement CatalogDataSource
 * for a database. Replace the TODO sections with your actual database logic.
 * 
 * Usage:
 * ```
 * // Initialize with your database configuration
 * val dbSource = DatabaseCatalogDataSource(
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/mtgdb",
 *     username = "mtg_user",
 *     password = "your_password"
 * )
 * 
 * // Configure CatalogFetcher to use it
 * CatalogFetcher.setDataSource(dbSource)
 * 
 * // Use normally
 * val catalog = CatalogFetcher.load()
 * ```
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
                
                // TODO: Replace with your actual database connection logic
                // Examples:
                // - JDBC: DriverManager.getConnection(jdbcUrl, username, password)
                // - Exposed: Database.connect(jdbcUrl, user = username, password = password)
                // - SQLDelight: driver.createConnection()
                
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
    
    private fun loadVariantsFromDatabase(log: (String) -> Unit): List<CardVariant> {
        // TODO: Implement actual database query
        // Example with JDBC:
        /*
        val connection = DriverManager.getConnection(jdbcUrl, username, password)
        val statement = connection.prepareStatement("""
            SELECT 
                name_original, 
                name_normalized, 
                set_code, 
                sku, 
                variant_type, 
                price_cents, 
                collector_number, 
                image_url
            FROM card_variants
            WHERE active = true
            ORDER BY name_normalized, set_code
        """)
        
        val results = mutableListOf<CardVariant>()
        val resultSet = statement.executeQuery()
        
        while (resultSet.next()) {
            results.add(CardVariant(
                nameOriginal = resultSet.getString("name_original"),
                nameNormalized = resultSet.getString("name_normalized"),
                setCode = resultSet.getString("set_code"),
                sku = resultSet.getString("sku"),
                variantType = resultSet.getString("variant_type"),
                priceInCents = resultSet.getInt("price_cents"),
                collectorNumber = resultSet.getString("collector_number").takeIf { !resultSet.wasNull() },
                imageUrl = resultSet.getString("image_url").takeIf { !resultSet.wasNull() }
            ))
        }
        
        resultSet.close()
        statement.close()
        connection.close()
        
        return results
        */
        
        // For now, return empty list as this is a template
        log("WARNING: DatabaseCatalogDataSource is using template implementation")
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
