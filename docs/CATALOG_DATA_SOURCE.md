# Catalog Data Source Abstraction

## Overview

The catalog data source abstraction provides a clean separation between the catalog data retrieval logic and the rest of the application. This makes it easy to swap between different data sources (remote HTTP/CSV, local database, mock data, etc.) without changing the application code.

## Architecture

```
┌─────────────────────────────────────────┐
│      Application Code                   │
│   (MainStore, PlatformServices)         │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      CatalogFetcher (Facade)            │
│  - Provides backward-compatible API     │
│  - Delegates to pluggable data source   │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   CatalogDataSource (Interface)         │
│  - load(forceRefresh, log): Catalog?    │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       ↓               ↓
┌─────────────┐  ┌──────────────┐
│  Remote     │  │  Database    │
│  DataSource │  │  DataSource  │
│  (Current)  │  │  (Future)    │
└─────────────┘  └──────────────┘
```

## Current Implementation

### RemoteCatalogDataSource

The current implementation fetches catalog data from remote HTTP endpoints:

- **Primary Source**: CSV endpoint (`single-card-list.csv`)
- **Fallback**: HTML page with embedded data
- **Caching**: Local JSON cache to reduce network requests
- **Price Normalization**: Fills in missing prices with defaults

## Usage

### Default Usage (No Changes Required)

The existing code continues to work without modification:

```kotlin
// In DesktopPlatformServices or anywhere else
val catalog = CatalogFetcher.load(forceRefresh = true) { msg ->
    println(msg)
}
```

### Using a Custom Data Source

To swap in a different data source (e.g., database):

```kotlin
// 1. Create your data source implementation
class DatabaseCatalogDataSource : CatalogDataSource {
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? {
        // Load from database
        return dbConnection.loadCatalog()
    }
}

// 2. Configure CatalogFetcher to use it
CatalogFetcher.setDataSource(DatabaseCatalogDataSource())

// 3. Use normally
val catalog = CatalogFetcher.load()
```

## Implementing a Database Data Source

Here's a template for implementing a database-backed catalog data source:

```kotlin
package catalog

import model.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Database-backed catalog data source.
 * Replace with your actual database implementation.
 */
class DatabaseCatalogDataSource(
    private val databaseUrl: String,
    private val credentials: DatabaseCredentials
) : CatalogDataSource {
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? = 
        withContext(Dispatchers.IO) {
            try {
                log("Loading catalog from database...")
                
                // Connect to database
                val connection = connectToDatabase(databaseUrl, credentials)
                
                // Query card variants
                val variants = connection.query("""
                    SELECT name_original, name_normalized, set_code, 
                           sku, variant_type, price_cents, collector_number, 
                           image_url
                    FROM card_variants
                    WHERE active = true
                """)
                
                // Convert to Catalog model
                val catalog = Catalog(variants = variants.map { row ->
                    CardVariant(
                        nameOriginal = row.getString("name_original"),
                        nameNormalized = row.getString("name_normalized"),
                        setCode = row.getString("set_code"),
                        sku = row.getString("sku"),
                        variantType = row.getString("variant_type"),
                        priceInCents = row.getInt("price_cents"),
                        collectorNumber = row.getStringOrNull("collector_number"),
                        imageUrl = row.getStringOrNull("image_url")
                    )
                })
                
                log("Loaded ${catalog.variants.size} variants from database")
                catalog
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                null
            }
        }
    
    private fun connectToDatabase(url: String, creds: DatabaseCredentials): DatabaseConnection {
        // Your database connection logic
        TODO("Implement database connection")
    }
}
```

## Testing with Mock Data

For testing, you can create a mock data source:

```kotlin
class MockCatalogDataSource(private val mockCatalog: Catalog) : CatalogDataSource {
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog {
        log("Loading mock catalog with ${mockCatalog.variants.size} variants")
        return mockCatalog
    }
}

// In tests
@Test
fun testWithMockCatalog() {
    val mockCatalog = Catalog(listOf(
        CardVariant("Lightning Bolt", "lightning bolt", "LEA", "SKU001", "Regular", 100)
    ))
    
    CatalogFetcher.setDataSource(MockCatalogDataSource(mockCatalog))
    
    val result = runBlocking { CatalogFetcher.load() }
    assertEquals(1, result?.variants?.size)
}
```

## Migration Path

To migrate to a database-backed implementation:

1. **Create the Database Schema**
   - Design tables for `card_variants` with appropriate columns
   - Add indexes for performance (name, set_code, sku)
   - Consider caching/materialized views for complex queries

2. **Implement DatabaseCatalogDataSource**
   - Create a new class implementing `CatalogDataSource`
   - Use your preferred database library (JDBC, Exposed, SQLDelight, etc.)
   - Handle connection pooling, transactions, and error cases

3. **Data Migration**
   - Write a one-time migration script to populate the database
   - Can use `RemoteCatalogDataSource` to fetch initial data
   - Set up periodic sync jobs if needed

4. **Switch Implementation**
   - In `DesktopPlatformServices` or app initialization
   - Call `CatalogFetcher.setDataSource(DatabaseCatalogDataSource(...))`
   - Test thoroughly before deploying

5. **Optional: Hybrid Approach**
   - Create a `HybridCatalogDataSource` that tries database first
   - Falls back to remote if database is unavailable
   - Provides best of both worlds during transition

## Benefits of the Abstraction

- ✅ **Testability**: Easy to inject mock data for unit tests
- ✅ **Flexibility**: Swap implementations without touching application code
- ✅ **Separation of Concerns**: Data access logic isolated from business logic
- ✅ **Backward Compatibility**: Existing code continues to work
- ✅ **Future-Proof**: Easy to add new sources (API v2, GraphQL, local files, etc.)

## Notes

- The interface is intentionally simple with just one method
- Implementations should handle their own caching strategies
- Error handling should be graceful (return null on failure)
- Logging callback allows implementations to communicate progress
- All implementations should be thread-safe and use appropriate coroutine dispatchers
