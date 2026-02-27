package state

import model.Seller
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchProgressTest {

    @Test
    fun isSearching_trueWhenNotComplete() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 10,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 10),
                Seller.MANAPOOL to SellerSearchStatus(Seller.MANAPOOL, SearchState.SEARCHING),
            ),
            multiMatches = emptyList(),
            isComplete = false,
        )
        assertTrue(progress.isSearching)
        assertFalse(progress.isComplete)
    }

    @Test
    fun isSearching_falseWhenComplete() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 58,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 58),
            ),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertFalse(progress.isSearching)
    }

    @Test
    fun progressFraction_calculatesCorrectly() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 30,
            sellerStatuses = emptyMap(),
            multiMatches = emptyList(),
            isComplete = false,
        )
        assertEquals(0.5f, progress.progressFraction)
    }

    @Test
    fun progressFraction_zeroTotalCards() {
        val progress = SearchProgress(
            totalCards = 0,
            cardsWithResults = 0,
            sellerStatuses = emptyMap(),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertEquals(1f, progress.progressFraction)
    }
}
