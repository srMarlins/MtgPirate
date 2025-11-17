# Catalog Data Source Architecture

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Current Implementation](#current-implementation)
- [Usage](#usage)
- [Custom Data Sources](#custom-data-sources)
- [Testing](#testing)
- [Migration Path](#migration-path)
- [Benefits](#benefits)

## Overview

The catalog data source abstraction enables clean separation between data retrieval and application logic, making it easy to swap between remote HTTP/CSV, local database, or mock data sources.

## Architecture

```
Application Code (MainStore, PlatformServices)
              ↓
CatalogFetcher (Facade - backward-compatible API)
              ↓
CatalogDataSource (Interface)
    - load(forceRefresh, log): Catalog?
              ↓
    ┌─────────┴─────────┐
    ↓                   ↓
RemoteDataSource    DatabaseDataSource
  (Current)            (Future)
```

## Current Implementation

**RemoteCatalogDataSource** fetches from remote endpoints:
- **Primary**: CSV endpoint (`single-card-list.csv`)
- **Fallback**: HTML page with embedded data
- **Caching**: Local JSON cache
- **Price Normalization**: Fills missing prices with defaults

## Usage

### Default Usage

Existing code works without modification:

```kotlin
val catalog = CatalogFetcher.load(forceRefresh = true) { msg ->
    println(msg)
}
```

### Custom Data Source

Swap implementations easily:

```kotlin
// 1. Create implementation
class DatabaseCatalogDataSource : CatalogDataSource {
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? {
        return dbConnection.loadCatalog()
    }
}

// 2. Configure
CatalogFetcher.setDataSource(DatabaseCatalogDataSource())

// 3. Use normally
val catalog = CatalogFetcher.load()
```

## Custom Data Sources

### Database Implementation Template

```kotlin
class DatabaseCatalogDataSource(
    private val databaseUrl: String,
    private val credentials: DatabaseCredentials
) : CatalogDataSource {
    
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? = 
        withContext(Dispatchers.IO) {
            try {
                log("Loading catalog from database...")
                val connection = connectToDatabase(databaseUrl, credentials)
                
                val variants = connection.query("""
                    SELECT name_original, name_normalized, set_code, 
                           sku, variant_type, price_cents
                    FROM card_variants WHERE active = true
                """)
                
                val catalog = Catalog(variants = variants.map { row ->
                    CardVariant(
                        nameOriginal = row.getString("name_original"),
                        nameNormalized = row.getString("name_normalized"),
                        setCode = row.getString("set_code"),
                        sku = row.getString("sku"),
                        variantType = row.getString("variant_type"),
                        priceInCents = row.getInt("price_cents")
                    )
                })
                
                log("Loaded ${catalog.variants.size} variants")
                catalog
            } catch (e: Exception) {
                log("Database load failed: ${e.message}")
                null
            }
        }
}
```

## Testing

Mock data source for tests:

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

Steps to migrate to database backend:

1. **Create Database Schema**
   - Design `card_variants` table
   - Add indexes for performance (name, set_code, sku)
   - Consider materialized views for complex queries

2. **Implement DatabaseCatalogDataSource**
   - Create class implementing `CatalogDataSource`
   - Use preferred database library (JDBC, Exposed, SQLDelight)
   - Handle connection pooling and errors

3. **Data Migration**
   - Write migration script to populate database
   - Can use `RemoteCatalogDataSource` for initial data
   - Set up periodic sync jobs if needed

4. **Switch Implementation**
   - In `DesktopPlatformServices` or app init
   - Call `CatalogFetcher.setDataSource(DatabaseCatalogDataSource(...))`
   - Test thoroughly

5. **Optional: Hybrid Approach**
   - Create `HybridCatalogDataSource`
   - Try database first, fallback to remote
   - Best of both worlds during transition

## Benefits

- ✅ **Testability** - Easy to inject mock data
- ✅ **Flexibility** - Swap implementations without touching app code
- ✅ **Separation of Concerns** - Data access isolated
- ✅ **Backward Compatibility** - Existing code works unchanged
- ✅ **Future-Proof** - Easy to add new sources

## Notes

- Simple one-method interface
- Implementations handle own caching
- Graceful error handling (return null)
- Logging callback for progress
- Thread-safe with appropriate dispatchers
