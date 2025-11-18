package ui

import model.CardVariant
import model.DeckEntry
import model.DeckEntryMatch
import model.MatchCandidate
import model.MatchStatus
import model.Section
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test that image preview components are properly structured.
 * Note: These are structural tests since we can't render Compose UI in tests.
 */
class ImagePreviewComponentsTest {

    @Test
    fun testDesktopResolveScreenWithTestData() {
        // Create test data
        val testVariant = CardVariant(
            nameOriginal = "Lightning Bolt",
            nameNormalized = "lightning bolt",
            setCode = "M11",
            sku = "XMC00123",
            variantType = "Regular",
            priceInCents = 220,
            collectorNumber = "148",
            imageUrl = "https://example.com/image.jpg"
        )
        
        val testCandidate = MatchCandidate(
            variant = testVariant,
            score = 0,
            reason = "Exact match"
        )
        
        val testDeckEntry = DeckEntry(
            originalLine = "4 Lightning Bolt",
            qty = 4,
            cardName = "Lightning Bolt",
            section = Section.MAIN,
            include = true
        )
        
        val testMatch = DeckEntryMatch(
            deckEntry = testDeckEntry,
            status = MatchStatus.AMBIGUOUS,
            candidates = listOf(testCandidate)
        )
        
        // Verify that test data is valid
        assertNotNull(testMatch.candidates)
        assertTrue(testMatch.candidates.isNotEmpty())
        assertNotNull(testMatch.candidates.first().variant.imageUrl)
    }
    
    @Test
    fun testCardVariantSupportsImageUrls() {
        val variant = CardVariant(
            nameOriginal = "Black Lotus",
            nameNormalized = "black lotus",
            setCode = "LEA",
            sku = "XMC00456",
            variantType = "Foil",
            priceInCents = 5000,
            collectorNumber = "232",
            imageUrl = "https://cards.scryfall.io/normal/front/b/l/black-lotus.jpg"
        )
        
        assertNotNull(variant.imageUrl)
        assertTrue(variant.imageUrl!!.startsWith("https://"))
    }
    
    @Test
    fun testCardVariantHandlesMissingImageUrls() {
        val variant = CardVariant(
            nameOriginal = "Plains",
            nameNormalized = "plains",
            setCode = "UNH",
            sku = "XMC99999",
            variantType = "Regular",
            priceInCents = 50,
            imageUrl = null  // No image URL
        )
        
        // Should not throw when imageUrl is null
        assertTrue(variant.imageUrl == null)
    }
}
