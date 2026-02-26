package integration

import deck.DecklistParser
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.*
import optimizer.ShoppingOptimizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingFlowTest {

    private fun testVariant(
        name: String,
        seller: Seller,
        priceCents: Int,
        variantType: VariantType = VariantType.REGULAR,
    ): CardVariant =
        CardVariant(
            nameOriginal = name,
            nameNormalized = NameNormalizer.normalize(name),
            setCode = "TST",
            sku = "${seller.name}-${name.replace(" ", "")}-${variantType.name}",
            variantType = variantType,
            priceInCents = priceCents,
            seller = seller,
        )

    private fun catalogOf(vararg variants: CardVariant) = Catalog(variants.toList())

    private fun testDeckEntry(name: String, qty: Int = 1): DeckEntry =
        DeckEntry(
            id = "test_${name.replace(" ", "_")}",
            originalLine = "$qty $name",
            qty = qty,
            cardName = name,
            section = Section.MAIN,
            include = true,
        )

    @Test
    fun fullFlow_importMatchOptimize() {
        // 1. Parse a small decklist
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            2 Tarmogoyf
            1 Black Lotus
        """.trimIndent()
        val entries = DecklistParser.parse(deckText, includeSideboard = true, includeCommanders = true)
        assertEquals(4, entries.size)

        // 2. Create mock catalogs for USEA, BOOTLEG_MAGE, and TCGPLAYER
        val useaCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.USEA, 220),
            testVariant("Counterspell", Seller.USEA, 220),
        )
        val bmCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
            testVariant("Counterspell", Seller.BOOTLEG_MAGE, 250),
            testVariant("Black Lotus", Seller.BOOTLEG_MAGE, 500),
        )
        val tcgplayerCatalog = catalogOf(
            testVariant("Tarmogoyf", Seller.TCGPLAYER, 5000),
        )

        val catalogs = mapOf(
            Seller.USEA to useaCatalog,
            Seller.BOOTLEG_MAGE to bmCatalog,
            Seller.TCGPLAYER to tcgplayerCatalog,
        )

        // 3. Run MultiCatalogMatcher
        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = catalogs,
            config = MultiCatalogMatcher.Config(proxyFirst = true)
        )

        // Verify matches
        // Lightning Bolt: BM is 180, USEA is 220. Proxy first -> BM (180)
        assertEquals(Seller.BOOTLEG_MAGE, matches[0].bestOption?.seller)
        assertEquals(180, matches[0].bestOption?.priceCents)

        // Counterspell: BM is 250, USEA is 220. Proxy first -> USEA (220)
        assertEquals(Seller.USEA, matches[1].bestOption?.seller)
        assertEquals(220, matches[1].bestOption?.priceCents)

        // Tarmogoyf: Only in TCGPlayer
        assertEquals(Seller.TCGPLAYER, matches[2].bestOption?.seller)
        assertEquals(5000, matches[2].bestOption?.priceCents)

        // Black Lotus: Only in BM
        assertEquals(Seller.BOOTLEG_MAGE, matches[3].bestOption?.seller)
        assertEquals(500, matches[3].bestOption?.priceCents)

        // 4. Run ShoppingOptimizer
        val plan = ShoppingOptimizer.optimize(matches)

        // 5. Assert: all cards matched, shopping plan has correct seller split, totals are correct
        assertEquals(3, plan.orders.size) // USEA, BM, TCGPLAYER

        val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
        val bmOrder = plan.orders.find { it.seller == Seller.BOOTLEG_MAGE }!!
        val tcgOrder = plan.orders.find { it.seller == Seller.TCGPLAYER }!!

        // USEA Order: 4 Counterspell @ 220 = 880. Discount tier < 60_00 -> 0%. Shipping 10_00.
        // Wait, USEA_DISCOUNT_CONFIG has 0 -> 10_00 shipping if afterDiscount < 100_00.
        assertEquals(880, useaOrder.subtotalCents)
        assertEquals(0, useaOrder.discountPercent)
        assertEquals(10_00, useaOrder.shippingCents)
        assertEquals(1880, useaOrder.totalCents)

        // BM Order: 4 Lightning Bolt @ 180 (720) + 1 Black Lotus @ 500 (500) = 1220.
        // BOOTLEG_MAGE_DISCOUNT_CONFIG: free shipping.
        assertEquals(1220, bmOrder.subtotalCents)
        assertEquals(0, bmOrder.shippingCents)
        assertEquals(1220, bmOrder.totalCents)

        // TCGPlayer Order: 2 Tarmogoyf @ 5000 = 10000.
        // TCGPLAYER_DISCOUNT_CONFIG: free shipping in mock config (actually "Varies by seller" but 0 in config).
        assertEquals(10000, tcgOrder.subtotalCents)
        assertEquals(10000, tcgOrder.totalCents)

        assertEquals(1880 + 1220 + 10000, plan.totalPriceCents)
    }

    @Test
    fun thresholdOptimizationFlow() {
        // Test that optimizer moves cards to hit better discount tiers
        // USEA: Needs 60_00 for 5% discount.
        // Let's say we have 55_00 at USEA and 10_00 at BM for a card available at both.

        val deckText = """
            1 Expensive USEA Card
            1 Shared Card
        """.trimIndent()
        val entries = DecklistParser.parse(deckText, includeSideboard = true, includeCommanders = true)

        val useaCatalog = catalogOf(
            testVariant("Expensive USEA Card", Seller.USEA, 55_00),
            testVariant("Shared Card", Seller.USEA, 10_00),
        )
        val bmCatalog = catalogOf(
            testVariant("Shared Card", Seller.BOOTLEG_MAGE, 5_00),
        )

        val catalogs = mapOf(
            Seller.USEA to useaCatalog,
            Seller.BOOTLEG_MAGE to bmCatalog,
        )

        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = catalogs,
            config = MultiCatalogMatcher.Config(proxyFirst = true)
        )

        // Naive match would pick BM for Shared Card (5_00 vs 10_00)
        // USEA: 55_00. Discount 0%. Total 55_00 + 10_00 ship = 65_00.
        // BM: 5_00. Total 5_00.
        // Naive Total: 70_00.

        // Optimized: Move Shared Card to USEA.
        // USEA: 55_00 + 10_00 = 65_00. Discount 5% -> 61_75. Shipping 0 (since > 60_00 after discount? No, shipping free if > 100_00).
        // Wait, USEA shipping config:
        // ShippingTier(10000, 0, "Normal Free"),
        // ShippingTier(0, 1000, "Normal $10"),
        // 61_75 is < 100_00, so shipping is 10_00. Total 71_75.

        // Hmm, let's adjust the numbers to make optimization actually better.
        // If we hit $100 tier: 15% discount AND free shipping.
        // USEA: 95_00. Naive: 95_00 + 10_00 ship = 105_00.
        // Shared Card: BM 5_00.
        // Naive Total: 110_00.

        // Move Shared Card (10_00 at USEA) to USEA.
        // USEA: 95_00 + 10_00 = 105_00. 15% discount -> 89_25.
        // Shipping free if > 100_00 after discount. 89_25 < 100_00. So still 10_00 ship. Total 99_25.
        // 99_25 < 110_00. Optimization should happen.

        val deckText2 = """
            1 Expensive USEA Card
            1 Shared Card
        """.trimIndent()
        val entries2 = DecklistParser.parse(deckText2, includeSideboard = true, includeCommanders = true)

        val useaCatalog2 = catalogOf(
            testVariant("Expensive USEA Card", Seller.USEA, 95_00),
            testVariant("Shared Card", Seller.USEA, 10_00),
        )
        val bmCatalog2 = catalogOf(
            testVariant("Shared Card", Seller.BOOTLEG_MAGE, 5_00),
        )

        val matches2 = MultiCatalogMatcher.match(
            entries = entries2,
            catalogs = mapOf(Seller.USEA to useaCatalog2, Seller.BOOTLEG_MAGE to bmCatalog2),
            config = MultiCatalogMatcher.Config(proxyFirst = true)
        )

        val plan = ShoppingOptimizer.optimize(matches2)

        val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
        // It should have pulled the shared card to USEA
        assertTrue(useaOrder.items.any { it.variant.nameOriginal == "Shared Card" }, "Shared Card should be in USEA order to hit discount tier")
        assertEquals(105_00, useaOrder.subtotalCents)
        assertEquals(15, useaOrder.discountPercent)
    }
}
