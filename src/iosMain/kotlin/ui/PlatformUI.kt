package ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS platform-specific UI constants optimized for mobile portrait screens.
 */
actual object PlatformUI {
    actual val screenPadding: Dp = 12.dp
    actual val cardPadding: Dp = 12.dp
    actual val sectionSpacing: Dp = 8.dp
    actual val itemSpacing: Dp = 6.dp
    actual val headerSize: String = "h5"
    actual val showBlinkingCursor: Boolean = false // Save screen space on mobile
}

