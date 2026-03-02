package state

import catalog.CatalogSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.DeckEntry
import model.OrderItem
import model.Section
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckSearchUseCaseTest {

    // -- Helpers --

    private fun testVariant(
        name: String,
        seller: Seller,
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

    private fun testEntry(
        name: String,
        qty: Int = 1,
        include: Boolean = true,
    ): DeckEntry = DeckEntry(
        id = "test_${name.replace(" ", "_")}",
        originalLine = "$qty $name",
        qty = qty,
        cardName = name,
        section = Section.MAIN,
        include = include,
    )

    /** A fake CatalogSource that returns the given variants from fetchCatalog. */
    private class FakeCatalogSource(
        override val seller: Seller,
        private val variants: List<CardVariant>,
        private val shouldFail: Boolean = false,
    ) : CatalogSource {
        override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
            if (shouldFail) throw RuntimeException("Simulated fetch failure for ${seller.name}")
            return variants
        }

        override suspend fun search(cardName: String): List<CardVariant> = emptyList()
        override fun checkoutUrl(items: List<OrderItem>): String? = null
        override fun formatForExport(items: List<OrderItem>): String = ""
    }

    /** Creates a DeckSearchUseCase with fake functional deps (no real DB). */
    private fun createUseCase(
        sources: List<CatalogSource>,
        storedVariants: MutableList<CardVariant> = mutableListOf(),
    ): DeckSearchUseCase {
        return DeckSearchUseCase(
            sources = sources,
            replaceCatalogForSeller = { seller, variants ->
                storedVariants.removeAll { it.seller == seller }
                storedVariants.addAll(variants)
            },
            markSellerFetched = { /* no-op */ },
            getAllVariants = { storedVariants.toList() },
        )
    }

    // -- Tests --

    @Test
    fun emitsProgressAndCompletes() = runTest {
        val source = FakeCatalogSource(
            seller = Seller.USEA,
            variants = listOf(testVariant("Lightning Bolt", Seller.USEA)),
        )

        val entries = listOf(testEntry("Lightning Bolt", qty = 4))
        val useCase = createUseCase(sources = listOf(source))
        val config = MultiCatalogMatcher.Config()

        val emissions = useCase.search(entries, config).toList()

        // At least 2 emissions: one intermediate + one final
        assertTrue(emissions.size >= 2, "Expected at least 2 emissions, got ${emissions.size}")

        // Last emission must be complete
        val last = emissions.last()
        assertTrue(last.isComplete, "Final emission should have isComplete=true")
        assertEquals(1, last.totalCards)
    }

    @Test
    fun filtersToMatchingCards() = runTest {
        // Supplier returns 3 cards, but deck only has 1
        val source = FakeCatalogSource(
            seller = Seller.USEA,
            variants = listOf(
                testVariant("Lightning Bolt", Seller.USEA),
                testVariant("Counterspell", Seller.USEA),
                testVariant("Tarmogoyf", Seller.USEA),
            ),
        )

        val entries = listOf(testEntry("Lightning Bolt"))
        val storedVariants = mutableListOf<CardVariant>()
        val useCase = createUseCase(sources = listOf(source), storedVariants = storedVariants)
        val config = MultiCatalogMatcher.Config()

        useCase.search(entries, config).toList()

        // Only Lightning Bolt should be stored (filtered by deck entries)
        assertEquals(1, storedVariants.size, "Only matching card should be stored")
        assertEquals(
            NameNormalizer.normalize("Lightning Bolt"),
            storedVariants[0].nameNormalized,
        )
    }

    @Test
    fun supplierFailureDoesNotBlockOthers() = runTest {
        val failSource = FakeCatalogSource(
            seller = Seller.USEA,
            variants = emptyList(),
            shouldFail = true,
        )
        val okSource = FakeCatalogSource(
            seller = Seller.BOOTLEG_MAGE,
            variants = listOf(testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE)),
        )

        val entries = listOf(testEntry("Lightning Bolt"))
        val useCase = createUseCase(sources = listOf(failSource, okSource))
        val config = MultiCatalogMatcher.Config()

        val emissions = useCase.search(entries, config).toList()
        val last = emissions.last()

        assertTrue(last.isComplete, "Search should complete even with one supplier failing")
        assertEquals(SearchState.ERROR, last.sellerStatuses[Seller.USEA]?.state)
        assertEquals(SearchState.DONE, last.sellerStatuses[Seller.BOOTLEG_MAGE]?.state)
    }

    @Test
    fun emptyDeckReturnsImmediately() = runTest {
        val source = FakeCatalogSource(
            seller = Seller.USEA,
            variants = listOf(testVariant("Lightning Bolt", Seller.USEA)),
        )

        // All entries have include = false
        val entries = listOf(testEntry("Lightning Bolt", include = false))
        val useCase = createUseCase(sources = listOf(source))
        val config = MultiCatalogMatcher.Config()

        val emissions = useCase.search(entries, config).toList()

        // Should get a single complete emission
        assertEquals(1, emissions.size, "Empty deck should emit exactly once")
        val single = emissions.first()
        assertTrue(single.isComplete)
        assertEquals(0, single.totalCards)
    }

    @Test
    fun bulkSearchFallbackWhenCatalogEmpty() = runTest {
        // Source returns empty from fetchCatalog but supports bulk search
        val source = object : CatalogSource {
            override val seller = Seller.TCGPLAYER
            override val supportsBulkSearch = true
            override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> = emptyList()
            override suspend fun searchBulk(cardNames: List<String>, log: (String) -> Unit): List<CardVariant> {
                return cardNames.map { testVariant(it, Seller.TCGPLAYER) }
            }
            override suspend fun search(cardName: String): List<CardVariant> = emptyList()
            override fun checkoutUrl(items: List<OrderItem>): String? = null
            override fun formatForExport(items: List<OrderItem>): String = ""
        }

        val entries = listOf(testEntry("Lightning Bolt"))
        val storedVariants = mutableListOf<CardVariant>()
        val useCase = createUseCase(sources = listOf(source), storedVariants = storedVariants)
        val config = MultiCatalogMatcher.Config()

        val emissions = useCase.search(entries, config).toList()
        val last = emissions.last()

        assertTrue(last.isComplete)
        assertEquals(SearchState.DONE, last.sellerStatuses[Seller.TCGPLAYER]?.state)
        // Should have found cards via bulk search fallback
        assertTrue(storedVariants.isNotEmpty(), "Bulk search fallback should produce variants")
    }

    @Test
    fun multipleSuppliersSearchInParallel() = runTest {
        val useaSource = FakeCatalogSource(
            seller = Seller.USEA,
            variants = listOf(
                testVariant("Lightning Bolt", Seller.USEA, 220),
                testVariant("Counterspell", Seller.USEA, 200),
            ),
        )
        val bmSource = FakeCatalogSource(
            seller = Seller.BOOTLEG_MAGE,
            variants = listOf(
                testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
            ),
        )

        val entries = listOf(
            testEntry("Lightning Bolt"),
            testEntry("Counterspell"),
        )
        val useCase = createUseCase(sources = listOf(useaSource, bmSource))
        val config = MultiCatalogMatcher.Config(proxyFirst = true)

        val emissions = useCase.search(entries, config).toList()
        val last = emissions.last()

        assertTrue(last.isComplete)
        assertEquals(2, last.totalCards)
        // Both suppliers should be DONE
        assertEquals(SearchState.DONE, last.sellerStatuses[Seller.USEA]?.state)
        assertEquals(SearchState.DONE, last.sellerStatuses[Seller.BOOTLEG_MAGE]?.state)
        // Multi-match results should include matches from both suppliers
        assertTrue(last.multiMatches.isNotEmpty())
    }
}
