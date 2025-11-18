package platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS Haptic Feedback utilities for providing tactile feedback during user interactions.
 * Uses UIKit's feedback generators for native iOS haptic patterns.
 */
object IosHapticFeedback {
    
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
    
    /**
     * Trigger impact haptic feedback.
     * Use for physical interactions like button presses, drag starts, or collisions.
     * 
     * @param style The intensity of the haptic feedback
     */
    fun triggerImpact(style: ImpactStyle = ImpactStyle.MEDIUM) {
        try {
            val feedbackStyle = when (style) {
                ImpactStyle.LIGHT -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
                ImpactStyle.MEDIUM -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                ImpactStyle.HEAVY -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            }
            
            val generator = UIImpactFeedbackGenerator(feedbackStyle)
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
            val generator = UISelectionFeedbackGenerator()
            generator.prepare()
            generator.selectionChanged()
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
            
            val generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(feedbackType)
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
            val feedbackStyle = when (style) {
                ImpactStyle.LIGHT -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
                ImpactStyle.MEDIUM -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                ImpactStyle.HEAVY -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            }
            
            val generator = UIImpactFeedbackGenerator(feedbackStyle)
            generator.prepare()
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }
}
