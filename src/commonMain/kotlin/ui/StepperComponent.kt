package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StepState {
    COMPLETED,
    ACTIVE,
    LOCKED
}

data class Step(
    val number: Int,
    val title: String,
    val description: String,
    val state: StepState
)

@Composable
fun AnimatedStepper(
    steps: List<Step>,
    currentStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            // Step node
            PixelStepNode(
                step = step,
                isActive = step.number == currentStep,
                onClick = {
                    if (step.state == StepState.COMPLETED || step.state == StepState.ACTIVE) {
                        onStepClick(step.number)
                    }
                }
            )

            // Connector line (except after last step)
            if (index < steps.size - 1) {
                PixelStepConnector(
                    isCompleted = step.state == StepState.COMPLETED,
                    isActive = steps[index + 1].state == StepState.ACTIVE
                )
            }
        }
    }
}

@Composable
fun PixelStepNode(
    step: Step,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colors

    // Bounce animation for active step - reduced scale to minimize layout impact
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Pixel art "power up" pulse animation - subtle for stability
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Glow rotation for active state
    val glowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val circleColor = when (step.state) {
        StepState.COMPLETED -> colors.primary
        StepState.ACTIVE -> colors.secondary
        StepState.LOCKED -> Color.Gray.copy(alpha = 0.3f)
    }

    val textColor = when (step.state) {
        StepState.COMPLETED -> colors.onPrimary
        StepState.ACTIVE -> colors.onSecondary
        StepState.LOCKED -> Color.Gray
    }

    val isClickable = step.state == StepState.COMPLETED || step.state == StepState.ACTIVE

    // Use a stable container with fixed width to prevent layout shifts
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(160.dp) // Fixed width for stable layout
            .then(
                if (isClickable) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    ) {
        // Pixel art circle with number or checkmark
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .scale(if (isActive) scale * pulse else 1f) // Combine animations, apply only to visual
        ) {
            // Outer glow effect for active step
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .pixelBorder(
                            borderWidth = 2.dp,
                            enabled = true,
                            glowAlpha = 0.6f
                        )
                        .background(circleColor.copy(alpha = 0.2f), shape = PixelShape(cornerSize = 12.dp))
                )
            }

            // Main step circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .pixelBorder(
                        borderWidth = 3.dp,
                        enabled = step.state != StepState.LOCKED,
                        glowAlpha = if (isActive) 0.8f else 0.3f
                    )
                    .background(circleColor, shape = PixelShape(cornerSize = 9.dp))
            ) {
                if (step.state == StepState.COMPLETED) {
                    // Pixel art checkmark
                    Text(
                        text = "✓",
                        color = textColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // Step number with pixel styling
                    Text(
                        text = "[${step.number}]",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.h6
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Step title with pixel badge style
        Box(
            modifier = Modifier
                .pixelBorder(
                    borderWidth = 2.dp,
                    enabled = step.state != StepState.LOCKED,
                    glowAlpha = if (isActive) 0.4f else 0.1f
                )
                .background(
                    color = when (step.state) {
                        StepState.COMPLETED -> colors.primary.copy(alpha = 0.2f)
                        StepState.ACTIVE -> colors.secondary.copy(alpha = 0.3f)
                        StepState.LOCKED -> Color.Gray.copy(alpha = 0.1f)
                    },
                    shape = PixelShape(cornerSize = 6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = step.title.uppercase(),
                style = MaterialTheme.typography.caption,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = when (step.state) {
                    StepState.COMPLETED -> colors.primary
                    StepState.ACTIVE -> colors.secondary
                    StepState.LOCKED -> Color.Gray
                },
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun RowScope.PixelStepConnector(
    isCompleted: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colors

    val animatedWidth by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    )

    // Animated pixel pattern for active connector - using a stable continuous animation
    val infiniteTransition = rememberInfiniteTransition()
    val pixelOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val lineColor = if (isCompleted) {
        colors.primary
    } else if (isActive) {
        colors.secondary.copy(alpha = 0.5f)
    } else {
        Color.Gray.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .weight(1f)
            .height(4.dp)
            .padding(horizontal = 12.dp)
            .drawBehind {
                val width = size.width
                val height = size.height

                // Background dotted line - stable reference
                val dotSize = 4f
                val dotSpacing = 8f
                var x = 0f

                while (x < width) {
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(dotSize, height)
                    )
                    x += dotSpacing
                }

                // Animated progress line - more stable calculation
                if (isCompleted || isActive) {
                    val progressWidth = width * animatedWidth

                    // Use a stable pattern size that doesn't change with layout
                    val patternSize = 12f
                    val gapSize = 4f
                    val fullPatternSize = patternSize + gapSize

                    // Calculate offset within a single pattern cycle for smooth animation
                    val animOffset = if (isActive && !isCompleted) {
                        pixelOffset * fullPatternSize
                    } else {
                        0f
                    }

                    // Draw the pattern starting from a stable position
                    var progressX = -animOffset

                    while (progressX < progressWidth) {
                        // Only draw if the bar is within the progress area
                        if (progressX + patternSize > 0) {
                            val barStart = maxOf(0f, progressX)
                            val barEnd = minOf(progressWidth, progressX + patternSize)
                            val barWidth = barEnd - barStart

                            if (barWidth > 0) {
                                drawRect(
                                    color = lineColor,
                                    topLeft = Offset(barStart, 0f),
                                    size = androidx.compose.ui.geometry.Size(barWidth, height)
                                )
                            }
                        }
                        progressX += fullPatternSize
                    }
                }
            }
    )
}



