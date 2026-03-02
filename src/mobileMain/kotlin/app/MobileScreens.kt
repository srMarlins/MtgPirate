package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.MultiMatch
import state.SearchProgress
import model.OrderItem
import model.ProFeature
import model.ProStatus
import model.Seller
import model.SellerOrder
import model.ShoppingPlan
import model.ShoppingPlanComparison
import platform.HapticFeedback
import ui.AnimatedLoadingDots
import ui.BlinkingCursor
import ui.CompactPixelImagePreview
import ui.formatForExport
import ui.HybridVariantPriorityItem
import ui.InlineLoadingCard
import ui.LazyListScrollIndicators
import ui.MobilePixelImageModal
import ui.MobileResultsScreen
import ui.MobileReorderableListWithPixelStyle
import ui.PixelAccent1
import ui.ProBadge
import ui.PixelBadge
import ui.PixelBadgeStyle
import ui.PixelButton
import ui.PixelButtonVariant
import ui.PixelCard
import ui.PixelDivider
import ui.PixelGreen
import ui.PixelOrange
import ui.PixelRed
import ui.PixelShape
import ui.PixelTextField
import ui.PixelToggle
import ui.ScanlineEffect
import ui.pixelBorder
import ui.sellerColor
import util.buildManaPoolUrl
import util.buildTcgPlayerUrl
import util.encodeUrlParameter
import util.formatPrice
import util.sellerCheckoutUrl

/**
 * Mobile Import Screen - Step 1 of the wizard.
 * Allows users to paste their decklist with pixel design styling.
 * Optimized for mobile portrait layout and safe area insets.
 */
