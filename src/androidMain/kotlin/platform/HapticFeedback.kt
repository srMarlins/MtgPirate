package platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.AndroidApp

/**
 * Android actual: uses Vibrator/VibrationEffect for haptic feedback.
 */
actual object HapticFeedback {

    actual enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }
    actual enum class NotificationType { SUCCESS, WARNING, ERROR }

    // Vibration duration constants (milliseconds)
    private const val DURATION_LIGHT_MS = 20L
    private const val DURATION_MEDIUM_MS = 30L
    private const val DURATION_HEAVY_MS = 50L
    private const val DURATION_SELECTION_MS = 10L
    private const val DURATION_NOTIFY_SUCCESS_MS = 30L
    private const val DURATION_NOTIFY_WARNING_MS = 40L
    private const val DURATION_NOTIFY_ERROR_MS = 60L

    // Vibration amplitude constants (0-255)
    private const val AMPLITUDE_LIGHT = 60
    private const val AMPLITUDE_MEDIUM = 120
    private const val AMPLITUDE_HEAVY = 200
    private const val AMPLITUDE_SELECTION = 40
    private const val AMPLITUDE_NOTIFY_SUCCESS = 100
    private const val AMPLITUDE_NOTIFY_WARNING = 150

    private fun vibrator(): Vibrator? {
        val ctx = AndroidApp.appContext ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    actual fun triggerImpact(style: ImpactStyle) {
        val vibrator = vibrator() ?: return
        try {
            val effect = when (style) {
                ImpactStyle.LIGHT -> VibrationEffect.createOneShot(DURATION_LIGHT_MS, AMPLITUDE_LIGHT)
                ImpactStyle.MEDIUM -> VibrationEffect.createOneShot(DURATION_MEDIUM_MS, AMPLITUDE_MEDIUM)
                ImpactStyle.HEAVY -> VibrationEffect.createOneShot(DURATION_HEAVY_MS, AMPLITUDE_HEAVY)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Haptic feedback not available
        }
    }

    actual fun triggerSelection() {
        val vibrator = vibrator() ?: return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(DURATION_SELECTION_MS, AMPLITUDE_SELECTION))
        } catch (_: Exception) {
            // Haptic feedback not available
        }
    }

    actual fun triggerNotification(type: NotificationType) {
        val vibrator = vibrator() ?: return
        try {
            val effect = when (type) {
                NotificationType.SUCCESS ->
                    VibrationEffect.createOneShot(DURATION_NOTIFY_SUCCESS_MS, AMPLITUDE_NOTIFY_SUCCESS)
                NotificationType.WARNING ->
                    VibrationEffect.createOneShot(DURATION_NOTIFY_WARNING_MS, AMPLITUDE_NOTIFY_WARNING)
                NotificationType.ERROR ->
                    VibrationEffect.createOneShot(DURATION_NOTIFY_ERROR_MS, VibrationEffect.MAX_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Haptic feedback not available
        }
    }

    actual fun prepareImpact(style: ImpactStyle) {
        // No-op on Android; vibration has no prepare step
    }
}
