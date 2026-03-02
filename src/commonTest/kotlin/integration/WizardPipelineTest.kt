package integration

import match.Matcher
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.DeckEntryMatch
import model.MatchCandidate
import model.MatchStatus
import model.Preferences
import model.Section
import model.Seller
import model.VariantType
import optimizer.ShoppingOptimizer
import model.MatchOption
import model.MultiMatch
import state.CatalogUseCase
import state.MatchingUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the full wizard pipeline:
 *   Import → Parse → Preferences → Single Match → Multi Match → Optimize → Export
 *
 * These tests simulate what happens at each wizard step to verify
 * that data flows correctly through the entire pipeline.
 */
class WizardPipelineTest {

    private val matchingUseCase = MatchingUseCase()

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

    private val defaultPreferences = Preferences(
        includeSideboard = true,
        includeCommanders = true,
        includeTokens = true,
        variantPriority = listOf("Foil", "Holo", "Regular"),
        fuzzyEnabled = true,
    )

    private val defaultMatchConfig = Matcher.MatchConfig(
        variantPriority = defaultPreferences.variantPriority,
        setPriority = defaultPreferences.setPriority,
        fuzzyEnabled = defaultPreferences.fuzzyEnabled,
    )

    // -----------------------------------------------------------------------
    // Step 1 → 2: Import to Preferences (parse deck)
    // -----------------------------------------------------------------------

