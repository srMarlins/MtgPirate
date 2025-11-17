package platform

/**
 * Desktop stub implementation of LiveActivityService.
 * Live Activities are only supported on iOS, so this provides no-op implementations.
 */
actual class LiveActivityService {
    
    actual fun isSupported(): Boolean = false
    
    actual fun startActivity(sessionId: String, totalCards: Int) {
        // No-op on desktop
    }
    
    actual fun updateActivity(
        phase: String,
        currentCardName: String?,
        currentIndex: Int,
        totalCards: Int,
        ambiguousCount: Int,
        totalPrice: Double
    ) {
        // No-op on desktop
    }
    
    actual fun updatePhase(phase: String) {
        // No-op on desktop
    }
    
    actual fun updateProgress(currentIndex: Int, cardName: String?) {
        // No-op on desktop
    }
    
    actual fun updateAmbiguousCount(count: Int) {
        // No-op on desktop
    }
    
    actual fun updatePrice(price: Double) {
        // No-op on desktop
    }
    
    actual fun completeActivity(success: Boolean, finalMessage: String?) {
        // No-op on desktop
    }
    
    actual fun endActivity() {
        // No-op on desktop
    }
    
    actual fun isActivityRunning(): Boolean = false
    
    actual fun startParsing(totalCards: Int) {
        // No-op on desktop
    }
    
    actual fun startMatching() {
        // No-op on desktop
    }
    
    actual fun startResolving(ambiguousCount: Int) {
        // No-op on desktop
    }
    
    actual fun startExporting() {
        // No-op on desktop
    }
}
