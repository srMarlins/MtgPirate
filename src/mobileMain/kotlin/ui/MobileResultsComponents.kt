package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import model.CardVariant
import model.DeckEntryMatch
import model.MatchOption
import model.MatchStatus
import model.MultiMatch
import model.Seller
import model.VariantType
import util.formatPrice

private const val FILTER_ALL = 0
private const val FILTER_MATCHED = 1
private const val FILTER_UNMATCHED = 2
private const val FILTER_AMBIGUOUS = 3
private const val PROXY_ALL = 0
private const val PROXY_ONLY = 1
private const val REAL_ONLY = 2

/** Left-edge color stripe indicating match status. */
@Composable
fun ResultCardStatusStripe(status: MatchStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        MatchStatus.AUTO_MATCHED -> PixelGreen
        MatchStatus.NOT_FOUND -> PixelRed
        MatchStatus.AMBIGUOUS -> PixelOrange
        MatchStatus.MANUAL_SELECTED -> PixelBlue
        MatchStatus.FUZZY_RECHECK -> PixelYellow
        MatchStatus.UNRESOLVED -> PixelGrey
    }
    Box(modifier.width(4.dp).fillMaxHeight().background(color))
}

/** Card-style result item with image thumbnail, status stripe, and inline metadata. */
@Composable
fun ResultCardItem(
    match: DeckEntryMatch,
    multiMatch: MultiMatch?,
    globalIndex: Int,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onShowAltDetail: () -> Unit,
    onEnrichVariant: ((CardVariant) -> Unit)?,
) {
    val variant = match.selectedVariant

    // Trigger image enrichment when variant comes into view
    variant?.let { v ->
        LaunchedEffect(v.sku) {
            if (v.imageUrl == null) onEnrichVariant?.invoke(v)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResultCardStatusStripe(match.status)
            CardImageWithModal(variant = variant, cardName = match.deckEntry.cardName)
            CardInfoColumn(
                match = match,
                variant = variant,
                multiMatch = multiMatch,
                globalIndex = globalIndex,
                onResolve = onResolve,
                onShowAllCandidates = onShowAllCandidates,
                onShowAltDetail = onShowAltDetail,
            )
        }

        PixelDivider(modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun CardImageWithModal(variant: CardVariant?, cardName: String) {
    var showModal by remember { mutableStateOf(false) }
    CompactPixelImagePreview(
        imageUrl = variant?.imageUrl,
        cardName = cardName,
        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        onClick = { showModal = true }
    )
    if (showModal && variant != null) {
        MobilePixelImageModal(
            imageUrl = variant.imageUrl,
            cardName = cardName,
            setCode = variant.setCode,
            variantType = variant.variantType.displayName,
            onDismiss = { showModal = false }
        )
    }
}

@Composable
private fun RowScope.CardInfoColumn(
    match: DeckEntryMatch,
    variant: CardVariant?,
    multiMatch: MultiMatch?,
    globalIndex: Int,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onShowAltDetail: () -> Unit,
) {
    val unitPrice = variant?.priceInCents?.let { formatPrice(it) }
    Column(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CardNamePriceRow(cardName = match.deckEntry.cardName, unitPrice = unitPrice)
        CardBadgeActionRow(
            match = match, variant = variant, multiMatch = multiMatch, globalIndex = globalIndex,
            seller = multiMatch?.bestOption?.seller,
            onResolve = onResolve, onShowAllCandidates = onShowAllCandidates,
            onShowAltDetail = onShowAltDetail,
        )
    }
}

@Composable
private fun CardNamePriceRow(cardName: String, unitPrice: String?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            cardName,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (unitPrice != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                unitPrice, style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colors.secondary
            )
        }
    }
}

@Composable
private fun CardBadgeActionRow(
    match: DeckEntryMatch,
    variant: CardVariant?,
    multiMatch: MultiMatch?,
    globalIndex: Int,
    seller: Seller?,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onShowAltDetail: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (variant != null) {
                PixelBadge(text = variant.setCode, color = MaterialTheme.colors.secondary, style = PixelBadgeStyle.MUTED)
                if (variant.variantType != VariantType.REGULAR) {
                    PixelBadge(text = variant.variantType.displayName.uppercase(), color = PixelAccent1, style = PixelBadgeStyle.ACCENT)
                }
            }
            if (seller != null) {
                PixelBadge(
                    text = seller.displayName,
                    color = if (seller.isProxy) PixelOrange else sellerColor(seller)
                )
            }
            if (match.deckEntry.qty > 1) {
                Text(
                    "x${match.deckEntry.qty}", style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        ResultCardAction(
            match = match, multiMatch = multiMatch, globalIndex = globalIndex,
            onResolve = onResolve, onShowAllCandidates = onShowAllCandidates,
            onShowAltDetail = onShowAltDetail,
        )
    }
}

/** Action button for a result card row. */
@Composable
private fun ResultCardAction(
    match: DeckEntryMatch,
    multiMatch: MultiMatch?,
    globalIndex: Int,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onShowAltDetail: () -> Unit,
) {
    val needsFix = match.status == MatchStatus.AMBIGUOUS ||
        match.status == MatchStatus.NOT_FOUND ||
        match.status == MatchStatus.FUZZY_RECHECK

    when {
        needsFix -> PixelButton(
            text = "Fix \u25B8", onClick = { onResolve(globalIndex) },
            variant = PixelButtonVariant.SECONDARY, modifier = Modifier.height(32.dp)
        )
        multiMatch != null && multiMatch.alternatives.isNotEmpty() -> PixelButton(
            text = "Alt \u25B8", onClick = onShowAltDetail,
            variant = PixelButtonVariant.SURFACE, modifier = Modifier.height(32.dp)
        )
        match.candidates.isNotEmpty() -> PixelButton(
            text = "View", onClick = { onShowAllCandidates(globalIndex) },
            variant = PixelButtonVariant.SURFACE, modifier = Modifier.height(32.dp)
        )
    }
}

/** Expandable panel showing alternative seller options. */
@Composable
fun AltSellersPanel(
    visible: Boolean,
    alternatives: List<MatchOption>,
    onUseSeller: (Seller) -> Unit,
    onCollapse: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        Column(
            Modifier.fillMaxWidth()
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
            alternatives.forEach { alt ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelBadge(text = alt.seller.displayName, color = sellerColor(alt.seller))
                        Text(formatPrice(alt.priceCents), style = MaterialTheme.typography.body2)
                    }
                    PixelButton(
                        text = "Use",
                        onClick = { onUseSeller(alt.seller); onCollapse() },
                        variant = PixelButtonVariant.SURFACE,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

/** Collapsed filter bar showing summary counts with tap-to-expand. */
@Composable
fun ResultsFilterBar(
    totalCount: Int,
    sortOption: SortOption,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCycleSortOption: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterBarChip(
            text = "ALL: $totalCount",
            arrow = if (isExpanded) " \u25B4" else " \u25BE",
            onClick = onToggleExpand,
            modifier = Modifier.weight(1f),
        )
        FilterBarChip(
            text = "Sort: ${sortOption.label}",
            arrow = " \u25BE",
            onClick = onCycleSortOption,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterBarChip(
    text: String,
    arrow: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(PixelShape(cornerSize = 6.dp))
            .background(MaterialTheme.colors.surface, shape = PixelShape(cornerSize = 6.dp))
            .clickable(onClick = onClick)
            .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = 0.2f)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
            Text(arrow, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
        }
    }
}

/** Expanded filter panel with status chips, seller chips, and proxy/variant filters. */
@Composable
fun ResultsFilterPanel(
    visible: Boolean,
    filterMode: Int,
    onFilterModeChange: (Int) -> Unit,
    matchedCount: Int,
    unmatchedCount: Int,
    ambiguousCount: Int,
    totalCount: Int,
    availableSellers: List<Seller>,
    sellerFilter: Seller?,
    onSellerFilterChange: (Seller?) -> Unit,
    proxyFilter: Int,
    onProxyFilterChange: (Int) -> Unit,
    variantTypeFilter: VariantType?,
    onVariantTypeFilterChange: (VariantType?) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusFilterChips(filterMode, onFilterModeChange, totalCount, matchedCount, unmatchedCount, ambiguousCount)
            if (availableSellers.size > 1) {
                SellerFilterChips(availableSellers, sellerFilter, onSellerFilterChange)
            }
            ProxyVariantFilterChips(proxyFilter, onProxyFilterChange, variantTypeFilter, onVariantTypeFilterChange)
            SortFilterChips(sortOption = sortOption, onSortOptionChange = onSortOptionChange)
        }
    }
}

@Composable
private fun StatusFilterChips(
    filterMode: Int,
    onFilterModeChange: (Int) -> Unit,
    totalCount: Int,
    matchedCount: Int,
    unmatchedCount: Int,
    ambiguousCount: Int,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(label = "ALL ($totalCount)", isActive = filterMode == FILTER_ALL,
            activeColor = MaterialTheme.colors.primary, onClick = { onFilterModeChange(FILTER_ALL) })
        FilterChip(label = "MATCHED ($matchedCount)", isActive = filterMode == FILTER_MATCHED,
            activeColor = PixelGreen, onClick = { onFilterModeChange(FILTER_MATCHED) })
        FilterChip(label = "UNMATCHED ($unmatchedCount)", isActive = filterMode == FILTER_UNMATCHED,
            activeColor = PixelRed, onClick = { onFilterModeChange(FILTER_UNMATCHED) })
        FilterChip(label = "AMBIGUOUS ($ambiguousCount)", isActive = filterMode == FILTER_AMBIGUOUS,
            activeColor = PixelOrange, onClick = { onFilterModeChange(FILTER_AMBIGUOUS) })
    }
}

@Composable
private fun SellerFilterChips(
    availableSellers: List<Seller>,
    sellerFilter: Seller?,
    onSellerFilterChange: (Seller?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(label = "All Sellers", isActive = sellerFilter == null,
            activeColor = MaterialTheme.colors.primary, onClick = { onSellerFilterChange(null) })
        availableSellers.forEach { seller ->
            FilterChip(label = seller.displayName, isActive = sellerFilter == seller,
                activeColor = sellerColor(seller), onClick = {
                    onSellerFilterChange(if (sellerFilter == seller) null else seller)
                })
        }
    }
}

@Composable
private fun ProxyVariantFilterChips(
    proxyFilter: Int,
    onProxyFilterChange: (Int) -> Unit,
    variantTypeFilter: VariantType?,
    onVariantTypeFilterChange: (VariantType?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("ALL" to PROXY_ALL, "PROXY" to PROXY_ONLY, "REAL" to REAL_ONLY).forEach { (label, value) ->
            val chipColor = when (value) {
                PROXY_ONLY -> PixelOrange; REAL_ONLY -> PixelGreen; else -> MaterialTheme.colors.primary
            }
            FilterChip(label = label, isActive = proxyFilter == value,
                activeColor = chipColor, onClick = { onProxyFilterChange(value) })
        }
        Spacer(Modifier.width(4.dp))
        FilterChip(label = "ALL", isActive = variantTypeFilter == null,
            activeColor = MaterialTheme.colors.primary, onClick = { onVariantTypeFilterChange(null) })
        VariantType.entries.forEach { vt ->
            FilterChip(
                label = vt.displayName.uppercase(),
                isActive = variantTypeFilter == vt,
                activeColor = MaterialTheme.colors.secondary,
                onClick = { onVariantTypeFilterChange(if (variantTypeFilter == vt) null else vt) }
            )
        }
    }
}

@Composable
private fun SortFilterChips(sortOption: SortOption, onSortOptionChange: (SortOption) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SortOption.entries.forEach { option ->
            FilterChip(
                label = option.label,
                isActive = sortOption == option,
                activeColor = MaterialTheme.colors.secondary,
                onClick = { onSortOptionChange(option) }
            )
        }
    }
}

/** Human-readable label for each sort option. */
private val SortOption.label: String
    get() = when (this) {
        SortOption.DEFAULT -> "Default"
        SortOption.NAME_ASC -> "Name \u25B2"
        SortOption.NAME_DESC -> "Name \u25BC"
        SortOption.QTY_ASC -> "Qty \u25B2"
        SortOption.QTY_DESC -> "Qty \u25BC"
        SortOption.PRICE_ASC -> "Price \u25B2"
        SortOption.PRICE_DESC -> "Price \u25BC"
        SortOption.STATUS_ASC -> "Status \u25B2"
        SortOption.STATUS_DESC -> "Status \u25BC"
    }

/** Cycle through sort options: Default -> Name -> Price -> Qty -> Default ... */
fun cycleSortOption(current: SortOption): SortOption = when (current) {
    SortOption.DEFAULT -> SortOption.NAME_ASC
    SortOption.NAME_ASC -> SortOption.NAME_DESC
    SortOption.NAME_DESC -> SortOption.PRICE_ASC
    SortOption.PRICE_ASC -> SortOption.PRICE_DESC
    SortOption.PRICE_DESC -> SortOption.QTY_ASC
    SortOption.QTY_ASC -> SortOption.QTY_DESC
    SortOption.QTY_DESC -> SortOption.STATUS_ASC
    SortOption.STATUS_ASC -> SortOption.STATUS_DESC
    SortOption.STATUS_DESC -> SortOption.DEFAULT
}
