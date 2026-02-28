package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Desktop purchase manager using RevenueCat REST API + Stripe Checkout.
 * TODO: Implement REST API calls after RevenueCat account setup.
 */
class DesktopPurchaseManager : PurchaseManager {

    override suspend fun checkEntitlement(): ProStatus {
        // Stub: will call RevenueCat REST API
        return ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        // Stub: will open Stripe Checkout in browser
        return PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        // Stub: will call RevenueCat REST API
        return ProStatus.Free
    }
}
