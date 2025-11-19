package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import platform.IosHapticFeedback

/**
 * State holder for drag operations
 */
internal data class HybridDragState<T>(
    val draggedItem: T,
    val startIndex: Int,
    val targetIndex: Int,
    val cumulativeOffset: Float
)

/**
 * Calculate visual offset for item during drag
 */
private fun calculateItemOffset(
    itemIndex: Int,
    draggedIndex: Int,
    targetIndex: Int,
    itemHeightPx: Float
): Float {
    return when {
        draggedIndex < targetIndex && itemIndex > draggedIndex && itemIndex <= targetIndex -> -itemHeightPx
        draggedIndex > targetIndex && itemIndex >= targetIndex && itemIndex < draggedIndex -> itemHeightPx
        else -> 0f
    }
}

/**
 * Modern iOS reorderable list with pixel design integration.
 * 
 * This component combines cutting-edge iOS UX patterns with the existing pixel art aesthetic:
 * - Modern iOS gestures and haptic feedback
 * - Smooth spring-based animations
 * - Pixel art borders and styling
 * - Card-based layout with elevation
 * 
 * @param items List of items to display
 * @param onReorder Callback when items are reordered
 * @param modifier Modifier for the container
 * @param usePixelStyle Whether to use pixel borders (true) or modern rounded corners (false)
 * @param itemContent Composable to render each item
 */
@Composable
fun <T> ModernIosReorderableListWithPixelStyle(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    usePixelStyle: Boolean = true,
    itemContent: @Composable (item: T, index: Int, totalItems: Int, isDragging: Boolean) -> Unit
) {
    // Drag state management
    var dragState by remember { mutableStateOf<HybridDragState<T>?>(null) }
    
    val density = LocalDensity.current
    val itemHeightDp = 72.dp
    val itemSpacingDp = 12.dp
    val totalItemHeightDp = itemHeightDp + itemSpacingDp
    val totalItemHeightPx = with(density) { totalItemHeightDp.toPx() }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(itemSpacingDp)
    ) {
        items.forEachIndexed { index, item ->
            val isDragging = dragState?.draggedItem == item
            val currentDragState = dragState
            
            // Calculate visual offset for smooth animations
            val targetOffset = if (currentDragState != null && !isDragging) {
                calculateItemOffset(
                    itemIndex = index,
                    draggedIndex = items.indexOf(currentDragState.draggedItem),
                    targetIndex = currentDragState.targetIndex,
                    itemHeightPx = totalItemHeightPx
                )
            } else {
                0f
            }
            
            HybridReorderableItem(
                item = item,
                isDragging = isDragging,
                targetOffset = targetOffset,
                usePixelStyle = usePixelStyle,
                onDragStart = {
                    IosHapticFeedback.prepareImpact(IosHapticFeedback.ImpactStyle.MEDIUM)
                    IosHapticFeedback.triggerImpact(IosHapticFeedback.ImpactStyle.MEDIUM)
                    
                    dragState = HybridDragState(
                        draggedItem = item,
                        startIndex = index,
                        targetIndex = index,
                        cumulativeOffset = 0f
                    )
                },
                onDrag = { delta ->
                    dragState?.let { state ->
                        val newOffset = state.cumulativeOffset + delta
                        val offsetInItems = (newOffset / totalItemHeightPx).toInt()
                        val newTargetIndex = (state.startIndex + offsetInItems)
                            .coerceIn(0, items.size - 1)
                        
                        if (newTargetIndex != state.targetIndex) {
                            IosHapticFeedback.triggerSelection()
                        }
                        
                        dragState = state.copy(
                            cumulativeOffset = newOffset,
                            targetIndex = newTargetIndex
                        )
                    }
                },
                onDragEnd = {
                    dragState?.let { state ->
                        if (state.startIndex != state.targetIndex) {
                            val newList = items.toMutableList()
                            val draggedItem = newList.removeAt(state.startIndex)
                            newList.add(state.targetIndex, draggedItem)
                            onReorder(newList)
                            
                            IosHapticFeedback.triggerNotification(IosHapticFeedback.NotificationType.SUCCESS)
                        } else {
                            IosHapticFeedback.triggerImpact(IosHapticFeedback.ImpactStyle.LIGHT)
                        }
                    }
                    
                    dragState = null
                }
            ) {
                itemContent(item, index, items.size, isDragging)
            }
        }
    }
}

