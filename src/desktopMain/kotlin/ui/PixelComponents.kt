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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path

// ========================================
// PIXEL SHAPE (for matching border clipping)
// ========================================
class PixelShape(private val cornerSize: Dp = 6.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val cornerPx = with(density) { cornerSize.toPx() }
        return androidx.compose.ui.graphics.Outline.Generic(
            path = Path().apply {
                // Start at top-left corner (after the chamfer)
                moveTo(cornerPx, 0f)
                // Top edge
                lineTo(size.width - cornerPx, 0f)
                // Top-right chamfer
                lineTo(size.width, cornerPx)
                // Right edge
                lineTo(size.width, size.height - cornerPx)
                // Bottom-right chamfer
                lineTo(size.width - cornerPx, size.height)
                // Bottom edge
                lineTo(cornerPx, size.height)
                // Bottom-left chamfer
                lineTo(0f, size.height - cornerPx)
                // Left edge
                lineTo(0f, cornerPx)
                // Top-left chamfer (close the path)
                close()
            }
        )
    }
}

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
            .background(backgroundColor, shape = PixelShape(cornerSize = 9.dp))
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
                text = "✦ $label",
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
                .background(colors.surface, shape = PixelShape(cornerSize = 6.dp))
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
            .background(colors.surface, shape = PixelShape(cornerSize = 9.dp))
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
            .background(colors.surface, shape = PixelShape(cornerSize = 6.dp))
    ) {
        // Progress fill - Mystical gradient
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFB794F6), // Mystical purple
                            Color(0xFF63B3ED), // Crystal blue
                            Color(0xFFFBD38D), // Golden treasure
                            Color(0xFFB794F6)  // Back to purple
                        )
                    ),
                    shape = PixelShape(cornerSize = 6.dp)
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

        // Main border color - Fantasy themed!
        val borderColor = if (enabled) {
            Color(0xFFB794F6) // Mystical purple (arcane glow)
        } else {
            Color(0xFF6B7280) // Muted grey
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
    // Calculate offset accounting for stroke width (stroke is centered on the line)
    val halfStroke = strokeWidth / 2f
    val offset = if (inner) strokeWidth else halfStroke

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
    val colors = MaterialTheme.colors
    Box(
        modifier = modifier
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
            .background(color, shape = PixelShape(cornerSize = 6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.caption,
            color = colors.onPrimary,
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

// ========================================
// MAGICAL SPARKLE EFFECT
// ========================================
@Composable
fun MagicalSparkles(
    modifier: Modifier = Modifier,
    count: Int = 5,
    color: Color = Color(0xFFB794F6)
) {
    val infiniteTransition = rememberInfiniteTransition()
    val sparklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier.drawBehind {
            repeat(count) { index ->
                val angle = (sparklePhase + (index * 360f / count)) % 360f
                val radius = size.minDimension / 3f
                val x = center.x + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                val y = center.y + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()

                // Draw sparkle
                val sparkleSize = 3f + (kotlin.math.sin(Math.toRadians((sparklePhase * 2 + index * 60).toDouble())) * 2f).toFloat()
                drawCircle(
                    color = color.copy(alpha = 0.8f),
                    radius = sparkleSize,
                    center = Offset(x, y)
                )

                // Draw sparkle glow
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = sparkleSize * 2,
                    center = Offset(x, y)
                )
            }
        }
    )
}

// ========================================
// FANTASY STAR DECORATION
// ========================================
@Composable
fun FantasyStarIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFBD38D),
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = if (animated) {
            infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            infiniteRepeatable(
                animation = tween(0),
                repeatMode = RepeatMode.Restart
            )
        }
    )

    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = if (animated) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            infiniteRepeatable(
                animation = tween(0),
                repeatMode = RepeatMode.Restart
            )
        }
    )

    Box(
        modifier = modifier
            .size(16.dp)
            .drawBehind {
                val rotationRad = Math.toRadians(rotation.toDouble())
                val centerX = size.width / 2
                val centerY = size.height / 2
                val outerRadius = size.minDimension / 2
                val innerRadius = outerRadius * 0.4f

                // Draw 4-pointed star
                val points = 8
                val path = androidx.compose.ui.graphics.Path()

                for (i in 0 until points) {
                    val angle = rotationRad + (i * Math.PI / (points / 2))
                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                    val x = centerX + (radius * kotlin.math.cos(angle).toFloat())
                    val y = centerY + (radius * kotlin.math.sin(angle).toFloat())

                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                // Draw glow
                drawPath(
                    path = path,
                    color = color.copy(alpha = twinkle * 0.3f),
                    style = Stroke(width = 4f)
                )

                // Draw star
                drawPath(
                    path = path,
                    color = color.copy(alpha = twinkle)
                )
            }
    )
}

// ========================================
// MYSTICAL TEXT WITH GLOW
// ========================================
@Composable
fun MysticalText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.h6,
    color: Color = MaterialTheme.colors.primary,
    glowColor: Color = color.copy(alpha = 0.5f)
) {
    Box(modifier = modifier) {
        // Glow layer
        Text(
            text = text,
            style = style,
            color = glowColor,
            modifier = Modifier.offset(x = 2.dp, y = 2.dp)
        )
        Text(
            text = text,
            style = style,
            color = glowColor,
            modifier = Modifier.offset(x = (-2).dp, y = (-2).dp)
        )

        // Main text
        Text(
            text = text,
            style = style,
            color = color
        )
    }
}

// ========================================
// FANTASY SECTION HEADER
// ========================================
@Composable
fun FantasySectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    icon: String = "✦"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FantasyStarIcon(color = MaterialTheme.colors.primary, animated = true)
        Spacer(modifier = Modifier.width(8.dp))

        MysticalText(
            text = "$icon $text $icon",
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.width(8.dp))
        FantasyStarIcon(color = MaterialTheme.colors.secondary, animated = true)
    }
}

// ========================================
// ENCHANTED DIVIDER
// ========================================
@Composable
fun EnchantedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .drawBehind {
                val colors = listOf(
                    Color(0xFFB794F6),
                    Color(0xFF63B3ED),
                    Color(0xFF68D391),
                    Color(0xFFFBD38D),
                    Color(0xFFB794F6)
                )

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = colors,
                        startX = -size.width + (shimmer * size.width * 2),
                        endX = shimmer * size.width * 2
                    )
                )
            }
    )
}

// ========================================
// MAGICAL LOADING SPINNER
// ========================================
@Composable
fun MagicalLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val colors = listOf(
                    Color(0xFFB794F6),
                    Color(0xFF63B3ED),
                    Color(0xFFB794F6).copy(alpha = 0.1f)
                )

                // Draw rotating magical circle
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = colors,
                        center = center
                    ),
                    radius = size.toPx() / 2,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Draw sparkles
                repeat(4) { index ->
                    val angle = rotation + (index * 90f)
                    val radius = size.toPx() / 2
                    val x = center.x + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                    val y = center.y + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()

                    drawCircle(
                        color = Color(0xFFFBD38D),
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
    )
}

