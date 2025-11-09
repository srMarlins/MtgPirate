package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun PreferencesWizardScreen(
    includeSideboard: Boolean,
    includeCommanders: Boolean,
    includeTokens: Boolean,
    variantPriority: List<String>,
    onIncludeSideboardChange: (Boolean) -> Unit,
    onIncludeCommandersChange: (Boolean) -> Unit,
    onIncludeTokensChange: (Boolean) -> Unit,
    onVariantPriorityChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // State for the reorderable list
    var variantList by remember { mutableStateOf(variantPriority.ifEmpty { listOf("Regular", "Foil", "Holo") }) }
    var draggedItem by remember { mutableStateOf<String?>(null) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▸ CONFIGURE",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PixelBadge(text = "STEP 2/3", color = MaterialTheme.colors.secondary)
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "└─ Customize how cards should be matched and prioritized",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))


            // Card Inclusion Section with pixel styling
            PixelCard(glowing = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CARD INCLUSION:",
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeSideboard, onCheckedChange = onIncludeSideboardChange)
                        Spacer(Modifier.width(4.dp))
                        Text("Sideboard", style = MaterialTheme.typography.body2)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeCommanders, onCheckedChange = onIncludeCommandersChange)
                        Spacer(Modifier.width(4.dp))
                        Text("Commanders", style = MaterialTheme.typography.body2)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeTokens, onCheckedChange = onIncludeTokensChange)
                        Spacer(Modifier.width(4.dp))
                        Text("Tokens", style = MaterialTheme.typography.body2)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Variant Priority Section with pixel styling
            PixelCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                glowing = false
            ) {
                Text("VARIANT PREFERENCES", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "└─ Drag items or use arrows to reorder. Top items are preferred.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                // Reorderable List with drag support
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Use weight instead of fixed height
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                        .background(MaterialTheme.colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 6.dp))
                        .padding(12.dp) // Increased padding
                ) {
                    val density = LocalDensity.current
                    val itemHeightPx = with(density) { 64.dp.toPx() } // Item + spacing in pixels

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        variantList.forEachIndexed { index, item ->
                            val isDragging = draggedItem == item

                            // Calculate visual offset for smooth animation
                            val visualOffset = if (isDragging) {
                                0f // Dragged item follows mouse directly
                            } else if (draggedItem != null && hoveredIndex != null) {
                                val draggedCurrentIndex = variantList.indexOf(draggedItem)
                                val targetIndex = hoveredIndex!!

                                // Determine if this item needs to move to make space
                                when {
                                    // If dragging down: items between original and target move up
                                    draggedCurrentIndex < targetIndex && index > draggedCurrentIndex && index <= targetIndex -> -64f
                                    // If dragging up: items between target and original move down
                                    draggedCurrentIndex > targetIndex && index >= targetIndex && index < draggedCurrentIndex -> 64f
                                    else -> 0f
                                }
                            } else {
                                0f
                            }

                            DraggableVariantItem(
                                variant = item,
                                index = index,
                                isDragging = isDragging,
                                visualOffset = visualOffset,
                                onDragStart = {
                                    draggedItem = item
                                    hoveredIndex = index
                                },
                                onDrag = { dragOffsetPx ->
                                    // Calculate which index the dragged item is hovering over
                                    // dragOffsetPx is the cumulative drag offset in pixels from start position
                                    val startIndex = variantList.indexOf(item)

                                    // Calculate target index based on how far we've dragged
                                    val positionDelta = (dragOffsetPx / itemHeightPx).toInt()
                                    val newHoveredIndex = (startIndex + positionDelta).coerceIn(0, variantList.size - 1)

                                    hoveredIndex = newHoveredIndex
                                },
                                onDragEnd = {

                                    // Reorder if needed
                                    if (draggedItem != null && hoveredIndex != null) {
                                        val currentIndex = variantList.indexOf(draggedItem)
                                        val targetIndex = hoveredIndex!!

                                        if (currentIndex != -1 && currentIndex != targetIndex) {
                                            val newList = variantList.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(targetIndex, item)
                                            variantList = newList
                                            onVariantPriorityChange(newList)
                                        }
                                    }

                                    // Clear drag state
                                    draggedItem = null
                                    hoveredIndex = null
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        val newList = variantList.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index - 1]
                                        newList[index - 1] = temp
                                        variantList = newList
                                        onVariantPriorityChange(newList)
                                    }
                                },
                                onMoveDown = {
                                    if (index < variantList.size - 1) {
                                        val newList = variantList.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index + 1]
                                        newList[index + 1] = temp
                                        variantList = newList
                                        onVariantPriorityChange(newList)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))


            // Navigation Buttons with pixel styling
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PixelButton(
                    text = "← Back",
                    onClick = onBack,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(180.dp)
                )
                PixelButton(
                    text = "Match Cards & View Results →",
                    onClick = onNext,
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.width(280.dp)
                )
            }
        }
    }
}