@Composable
fun MobileImportScreen(
    deckText: String,
    onDeckTextChange: (String) -> Unit,
    onNext: () -> Unit,
    onShowSavedImports: () -> Unit,
    isLoadingCatalog: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    proStatus: ProStatus = ProStatus.Free,
) {
    // Dismiss keyboard when tapping outside the text field
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
    ) {
        // Scanline effect overlay
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Inline header with DECK LOOT branding, stepper, and theme toggle
            MobileInlineHeader(
                currentStep = 1,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                proStatus = proStatus,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {

            // Title with pixel styling - compact for mobile
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "▸ DECK IMPORT",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "└─ Paste your decklist below",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Inline loading indicator when catalog is loading
            InlineLoadingCard(
                message = "Loading catalog...",
                visible = isLoadingCatalog
            )

            if (isLoadingCatalog) {
                Spacer(Modifier.height(12.dp))
            }

            // Deck text input with pixel card
            PixelCard(
                glowing = deckText.isBlank(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PixelTextField(
                    value = deckText,
                    onValueChange = onDeckTextChange,
                    label = "DECKLIST.TXT",
                    placeholder = "4 Lightning Bolt\n2 Brainstorm\n1 Black Lotus",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons - touch-friendly height
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelButton(
                    text = "📚 Saved",
                    onClick = onShowSavedImports,
                    modifier = Modifier.weight(1f).height(52.dp),
                    variant = PixelButtonVariant.SURFACE
                )

                PixelButton(
                    text = "Next →",
                    onClick = onNext,
                    enabled = deckText.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                    variant = PixelButtonVariant.SECONDARY
                )
            }
            }
        }
    }
}

/**
 * Mobile Preferences Screen - Step 2 of the wizard.
 * Mobile-optimized layout for portrait screens and safe area insets.
 */
@Composable
fun MobilePreferencesScreen(
    includeSideboard: Boolean,
    includeCommanders: Boolean,
    includeTokens: Boolean,
    variantPriority: List<String>,
    onIncludeSideboardChange: (Boolean) -> Unit,
    onIncludeCommandersChange: (Boolean) -> Unit,
    onIncludeTokensChange: (Boolean) -> Unit,
    onVariantPriorityChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    enabledSellers: List<String> = Seller.entries.map { it.name },
    proxyFirst: Boolean = true,
    onEnabledSellersChange: (List<String>) -> Unit = {},
    onProxyFirstChange: (Boolean) -> Unit = {},
    proStatus: ProStatus = ProStatus.Free,
    onShowUpgradePrompt: (ProFeature) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Inline header with DECK LOOT branding, stepper, and theme toggle
            MobileInlineHeader(
                currentStep = 2,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                proStatus = proStatus,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {

            // Compact mobile header
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    "▸ CONFIGURE",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "└─ Set card matching preferences",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Card Inclusion - Compact single-row layout
            PixelCard(
                glowing = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "CARD INCLUSION:",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("CMD", style = MaterialTheme.typography.body2)
                        PixelToggle(
                            checked = includeCommanders,
                            onCheckedChange = {
                                HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.LIGHT)
                                onIncludeCommandersChange(it)
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("SB", style = MaterialTheme.typography.body2)
                        PixelToggle(
                            checked = includeSideboard,
                            onCheckedChange = {
                                HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.LIGHT)
                                onIncludeSideboardChange(it)
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("TOK", style = MaterialTheme.typography.body2)
                        PixelToggle(
                            checked = includeTokens,
                            onCheckedChange = {
                                HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.LIGHT)
                                onIncludeTokensChange(it)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Sellers section
            PixelCard(
                glowing = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SELLERS:",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Seller.entries.forEach { seller ->
                        val isEnabled = seller.name in enabledSellers
                        val isLocked = seller != Seller.BOOTLEG_MAGE && !proStatus.isPro
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    seller.displayName,
                                    style = MaterialTheme.typography.body2,
                                    color = if (isLocked) MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                            else MaterialTheme.colors.onSurface,
                                )
                                PixelBadge(
                                    text = if (seller.isProxy) "P" else "R",
                                    color = if (seller.isProxy) PixelOrange else PixelGreen
                                )
                                if (isLocked) {
                                    ProBadge(onClick = { onShowUpgradePrompt(ProFeature.MULTI_SELLER) })
                                }
                            }
                            PixelToggle(
                                checked = isEnabled,
                                enabled = !isLocked,
                                onCheckedChange = { checked ->
                                    if (isLocked) {
                                        onShowUpgradePrompt(ProFeature.MULTI_SELLER)
                                        return@PixelToggle
                                    }
                                    if (!checked && enabledSellers.size <= 1) return@PixelToggle
                                    HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.LIGHT)
                                    val updated = if (checked) {
                                        enabledSellers + seller.name
                                    } else {
                                        enabledSellers - seller.name
                                    }
                                    onEnabledSellersChange(updated)
                                }
                            )
                        }
                    }

                    PixelDivider()

                    // Prefer Proxies toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PREFER PROXIES",
                            style = MaterialTheme.typography.body2,
                        )
                        PixelToggle(
                            checked = proxyFirst,
                            onCheckedChange = {
                                HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.LIGHT)
                                onProxyFirstChange(it)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Variant Priority
            PixelCard(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                glowing = false
            ) {
                Text(
                    "VARIANT PREFERENCES",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Spacer(Modifier.height(4.dp))

                val variants = variantPriority.ifEmpty { listOf("Regular", "Foil", "Holo") }

                MobileReorderableListWithPixelStyle(
                    items = variants,
                    onReorder = onVariantPriorityChange,
                    usePixelStyle = true,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { variant, index, total, isDragging ->
                    HybridVariantPriorityItem(
                        variantName = variant,
                        position = index + 1,
                        totalItems = total,
                        isDragging = isDragging,
                        usePixelStyle = true
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelButton(
                    text = "← Back",
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(44.dp),
                    variant = PixelButtonVariant.SURFACE
                )

                PixelButton(
                    text = "Next →",
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(44.dp),
                    variant = PixelButtonVariant.SECONDARY
                )
            }
            }
        }
    }
}

/**
 * Mobile Results Screen - Step 3 of the wizard.
 * Mobile-optimized for portrait layout and safe area insets.
 */
@Composable
fun MobileResultsScreenWrapper(
    matches: List<model.DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrefetchImages: ((List<model.CardVariant>) -> Unit)? = null,
    isLoadingCatalog: Boolean = false,
    isMatching: Boolean = false,
    searchProgress: SearchProgress? = null,
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
    proStatus: ProStatus = ProStatus.Free,
    onShowUpgradePrompt: (ProFeature) -> Unit = {},
    onShowAltDetail: (Int) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Inline header with DECK LOOT branding, stepper, and theme toggle
            MobileInlineHeader(
                currentStep = 3,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                proStatus = proStatus,
            )

            // Results screen content - will handle its own padding and loading display
            Box(modifier = Modifier.weight(1f)) {
                MobileResultsScreen(
                    matches = matches,
                    onResolve = onResolve,
                    onShowAllCandidates = onResolve,
                    onClose = onBack,
                    onExport = onNext,
                    onPrefetchImages = onPrefetchImages,
                    isLoading = isLoadingCatalog || isMatching || (searchProgress != null && searchProgress.isSearching),
                    searchProgress = searchProgress,
                    matchedCount = matchedCount,
                    unmatchedCount = unmatchedCount,
                    ambiguousCount = ambiguousCount,
                    multiMatches = multiMatches,
                    availableSellers = availableSellers,
                    onOverrideSeller = onOverrideSeller,
                    proStatus = proStatus,
                    onShowUpgradePrompt = onShowUpgradePrompt,
                    onShowAltDetail = onShowAltDetail,
                )
            }
        }
    }
}

/**
 * Mobile Resolve Screen - Card variant selection.
 * Mobile-optimized for portrait layout with vertical card design.
 */
@Composable
fun MobileResolveScreen(
    match: model.DeckEntryMatch,
    onSelect: (model.CardVariant) -> Unit,
    onBack: () -> Unit,
    onPrefetchImages: ((List<model.CardVariant>) -> Unit)? = null
) {
    // Batch prefetch images for all candidate variants
    val variantsNeedingImages = remember(match.candidates) {
        match.candidates.map { it.variant }.filter { it.smallImageUrl == null }
    }
    LaunchedEffect(variantsNeedingImages) {
        if (variantsNeedingImages.isNotEmpty()) {
            onPrefetchImages?.invoke(variantsNeedingImages)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Compact header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "▸ RESOLVE",
                        style = MaterialTheme.typography.h5,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "└─ ${match.deckEntry.cardName}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                // Candidate count badge
                if (match.candidates.isNotEmpty()) {
                    PixelBadge(
                        text = "${match.candidates.size}",
                        color = MaterialTheme.colors.secondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Empty state
            if (match.candidates.isEmpty()) {
                PixelCard(glowing = true, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "⚠ NO CANDIDATES",
                            style = MaterialTheme.typography.h6,
                            color = PixelRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No matching cards found in catalog",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Candidates list with vertical cards
                val sorted = remember(match.candidates) {
                    match.candidates.sortedBy { it.score }
                }

                PixelCard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    glowing = false
                ) {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sorted, key = { it.uniqueIdentifier }) { cand: model.MatchCandidate ->
                                val variant = cand.variant

                                // Candidate card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(PixelShape(cornerSize = 6.dp))
                                        .background(
                                            MaterialTheme.colors.surface.copy(alpha = 0.5f),
                                            shape = PixelShape(cornerSize = 6.dp)
                                        )
                                        .pixelBorder(
                                            borderWidth = 2.dp,
                                            cornerSize = 6.dp,
                                            enabled = true,
                                            glowAlpha = 0.2f
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Image preview and card info row
                                        var showImageModal by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Compact inline image preview
                                            CompactPixelImagePreview(
                                                smallImageUrl = variant.smallImageUrl,
                                                cardName = variant.nameOriginal,
                                                onClick = { showImageModal = true }
                                            )

                                            // Card details and price
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    variant.nameOriginal,
                                                    style = MaterialTheme.typography.body1,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    PixelBadge(
                                                        text = variant.setCode,
                                                        color = MaterialTheme.colors.secondary,
                                                        style = PixelBadgeStyle.MUTED
                                                    )
                                                    PixelBadge(
                                                        text = variant.variantType.displayName,
                                                        color = PixelAccent1,
                                                        style = PixelBadgeStyle.ACCENT
                                                    )
                                                }
                                                Text(
                                                    util.formatPrice(variant.priceInCents),
                                                    style = MaterialTheme.typography.h6,
                                                    color = MaterialTheme.colors.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Compact icon select button - centered with image
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(PixelShape(cornerSize = 6.dp))
                                                    .background(MaterialTheme.colors.secondary, shape = PixelShape(cornerSize = 6.dp))
                                                    .clickable {
                                                        HapticFeedback.triggerImpact(
                                                            HapticFeedback.ImpactStyle.MEDIUM
                                                        )
                                                        onSelect(variant)
                                                    }
                                                    .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = 0.3f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "✓",
                                                    style = MaterialTheme.typography.h5,
                                                    color = MaterialTheme.colors.onSecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Image modal
                                        if (showImageModal) {
                                            MobilePixelImageModal(
                                                imageUrl = variant.imageUrl,
                                                cardName = variant.nameOriginal,
                                                setCode = variant.setCode,
                                                variantType = variant.variantType.displayName,
                                                onDismiss = { showImageModal = false }
                                            )
                                        }
                                    }
                                }
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

            // Back button
            PixelButton(
                text = "← Back",
                onClick = onBack,
                variant = PixelButtonVariant.SURFACE,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}



/**
 * Mobile Alt Detail Screen - shows current card and all alternative sellers.
 * Navigated to from the "Alt ▸" button on a result card row.
 */
@Composable
fun MobileAltDetailScreen(
    match: model.DeckEntryMatch,
    multiMatch: MultiMatch,
    onUseSeller: (Seller) -> Unit,
    onBack: () -> Unit,
    onPrefetchImages: ((List<model.CardVariant>) -> Unit)? = null,
) {
    // Batch prefetch images for all variants in this screen
    val variantsNeedingImages = remember(match.candidates, multiMatch.alternatives) {
        val all = match.candidates.map { it.variant } +
            multiMatch.alternatives.map { it.variant } +
            listOfNotNull(match.selectedVariant)
        all.filter { it.smallImageUrl == null }
    }
    LaunchedEffect(variantsNeedingImages) {
        if (variantsNeedingImages.isNotEmpty()) {
            onPrefetchImages?.invoke(variantsNeedingImages)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "\u25B8 ALTERNATIVES",
                        style = MaterialTheme.typography.h5,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\u2514\u2500 ${match.deckEntry.cardName}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                PixelBadge(
                    text = "${multiMatch.alternatives.size + 1}",
                    color = MaterialTheme.colors.secondary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Current card (glowing card)
            val currentVariant = match.selectedVariant
            if (currentVariant != null) {
                PixelCard(glowing = true) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "CURRENT",
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var showModal by remember { mutableStateOf(false) }
                            CompactPixelImagePreview(
                                smallImageUrl = currentVariant.smallImageUrl,
                                cardName = match.deckEntry.cardName,
                                onClick = { showModal = true }
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    match.deckEntry.cardName,
                                    style = MaterialTheme.typography.body1,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PixelBadge(text = currentVariant.setCode, color = MaterialTheme.colors.secondary, style = PixelBadgeStyle.MUTED)
                                    if (currentVariant.variantType != model.VariantType.REGULAR) {
                                        PixelBadge(
                                            text = currentVariant.variantType.displayName.uppercase(),
                                            color = PixelAccent1,
                                            style = PixelBadgeStyle.ACCENT
                                        )
                                    }
                                    val bestSeller = multiMatch.bestOption?.seller
                                    if (bestSeller != null) {
                                        PixelBadge(
                                            text = bestSeller.displayName,
                                            color = sellerColor(bestSeller),
                                            style = PixelBadgeStyle.FILLED
                                        )
                                    }
                                }
                            }
                            Text(
                                formatPrice(currentVariant.priceInCents),
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (showModal) {
                                MobilePixelImageModal(
                                    imageUrl = currentVariant.imageUrl,
                                    cardName = match.deckEntry.cardName,
                                    setCode = currentVariant.setCode,
                                    variantType = currentVariant.variantType.displayName,
                                    onDismiss = { showModal = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Alternatives list — single container with divider-separated rows
            PixelCard(modifier = Modifier.weight(1f).fillMaxWidth(), glowing = false) {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        items(
                            multiMatch.alternatives,
                            key = { "${it.seller.name}-${it.variant.sku}" }
                        ) { alt ->
                            val variant = alt.variant
                            var showModal by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactPixelImagePreview(
                                    smallImageUrl = variant.smallImageUrl,
                                    cardName = variant.nameOriginal,
                                    onClick = { showModal = true }
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Name + price row
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            variant.nameOriginal,
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.body2,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            formatPrice(alt.priceCents),
                                            style = MaterialTheme.typography.body2,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colors.secondary
                                        )
                                    }
                                    // Badges + action row
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            PixelBadge(text = variant.setCode, color = MaterialTheme.colors.secondary, style = PixelBadgeStyle.MUTED)
                                            if (variant.variantType != model.VariantType.REGULAR) {
                                                PixelBadge(
                                                    text = variant.variantType.displayName.uppercase(),
                                                    color = PixelAccent1,
                                                    style = PixelBadgeStyle.ACCENT
                                                )
                                            }
                                            PixelBadge(
                                                text = alt.seller.displayName,
                                                color = sellerColor(alt.seller),
                                                style = PixelBadgeStyle.FILLED
                                            )
                                        }
                                        PixelButton(
                                            text = "Use",
                                            onClick = {
                                                HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.MEDIUM)
                                                onUseSeller(alt.seller)
                                            },
                                            variant = PixelButtonVariant.SECONDARY,
                                            modifier = Modifier.height(32.dp)
                                        )
                                    }
                                }
                            }
                            if (showModal) {
                                MobilePixelImageModal(
                                    imageUrl = variant.imageUrl,
                                    cardName = variant.nameOriginal,
                                    setCode = variant.setCode,
                                    variantType = variant.variantType.displayName,
                                    onDismiss = { showModal = false }
                                )
                            }
                            // Divider between items (not after the last one)
                            if (alt != multiMatch.alternatives.last()) {
                                PixelDivider()
                            }
                        }
                    }
                    LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
                }
            }

            Spacer(Modifier.height(12.dp))

            // Back button
            PixelButton(
                text = "\u2190 Back",
                onClick = onBack,
                variant = PixelButtonVariant.SURFACE,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

/**
 * Mobile Shopping Plan Screen - Step 4 of the wizard.
 * Mobile-optimized multi-seller shopping plan with per-seller order cards,
 * expandable item lists, and checkout action buttons.
 */
@Composable
fun MobileShoppingPlanScreen(
    shoppingPlanComparison: ShoppingPlanComparison?,
    multiMatches: List<MultiMatch>,
    isPro: Boolean,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckoutUsea: (SellerOrder) -> Unit = {},
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    proStatus: ProStatus = ProStatus.Free,
) {
    // Trigger optimization if we have multi-matches but no plan yet
    LaunchedEffect(shoppingPlanComparison, multiMatches) {
        if (shoppingPlanComparison == null && multiMatches.isNotEmpty()) {
            onOptimize()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Inline header with DECK LOOT branding, stepper, and theme toggle
            MobileInlineHeader(
                currentStep = 4,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                proStatus = proStatus,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "▸ SHOPPING PLAN",
                        style = MaterialTheme.typography.h5,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "└─ Optimized order across sellers",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (isLoading || shoppingPlanComparison == null) {
                    // Loading state — glowing card with animated content
                    PixelCard(glowing = true, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "⚡ OPTIMIZING",
                                style = MaterialTheme.typography.h5,
                                color = MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Finding the best prices across sellers",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(16.dp))
                            AnimatedLoadingDots()
                        }
                    }
                } else {
                    val activePlan = shoppingPlanComparison.activePlan
                    val proPlan = shoppingPlanComparison.proPlan
                    val showComparison = !isPro && shoppingPlanComparison.savingsDeltaCents > 0
                    val coroutineScope = rememberCoroutineScope()
                    val pagerState = rememberPagerState { activePlan.orders.size }

                    // Grand total summary card
                    MobileShoppingPlanSummary(activePlan)
                    Spacer(Modifier.height(8.dp))

                    // Page indicator dots with swipe hint
                    if (activePlan.orders.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                activePlan.orders.forEachIndexed { index, order ->
                                    val dotColor = if (index == pagerState.currentPage) {
                                        sellerColor(order.seller)
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                                            .clip(PixelShape(cornerSize = 4.dp))
                                            .background(dotColor, shape = PixelShape(cornerSize = 4.dp))
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${pagerState.currentPage + 1}/${activePlan.orders.size}",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Horizontal pager of seller order cards
                    // End padding lets the next card peek in as a visual swipe affordance
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        pageSpacing = 12.dp,
                        contentPadding = if (activePlan.orders.size > 1) PaddingValues(end = 24.dp) else PaddingValues(),
                    ) { page ->
                        MobileSellerOrderCard(
                            order = activePlan.orders[page],
                            onCopyToClipboard = onCopyToClipboard,
                            onOpenUrl = onOpenUrl,
                            onCheckoutUsea = onCheckoutUsea,
                            onCheckoutComplete = {
                                if (pagerState.currentPage < activePlan.orders.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                        )
                    }

                    if (showComparison) {
                        Spacer(Modifier.height(8.dp))
                        MobileProComparisonCard(
                            activePlan = activePlan,
                            proPlan = proPlan,
                            savingsDeltaCents = shoppingPlanComparison.savingsDeltaCents,
                            onUpgrade = onUpgrade,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Back button
                PixelButton(
                    text = "← Back to Results",
                    onClick = onBack,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }
        }
    }
}

/**
 * Summary card showing seller count and grand total price.
 */
@Composable
private fun MobileShoppingPlanSummary(plan: ShoppingPlan) {
    val totalCards = plan.orders.sumOf { order -> order.items.sumOf { it.qty } }

    PixelCard(glowing = true) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${plan.orders.size} SELLERS",
                style = MaterialTheme.typography.overline,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Grand Total",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatPrice(plan.totalPriceCents),
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            PixelDivider()

            // Per-seller breakdown
            plan.orders.forEach { order ->
                val orderCards = order.items.sumOf { it.qty }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.width(3.dp).height(12.dp).background(sellerColor(order.seller)))
                        Text(
                            order.seller.displayName,
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            "($orderCards)",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        formatPrice(order.totalCents),
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = sellerColor(order.seller)
                    )
                }
            }
        }
    }
}

/**
 * Mobile Pro comparison card showing potential savings with Pro.
 * Compact layout optimized for mobile screens.
 */
@Composable
private fun MobileProComparisonCard(
    activePlan: ShoppingPlan,
    proPlan: ShoppingPlan,
    savingsDeltaCents: Int,
    onUpgrade: () -> Unit,
) {
    PixelCard(glowing = true) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "SAVE WITH DECKLOOT PRO",
                style = MaterialTheme.typography.subtitle2,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold
            )

            PixelDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Your Plan",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        formatPrice(activePlan.totalPriceCents),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${activePlan.orders.size} seller(s)",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Pro Plan",
                        style = MaterialTheme.typography.caption,
                        color = PixelGreen
                    )
                    Text(
                        formatPrice(proPlan.totalPriceCents),
                        style = MaterialTheme.typography.h6,
                        color = PixelGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${proPlan.orders.size} seller(s)",
                        style = MaterialTheme.typography.caption,
                        color = PixelGreen.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(PixelGreen.copy(alpha = 0.1f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "You could save ${formatPrice(savingsDeltaCents)} with Pro",
                    style = MaterialTheme.typography.body2,
                    color = PixelGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            proPlan.orders
                .filter { order -> activePlan.orders.none { it.seller == order.seller } }
                .forEach { lockedOrder ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelBadge(
                                text = lockedOrder.seller.displayName,
                                color = sellerColor(lockedOrder.seller)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${lockedOrder.items.sumOf { it.qty }} cards",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            formatPrice(lockedOrder.totalCents),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

            PixelButton(
                text = "Unlock Pro",
                onClick = onUpgrade,
                variant = PixelButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )
        }
    }
}

/**
 * Full-page seller order card for the horizontal pager.
 * Shows seller name prominently, order details, always-visible card list, and checkout button.
 */
@Composable
private fun MobileSellerOrderCard(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckoutUsea: (SellerOrder) -> Unit = {},
    onCheckoutComplete: () -> Unit = {},
) {
    val color = sellerColor(order.seller)
    val totalItems = order.items.sumOf { it.qty }

    PixelCard(glowing = false, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Seller name header — prominent at top
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Colored stripe accent
                    Box(Modifier.width(4.dp).height(24.dp).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        order.seller.displayName.uppercase(),
                        style = MaterialTheme.typography.h6,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                PixelBadge(
                    text = "$totalItems cards",
                    color = color,
                    style = PixelBadgeStyle.MUTED
                )
            }

            Spacer(Modifier.height(8.dp))

            // Order summary row: subtotal, discount, shipping, total
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    Text(formatPrice(order.subtotalCents), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                }
                if (order.discountPercent > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Discount (${order.discountPercent}%)", style = MaterialTheme.typography.caption, color = PixelGreen)
                        val discountAmount = order.subtotalCents * order.discountPercent / 100
                        Text("- ${formatPrice(discountAmount)}", style = MaterialTheme.typography.caption, color = PixelGreen)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Shipping", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    Text(
                        if (order.shippingCents == 0) "Free" else formatPrice(order.shippingCents),
                        style = MaterialTheme.typography.caption,
                        color = if (order.shippingCents == 0) PixelGreen else MaterialTheme.colors.onSurface
                    )
                }
                PixelDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                    Text(formatPrice(order.totalCents), style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold, color = color)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Always-visible scrollable card list
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QTY", Modifier.width(32.dp), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
                Text("CARD", Modifier.weight(1f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
                Text("SET", Modifier.width(40.dp), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
                Text("PRICE", Modifier.width(52.dp), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
            }
            PixelDivider()

            val itemListState = rememberLazyListState()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = itemListState
                ) {
                    items(
                        order.items,
                        key = { it.variant.uniqueIdentifier }
                    ) { item ->
                        val hasLink = item.variant.purchaseUri != null
                        Row(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (hasLink) Modifier.clickable {
                                        onOpenUrl(item.variant.purchaseUri!!)
                                    } else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${item.qty}", Modifier.width(32.dp), style = MaterialTheme.typography.body2)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.variant.nameOriginal,
                                    style = MaterialTheme.typography.body2,
                                    color = if (hasLink) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                    maxLines = 1
                                )
                                if (hasLink) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("↗", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary.copy(alpha = 0.6f))
                                }
                            }
                            Text(item.variant.setCode, Modifier.width(40.dp), style = MaterialTheme.typography.body2)
                            Text(formatPrice(item.variant.priceInCents), Modifier.width(52.dp), style = MaterialTheme.typography.body2)
                        }
                        PixelDivider()
                    }
                }
                LazyListScrollIndicators(
                    state = itemListState,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(Modifier.height(8.dp))

            // PRIMARY BUY BUTTON
            PixelButton(
                text = sellerCheckoutLabel(order.seller),
                onClick = {
                    HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.MEDIUM)
                    performSellerCheckout(order.seller, order, onCopyToClipboard, onOpenUrl, onCheckoutUsea)
                    onCheckoutComplete()
                },
                variant = PixelButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            Spacer(Modifier.height(6.dp))

            // Copy list button
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    kotlinx.coroutines.delay(2000)
                    copied = false
                }
            }
            PixelButton(
                text = if (copied) "Copied!" else "Copy Card List",
                onClick = {
                    HapticFeedback.triggerImpact(HapticFeedback.ImpactStyle.MEDIUM)
                    onCopyToClipboard(formatForExport(order.seller, order.items))
                    copied = true
                },
                variant = PixelButtonVariant.SURFACE,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
    }
}

/** Seller-specific checkout button label. */
private fun sellerCheckoutLabel(seller: Seller): String = when (seller) {
    Seller.USEA -> "Checkout on USEA"
    Seller.BOOTLEG_MAGE -> "Buy on Bootleg Mage"
    Seller.TCGPLAYER -> "Buy on TCGPlayer"
    Seller.MANAPOOL -> "Buy on ManaPool"
}

/** Perform seller-specific checkout action. */
private fun performSellerCheckout(
    seller: Seller,
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckoutUsea: (SellerOrder) -> Unit = {},
) {
    when (seller) {
        Seller.USEA -> onCheckoutUsea(order)
        Seller.BOOTLEG_MAGE -> {
            onCopyToClipboard(formatForExport(Seller.BOOTLEG_MAGE, order.items))
            sellerCheckoutUrl(Seller.BOOTLEG_MAGE, order.items)?.let { onOpenUrl(it) }
        }
        Seller.TCGPLAYER -> onOpenUrl(buildTcgPlayerUrl(order.items))
        Seller.MANAPOOL -> onOpenUrl(buildManaPoolUrl(order.items))
    }
}

