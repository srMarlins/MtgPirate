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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.CardVariant
import model.DeckEntryMatch
import model.MatchStatus
import model.MultiMatch
import model.Seller
import model.VariantType
import state.SearchProgress
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
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onEnrichVariant: ((CardVariant) -> Unit)? = null,
    isLoading: Boolean = false,
    searchProgress: SearchProgress? = null,
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    totalPriceCents: Int = 0,
) {
    val totalMatched = matches.filter { it.selectedVariant != null }
    val totalCents = totalPriceCents
    val missed = unmatchedCount
    val ambiguous = ambiguousCount

    var filterMode by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Matched, 2 = Unmatched, 3 = Ambiguous
    var sellerFilter by remember { mutableStateOf<Seller?>(null) }
    var proxyFilter by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Proxy Only, 2 = Real Only
    var variantTypeFilter by remember { mutableStateOf<VariantType?>(null) } // null = All
    val sortSaver = remember { Saver<SortOption, String>(save = { it.name }, restore = { SortOption.valueOf(it) }) }
    var sortOption by rememberSaveable(stateSaver = sortSaver) { mutableStateOf(SortOption.DEFAULT) }

    // Track which rows are expanded to show alternatives
    var expandedRows by remember { mutableStateOf(emptySet<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\u25b8 RESULTS",
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
                "\u2514\u2500 Click cards to filter \u2022 Review and resolve any issues",
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

                // Total Price (not clickable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pixelBorder(borderWidth = 2.dp, cornerSize = 9.dp, enabled = true, glowAlpha = 0.1f)
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

            // Compact filter row: seller | proxy/real | variant type
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seller filter chips
                if (availableSellers.size > 1) {
                    FilterChip(
                        label = "ALL",
                        isActive = sellerFilter == null,
                        activeColor = MaterialTheme.colors.primary,
                        onClick = { sellerFilter = null }
                    )
                    availableSellers.forEach { seller ->
                        FilterChip(
                            label = seller.displayName.uppercase(),
                            isActive = sellerFilter == seller,
                            activeColor = sellerColor(seller),
                            onClick = { sellerFilter = if (sellerFilter == seller) null else seller }
                        )
                    }

                    // Divider
                    Box(Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colors.onSurface.copy(alpha = 0.2f)))
                }

                // Proxy/Real filter
                listOf("ALL" to 0, "PROXY" to 1, "REAL" to 2).forEach { (label, value) ->
                    val chipColor = when (value) {
                        1 -> PixelOrange; 2 -> PixelGreen; else -> MaterialTheme.colors.primary
                    }
                    FilterChip(label = label, isActive = proxyFilter == value, activeColor = chipColor, onClick = { proxyFilter = value })
                }

                // Divider
                Box(Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colors.onSurface.copy(alpha = 0.2f)))

                // Variant type filter
                FilterChip(label = "ALL", isActive = variantTypeFilter == null, activeColor = MaterialTheme.colors.primary, onClick = { variantTypeFilter = null })
                VariantType.entries.forEach { vt ->
                    FilterChip(
                        label = vt.displayName.uppercase(),
                        isActive = variantTypeFilter == vt,
                        activeColor = MaterialTheme.colors.secondary,
                        onClick = { variantTypeFilter = if (variantTypeFilter == vt) null else vt }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Loading/search progress indicator
            if (searchProgress != null && searchProgress.isSearching) {
                SearchProgressPanel(searchProgress = searchProgress)
                Spacer(Modifier.height(16.dp))
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedLoadingDots()
                }
                Spacer(Modifier.height(16.dp))
            }

            // Show results when we have data, even during loading
            val hasResults = multiMatches.isNotEmpty() || matches.isNotEmpty()
            if (!isLoading || hasResults) {

            // Table Header with pixel styling and sorting
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
                        if (sortOption == SortOption.QTY_ASC) Text(" \u25b2", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.QTY_DESC) Text(" \u25bc", style = MaterialTheme.typography.caption)
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
                        if (sortOption == SortOption.NAME_ASC) Text(" \u25b2", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.NAME_DESC) Text(" \u25bc", style = MaterialTheme.typography.caption)
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
                        if (sortOption == SortOption.STATUS_ASC) Text(" \u25b2", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.STATUS_DESC) Text(" \u25bc", style = MaterialTheme.typography.caption)
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
                        if (sortOption == SortOption.PRICE_ASC) Text(" \u25b2", style = MaterialTheme.typography.caption)
                        if (sortOption == SortOption.PRICE_DESC) Text(" \u25bc", style = MaterialTheme.typography.caption)
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

            // Apply seller filter (cards without a match always pass through)
            val sellerFiltered = if (sellerFilter != null) {
                filtered.filter { it.selectedVariant == null || it.selectedVariant?.seller == sellerFilter }
            } else {
                filtered
            }

            // Apply proxy/real filter (cards without a match always pass through)
            val proxyFiltered = when (proxyFilter) {
                1 -> sellerFiltered.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == true }
                2 -> sellerFiltered.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == false }
                else -> sellerFiltered
            }

            // Apply variant type filter (cards without a match always pass through)
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

            // Build a lookup from deckEntry id to multiMatch for alternatives
            val multiMatchByEntryId = remember(multiMatches) {
                multiMatches.associateBy { it.deckEntry.id }
            }

            // Results List with pixel card
            Spacer(Modifier.height(8.dp))
            PixelCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                glowing = false
            ) {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(sorted, key = { _, m -> m.uniqueIdentifier }) { _, m ->
                            val globalIndex = matches.indexOf(m)
                            val variant = m.selectedVariant
                            val rowTotal = variant?.priceInCents?.let { it * m.deckEntry.qty }
                            val multiMatch = multiMatchByEntryId[m.deckEntry.id]
                            val hasAlternatives = multiMatch != null && multiMatch.alternatives.size > 1
                            val isExpanded = expandedRows.contains(m.uniqueIdentifier)

                            // Trigger image enrichment when variant comes into view
                            variant?.let { v ->
                                androidx.compose.runtime.LaunchedEffect(v.sku) {
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
                                    Text("${m.deckEntry.qty}", Modifier.width(50.dp), style = MaterialTheme.typography.body1)
                                    Row(Modifier.weight(0.35f), verticalAlignment = Alignment.CenterVertically) {
                                        Text(m.deckEntry.cardName, style = MaterialTheme.typography.body1)
                                        val collectorNumber = m.selectedVariant?.collectorNumber
                                        if (!collectorNumber.isNullOrBlank()) {
                                            Spacer(Modifier.width(4.dp))
                                            PixelBadge(
                                                text = collectorNumber,
                                                color = MaterialTheme.colors.onSurface
                                            )
                                        }
                                        // Seller badge
                                        variant?.let { v ->
                                            Spacer(Modifier.width(4.dp))
                                            PixelBadge(
                                                text = v.seller.displayName,
                                                color = sellerColor(v.seller)
                                            )
                                        }
                                    }

                                    // Status badge with pixel styling
                                    val (statusText, statusColor) = when (m.status) {
                                        MatchStatus.AUTO_MATCHED -> "Auto" to PixelGreen
                                        MatchStatus.MANUAL_SELECTED -> "Manual" to PixelBlue
                                        MatchStatus.AMBIGUOUS -> "Ambiguous" to PixelOrange
                                        MatchStatus.NOT_FOUND -> "Not Found" to PixelRed
                                        MatchStatus.UNRESOLVED -> "Pending" to PixelGrey
                                        MatchStatus.FUZZY_RECHECK -> "Recheck" to PixelYellow
                                    }
                                    Box(Modifier.weight(0.15f).padding(end = 8.dp)) {
                                        PixelBadge(
                                            text = statusText,
                                            color = statusColor
                                        )
                                    }

                                    Text(
                                        variant?.variantType?.displayName ?: "-",
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
                                        if (m.status == MatchStatus.AMBIGUOUS || m.status == MatchStatus.NOT_FOUND || m.status == MatchStatus.FUZZY_RECHECK) {
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
                                        // Expand/collapse alternatives button
                                        if (hasAlternatives) {
                                            PixelButton(
                                                text = if (isExpanded) "\u25b2 Alt" else "\u25bc Alt",
                                                onClick = {
                                                    expandedRows = if (isExpanded) {
                                                        expandedRows - m.uniqueIdentifier
                                                    } else {
                                                        expandedRows + m.uniqueIdentifier
                                                    }
                                                },
                                                variant = PixelButtonVariant.SURFACE,
                                                modifier = Modifier.height(36.dp)
                                            )
                                        }
                                    }
                                }

                                // Expandable alternatives section
                                if (hasAlternatives) {
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colors.surface.copy(alpha = 0.5f))
                                                .padding(start = 50.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
                                        ) {
                                            Text(
                                                "\u2514\u2500 ALTERNATIVES",
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            val multiMatchIndex = multiMatches.indexOfFirst {
                                                it.deckEntry.id == m.deckEntry.id
                                            }
                                            multiMatch.alternatives.forEach { option ->
                                                // Skip the currently selected option
                                                val isCurrent = multiMatch.bestOption?.let {
                                                    it.seller == option.seller &&
                                                        it.variant.sku == option.variant.sku
                                                } ?: false

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .then(
                                                            if (!isCurrent && multiMatchIndex >= 0) {
                                                                Modifier.clickable {
                                                                    onOverrideSeller(multiMatchIndex, option.seller)
                                                                }
                                                            } else {
                                                                Modifier
                                                            }
                                                        )
                                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    PixelBadge(
                                                        text = option.seller.displayName,
                                                        color = sellerColor(option.seller)
                                                    )
                                                    Text(
                                                        option.variant.variantType.displayName,
                                                        style = MaterialTheme.typography.body2,
                                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        formatPrice(option.priceCents),
                                                        style = MaterialTheme.typography.body2,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colors.secondary
                                                    )
                                                    if (isCurrent) {
                                                        PixelBadge(
                                                            text = "CURRENT",
                                                            color = PixelGreen
                                                        )
                                                    }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelButton(
                    text = "\u2190 Back to Configure",
                    onClick = onClose,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(180.dp)
                )
                if (matches.isNotEmpty()) {
                    PixelButton(
                        text = "Continue to Export \u2192",
                        onClick = onExport,
                        variant = PixelButtonVariant.SECONDARY,
                        modifier = Modifier.width(280.dp)
                    )
                }
            }
            } // End of if (!isLoading)
        }
    }
}
