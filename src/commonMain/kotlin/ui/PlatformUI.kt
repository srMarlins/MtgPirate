package ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Platform-specific UI constants for responsive design.
 * Desktop uses larger padding, mobile uses compact padding.
 */
expect object PlatformUI {
    val screenPadding: Dp
    val cardPadding: Dp
    val sectionSpacing: Dp
    val itemSpacing: Dp
    val headerSize: String // "h4" for desktop, "h5" for mobile
    val showBlinkingCursor: Boolean
}

