package match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.MatchStatus
import model.Section
import model.VariantType

class MatcherTest {

    private val defaultConfig = Matcher.MatchConfig(
        variantPriority = listOf("Regular"),
        setPriority = emptyList(),
        fuzzyEnabled = true
    )

    private fun variant(name: String, set: String = "SET", sku: String = "SKU-1"): CardVariant {
        return CardVariant(
            nameOriginal = name,
            nameNormalized = NameNormalizer.normalize(name),
            setCode = set,
            sku = sku,
            variantType = VariantType.REGULAR,
            priceInCents = 100
        )
    }

    private fun entry(name: String, id: String = "1"): DeckEntry {
        return DeckEntry(
            id = id,
            originalLine = "1 $name",
            qty = 1,
            cardName = name,
            section = Section.MAIN,
            include = true
        )
    }

    @Test
    fun exactMatchReturnsAutoMatched() {
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val results = Matcher.matchAll(listOf(entry("Lightning Bolt")), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.AUTO_MATCHED, results[0].status)
    }

    @Test
    fun notFoundWhenNoMatchExistsAndFuzzyDisabled() {
        val catalog = Catalog(listOf(variant("Counterspell")))
        val config = defaultConfig.copy(fuzzyEnabled = false)
        val results = Matcher.matchAll(listOf(entry("Totally Different Card Name")), catalog, config)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.NOT_FOUND, results[0].status)
    }

    @Test
    fun standardFuzzyMatchFindsCloseTypo() {
        // "Lghtning Bolt" vs "Lightning Bolt" - Levenshtein distance 1 (missing 'i')
        // Standard threshold for length <= 15 is 2, so distance 1 is within range
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val results = Matcher.matchAll(listOf(entry("Lghtning Bolt")), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.AMBIGUOUS, results[0].status)
    }

    @Test
    fun recheckMatchesModeratelyMisspelledName() {
        // "Litening Bolt" vs "Lightning Bolt"
        // Normalized: "litening bolt" vs "lightning bolt"
        // Levenshtein distance = 3 (verified by manual calculation)
        // Standard threshold for length 13 (<= 15) = 2, so distance 3 is NOT within standard range
        // Relaxed threshold = 2 + 2 = 4, so distance 3 IS within relaxed range
        // Expected: FUZZY_RECHECK
        val catalog = Catalog(listOf(variant("Lightning Bolt")))

        // First verify this does NOT match with fuzzy disabled
        val configNoFuzzy = defaultConfig.copy(fuzzyEnabled = false)
        val noFuzzyResults = Matcher.matchAll(listOf(entry("Litening Bolt")), catalog, configNoFuzzy)
        assertEquals(MatchStatus.NOT_FOUND, noFuzzyResults[0].status)

        // With fuzzy enabled, standard pass misses it (distance 3 > threshold 2)
        // but recheck pass catches it (distance 3 <= relaxed threshold 4)
        val results = Matcher.matchAll(listOf(entry("Litening Bolt")), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.FUZZY_RECHECK, results[0].status)
        assertTrue(results[0].candidates.isNotEmpty())
        assertEquals("Lightning Bolt", results[0].candidates[0].variant.nameOriginal)
        assertTrue(results[0].candidates[0].reason.startsWith("recheck:"))
    }

    @Test
    fun recheckDoesNotRunWhenFuzzyDisabled() {
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val config = defaultConfig.copy(fuzzyEnabled = false)
        // Distance 3 from "Lightning Bolt" - would be caught by recheck but fuzzy is off
        val results = Matcher.matchAll(listOf(entry("Litening Bolt")), catalog, config)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.NOT_FOUND, results[0].status)
    }

    @Test
    fun recheckDoesNotAffectAlreadyMatchedEntries() {
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val results = Matcher.matchAll(listOf(entry("Lightning Bolt")), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.AUTO_MATCHED, results[0].status)
    }

    @Test
    fun recheckReturnsNotFoundWhenStillTooFar() {
        // "Ltenin Bolt" vs "Lightning Bolt"
        // Normalized: "ltenin bolt" vs "lightning bolt"
        // Levenshtein distance = 5 (verified), relaxed threshold = 4
        // Distance 5 > 4, so still NOT_FOUND even after recheck
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val results = Matcher.matchAll(listOf(entry("Ltenin Bolt")), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.NOT_FOUND, results[0].status)
    }

    @Test
    fun excludedEntryReturnsUnresolved() {
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val excluded = DeckEntry(
            id = "1",
            originalLine = "1 Lightning Bolt",
            qty = 1,
            cardName = "Lightning Bolt",
            section = Section.MAIN,
            include = false
        )
        val results = Matcher.matchAll(listOf(excluded), catalog, defaultConfig)
        assertEquals(1, results.size)
        assertEquals(MatchStatus.UNRESOLVED, results[0].status)
    }

    @Test
    fun recheckOnlyIncludesCandidatesBeyondStandardThreshold() {
        // Verify that recheck candidates have distances strictly above the standard threshold
        // "Litening Bolt" has distance 3 from "Lightning Bolt", standard threshold is 2
        val catalog = Catalog(listOf(variant("Lightning Bolt")))
        val results = Matcher.matchAll(listOf(entry("Litening Bolt")), catalog, defaultConfig)
        assertEquals(MatchStatus.FUZZY_RECHECK, results[0].status)
        // All candidates should have score > standard threshold (2)
        for (candidate in results[0].candidates) {
            assertTrue(candidate.score > 2, "Recheck candidate score ${candidate.score} should be > 2")
        }
    }
}
