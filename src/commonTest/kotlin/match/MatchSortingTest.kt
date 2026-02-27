package match

import model.CardVariant
import model.MatchOption
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchSortingTest {

    private fun variant(
        name: String = "Test Card",
        seller: Seller = Seller.USEA,
        priceCents: Int = 220,
        variantType: VariantType = VariantType.REGULAR,
    ): CardVariant = CardVariant(
        nameOriginal = name,
        nameNormalized = NameNormalizer.normalize(name),
        setCode = "TST",
        sku = "${seller.name}-${name.replace(" ", "")}-${variantType.name}",
        variantType = variantType,
        priceInCents = priceCents,
        seller = seller,
    )

    @Test
    fun variantComparator_proxyFirstWhenEnabled() {
        val proxyVariant = variant(seller = Seller.USEA, priceCents = 300)
        val realVariant = variant(seller = Seller.TCGPLAYER, priceCents = 100)

        val sorted = listOf(realVariant, proxyVariant).sortedWith(
            MatchSorting.variantComparator(
                proxyFirst = true,
                variantPriority = listOf("Regular"),
                setPriority = emptyList(),
            )
        )

        assertEquals(Seller.USEA, sorted.first().seller, "Proxy should come first when proxyFirst=true")
    }

    @Test
    fun variantComparator_realFirstWhenProxyDisabled() {
        val proxyVariant = variant(seller = Seller.USEA, priceCents = 100)
        val realVariant = variant(seller = Seller.TCGPLAYER, priceCents = 300)

        val sorted = listOf(proxyVariant, realVariant).sortedWith(
            MatchSorting.variantComparator(
                proxyFirst = false,
                variantPriority = listOf("Regular"),
                setPriority = emptyList(),
            )
        )

        assertEquals(Seller.TCGPLAYER, sorted.first().seller, "Real should come first when proxyFirst=false")
    }

    @Test
    fun matchOptionComparator_proxyFirstThenCheapest() {
        val expensiveProxy = MatchOption(
            variant = variant(seller = Seller.USEA, priceCents = 500),
            seller = Seller.USEA,
            priceCents = 500,
            isProxy = true,
            matchScore = 0,
        )
        val cheapReal = MatchOption(
            variant = variant(seller = Seller.TCGPLAYER, priceCents = 100),
            seller = Seller.TCGPLAYER,
            priceCents = 100,
            isProxy = false,
            matchScore = 0,
        )

        val sorted = listOf(cheapReal, expensiveProxy).sortedWith(
            MatchSorting.matchOptionComparator(proxyFirst = true)
        )

        assertEquals(true, sorted.first().isProxy, "Proxy option should come first")
    }

    @Test
    fun fuzzyThreshold_shortNames() {
        assertEquals(2, MatchSorting.fuzzyThreshold(10))
        assertEquals(2, MatchSorting.fuzzyThreshold(15))
    }

    @Test
    fun fuzzyThreshold_longNames() {
        assertEquals(3, MatchSorting.fuzzyThreshold(16))
        assertEquals(3, MatchSorting.fuzzyThreshold(30))
    }

    @Test
    fun fuzzyThreshold_relaxed() {
        assertEquals(4, MatchSorting.fuzzyThreshold(10, relaxed = true))
        assertEquals(5, MatchSorting.fuzzyThreshold(16, relaxed = true))
    }
}
