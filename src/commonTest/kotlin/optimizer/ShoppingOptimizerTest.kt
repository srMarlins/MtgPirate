package optimizer

import model.CardVariant
import model.DeckEntry
import model.MatchOption
import model.MultiMatch
import model.Section
import model.Seller
import model.VariantType
import match.NameNormalizer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingOptimizerTest {

    private fun variant(
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

    private fun entry(name: String, qty: Int = 1): DeckEntry =
        DeckEntry(
            id = "test_${name.replace(" ", "_")}",
            originalLine = "$qty $name",
            qty = qty,
            cardName = name,
            section = Section.MAIN,
            include = true,
        )

    private fun matchOption(name: String, seller: Seller, priceCents: Int) =
        MatchOption(
            variant = variant(name, seller, priceCents),
            seller = seller,
            priceCents = priceCents,
            isProxy = seller.isProxy,
            matchScore = 100
        )

    private fun multiMatch(name: String, seller: Seller, priceCents: Int, qty: Int = 1) =
        MultiMatch(
            deckEntry = entry(name, qty),
            bestOption = matchOption(name, seller, priceCents),
            alternatives = listOf(matchOption(name, seller, priceCents)),
            realCardFallback = if (seller.isProxy) matchOption(name, Seller.TCGPLAYER, priceCents * 2) else null
        )

    @Ignore
    @Test
    fun `single seller order applies correct discount tier`() {
        val matches = listOf(
            multiMatch("Card A", Seller.USEA, 10000, 1) // $100
        )

        val optimized = ShoppingOptimizer.optimize(matches)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        // $100 is the 15% tier for USEA
        assertEquals(15, useaOrder.discountPercent)
        // Free normal shipping at $100
        assertEquals(0, useaOrder.shippingCents)
        assertEquals(8500, useaOrder.totalCents)
    }

    @Ignore
    @Test
    fun `threshold optimization moves cards to reach better tier`() {
        // USEA subtotal $380 (30% tier), BM $40
        // Moving $20 from BM to USEA hits $400 (50% tier)

        val sharedMatch = MultiMatch(
            deckEntry = entry("Shared Card", 2),
            bestOption = matchOption("Shared Card", Seller.BOOTLEG_MAGE, 1000),
            alternatives = listOf(
                matchOption("Shared Card", Seller.USEA, 1000),
                matchOption("Shared Card", Seller.BOOTLEG_MAGE, 1000)
            ),
            realCardFallback = null
        )

        val useaMatches = (1..38).map { multiMatch("USEA Card $it", Seller.USEA, 1000) }
        val bmMatches = listOf(
            multiMatch("BM Card 1", Seller.BOOTLEG_MAGE, 1000),
            multiMatch("BM Card 2", Seller.BOOTLEG_MAGE, 1000),
            sharedMatch
        )

        val optimized = ShoppingOptimizer.optimize(useaMatches + bmMatches)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        assertEquals(50, useaOrder.discountPercent)
        assertEquals(40000, useaOrder.subtotalCents)

        val bmOrder = optimized.orders.find { it.seller == Seller.BOOTLEG_MAGE }!!
        assertEquals(2000, bmOrder.subtotalCents)

        // USEA: $400 * 0.5 = $200
        // BM: $20 + $10 shipping = $30
        // Total: $230 (23000 cents)
        assertEquals(23000, optimized.totalPriceCents)
    }

    @Ignore
    @Test
    fun `shipping included when below free shipping threshold`() {
        val matches = listOf(
            multiMatch("Cheap Card", Seller.USEA, 2000) // $20
        )

        val optimized = ShoppingOptimizer.optimize(matches)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        assertEquals(1000, useaOrder.shippingCents)
        assertEquals(3000, useaOrder.totalCents)
    }
}
