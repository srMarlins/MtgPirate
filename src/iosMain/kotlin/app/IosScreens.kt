package app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.MultiMatch
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan
import ui.AnimatedLoadingDots
import ui.BlinkingCursor
import ui.CatalogScreen
import ui.CompactPixelImagePreview
import ui.formatForExport
import ui.HybridVariantPriorityItem
import ui.InlineLoadingCard
import ui.LazyListScrollIndicators
import ui.MatchesScreen
import ui.MobilePixelImageModal
import ui.MobileResultsScreen
import ui.ModernIosReorderableListWithPixelStyle
import ui.PixelBadge
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
import util.formatPrice

/**
 * iOS Import Screen - Step 1 of the wizard.
 * Allows users to paste their decklist with pixel design styling.
 * Optimized for mobile portrait layout and safe area insets.
 */
@Composable
fun IosImportScreen(
    deckText: String,
    onDeckTextChange: (String) -> Unit,
    onNext: () -> Unit,
    onShowSavedImports: () -> Unit,
    isLoadingCatalog: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
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
            // Inline header with MTG PIRATE branding, stepper, and theme toggle
            IosInlineHeader(
                currentStep = 1,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
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
 * iOS Preferences Screen - Step 2 of the wizard.
 * Mobile-optimized layout for portrait screens and safe area insets.
 */
@Composable
fun IosPreferencesScreen(
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
    onToggleTheme: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Inline header with MTG PIRATE branding, stepper, and theme toggle
            IosInlineHeader(
                currentStep = 2,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )

            Spacer(Modifier.height(8.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {

            // Compact mobile header
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
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

            Spacer(Modifier.height(12.dp))

            // Card Inclusion - Compact row layout for mobile portrait
            PixelCard(
                glowing = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "CARD INCLUSION:",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Vertical stack with full text labels
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "COMMANDER",
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.weight(1f)
                        )
                        PixelToggle(
                            checked = includeCommanders,
                            onCheckedChange = { 
                                platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.LIGHT)
                                onIncludeCommandersChange(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SIDEBOARD",
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.weight(1f)
                        )
                        PixelToggle(
                            checked = includeSideboard,
                            onCheckedChange = {
                                platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.LIGHT)
                                onIncludeSideboardChange(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOKEN",
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.weight(1f)
                        )
                        PixelToggle(
                            checked = includeTokens,
                            onCheckedChange = {
                                platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.LIGHT)
                                onIncludeTokensChange(it)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Variant Priority - Scrollable with improved button states
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
                Spacer(Modifier.height(2.dp))
                Text(
                    "└─ Drag items or use arrows to reorder",
                style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))

                // Modern iOS reorderable list with haptic feedback and smooth animations
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(PixelShape(cornerSize = 6.dp))
                        .background(
                            MaterialTheme.colors.surface.copy(alpha = 0.5f),
                            shape = PixelShape(cornerSize = 6.dp)
                        )
                        .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = 0.2f)
                        .padding(8.dp)
                ) {
                    val variants = variantPriority.ifEmpty { listOf("Regular", "Foil", "Holo") }
                    
                    ModernIosReorderableListWithPixelStyle(
                        items = variants,
                        onReorder = onVariantPriorityChange,
                        usePixelStyle = true,
                        modifier = Modifier.fillMaxSize()
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
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelButton(
                    text = "← Back",
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    variant = PixelButtonVariant.SURFACE
                )

                PixelButton(
                    text = "Next →",
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(52.dp),
                    variant = PixelButtonVariant.SECONDARY
                )
            }
            }
        }
    }
}

/**
 * iOS Results Screen - Step 3 of the wizard.
 * Mobile-optimized for portrait layout and safe area insets.
 */
@Composable
fun IosResultsScreen(
    matches: List<model.DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEnrichVariant: ((model.CardVariant) -> Unit)? = null,
    isLoadingCatalog: Boolean = false,
    isMatching: Boolean = false,
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Inline header with MTG PIRATE branding, stepper, and theme toggle
            IosInlineHeader(
                currentStep = 3,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )

            // Results screen content - will handle its own padding and loading display
            Box(modifier = Modifier.weight(1f)) {
                MobileResultsScreen(
                    matches = matches,
                    onResolve = onResolve,
                    onShowAllCandidates = onResolve,
                    onClose = onBack,
                    onExport = onNext,
                    onEnrichVariant = onEnrichVariant,
                    isLoading = isLoadingCatalog || isMatching,
                    matchedCount = matchedCount,
                    unmatchedCount = unmatchedCount,
                    ambiguousCount = ambiguousCount,
                    multiMatches = multiMatches,
                    availableSellers = availableSellers,
                    onOverrideSeller = onOverrideSeller,
                )
            }
        }
    }
}

/**
 * iOS Resolve Screen - Card variant selection.
 * Mobile-optimized for portrait layout with vertical card design.
 */
@Composable
fun IosResolveScreen(
    match: model.DeckEntryMatch,
    onSelect: (model.CardVariant) -> Unit,
    onBack: () -> Unit,
    onEnrichVariant: ((model.CardVariant) -> Unit)? = null
) {
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
                                
                                // Trigger image enrichment
                                androidx.compose.runtime.LaunchedEffect(variant.sku) {
                                    if (variant.imageUrl == null) {
                                        onEnrichVariant?.invoke(variant)
                                    }
                                }
                                
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
                                                imageUrl = variant.imageUrl,
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
                                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                    )
                                                    PixelBadge(
                                                        text = variant.variantType.displayName,
                                                        color = MaterialTheme.colors.primary
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
                                                        platform.IosHapticFeedback.triggerImpact(
                                                            platform.IosHapticFeedback.ImpactStyle.MEDIUM
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

private const val TCGPLAYER_MASS_ENTRY_URL = "https://www.tcgplayer.com/massentry?productline=Magic"
private const val BOOTLEG_MAGE_DECK_IMPORT_URL = "https://bootlegmage.com/deck-import/"


/**
 * iOS Shopping Plan Screen - Step 4 of the wizard.
 * Mobile-optimized multi-seller shopping plan with per-seller order cards,
 * expandable item lists, and checkout action buttons.
 */
@Composable
fun IosShoppingPlanScreen(
    shoppingPlan: ShoppingPlan?,
    multiMatches: List<MultiMatch>,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
) {
    // Auto-trigger optimization if plan is null but multiMatches exist
    LaunchedEffect(shoppingPlan, multiMatches) {
        if (shoppingPlan == null && multiMatches.isNotEmpty()) {
            onOptimize()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Inline header with MTG PIRATE branding, stepper, and theme toggle
            IosInlineHeader(
                currentStep = 4,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
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

                if (isLoading || shoppingPlan == null) {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Optimizing shopping plan",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            AnimatedLoadingDots()
                        }
                    }
                } else {
                    // Grand total summary card
                    MobileShoppingPlanSummary(shoppingPlan)
                    Spacer(Modifier.height(12.dp))

                    // Per-seller order cards (scrollable)
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            shoppingPlan.orders,
                            key = { it.seller.name }
                        ) { order ->
                            MobileSellerOrderCard(
                                order = order,
                                onCopyToClipboard = onCopyToClipboard,
                                onOpenUrl = onOpenUrl,
                            )
                        }
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
 * Summary card showing total price and savings across all sellers.
 */
@Composable
private fun MobileShoppingPlanSummary(plan: ShoppingPlan) {
    PixelCard(glowing = true) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (plan.savingsVsSingleSeller > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Savings vs. single seller",
                        style = MaterialTheme.typography.body2,
                        color = PixelGreen
                    )
                    Text(
                        "- ${formatPrice(plan.savingsVsSingleSeller)}",
                        style = MaterialTheme.typography.body2,
                        color = PixelGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            PixelDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plan.orders.forEach { order ->
                    PixelBadge(
                        text = "${order.seller.displayName}: ${formatPrice(order.totalCents)}",
                        color = sellerColor(order.seller)
                    )
                }
            }
        }
    }
}

/**
 * Expandable card for a single seller's order (mobile-optimized).
 */
@Composable
private fun MobileSellerOrderCard(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = sellerColor(order.seller)
    val totalItems = order.items.sumOf { it.qty }

    PixelCard(glowing = false) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Seller header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelBadge(text = order.seller.displayName, color = color)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$totalItems cards",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    formatPrice(order.totalCents),
                    style = MaterialTheme.typography.h6,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Order details: subtotal, discount, shipping
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Subtotal",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        formatPrice(order.subtotalCents),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface
                    )
                }

                if (order.discountPercent > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Discount (${order.discountPercent}%)",
                            style = MaterialTheme.typography.body2,
                            color = PixelGreen
                        )
                        val discountAmount = order.subtotalCents * order.discountPercent / 100
                        Text(
                            "- ${formatPrice(discountAmount)}",
                            style = MaterialTheme.typography.body2,
                            color = PixelGreen
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Shipping",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        if (order.shippingCents == 0) "Free" else formatPrice(order.shippingCents),
                        style = MaterialTheme.typography.body2,
                        color = if (order.shippingCents == 0) PixelGreen
                        else MaterialTheme.colors.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            PixelDivider()
            Spacer(Modifier.height(8.dp))

            // Expand/collapse toggle for card list
            Row(
                Modifier.fillMaxWidth().clickable {
                    platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.LIGHT)
                    expanded = !expanded
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "Hide cards" else "Show cards ($totalItems)",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Expandable card list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Item list header
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "QTY",
                            Modifier.width(36.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "CARD",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "SET",
                            Modifier.width(44.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "PRICE",
                            Modifier.width(56.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    PixelDivider()

                    // Constrain item list height
                    val itemListState = rememberLazyListState()
                    Box(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        LazyColumn(
                            Modifier.fillMaxWidth(),
                            state = itemListState
                        ) {
                            items(
                                order.items,
                                key = { it.variant.uniqueIdentifier }
                            ) { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${item.qty}",
                                        Modifier.width(36.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        item.variant.nameOriginal,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.body2,
                                        maxLines = 1
                                    )
                                    Text(
                                        item.variant.setCode,
                                        Modifier.width(44.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        formatPrice(item.variant.priceInCents),
                                        Modifier.width(56.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                                PixelDivider()
                            }
                        }
                        LazyListScrollIndicators(
                            state = itemListState,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Per-seller action buttons
            MobileSellerActionButtons(
                order = order,
                onCopyToClipboard = onCopyToClipboard,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

/**
 * Per-seller action buttons for checkout and export (mobile-optimized).
 */
@Composable
private fun MobileSellerActionButtons(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (order.seller) {
            Seller.USEA -> {
                PixelButton(
                    text = "Copy CSV",
                    onClick = {
                        platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.MEDIUM)
                        val exportText = formatForExport(Seller.USEA, order.items)
                        onCopyToClipboard(exportText)
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
                PixelButton(
                    text = "Email",
                    onClick = {
                        platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.MEDIUM)
                        val exportText = formatForExport(Seller.USEA, order.items)
                        val subject = "MTG Proxy Order - ${order.items.sumOf { it.qty }} cards"
                        val mailtoUrl = "mailto:?subject=$subject&body=$exportText"
                        onOpenUrl(mailtoUrl)
                    },
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
            }
            Seller.BOOTLEG_MAGE -> {
                PixelButton(
                    text = "Open Deck Import",
                    onClick = {
                        platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.MEDIUM)
                        onOpenUrl(BOOTLEG_MAGE_DECK_IMPORT_URL)
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
            }
            Seller.TCGPLAYER -> {
                PixelButton(
                    text = "Mass Entry",
                    onClick = {
                        platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.MEDIUM)
                        onOpenUrl(TCGPLAYER_MASS_ENTRY_URL)
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
                PixelButton(
                    text = "Copy List",
                    onClick = {
                        platform.IosHapticFeedback.triggerImpact(platform.IosHapticFeedback.ImpactStyle.MEDIUM)
                        val exportText = formatForExport(Seller.TCGPLAYER, order.items)
                        onCopyToClipboard(exportText)
                    },
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f).height(40.dp)
                )
            }
        }
    }
}

/**
 * iOS Catalog Screen - View all catalog entries.
 */
@Composable
fun IosCatalogScreen(
    catalog: model.Catalog,
    onBack: () -> Unit,
    onEnrichVariant: ((model.CardVariant) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CatalogScreen(
            catalog = catalog,
            onClose = onBack,
            onEnrichVariant = onEnrichVariant
        )
    }
}

/**
 * iOS Matches Screen - View all matches.
 */
@Composable
fun IosMatchesScreen(
    matches: List<model.DeckEntryMatch>,
    onBack: () -> Unit,
    onEnrichVariant: ((model.CardVariant) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MatchesScreen(
            matches = matches,
            onClose = onBack,
            onEnrichVariant = onEnrichVariant
        )
    }
}
