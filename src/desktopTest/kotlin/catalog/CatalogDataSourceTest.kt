package catalog

import model.Catalog
import model.CardVariant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Example test demonstrating how to use the CatalogDataSource abstraction.
 * 
 * This shows how you can:
 * 1. Create a mock data source for testing
 * 2. Inject it into CatalogFetcher
 * 3. Test your code without network calls
 */
class CatalogDataSourceTest {
    
    /**
     * Mock implementation for testing
     */
    class MockCatalogDataSource(private val mockCatalog: Catalog) : CatalogDataSource {
        override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog {
            log("Loading mock catalog with ${mockCatalog.variants.size} variants")
            return mockCatalog
        }
    }
    
    @Test
    fun `test mock catalog data source`() = runBlocking {
        // Create a mock catalog with test data
        val mockVariants = listOf(
            CardVariant(
                nameOriginal = "Lightning Bolt",
                nameNormalized = "lightning bolt",
                setCode = "LEA",
                sku = "SKU001",
                variantType = "Regular",
                priceInCents = 100,
                collectorNumber = "1",
                imageUrl = null
            ),
            CardVariant(
                nameOriginal = "Black Lotus",
                nameNormalized = "black lotus",
                setCode = "LEA",
                sku = "SKU002",
                variantType = "Regular",
                priceInCents = 100000,
                collectorNumber = "2",
                imageUrl = null
            )
        )
        val mockCatalog = Catalog(mockVariants)
        
        // Inject mock data source
        val mockSource = MockCatalogDataSource(mockCatalog)
        CatalogFetcher.setDataSource(mockSource)
        
        // Test loading
        val logs = mutableListOf<String>()
        val result = CatalogFetcher.load(forceRefresh = false) { msg -> logs.add(msg) }
        
        // Verify results
        assertNotNull(result)
        assertEquals(2, result.variants.size)
        assertEquals("Lightning Bolt", result.variants[0].nameOriginal)
        assertEquals("Black Lotus", result.variants[1].nameOriginal)
        assertEquals(1, logs.size)
        assertEquals("Loading mock catalog with 2 variants", logs[0])
        
        // Restore default data source for other tests
        CatalogFetcher.setDataSource(RemoteCatalogDataSource())
    }
    
    @Test
    fun `test empty catalog`() = runBlocking {
        val emptyCatalog = Catalog(emptyList())
        val mockSource = MockCatalogDataSource(emptyCatalog)
        CatalogFetcher.setDataSource(mockSource)
        
        val result = CatalogFetcher.load()
        
        assertNotNull(result)
        assertEquals(0, result.variants.size)
        
        // Restore default
        CatalogFetcher.setDataSource(RemoteCatalogDataSource())
    }
}
