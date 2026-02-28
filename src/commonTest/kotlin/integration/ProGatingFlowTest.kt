package integration

import model.ProFeature
import model.ProStatus
import model.PurchaseResult
import model.Seller
import purchase.PurchaseManager
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

    @Test
    fun freeUser_cannotEnableMultipleSellers() {
        val status = ProStatus.Free
        val newSellers = listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name)
        val isOnlyUsea = newSellers.size == 1 && newSellers.first() == Seller.USEA.name
        assertFalse(isOnlyUsea || status.isPro)
    }

    @Test
    fun freeUser_canEnableUseaOnly() {
        val status = ProStatus.Free
        val newSellers = listOf(Seller.USEA.name)
        val isOnlyUsea = newSellers.size == 1 && newSellers.first() == Seller.USEA.name
        assertTrue(isOnlyUsea || status.isPro)
    }

    @Test
    fun proUser_canEnableMultipleSellers() {
        val status = ProStatus.Pro
        val newSellers = listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name)
        val isOnlyUsea = newSellers.size == 1 && newSellers.first() == Seller.USEA.name
        assertTrue(isOnlyUsea || status.isPro)
    }

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

    @Test
    fun proStatus_isPro_correctForAllStates() {
        assertFalse(ProStatus.Free.isPro)
        assertTrue(ProStatus.Pro.isPro)
        assertFalse(ProStatus.Loading.isPro)
    }

    @Test
    fun proFeature_allFeaturesPresent() {
        val expected = setOf(
            ProFeature.MULTI_SELLER,
            ProFeature.SHOPPING_OPTIMIZER,
            ProFeature.SELLER_OVERRIDE,
            ProFeature.IMPORT_HISTORY,
            ProFeature.THEME_CUSTOMIZATION,
        )
        assertEquals(expected, ProFeature.entries.toSet())
    }
}
