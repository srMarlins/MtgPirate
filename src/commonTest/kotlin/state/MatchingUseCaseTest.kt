package state

import deck.DecklistParser
import match.Matcher
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.MatchStatus
import model.Preferences
import model.Section
import model.Seller
import model.VariantType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [MatchingUseCase] — the use-case layer that orchestrates
 * deck parsing, single-seller matching, multi-seller matching, and count calculation.
 */
class MatchingUseCaseTest {

    private val useCase = MatchingUseCase()

    // -- Test helpers --

    private fun variant(
        name: String,
        seller: Seller = Seller.USEA,
        priceCents: Int = 220,
        setCode: String = "TST",
        variantType: VariantType = VariantType.REGULAR,
    ): CardVariant = CardVariant(
        nameOriginal = name,
        nameNormalized = NameNormalizer.normalize(name),
        setCode = setCode,
        sku = "${seller.name}-${name.replace(" ", "")}-$setCode-${variantType.name}",
        variantType = variantType,
        priceInCents = priceCents,
        seller = seller,
    )

    private fun catalogOf(vararg variants: CardVariant) = Catalog(variants.toList())

    private val defaultConfig = Matcher.MatchConfig(
        variantPriority = listOf("Foil", "Holo", "Regular"),
        setPriority = emptyList(),
        fuzzyEnabled = true,
    )

    // -----------------------------------------------------------------------
    // parseDeck tests
    // -----------------------------------------------------------------------

    @Test
    fun parseDeck_basicDecklist() = runTest {
        val deckText = """
            4 Lightning Bolt
            3 Counterspell
            2 Tarmogoyf
        """.trimIndent()

        val entries = useCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)

