package optimizer

import model.DeckEntry
import model.MatchOption
import model.MultiMatch
import model.Section
import model.Seller
import model.CardVariant
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingOptimizerDropTest {

    private fun makeMultiMatch(
        name: String,
        sellers: List<Seller>,
        priceEach: Int = 220,
    ): MultiMatch {
        val entry = DeckEntry(
            id = name, originalLine = "1 $name", qty = 1,
            cardName = name, section = Section.MAIN, include = true,
        )
        val options = sellers.map { seller ->
            MatchOption(
                variant = CardVariant(
                    nameOriginal = name, nameNormalized = name.lowercase(),
                    setCode = "TST", sku = "${seller.name}:$name",
                    variantType = VariantType.REGULAR, priceInCents = priceEach,
                    seller = seller,
                ),
                seller = seller,
                priceCents = priceEach,
                isProxy = seller.isProxy,
                matchScore = 0,
            )
        }
        return MultiMatch(
            deckEntry = entry,
            bestOption = options.firstOrNull(),
            alternatives = options,
            realCardFallback = null,
        )
    }

    @Test
    fun droppedCardCount_isZero_whenAllCardsMatchSeller() {
        val matches = listOf(
            makeMultiMatch("Card A", listOf(Seller.BOOTLEG_MAGE)),
            makeMultiMatch("Card B", listOf(Seller.BOOTLEG_MAGE)),
        )
        val plan = ShoppingOptimizer.optimizeForSellers(matches, setOf(Seller.BOOTLEG_MAGE))
        assertEquals(0, plan.droppedCardCount)
        assertEquals(2, plan.orders.flatMap { it.items }.sumOf { it.qty })
    }

    @Test
    fun droppedCardCount_countsQty_whenCardsMissingSeller() {
        val matches = listOf(
            makeMultiMatch("Card A", listOf(Seller.BOOTLEG_MAGE)),
            makeMultiMatch("Card B", listOf(Seller.USEA)),
        )
        val plan = ShoppingOptimizer.optimizeForSellers(matches, setOf(Seller.BOOTLEG_MAGE))
        assertEquals(1, plan.droppedCardCount)
        assertEquals(1, plan.orders.flatMap { it.items }.sumOf { it.qty })
    }

    @Test
    fun droppedCardCount_allDropped_returnsEmptyPlanWithCount() {
        val matches = listOf(
            makeMultiMatch("Card A", listOf(Seller.USEA)),
            makeMultiMatch("Card B", listOf(Seller.TCGPLAYER)),
        )
        val plan = ShoppingOptimizer.optimizeForSellers(matches, setOf(Seller.BOOTLEG_MAGE))
        assertEquals(2, plan.droppedCardCount)
        assertEquals(0, plan.totalPriceCents)
        assertEquals(0, plan.orders.size)
    }

    @Test
    fun droppedCardCount_isZero_whenNoSellerFilter() {
        val matches = listOf(
            makeMultiMatch("Card A", listOf(Seller.USEA)),
            makeMultiMatch("Card B", listOf(Seller.BOOTLEG_MAGE)),
        )
        val plan = ShoppingOptimizer.optimize(matches)
        assertEquals(0, plan.droppedCardCount)
    }
}
