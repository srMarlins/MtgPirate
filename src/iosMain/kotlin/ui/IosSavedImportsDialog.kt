@file:OptIn(kotlin.time.ExperimentalTime::class)
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
import androidx.compose.ui.window.DialogProperties
import model.SavedImport
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * iOS-specific mobile-optimized SavedImportsDialog.
 * Uses full-screen style with compact spacing for mobile devices.
 */
@Composable
actual fun SavedImportsDialog(
    savedImports: List<SavedImport>,
    onDismiss: () -> Unit,
    onSelectImport: (String) -> Unit,
    onDeleteImport: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.4f)
                    .background(MaterialTheme.colors.surface, shape = PixelShape(cornerSize = 8.dp))
            ) {
                // Scanline effect
                ScanlineEffect(alpha = 0.03f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Compact header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "▸ DECK HISTORY",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            PixelBadge(
                                text = "${savedImports.size}",
                                color = MaterialTheme.colors.secondary
                            )
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                                .background(
                                    MaterialTheme.colors.error.copy(alpha = 0.2f),
                                    shape = PixelShape(cornerSize = 4.dp)
                                )
                                .clickable { onDismiss() }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "└─ Tap to restore a saved deck",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(Modifier.height(12.dp))

                    // List of saved imports
                    if (savedImports.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                                .background(
                                    MaterialTheme.colors.surface.copy(alpha = 0.5f),
                                    shape = PixelShape(cornerSize = 6.dp)
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "NO SAVED DECKS",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Import a deck first",
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
                                .background(
                                    MaterialTheme.colors.surface.copy(alpha = 0.5f),
                                    shape = PixelShape(cornerSize = 6.dp)
                                )
                        ) {
                            val listState = rememberLazyListState()
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    state = listState
                                ) {
                                    items(savedImports) { import ->
                                        MobileSavedImportCard(
                                            import = import,
                                            onSelect = { onSelectImport(import.id) },
                                            onDelete = { onDeleteImport(import.id) }
                                        )
                                    }
                                }
                                LazyListScrollIndicators(
                                    state = listState,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Bottom button - full width and touch-friendly
                    PixelButton(
                        text = "Close",
                        onClick = onDismiss,
                        variant = PixelButtonVariant.SURFACE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mobile-optimized saved import card for iOS.
 * Compact layout with larger touch targets and clear Load button.
 */
@Composable
fun MobileSavedImportCard(
    import: SavedImport,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val formattedDate = remember(import.timestamp) {
        try {
            val instant = Instant.parse(import.timestamp)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${localDateTime.month.name.take(3)} ${localDateTime.day}, ${localDateTime.year}"
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    PixelCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Deck name
            Text(
                import.name,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Info row - aligned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📅 $formattedDate",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "🃏 ${import.cardCount}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            // Badges row if any options are enabled
            if (import.includeSideboard || import.includeCommanders || import.includeTokens) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            Spacer(Modifier.height(12.dp))

            // Action buttons row - Load and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Load button
                PixelButton(
                    text = "Load",
                    onClick = onSelect,
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
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
                        .size(44.dp)
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                        .background(buttonColor, shape = PixelShape(cornerSize = 4.dp))
                        .clickable {
                            if (showDeleteConfirm) {
                                onDelete()
                                showDeleteConfirm = false
                            } else {
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
