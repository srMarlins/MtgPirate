package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ========================================
// PIXEL BUTTON COMPONENT
// ========================================
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: PixelButtonVariant = PixelButtonVariant.PRIMARY
) {
    val colors = MaterialTheme.colors
    val backgroundColor = when {
        !enabled -> Color.Gray.copy(alpha = 0.3f)
        variant == PixelButtonVariant.PRIMARY -> colors.primary
        variant == PixelButtonVariant.SECONDARY -> colors.secondary
        else -> colors.surface
    }

    val textColor = when {
        !enabled -> Color.Gray
        variant == PixelButtonVariant.PRIMARY -> colors.onPrimary
        variant == PixelButtonVariant.SECONDARY -> colors.onSecondary
        else -> colors.onSurface
    }

    // Pulse animation when enabled
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .pixelBorder(
                borderWidth = 3.dp,
                enabled = enabled,
                glowAlpha = if (enabled) glowAlpha else 0f
            )
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.button,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class PixelButtonVariant {
    PRIMARY, SECONDARY, SURFACE
}

// ========================================
// PIXEL TEXT FIELD
// ========================================
@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    val colors = MaterialTheme.colors

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = "▸ $label",
                style = MaterialTheme.typography.caption,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                .background(colors.surface)
                .padding(12.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = colors.onSurface.copy(alpha = 0.4f)
                    )
                },
                colors = TextFieldDefaults.textFieldColors(
                    textColor = colors.onSurface,
                    backgroundColor = Color.Transparent,
                    cursorColor = colors.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = singleLine,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ========================================
// PIXEL CARD COMPONENT
// ========================================
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colors
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = modifier
            .pixelBorder(
                borderWidth = 3.dp,
                enabled = true,
                glowAlpha = if (glowing) glowAlpha else 0.1f
            )
            .background(colors.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

// ========================================
// PIXEL DIVIDER
// ========================================
@Composable
fun PixelDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
    animated: Boolean = false
) {
    val colors = MaterialTheme.colors

    if (animated) {
        val infiniteTransition = rememberInfiniteTransition()
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(thickness)
                .drawBehind {
                    val dashWidth = 10f
                    val gapWidth = 10f
                    var currentX = offset

                    while (currentX < size.width) {
                        drawLine(
                            color = colors.primary,
                            start = Offset(currentX, size.height / 2),
                            end = Offset(currentX + dashWidth, size.height / 2),
                            strokeWidth = thickness.toPx()
                        )
                        currentX += dashWidth + gapWidth
                    }
                }
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(thickness)
                .background(colors.primary.copy(alpha = 0.3f))
        )
    }
}

// ========================================
// PIXEL PROGRESS BAR
// ========================================
@Composable
fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    showPercentage: Boolean = true
) {
    val colors = MaterialTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
            .background(colors.surface)
    ) {
        // Progress fill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.primary,
                            colors.secondary,
                            colors.primary
                        )
                    )
                )
        )

        // Percentage text
        if (showPercentage) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.caption,
                    color = if (animatedProgress > 0.5f) colors.onPrimary else colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ========================================
// SCANLINE EFFECT
// ========================================
@Composable
fun ScanlineEffect(
    modifier: Modifier = Modifier,
    alpha: Float = 0.05f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawScanlines(alpha)
            }
    )
}

private fun DrawScope.drawScanlines(alpha: Float) {
    val lineHeight = 4f
    var y = 0f

    while (y < size.height) {
        drawRect(
            color = Color.Black.copy(alpha = alpha),
            topLeft = Offset(0f, y),
            size = Size(size.width, lineHeight / 2)
        )
        y += lineHeight
    }
}

// ========================================
// PIXEL BORDER MODIFIER
// ========================================
fun Modifier.pixelBorder(
    borderWidth: Dp = 2.dp,
    enabled: Boolean = true,
    glowAlpha: Float = 0f
): Modifier {
    return this.drawBehind {
        val strokeWidth = borderWidth.toPx()
        val width = size.width
        val height = size.height

        // Corner size for pixel art effect
        val cornerSize = strokeWidth * 3

        // Main border color
        val borderColor = if (enabled) {
            Color(0xFF00FFFF) // Cyan
        } else {
            Color.Gray
        }

        // Draw outer glow if enabled
        if (glowAlpha > 0f && enabled) {
            val glowColor = borderColor.copy(alpha = glowAlpha)
            drawPixelBorder(width, height, cornerSize, strokeWidth + 2f, glowColor)
        }

        // Draw main border
        drawPixelBorder(width, height, cornerSize, strokeWidth, borderColor)

        // Draw inner shadow for depth
        val shadowColor = Color.Black.copy(alpha = 0.3f)
        drawPixelBorder(width, height, cornerSize, strokeWidth / 2, shadowColor, inner = true)
    }
}

private fun DrawScope.drawPixelBorder(
    width: Float,
    height: Float,
    cornerSize: Float,
    strokeWidth: Float,
    color: Color,
    inner: Boolean = false
) {
    val offset = if (inner) strokeWidth else 0f

    // Top line
    drawLine(
        color = color,
        start = Offset(cornerSize + offset, offset),
        end = Offset(width - cornerSize - offset, offset),
        strokeWidth = strokeWidth
    )

    // Bottom line
    drawLine(
        color = color,
        start = Offset(cornerSize + offset, height - offset),
        end = Offset(width - cornerSize - offset, height - offset),
        strokeWidth = strokeWidth
    )

    // Left line
    drawLine(
        color = color,
        start = Offset(offset, cornerSize + offset),
        end = Offset(offset, height - cornerSize - offset),
        strokeWidth = strokeWidth
    )

    // Right line
    drawLine(
        color = color,
        start = Offset(width - offset, cornerSize + offset),
        end = Offset(width - offset, height - cornerSize - offset),
        strokeWidth = strokeWidth
    )

    // Corners (pixel art style)
    // Top-left
    drawLine(
        color = color,
        start = Offset(cornerSize + offset, offset),
        end = Offset(offset, cornerSize + offset),
        strokeWidth = strokeWidth
    )

    // Top-right
    drawLine(
        color = color,
        start = Offset(width - cornerSize - offset, offset),
        end = Offset(width - offset, cornerSize + offset),
        strokeWidth = strokeWidth
    )

    // Bottom-left
    drawLine(
        color = color,
        start = Offset(offset, height - cornerSize - offset),
        end = Offset(cornerSize + offset, height - offset),
        strokeWidth = strokeWidth
    )

    // Bottom-right
    drawLine(
        color = color,
        start = Offset(width - offset, height - cornerSize - offset),
        end = Offset(width - cornerSize - offset, height - offset),
        strokeWidth = strokeWidth
    )
}

// ========================================
// PIXEL BADGE
// ========================================
@Composable
fun PixelBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary
) {
    Box(
        modifier = modifier
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.caption,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }
}

// ========================================
// BLINKING CURSOR
// ========================================
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Text(
        text = "█",
        color = MaterialTheme.colors.primary.copy(alpha = alpha),
        modifier = modifier
    )
}

