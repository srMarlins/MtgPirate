package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Platform-specific purchase manager wrapping RevenueCat.
 * Desktop uses REST API, iOS uses Swift SDK, Android uses Kotlin SDK.
 */
interface PurchaseManager {
    /** Check current entitlement status with RevenueCat. */
    suspend fun checkEntitlement(): ProStatus

    /** Trigger the purchase flow. Returns result of the attempt. */
    suspend fun purchase(): PurchaseResult

    /** Restore previous purchases. */
    suspend fun restorePurchases(): ProStatus
}