@Composable
fun DraggableVariantItem(
    variant: String,
    index: Int,
    isDragging: Boolean,
    visualOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val colors = MaterialTheme.colors
    val density = LocalDensity.current
    var cumulativeDragOffsetPx by remember { mutableStateOf(0f) }
    var wasMovedDuringDrag by remember { mutableStateOf(false) }

    // Track if this item was moved during drag
    LaunchedEffect(visualOffset) {
        if (visualOffset != 0f) {
            wasMovedDuringDrag = true
        } else if (wasMovedDuringDrag && visualOffset == 0f) {
            // Reset flag after a brief moment
            kotlinx.coroutines.delay(50)
            wasMovedDuringDrag = false
        }
    }

    // Convert pixel offset to dp for rendering
    val cumulativeDragOffsetDp = with(density) { cumulativeDragOffsetPx.toDp() }

    // Smooth animation for visual offset
    // Use snap (instant) when an item that was moved is returning to 0 (after drop)
    val animatedOffset by animateFloatAsState(
        targetValue = visualOffset,
        animationSpec = if (visualOffset == 0f && wasMovedDuringDrag) {
            // Instantly snap to 0 when settling after drop - no animation
            snap()
        } else {
            // Smooth animation when items are moving to make space during drag
            tween(
                durationMillis = 200,
                easing = FastOutSlowInEasing
            )
        }
    )


    // Enhanced animations for more solid feel
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing
        )
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isDragging) 4.dp else 2.dp,
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing
        )
    )

    // Pulsing glow animation when dragging
    val infiniteTransition = rememberInfiniteTransition()
    val dragGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val glowAlpha = if (isDragging) dragGlowAlpha else 0.2f

    // Background color with gradient when dragging
    val backgroundColor = if (isDragging) {
        Brush.horizontalGradient(
            colors = listOf(
                colors.primary.copy(alpha = 0.15f),
                colors.secondary.copy(alpha = 0.15f),
                colors.primary.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(colors.surface, colors.surface)
        )
    }

    // Calculate the final offset - either from dragging or from animation
    val finalOffset = if (isDragging) {
        cumulativeDragOffsetDp
    } else {
        animatedOffset.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = finalOffset)
            .scale(scale)
            .zIndex(if (isDragging) 10f else 0f)
            .pointerInput(variant) { // Use variant as key to prevent state confusion
                detectDragGestures(
                    onDragStart = {
                        cumulativeDragOffsetPx = 0f
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // dragAmount.y is in pixels
                        cumulativeDragOffsetPx += dragAmount.y
                        // Report the cumulative offset in pixels
                        onDrag(cumulativeDragOffsetPx)
                    },
                    onDragEnd = {
                        cumulativeDragOffsetPx = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        cumulativeDragOffsetPx = 0f
                        onDragEnd()
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder(
                    borderWidth = borderWidth,
                    enabled = true,
                    glowAlpha = glowAlpha
                )
                .background(backgroundColor, shape = PixelShape(cornerSize = 6.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pixel-styled drag handle
                    PixelDragHandle(isDragging = isDragging)

                    PixelBadge(
                        text = "${index + 1}",
                        color = if (isDragging) colors.primary else colors.secondary.copy(alpha = 0.6f),
                        modifier = Modifier
                    )

                    Text(
                        text = variant,
                        style = MaterialTheme.typography.body1,
                        fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal,
                        color = if (isDragging) colors.primary else colors.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PixelIconButton(
                        icon = "↑",
                        onClick = onMoveUp,
                        variant = PixelIconButtonVariant.SECONDARY
                    )
                    PixelIconButton(
                        icon = "↓",
                        onClick = onMoveDown,
                        variant = PixelIconButtonVariant.SECONDARY
                    )
                }
            }
        }
    }
}