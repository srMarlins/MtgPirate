package platform

/**
 * iOS actual: delegates to cached UIKit haptic generators via IosHapticFeedback.
 */
actual object HapticFeedback {

    actual enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }
    actual enum class NotificationType { SUCCESS, WARNING, ERROR }

    actual fun triggerImpact(style: ImpactStyle) {
        val iosStyle = when (style) {
            ImpactStyle.LIGHT -> IosHapticFeedback.ImpactStyle.LIGHT
            ImpactStyle.MEDIUM -> IosHapticFeedback.ImpactStyle.MEDIUM
            ImpactStyle.HEAVY -> IosHapticFeedback.ImpactStyle.HEAVY
        }
        IosHapticFeedback.triggerImpact(iosStyle)
    }

    actual fun triggerSelection() {
        IosHapticFeedback.triggerSelection()
    }

    actual fun triggerNotification(type: NotificationType) {
        val iosType = when (type) {
            NotificationType.SUCCESS -> IosHapticFeedback.NotificationType.SUCCESS
            NotificationType.WARNING -> IosHapticFeedback.NotificationType.WARNING
            NotificationType.ERROR -> IosHapticFeedback.NotificationType.ERROR
        }
        IosHapticFeedback.triggerNotification(iosType)
    }

    actual fun prepareImpact(style: ImpactStyle) {
        val iosStyle = when (style) {
            ImpactStyle.LIGHT -> IosHapticFeedback.ImpactStyle.LIGHT
            ImpactStyle.MEDIUM -> IosHapticFeedback.ImpactStyle.MEDIUM
            ImpactStyle.HEAVY -> IosHapticFeedback.ImpactStyle.HEAVY
        }
        IosHapticFeedback.prepareImpact(iosStyle)
    }
}
