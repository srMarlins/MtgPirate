package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import state.ViewState
import ui.*

/**
 * Main iOS app entry point with MVI architecture and pixel design.
 * Initializes the app dependencies and renders the navigation host.
 */
@Composable
fun IosApp() {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val database = remember { Database(DatabaseDriverFactory()) }
    val catalogStore = remember { CatalogStore(database) }
    val importsStore = remember { ImportsStore(database) }
    val platformServices = remember { IosMviPlatformServices(database) }
    
    val viewModel = remember {
        MviViewModel(
            scope = scope,
            database = database,
            catalogStore = catalogStore,
            importsStore = importsStore,
            platformServices = platformServices
        )
    }
    
    val state by viewModel.viewState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.processIntent(ViewIntent.Init)
    }
    
    AppTheme(darkTheme = state.isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            iosNavigationHost(viewModel = viewModel, state = state)
        }
    }
}

/**
 * iOS navigation host managing wizard flow and screen navigation.
 */
@Composable
fun iosNavigationHost(
    viewModel: MviViewModel,
    state: ViewState
) {
    var currentScreen by remember { mutableStateOf(IosScreen.IMPORT) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.02f)
        
        renderScreen(
            currentScreen = currentScreen,
            state = state,
            viewModel = viewModel,
            onNavigate = { currentScreen = it }
        )
        
        IosBottomNavBar(
            currentScreen = currentScreen,
            onNavigate = { currentScreen = it },
            hasCatalog = state.catalog != null,
            hasMatches = state.matches.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        IosThemeToggleFab(
            isDarkTheme = state.isDarkTheme,
            onToggle = { viewModel.processIntent(ViewIntent.ToggleTheme) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )
        
        if (state.showSavedImportsWindow) {
            SavedImportsDialog(
                savedImports = state.savedImports,
                onDismiss = {
                    viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(false))
                },
                onSelectImport = { importId ->
                    viewModel.processIntent(ViewIntent.LoadSavedImport(importId))
                    currentScreen = IosScreen.IMPORT
                },
                onDeleteImport = { importId ->
                    viewModel.processIntent(ViewIntent.DeleteSavedImport(importId))
                }
            )
        }
    }
}

/**
 * Render the current screen based on navigation state.
 */
@Composable
private fun renderScreen(
    currentScreen: IosScreen,
    state: ViewState,
    viewModel: MviViewModel,
    onNavigate: (IosScreen) -> Unit
) {
    when (currentScreen) {
        IosScreen.IMPORT -> renderImportScreen(state, viewModel, onNavigate)
        IosScreen.PREFERENCES -> renderPreferencesScreen(state, viewModel, onNavigate)
        IosScreen.RESULTS -> renderResultsScreen(state, viewModel, onNavigate)
        IosScreen.RESOLVE -> renderResolveScreen(state, viewModel, onNavigate)
        IosScreen.EXPORT -> renderExportScreen(state, viewModel, onNavigate)
        IosScreen.CATALOG -> renderCatalogScreen(state, onNavigate)
        IosScreen.MATCHES -> renderMatchesScreen(state, onNavigate)
    }
}

@Composable
private fun renderImportScreen(state: ViewState, viewModel: MviViewModel, onNavigate: (IosScreen) -> Unit) {
    importScreen(
        state = state,
        onNext = {
            viewModel.processIntent(ViewIntent.ParseDeck)
            viewModel.processIntent(ViewIntent.CompleteWizardStep(1))
            onNavigate(IosScreen.PREFERENCES)
        },
        onShowSavedImports = {
            viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(true))
        },
        onDeckTextChange = { viewModel.processIntent(ViewIntent.UpdateDeckText(it)) }
    )
}

@Composable
private fun renderPreferencesScreen(state: ViewState, viewModel: MviViewModel, onNavigate: (IosScreen) -> Unit) {
    preferencesScreen(
        state = state,
        viewModel = viewModel,
        onBack = { onNavigate(IosScreen.IMPORT) },
        onNext = {
            viewModel.processIntent(ViewIntent.CompleteWizardStep(2))
            viewModel.processIntent(ViewIntent.RunMatch)
            onNavigate(IosScreen.RESULTS)
        }
    )
}