    @Test
    fun step1_importToPreferences_parsesDeckCorrectly() = runTest {
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            2 Tarmogoyf
            1 Black Lotus
            Sideboard:
            3 Pyroblast
            2 Hydroblast
        """.trimIndent()

        val entries = matchingUseCase.parseDeck(
            deckText,
            defaultPreferences.includeSideboard,
            defaultPreferences.includeCommanders,
        )

        assertEquals(6, entries.size)
        // Main deck cards
        assertEquals(Section.MAIN, entries[0].section)
        assertEquals("Lightning Bolt", entries[0].cardName)
        assertEquals(4, entries[0].qty)
        // Sideboard cards
        assertEquals(Section.SIDEBOARD, entries[4].section)
        assertEquals("Pyroblast", entries[4].cardName)
        assertTrue(entries.all { it.include }, "All entries should be included when sideboard=true")
    }

    @Test
    fun step1_importToPreferences_sideboardExcludedWhenDisabled() = runTest {
        val deckText = """
            4 Lightning Bolt
            Sideboard:
            3 Pyroblast
        """.trimIndent()

        val entries = matchingUseCase.parseDeck(deckText, includeSideboard = false, includeCommanders = true)

        assertEquals(2, entries.size)
        assertTrue(entries[0].include, "Main deck card should be included")
        assertEquals(false, entries[1].include, "Sideboard card should be excluded")
    }

    // -----------------------------------------------------------------------
    // Step 2 → 3: Preferences to Results (single-seller match)
    // -----------------------------------------------------------------------

    @Test
    fun step2_preferencesToResults_singleSellerMatch() = runTest {
        // Parse
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n4 Counterspell\n2 Tarmogoyf",
            includeSideboard = true,
            includeCommanders = true,
        )

        // Build catalog (simulates what loadCatalog() provides)
        val catalog = catalogOf(
            variant("Lightning Bolt", Seller.USEA, 220),
            variant("Counterspell", Seller.USEA, 180),
            variant("Tarmogoyf", Seller.USEA, 500),
        )

        // Match
        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)

        assertEquals(3, matches.size)
        assertTrue(matches.all { it.status == MatchStatus.AUTO_MATCHED })
        assertTrue(matches.all { it.selectedVariant != null })
    }

    @Test
    fun step2_preferencesToResults_partialMatches() = runTest {
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n1 Card Not In Catalog",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalog = catalogOf(variant("Lightning Bolt", Seller.USEA, 220))

        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)

        assertEquals(2, matches.size)
        assertEquals(MatchStatus.AUTO_MATCHED, matches[0].status)
        assertNotNull(matches[0].selectedVariant)

        // Second card not found
        assertTrue(
            matches[1].status == MatchStatus.NOT_FOUND || matches[1].status == MatchStatus.FUZZY_RECHECK,
        )
    }

    @Test
    fun step2_matchCountsReflectResults() = runTest {
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n2 Counterspell\n1 Missing Card",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalog = catalogOf(
            variant("Lightning Bolt", priceCents = 220),
            variant("Counterspell", priceCents = 180),
        )

        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)
        val counts = matchingUseCase.calculateMatchCounts(matches)

        assertEquals(2, counts.matched)
        assertTrue(counts.unmatched >= 1, "Missing Card should be unmatched")
        // 4*220 + 2*180 = 880 + 360 = 1240
        assertEquals(1240, counts.totalPrice)
    }

    // -----------------------------------------------------------------------
    // Step 2 → 3: Multi-seller matching (after single match)
    // -----------------------------------------------------------------------

    @Test
    fun step2_multiSellerMatch_splitsAcrossSellers() = runTest {
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n4 Counterspell\n2 Tarmogoyf",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(
                variant("Lightning Bolt", Seller.USEA, 220),
                variant("Counterspell", Seller.USEA, 220),
            ),
            Seller.BOOTLEG_MAGE to catalogOf(
                variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
                variant("Counterspell", Seller.BOOTLEG_MAGE, 250),
            ),
            Seller.TCGPLAYER to catalogOf(
                variant("Tarmogoyf", Seller.TCGPLAYER, 5000),
            ),
        )

        val config = MultiCatalogMatcher.Config(proxyFirst = true)
        val multiMatches = matchingUseCase.matchEntriesMulti(entries, catalogs, config)

        assertEquals(3, multiMatches.size)

        // Lightning Bolt: BM (180) cheaper than USEA (220) — both proxy
        assertEquals(Seller.BOOTLEG_MAGE, multiMatches[0].bestOption?.seller)

        // Counterspell: USEA (220) cheaper than BM (250) — both proxy
        assertEquals(Seller.USEA, multiMatches[1].bestOption?.seller)

        // Tarmogoyf: Only in TCGPlayer
        assertEquals(Seller.TCGPLAYER, multiMatches[2].bestOption?.seller)
    }

    @Test
    fun step2_multiSellerMatch_noCatalogForCard() = runTest {
        val entries = matchingUseCase.parseDeck(
            "1 Rare Unique Card",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Lightning Bolt", Seller.USEA, 220)),
        )

        val config = MultiCatalogMatcher.Config(proxyFirst = true)
        val multiMatches = matchingUseCase.matchEntriesMulti(entries, catalogs, config)

        assertEquals(1, multiMatches.size)
        // Card not found in any seller
        assertNull(multiMatches[0].bestOption)
    }

    // -----------------------------------------------------------------------
    // Step 3 → 4: Results to Export (shopping optimization)
    // -----------------------------------------------------------------------

    @Test
    fun step3_shoppingOptimizer_createsOrders() = runTest {
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n4 Counterspell\n2 Tarmogoyf",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(
                variant("Lightning Bolt", Seller.USEA, 220),
                variant("Counterspell", Seller.USEA, 220),
            ),
            Seller.BOOTLEG_MAGE to catalogOf(
                variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
                variant("Counterspell", Seller.BOOTLEG_MAGE, 250),
            ),
            Seller.TCGPLAYER to catalogOf(
                variant("Tarmogoyf", Seller.TCGPLAYER, 5000),
            ),
        )

        val multiMatches = MultiCatalogMatcher.match(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )

        val plan = ShoppingOptimizer.optimize(multiMatches)

        assertNotNull(plan)
        assertTrue(plan.orders.isNotEmpty(), "Should have at least one seller order")
        assertTrue(plan.totalPriceCents > 0, "Total price should be positive")

        // Verify all matched cards appear in some order
        val allOrderItems = plan.orders.flatMap { it.items }
        assertTrue(allOrderItems.isNotEmpty())
    }

    @Test
    fun step3_shoppingOptimizer_emptyMultiMatches() {
        val plan = ShoppingOptimizer.optimize(emptyList())

        assertEquals(0, plan.orders.size)
        assertEquals(0, plan.totalPriceCents)
    }

    @Test
    fun step3_shoppingOptimizer_allUnmatched() = runTest {
        val entries = matchingUseCase.parseDeck(
            "1 Card Not In Any Catalog",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Lightning Bolt", Seller.USEA, 220)),
        )

        val multiMatches = MultiCatalogMatcher.match(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )

        val plan = ShoppingOptimizer.optimize(multiMatches)

        // No matches → no orders
        assertEquals(0, plan.orders.size)
        assertEquals(0, plan.totalPriceCents)
    }

    // -----------------------------------------------------------------------
    // Full wizard flow: Import → Parse → Match → Multi-Match → Optimize
    // -----------------------------------------------------------------------

    @Test
    fun fullWizardFlow_allStepsSequential() = runTest {
        // === Step 1: Import (user enters deck text) ===
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            2 Tarmogoyf
            1 Black Lotus
        """.trimIndent()

        // === Step 1→2: wizardImportToPreferences ===
        // Parse deck with default preferences
        val entries = matchingUseCase.parseDeck(
            deckText,
            defaultPreferences.includeSideboard,
            defaultPreferences.includeCommanders,
        )
        assertEquals(4, entries.size, "Should parse 4 cards")

        // === Step 2→3: wizardPreferencesToResults ===
        // Single-seller match (runMatch)
        val singleCatalog = catalogOf(
            variant("Lightning Bolt", Seller.USEA, 220),
            variant("Counterspell", Seller.USEA, 180),
            variant("Tarmogoyf", Seller.USEA, 500),
        )
        val singleMatches = matchingUseCase.matchEntries(entries, singleCatalog, defaultMatchConfig)
        assertEquals(4, singleMatches.size)

        val matchedSingle = singleMatches.filter { it.status == MatchStatus.AUTO_MATCHED }
        assertEquals(3, matchedSingle.size, "3 of 4 cards should match in single catalog")

        val notFoundSingle = singleMatches.filter { it.status != MatchStatus.AUTO_MATCHED }
        assertTrue(notFoundSingle.any { it.deckEntry.cardName == "Black Lotus" })

        // Verify match counts
        val singleCounts = matchingUseCase.calculateMatchCounts(singleMatches)
        assertEquals(3, singleCounts.matched)

        // Multi-seller match (loadAllCatalogs → runMultiMatch)
        val multiCatalogs = mapOf(
            Seller.USEA to catalogOf(
                variant("Lightning Bolt", Seller.USEA, 220),
                variant("Counterspell", Seller.USEA, 220),
            ),
            Seller.BOOTLEG_MAGE to catalogOf(
                variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
                variant("Counterspell", Seller.BOOTLEG_MAGE, 250),
                variant("Black Lotus", Seller.BOOTLEG_MAGE, 500),
            ),
            Seller.TCGPLAYER to catalogOf(
                variant("Tarmogoyf", Seller.TCGPLAYER, 5000),
            ),
        )
        val multiMatches = matchingUseCase.matchEntriesMulti(
            entries, multiCatalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )
        assertEquals(4, multiMatches.size, "All 4 cards should have multi-match results")

        // All cards should now be matched (Black Lotus found in BM)
        val allMatched = multiMatches.all { it.bestOption != null }
        assertTrue(allMatched, "All cards should be matched across multiple sellers")

        // === Step 3→4: wizardResultsToExport ===
        val plan = ShoppingOptimizer.optimize(multiMatches)

        assertNotNull(plan)
        assertTrue(plan.orders.isNotEmpty())
        assertTrue(plan.totalPriceCents > 0)

        // Verify seller distribution
        val sellers = plan.orders.map { it.seller }.toSet()
        assertTrue(sellers.isNotEmpty(), "Should have orders from at least one seller")

        // Verify total makes sense
        val itemCount = plan.orders.sumOf { order -> order.items.sumOf { it.qty } }
        assertEquals(11, itemCount, "Total items should be 4+4+2+1=11")
    }

