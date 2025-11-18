package platform

/**
 * Platform-agnostic interface for managing Live Activities.
 * 
 * On iOS 16.2+, this integrates with ActivityKit to show progress in the Dynamic Island.
 * On other platforms, this provides no-op implementations.
 */
expect class LiveActivityService() {
    
    /**
     * Check if Live Activities are supported on this platform/device.
     */
    fun isSupported(): Boolean
    
    /**
     * Start a new Live Activity for card matching session.
     */
    fun startActivity(sessionId: String, totalCards: Int)
    
    /**
     * Update the Live Activity with full state.
     */
    fun updateActivity(state: LiveActivityState)
    
    /**
     * Update only the current phase.
     */
    fun updatePhase(phase: String)
    
    /**
     * Update progress with current card being processed.
     */
    fun updateProgress(currentIndex: Int, cardName: String?)
    
    /**
     * Update the count of ambiguous cards needing resolution.
     */
    fun updateAmbiguousCount(count: Int)
    
    /**
     * Update the total price.
     */
    fun updatePrice(price: Double)
    
    /**
     * Complete the Live Activity with final state.
     */
    fun completeActivity(success: Boolean, finalMessage: String?)
    
    /**
     * End/dismiss the Live Activity immediately.
     */
    fun endActivity()
    
    /**
     * Check if a Live Activity is currently running.
     */
    fun isActivityRunning(): Boolean
    
    // Convenience methods for common phase transitions
    fun startParsing(totalCards: Int)
    fun startMatching()
    fun startResolving(ambiguousCount: Int)
    fun startExporting()
}

/**
 * State data for Live Activity updates.
 * Groups related parameters to avoid long parameter lists.
 */
data class LiveActivityState(
    val phase: String,
    val currentCardName: String? = null,
    val currentIndex: Int,
    val totalCards: Int,
    val ambiguousCount: Int = 0,
    val totalPrice: Double = 0.0
)
