package state

import model.MultiMatch
import model.Seller

/** State of a single supplier's search. */
enum class SearchState { PENDING, SEARCHING, DONE, ERROR }

/** Progress for a single supplier. */
data class SellerSearchStatus(
    val seller: Seller,
    val state: SearchState,
    val cardsFound: Int = 0,
    val message: String? = null,
)

/** Overall search progress emitted by DeckSearchUseCase. */
data class SearchProgress(
    val totalCards: Int,
    val cardsWithResults: Int,
    val sellerStatuses: Map<Seller, SellerSearchStatus>,
    val multiMatches: List<MultiMatch>,
    val isComplete: Boolean,
) {
    val isSearching: Boolean get() = !isComplete

    /** Seller-based progress: fraction of sellers that have completed (DONE or ERROR). */
    val progressFraction: Float
        get() {
            if (sellerStatuses.isEmpty()) return 1f
            val completed = sellerStatuses.values.count {
                it.state == SearchState.DONE || it.state == SearchState.ERROR
            }
            return completed.toFloat() / sellerStatuses.size
        }
}
