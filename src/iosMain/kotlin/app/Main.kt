package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.CatalogStore
import database.Database
import database.DatabaseDriverFactory
import database.ImportsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.IosMviPlatformServices
import state.MviViewModel
import state.ViewIntent
import ui.*

/**
 * Main iOS app composable with MVI architecture and pixel design.
 */
@Composable
fun IosApp() {
    // Create app-level coroutine scope
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Initialize database and stores
    val database = remember { Database(DatabaseDriverFactory()) }
    val catalogStore = remember { CatalogStore(database) }
    val importsStore = remember { ImportsStore(database) }
    val platformServices = remember { IosMviPlatformServices(database) }

    // Create MVI ViewModel
    val viewModel = remember {
        MviViewModel(
            scope = scope,
            database = database,
            catalogStore = catalogStore,
            importsStore = importsStore,
            platformServices = platformServices
        )
    }

    // Collect view state
    val state by viewModel.viewState.collectAsState()

    // Initialize on first launch
    LaunchedEffect(Unit) {
        viewModel.processIntent(ViewIntent.Init)
    }

    // Apply pixel theme
    AppTheme(darkTheme = state.isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            // Main navigation with wizard flow
            IosNavigationHost(
                viewModel = viewModel,
                state = state
            )
        }
    }
}

/**
 * iOS navigation host managing wizard flow and screens.
 */
@Composable
fun IosNavigationHost(
    viewModel: MviViewModel,
    state: state.ViewState
) {
    // Track current screen
    var currentScreen by remember { mutableStateOf(IosScreen.IMPORT) }
    
    // Navigation helper
    fun navigateTo(screen: IosScreen) {
        currentScreen = screen
    }
    
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Background scanline effect
        ScanlineEffect(alpha = 0.02f)
        
        // Render current screen
        when (currentScreen) {
            IosScreen.IMPORT -> IosImportScreen(
                deckText = state.deckText,
                onDeckTextChange = { viewModel.processIntent(ViewIntent.UpdateDeckText(it)) },
                onNext = {
                    viewModel.processIntent(ViewIntent.ParseDeck)
                    viewModel.processIntent(ViewIntent.CompleteWizardStep(1))
                    navigateTo(IosScreen.PREFERENCES)
                },
                onShowSavedImports = {
                    viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(true))
                }
            )
            
            IosScreen.PREFERENCES -> IosPreferencesScreen(
                includeSideboard = state.preferences.includeSideboard,
                includeCommanders = state.preferences.includeCommanders,
                includeTokens = state.preferences.includeTokens,
                variantPriority = state.preferences.variantPriority,
                onIncludeSideboardChange = { viewModel.processIntent(ViewIntent.ToggleIncludeSideboard(it)) },
                onIncludeCommandersChange = { viewModel.processIntent(ViewIntent.ToggleIncludeCommanders(it)) },
                onIncludeTokensChange = { viewModel.processIntent(ViewIntent.ToggleIncludeTokens(it)) },
                onVariantPriorityChange = { viewModel.processIntent(ViewIntent.UpdateVariantPriority(it)) },
                onBack = { navigateTo(IosScreen.IMPORT) },
                onNext = {
                    viewModel.processIntent(ViewIntent.CompleteWizardStep(2))
                    viewModel.processIntent(ViewIntent.RunMatch)
                    navigateTo(IosScreen.RESULTS)
                }
            )
            
            IosScreen.RESULTS -> IosResultsScreen(
                matches = state.matches,
                onResolve = { idx ->
                    viewModel.processIntent(ViewIntent.OpenResolve(idx))
                    navigateTo(IosScreen.RESOLVE)
                },
                onBack = { navigateTo(IosScreen.PREFERENCES) },
                onNext = {
                    viewModel.processIntent(ViewIntent.CompleteWizardStep(3))
                    navigateTo(IosScreen.EXPORT)
                },
                onEnrichVariant = { variant ->
                    viewModel.processIntent(ViewIntent.EnrichVariantWithImage(variant))
                }
            )
            
            IosScreen.RESOLVE -> {
                val index = state.showCandidatesFor
                val match = index?.let { state.matches.getOrNull(it) }
                if (index != null && match != null) {
                    IosResolveScreen(
                        match = match,
                        onSelect = { variant ->
                            viewModel.processIntent(ViewIntent.ResolveCandidate(index, variant))
                            viewModel.processIntent(ViewIntent.CloseResolve)
                            navigateTo(IosScreen.RESULTS)
                        },
                        onBack = {
                            viewModel.processIntent(ViewIntent.CloseResolve)
                            navigateTo(IosScreen.RESULTS)
                        },
                        onEnrichVariant = { variant ->
                            viewModel.processIntent(ViewIntent.EnrichVariantWithImage(variant))
                        }
                    )
                } else {
                    navigateTo(IosScreen.RESULTS)
                }
            }
            
            IosScreen.EXPORT -> IosExportScreen(
                matches = state.matches,
                onBack = { navigateTo(IosScreen.RESULTS) },
                onExport = {
                    viewModel.processIntent(ViewIntent.ExportCsv)
                    viewModel.processIntent(ViewIntent.CompleteWizardStep(4))
                }
            )
            
            IosScreen.CATALOG -> {
                val catalog = state.catalog
                if (catalog != null) {
                    IosCatalogScreen(
                        catalog = catalog,
                        onBack = { navigateTo(IosScreen.IMPORT) },
                        onEnrichVariant = { variant ->
                            viewModel.processIntent(ViewIntent.EnrichVariantWithImage(variant))
                        }
                    )
                } else {
                    navigateTo(IosScreen.IMPORT)
                }
            }
            
            IosScreen.MATCHES -> IosMatchesScreen(
                matches = state.matches,
                onBack = { navigateTo(IosScreen.RESULTS) },
                onEnrichVariant = { variant ->
                    viewModel.processIntent(ViewIntent.EnrichVariantWithImage(variant))
                }
            )
        }
        
        // Theme toggle floating action button
        IosThemeToggleFab(
            isDarkTheme = state.isDarkTheme,
            onToggle = { viewModel.processIntent(ViewIntent.ToggleTheme) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        
        // Saved imports dialog
        if (state.showSavedImportsWindow) {
            SavedImportsDialog(
                savedImports = state.savedImports,
                onDismiss = {
                    viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(false))
                },
                onSelectImport = { importId ->
                    viewModel.processIntent(ViewIntent.LoadSavedImport(importId))
                    navigateTo(IosScreen.IMPORT)
                },
                onDeleteImport = { importId ->
                    viewModel.processIntent(ViewIntent.DeleteSavedImport(importId))
                }
            )
        }
    }
}

