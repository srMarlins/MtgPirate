package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixelated scimitar (curved sword) logo for pirate/fantasy aesthetic
 * Uses the scimitar_logo.svg resource
 */
@Composable
expect fun PixelPirateLogo(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
)

