package integration

import catalog.BootlegMageCatalogSource
import catalog.KtorRemoteCatalogDataSource
import catalog.ScryfallApi
import catalog.ScryfallPricingSource
import catalog.UseaCatalogSource
import match.Matcher
import match.MultiCatalogMatcher
import model.Catalog
import model.MatchStatus
import model.Seller
import optimizer.ShoppingOptimizer
import state.MatchingUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Real end-to-end integration tests that hit live seller APIs.
 *
 * These tests verify the full pipeline works with actual data:
 *   Parse decklist → Fetch real catalogs → Match → Multi-match → Optimize
 *
 * NOTE: These depend on external APIs being available. If a seller's site is
 * down, the test will log the failure and skip that seller gracefully.
 */
class RealPipelineTest {

    private val matchingUseCase = MatchingUseCase()

    private val defaultMatchConfig = Matcher.MatchConfig(
        variantPriority = listOf("Foil", "Holo", "Regular"),
        setPriority = emptyList(),
        fuzzyEnabled = true,
    )

    // A small, realistic decklist with well-known cards
    private val testDeckText = """
        4 Lightning Bolt
        4 Counterspell
        2 Swords to Plowshares
        1 Sol Ring
        1 Mana Crypt
        1 Dark Ritual
        4 Brainstorm
        4 Ponder
    """.trimIndent()

    // -----------------------------------------------------------------------
    // Individual catalog source tests
    // -----------------------------------------------------------------------

    @Test
    fun bootlegMage_fetchCatalogAndMatch() = runTest(timeout = 2.minutes) {
        val source = BootlegMageCatalogSource()
        val variants = try {
            source.fetchCatalog { println("[BM] $it") }
        } catch (e: Exception) {
            println("BootlegMage unavailable (${e.message}), skipping test")
            return@runTest
        }

        println("BootlegMage: fetched ${variants.size} variants")
        if (variants.isEmpty()) {
            println("BootlegMage returned empty catalog (site may be down), skipping assertions")
            return@runTest
        }

        // Verify variant structure
        val sample = variants.first()
        assertTrue(sample.nameOriginal.isNotBlank(), "Variant name should not be blank")
        assertTrue(sample.priceInCents > 0, "Variant price should be positive")
        assertEquals(Seller.BOOTLEG_MAGE, sample.seller)

        // Match against real catalog
        val catalog = Catalog(variants)
        val entries = matchingUseCase.parseDeck(testDeckText, includeSideboard = true, includeCommanders = true)

        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)
        assertEquals(entries.size, matches.size, "Should have a match result for every entry")

        val matchedCount = matches.count { it.status == MatchStatus.AUTO_MATCHED }
        println("BootlegMage: matched $matchedCount / ${entries.size} cards")
        assertTrue(matchedCount > 0, "Should match at least some cards against BootlegMage catalog")