/**
 * iOS screen enum for navigation.
 */
enum class IosScreen {
    IMPORT,
    PREFERENCES,
    RESULTS,
    RESOLVE,
    EXPORT,
    CATALOG,
    MATCHES
}

/**
 * iOS bottom navigation bar with pixel styling.
 */
@Composable
fun IosBottomNavBar(
    currentScreen: IosScreen,
    onNavigate: (IosScreen) -> Unit,
    hasCatalog: Boolean,
    hasMatches: Boolean,
    modifier: Modifier = Modifier
) {
    PixelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IosNavButton(
                text = "Import",
                isActive = currentScreen == IosScreen.IMPORT,
                onClick = { onNavigate(IosScreen.IMPORT) }
            )
            
            IosNavButton(
                text = "Catalog",
                isActive = currentScreen == IosScreen.CATALOG,
                enabled = hasCatalog,
                onClick = { onNavigate(IosScreen.CATALOG) }
            )
            
            IosNavButton(
                text = "Matches",
                isActive = currentScreen == IosScreen.MATCHES,
                enabled = hasMatches,
                onClick = { onNavigate(IosScreen.MATCHES) }
            )
        }
    }
}

/**
 * iOS navigation button for bottom bar.
 */
@Composable
fun RowScope.IosNavButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    PixelButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        variant = if (isActive) PixelButtonVariant.PRIMARY else PixelButtonVariant.SURFACE,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
    )
}

/**
 * iOS theme toggle floating action button with pixel styling.
 */
@Composable
fun IosThemeToggleFab(
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.5f)
            .background(MaterialTheme.colors.primary, shape = PixelShape(cornerSize = 9.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDarkTheme) "☀" else "☾",
            fontSize = 24.sp,
            color = MaterialTheme.colors.onPrimary
        )
    }
}

/**
 * Entry point for iOS app to create the main UIViewController.
 * Call this function from Swift to launch the Compose UI.
 */
fun MainViewController() = androidx.compose.ui.window.ComposeUIViewController {
    IosApp()
}

