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
import androidx.compose.ui.draw.scale
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
            .padding(vertical = 8.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            // Step node
            StepNode(
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
                StepConnector(
                    isCompleted = step.state == StepState.COMPLETED,
                    isActive = steps[index + 1].state == StepState.ACTIVE
                )
            }
        }
    }
}

@Composable
fun StepNode(
    step: Step,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animations
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val pulseAnimation = rememberInfiniteTransition()
    val pulse by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val circleColor = when (step.state) {
        StepState.COMPLETED -> MaterialTheme.colors.primary
        StepState.ACTIVE -> MaterialTheme.colors.secondary
        StepState.LOCKED -> Color.Gray.copy(alpha = 0.3f)
    }

    val textColor = when (step.state) {
        StepState.COMPLETED -> Color.White
        StepState.ACTIVE -> Color.White
        StepState.LOCKED -> Color.Gray
    }

    val isClickable = step.state == StepState.COMPLETED || step.state == StepState.ACTIVE

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .then(
                if (isClickable) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    ) {
        // Circle with number or checkmark
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .scale(if (isActive) pulse else 1f)
                .clip(CircleShape)
                .background(
                    if (isActive && step.state == StepState.ACTIVE) {
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colors.secondary,
                                MaterialTheme.colors.secondaryVariant
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(circleColor, circleColor)
                        )
                    }
                )
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colors.secondary.copy(alpha = 0.5f) else Color.Transparent,
                    shape = CircleShape
                )
        ) {
            if (step.state == StepState.COMPLETED) {
                Text(
                    text = "✓",
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = step.number.toString(),
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Step title
        Text(
            text = step.title,
            style = MaterialTheme.typography.caption,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = when (step.state) {
                StepState.COMPLETED -> MaterialTheme.colors.primary
                StepState.ACTIVE -> MaterialTheme.colors.secondary
                StepState.LOCKED -> Color.Gray
            }
        )
    }
}

@Composable
fun RowScope.StepConnector(
    isCompleted: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedWidth by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
    )

    val lineColor = if (isCompleted) {
        listOf(
            MaterialTheme.colors.primary,
            MaterialTheme.colors.primaryVariant
        )
    } else if (isActive) {
        listOf(
            MaterialTheme.colors.secondary.copy(alpha = 0.5f),
            MaterialTheme.colors.secondary.copy(alpha = 0.3f)
        )
    } else {
        listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))
    }

    Box(
        modifier = modifier
            .weight(1f)
            .height(2.dp)
            .padding(horizontal = 4.dp)
    ) {
        // Background line
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.3f))
        )

        // Animated progress line
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedWidth)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(colors = lineColor))
        )
    }
}

