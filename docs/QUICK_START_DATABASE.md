# Database Backend Quick Start

## Table of Contents

- [Step 1: Choose Database](#step-1-choose-database)
- [Step 2: Add Dependencies](#step-2-add-dependencies)
- [Step 3: Create Schema](#step-3-create-schema)
- [Step 4: Implement Data Source](#step-4-implement-data-source)
- [Step 5: Configure](#step-5-configure)
- [Step 6: Populate Database](#step-6-populate-database)
- [Step 7: Test](#step-7-test)
- [Hybrid Approach](#hybrid-approach)
- [Advanced Topics](#advanced-topics)

## Step 1: Choose Database

Common choices:
- **PostgreSQL** (recommended for production)
- **MySQL/MariaDB**
- **SQLite** (for local/embedded)
- **H2** (for testing)

## Step 2: Add Dependencies

Add database driver to `build.gradle.kts`:

```kotlin
val desktopMain by getting {
    dependencies {
        // PostgreSQL
        implementation("org.postgresql:postgresql:42.7.1")
        
        // OR MySQL
        implementation("mysql:mysql-connector-java:8.0.33")
        
        // OR SQLite
        implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    }
}
```

## Step 3: Create Schema

Create database tables:

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

-- Performance indexes
CREATE INDEX idx_name_normalized ON card_variants(name_normalized);
CREATE INDEX idx_set_code ON card_variants(set_code);
CREATE INDEX idx_sku ON card_variants(sku);
CREATE INDEX idx_active ON card_variants(active);
```

## Step 4: Implement Data Source

Edit `src/desktopMain/kotlin/catalog/DatabaseCatalogDataSource.kt`:

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
                    SELECT name_original, name_normalized, set_code, sku, 
                           variant_type, price_cents, collector_number, image_url
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
                log("Loaded ${catalog.variants.size} variants")
                catalog
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                e.printStackTrace()
                null
            }
        }
}
```

## Step 5: Configure

In `src/desktopMain/kotlin/platform/DesktopPlatformServices.kt`:

```kotlin
class DesktopPlatformServices : PlatformServices {
    
    init {
        val dbSource = DatabaseCatalogDataSource(
            jdbcUrl = System.getenv("DB_URL") 
                ?: "jdbc:postgresql://localhost:5432/mtgpirate",
            username = System.getenv("DB_USER") ?: "mtg_user",
            password = System.getenv("DB_PASSWORD") ?: "your_password"
        )
        
        CatalogFetcher.setDataSource(dbSource)
    }
    
    // ... rest unchanged
}
```

## Step 6: Populate Database

Migration script to populate from remote source:

```kotlin
fun main() = runBlocking {
    // Load from remote
    val remoteSource = RemoteCatalogDataSource()
    val catalog = remoteSource.load(forceRefresh = true) { println(it) }
    
    if (catalog != null) {
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
        println("Migrated ${catalog.variants.size} variants to database")
    }
}
```

## Step 7: Test

Verify the implementation:

1. Catalog loads from database
2. Card searches work correctly
3. Performance is acceptable
4. Error handling works (try disconnecting database)

## Hybrid Approach

Safer migration with automatic fallback:

```kotlin
init {
    val dbSource = DatabaseCatalogDataSource(...)
    val remoteSource = RemoteCatalogDataSource()
    
    val hybridSource = HybridCatalogDataSource(dbSource, remoteSource)
    CatalogFetcher.setDataSource(hybridSource)
}
```

**Benefits:**
- ✅ Fast loading from database
- ✅ Automatic fallback to remote
- ✅ No downtime during migration
- ✅ Can validate database data

**Rollback:**
```kotlin
CatalogFetcher.setDataSource(RemoteCatalogDataSource())
```

## Advanced Topics

### Connection Pooling (Production)

Use HikariCP for connection pooling:

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

**Add dependency:**
```kotlin
implementation("com.zaxxer:HikariCP:5.1.0")
```
