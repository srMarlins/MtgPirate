package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Android purchase manager wrapping RevenueCat Kotlin SDK.
 * TODO: Implement after adding RevenueCat Android SDK dependency.
 */
class AndroidPurchaseManager : PurchaseManager {

    override suspend fun checkEntitlement(): ProStatus {
        return ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        return PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        return ProStatus.Free
    }
}
