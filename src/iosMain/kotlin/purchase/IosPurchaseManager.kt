package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * iOS purchase manager wrapping RevenueCat Swift SDK via Kotlin/Native interop.
 * TODO: Implement after adding RevenueCat iOS SDK dependency.
 */
class IosPurchaseManager : PurchaseManager {

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
