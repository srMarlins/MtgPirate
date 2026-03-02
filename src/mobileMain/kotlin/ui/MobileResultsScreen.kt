package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.CardVariant
import model.DeckEntryMatch
import model.MatchStatus
import model.MultiMatch
import model.ProFeature
import model.ProStatus
import model.Seller
import model.VariantType
import state.SearchProgress

private const val FILTER_ALL = 0
private const val FILTER_MATCHED = 1
private const val FILTER_UNMATCHED = 2
private const val FILTER_AMBIGUOUS = 3
private const val PROXY_ALL = 0
private const val PROXY_ONLY = 1
private const val REAL_ONLY = 2

/** Mobile-optimized Results Screen with card-based layout and collapsible filters. */
@Composable
fun MobileResultsScreen(
    matches: List<DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onEnrichVariant: ((CardVariant) -> Unit)? = null,
    isLoading: Boolean = false,
    searchProgress: SearchProgress? = null,
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
    proStatus: ProStatus = ProStatus.Free,
    onShowUpgradePrompt: (ProFeature) -> Unit = {},
    onShowAltDetail: (Int) -> Unit = {},
) {
    val state = rememberResultsState(multiMatches)
    val sorted = rememberSortedResults(matches, state)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            ResultsHeader(matches, state, matchedCount, unmatchedCount, ambiguousCount, availableSellers)
            if (isLoading) { LoadingSection(searchProgress); Spacer(Modifier.height(8.dp)) }
            if (!isLoading) {
                ResultsContent(
                    sorted, matches, multiMatches, state, onResolve,
                    onShowAllCandidates, onOverrideSeller, onEnrichVariant,
                    onShowAltDetail,
                    Modifier.fillMaxWidth().weight(1f),
                )
                Spacer(Modifier.height(10.dp))
                FooterActions(onClose, onExport, matches.isNotEmpty())
            }
        }
    }
}

/** Holds all filter/sort state for the results screen. */
internal class ResultsState(
    filterMode: Int,
    sortOption: SortOption,
    sellerFilter: Seller?,
    proxyFilter: Int,
    variantTypeFilter: VariantType?,
    filtersExpanded: Boolean,
    multiMatchByEntryId: Map<String, MultiMatch>,
) {
    var filterMode by mutableStateOf(filterMode)
    var sortOption by mutableStateOf(sortOption)
    var sellerFilter by mutableStateOf(sellerFilter)
    var proxyFilter by mutableStateOf(proxyFilter)
    var variantTypeFilter by mutableStateOf(variantTypeFilter)
    var filtersExpanded by mutableStateOf(filtersExpanded)
    var multiMatchByEntryId by mutableStateOf(multiMatchByEntryId)
}

@Composable
private fun rememberResultsState(multiMatches: List<MultiMatch>): ResultsState {
    val lookup = remember(multiMatches) { multiMatches.associateBy { it.deckEntry.id } }
    val state = remember {
        ResultsState(FILTER_ALL, SortOption.DEFAULT, null, PROXY_ALL, null, false, lookup)
    }
    state.multiMatchByEntryId = lookup
    return state
}

@Composable
private fun rememberSortedResults(matches: List<DeckEntryMatch>, state: ResultsState): List<DeckEntryMatch> {
    return remember(
        matches, state.filterMode, state.sellerFilter, state.proxyFilter, state.variantTypeFilter, state.sortOption
    ) {
        val filtered = applyStatusFilter(matches, state.filterMode)
        val withSeller = applySellerFilter(filtered, state.sellerFilter, state.multiMatchByEntryId)
        val withProxy = applyProxyFilter(withSeller, state.proxyFilter)
        val withVariant = applyVariantFilter(withProxy, state.variantTypeFilter)
        applySorting(withVariant, state.sortOption)
    }
}

@Composable
private fun ResultsHeader(
    matches: List<DeckEntryMatch>,
    state: ResultsState,
    matchedCount: Int,
    unmatchedCount: Int,
    ambiguousCount: Int,
    availableSellers: List<Seller>,
) {
    ResultsFilterBar(
        totalCount = matches.size, sortOption = state.sortOption, isExpanded = state.filtersExpanded,
        onToggleExpand = { state.filtersExpanded = !state.filtersExpanded },
        onCycleSortOption = { state.sortOption = cycleSortOption(state.sortOption) },
    )
    ResultsFilterPanel(
        visible = state.filtersExpanded, filterMode = state.filterMode,
        onFilterModeChange = { state.filterMode = it },
        matchedCount = matchedCount, unmatchedCount = unmatchedCount, ambiguousCount = ambiguousCount,
        totalCount = matches.size, availableSellers = availableSellers,
        sellerFilter = state.sellerFilter, onSellerFilterChange = { state.sellerFilter = it },
        proxyFilter = state.proxyFilter, onProxyFilterChange = { state.proxyFilter = it },
        variantTypeFilter = state.variantTypeFilter, onVariantTypeFilterChange = { state.variantTypeFilter = it },
        sortOption = state.sortOption, onSortOptionChange = { state.sortOption = it },
    )
}