        assertEquals(3, entries.size)
        assertEquals("Lightning Bolt", entries[0].cardName)
        assertEquals(4, entries[0].qty)
        assertEquals("Counterspell", entries[1].cardName)
        assertEquals(3, entries[1].qty)
        assertEquals("Tarmogoyf", entries[2].cardName)
        assertEquals(2, entries[2].qty)
    }

    @Test
    fun parseDeck_withSideboard() = runTest {
        val deckText = """
            4 Lightning Bolt
            Sideboard:
            2 Pyroblast
        """.trimIndent()

        val withSideboard = useCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)
        assertEquals(2, withSideboard.size)
        assertTrue(withSideboard.all { it.include })

        val withoutSideboard = useCase.parseDeck(deckText, includeSideboard = false, includeCommanders = true)
        assertEquals(2, withoutSideboard.size)
        // Sideboard entry should be excluded
        val sideboardEntry = withoutSideboard.first { it.cardName == "Pyroblast" }
        assertEquals(false, sideboardEntry.include)
    }

    @Test
    fun parseDeck_emptyInput() = runTest {
        val entries = useCase.parseDeck("", includeSideboard = true, includeCommanders = true)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun parseDeck_withSetCodeHints() = runTest {
        val deckText = "4 Lightning Bolt (M11)"
        val entries = useCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)

        assertEquals(1, entries.size)
        assertEquals("Lightning Bolt", entries[0].cardName)
        assertEquals("M11", entries[0].setCodeHint)
    }

    // -----------------------------------------------------------------------
    // matchEntries (single-seller) tests
    // -----------------------------------------------------------------------

    @Test
    fun matchEntries_allExactMatches() = runTest {
        val entries = useCase.parseDeck(
            "4 Lightning Bolt\n3 Counterspell",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(
            variant("Lightning Bolt"),
            variant("Counterspell"),
        )

        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(2, matches.size)
        assertTrue(matches.all { it.status == MatchStatus.AUTO_MATCHED })
        assertNotNull(matches[0].selectedVariant)
        assertEquals("Lightning Bolt", matches[0].selectedVariant!!.nameOriginal)
        assertNotNull(matches[1].selectedVariant)
        assertEquals("Counterspell", matches[1].selectedVariant!!.nameOriginal)
    }

    @Test
    fun matchEntries_notFoundCard() = runTest {
        val entries = useCase.parseDeck(
            "1 Nonexistent Card Name XYZ",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(variant("Lightning Bolt"))

        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(1, matches.size)
        assertTrue(
            matches[0].status == MatchStatus.NOT_FOUND || matches[0].status == MatchStatus.FUZZY_RECHECK,
            "Expected NOT_FOUND or FUZZY_RECHECK, got ${matches[0].status}"
        )
    }

    @Test
    fun matchEntries_fuzzyMatch() = runTest {
        val entries = useCase.parseDeck(
            "1 Ligthning Bolt",  // intentional typo
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(variant("Lightning Bolt"))

        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(1, matches.size)
        // Fuzzy match should find it as AMBIGUOUS (needs user resolution)
        assertTrue(
            matches[0].status == MatchStatus.AMBIGUOUS || matches[0].status == MatchStatus.FUZZY_RECHECK,
            "Expected AMBIGUOUS or FUZZY_RECHECK for fuzzy match, got ${matches[0].status}"
        )
        assertTrue(matches[0].candidates.isNotEmpty(), "Should have fuzzy candidates")
    }

    @Test
    fun matchEntries_emptyCatalog() = runTest {
        val entries = useCase.parseDeck(
            "4 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = Catalog(emptyList())

        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(1, matches.size)
        assertEquals(MatchStatus.NOT_FOUND, matches[0].status)
        assertNull(matches[0].selectedVariant)
    }

    @Test
    fun matchEntries_multipleVariantsSelectByPriority() = runTest {
        val entries = useCase.parseDeck(
            "1 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(
            variant("Lightning Bolt", variantType = VariantType.REGULAR, priceCents = 220),
            variant("Lightning Bolt", variantType = VariantType.FOIL, priceCents = 350),
            variant("Lightning Bolt", variantType = VariantType.HOLO, priceCents = 300),
        )

        // Price-first: cheapest variant wins, variant priority is tiebreaker
        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(1, matches.size)
        assertEquals(MatchStatus.AUTO_MATCHED, matches[0].status)
        // Should pick Regular since it's cheapest ($2.20 < $3.00 < $3.50)
        assertEquals(VariantType.REGULAR, matches[0].selectedVariant!!.variantType)
    }

    @Test
    fun matchEntries_excludedEntries() = runTest {
        val deckText = """
            4 Lightning Bolt
            Sideboard:
            2 Pyroblast
        """.trimIndent()
        val entries = useCase.parseDeck(deckText, includeSideboard = false, includeCommanders = true)
        val catalog = catalogOf(
            variant("Lightning Bolt"),
            variant("Pyroblast"),
        )

        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        assertEquals(2, matches.size)
        // Main card matched
        assertEquals(MatchStatus.AUTO_MATCHED, matches[0].status)
        // Sideboard card excluded
        assertEquals(MatchStatus.UNRESOLVED, matches[1].status)
    }

    // -----------------------------------------------------------------------
    // matchEntriesMulti (multi-seller) tests
    // -----------------------------------------------------------------------

    @Test
    fun matchEntriesMulti_bestPriceAcrossSellers() = runTest {
        val entries = useCase.parseDeck(
            "4 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Lightning Bolt", Seller.USEA, 220)),
            Seller.BOOTLEG_MAGE to catalogOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)),
        )

        val config = MultiCatalogMatcher.Config(proxyFirst = true)
        val multiMatches = useCase.matchEntriesMulti(entries, catalogs, config)

        assertEquals(1, multiMatches.size)
        // BM is cheaper, both are proxy, so BM should win
        assertEquals(Seller.BOOTLEG_MAGE, multiMatches[0].bestOption?.seller)
        assertEquals(180, multiMatches[0].bestOption?.priceCents)
        // Should have alternatives from both sellers
        assertTrue(multiMatches[0].alternatives.size >= 2)
    }

    @Test
    fun matchEntriesMulti_proxyFirstPreference() = runTest {
        val entries = useCase.parseDeck(
            "1 Tarmogoyf",
            includeSideboard = true,
            includeCommanders = true,
        )

        // Use same price so the proxy/non-proxy preference is the deciding factor
        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Tarmogoyf", Seller.USEA, 500)),
            Seller.TCGPLAYER to catalogOf(variant("Tarmogoyf", Seller.TCGPLAYER, 500)),
        )

        // Proxy first = true → USEA (proxy) should sort before TCGPlayer (non-proxy)
        val proxyFirst = useCase.matchEntriesMulti(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )
        assertEquals(Seller.USEA, proxyFirst[0].bestOption?.seller)

        // Proxy first = false → all treated equally, falls through to match score then price.
        // Same price, same score → order depends on insertion order. Just verify it matches.
        val realFirst = useCase.matchEntriesMulti(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = false)
        )
        assertNotNull(realFirst[0].bestOption, "Should still find a match with proxyFirst=false")
    }

    @Test
    fun matchEntriesMulti_realCardFallback() = runTest {
        val entries = useCase.parseDeck(
            "1 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.BOOTLEG_MAGE to catalogOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)),
            Seller.TCGPLAYER to catalogOf(variant("Lightning Bolt", Seller.TCGPLAYER, 2500)),
        )

        val multiMatches = useCase.matchEntriesMulti(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )

        assertEquals(1, multiMatches.size)
        // Best option should be proxy (BM)
        assertEquals(Seller.BOOTLEG_MAGE, multiMatches[0].bestOption?.seller)
        assertTrue(multiMatches[0].bestOption!!.isProxy)
        // Real card fallback should be TCGPlayer
        assertNotNull(multiMatches[0].realCardFallback)
        assertEquals(Seller.TCGPLAYER, multiMatches[0].realCardFallback!!.seller)
    }

    @Test
    fun matchEntriesMulti_cardOnlyInOneSeller() = runTest {
        val entries = useCase.parseDeck(
            "1 Black Lotus",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Lightning Bolt", Seller.USEA, 220)),
            Seller.BOOTLEG_MAGE to catalogOf(variant("Black Lotus", Seller.BOOTLEG_MAGE, 500)),
        )

        val multiMatches = useCase.matchEntriesMulti(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )

        assertEquals(1, multiMatches.size)
        assertEquals(Seller.BOOTLEG_MAGE, multiMatches[0].bestOption?.seller)
        // No real card fallback since it's only in one proxy seller
        assertNull(multiMatches[0].realCardFallback)
    }

    // -----------------------------------------------------------------------
    // calculateMatchCounts tests
    // -----------------------------------------------------------------------

    @Test
    fun calculateMatchCounts_allMatched() = runTest {
        val entries = useCase.parseDeck(
            "4 Lightning Bolt\n2 Counterspell",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(
            variant("Lightning Bolt", priceCents = 220),
            variant("Counterspell", priceCents = 180),
        )
        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        val counts = useCase.calculateMatchCounts(matches)

        assertEquals(2, counts.matched)
        assertEquals(0, counts.unmatched)
        assertEquals(0, counts.ambiguous)
        // 4 * 220 + 2 * 180 = 880 + 360 = 1240
        assertEquals(1240, counts.totalPrice)
    }

    @Test
    fun calculateMatchCounts_withUnmatched() = runTest {
        val entries = useCase.parseDeck(
            "4 Lightning Bolt\n1 Nonexistent Card XYZ",
            includeSideboard = true,
            includeCommanders = true,
        )
        val catalog = catalogOf(variant("Lightning Bolt", priceCents = 220))
        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        val counts = useCase.calculateMatchCounts(matches)

        assertEquals(1, counts.matched)
        // "Nonexistent Card XYZ" should be unmatched (included but no selection)
        assertTrue(counts.unmatched >= 1, "Expected at least 1 unmatched")
        assertEquals(880, counts.totalPrice) // 4 * 220
    }

    @Test
    fun calculateMatchCounts_emptyMatches() = runTest {
        val counts = useCase.calculateMatchCounts(emptyList())

        assertEquals(0, counts.matched)
        assertEquals(0, counts.unmatched)
        assertEquals(0, counts.ambiguous)
        assertEquals(0, counts.totalPrice)
    }

    @Test
    fun calculateMatchCounts_excludedEntriesNotCountedAsUnmatched() = runTest {
        val deckText = """
            4 Lightning Bolt
            Sideboard:
            2 Pyroblast
        """.trimIndent()
        val entries = useCase.parseDeck(deckText, includeSideboard = false, includeCommanders = true)
        val catalog = catalogOf(variant("Lightning Bolt", priceCents = 220))
        val matches = useCase.matchEntries(entries, catalog, defaultConfig)

        val counts = useCase.calculateMatchCounts(matches)

        assertEquals(1, counts.matched)
        // Pyroblast is excluded (include=false), so it should NOT be counted as unmatched
        assertEquals(0, counts.unmatched)
        assertEquals(880, counts.totalPrice) // 4 * 220
    }

    // -----------------------------------------------------------------------
    // End-to-end: parse → match → count pipeline
    // -----------------------------------------------------------------------

    @Test
    fun fullPipeline_parseMatchCount() = runTest {
        // Simulate what happens when user navigates Import → Preferences → Results
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            2 Tarmogoyf
            1 Black Lotus
        """.trimIndent()

        // Step 1: Parse
        val entries = useCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)
        assertEquals(4, entries.size)

        // Step 2: Match against single catalog
        val catalog = catalogOf(
            variant("Lightning Bolt", priceCents = 220),
            variant("Counterspell", priceCents = 180),
            variant("Tarmogoyf", priceCents = 5000),
            // Black Lotus not in catalog
        )
        val matches = useCase.matchEntries(entries, catalog, defaultConfig)
        assertEquals(4, matches.size)

        val matched = matches.filter { it.status == MatchStatus.AUTO_MATCHED }
        assertEquals(3, matched.size)

        val notFound = matches.filter { it.status == MatchStatus.NOT_FOUND || it.status == MatchStatus.FUZZY_RECHECK }
        assertTrue(notFound.isNotEmpty(), "Black Lotus should be not found")

        // Step 3: Calculate counts
        val counts = useCase.calculateMatchCounts(matches)
        assertEquals(3, counts.matched)
        assertTrue(counts.unmatched >= 1)
        // 4*220 + 4*180 + 2*5000 = 880 + 720 + 10000 = 11600
        assertEquals(11600, counts.totalPrice)
    }
}
