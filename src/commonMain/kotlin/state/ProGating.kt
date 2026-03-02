package state

import model.ProStatus
import model.Seller

/**
 * Returns `true` when a seller-list update should be blocked (user is not Pro
 * and is trying to enable sellers beyond the free-tier Bootleg Mage default).
 *
 * Free-tier rules:
 * - Empty list (unchecking all): allowed
 * - Exactly one seller that is BOOTLEG_MAGE: allowed
 * - Anything else: blocked unless Pro
 */
fun shouldBlockSellerUpdate(sellers: List<String>, proStatus: ProStatus): Boolean {
    if (sellers.isEmpty()) return false
    val isOnlyBm = sellers.size == 1 && sellers.first() == Seller.BOOTLEG_MAGE.name
    if (isOnlyBm) return false
    return !proStatus.isPro
}
