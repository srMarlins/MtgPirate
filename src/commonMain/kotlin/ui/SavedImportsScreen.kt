package ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import model.SavedImport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SavedImportsDialog(
    savedImports: List<SavedImport>,
    onDismiss: () -> Unit,
    onSelectImport: (String) -> Unit,
    onDeleteImport: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(800.dp)
                .height(600.dp)
                .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.4f)
                .background(MaterialTheme.colors.surface, shape = PixelShape(cornerSize = 8.dp))
        ) {
            // Scanline effect
            ScanlineEffect(alpha = 0.03f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "▸ DECK HISTORY",
                            style = MaterialTheme.typography.h5,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        PixelBadge(
                            text = "${savedImports.size} SAVED",
                            color = MaterialTheme.colors.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        BlinkingCursor()
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                            .background(MaterialTheme.colors.error.copy(alpha = 0.2f), shape = PixelShape(cornerSize = 4.dp))
                            .clickable { onDismiss() }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.error)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "└─ Select a previous import to restore it",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(16.dp))

                // List of saved imports
                if (savedImports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 6.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "NO SAVED DECKS",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Import a deck first, then it will appear here",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.5f), shape = PixelShape(cornerSize = 6.dp))
                    ) {
                        val listState = rememberLazyListState()
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                state = listState
                            ) {
                                items(savedImports) { import ->
                                    SavedImportCard(
                                        import = import,
                                        onSelect = { onSelectImport(import.id) },
                                        onDelete = { onDeleteImport(import.id) }
                                    )
                                }
                            }
                            LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PixelButton(
                        text = "Close",
                        onClick = onDismiss,
                        variant = PixelButtonVariant.SURFACE,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SavedImportCard(
    import: SavedImport,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    val formattedDate = remember(import.timestamp) {
        try {
            val instant = Instant.parse(import.timestamp)
            dateFormatter.format(instant)
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    PixelCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Import info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect() }
                    .padding(4.dp)
            ) {
                Text(
                    import.name,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📅 $formattedDate",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "🃏 ${import.cardCount} cards",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (import.includeSideboard) {
                        PixelBadge(text = "SB", color = MaterialTheme.colors.secondary.copy(alpha = 0.7f))
                    }
                    if (import.includeCommanders) {
                        PixelBadge(text = "CMD", color = MaterialTheme.colors.secondary.copy(alpha = 0.7f))
                    }
                    if (import.includeTokens) {
                        PixelBadge(text = "TOK", color = MaterialTheme.colors.secondary.copy(alpha = 0.7f))
                    }
                }
            }

            // Right side - Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelButton(
                    text = "Load",
                    onClick = onSelect,
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.width(100.dp)
                )

                // Animated trash/checkmark button
                val buttonColor by animateColorAsState(
                    targetValue = if (showDeleteConfirm)
                        MaterialTheme.colors.secondary.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colors.error.copy(alpha = 0.2f),
                    animationSpec = tween(200)
                )

                val icon by remember {
                    derivedStateOf { if (showDeleteConfirm) "✓" else "🗑" }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                        .background(buttonColor, shape = PixelShape(cornerSize = 4.dp))
                        .clickable {
                            if (showDeleteConfirm) {
                                // Second click - actually delete
                                onDelete()
                                showDeleteConfirm = false
                            } else {
                                // First click - show checkmark
                                showDeleteConfirm = true
                            }
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.body2,
                        color = if (showDeleteConfirm)
                            MaterialTheme.colors.secondary
                        else
                            MaterialTheme.colors.error
                    )
                }
            }
        }
    }
}

