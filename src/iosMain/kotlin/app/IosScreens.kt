package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.*

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
    isLoadingCatalog: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect overlay
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Compact stepper for mobile
            CompactStepper(
                currentStep = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

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
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Compact stepper for mobile
            CompactStepper(
                currentStep = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

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
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.2f)
                        .background(
                            MaterialTheme.colors.surface.copy(alpha = 0.5f),
                            shape = PixelShape(cornerSize = 6.dp)
                        )
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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact stepper at the top with safe padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CompactStepper(
                    currentStep = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
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
                    ambiguousCount = ambiguousCount
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
                .safeDrawingPadding()
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
                            color = Color(0xFFF44336),
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
                            items(sorted) { cand: model.MatchCandidate ->
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
                                        .pixelBorder(
                                            borderWidth = 2.dp,
                                            enabled = true,
                                            glowAlpha = 0.2f
                                        )
                                        .background(
                                            MaterialTheme.colors.surface.copy(alpha = 0.5f),
                                            shape = PixelShape(cornerSize = 6.dp)
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
                                                        text = variant.variantType,
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
                                                    .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
                                                    .background(MaterialTheme.colors.secondary, shape = PixelShape(cornerSize = 6.dp))
                                                    .clickable {
                                                        platform.IosHapticFeedback.triggerImpact(
                                                            platform.IosHapticFeedback.ImpactStyle.MEDIUM
                                                        )
                                                        onSelect(variant)
                                                    },
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
                                                variantType = variant.variantType,
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
 * iOS Export Screen - Step 4 of the wizard.
 * Mobile-optimized single-column layout for portrait screens.
 */
@Composable
fun IosExportScreen(
    matches: List<model.DeckEntryMatch>,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val resolved = matches.filter { it.selectedVariant != null }
    val unresolved = matches.filter { 
        it.selectedVariant == null && it.deckEntry.include 
    }
    val ambiguousCount = matches.count { 
        it.status == model.MatchStatus.AMBIGUOUS 
    }

    val promo = util.Promotions.calculate(matches)

    // Shipping selection state
    var selectedShipping by remember { 
        mutableStateOf(promo.shippingType) 
    }
    val expressEligible = promo.subtotalAfterDiscountCents > 300_00
    
    // Coerce selection if express becomes ineligible
    LaunchedEffect(expressEligible) {
        if (!expressEligible) {
            selectedShipping = util.Promotions.ShippingType.NORMAL
        }
    }

    val normalShippingCost = if (promo.subtotalAfterDiscountCents > 100_00) 0 else 10_00
    val selectedShippingCost = calculateShippingCost(
        selectedShipping,
        expressEligible,
        normalShippingCost
    )
    val grandTotal = promo.subtotalAfterDiscountCents + selectedShippingCost

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Compact stepper for mobile
            CompactStepper(
                currentStep = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Header
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "▸ EXPORT",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "└─ Review totals and export CSV",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Ambiguous guard
            if (ambiguousCount > 0) {
                PixelCard(glowing = true) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelBadge(text = "⚠", color = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$ambiguousCount ambiguous cards - please resolve them first",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Scrollable content area
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Card
                item {
                    ExportSummaryCard(resolved, unresolved)
                }

                // Pricing Card
                item {
                    ExportPricingCard(promo)
                }

                // Shipping Card
                item {
                    ExportShippingCard(
                        selectedShipping = selectedShipping,
                        onShippingChange = { selectedShipping = it },
                        normalShippingCost = normalShippingCost,
                        expressEligible = expressEligible
                    )
                }

                // Grand Total Card
                item {
                    PixelCard(
                        glowing = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Grand Total:",
                                style = MaterialTheme.typography.h6,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                            Text(
                                util.formatPrice(grandTotal),
                                style = MaterialTheme.typography.h5,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.secondary
                            )
                        }
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
                    text = "Export CSV",
                    onClick = onExport,
                    enabled = ambiguousCount == 0 && resolved.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp),
                    variant = PixelButtonVariant.SECONDARY
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