    @Test
    fun fullWizardFlow_withSideboardToggle() = runTest {
        val deckText = """
            4 Lightning Bolt
            Sideboard:
            2 Pyroblast
        """.trimIndent()

        // Step 1→2: Parse WITHOUT sideboard
        val entriesNoSB = matchingUseCase.parseDeck(deckText, includeSideboard = false, includeCommanders = true)
        assertEquals(2, entriesNoSB.size)

        val catalog = catalogOf(
            variant("Lightning Bolt", priceCents = 220),
            variant("Pyroblast", priceCents = 150),
        )
        val matchesNoSB = matchingUseCase.matchEntries(entriesNoSB, catalog, defaultMatchConfig)

        // Only main deck should be matched
        val countsNoSB = matchingUseCase.calculateMatchCounts(matchesNoSB)
        assertEquals(1, countsNoSB.matched, "Only Lightning Bolt should be matched")
        assertEquals(0, countsNoSB.unmatched, "Excluded sideboard cards should not count as unmatched")
        assertEquals(880, countsNoSB.totalPrice) // 4*220

        // Step 1→2 again: Parse WITH sideboard
        val entriesWithSB = matchingUseCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)
        val matchesWithSB = matchingUseCase.matchEntries(entriesWithSB, catalog, defaultMatchConfig)