@Composable
private fun renderResultsScreen(state: ViewState, viewModel: MviViewModel, onNavigate: (IosScreen) -> Unit) {
    resultsScreen(
        state = state,
        onResolve = { idx ->
            viewModel.processIntent(ViewIntent.OpenResolve(idx))
            onNavigate(IosScreen.RESOLVE)
        },
        onBack = { onNavigate(IosScreen.PREFERENCES) },
        onNext = {
            viewModel.processIntent(ViewIntent.CompleteWizardStep(3))
            onNavigate(IosScreen.EXPORT)
        }
    )
}

@Composable
private fun renderResolveScreen(state: ViewState, viewModel: MviViewModel, onNavigate: (IosScreen) -> Unit) {
    val index = state.showCandidatesFor
    val match = index?.let { state.matches.getOrNull(it) }
    if (index != null && match != null) {
        IosResolveScreen(
            match = match,
            onSelect = { variant ->
                viewModel.processIntent(ViewIntent.ResolveCandidate(index, variant))
                viewModel.processIntent(ViewIntent.CloseResolve)
                onNavigate(IosScreen.RESULTS)
            },
            onBack = {
                viewModel.processIntent(ViewIntent.CloseResolve)
                onNavigate(IosScreen.RESULTS)
            }
        )
    } else {
        onNavigate(IosScreen.RESULTS)
    }
}

@Composable
private fun renderExportScreen(state: ViewState, viewModel: MviViewModel, onNavigate: (IosScreen) -> Unit) {
    exportScreen(
        state = state,
        onBack = { onNavigate(IosScreen.RESULTS) },
        onExport = {
            viewModel.processIntent(ViewIntent.ExportCsv)
            viewModel.processIntent(ViewIntent.CompleteWizardStep(4))
        }
    )
}

@Composable
private fun renderCatalogScreen(state: ViewState, onNavigate: (IosScreen) -> Unit) {
    state.catalog?.let { catalog ->
        IosCatalogScreen(
            catalog = catalog,
            onBack = { onNavigate(IosScreen.IMPORT) }
        )
    } ?: run { onNavigate(IosScreen.IMPORT) }
}

@Composable
private fun renderMatchesScreen(state: ViewState, onNavigate: (IosScreen) -> Unit) {
    IosMatchesScreen(
        matches = state.matches,
        onBack = { onNavigate(IosScreen.RESULTS) }
    )
}

/**
 * Helper composable for import screen.
 */
@Composable
private fun importScreen(
    state: ViewState,
    onNext: () -> Unit,
    onShowSavedImports: () -> Unit,
    onDeckTextChange: (String) -> Unit
) {
    IosImportScreen(
        deckText = state.deckText,
        onDeckTextChange = onDeckTextChange,
        onNext = onNext,
        onShowSavedImports = onShowSavedImports
    )
}

/**
 * Helper composable for preferences screen.
 */
@Composable
private fun preferencesScreen(
    state: ViewState,
    viewModel: MviViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    IosPreferencesScreen(
        includeSideboard = state.includeSideboard,
        includeCommanders = state.includeCommanders,
        includeTokens = state.includeTokens,
        variantPriority = state.preferences.variantPriority,
        onIncludeSideboardChange = { viewModel.processIntent(ViewIntent.ToggleIncludeSideboard(it)) },
        onIncludeCommandersChange = { viewModel.processIntent(ViewIntent.ToggleIncludeCommanders(it)) },
        onIncludeTokensChange = { viewModel.processIntent(ViewIntent.ToggleIncludeTokens(it)) },
        onVariantPriorityChange = { viewModel.processIntent(ViewIntent.UpdateVariantPriority(it)) },
        onBack = onBack,
        onNext = onNext
    )
}

/**
 * Helper composable for results screen.
 */
@Composable
private fun resultsScreen(
    state: ViewState,
    onResolve: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    IosResultsScreen(
        matches = state.matches,
        onResolve = onResolve,
        onBack = onBack,
        onNext = onNext
    )
}

/**
 * Helper composable for export screen.
 */
@Composable
private fun exportScreen(
    state: ViewState,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    IosExportScreen(
        matches = state.matches,
        onBack = onBack,
        onExport = onExport
    )
}

/**
 * iOS screen navigation destinations.
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
