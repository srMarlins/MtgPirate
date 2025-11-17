package ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop platform-specific UI constants optimized for larger screens.
 */
actual object PlatformUI {
    actual val screenPadding: Dp = 24.dp
    actual val cardPadding: Dp = 16.dp
    actual val sectionSpacing: Dp = 16.dp
    actual val itemSpacing: Dp = 12.dp
    actual val headerSize: String = "h4"
    actual val showBlinkingCursor: Boolean = true
}

