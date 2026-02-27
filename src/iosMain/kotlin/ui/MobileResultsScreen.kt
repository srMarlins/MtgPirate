package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import model.CardVariant
import model.DeckEntryMatch
import model.MatchStatus
import model.MultiMatch
import model.Seller
import model.VariantType
import state.SearchProgress
import util.formatPrice

/**
 * Mobile-optimized Results Screen for iOS.
 * Designed for portrait mode with condensed layout.
 */
@Composable
fun MobileResultsScreen(
    matches: List<DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onEnrichVariant: ((CardVariant) -> Unit)? = null,
    isLoading: Boolean = false,
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
    searchProgress: SearchProgress? = null,
) {
    val totalMatched = matches.filter { it.selectedVariant != null }
    val missed = unmatchedCount
    val ambiguous = ambiguousCount

    var filterMode by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Matched, 2 = Unmatched, 3 = Ambiguous
    val sortSaver = remember { Saver<SortOption, String>(save = { it.name }, restore = { SortOption.valueOf(it) }) }
    var sortOption by rememberSaveable(stateSaver = sortSaver) { mutableStateOf(SortOption.DEFAULT) }

    // Multi-seller state
    var sellerFilter by remember { mutableStateOf<Seller?>(null) }
    var proxyFilter by remember { mutableStateOf(0) } // 0 = All, 1 = Proxy Only, 2 = Real Only
    var variantTypeFilter by remember { mutableStateOf<VariantType?>(null) }
    var expandedRows by remember { mutableStateOf(emptySet<String>()) }
    val multiMatchByEntryId = remember(multiMatches) {
        multiMatches.associateBy { it.deckEntry.id }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(12.dp)) {
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

            // Summary Cards as clickable filters (4 cards, no TOTAL)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // All Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(PixelShape(cornerSize = 9.dp))
                        .background(
                            if (filterMode == 0) MaterialTheme.colors.primary.copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 0 }
                        .pixelBorder(
                            borderWidth = if (filterMode == 0) 3.dp else 2.dp,
                            cornerSize = 9.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 0) 0.5f else 0.1f
                        )
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
                        .clip(PixelShape(cornerSize = 9.dp))
                        .background(
                            if (filterMode == 1) PixelGreen.copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 1 }
                        .pixelBorder(
                            borderWidth = if (filterMode == 1) 3.dp else 2.dp,
                            cornerSize = 9.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 1) 0.5f else if (totalMatched.isNotEmpty()) 0.3f else 0.1f
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "MATCHED",
                            style = MaterialTheme.typography.caption,
                            color = PixelGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${totalMatched.size}",
                            style = MaterialTheme.typography.h5,
                            color = PixelGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Unmatched Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(PixelShape(cornerSize = 9.dp))
                        .background(
                            if (filterMode == 2) PixelRed.copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 2 }
                        .pixelBorder(
                            borderWidth = if (filterMode == 2) 3.dp else 2.dp,
                            cornerSize = 9.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 2) 0.5f else if (missed > 0) 0.3f else 0.1f
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "UNMATCHED",
                            style = MaterialTheme.typography.caption,
                            color = if (missed > 0) PixelRed else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$missed",
                            style = MaterialTheme.typography.h5,
                            color = if (missed > 0) PixelRed else MaterialTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Ambiguous Cards
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(PixelShape(cornerSize = 9.dp))
                        .background(
                            if (filterMode == 3) PixelOrange.copy(alpha = 0.2f) else MaterialTheme.colors.surface,
                            shape = PixelShape(cornerSize = 9.dp)
                        )
                        .clickable { filterMode = 3 }
                        .pixelBorder(
                            borderWidth = if (filterMode == 3) 3.dp else 2.dp,
                            cornerSize = 9.dp,
                            enabled = true,
                            glowAlpha = if (filterMode == 3) 0.5f else if (ambiguous > 0) 0.3f else 0.1f
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "AMBIGUOUS",
                            style = MaterialTheme.typography.caption,
                            color = if (ambiguous > 0) PixelOrange else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$ambiguous",
                            style = MaterialTheme.typography.h5,
                            color = if (ambiguous > 0) PixelOrange else MaterialTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Seller filter chips (only when multiple sellers available)
            if (availableSellers.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All Sellers" chip
                    Box(
                        modifier = Modifier
                            .clip(PixelShape(cornerSize = 6.dp))
                            .background(
                                if (sellerFilter == null) MaterialTheme.colors.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colors.surface,
                                shape = PixelShape(cornerSize = 6.dp)
                            )
                            .clickable { sellerFilter = null }
                            .pixelBorder(
                                borderWidth = if (sellerFilter == null) 2.dp else 1.dp,
                                cornerSize = 6.dp,
                                enabled = true,
                                glowAlpha = if (sellerFilter == null) 0.4f else 0.1f
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "All",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    // Per-seller chips
                    availableSellers.forEach { seller ->
                        val color = sellerColor(seller)
                        val isSelected = sellerFilter == seller
                        Box(
                            modifier = Modifier
                                .clip(PixelShape(cornerSize = 6.dp))
                                .background(
                                    if (isSelected) color.copy(alpha = 0.2f)
                                    else MaterialTheme.colors.surface,
                                    shape = PixelShape(cornerSize = 6.dp)
                                )
                                .clickable { sellerFilter = if (isSelected) null else seller }
                                .pixelBorder(
                                    borderWidth = if (isSelected) 2.dp else 1.dp,
                                    cornerSize = 6.dp,
                                    enabled = true,
                                    glowAlpha = if (isSelected) 0.4f else 0.1f
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                seller.displayName,
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }

            // Proxy/Real + Variant type filter chips (horizontally scrollable)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Proxy filter
                listOf("ALL" to 0, "PROXY" to 1, "REAL" to 2).forEach { (label, value) ->
                    val chipColor = when (value) {
                        1 -> PixelOrange; 2 -> PixelGreen; else -> MaterialTheme.colors.primary
                    }
                    FilterChip(
                        label = label,
                        isActive = proxyFilter == value,
                        activeColor = chipColor,
                        onClick = { proxyFilter = value }
                    )
                }

                // Variant type filter
                FilterChip(
                    label = "ALL",
                    isActive = variantTypeFilter == null,
                    activeColor = MaterialTheme.colors.primary,
                    onClick = { variantTypeFilter = null }
                )
                VariantType.entries.forEach { vt ->
                    FilterChip(
                        label = vt.displayName.uppercase(),
                        isActive = variantTypeFilter == vt,
                        activeColor = MaterialTheme.colors.secondary,
                        onClick = { variantTypeFilter = if (variantTypeFilter == vt) null else vt }
                    )
                }
            }

            // Search progress indicator (per-seller streaming)
            if (searchProgress != null && searchProgress.isSearching) {
                Spacer(Modifier.height(8.dp))
                SearchProgressPanel(searchProgress = searchProgress)
            }

            Spacer(Modifier.height(16.dp))

            // Loading indicator during matching
            if (isLoading && (searchProgress == null || !searchProgress.isSearching)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Matching cards...",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedLoadingDots()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Hide table and list when loading
            if (!isLoading) {

            // Table Header - Mobile optimized with 4 columns
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = 0.3f)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f), shape = PixelShape(cornerSize = 6.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Sortable Qty header
                    Row(
                        Modifier.width(40.dp).clickable {
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
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.QTY_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.QTY_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    // Sortable Card Name header
                    Row(
                        Modifier.weight(1f).padding(end = 8.dp).clickable {
                            sortOption = when (sortOption) {
                                SortOption.NAME_ASC -> SortOption.NAME_DESC
                                SortOption.NAME_DESC -> SortOption.DEFAULT
                                else -> SortOption.NAME_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CARD",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.NAME_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.NAME_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    // Sortable Price header
                    Row(
                        Modifier.width(60.dp).clickable {
                            sortOption = when (sortOption) {
                                SortOption.PRICE_ASC -> SortOption.PRICE_DESC
                                SortOption.PRICE_DESC -> SortOption.DEFAULT
                                else -> SortOption.PRICE_ASC
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PRICE",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                        if (sortOption == SortOption.PRICE_ASC) Text(" ▲", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.PRICE_DESC) Text(" ▼", style = MaterialTheme.typography.caption)
                    }

                    Box(
                        Modifier.width(80.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "ACTION",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val filtered = when (filterMode) {
                1 -> matches.filter { it.selectedVariant != null }
                2 -> matches.filter { it.selectedVariant == null && it.deckEntry.include }
                3 -> matches.filter { it.status == MatchStatus.AMBIGUOUS }
                else -> matches
            }

            // Apply seller filter
            val sellerFiltered = if (sellerFilter != null) {
                filtered.filter { m ->
                    val mm = multiMatchByEntryId[m.deckEntry.id]
                    mm?.bestOption?.seller == sellerFilter
                }
            } else {
                filtered
            }

            // Apply proxy/real filter
            val proxyFiltered = when (proxyFilter) {
                1 -> sellerFiltered.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == true }
                2 -> sellerFiltered.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == false }
                else -> sellerFiltered
            }

            // Apply variant type filter
            val variantFiltered = if (variantTypeFilter != null) {
                proxyFiltered.filter { it.selectedVariant == null || it.selectedVariant?.variantType == variantTypeFilter }
            } else {
                proxyFiltered
            }

            // Apply sorting
            val sorted = when (sortOption) {
                SortOption.NAME_ASC -> variantFiltered.sortedBy { it.deckEntry.cardName.lowercase() }
                SortOption.NAME_DESC -> variantFiltered.sortedByDescending { it.deckEntry.cardName.lowercase() }
                SortOption.QTY_ASC -> variantFiltered.sortedBy { it.deckEntry.qty }
                SortOption.QTY_DESC -> variantFiltered.sortedByDescending { it.deckEntry.qty }
                SortOption.PRICE_ASC -> variantFiltered.sortedBy { it.selectedVariant?.priceInCents ?: Int.MAX_VALUE }
                SortOption.PRICE_DESC -> variantFiltered.sortedByDescending { it.selectedVariant?.priceInCents ?: -1 }
                SortOption.STATUS_ASC -> variantFiltered.sortedBy { it.status.ordinal }
                SortOption.STATUS_DESC -> variantFiltered.sortedByDescending { it.status.ordinal }
                SortOption.DEFAULT -> variantFiltered
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
                        itemsIndexed(sorted, key = { _, m -> m.uniqueIdentifier }) { _, m ->
                            val globalIndex = matches.indexOf(m)
                            val variant = m.selectedVariant
                            val rowTotal = variant?.priceInCents?.let { it * m.deckEntry.qty }
                            val multiMatch = multiMatchByEntryId[m.deckEntry.id]
                            val isExpanded = expandedRows.contains(m.deckEntry.id)

                            // Trigger image enrichment when variant comes into view
                            variant?.let { v ->
                                LaunchedEffect(v.sku) {
                                    if (v.imageUrl == null) {
                                        onEnrichVariant?.invoke(v)
                                    }
                                }
                            }

                            Column {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // QTY column
                                    Text("${m.deckEntry.qty}", Modifier.width(40.dp), style = MaterialTheme.typography.body2)

                                    // CARD column with status badge inline
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            m.deckEntry.cardName,
                                            style = MaterialTheme.typography.body2,
                                            maxLines = 2
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Status badge
                                            val (statusText, statusColor) = when (m.status) {
                                                MatchStatus.AUTO_MATCHED -> "Auto" to PixelGreen
                                                MatchStatus.MANUAL_SELECTED -> "Manual" to PixelBlue
                                                MatchStatus.AMBIGUOUS -> "Ambiguous" to PixelOrange
                                                MatchStatus.NOT_FOUND -> "Not Found" to PixelRed
                                                MatchStatus.UNRESOLVED -> "Pending" to PixelGrey
                                                MatchStatus.FUZZY_RECHECK -> "Recheck" to PixelYellow
                                            }
                                            PixelBadge(
                                                text = statusText,
                                                color = statusColor
                                            )

                                            // Seller badge
                                            val bestSeller = multiMatch?.bestOption?.seller
                                            if (bestSeller != null) {
                                                PixelBadge(
                                                    text = bestSeller.displayName,
                                                    color = sellerColor(bestSeller)
                                                )
                                            }

                                            // PROXY badge
                                            if (variant?.seller?.isProxy == true) {
                                                PixelBadge(
                                                    text = "P",
                                                    color = PixelOrange
                                                )
                                            }

                                            // Collector number badge if available
                                            val collectorNumber = m.selectedVariant?.collectorNumber
                                            if (!collectorNumber.isNullOrBlank()) {
                                                PixelBadge(
                                                    text = collectorNumber,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }

                                    // PRICE column
                                    Text(
                                        rowTotal?.let { formatPrice(it) } ?: "-",
                                        Modifier.width(60.dp),
                                        style = MaterialTheme.typography.body2
                                    )

                                    // ACTION column
                                    Row(Modifier.width(80.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (m.status == MatchStatus.AMBIGUOUS || m.status == MatchStatus.NOT_FOUND || m.status == MatchStatus.FUZZY_RECHECK) {
                                            PixelButton(
                                                text = "Fix",
                                                onClick = { onResolve(globalIndex) },
                                                variant = PixelButtonVariant.SECONDARY,
                                                modifier = Modifier.height(32.dp).width(75.dp)
                                            )
                                        } else if (multiMatch != null && multiMatch.alternatives.isNotEmpty()) {
                                            PixelButton(
                                                text = "Alt",
                                                onClick = {
                                                    expandedRows = if (isExpanded) {
                                                        expandedRows - m.deckEntry.id
                                                    } else {
                                                        expandedRows + m.deckEntry.id
                                                    }
                                                },
                                                variant = PixelButtonVariant.SURFACE,
                                                modifier = Modifier.height(32.dp).width(75.dp)
                                            )
                                        } else if (m.candidates.isNotEmpty()) {
                                            PixelButton(
                                                text = "View",
                                                onClick = { onShowAllCandidates(globalIndex) },
                                                variant = PixelButtonVariant.SURFACE,
                                                modifier = Modifier.height(32.dp).width(75.dp)
                                            )
                                        }
                                    }
                                }

                                // Expandable alternatives section
                                if (multiMatch != null && multiMatch.alternatives.isNotEmpty()) {
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colors.surface.copy(alpha = 0.5f))
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Alternative sellers:",
                                                style = MaterialTheme.typography.caption,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colors.primary
                                            )
                                            multiMatch.alternatives.forEach { alt ->
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        PixelBadge(
                                                            text = alt.seller.displayName,
                                                            color = sellerColor(alt.seller)
                                                        )
                                                        Text(
                                                            formatPrice(alt.priceCents),
                                                            style = MaterialTheme.typography.body2
                                                        )
                                                    }
                                                    PixelButton(
                                                        text = "Use",
                                                        onClick = {
                                                            val mmIndex = multiMatches.indexOfFirst {
                                                                it.deckEntry.id == m.deckEntry.id
                                                            }
                                                            if (mmIndex >= 0) {
                                                                onOverrideSeller(mmIndex, alt.seller)
                                                            }
                                                            expandedRows = expandedRows - m.deckEntry.id
                                                        },
                                                        variant = PixelButtonVariant.SURFACE,
                                                        modifier = Modifier.height(28.dp)
                                                    )
                                                }
                                            }
                                        }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelButton(
                    text = "← Back to Configure",
                    onClick = onClose,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f).height(52.dp)
                )
                if (matches.isNotEmpty()) {
                    PixelButton(
                        text = "Continue to Export →",
                        onClick = onExport,
                        variant = PixelButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                }
            }
            } // End of if (!isLoading)
        }
    }
}
