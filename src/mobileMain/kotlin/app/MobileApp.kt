package app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.CatalogStore
import database.Database
import database.ImportsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import model.ProFeature
import model.ProStatus
import purchase.PurchaseManager
import state.MviPlatformServices
import state.MviViewModel
import state.ViewIntent
import ui.AppTheme
import ui.ProBadge
import ui.UpgradeDialog
import ui.PixelShape
import ui.SavedImportsDialog
import ui.ScanlineEffect
import ui.pixelBorder

/**
 * Mobile screen enum for navigation.
 */
enum class MobileScreen {
    IMPORT,
    PREFERENCES,
    RESULTS,
    RESOLVE,
    ALT_DETAIL,
    EXPORT,
}

/**
 * Shared mobile app composable used by both iOS and Android.
 * Accepts the database and platform services from the platform entry point
 * to ensure a single shared database instance.
 */
@Composable
fun MobileApp(
    database: Database,
    platformServices: MviPlatformServices,
    purchaseManager: PurchaseManager? = null,
    onOpenUseaCheckout: (itemsJson: String, couponCode: String) -> Unit = { _, _ -> },
) {
    // Create app-level coroutine scope
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Initialize stores from shared database
    val catalogStore = remember { CatalogStore(database) }
    val importsStore = remember { ImportsStore(database) }

    // Create MVI ViewModel
    val viewModel = remember {
        MviViewModel(
            scope = scope,
            database = database,
            catalogStore = catalogStore,
            importsStore = importsStore,
            platformServices = platformServices,
            purchaseManager = purchaseManager,
        )
    }

    // Clean up coroutine scope when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            scope.coroutineContext[Job]?.cancel()
        }
    }

    // Collect view state
    val state by viewModel.viewState.collectAsState()

    // Initialize on first launch
    LaunchedEffect(Unit) {
        viewModel.processIntent(ViewIntent.Init)
    }

    // Collect view effects for USEA checkout
    LaunchedEffect(Unit) {
        viewModel.viewEffects.collect { effect ->
            when (effect) {
                is state.ViewEffect.OpenUseaCheckout -> {
                    if (effect.unmatchedItems.isNotEmpty()) {
                        scope.launch {
                            platformServices.copyToClipboard(
                                ui.formatForGoogleSheet(effect.unmatchedItems)
                            )
                        }
                    }
                    onOpenUseaCheckout(effect.itemsJson, effect.couponCode)
                }
                else -> {} // Other effects handled elsewhere
            }
        }
    }

    // Apply pixel theme
    AppTheme(darkTheme = state.isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            // Main navigation with wizard flow
            MobileNavigationHost(
                viewModel = viewModel,
                state = state,
                scope = scope,
                platformServices = platformServices
            )
        }
    }
}

/**
 * Mobile navigation host managing wizard flow and screens.
 */
