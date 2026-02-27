package catalog

import model.Seller
import model.VariantType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManaPoolCatalogSourceTest {

    private val source = ManaPoolCatalogSource()

    // ---- seller property ----

    @Test
    fun `seller is MANAPOOL`() {
        assertEquals(Seller.MANAPOOL, source.seller)
    }

    // ---- checkoutUrl ----

    @Test
    fun `checkoutUrl returns manapool URL`() {
        val url = source.checkoutUrl(emptyList())
        assertEquals("https://manapool.com", url)
    }

    // ---- formatForExport ----

    @Test
    fun `formatForExport produces qty Name SET lines`() {
        val items = listOf(
            model.OrderItem(
                variant = model.CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "MP-M11-1-R",
                    variantType = VariantType.REGULAR,
                    priceInCents = 150,
                    seller = Seller.MANAPOOL,
                ),
                qty = 4,
                isProxy = false,
            ),
            model.OrderItem(
                variant = model.CardVariant(
                    nameOriginal = "Counterspell",
                    nameNormalized = "counterspell",
                    setCode = "ICE",
                    sku = "MP-ICE-2-R",
                    variantType = VariantType.REGULAR,
                    priceInCents = 75,
                    seller = Seller.MANAPOOL,
                ),
                qty = 2,
                isProxy = false,
            ),
        )
        val export = source.formatForExport(items)
        val lines = export.lines()
        assertEquals(2, lines.size)
        assertEquals("4 Lightning Bolt [M11]", lines[0])
        assertEquals("2 Counterspell [ICE]", lines[1])
    }

    @Test
    fun `formatForExport with empty list returns empty string`() {
        assertEquals("", source.formatForExport(emptyList()))
    }

    // ---- toCardVariants ----

    @Test
    fun `regular card with price produces one REGULAR variant`() {
        val single = ManaPoolSingle(
            name = "Lightning Bolt",
            setCode = "m11",
            number = "149",
            priceCents = 150,
        )
        val variants = single.toCardVariants()
        assertEquals(1, variants.size)
        val v = variants[0]
        assertEquals("Lightning Bolt", v.nameOriginal)
        assertEquals("lightning bolt", v.nameNormalized)
        assertEquals("M11", v.setCode)
        assertEquals("MP-M11-149-R", v.sku)
        assertEquals(VariantType.REGULAR, v.variantType)
        assertEquals(150, v.priceInCents)
        assertEquals(Seller.MANAPOOL, v.seller)
    }

    @Test
    fun `card with regular and foil produces two variants`() {
        val single = ManaPoolSingle(
            name = "Lightning Bolt",
            setCode = "m11",
            number = "149",
            priceCents = 150,
            priceCentsFoil = 500,
        )
        val variants = single.toCardVariants()
        assertEquals(2, variants.size)
        assertEquals(VariantType.REGULAR, variants[0].variantType)
        assertEquals(150, variants[0].priceInCents)
        assertEquals(VariantType.FOIL, variants[1].variantType)
        assertEquals(500, variants[1].priceInCents)
    }

    @Test
    fun `card with etched price produces HOLO variant`() {
        val single = ManaPoolSingle(
            name = "Sol Ring",
            setCode = "cmr",
            number = "322",
            priceCentsEtched = 800,
        )
        val variants = single.toCardVariants()
        assertEquals(1, variants.size)
        assertEquals(VariantType.HOLO, variants[0].variantType)
        assertEquals(800, variants[0].priceInCents)
        assertEquals("MP-CMR-322-E", variants[0].sku)
    }

    @Test
    fun `card with all three prices produces three variants`() {
        val single = ManaPoolSingle(
            name = "Sol Ring",
            setCode = "cmr",
            number = "322",
            priceCents = 200,
            priceCentsFoil = 600,
            priceCentsEtched = 800,
        )
        val variants = single.toCardVariants()
        assertEquals(3, variants.size)
        assertEquals(VariantType.REGULAR, variants[0].variantType)
        assertEquals(VariantType.FOIL, variants[1].variantType)
        assertEquals(VariantType.HOLO, variants[2].variantType)
    }

    @Test
    fun `card with null prices produces no variants`() {
        val single = ManaPoolSingle(
            name = "Nonexistent Card",
            setCode = "unk",
            number = "1",
            priceCents = null,
            priceCentsFoil = null,
            priceCentsEtched = null,
        )
        val variants = single.toCardVariants()
        assertTrue(variants.isEmpty())
    }

    @Test
    fun `card with zero price is skipped`() {
        val single = ManaPoolSingle(
            name = "Free Card",
            setCode = "tst",
            number = "1",
            priceCents = 0,
        )
        val variants = single.toCardVariants()
        assertTrue(variants.isEmpty())
    }

    @Test
    fun `set code is uppercased`() {
        val single = ManaPoolSingle(
            name = "Card",
            setCode = "abc",
            number = "1",
            priceCents = 100,
        )
        val variants = single.toCardVariants()
        assertEquals("ABC", variants[0].setCode)
    }

    @Test
    fun `null number defaults to 0 in sku`() {
        val single = ManaPoolSingle(
            name = "Card",
            setCode = "TST",
            number = null,
            priceCents = 100,
        )
        val variants = single.toCardVariants()
        assertEquals("MP-TST-0-R", variants[0].sku)
    }

    @Test
    fun `purchase URI is passed through`() {
        val single = ManaPoolSingle(
            name = "Card",
            setCode = "TST",
            number = "1",
            priceCents = 100,
            url = "https://manapool.com/card/tst/1/card",
        )
        val variants = single.toCardVariants()
        assertEquals("https://manapool.com/card/tst/1/card", variants[0].purchaseUri)
    }

    // ---- JSON parsing ----

    @Test
    fun `parse sample API response JSON`() {
        val json = Json { ignoreUnknownKeys = true }
        val sampleJson = """
            {
                "meta": {"as_of": "2026-02-26T00:00:00Z", "base_url": "https://manapool.com"},
                "data": [
                    {
                        "name": "Lightning Bolt",
                        "set_code": "m11",
                        "number": "149",
                        "scryfall_id": "abc123",
                        "available_quantity": 5,
                        "price_cents": 150,
                        "price_cents_foil": 500,
                        "price_cents_etched": null,
                        "price_market": 140,
                        "url": "https://manapool.com/card/m11/149/lightning-bolt"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ManaPoolPricesResponse>(sampleJson)
        assertEquals(1, response.data.size)
        assertEquals("Lightning Bolt", response.data[0].name)
        assertEquals("m11", response.data[0].setCode)
        assertEquals(150, response.data[0].priceCents)
        assertEquals(500, response.data[0].priceCentsFoil)
    }

    @Test
    fun `parse JSON with missing optional fields`() {
        val json = Json { ignoreUnknownKeys = true }
        val sampleJson = """
            {
                "meta": {"as_of": "2026-02-26T00:00:00Z"},
                "data": [
                    {
                        "name": "Basic Land",
                        "set_code": "m21",
                        "price_cents": 5
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ManaPoolPricesResponse>(sampleJson)
        assertEquals(1, response.data.size)
        val single = response.data[0]
        assertEquals("Basic Land", single.name)
        assertEquals("m21", single.setCode)
        assertEquals(null, single.number)
        assertEquals(null, single.scryfallId)
        assertEquals(0, single.availableQuantity)
        assertEquals(5, single.priceCents)
        assertEquals(null, single.priceCentsFoil)
        assertEquals(null, single.priceCentsEtched)
        assertEquals(null, single.url)
    }
}
