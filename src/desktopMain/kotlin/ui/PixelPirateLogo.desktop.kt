package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density

/**
 * Desktop implementation of PixelPirateLogo that loads the scimitar_logo.svg resource
 */
@Composable
actual fun PixelPirateLogo(
    size: Dp,
    modifier: Modifier
) {
    val painter = loadSvgPainter(
        inputStream = object {}.javaClass.getResourceAsStream("/scimitar_logo.svg")
            ?: throw IllegalStateException("scimitar_logo.svg not found in resources"),
        density = Density(1f)
    )

    Image(
        painter = painter,
        contentDescription = "MTG Pirate Scimitar Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

