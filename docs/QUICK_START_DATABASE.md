# Quick Start: Swapping to Database Backend

This guide shows you how to quickly swap from the remote HTTP/CSV catalog to a database-backed implementation.

## Step 1: Choose Your Database

First, decide which database you want to use. Common choices:
- PostgreSQL (recommended for production)
- MySQL/MariaDB
- SQLite (for local/embedded use)
- H2 (for testing)

## Step 2: Add Database Dependencies

Add your database driver to `build.gradle.kts`:

```kotlin
// In the desktopMain dependencies block
val desktopMain by getting {
    dependencies {
        // Existing dependencies...
        
        // Add your database driver
        implementation("org.postgresql:postgresql:42.7.1") // For PostgreSQL
        // OR
        implementation("mysql:mysql-connector-java:8.0.33") // For MySQL
        // OR
        implementation("org.xerial:sqlite-jdbc:3.44.1.0") // For SQLite
    }
}
```

## Step 3: Create Database Schema

Create your database tables. Example SQL:

```sql
CREATE TABLE card_variants (
    id SERIAL PRIMARY KEY,
    name_original VARCHAR(255) NOT NULL,
    name_normalized VARCHAR(255) NOT NULL,
    set_code VARCHAR(10) NOT NULL,
    sku VARCHAR(50) NOT NULL UNIQUE,
    variant_type VARCHAR(20) NOT NULL,
    price_cents INTEGER NOT NULL DEFAULT 0,
    collector_number VARCHAR(20),
    image_url TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for performance
CREATE INDEX idx_name_normalized ON card_variants(name_normalized);
CREATE INDEX idx_set_code ON card_variants(set_code);
CREATE INDEX idx_sku ON card_variants(sku);
CREATE INDEX idx_active ON card_variants(active);
```

## Step 4: Implement Your Data Source

Edit `src/desktopMain/kotlin/catalog/DatabaseCatalogDataSource.kt` and replace the TODO sections:

```kotlin
package catalog

import model.Catalog
import model.CardVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.DriverManager

class DatabaseCatalogDataSource(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String
) : CatalogDataSource {
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? = 
        withContext(Dispatchers.IO) {
            try {
                log("Loading catalog from database: $jdbcUrl")
                
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
                        collectorNumber = resultSet.getString("collector_number")
                            .takeIf { !resultSet.wasNull() },
                        imageUrl = resultSet.getString("image_url")
                            .takeIf { !resultSet.wasNull() }
                    ))
                }
                
                resultSet.close()
                statement.close()
                connection.close()
                
                val catalog = Catalog(variants = results)
                log("Loaded ${catalog.variants.size} variants from database")
                catalog
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                e.printStackTrace()
                null
            }
        }
}
```

## Step 5: Configure the Data Source

In `src/desktopMain/kotlin/platform/DesktopPlatformServices.kt`, configure the data source on initialization:

```kotlin
class DesktopPlatformServices : PlatformServices {
    
    init {
        // Configure database data source
        val dbSource = DatabaseCatalogDataSource(
            jdbcUrl = System.getenv("DB_URL") 
                ?: "jdbc:postgresql://localhost:5432/mtgpirate",
            username = System.getenv("DB_USER") ?: "mtg_user",
            password = System.getenv("DB_PASSWORD") ?: "your_password"
        )
        
        // Swap in the database implementation
        CatalogFetcher.setDataSource(dbSource)
    }
    
    // ... rest of the class remains unchanged
}
```

## Step 6: Populate the Database

Create a one-time migration script to populate your database from the remote source:

```kotlin
// In a separate migration tool or script
fun main() = runBlocking {
    // Load from remote
    val remoteSource = RemoteCatalogDataSource()
    val catalog = remoteSource.load(forceRefresh = true) { println(it) }
    
    if (catalog != null) {
        // Connect to database
        val connection = DriverManager.getConnection(jdbcUrl, username, password)
        val insertStmt = connection.prepareStatement("""
            INSERT INTO card_variants 
                (name_original, name_normalized, set_code, sku, variant_type, 
                 price_cents, collector_number, image_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (sku) DO UPDATE SET
                price_cents = EXCLUDED.price_cents,
                updated_at = CURRENT_TIMESTAMP
        """)
        
        catalog.variants.forEach { variant ->
            insertStmt.setString(1, variant.nameOriginal)
            insertStmt.setString(2, variant.nameNormalized)
            insertStmt.setString(3, variant.setCode)
            insertStmt.setString(4, variant.sku)
            insertStmt.setString(5, variant.variantType)
            insertStmt.setInt(6, variant.priceInCents)
            insertStmt.setString(7, variant.collectorNumber)
            insertStmt.setString(8, variant.imageUrl)
            insertStmt.executeUpdate()
        }
        
        connection.close()
        println("Successfully migrated ${catalog.variants.size} variants to database")
    }
}
```

## Step 7: Test It

Run your application and verify:

1. The catalog loads from the database
2. All card searches work correctly
3. Performance is acceptable
4. Error handling works (try disconnecting the database)

## Optional: Hybrid Approach

For a safer migration, use the hybrid approach:

```kotlin
init {
    val dbSource = DatabaseCatalogDataSource(...)
    val remoteSource = RemoteCatalogDataSource()
    
    // Try database first, fallback to remote
    val hybridSource = HybridCatalogDataSource(dbSource, remoteSource)
    CatalogFetcher.setDataSource(hybridSource)
}
```

This way:
- ✅ Fast loading from database when available
- ✅ Automatic fallback to remote if database fails
- ✅ No downtime during migration
- ✅ Can validate database data against remote

## Rollback Plan

If you need to rollback to the remote source:

```kotlin
// Simply restore the default
CatalogFetcher.setDataSource(RemoteCatalogDataSource())
```

That's it! Your application now uses a database for catalog data.

## Advanced: Connection Pooling

For production, use a connection pool:

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class PooledDatabaseCatalogDataSource(
    jdbcUrl: String,
    username: String,
    password: String
) : CatalogDataSource {
    
    private val dataSource: HikariDataSource
    
    init {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
        }
        dataSource = HikariDataSource(config)
    }
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? = 
        withContext(Dispatchers.IO) {
            try {
                dataSource.connection.use { connection ->
                    // Use connection...
                }
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                null
            }
        }
}
```

Remember to add HikariCP dependency:
```kotlin
implementation("com.zaxxer:HikariCP:5.1.0")
```