/**
 * Hybrid reorderable item that can use either pixel or modern styling
 */
@Composable
private fun <T> HybridReorderableItem(
    item: T,
    isDragging: Boolean,
    targetOffset: Float,
    usePixelStyle: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colors
    val density = LocalDensity.current
    
    var dragOffset by remember { mutableStateOf(0f) }
    
    // Smooth spring animation
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    // Lift animation
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    // Elevation
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    // Glow animation
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Background gradient
    val backgroundColor = if (isDragging) {
        Brush.horizontalGradient(
            colors = listOf(
                colors.surface,
                colors.primary.copy(alpha = 0.08f),
                colors.surface
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(colors.surface, colors.surface)
        )
    }
    
    val finalOffsetDp = with(density) {
        if (isDragging) dragOffset.toDp() else animatedOffset.toDp()
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = finalOffsetDp)
            .scale(scale)
            .zIndex(if (isDragging) 10f else 0f)
            .pointerInput(item) {  // Use item as key to ensure unique gesture handler per item
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ ->
                        dragOffset = 0f
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.y
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = {
                        dragOffset = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragOffset = 0f
                        onDragEnd()
                    }
                )
            }
    ) {
        if (usePixelStyle) {
            // Pixel art style with borders
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(PixelShape(cornerSize = 9.dp))
                    .background(backgroundColor, shape = PixelShape(cornerSize = 9.dp))
                    .pixelBorder(
                        borderWidth = if (isDragging) 3.dp else 2.dp,
                        cornerSize = 9.dp,
                        enabled = true,
                        glowAlpha = if (isDragging) glowAlpha else 0.1f
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        } else {
            // Modern rounded style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .shadow(
                        elevation = elevation,
                        shape = RoundedCornerShape(16.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                content()
            }
            
            // Glow overlay when dragging
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            colors.primary.copy(alpha = glowAlpha * 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
        }
    }
}

/**
 * Pre-built variant priority item with modern iOS design and pixel styling option
 */
@Composable
fun HybridVariantPriorityItem(
    variantName: String,
    position: Int,
    totalItems: Int,
    isDragging: Boolean = false,
    usePixelStyle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colors
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Drag handle
            if (usePixelStyle) {
                PixelDragHandle(
                    isDragging = isDragging,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(28.dp)
                )
            } else {
                ModernDragHandle(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                )
            }
            
            // Position badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(if (usePixelStyle) PixelShape(cornerSize = 6.dp) else RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.15f))
                    .then(
                        if (usePixelStyle) Modifier.pixelBorder(
                            borderWidth = 2.dp,
                            cornerSize = 6.dp,
                            enabled = true,
                            glowAlpha = 0.1f
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$position",
                    style = MaterialTheme.typography.body2,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Variant name
            Text(
                text = variantName,
                style = MaterialTheme.typography.body1,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Variant-specific icon
        Text(
            text = getVariantIcon(variantName),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Get icon for variant type
 */
private fun getVariantIcon(variantName: String): String {
    return when {
        variantName.contains("foil", ignoreCase = true) -> "✨"
        variantName.contains("holo", ignoreCase = true) || 
        variantName.contains("rainbow", ignoreCase = true) -> "🌈"
        variantName.contains("extended", ignoreCase = true) || 
        variantName.contains("borderless", ignoreCase = true) ||
        variantName.contains("showcase", ignoreCase = true) ||
        variantName.contains("etched", ignoreCase = true) -> "⭐"
        variantName.contains("regular", ignoreCase = true) || 
        variantName.contains("normal", ignoreCase = true) -> "📄"
        else -> "🃏"
    }
}

/**
 * Modern iOS-style drag handle
 */
@Composable
private fun ModernDragHandle(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colors
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.onSurface.copy(alpha = 0.3f))
            )
        }
    }
}
