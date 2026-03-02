package integration

import deck.DecklistParser
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.Section
import model.Seller
import model.ShoppingPlan
import model.VariantType
import optimizer.ShoppingOptimizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

        // 2. Create mock catalogs for USEA, BOOTLEG_MAGE, and MANAPOOL
        val useaCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.USEA, 220),
            testVariant("Counterspell", Seller.USEA, 220),
        )
        val bmCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
            testVariant("Counterspell", Seller.BOOTLEG_MAGE, 250),
            testVariant("Black Lotus", Seller.BOOTLEG_MAGE, 500),
        )
        val manapoolCatalog = catalogOf(
            testVariant("Tarmogoyf", Seller.MANAPOOL, 5000),
        )

        val catalogs = mapOf(
            Seller.USEA to useaCatalog,
            Seller.BOOTLEG_MAGE to bmCatalog,
            Seller.MANAPOOL to manapoolCatalog,
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

        // Tarmogoyf: Only in ManaPool
        assertEquals(Seller.MANAPOOL, matches[2].bestOption?.seller)
        assertEquals(5000, matches[2].bestOption?.priceCents)

        // Black Lotus: Only in BM
        assertEquals(Seller.BOOTLEG_MAGE, matches[3].bestOption?.seller)
        assertEquals(500, matches[3].bestOption?.priceCents)

        // 4. Run ShoppingOptimizer
        val plan = ShoppingOptimizer.optimize(matches)

        // 5. Assert: shopping plan has correct seller split and totals.
        // The iterative optimizer moves Counterspell from USEA to BM because eliminating
        // USEA's $10 shipping outweighs the per-card price increase (220->250, 4x = 120).
        // Net savings: 1000 (shipping) - 120 (price delta) = 880.
        assertEquals(2, plan.orders.size) // BM, MANAPOOL (USEA eliminated)

        val bmOrder = plan.orders.find { it.seller == Seller.BOOTLEG_MAGE }!!
        val mpOrder = plan.orders.find { it.seller == Seller.MANAPOOL }!!

        // BM Order: 4 Lightning Bolt @ 180 (720) + 1 Black Lotus @ 500 (500)
        //         + 4 Counterspell @ 250 (1000) = 2220.
        // BOOTLEG_MAGE_DISCOUNT_CONFIG: free shipping.
        assertEquals(2220, bmOrder.subtotalCents)
        assertEquals(0, bmOrder.shippingCents)
        assertEquals(2220, bmOrder.totalCents)

        // ManaPool Order: 2 Tarmogoyf @ 5000 = 10000.
        assertEquals(10000, mpOrder.subtotalCents)
        assertEquals(10000, mpOrder.totalCents)

        assertEquals(2220 + 10000, plan.totalPriceCents)
    }

    @Test
    fun thresholdOptimizationFlow() {
        // Test that optimizer moves cards to hit better discount tier.
        // USEA needs 100_00 for 15% discount + free shipping.
        // Naive: USEA 95_00 + $10 ship = 105_00, BM 5_00 = 5_00. Total 110_00.
        // Optimized: Move Shared Card to USEA -> 105_00, 15% discount -> 89_25 + $10 ship = 99_25.
        // 99_25 < 110_00 so optimization should fire.

        val deckText = """
            1 Expensive USEA Card
            1 Shared Card
        """.trimIndent()
        val entries = DecklistParser.parse(deckText, includeSideboard = true, includeCommanders = true)

        val useaCatalog = catalogOf(
            testVariant("Expensive USEA Card", Seller.USEA, 95_00),
            testVariant("Shared Card", Seller.USEA, 10_00),
        )
        val bmCatalog = catalogOf(
            testVariant("Shared Card", Seller.BOOTLEG_MAGE, 5_00),
        )

        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
            config = MultiCatalogMatcher.Config(proxyFirst = true)
        )

        val plan = ShoppingOptimizer.optimize(matches)

        val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
        // Optimizer should have pulled the shared card to USEA to hit the $100 discount tier
        assertTrue(useaOrder.items.any { it.variant.nameOriginal == "Shared Card" }, "Shared Card should be in USEA order to hit discount tier")
        assertEquals(105_00, useaOrder.subtotalCents)
        assertEquals(15, useaOrder.discountPercent)
    }

    @Test
    fun iterativeOptimization_crossesDiscountThresholdByReassigning() {
        val entries = listOf(
            testDeckEntry("Volcanic Island", 1),
            testDeckEntry("Underground Sea", 1),
            testDeckEntry("Mystic Remora", 1),
        )

        val useaCatalog = catalogOf(
            testVariant("Volcanic Island", Seller.USEA, 50_00),
            testVariant("Underground Sea", Seller.USEA, 40_00),
            testVariant("Mystic Remora", Seller.USEA, 15_00),
        )
        val bmCatalog = catalogOf(
            testVariant("Mystic Remora", Seller.BOOTLEG_MAGE, 12_00),
        )

        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
            config = MultiCatalogMatcher.Config(proxyFirst = true),
        )

        val plan = ShoppingOptimizer.optimize(matches)

        val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
        assertTrue(
            useaOrder.items.any { it.variant.nameOriginal == "Mystic Remora" },
            "Mystic Remora should be pulled to USEA to cross discount threshold"
        )
        assertEquals(105_00, useaOrder.subtotalCents)
        assertEquals(15, useaOrder.discountPercent)
        assertEquals(89_25 + 10_00, useaOrder.totalCents)
    }

    @Test
    fun iterativeOptimization_doesNotMoveWhenWorsens() {
        val entries = listOf(
            testDeckEntry("Tropical Island", 1),
            testDeckEntry("Ponder", 1),
        )

        val useaCatalog = catalogOf(
            testVariant("Tropical Island", Seller.USEA, 55_00),
            testVariant("Ponder", Seller.USEA, 8_00),
        )
        val bmCatalog = catalogOf(
            testVariant("Ponder", Seller.BOOTLEG_MAGE, 2_00),
        )

        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
            config = MultiCatalogMatcher.Config(proxyFirst = true),
        )

        val plan = ShoppingOptimizer.optimize(matches)

        val bmOrder = plan.orders.find { it.seller == Seller.BOOTLEG_MAGE }
        assertNotNull(bmOrder, "BM order should exist")
        assertTrue(bmOrder.items.any { it.variant.nameOriginal == "Ponder" })
    }

    @Test
    fun optimizeSubset_filtersBySeller() {
        val entries = listOf(
            testDeckEntry("Lightning Bolt", 4),
            testDeckEntry("Counterspell", 4),
        )

        val useaCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.USEA, 220),
            testVariant("Counterspell", Seller.USEA, 220),
        )
        val bmCatalog = catalogOf(
            testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
            testVariant("Counterspell", Seller.BOOTLEG_MAGE, 250),
        )

        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
            config = MultiCatalogMatcher.Config(proxyFirst = true),
        )

        val useaOnly = ShoppingOptimizer.optimizeForSellers(matches, setOf(Seller.USEA))
        assertTrue(useaOnly.orders.all { it.seller == Seller.USEA })
        assertEquals(2, useaOnly.orders.first().items.size)

        val fullPlan = ShoppingOptimizer.optimize(matches)
        assertTrue(fullPlan.orders.any { it.seller == Seller.BOOTLEG_MAGE })
    }
}
