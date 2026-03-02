package integration

import model.ProStatus
import model.PurchaseResult
import model.Seller
import purchase.PurchaseManager
import state.shouldBlockSellerUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Mock PurchaseManager for testing. */
class MockPurchaseManager(
    var entitlement: ProStatus = ProStatus.Free,
    var purchaseResult: PurchaseResult = PurchaseResult.SUCCESS,
) : PurchaseManager {
    override suspend fun checkEntitlement(): ProStatus = entitlement
    override suspend fun purchase(): PurchaseResult {
        if (purchaseResult == PurchaseResult.SUCCESS) {
            entitlement = ProStatus.Pro
        }
        return purchaseResult
    }
    override suspend fun restorePurchases(): ProStatus = entitlement
}

class ProGatingFlowTest {

    // -- shouldBlockSellerUpdate tests (production function) --

    @Test
    fun freeUser_cannotEnableMultipleSellers() {
        assertTrue(
            shouldBlockSellerUpdate(
                listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name),
                ProStatus.Free,
            )
        )
    }

    @Test
    fun freeUser_canEnableUseaOnly() {
        assertFalse(
            shouldBlockSellerUpdate(listOf(Seller.USEA.name), ProStatus.Free)
        )
    }

    @Test
    fun proUser_canEnableMultipleSellers() {
        assertFalse(
            shouldBlockSellerUpdate(
                listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name),
                ProStatus.Pro,
            )
        )
    }

    @Test
    fun emptySellerList_isAllowed() {
        assertFalse(shouldBlockSellerUpdate(emptyList(), ProStatus.Free))
    }

    @Test
    fun singleNonUseaSeller_blockedForFree() {
        assertTrue(
            shouldBlockSellerUpdate(listOf(Seller.BOOTLEG_MAGE.name), ProStatus.Free)
        )
    }

    @Test
    fun allSellers_allowedForPro() {
        val allSellers = Seller.entries.map { it.name }
        assertFalse(shouldBlockSellerUpdate(allSellers, ProStatus.Pro))
    }

    @Test
    fun loadingStatus_blocksMultipleSellers() {
        assertTrue(
            shouldBlockSellerUpdate(
                listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name),
                ProStatus.Loading,
            )
        )
    }

    // -- MockPurchaseManager tests --

    @Test
    fun mockPurchaseManager_purchaseSuccess_grantsPro() = runTest {
        val manager = MockPurchaseManager()
        assertEquals(ProStatus.Free, manager.entitlement)
        val result = manager.purchase()
        assertEquals(PurchaseResult.SUCCESS, result)
        assertEquals(ProStatus.Pro, manager.entitlement)
    }

    @Test
    fun mockPurchaseManager_purchaseCancelled_staysFree() = runTest {
        val manager = MockPurchaseManager(purchaseResult = PurchaseResult.CANCELLED)
        val result = manager.purchase()
        assertEquals(PurchaseResult.CANCELLED, result)
        assertEquals(ProStatus.Free, manager.entitlement)
    }

    @Test
    fun mockPurchaseManager_restore_returnsCachedStatus() = runTest {
        val manager = MockPurchaseManager(entitlement = ProStatus.Pro)
        val status = manager.restorePurchases()
        assertEquals(ProStatus.Pro, status)
    }

    // -- ProStatus tests --

    @Test
    fun proStatus_isPro_correctForAllStates() {
        assertFalse(ProStatus.Free.isPro)
        assertTrue(ProStatus.Pro.isPro)
        assertFalse(ProStatus.Loading.isPro)
    }
}
