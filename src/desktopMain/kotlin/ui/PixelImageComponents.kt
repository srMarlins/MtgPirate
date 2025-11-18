package ui

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Pixel-styled small image preview component
 * Shows a small inline preview that can be clicked to open a larger view
 */
@Composable
fun PixelImagePreview(
    imageUrl: String?,
    cardName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colors
    
    Box(
        modifier = modifier
            .width(60.dp)
            .height(84.dp)
            .clip(PixelShape(cornerSize = 4.dp))
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
            .background(colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 4.dp))
            .clickable(onClick = onClick),
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
                        .fillMaxSize()
                        .padding(2.dp),
                    onState = { loadState = it }
                )
                
                // Show loading indicator while image loads
                if (loadState is AsyncImagePainter.State.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                // Show error state
                if (loadState is AsyncImagePainter.State.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.h6,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }
            else -> {
                // No image URL available
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🖼",
                        style = MaterialTheme.typography.h6,
                        color = colors.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No\nImage",
                        style = MaterialTheme.typography.caption,
                        color = colors.onSurface.copy(alpha = 0.3f),
                        fontSize = MaterialTheme.typography.caption.fontSize * 0.7f
                    )
                }
            }
        }
        
        // Click indicator overlay (appears on hover)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.primary.copy(alpha = 0.0f)), // Transparent, just for hover effect
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.primary.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔍",
                    style = MaterialTheme.typography.caption,
                    color = Color.White,
                    fontSize = MaterialTheme.typography.caption.fontSize * 0.8f
                )
            }
        }
    }
}

/**
 * Pixel-styled modal dialog for large image preview
 */
@Composable
fun PixelImageModal(
    imageUrl: String?,
    cardName: String,
    setCode: String,
    variantType: String,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colors
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .wrapContentHeight()
                .clip(PixelShape(cornerSize = 12.dp))
                .pixelBorder(borderWidth = 4.dp, enabled = true, glowAlpha = 0.6f)
                .background(colors.surface, shape = PixelShape(cornerSize = 12.dp))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header with card info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "▸ $cardName",
                                style = MaterialTheme.typography.h6,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            BlinkingCursor()
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelBadge(text = setCode, color = colors.secondary)
                            PixelBadge(text = variantType, color = colors.primary)
                        }
                    }
                    
                    // Close button
                    PixelButton(
                        text = "✕",
                        onClick = onDismiss,
                        variant = PixelButtonVariant.SURFACE,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Large image preview
                Box(
                    modifier = Modifier
                        .width(488.dp)
                        .height(680.dp)
                        .clip(PixelShape(cornerSize = 8.dp))
                        .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.4f)
                        .background(colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageUrl != null -> {
                            var loadState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
                            
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = cardName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                onState = { loadState = it }
                            )
                            
                            // Show loading indicator
                            if (loadState is AsyncImagePainter.State.Loading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    MagicalLoadingSpinner(size = 64.dp)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Loading image...",
                                        style = MaterialTheme.typography.body1,
                                        color = colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            // Show error state
                            if (loadState is AsyncImagePainter.State.Error) {
                                PixelCard(glowing = true) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "⚠ IMAGE LOAD FAILED",
                                            style = MaterialTheme.typography.h6,
                                            color = Color(0xFFF44336),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Unable to load image from URL",
                                            style = MaterialTheme.typography.body2,
                                            color = colors.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // No image URL
                            PixelCard(glowing = false) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🖼",
                                        style = MaterialTheme.typography.h3,
                                        color = colors.onSurface.copy(alpha = 0.3f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "NO IMAGE AVAILABLE",
                                        style = MaterialTheme.typography.h6,
                                        color = colors.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "This card variant does not have an image URL",
                                        style = MaterialTheme.typography.body2,
                                        color = colors.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Footer with instructions
                Text(
                    "Click outside or press ESC to close",
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
