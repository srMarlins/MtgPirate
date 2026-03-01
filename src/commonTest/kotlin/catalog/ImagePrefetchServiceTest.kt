package catalog

import model.CardVariant
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImagePrefetchServiceTest {

    private fun variant(
        name: String,
        setCode: String = "M21",
        collectorNumber: String? = null,
        sku: String = "SKU-$name",
        seller: Seller = Seller.USEA,
    ) = CardVariant(
        nameOriginal = name,
        nameNormalized = name.lowercase(),
        setCode = setCode,
        sku = sku,
        variantType = VariantType.REGULAR,
        priceInCents = 100,
        collectorNumber = collectorNumber,
        seller = seller,
    )

    @Test
    fun buildIdentifiers_exactMatch_whenCollectorNumberPresent() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "M11", collectorNumber = "149"),
        )
        val identifiers = ImagePrefetchService.buildExactIdentifiers(variants)
        assertEquals(1, identifiers.size)
        assertEquals("m11", identifiers[0]["set"])
        assertEquals("149", identifiers[0]["collector_number"])
    }

    @Test
    fun buildIdentifiers_skipsVariants_withoutCollectorNumber() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "M11", collectorNumber = null),
        )
        val identifiers = ImagePrefetchService.buildExactIdentifiers(variants)
        assertTrue(identifiers.isEmpty())
    }

    @Test
    fun buildNameSetIdentifiers_usesNameAndSet() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "M11"),
        )
        val identifiers = ImagePrefetchService.buildNameSetIdentifiers(variants)
        assertEquals(1, identifiers.size)
        assertEquals("Lightning Bolt", identifiers[0]["name"])
        assertEquals("m11", identifiers[0]["set"])
    }

    @Test
    fun buildNameOnlyIdentifiers_usesNameOnly() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "UNK"),
        )
        val identifiers = ImagePrefetchService.buildNameOnlyIdentifiers(variants)
        assertEquals(1, identifiers.size)
        assertEquals("Lightning Bolt", identifiers[0]["name"])
        assertTrue("set" !in identifiers[0])
    }

    @Test
    fun matchBatchResults_matchesBySetAndCollectorNumber() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "M11", collectorNumber = "149"),
        )
        val scryfallCards = listOf(
            ScryfallApi.ScryfallCard(
                id = "abc",
                name = "Lightning Bolt",
                set = "m11",
                collectorNumber = "149",
                imageUris = ScryfallApi.ImageUris(
                    small = "https://small.jpg",
                    normal = "https://normal.jpg"
                )
            )
        )
        val matched = ImagePrefetchService.matchBatchResults(variants, scryfallCards)
        assertEquals(1, matched.size)
        assertEquals("https://small.jpg", matched.values.first().small)
        assertEquals("https://normal.jpg", matched.values.first().normal)
    }

    @Test
    fun matchBatchResults_matchesByNameFallback() {
        val variants = listOf(
            variant("Lightning Bolt", setCode = "M11", collectorNumber = null),
        )
        val scryfallCards = listOf(
            ScryfallApi.ScryfallCard(
                id = "abc",
                name = "Lightning Bolt",
                set = "m11",
                collectorNumber = "149",
                imageUris = ScryfallApi.ImageUris(
                    small = "https://small.jpg",
                    normal = "https://normal.jpg"
                )
            )
        )
        val matched = ImagePrefetchService.matchBatchResults(variants, scryfallCards)
        assertEquals(1, matched.size)
    }
}