@Composable
fun MobileNavigationHost(
    viewModel: MviViewModel,
    state: state.ViewState,
    scope: CoroutineScope,
    platformServices: MviPlatformServices,
) {
    // Track current screen
    var currentScreen by remember { mutableStateOf(MobileScreen.IMPORT) }

    // Navigation helper
    fun navigateTo(screen: MobileScreen) {
        currentScreen = screen
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Background scanline effect
        ScanlineEffect(alpha = 0.02f)

        // Render current screen with crossfade animation to prevent jumping
        Crossfade(
            targetState = currentScreen,
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            when (screen) {
                MobileScreen.IMPORT -> MobileImportScreen(
                deckText = state.deckText,
                onDeckTextChange = { viewModel.processIntent(ViewIntent.UpdateDeckText(it)) },
                onNext = {
                    viewModel.processIntent(ViewIntent.WizardImportToPreferences)
                    navigateTo(MobileScreen.PREFERENCES)
                },
                onShowSavedImports = {
                    if (state.proStatus.isPro) {
                        viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(true))
                    } else {
                        viewModel.processIntent(ViewIntent.ShowUpgradePrompt(ProFeature.IMPORT_HISTORY))
                    }
                },
                isLoadingCatalog = state.loadingCatalog,
                isDarkTheme = state.isDarkTheme,
                onToggleTheme = { viewModel.processIntent(ViewIntent.ToggleTheme) },
                proStatus = state.proStatus,
            )

            MobileScreen.PREFERENCES -> MobilePreferencesScreen(
                includeSideboard = state.includeSideboard,
                includeCommanders = state.includeCommanders,
                includeTokens = state.includeTokens,
                variantPriority = state.preferences.variantPriority,
                onIncludeSideboardChange = { viewModel.processIntent(ViewIntent.ToggleIncludeSideboard(it)) },
                onIncludeCommandersChange = { viewModel.processIntent(ViewIntent.ToggleIncludeCommanders(it)) },
                onIncludeTokensChange = { viewModel.processIntent(ViewIntent.ToggleIncludeTokens(it)) },
                onVariantPriorityChange = { viewModel.processIntent(ViewIntent.UpdateVariantPriority(it)) },
                enabledSellers = state.preferences.enabledSellers,
                proxyFirst = state.preferences.proxyFirst,
                onEnabledSellersChange = { viewModel.processIntent(ViewIntent.UpdateEnabledSellers(it)) },
                onProxyFirstChange = { viewModel.processIntent(ViewIntent.UpdateProxyFirst(it)) },
                proStatus = state.proStatus,
                onShowUpgradePrompt = { feature -> viewModel.processIntent(ViewIntent.ShowUpgradePrompt(feature)) },
                onBack = { navigateTo(MobileScreen.IMPORT) },
                onNext = {
                    viewModel.processIntent(ViewIntent.WizardPreferencesToResults)
                    navigateTo(MobileScreen.RESULTS)
                },
                isDarkTheme = state.isDarkTheme,
                onToggleTheme = { viewModel.processIntent(ViewIntent.ToggleTheme) }
            )

            MobileScreen.RESULTS -> MobileResultsScreenWrapper(
                matches = state.matches,
                onResolve = { idx ->
                    viewModel.processIntent(ViewIntent.OpenResolve(idx))
                    navigateTo(MobileScreen.RESOLVE)
                },
                onBack = { navigateTo(MobileScreen.PREFERENCES) },
                onNext = {
                    viewModel.processIntent(ViewIntent.WizardResultsToExport)
                    navigateTo(MobileScreen.EXPORT)
                },
                onPrefetchImages = { variants ->
                    viewModel.processIntent(ViewIntent.PrefetchImages(variants))
                },
                isLoadingCatalog = state.loadingCatalog,
                isMatching = state.isMatching,
                searchProgress = state.searchProgress,
                matchedCount = state.matchedCount,
                unmatchedCount = state.unmatchedCount,
                ambiguousCount = state.ambiguousCount,
                isDarkTheme = state.isDarkTheme,
                onToggleTheme = { viewModel.processIntent(ViewIntent.ToggleTheme) },
                multiMatches = state.multiMatches,
                availableSellers = state.availableSellers,
                onOverrideSeller = { matchIndex, seller ->
                    viewModel.processIntent(ViewIntent.OverrideCardSeller(matchIndex, seller))
                },
                proStatus = state.proStatus,
                onShowUpgradePrompt = { feature ->
                    viewModel.processIntent(ViewIntent.ShowUpgradePrompt(feature))
                },
                onShowAltDetail = { idx ->
                    viewModel.processIntent(ViewIntent.OpenAltDetail(idx))
                    navigateTo(MobileScreen.ALT_DETAIL)
                },
            )

                MobileScreen.RESOLVE -> {
                    val index = state.showCandidatesFor
                    val match = index?.let { state.matches.getOrNull(it) }
                    if (index != null && match != null) {
                        MobileResolveScreen(
                            match = match,
                            onSelect = { variant ->
                                viewModel.processIntent(ViewIntent.ResolveCandidate(index, variant))
                                viewModel.processIntent(ViewIntent.CloseResolve)
                                navigateTo(MobileScreen.RESULTS)
                            },
                            onBack = {
                                viewModel.processIntent(ViewIntent.CloseResolve)
                                navigateTo(MobileScreen.RESULTS)
                            },
                            onPrefetchImages = { variants ->
                                viewModel.processIntent(ViewIntent.PrefetchImages(variants))
                            }
                        )
                    } else {
                        // If data is missing, show empty box to prevent flash
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                MobileScreen.ALT_DETAIL -> {
                    val altIdx = state.showAltDetailFor
                    val multiMatch = altIdx?.let { state.multiMatches.getOrNull(it) }
                    val deckMatch = multiMatch?.let { mm ->
                        state.matches.firstOrNull { it.deckEntry.id == mm.deckEntry.id }
                    }
                    if (altIdx != null && multiMatch != null && deckMatch != null) {
                        MobileAltDetailScreen(
                            match = deckMatch,
                            multiMatch = multiMatch,
                            onUseSeller = { seller ->
                                viewModel.processIntent(ViewIntent.OverrideCardSeller(altIdx, seller))
                                viewModel.processIntent(ViewIntent.CloseAltDetail)
                                navigateTo(MobileScreen.RESULTS)
                            },
                            onBack = {
                                viewModel.processIntent(ViewIntent.CloseAltDetail)
                                navigateTo(MobileScreen.RESULTS)
                            },
                            onPrefetchImages = { variants ->
                                viewModel.processIntent(ViewIntent.PrefetchImages(variants))
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                MobileScreen.EXPORT -> MobileShoppingPlanScreen(
                    shoppingPlanComparison = state.shoppingPlanComparison,
                    multiMatches = state.multiMatches,
                    isPro = state.proStatus.isPro,
                    onOptimize = {
                        viewModel.processIntent(ViewIntent.OptimizeShoppingPlan)
                    },
                    onCopyToClipboard = { text ->
                        scope.launch { platformServices.copyToClipboard(text) }
                    },
                    onOpenUrl = { url ->
                        scope.launch { platformServices.openUrl(url) }
                    },
                    onCheckoutUsea = { order ->
                        viewModel.processIntent(ViewIntent.CheckoutUsea(order))
                    },
                    onBack = { navigateTo(MobileScreen.RESULTS) },
                    onUpgrade = { viewModel.processIntent(ViewIntent.PurchasePro) },
                    isLoading = state.isMatching,
                    isDarkTheme = state.isDarkTheme,
                    onToggleTheme = { viewModel.processIntent(ViewIntent.ToggleTheme) },
                    proStatus = state.proStatus,
                )
            }
        }

        // Saved imports dialog
        if (state.showSavedImportsWindow) {
            SavedImportsDialog(
                savedImports = state.savedImports,
                onDismiss = {
                    viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(false))
                },
                onSelectImport = { importId ->
                    viewModel.processIntent(ViewIntent.LoadSavedImport(importId))
                    navigateTo(MobileScreen.IMPORT)
                },
                onDeleteImport = { importId ->
                    viewModel.processIntent(ViewIntent.DeleteSavedImport(importId))
                }
            )
        }

        // Upgrade prompt dialog
        state.showUpgradePrompt?.let { feature ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
                contentAlignment = Alignment.Center,
            ) {
                UpgradeDialog(
                    feature = feature,
                    onPurchase = { viewModel.processIntent(ViewIntent.PurchasePro) },
                    onRestore = { viewModel.processIntent(ViewIntent.RestorePurchases) },
                    onDismiss = { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
                )
            }
        }
    }
}

/**
 * Inline header combining DECK LOOT branding, stepper, and theme toggle.
 * Designed for compact mobile layout with all elements in one row.
 */
@Composable
fun MobileInlineHeader(
    currentStep: Int,
    totalSteps: Int = 4,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    proStatus: ProStatus = ProStatus.Free,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DECK LOOT branding on the left
        Text(
            text = "DECK LOOT",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Compact stepper in the center
        CompactStepper(
            currentStep = currentStep,
            totalSteps = totalSteps,
            modifier = Modifier.weight(1f)
        )

        // Smaller inline theme toggle on the right with Pro badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            if (!proStatus.isPro) {
                ProBadge()
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                    .background(MaterialTheme.colors.primary, shape = PixelShape(cornerSize = 6.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleTheme
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDarkTheme) "☀" else "☾",
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}
