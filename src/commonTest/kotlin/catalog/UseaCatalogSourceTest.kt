package catalog

import model.CardVariant
import model.Catalog
import model.OrderItem
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseaCatalogSourceTest {
    @Test
    fun `seller is USEA`() {
        val source = UseaCatalogSource(FakeCatalogDataSource(emptyList()))
        assertEquals(Seller.USEA, source.seller)
    }

    @Test
    fun `fetchCatalog tags variants with USEA seller`() = kotlinx.coroutines.test.runTest {
        val variants = listOf(
            CardVariant(
                nameOriginal = "Lightning Bolt",
                nameNormalized = "lightning bolt",
                setCode = "M11",
                sku = "XMC001",
                variantType = VariantType.REGULAR,
                priceInCents = 220,
            )
        )
        val source = UseaCatalogSource(FakeCatalogDataSource(variants))
        val result = source.fetchCatalog()
        assertEquals(1, result.size)
        assertEquals(Seller.USEA, result[0].seller)
        assertEquals("Lightning Bolt", result[0].nameOriginal)
    }

    @Test
    fun `fetchCatalog returns empty list when datasource fails`() = kotlinx.coroutines.test.runTest {
        val source = UseaCatalogSource(FakeCatalogDataSource(null))
        val result = source.fetchCatalog()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search returns empty - USEA is catalog-only`() = kotlinx.coroutines.test.runTest {
        val source = UseaCatalogSource(FakeCatalogDataSource(emptyList()))
        val result = source.search("Lightning Bolt")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `checkoutUrl returns null - email-based ordering`() {
        val source = UseaCatalogSource(FakeCatalogDataSource(emptyList()))
        assertNull(source.checkoutUrl(emptyList()))
    }

    @Test
    fun `formatForExport generates CSV with header`() {
        val source = UseaCatalogSource(FakeCatalogDataSource(emptyList()))
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "XMC001",
                    variantType = VariantType.REGULAR,
                    priceInCents = 220,
                    seller = Seller.USEA,
                ),
                qty = 4,
                isProxy = true,
            )
        )
        val csv = source.formatForExport(items)
        assertTrue(csv.startsWith("Card Name,Set,SKU,Card Type,Quantity,Base Price"))
        assertTrue(csv.contains("Lightning Bolt"))
        assertTrue(csv.contains("4"))
    }
}

/** Simple fake for testing */
private class FakeCatalogDataSource(private val variants: List<CardVariant>?) : CatalogDataSource {
    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? {
        return variants?.let { Catalog(it) }
    }
}
