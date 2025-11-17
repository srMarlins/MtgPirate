package ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Provides a mobile-optimized padding wrapper for iOS screens.
 * Reduces the default 24dp padding used in desktop screens to 12dp for mobile.
 */
@Composable
fun MobilePaddingWrapper(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .padding(bottom = 68.dp) // Extra bottom padding for nav
    ) {
        content()
    }
}

/**
 * Detects if screen padding should be applied based on platform.
 * For iOS, returns smaller padding values optimized for mobile.
 */
object MobilePadding {
    val screen = 12.dp
    val card = 12.dp
    val button = 10.dp
    val section = 8.dp
    val item = 6.dp
    val small = 4.dp
}

