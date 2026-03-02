package state

import model.ProStatus
import model.Seller

/**
 * Returns `true` when a seller-list update should be blocked (user is not Pro
 * and is trying to enable sellers beyond the free-tier USEA-only default).
 *
 * Free-tier rules:
 * - Empty list (unchecking all): allowed
 * - Exactly one seller that is USEA: allowed
 * - Anything else: blocked unless Pro
 */
fun shouldBlockSellerUpdate(sellers: List<String>, proStatus: ProStatus): Boolean {
    if (sellers.isEmpty()) return false
    val isOnlyUsea = sellers.size == 1 && sellers.first() == Seller.USEA.name
    if (isOnlyUsea) return false
    return !proStatus.isPro
}
