package platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS Haptic Feedback utilities for providing tactile feedback during user interactions.
 * Uses UIKit's feedback generators for native iOS haptic patterns.
 *
 * Generators are cached as object-level properties per Apple guidelines:
 * recreating them on every call adds overhead and delays haptic response.
 */
object IosHapticFeedback {

    // Cached feedback generators (Apple recommends reusing these)
    private val lightImpactGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val mediumImpactGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavyImpactGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val selectionGenerator = UISelectionFeedbackGenerator()
    private val notificationGenerator = UINotificationFeedbackGenerator()

    /**
     * Impact feedback styles matching iOS native patterns.
     */
    enum class ImpactStyle {
        LIGHT,   // Subtle feedback for minor interactions
        MEDIUM,  // Standard feedback for most interactions
        HEAVY    // Strong feedback for significant actions
    }

    /**
     * Notification feedback types for success/error states.
     */
    enum class NotificationType {
        SUCCESS,
        WARNING,
        ERROR
    }

    private fun generatorForStyle(style: ImpactStyle): UIImpactFeedbackGenerator = when (style) {
        ImpactStyle.LIGHT -> lightImpactGenerator
        ImpactStyle.MEDIUM -> mediumImpactGenerator
        ImpactStyle.HEAVY -> heavyImpactGenerator
    }

    /**
     * Trigger impact haptic feedback.
     * Use for physical interactions like button presses, drag starts, or collisions.
     *
     * @param style The intensity of the haptic feedback
     */
    fun triggerImpact(style: ImpactStyle = ImpactStyle.MEDIUM) {
        try {
            val generator = generatorForStyle(style)
            generator.prepare()
            generator.impactOccurred()
        } catch (e: Exception) {
            // Haptic feedback not available on this device/simulator
            // Silently fail - haptics are optional UX enhancement
        }
    }

    /**
     * Trigger selection haptic feedback.
     * Use for discrete value changes, like moving between list items or picker values.
     */
    fun triggerSelection() {
        try {
            selectionGenerator.prepare()
            selectionGenerator.selectionChanged()
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }

    /**
     * Trigger notification haptic feedback.
     * Use to communicate success, warning, or error states.
     *
     * @param type The type of notification feedback
     */
    fun triggerNotification(type: NotificationType) {
        try {
            val feedbackType = when (type) {
                NotificationType.SUCCESS -> UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                NotificationType.WARNING -> UINotificationFeedbackType.UINotificationFeedbackTypeWarning
                NotificationType.ERROR -> UINotificationFeedbackType.UINotificationFeedbackTypeError
            }

            notificationGenerator.prepare()
            notificationGenerator.notificationOccurred(feedbackType)
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }

    /**
     * Prepare haptic feedback generator for upcoming interaction.
     * Call this shortly before the expected haptic event to reduce latency.
     *
     * @param style The impact style to prepare for
     */
    fun prepareImpact(style: ImpactStyle = ImpactStyle.MEDIUM) {
        try {
            generatorForStyle(style).prepare()
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }
}
