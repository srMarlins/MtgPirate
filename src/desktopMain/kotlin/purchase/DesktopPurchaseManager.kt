package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Desktop purchase manager using RevenueCat REST API + Stripe Checkout.
 * TODO: Implement REST API calls after RevenueCat account setup.
 *
 * Supports a debug override: launch with `./gradlew run -PdebugPro=true`
 * to unlock Pro features without a real purchase flow.
 */
class DesktopPurchaseManager : PurchaseManager {

    private val debugPro: Boolean = System.getProperty("debugPro")?.toBoolean() ?: false

    override suspend fun checkEntitlement(): ProStatus {
        return if (debugPro) ProStatus.Pro else ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        return if (debugPro) PurchaseResult.SUCCESS else PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        return if (debugPro) ProStatus.Pro else ProStatus.Free
    }
}
