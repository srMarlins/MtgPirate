package match

import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.MatchStatus
import model.Section
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatcherTest {

    private val defaultConfig = Matcher.MatchConfig(
        variantPriority = listOf("Regular"),
        setPriority = emptyList(),
        fuzzyEnabled = true
    )

    private fun variant(name: String, set: String = "TST", sku: String = "sku-$name"): CardVariant =
        CardVariant(
            nameOriginal = name,
            nameNormalized = NameNormalizer.normalize(name),
            setCode = set,
            sku = sku,
            variantType = VariantType.REGULAR,
            priceInCents = 100
        )

    private fun entry(name: String, id: String = "e-$name"): DeckEntry =
        DeckEntry(
            id = id,
            originalLine = "1 $name",
            qty = 1,
            cardName = name,
            section = Section.MAIN,
            include = true
        )

    @Test
    fun alternateNameMatchesCanonicalCard() {
        val catalog = Catalog(listOf(variant("Seething Song")))
        val results = Matcher.matchAll(listOf(entry("Calliope's Song")), catalog, defaultConfig)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(MatchStatus.AUTO_MATCHED, result.status)
        assertEquals("Seething Song", result.selectedVariant?.nameOriginal)
        assertTrue(result.notes.contains("alternate name"), "Expected notes to mention alternate name")
    }

    @Test
    fun alternateNameWithAccentMatchesCanonicalCard() {
        val catalog = Catalog(listOf(variant("Sylvan Library")))
        val results = Matcher.matchAll(
            listOf(entry("La abundancia de Yucah\u00fa")),
            catalog,
            defaultConfig
        )

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(MatchStatus.AUTO_MATCHED, result.status)
        assertEquals("Sylvan Library", result.selectedVariant?.nameOriginal)
    }

    @Test
    fun fuzzyBlocklistPreventsElectrodominanceMatchingNecrodominance() {
        val catalog = Catalog(listOf(variant("Necrodominance")))
        val results = Matcher.matchAll(listOf(entry("Electrodominance")), catalog, defaultConfig)

        assertEquals(1, results.size)
        val result = results.first()
        // Should NOT match Necrodominance due to blocklist
        assertEquals(MatchStatus.NOT_FOUND, result.status)
    }

    @Test
    fun fuzzyBlocklistPreventsNecrodominanceMatchingElectrodominance() {
        val catalog = Catalog(listOf(variant("Electrodominance")))
        val results = Matcher.matchAll(listOf(entry("Necrodominance")), catalog, defaultConfig)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(MatchStatus.NOT_FOUND, result.status)
    }

    @Test
    fun exactMatchStillWorksWithBlocklistedCard() {
        // The blocklist should only affect fuzzy matching, not exact matches
        val catalog = Catalog(listOf(variant("Necrodominance")))
        val results = Matcher.matchAll(listOf(entry("Necrodominance")), catalog, defaultConfig)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(MatchStatus.AUTO_MATCHED, result.status)
        assertEquals("Necrodominance", result.selectedVariant?.nameOriginal)
    }
}
