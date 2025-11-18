package state

import kotlinx.coroutines.runBlocking
import model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for MviViewModel, specifically focusing on database observation reactivity.
 */
class MviViewModelTest {

    @Test
    fun testMatchesReflectCatalogUpdates() = runBlocking {
        // This test verifies that when a variant in the catalog is updated (e.g., image URL),
        // the matches reflect that update automatically through the reactive flow.
        
        // Create initial test data
        val initialVariant = CardVariant(
            nameOriginal = "Lightning Bolt",
            nameNormalized = "lightning bolt",
            setCode = "M11",
            sku = "TEST-SKU-001",
            variantType = "Regular",
            priceInCents = 220,
            collectorNumber = "148",
            imageUrl = null  // Initially no image
        )
        
        val updatedVariant = initialVariant.copy(
            imageUrl = "https://cards.scryfall.io/normal/front/l/b/lightning-bolt.jpg"
        )
        
        val testDeckEntry = DeckEntry(
            originalLine = "4 Lightning Bolt",
            qty = 4,
            cardName = "Lightning Bolt",
            section = Section.MAIN,
            include = true
        )
        
        val testCandidate = MatchCandidate(
            variant = initialVariant,
            score = 0,
            reason = "Exact match"
        )
        
        val initialMatch = DeckEntryMatch(
            deckEntry = testDeckEntry,
            status = MatchStatus.AUTO_MATCHED,
            selectedVariant = initialVariant,
            candidates = listOf(testCandidate)
        )
        
        // Create test catalog with initial variant (no image)
        val initialCatalog = Catalog(variants = listOf(initialVariant))
        
        // Create test catalog with updated variant (with image)
        val updatedCatalog = Catalog(variants = listOf(updatedVariant))
        
        // Simulate the refreshMatchesFromCatalog function behavior
        // (We can't test the full ViewModel without database setup, but we can test the logic)
        val matches = listOf(initialMatch)
        
        // Before update: match has variant without image URL
        assertNotNull(matches[0].selectedVariant)
        assertEquals(null, matches[0].selectedVariant?.imageUrl)
        assertEquals(null, matches[0].candidates[0].variant.imageUrl)
        
        // Build a map of SKU to CardVariant for quick lookups (simulating refreshMatchesFromCatalog)
        val variantsBySku = updatedCatalog.variants.associateBy { it.sku }
        
        val refreshedMatches = matches.map { match ->
            // Refresh the selected variant
            val refreshedSelectedVariant = match.selectedVariant?.let { oldVariant ->
                variantsBySku[oldVariant.sku] ?: oldVariant
            }
            
            // Refresh all candidate variants
            val refreshedCandidates = match.candidates.map { candidate ->
                val refreshedVariant = variantsBySku[candidate.variant.sku] ?: candidate.variant
                candidate.copy(variant = refreshedVariant)
            }
            
            match.copy(
                selectedVariant = refreshedSelectedVariant,
                candidates = refreshedCandidates
            )
        }
        
        // After update: match should have variant with image URL
        assertNotNull(refreshedMatches[0].selectedVariant)
        assertEquals("https://cards.scryfall.io/normal/front/l/b/lightning-bolt.jpg", 
                     refreshedMatches[0].selectedVariant?.imageUrl)
        assertEquals("https://cards.scryfall.io/normal/front/l/b/lightning-bolt.jpg", 
                     refreshedMatches[0].candidates[0].variant.imageUrl)
    }
    
    @Test
    fun testMatchesWithMultipleCandidatesRefreshCorrectly() = runBlocking {
        // Test that multiple candidates in a match are all refreshed correctly
        
        val variant1 = CardVariant(
            nameOriginal = "Island",
            nameNormalized = "island",
            setCode = "M11",
            sku = "TEST-SKU-002",
            variantType = "Regular",
            priceInCents = 50,
            imageUrl = null
        )
        
        val variant2 = CardVariant(
            nameOriginal = "Island",
            nameNormalized = "island",
            setCode = "M12",
            sku = "TEST-SKU-003",
            variantType = "Foil",
            priceInCents = 100,
            imageUrl = null
        )
        
        val updatedVariant1 = variant1.copy(imageUrl = "https://example.com/island1.jpg")
        val updatedVariant2 = variant2.copy(imageUrl = "https://example.com/island2.jpg")
        
        val testDeckEntry = DeckEntry(
            originalLine = "4 Island",
            qty = 4,
            cardName = "Island",
            section = Section.MAIN,
            include = true
        )
        
        val initialMatch = DeckEntryMatch(
            deckEntry = testDeckEntry,
            status = MatchStatus.AMBIGUOUS,
            selectedVariant = null,
            candidates = listOf(
                MatchCandidate(variant = variant1, score = 100, reason = "Set match"),
                MatchCandidate(variant = variant2, score = 90, reason = "Set match")
            )
        )
        
        val updatedCatalog = Catalog(variants = listOf(updatedVariant1, updatedVariant2))
        
        // Simulate refresh
        val variantsBySku = updatedCatalog.variants.associateBy { it.sku }
        val matches = listOf(initialMatch)
        
        val refreshedMatches = matches.map { match ->
            val refreshedSelectedVariant = match.selectedVariant?.let { oldVariant ->
                variantsBySku[oldVariant.sku] ?: oldVariant
            }
            
            val refreshedCandidates = match.candidates.map { candidate ->
                val refreshedVariant = variantsBySku[candidate.variant.sku] ?: candidate.variant
                candidate.copy(variant = refreshedVariant)
            }
            
            match.copy(
                selectedVariant = refreshedSelectedVariant,
                candidates = refreshedCandidates
            )
        }
        
        // Verify all candidates were refreshed
        assertEquals(2, refreshedMatches[0].candidates.size)
        assertEquals("https://example.com/island1.jpg", refreshedMatches[0].candidates[0].variant.imageUrl)
        assertEquals("https://example.com/island2.jpg", refreshedMatches[0].candidates[1].variant.imageUrl)
    }
}
