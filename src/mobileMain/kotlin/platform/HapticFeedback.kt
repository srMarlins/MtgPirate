package platform

/**
 * Cross-platform mobile haptic feedback abstraction.
 * Provides tactile feedback for user interactions on iOS and Android.
 */
expect object HapticFeedback {

    enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }

    enum class NotificationType { SUCCESS, WARNING, ERROR }

    fun triggerImpact(style: ImpactStyle = ImpactStyle.MEDIUM)
    fun triggerSelection()
    fun triggerNotification(type: NotificationType)
    fun prepareImpact(style: ImpactStyle = ImpactStyle.MEDIUM)
}