        val countsWithSB = matchingUseCase.calculateMatchCounts(matchesWithSB)
        assertEquals(2, countsWithSB.matched, "Both cards should be matched")
        // 4*220 + 2*150 = 880 + 300 = 1180
        assertEquals(1180, countsWithSB.totalPrice)
    }

    @Test
    fun fullWizardFlow_emptyDeck() = runTest {
        val entries = matchingUseCase.parseDeck("", includeSideboard = true, includeCommanders = true)
        assertTrue(entries.isEmpty())

        val catalog = catalogOf(variant("Lightning Bolt"))
        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)
        assertTrue(matches.isEmpty())

        val multiMatches = matchingUseCase.matchEntriesMulti(
            entries,
            mapOf(Seller.USEA to catalog),
            MultiCatalogMatcher.Config(proxyFirst = true),
        )
        assertTrue(multiMatches.isEmpty())

        val plan = ShoppingOptimizer.optimize(multiMatches)
        assertEquals(0, plan.orders.size)
        assertEquals(0, plan.totalPriceCents)
    }

    // -----------------------------------------------------------------------
    // Resolve candidate flow (manual card selection)
    // -----------------------------------------------------------------------

    @Test
    fun resolveCandidate_manualSelection() = runTest {
        val entries = matchingUseCase.parseDeck(
            "1 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )

        // Create catalog with multiple variants → ambiguous
        val foil = variant("Lightning Bolt", variantType = VariantType.FOIL, priceCents = 350, setCode = "M11")
        val regular = variant("Lightning Bolt", variantType = VariantType.REGULAR, priceCents = 220, setCode = "M11")
        val catalog = catalogOf(foil, regular)

        // Use config with empty priority to force ambiguity
        val config = Matcher.MatchConfig(
            variantPriority = emptyList(),
            setPriority = emptyList(),
            fuzzyEnabled = true,
        )
        val matches = matchingUseCase.matchEntries(entries, catalog, config)

        // Simulate user resolving the match
        val originalMatch = matches[0]
        // Auto-matched picks one, but user can override
        val userChoice = regular
        val resolved = originalMatch.copy(
            status = MatchStatus.MANUAL_SELECTED,
            selectedVariant = userChoice,
        )

        assertEquals(MatchStatus.MANUAL_SELECTED, resolved.status)
        assertEquals(regular.sku, resolved.selectedVariant!!.sku)

        // Recalculate counts after resolution
        val counts = matchingUseCase.calculateMatchCounts(listOf(resolved))
        assertEquals(1, counts.matched)
        assertEquals(0, counts.unmatched)
        assertEquals(220, counts.totalPrice) // 1 * 220
    }

    // -----------------------------------------------------------------------
    // Override seller flow
    // -----------------------------------------------------------------------

    @Test
    fun overrideSeller_updatesMultiMatch() = runTest {
        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt",
            includeSideboard = true,
            includeCommanders = true,
        )

        val catalogs = mapOf(
            Seller.USEA to catalogOf(variant("Lightning Bolt", Seller.USEA, 220)),
            Seller.BOOTLEG_MAGE to catalogOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)),
        )

        val multiMatches = matchingUseCase.matchEntriesMulti(
            entries, catalogs, MultiCatalogMatcher.Config(proxyFirst = true)
        )

        // Best is BM (180)
        assertEquals(Seller.BOOTLEG_MAGE, multiMatches[0].bestOption?.seller)

        // Simulate overriding to USEA (the overrideCardSeller handler)
        val match = multiMatches[0]
        val useaOption = match.alternatives.first { it.seller == Seller.USEA }
        val overridden = match.copy(bestOption = useaOption)

        assertEquals(Seller.USEA, overridden.bestOption?.seller)
        assertEquals(220, overridden.bestOption?.priceCents)

        // Optimize all-seller plan (Pro plan) — always picks cheapest regardless of override
        val plan = ShoppingOptimizer.optimize(listOf(overridden))
        val bmOrder = plan.orders.find { it.seller == Seller.BOOTLEG_MAGE }
        assertNotNull(bmOrder, "Pro plan should pick cheapest seller (BM at 180)")
        assertEquals(720, bmOrder.subtotalCents) // 4 * 180

        // Optimize restricted to USEA only — override seller is respected within constraint
        val useaPlan = ShoppingOptimizer.optimizeForSellers(listOf(overridden), setOf(Seller.USEA))
        val useaOrder = useaPlan.orders.find { it.seller == Seller.USEA }
        assertNotNull(useaOrder, "Should have a USEA order when restricted")
        assertEquals(880, useaOrder.subtotalCents) // 4 * 220
    }

    // -----------------------------------------------------------------------
    // Refresh matches from catalog (simulates reactive pipeline)
    // -----------------------------------------------------------------------

    @Test
    fun refreshMatches_updatesVariantsFromCatalog() {
        val originalVariant = variant("Lightning Bolt", priceCents = 220)
        val matches = listOf(
            DeckEntryMatch(
                deckEntry = DeckEntry("1", "4 Lightning Bolt", 4, "Lightning Bolt", Section.MAIN, true),
                status = MatchStatus.AUTO_MATCHED,
                selectedVariant = originalVariant,
                candidates = listOf(MatchCandidate(originalVariant, 0, "exact")),
            )
        )

        val updatedVariant = originalVariant.copy(imageUrl = "https://example.com/bolt.jpg")
        val updatedCatalog = Catalog(listOf(updatedVariant))

        val refreshed = CatalogUseCase.refreshMatchesFromCatalog(matches, updatedCatalog)

        assertEquals(1, refreshed.size)
        assertEquals("https://example.com/bolt.jpg", refreshed[0].selectedVariant?.imageUrl)
        assertEquals("https://example.com/bolt.jpg", refreshed[0].candidates[0].variant.imageUrl)
    }

    @Test
    fun refreshMultiMatches_updatesVariantsFromCatalog() {
        val bolt = variant("Lightning Bolt", Seller.USEA, priceCents = 220)
        val boltBM = variant("Lightning Bolt", Seller.BOOTLEG_MAGE, priceCents = 180)
        val boltTCG = variant("Lightning Bolt", Seller.TCGPLAYER, priceCents = 300)

        val entry = DeckEntry("1", "4 Lightning Bolt", 4, "Lightning Bolt", Section.MAIN, true)
        val multiMatches = listOf(
            MultiMatch(
                deckEntry = entry,
                bestOption = MatchOption(boltBM, Seller.BOOTLEG_MAGE, 180, isProxy = true, matchScore = 0),
                alternatives = listOf(
                    MatchOption(bolt, Seller.USEA, 220, isProxy = true, matchScore = 0),
                ),
                realCardFallback = MatchOption(boltTCG, Seller.TCGPLAYER, 300, isProxy = false, matchScore = 0),
            )
        )

        // Simulate catalog update — bestOption, alternative, and fallback all get image URLs
        val updatedBoltBM = boltBM.copy(imageUrl = "https://example.com/bm-bolt.jpg")
        val updatedBolt = bolt.copy(imageUrl = "https://example.com/usea-bolt.jpg")
        val updatedBoltTCG = boltTCG.copy(imageUrl = "https://example.com/tcg-bolt.jpg")
        val updatedCatalog = Catalog(listOf(updatedBoltBM, updatedBolt, updatedBoltTCG))

        val refreshed = CatalogUseCase.refreshMultiMatchesFromCatalog(multiMatches, updatedCatalog)

        assertEquals(1, refreshed.size)
        assertEquals("https://example.com/bm-bolt.jpg", refreshed[0].bestOption?.variant?.imageUrl)
        assertEquals("https://example.com/usea-bolt.jpg", refreshed[0].alternatives[0].variant.imageUrl)
        assertEquals("https://example.com/tcg-bolt.jpg", refreshed[0].realCardFallback?.variant?.imageUrl)
    }

    // -----------------------------------------------------------------------
    // Large decklist stress test
    // -----------------------------------------------------------------------

    @Test
    fun largeDecklist_matchPerformance() = runTest {
        // Build a 60-card decklist
        val cardNames = (1..60).map { "Test Card $it" }
        val deckText = cardNames.joinToString("\n") { "1 $it" }

        val entries = matchingUseCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)
        assertEquals(60, entries.size)

        // Build catalog with all cards
        val variants = cardNames.map { variant(it, priceCents = 100) }
        val catalog = Catalog(variants)

        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)
        assertEquals(60, matches.size)
        assertTrue(matches.all { it.status == MatchStatus.AUTO_MATCHED })

        val counts = matchingUseCase.calculateMatchCounts(matches)
        assertEquals(60, counts.matched)
        assertEquals(0, counts.unmatched)
        assertEquals(6000, counts.totalPrice) // 60 * 1 * 100
    }
}
