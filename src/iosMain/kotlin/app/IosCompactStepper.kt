package app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.*

/**
 * Compact stepper for iOS mobile portrait mode.
 * Much smaller than the full AnimatedStepper, designed for limited vertical space.
 * Optimized for inline layout - no internal padding.
 */
@Composable
fun CompactStepper(
    currentStep: Int,
    totalSteps: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val stepNumber = index + 1
            val isActive = stepNumber == currentStep
            val isCompleted = stepNumber < currentStep
            
            // Step indicator dot
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .pixelBorder(
                        borderWidth = 1.dp,
                        enabled = isActive || isCompleted,
                        glowAlpha = if (isActive) 0.6f else 0.2f
                    )
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colors.primary
                            isActive -> MaterialTheme.colors.secondary
                            else -> Color.Gray.copy(alpha = 0.3f)
                        },
                        shape = PixelShape(cornerSize = 3.dp)
                    )
            )
            
            // Connector between dots
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(
                            if (isCompleted) MaterialTheme.colors.primary.copy(alpha = 0.5f)
                            else Color.Gray.copy(alpha = 0.2f)
                        )
                )
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        // Step text
        Text(
            text = "$currentStep/$totalSteps",
            style = MaterialTheme.typography.caption,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
    }
}
