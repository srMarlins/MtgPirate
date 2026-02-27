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
    fun progressFraction_sellerBased() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 30,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 30),
                Seller.MANAPOOL to SellerSearchStatus(Seller.MANAPOOL, SearchState.SEARCHING),
            ),
            multiMatches = emptyList(),
            isComplete = false,
        )
        // 1 of 2 sellers done = 0.5
        assertEquals(0.5f, progress.progressFraction)
    }

    @Test
    fun progressFraction_allSellersDone() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 58,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 30),
                Seller.MANAPOOL to SellerSearchStatus(Seller.MANAPOOL, SearchState.DONE, 28),
            ),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertEquals(1f, progress.progressFraction)
    }

    @Test
    fun progressFraction_noSellers() {
        val progress = SearchProgress(
            totalCards = 0,
            cardsWithResults = 0,
            sellerStatuses = emptyMap(),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertEquals(1f, progress.progressFraction)
    }

    @Test
    fun progressFraction_errorCountsAsComplete() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 0,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.ERROR, message = "timeout"),
                Seller.MANAPOOL to SellerSearchStatus(Seller.MANAPOOL, SearchState.DONE, 28),
            ),
            multiMatches = emptyList(),
            isComplete = false,
        )
        // Both are completed (error + done) = 1.0
        assertEquals(1f, progress.progressFraction)
    }
}
