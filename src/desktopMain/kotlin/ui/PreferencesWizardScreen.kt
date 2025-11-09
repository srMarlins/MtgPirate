package ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun PreferencesWizardScreen(
    includeCommanders: Boolean,
    includeTokens: Boolean,
    variantPriority: List<String>,
    onIncludeCommandersChange: (Boolean) -> Unit,
    onIncludeTokensChange: (Boolean) -> Unit,
    onVariantPriorityChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // State for the reorderable list
    var variantList by remember { mutableStateOf(variantPriority.ifEmpty { listOf("Regular", "Foil", "Holo") }) }
    var newVariantText by remember { mutableStateOf("") }
    var includeSideboard by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // Header
        Text("Step 2: Configure Options", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(4.dp))
        Text(
            "Customize how cards should be matched and prioritized",
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))

        // Card Inclusion Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Card Inclusion:",
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSideboard, onCheckedChange = { includeSideboard = it })
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

        Spacer(Modifier.height(16.dp))

        // Variant Priority Section
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Variant Preferences", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Drag items or use arrows to reorder. Top items are preferred.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                // Reorderable List with drag support
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    elevation = 0.dp,
                    shape = RoundedCornerShape(4.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        variantList.forEachIndexed { index, item ->
                            val isDragging = draggedIndex == index
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp)

                            DraggableVariantItem(
                                variant = item,
                                index = index,
                                isDragging = isDragging,
                                elevation = elevation,
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { delta ->
                                    dragOffset += delta
                                    // Calculate target index based on drag offset
                                    val itemHeight = 56 // Approximate item height in dp
                                    val targetIndex =
                                        (index + (dragOffset / itemHeight).toInt()).coerceIn(0, variantList.size - 1)

                                    if (targetIndex != index) {
                                        val newList = variantList.toMutableList()
                                        val draggedItem = newList.removeAt(index)
                                        newList.add(targetIndex, draggedItem)
                                        variantList = newList
                                        onVariantPriorityChange(newList)
                                        draggedIndex = targetIndex
                                        dragOffset = 0f
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    dragOffset = 0f
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
                                },
                                onRemove = {
                                    val newList = variantList.toMutableList()
                                    newList.removeAt(index)
                                    variantList = newList
                                    onVariantPriorityChange(newList)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Add new variant
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newVariantText,
                        onValueChange = { newVariantText = it },
                        label = { Text("Add Variant") },
                        placeholder = { Text("e.g., Etched, Extended") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newVariantText.isNotBlank() && !variantList.contains(newVariantText.trim())) {
                                val newList = variantList + newVariantText.trim()
                                variantList = newList
                                onVariantPriorityChange(newList)
                                newVariantText = ""
                            }
                        },
                        enabled = newVariantText.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Navigation Buttons
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(48.dp).width(150.dp)
            ) {
                Text("← Back")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.height(48.dp).width(200.dp)
            ) {
                Text("Match Cards & View Results →")
            }
        }
    }
}

@Composable
fun DraggableVariantItem(
    variant: String,
    index: Int,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation)
            .zIndex(if (isDragging) 1f else 0f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        elevation = elevation,
        shape = RoundedCornerShape(4.dp),
        backgroundColor = if (isDragging)
            MaterialTheme.colors.primary.copy(alpha = 0.1f)
        else
            MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "☰",
                    style = MaterialTheme.typography.h6,
                    color = if (isDragging)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Text(variant, style = MaterialTheme.typography.body1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Text("↑", style = MaterialTheme.typography.body1)
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Text("↓", style = MaterialTheme.typography.body1)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Text("×", style = MaterialTheme.typography.body1, color = Color(0xFFF44336))
                }
            }
        }
    }
}