        // Calculate counts
        val counts = matchingUseCase.calculateMatchCounts(matches)
        assertEquals(matchedCount, counts.matched)
        if (matchedCount > 0) {
            assertTrue(counts.totalPrice > 0, "Matched cards should have a positive total price")
        }
    }

    @Test
    fun usea_fetchCatalogAndMatch() = runTest(timeout = 2.minutes) {
        val remoteSource = KtorRemoteCatalogDataSource()
        val source = UseaCatalogSource(remoteCatalogDataSource = remoteSource)
        val variants = source.fetchCatalog { println("[USEA] $it") }

        println("USEA: fetched ${variants.size} variants")
        assertTrue(variants.isNotEmpty(), "USEA catalog should not be empty")

        // Verify seller tag
        assertTrue(variants.all { it.seller == Seller.USEA }, "All USEA variants should have USEA seller tag")

        // Match against real catalog
        val catalog = Catalog(variants)
        val entries = matchingUseCase.parseDeck(testDeckText, includeSideboard = true, includeCommanders = true)

        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)
        assertEquals(entries.size, matches.size)

        val matchedCount = matches.count { it.status == MatchStatus.AUTO_MATCHED }
        println("USEA: matched $matchedCount / ${entries.size} cards")
        assertTrue(matchedCount > 0, "Should match at least some cards against USEA catalog")

        val counts = matchingUseCase.calculateMatchCounts(matches)
        assertEquals(matchedCount, counts.matched)
    }

    @Test
    fun scryfall_searchAndMatch() = runTest(timeout = 2.minutes) {
        val source = ScryfallPricingSource(ScryfallApi)

        // Scryfall's fetchCatalog returns empty (on-demand only), so use search
        val boltVariants = source.search("Lightning Bolt")
        println("Scryfall: found ${boltVariants.size} variants for Lightning Bolt")
        assertTrue(boltVariants.isNotEmpty(), "Scryfall should find Lightning Bolt")
        assertTrue(boltVariants.all { it.seller == Seller.TCGPLAYER })

        val solRingVariants = source.search("Sol Ring")
        println("Scryfall: found ${solRingVariants.size} variants for Sol Ring")
        assertTrue(solRingVariants.isNotEmpty(), "Scryfall should find Sol Ring")

        // Build a catalog from searched cards and match
        val allVariants = boltVariants + solRingVariants
        val catalog = Catalog(allVariants)

        val entries = matchingUseCase.parseDeck(
            "4 Lightning Bolt\n1 Sol Ring",
            includeSideboard = true,
            includeCommanders = true,
        )
        val matches = matchingUseCase.matchEntries(entries, catalog, defaultMatchConfig)

        assertEquals(2, matches.size)
        // Both should be found in Scryfall data
        assertTrue(
            matches.all { it.status == MatchStatus.AUTO_MATCHED },
            "Both cards should match against Scryfall data"
        )

        val counts = matchingUseCase.calculateMatchCounts(matches)
        assertEquals(2, counts.matched)
        assertTrue(counts.totalPrice > 0, "Real cards should have real prices")
        println("Scryfall match total: ${counts.totalPrice} cents")

        ScryfallApi.close()
    }

    // -----------------------------------------------------------------------
    // Multi-seller matching with real data
    // -----------------------------------------------------------------------

    @Test
    fun multiSeller_realCatalogsMatchAndOptimize() = runTest(timeout = 3.minutes) {
        val entries = matchingUseCase.parseDeck(testDeckText, includeSideboard = true, includeCommanders = true)
        assertEquals(8, entries.size, "Should parse 8 unique card lines")

        // Fetch catalogs from all available sellers
        val catalogs = mutableMapOf<Seller, Catalog>()

        // BootlegMage
        try {
            val bmSource = BootlegMageCatalogSource()
            val bmVariants = bmSource.fetchCatalog { println("[BM] $it") }
            if (bmVariants.isNotEmpty()) {
                catalogs[Seller.BOOTLEG_MAGE] = Catalog(bmVariants)
                println("Loaded BootlegMage: ${bmVariants.size} variants")
            }
        } catch (e: Exception) {
            println("BootlegMage unavailable: ${e.message}")
        }

        // USEA
        try {
            val useaSource = UseaCatalogSource(KtorRemoteCatalogDataSource())
            val useaVariants = useaSource.fetchCatalog { println("[USEA] $it") }
            if (useaVariants.isNotEmpty()) {
                catalogs[Seller.USEA] = Catalog(useaVariants)
                println("Loaded USEA: ${useaVariants.size} variants")
            }
        } catch (e: Exception) {
            println("USEA unavailable: ${e.message}")
        }

        // Scryfall (search for each card individually)
        try {
            val scryfallSource = ScryfallPricingSource(ScryfallApi)
            val scryfallVariants = mutableListOf<model.CardVariant>()
            for (entry in entries) {
                try {
                    val results = scryfallSource.search(entry.cardName)
                    scryfallVariants.addAll(results)
                } catch (e: Exception) {
                    println("Scryfall search failed for ${entry.cardName}: ${e.message}")
                }
            }
            if (scryfallVariants.isNotEmpty()) {
                catalogs[Seller.TCGPLAYER] = Catalog(scryfallVariants)
                println("Loaded Scryfall/TCGPlayer: ${scryfallVariants.size} variants")
            }
        } catch (e: Exception) {
            println("Scryfall unavailable: ${e.message}")
        }

        assertTrue(catalogs.isNotEmpty(), "Should load at least one catalog")
        println("\nLoaded catalogs from ${catalogs.size} seller(s): ${catalogs.keys.joinToString()}")

        // Step 1: Single-seller match (using combined catalog like the app does)
        val allVariants = catalogs.values.flatMap { it.variants }
        val combinedCatalog = Catalog(allVariants)
        val singleMatches = matchingUseCase.matchEntries(entries, combinedCatalog, defaultMatchConfig)

        val singleCounts = matchingUseCase.calculateMatchCounts(singleMatches)
        println("\nSingle-seller matching:")
        println("  Matched: ${singleCounts.matched}")
        println("  Unmatched: ${singleCounts.unmatched}")
        println("  Ambiguous: ${singleCounts.ambiguous}")
        println("  Total price: ${singleCounts.totalPrice} cents")

        assertTrue(singleCounts.matched > 0, "Should match at least some cards across all catalogs")

        // Step 2: Multi-seller match
        val multiConfig = MultiCatalogMatcher.Config(
            variantPriority = listOf("Foil", "Holo", "Regular"),
            fuzzyEnabled = true,
            proxyFirst = true,
        )
        val multiMatches = matchingUseCase.matchEntriesMulti(entries, catalogs, multiConfig)

        assertEquals(entries.size, multiMatches.size, "Should have a multi-match for every entry")

        val multiMatchedCount = multiMatches.count { it.bestOption != null }
        println("\nMulti-seller matching:")
        println("  Matched: $multiMatchedCount / ${entries.size}")

        for (mm in multiMatches) {
            val best = mm.bestOption
            if (best != null) {
                println("  ${mm.deckEntry.cardName}: ${best.seller} @ ${best.priceCents}c (${mm.alternatives.size} alternatives)")
            } else {
                println("  ${mm.deckEntry.cardName}: NOT FOUND in any seller")
            }
        }

        assertTrue(multiMatchedCount > 0, "Should find at least some cards across multiple sellers")

        // Verify alternatives exist for cards in multiple catalogs
        val cardsWithAlternatives = multiMatches.count { it.alternatives.size > 1 }
        println("  Cards with alternatives from multiple sellers: $cardsWithAlternatives")

        // Step 3: Shopping optimization
        val plan = ShoppingOptimizer.optimize(multiMatches)

        println("\nShopping plan:")
        println("  Orders: ${plan.orders.size}")
        for (order in plan.orders) {
            println("  ${order.seller}: ${order.items.size} items, subtotal=${order.subtotalCents}c, " +
                "discount=${order.discountPercent}%, shipping=${order.shippingCents}c, total=${order.totalCents}c")
        }
        println("  Grand total: ${plan.totalPriceCents} cents")
        println("  Savings: ${plan.savingsVsSingleSeller} cents")

        if (multiMatchedCount > 0) {
            assertTrue(plan.orders.isNotEmpty(), "Should have at least one seller order")
            assertTrue(plan.totalPriceCents > 0, "Total should be positive when cards are matched")

            // Verify all matched cards appear in orders
            val orderedCardCount = plan.orders.sumOf { order -> order.items.sumOf { it.qty } }
            val expectedCardCount = multiMatches
                .filter { it.bestOption != null }
                .sumOf { it.deckEntry.qty }
            assertEquals(expectedCardCount, orderedCardCount,
                "All matched cards should appear in shopping plan")
        }

        ScryfallApi.close()
    }

    // -----------------------------------------------------------------------
    // Full wizard simulation with real data
    // -----------------------------------------------------------------------

    @Test
    fun fullWizard_realDataEndToEnd() = runTest(timeout = 3.minutes) {
        println("=== Full Wizard Pipeline Test (Real Data) ===\n")

        // === Step 1: Import ===
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            1 Sol Ring
            1 Mana Crypt
        """.trimIndent()

        // === Step 1→2: Parse ===
        val entries = matchingUseCase.parseDeck(deckText, includeSideboard = true, includeCommanders = true)
        assertEquals(4, entries.size, "Step 1: Should parse 4 cards")
        println("Step 1→2: Parsed ${entries.size} cards")
        entries.forEach { println("  ${it.qty}x ${it.cardName}") }

        // === Step 2→3: Load catalogs + match ===

        // Load USEA catalog (the primary single-seller catalog)
        println("\nStep 2→3: Loading USEA catalog...")
        val remoteSource = KtorRemoteCatalogDataSource()
        val useaSource = UseaCatalogSource(remoteCatalogDataSource = remoteSource)
        val useaVariants = useaSource.fetchCatalog { println("  [USEA] $it") }
        println("  USEA loaded: ${useaVariants.size} variants")

        if (useaVariants.isNotEmpty()) {
            // Single-seller match (what runMatch does)
            val useaCatalog = Catalog(useaVariants)
            val singleMatches = matchingUseCase.matchEntries(entries, useaCatalog, defaultMatchConfig)

            val singleCounts = matchingUseCase.calculateMatchCounts(singleMatches)
            println("\n  Single-seller results:")
            println("    Matched: ${singleCounts.matched} / ${entries.size}")
            println("    Total: ${singleCounts.totalPrice} cents")

            singleMatches.forEach { m ->
                val status = when (m.status) {
                    MatchStatus.AUTO_MATCHED -> "MATCHED"
                    MatchStatus.AMBIGUOUS -> "AMBIGUOUS (${m.candidates.size} candidates)"
                    MatchStatus.NOT_FOUND -> "NOT FOUND"
                    MatchStatus.FUZZY_RECHECK -> "FUZZY RECHECK"
                    else -> m.status.name
                }
                val price = m.selectedVariant?.let { "${it.priceInCents}c" } ?: "N/A"
                println("    ${m.deckEntry.cardName}: $status [$price]")
            }

            assertTrue(singleCounts.matched > 0, "Should match at least some cards from USEA")
        }

        // Load multi-seller catalogs (what loadAllCatalogs does)
        println("\nStep 2→3: Loading multi-seller catalogs...")
        val allCatalogs = mutableMapOf<Seller, Catalog>()

        if (useaVariants.isNotEmpty()) {
            allCatalogs[Seller.USEA] = Catalog(useaVariants)
        }

        try {
            val bmSource = BootlegMageCatalogSource()
            val bmVariants = bmSource.fetchCatalog { println("  [BM] $it") }
            if (bmVariants.isNotEmpty()) {
                allCatalogs[Seller.BOOTLEG_MAGE] = Catalog(bmVariants)
                println("  BootlegMage loaded: ${bmVariants.size} variants")
            }
        } catch (e: Exception) {
            println("  BootlegMage unavailable: ${e.message}")
        }

        assertTrue(allCatalogs.isNotEmpty(), "Should load at least one catalog")

        // Multi-seller match (what runMultiMatch does)
        val multiConfig = MultiCatalogMatcher.Config(proxyFirst = true)
        val multiMatches = matchingUseCase.matchEntriesMulti(entries, allCatalogs, multiConfig)

        println("\n  Multi-seller results:")
        multiMatches.forEach { mm ->
            val best = mm.bestOption
            if (best != null) {
                val altCount = mm.alternatives.size
                println("    ${mm.deckEntry.cardName}: ${best.seller} @ ${best.priceCents}c ($altCount alternatives)")
            } else {
                println("    ${mm.deckEntry.cardName}: NOT FOUND")
            }
        }

        // === Step 3→4: Optimize shopping plan ===
        println("\nStep 3→4: Optimizing shopping plan...")
        val plan = ShoppingOptimizer.optimize(multiMatches)

        println("  Shopping Plan:")
        plan.orders.forEach { order ->
            println("    ${order.seller.displayName}:")
            order.items.forEach { item ->
                println("      ${item.qty}x ${item.variant.nameOriginal} @ ${item.variant.priceInCents}c")
            }
            println("      Subtotal: ${order.subtotalCents}c, Discount: ${order.discountPercent}%, " +
                "Shipping: ${order.shippingCents}c, Total: ${order.totalCents}c")
        }
        println("  Grand Total: ${plan.totalPriceCents} cents")
        println("  Savings: ${plan.savingsVsSingleSeller} cents")

        val matchedMulti = multiMatches.count { it.bestOption != null }
        if (matchedMulti > 0) {
            assertTrue(plan.orders.isNotEmpty(), "Should produce a shopping plan")
            assertTrue(plan.totalPriceCents > 0, "Plan should have a positive total")
        }

        println("\n=== Pipeline Complete ===")
        ScryfallApi.close()
    }
}
