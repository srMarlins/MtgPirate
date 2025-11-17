package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import model.CardVariant
import model.DeckEntryMatch
import model.MatchStatus
import util.formatPrice

enum class SortOption {
    DEFAULT, // Original order
    NAME_ASC,
    NAME_DESC,
    QTY_ASC,
    QTY_DESC,
    PRICE_ASC,
    PRICE_DESC,
    STATUS_ASC,
    STATUS_DESC
}

@Composable
fun ResultsScreen(
    matches: List<DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onEnrichVariant: ((CardVariant) -> Unit)? = null
) {
    val totalMatched = matches.filter { it.selectedVariant != null }
    val totalCents = totalMatched.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }
    val missed = matches.count { it.selectedVariant == null && it.deckEntry.include }
    val ambiguous = matches.count { it.status == MatchStatus.AMBIGUOUS }

    var filterMode by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Matched, 2 = Unmatched, 3 = Ambiguous
    val sortSaver = remember { Saver<SortOption, String>(save = { it.name }, restore = { SortOption.valueOf(it) }) }
    var sortOption by rememberSaveable(stateSaver = sortSaver) { mutableStateOf(SortOption.DEFAULT) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▸ RESULTS",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PixelBadge(text = "STEP 3/4", color = MaterialTheme.colors.secondary)
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "└─ Click cards to filter • Review and resolve any issues",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))

            // Summary Cards as clickable filters
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // All Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(
                            borderWidth = if (filterMode == 0) 3.dp else 2.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 0) 0.5f else 0.1f
                        )
                        .background(
                            if (filterMode == 0) MaterialTheme.colors.primary.copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 0 }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "ALL",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${matches.size}",
                            style = MaterialTheme.typography.h5,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Matched Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(
                            borderWidth = if (filterMode == 1) 3.dp else 2.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 1) 0.5f else if (totalMatched.isNotEmpty()) 0.3f else 0.1f
                        )
                        .background(
                            if (filterMode == 1) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 1 }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "MATCHED",
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${totalMatched.size}",
                            style = MaterialTheme.typography.h5,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Unmatched Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(
                            borderWidth = if (filterMode == 2) 3.dp else 2.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 2) 0.5f else if (missed > 0) 0.3f else 0.1f
                        )
                        .background(
                            if (filterMode == 2) Color(0xFFF44336).copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 2 }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "UNMATCHED",
                            style = MaterialTheme.typography.caption,
                            color = if (missed > 0) Color(0xFFF44336) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$missed",
                            style = MaterialTheme.typography.h5,
                            color = if (missed > 0) Color(0xFFF44336) else MaterialTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Ambiguous Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(
                            borderWidth = if (filterMode == 3) 3.dp else 2.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 3) 0.5f else if (ambiguous > 0) 0.3f else 0.1f
                        )
                        .background(
                            if (filterMode == 3) Color(0xFFFF9800).copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 3 }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "AMBIGUOUS",
                            style = MaterialTheme.typography.caption,
                            color = if (ambiguous > 0) Color(0xFFFF9800) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$ambiguous",
                            style = MaterialTheme.typography.h5,
                            color = if (ambiguous > 0) Color(0xFFFF9800) else MaterialTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Total Price (not clickable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.1f)
                        .background(MaterialTheme.colors.surface, shape = PixelShape(cornerSize = 9.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "TOTAL",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatPrice(totalCents),
                            style = MaterialTheme.typography.h5,
                            color = MaterialTheme.colors.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Table Header with pixel styling and sorting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f), shape = PixelShape(cornerSize = 6.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Sortable Qty header
                    Row(
                        Modifier.width(50.dp).clickable {
                            sortOption = when (sortOption) {
                                SortOption.QTY_ASC -> SortOption.QTY_DESC
                                SortOption.QTY_DESC -> SortOption.DEFAULT
                                else -> SortOption.QTY_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "QTY",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.QTY_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.QTY_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    // Sortable Card Name header
                    Row(
                        Modifier.weight(0.35f).clickable {
                            sortOption = when (sortOption) {
                                SortOption.NAME_ASC -> SortOption.NAME_DESC
                                SortOption.NAME_DESC -> SortOption.DEFAULT
                                else -> SortOption.NAME_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CARD NAME",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.NAME_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.NAME_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    // Sortable Status header
                    Row(
                        Modifier.weight(0.15f).clickable {
                            sortOption = when (sortOption) {
                                SortOption.STATUS_ASC -> SortOption.STATUS_DESC
                                SortOption.STATUS_DESC -> SortOption.DEFAULT
                                else -> SortOption.STATUS_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "STATUS",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.STATUS_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.STATUS_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    Text(
                        "VARIANT",
                        Modifier.weight(0.15f),
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )

                    // Sortable Unit Price header
                    Row(
                        Modifier.width(70.dp).clickable {
                            sortOption = when (sortOption) {
                                SortOption.PRICE_ASC -> SortOption.PRICE_DESC
                                SortOption.PRICE_DESC -> SortOption.DEFAULT
                                else -> SortOption.PRICE_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "UNIT",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.PRICE_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.PRICE_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    Text(
                        "TOTAL",
                        Modifier.width(80.dp),
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ACTIONS",
                        Modifier.width(180.dp),
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val filtered = when (filterMode) {
                1 -> matches.filter { it.selectedVariant != null }
                2 -> matches.filter { it.selectedVariant == null && it.deckEntry.include }
                3 -> matches.filter { it.status == MatchStatus.AMBIGUOUS }
                else -> matches
            }

            // Apply sorting
            val sorted = when (sortOption) {
                SortOption.NAME_ASC -> filtered.sortedBy { it.deckEntry.cardName.lowercase() }
                SortOption.NAME_DESC -> filtered.sortedByDescending { it.deckEntry.cardName.lowercase() }
                SortOption.QTY_ASC -> filtered.sortedBy { it.deckEntry.qty }
                SortOption.QTY_DESC -> filtered.sortedByDescending { it.deckEntry.qty }
                SortOption.PRICE_ASC -> filtered.sortedBy { it.selectedVariant?.priceInCents ?: Int.MAX_VALUE }
                SortOption.PRICE_DESC -> filtered.sortedByDescending { it.selectedVariant?.priceInCents ?: -1 }
                SortOption.STATUS_ASC -> filtered.sortedBy { it.status.ordinal }
                SortOption.STATUS_DESC -> filtered.sortedByDescending { it.status.ordinal }
                SortOption.DEFAULT -> filtered
            }

            // Results List with pixel card
            Spacer(Modifier.height(8.dp))
            PixelCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                glowing = false
            ) {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(sorted) { _, m ->
                            val globalIndex = matches.indexOf(m)
                            val variant = m.selectedVariant
                            val rowTotal = variant?.priceInCents?.let { it * m.deckEntry.qty }

                            // Trigger image enrichment when variant comes into view
                            variant?.let { v ->
                                androidx.compose.runtime.LaunchedEffect(v.sku) {
                                    if (v.imageUrl == null) {
                                        onEnrichVariant?.invoke(v)
                                    }
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${m.deckEntry.qty}", Modifier.width(50.dp), style = MaterialTheme.typography.body1)
                                Row(Modifier.weight(0.35f), verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.deckEntry.cardName, style = MaterialTheme.typography.body1)
                                    val collectorNumber = m.selectedVariant?.collectorNumber
                                    if (!collectorNumber.isNullOrBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        PixelBadge(
                                            text = collectorNumber,
                                            color = MaterialTheme.colors.onSurface
                                        )
                                    }
                                }

                                // Status badge with pixel styling
                                val (statusText, statusColor) = when (m.status) {
                                    MatchStatus.AUTO_MATCHED -> "Auto" to Color(0xFF4CAF50)
                                    MatchStatus.MANUAL_SELECTED -> "Manual" to Color(0xFF2196F3)
                                    MatchStatus.AMBIGUOUS -> "Ambiguous" to Color(0xFFFF9800)
                                    MatchStatus.NOT_FOUND -> "Not Found" to Color(0xFFF44336)
                                    MatchStatus.UNRESOLVED -> "Pending" to Color(0xFF9E9E9E)
                                }
                                Box(Modifier.weight(0.15f).padding(end = 8.dp)) {
                                    PixelBadge(
                                        text = statusText,
                                        color = statusColor
                                    )
                                }

                                Text(
                                    variant?.variantType ?: "-",
                                    Modifier.weight(0.15f),
                                    style = MaterialTheme.typography.body2
                                )
                                Text(variant?.let { formatPrice(it.priceInCents) } ?: "-",
                                    Modifier.width(70.dp),
                                    style = MaterialTheme.typography.body2)
                                Text(rowTotal?.let { formatPrice(it) } ?: "-",
                                    Modifier.width(80.dp),
                                    style = MaterialTheme.typography.body2)

                                Row(Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (m.status == MatchStatus.AMBIGUOUS || m.status == MatchStatus.NOT_FOUND) {
                                        PixelButton(
                                            text = "Resolve",
                                            onClick = { onResolve(globalIndex) },
                                            variant = PixelButtonVariant.SECONDARY,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                    if (m.candidates.isNotEmpty()) {
                                        PixelButton(
                                            text = "View",
                                            onClick = { onShowAllCandidates(globalIndex) },
                                            variant = PixelButtonVariant.SURFACE,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                }
                            }
                            PixelDivider()
                        }
                    }
                    LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer Actions with pixel styling
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelButton(
                    text = "← Back to Configure",
                    onClick = onClose,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(220.dp)
                )
                if (matches.isNotEmpty()) {
                    PixelButton(
                        text = "Export Results →",
                        onClick = onExport,
                        variant = PixelButtonVariant.SECONDARY,
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
        }
    }
}

