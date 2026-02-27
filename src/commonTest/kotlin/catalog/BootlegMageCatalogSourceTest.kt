package catalog

import model.OrderItem
import model.Seller
import model.VariantType
import model.CardVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BootlegMageCatalogSourceTest {

    private val source = BootlegMageCatalogSource()

    // ---- seller property ----

    @Test
    fun `seller is BOOTLEG_MAGE`() {
        assertEquals(Seller.BOOTLEG_MAGE, source.seller)
    }

    // ---- checkoutUrl ----

    @Test
    fun `checkoutUrl returns deck import URL`() {
        val url = source.checkoutUrl(emptyList())
        assertEquals("https://bootlegmage.com/deck-import/", url)
    }

    @Test
    fun `checkoutUrl returns same URL regardless of items`() {
        val items = listOf(sampleOrderItem())
        val url = source.checkoutUrl(items)
        assertEquals("https://bootlegmage.com/deck-import/", url)
    }

    // ---- formatForExport ----

    @Test
    fun `formatForExport produces qty cardName lines`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "BM-1",
                    variantType = VariantType.REGULAR,
                    priceInCents = 220,
                    seller = Seller.BOOTLEG_MAGE,
                ),
                qty = 4,
                isProxy = true,
            ),
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Counterspell",
                    nameNormalized = "counterspell",
                    setCode = "ICE",
                    sku = "BM-2",
                    variantType = VariantType.FOIL,
                    priceInCents = 350,
                    seller = Seller.BOOTLEG_MAGE,
                ),
                qty = 2,
                isProxy = true,
            ),
        )
        val export = source.formatForExport(items)
        val lines = export.lines()
        assertEquals(2, lines.size)
        assertEquals("4 Lightning Bolt", lines[0])
        assertEquals("2 Counterspell", lines[1])
    }

    @Test
    fun `formatForExport with empty list returns empty string`() {
        assertEquals("", source.formatForExport(emptyList()))
    }

    // ---- parseProductName ----

    @Test
    fun `parseProductName with full format Card - SET - Variant`() {
        val result = source.parseProductName("Lightning Bolt - M11 - Foil")
        assertNotNull(result)
        assertEquals("Lightning Bolt", result.cardName)
        assertEquals("M11", result.setCode)
        assertEquals(VariantType.FOIL, result.variantType)
    }

    @Test
    fun `parseProductName with regular variant`() {
        val result = source.parseProductName("Dark Ritual - ICE - Regular")
        assertNotNull(result)
        assertEquals("Dark Ritual", result.cardName)
        assertEquals("ICE", result.setCode)
        assertEquals(VariantType.REGULAR, result.variantType)
    }

    @Test
    fun `parseProductName with holo variant`() {
        val result = source.parseProductName("Sol Ring - CMD - Holo")
        assertNotNull(result)
        assertEquals("Sol Ring", result.cardName)
        assertEquals("CMD", result.setCode)
        assertEquals(VariantType.HOLO, result.variantType)
    }

    @Test
    fun `parseProductName with two parts defaults to Regular`() {
        val result = source.parseProductName("Lightning Bolt - M11")
        assertNotNull(result)
        assertEquals("Lightning Bolt", result.cardName)
        assertEquals("M11", result.setCode)
        assertEquals(VariantType.REGULAR, result.variantType)
    }

    @Test
    fun `parseProductName with single name defaults to UNK set and Regular`() {
        val result = source.parseProductName("Lightning Bolt")
        assertNotNull(result)
        assertEquals("Lightning Bolt", result.cardName)
        assertEquals("UNK", result.setCode)
        assertEquals(VariantType.REGULAR, result.variantType)
    }

    @Test
    fun `parseProductName with blank returns null`() {
        assertNull(source.parseProductName(""))
        assertNull(source.parseProductName("   "))
    }

    @Test
    fun `parseProductName uppercases set code`() {
        val result = source.parseProductName("Card - m11 - Foil")
        assertNotNull(result)
        assertEquals("M11", result.setCode)
    }

    @Test
    fun `parseProductName with card name containing dashes`() {
        // Card name "Nicol Bolas, Dragon-God" wouldn't use " - " separator, but
        // a name like "Fire - Ice" (split card) that has " - " in it with extra parts
        val result = source.parseProductName("Fire // Ice - STA - Foil")
        assertNotNull(result)
        assertEquals("Fire // Ice", result.cardName)
        assertEquals("STA", result.setCode)
        assertEquals(VariantType.FOIL, result.variantType)
    }

    @Test
    fun `parseProductName with four-part name preserves card name with dashes`() {
        val result = source.parseProductName("Some - Card - SET - Holo")
        assertNotNull(result)
        assertEquals("Some - Card", result.cardName)
        assertEquals("SET", result.setCode)
        assertEquals(VariantType.HOLO, result.variantType)
    }

    // ---- parseWooCommerceJson ----

    @Test
    fun `parseWooCommerceJson with valid product array`() {
        val json = """
            [
                {
                    "id": 42,
                    "name": "Lightning Bolt - M11 - Foil",
                    "price": "3.50",
                    "permalink": "https://bootlegmage.com/product/lightning-bolt-m11-foil/",
                    "images": [{"src": "https://bootlegmage.com/img/bolt.jpg"}]
                }
            ]
        """.trimIndent()
        val variants = source.parseWooCommerceJson(json)
        assertEquals(1, variants.size)
        val v = variants[0]
        assertEquals("Lightning Bolt", v.nameOriginal)
        assertEquals("lightning bolt", v.nameNormalized)
        assertEquals("M11", v.setCode)
        assertEquals("BM-42", v.sku)
        assertEquals(VariantType.FOIL, v.variantType)
        assertEquals(350, v.priceInCents)
        assertEquals(Seller.BOOTLEG_MAGE, v.seller)
        assertEquals("https://bootlegmage.com/product/lightning-bolt-m11-foil/", v.purchaseUri)
        assertEquals("https://bootlegmage.com/img/bolt.jpg", v.imageUrl)
    }

    @Test
    fun `parseWooCommerceJson with multiple products`() {
        val json = """
            [
                {"id": 1, "name": "Lightning Bolt - M11 - Regular", "price": "2.20"},
                {"id": 2, "name": "Counterspell - ICE - Foil", "price": "3.50"},
                {"id": 3, "name": "Sol Ring - CMD - Holo", "price": "3.00"}
            ]
        """.trimIndent()
        val variants = source.parseWooCommerceJson(json)
        assertEquals(3, variants.size)
        assertEquals(VariantType.REGULAR, variants[0].variantType)
        assertEquals(VariantType.FOIL, variants[1].variantType)
        assertEquals(VariantType.HOLO, variants[2].variantType)
    }

    @Test
    fun `parseWooCommerceJson uses default price when price is zero`() {
        val json = """[{"id": 1, "name": "Card - SET - Foil", "price": "0"}]"""
        val variants = source.parseWooCommerceJson(json)
        assertEquals(1, variants.size)
        assertEquals(VariantType.FOIL.defaultPriceInCents, variants[0].priceInCents)
    }

    @Test
    fun `parseWooCommerceJson skips products without name`() {
        val json = """[{"id": 1, "price": "2.20"}]"""
        val variants = source.parseWooCommerceJson(json)
        assertTrue(variants.isEmpty())
    }

    @Test
    fun `parseWooCommerceJson returns empty for blank input`() {
        assertTrue(source.parseWooCommerceJson("").isEmpty())
        assertTrue(source.parseWooCommerceJson("   ").isEmpty())
    }

    @Test
    fun `parseWooCommerceJson returns empty for malformed JSON`() {
        assertTrue(source.parseWooCommerceJson("not json").isEmpty())
    }

    @Test
    fun `parseWooCommerceJson handles missing optional fields`() {
        val json = """[{"id": 5, "name": "Dark Ritual - ICE - Regular", "price": "2.20"}]"""
        val variants = source.parseWooCommerceJson(json)
        assertEquals(1, variants.size)
        assertNull(variants[0].imageUrl)
        assertNull(variants[0].purchaseUri)
    }

    // ---- parseShopHtml ----

    @Test
    fun `parseShopHtml with WooCommerce product listing`() {
        val html = """
            <html><body>
            <ul class="products">
                <li class="product">
                    <a href="https://bootlegmage.com/product/bolt/">
                        <img src="https://bootlegmage.com/img/bolt.jpg" />
                        <h2 class="woocommerce-loop-product__title">Lightning Bolt - M11 - Foil</h2>
                        <span class="woocommerce-Price-amount">$3.50</span>
                    </a>
                </li>
            </ul>
            </body></html>
        """.trimIndent()
        val variants = source.parseShopHtml(html)
        assertEquals(1, variants.size)
        val v = variants[0]
        assertEquals("Lightning Bolt", v.nameOriginal)
        assertEquals("M11", v.setCode)
        assertEquals(VariantType.FOIL, v.variantType)
        assertEquals(350, v.priceInCents)
        assertEquals(Seller.BOOTLEG_MAGE, v.seller)
        assertEquals("BM-HTML-1", v.sku)
    }

    @Test
    fun `parseShopHtml with div products`() {
        val html = """
            <html><body>
            <div class="product">
                <a href="https://bootlegmage.com/product/ritual/">
                    <h2>Dark Ritual - ICE - Regular</h2>
                    <span class="price">$2.20</span>
                </a>
            </div>
            </body></html>
        """.trimIndent()
        val variants = source.parseShopHtml(html)
        assertEquals(1, variants.size)
        assertEquals("Dark Ritual", variants[0].nameOriginal)
        assertEquals("ICE", variants[0].setCode)
    }

    @Test
    fun `parseShopHtml returns empty for blank HTML`() {
        assertTrue(source.parseShopHtml("").isEmpty())
    }

    @Test
    fun `parseShopHtml returns empty when no products found`() {
        val html = "<html><body><p>No products</p></body></html>"
        assertTrue(source.parseShopHtml(html).isEmpty())
    }

    @Test
    fun `parseShopHtml with multiple products`() {
        val html = """
            <html><body>
            <ul class="products">
                <li class="product">
                    <a href="/p/1"><h2 class="woocommerce-loop-product__title">Card A - SET - Regular</h2></a>
                    <span class="woocommerce-Price-amount">$2.20</span>
                </li>
                <li class="product">
                    <a href="/p/2"><h2 class="woocommerce-loop-product__title">Card B - SET - Foil</h2></a>
                    <span class="woocommerce-Price-amount">$3.50</span>
                </li>
            </ul>
            </body></html>
        """.trimIndent()
        val variants = source.parseShopHtml(html)
        assertEquals(2, variants.size)
        assertEquals("BM-HTML-1", variants[0].sku)
        assertEquals("BM-HTML-2", variants[1].sku)
    }

    // ---- parseStoreProductName (new Store API format) ----

    @Test
    fun `parseStoreProductName with standard format`() {
        val result = source.parseStoreProductName("Ocelot Pride SCH Textless #47 Foil")
        assertNotNull(result)
        assertEquals("Ocelot Pride", result.cardName)
        assertEquals("SCH", result.setCode)
        assertEquals(VariantType.FOIL, result.variantType)
        assertEquals("47", result.collectorNumber)
    }

    @Test
    fun `parseStoreProductName without foil`() {
        val result = source.parseStoreProductName("Visions of Beyond SLD Full Art #1670")
        assertNotNull(result)
        assertEquals("Visions of Beyond", result.cardName)
        assertEquals("SLD", result.setCode)
        assertEquals(VariantType.REGULAR, result.variantType)
        assertEquals("1670", result.collectorNumber)
    }

    @Test
    fun `parseStoreProductName simple card`() {
        val result = source.parseStoreProductName("Lightning Bolt M11 #1")
        assertNotNull(result)
        assertEquals("Lightning Bolt", result.cardName)
        assertEquals("M11", result.setCode)
        assertEquals("1", result.collectorNumber)
    }

    @Test
    fun `parseStoreProductName with blank returns null`() {
        assertNull(source.parseStoreProductName(""))
        assertNull(source.parseStoreProductName("   "))
    }

    @Test
    fun `parseStoreProductName without collector number`() {
        val result = source.parseStoreProductName("Sol Ring CMD Foil")
        assertNotNull(result)
        assertEquals("Sol Ring", result.cardName)
        assertEquals("CMD", result.setCode)
        assertEquals(VariantType.FOIL, result.variantType)
        assertNull(result.collectorNumber)
    }

    @Test
    fun `parseStoreProductName with holo variant`() {
        val result = source.parseStoreProductName("Dark Ritual ICE Holo #1")
        assertNotNull(result)
        assertEquals("Dark Ritual", result.cardName)
        assertEquals("ICE", result.setCode)
        assertEquals(VariantType.HOLO, result.variantType)
    }

    // ---- parseStoreApiJson (new Store API JSON) ----

    @Test
    fun `parseStoreApiJson with valid product`() {
        val json = """
            [
                {
                    "id": 119622,
                    "name": "Ocelot Pride SCH Textless #47 Foil",
                    "sku": "C1724-SCH",
                    "prices": {
                        "price": "350",
                        "regular_price": "400",
                        "sale_price": "350",
                        "currency_code": "USD"
                    },
                    "images": [{"src": "https://bootlegmage.com/img/ocelot.jpg"}],
                    "permalink": "https://bootlegmage.com/product/ocelot-pride/"
                }
            ]
        """.trimIndent()
        val variants = source.parseStoreApiJson(json)
        assertEquals(1, variants.size)
        val v = variants[0]
        assertEquals("Ocelot Pride", v.nameOriginal)
        assertEquals("SCH", v.setCode)
        assertEquals("C1724-SCH", v.sku)
        assertEquals(VariantType.FOIL, v.variantType)
        assertEquals(350, v.priceInCents)
        assertEquals(Seller.BOOTLEG_MAGE, v.seller)
        assertEquals("https://bootlegmage.com/product/ocelot-pride/", v.purchaseUri)
        assertEquals("https://bootlegmage.com/img/ocelot.jpg", v.imageUrl)
        assertEquals("47", v.collectorNumber)
    }

    @Test
    fun `parseStoreApiJson with multiple products`() {
        val json = """
            [
                {"id": 1, "name": "Lightning Bolt M11 #1", "prices": {"price": "300"}, "sku": "C1-M11"},
                {"id": 2, "name": "Sol Ring CMD #1 Foil", "prices": {"price": "350"}, "sku": "C2-CMD"}
            ]
        """.trimIndent()
        val variants = source.parseStoreApiJson(json)
        assertEquals(2, variants.size)
        assertEquals(VariantType.REGULAR, variants[0].variantType)
        assertEquals(VariantType.FOIL, variants[1].variantType)
    }

    @Test
    fun `parseStoreApiJson returns empty for blank input`() {
        assertTrue(source.parseStoreApiJson("").isEmpty())
        assertTrue(source.parseStoreApiJson("   ").isEmpty())
    }

    @Test
    fun `parseStoreApiJson price in cents not dollars`() {
        val json = """[{"id": 1, "name": "Card TST #1", "prices": {"price": "220"}}]"""
        val variants = source.parseStoreApiJson(json)
        assertEquals(1, variants.size)
        assertEquals(220, variants[0].priceInCents)
    }

    // ---- all variants tagged with BOOTLEG_MAGE seller ----

    @Test
    fun `all parsed variants have BOOTLEG_MAGE seller`() {
        val json = """
            [
                {"id": 1, "name": "Card A - SET - Regular", "price": "2.20"},
                {"id": 2, "name": "Card B - SET - Foil", "price": "3.50"}
            ]
        """.trimIndent()
        val variants = source.parseWooCommerceJson(json)
        assertTrue(variants.all { it.seller == Seller.BOOTLEG_MAGE })
    }

    @Test
    fun `HTML parsed variants have BOOTLEG_MAGE seller`() {
        val html = """
            <html><body>
            <li class="product">
                <h2 class="woocommerce-loop-product__title">Card - SET - Holo</h2>
                <span class="price">$3.00</span>
            </li>
            </body></html>
        """.trimIndent()
        val variants = source.parseShopHtml(html)
        assertTrue(variants.all { it.seller == Seller.BOOTLEG_MAGE })
    }

    // ---- helper ----

    private fun sampleOrderItem() = OrderItem(
        variant = CardVariant(
            nameOriginal = "Lightning Bolt",
            nameNormalized = "lightning bolt",
            setCode = "M11",
            sku = "BM-1",
            variantType = VariantType.REGULAR,
            priceInCents = 220,
            seller = Seller.BOOTLEG_MAGE,
        ),
        qty = 4,
        isProxy = true,
    )
}