@Composable
private fun ColumnScope.ResultsContent(
    sorted: List<DeckEntryMatch>,
    matches: List<DeckEntryMatch>,
    multiMatches: List<MultiMatch>,
    state: ResultsState,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onOverrideSeller: (Int, Seller) -> Unit,
    onEnrichVariant: ((CardVariant) -> Unit)?,
    onShowAltDetail: (Int) -> Unit,
    modifier: Modifier,
) {
    PixelCard(modifier = modifier, glowing = false) {
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(sorted, key = { _, m -> m.uniqueIdentifier }) { _, m ->
                    val globalIndex = matches.indexOf(m)
                    val multiMatch = state.multiMatchByEntryId[m.deckEntry.id]
                    ResultCardItem(
                        match = m, multiMatch = multiMatch, globalIndex = globalIndex,
                        onResolve = onResolve, onShowAllCandidates = onShowAllCandidates,
                        onShowAltDetail = {
                            val idx = multiMatches.indexOfFirst { it.deckEntry.id == m.deckEntry.id }
                            if (idx >= 0) onShowAltDetail(idx)
                        },
                        onEnrichVariant = onEnrichVariant,
                    )
                }
            }
            LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun FooterActions(onClose: () -> Unit, onExport: () -> Unit, hasMatches: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelButton(
            text = "\u2190 Back", onClick = onClose,
            variant = PixelButtonVariant.SURFACE, modifier = Modifier.weight(1f).height(48.dp)
        )
        if (hasMatches) {
            PixelButton(
                text = "Export \u2192", onClick = onExport,
                variant = PixelButtonVariant.SECONDARY, modifier = Modifier.weight(1f).height(48.dp)
            )
        }
    }
}

@Composable
private fun LoadingSection(searchProgress: SearchProgress?) {
    if (searchProgress != null && searchProgress.isSearching) {
        SearchProgressPanel(searchProgress = searchProgress)
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Matching cards...", style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold
                )
                AnimatedLoadingDots()
            }
        }
    }
}

private fun applyStatusFilter(matches: List<DeckEntryMatch>, filterMode: Int) = when (filterMode) {
    FILTER_MATCHED -> matches.filter { it.selectedVariant != null }
    FILTER_UNMATCHED -> matches.filter { it.selectedVariant == null && it.deckEntry.include }
    FILTER_AMBIGUOUS -> matches.filter { it.status == MatchStatus.AMBIGUOUS }
    else -> matches
}

private fun applySellerFilter(
    matches: List<DeckEntryMatch>, seller: Seller?, lookup: Map<String, MultiMatch>,
) = if (seller != null) matches.filter { lookup[it.deckEntry.id]?.bestOption?.seller == seller } else matches

private fun applyProxyFilter(matches: List<DeckEntryMatch>, proxyFilter: Int) = when (proxyFilter) {
    PROXY_ONLY -> matches.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == true }
    REAL_ONLY -> matches.filter { it.selectedVariant == null || it.selectedVariant?.seller?.isProxy == false }
    else -> matches
}

private fun applyVariantFilter(matches: List<DeckEntryMatch>, vt: VariantType?) =
    if (vt != null) matches.filter { it.selectedVariant == null || it.selectedVariant?.variantType == vt } else matches

private fun applySorting(matches: List<DeckEntryMatch>, sortOption: SortOption) = when (sortOption) {
    SortOption.NAME_ASC -> matches.sortedBy { it.deckEntry.cardName.lowercase() }
    SortOption.NAME_DESC -> matches.sortedByDescending { it.deckEntry.cardName.lowercase() }
    SortOption.QTY_ASC -> matches.sortedBy { it.deckEntry.qty }
    SortOption.QTY_DESC -> matches.sortedByDescending { it.deckEntry.qty }
    SortOption.PRICE_ASC -> matches.sortedBy { it.selectedVariant?.priceInCents ?: Int.MAX_VALUE }
    SortOption.PRICE_DESC -> matches.sortedByDescending { it.selectedVariant?.priceInCents ?: -1 }
    SortOption.STATUS_ASC -> matches.sortedBy { it.status.ordinal }
    SortOption.STATUS_DESC -> matches.sortedByDescending { it.status.ordinal }
    SortOption.DEFAULT -> matches
}
