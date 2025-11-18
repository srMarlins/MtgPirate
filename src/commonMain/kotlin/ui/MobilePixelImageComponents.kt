package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Compact mobile-optimized pixel-styled image preview
 * Designed for inline display in mobile portrait layouts
 */
@Composable
fun CompactPixelImagePreview(
    imageUrl: String?, cardName: String, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val colors = MaterialTheme.colors

    Box(
        modifier = modifier.size(width = 56.dp, height = 78.dp)
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
            .background(colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 4.dp))
            .clickable(onClick = onClick), 
        contentAlignment = Alignment.Center
    ) {
        // Inner box to clip image content within pixel border bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp) // Account for border width
                .clip(PixelShape(cornerSize = 2.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                imageUrl != null -> {
                    var loadState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

                    AsyncImage(
                        model = imageUrl,
                        contentDescription = cardName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize(1.05f), // Slightly larger to ensure border clips edges
                        onState = { loadState = it }
                    )

                    // Loading indicator
                    if (loadState is AsyncImagePainter.State.Loading) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), color = colors.primary, strokeWidth = 2.dp
                            )
                        }
                    }

                    // Error state
                    if (loadState is AsyncImagePainter.State.Error) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✗", style = MaterialTheme.typography.body1, color = Color(0xFFF44336)
                            )
                        }
                    }
                }

                else -> {
                    // No image placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🖼", style = MaterialTheme.typography.body2, color = colors.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mobile-optimized modal dialog for enlarged image preview
 */
@Composable
fun MobilePixelImageModal(
    imageUrl: String?, cardName: String, setCode: String, variantType: String, onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colors

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight().clip(PixelShape(cornerSize = 12.dp))
                .pixelBorder(borderWidth = 4.dp, enabled = true, glowAlpha = 0.6f)
                .background(colors.surface, shape = PixelShape(cornerSize = 12.dp)).padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
            ) {
                // Compact header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "▸ $cardName",
                            style = MaterialTheme.typography.body1,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PixelBadge(text = setCode, color = colors.secondary)
                            PixelBadge(text = variantType, color = colors.primary)
                        }
                    }

                    // Close button
                    Box(
                        modifier = Modifier.size(40.dp).clip(PixelShape(cornerSize = 6.dp))
                            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
                            .background(colors.surface, shape = PixelShape(cornerSize = 6.dp))
                            .clickable(onClick = onDismiss), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕", style = MaterialTheme.typography.h6, color = colors.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Large image preview - mobile aspect ratio
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.715f) // Standard MTG card aspect ratio
                        .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.4f)
                        .background(colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner box to clip image content within pixel border bounds
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp) // Account for border width
                            .clip(PixelShape(cornerSize = 4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            imageUrl != null -> {
                                var loadState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = cardName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    onState = { loadState = it }
                                )

                            // Loading indicator
                            if (loadState is AsyncImagePainter.State.Loading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    MagicalLoadingSpinner(size = 48.dp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Loading image...",
                                        style = MaterialTheme.typography.body2,
                                        color = colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Error state
                            if (loadState is AsyncImagePainter.State.Error) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "⚠", style = MaterialTheme.typography.h4, color = Color(0xFFF44336)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Failed to load image",
                                        style = MaterialTheme.typography.body2,
                                        color = colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                            else -> {
                                // No image placeholder
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "🖼",
                                        style = MaterialTheme.typography.h3,
                                        color = colors.onSurface.copy(alpha = 0.3f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "NO IMAGE",
                                        style = MaterialTheme.typography.body1,
                                        color = colors.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Footer
                Text(
                    "Tap outside to close",
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
