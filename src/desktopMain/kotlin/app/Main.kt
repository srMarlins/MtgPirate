package app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import platform.DesktopPlatformServices
import state.MainIntent
import state.MainStore
import ui.*
import java.awt.Cursor

/**
 * Unified custom title bar with window controls and app navigation
 */
@Composable
fun FrameWindowScope.CustomTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    loadingCatalog: Boolean,
    hasCatalog: Boolean,
    hasMatches: Boolean,
    onCatalogClick: () -> Unit,
    onExportClick: () -> Unit,
    onMatchesClick: () -> Unit,
    onResultsClick: () -> Unit
) {
    WindowDraggableArea {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            if (isDarkTheme) Color(0xFF1E293B) else Color(0xFF475569),
                            if (isDarkTheme) Color(0xFF334155) else Color(0xFF64748B)
                        )
                    )
                )
                .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Logo and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "█▓▒░",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        "MTG PIRATE",
                        style = MaterialTheme.typography.subtitle1,
                        color = if (isDarkTheme) Color(0xFFF1F5F9) else Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "░▒▓█",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.secondary
                    )
                }

                // Center - Navigation buttons (compact)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactPixelButton(
                        text = if (loadingCatalog) "..." else "CATALOG",
                        onClick = onCatalogClick,
                        enabled = !loadingCatalog,
                        color = MaterialTheme.colors.primary
                    )

                    CompactPixelButton(
                        text = "EXPORT",
                        onClick = onExportClick,
                        enabled = hasMatches,
                        color = MaterialTheme.colors.secondary
                    )

                    CompactPixelButton(
                        text = "MATCHES",
                        onClick = onMatchesClick,
                        enabled = hasMatches,
                        color = Color(0xFF8B5CF6)
                    )

                    CompactPixelButton(
                        text = "RESULTS",
                        onClick = onResultsClick,
                        enabled = hasMatches,
                        color = Color(0xFF06B6D4)
                    )
                }

                // Right side - Theme toggle and Window Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Theme toggle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (isDarkTheme) Color(0xFF475569) else Color(0xFF94A3B8),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onToggleTheme() }
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isDarkTheme) "☀" else "☾",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    // Minimize button
                    WindowControlButton(
                        text = "—",
                        color = Color(0xFFFBBF24),
                        onClick = { windowState.isMinimized = true }
                    )

                    // Maximize/Restore button
                    WindowControlButton(
                        text = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                        color = Color(0xFF10B981),
                        onClick = {
                            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                        }
                    )

                    // Close button
                    WindowControlButton(
                        text = "✕",
                        color = Color(0xFFEF4444),
                        onClick = onClose
                    )
                }
            }
        }
    }
}

/**
 * Compact pixel-styled navigation button for title bar
 */
@Composable
fun CompactPixelButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .height(32.dp)
            .background(
                if (enabled && isHovered) color else color.copy(alpha = if (enabled) 0.6f else 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .hoverable(interactionSource = interactionSource)
            .clickable(enabled = enabled) { onClick() }
            .pointerHoverIcon(
                if (enabled) PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                else PointerIcon(Cursor.getDefaultCursor())
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Pixel-styled window control button
 */
@Composable
fun WindowControlButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                if (isHovered) color else color.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            )
            .hoverable(interactionSource = interactionSource)
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

fun main() = application {
    val scope = rememberCoroutineScope()
    val platformServices = remember { DesktopPlatformServices() }
    val store = remember { MainStore(scope, platformServices) }
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) { store.dispatch(MainIntent.Init) }

    // Load scimitar logo as window icon
    val windowIcon = remember {
        loadSvgPainter(
            inputStream = object {}.javaClass.getResourceAsStream("/scimitar_logo.svg")
                ?: throw IllegalStateException("scimitar_logo.svg not found in resources"),
            density = Density(1f)
        )
    }

    val windowState = rememberWindowState(width = 1200.dp, height = 900.dp)


    // Define wizard steps based on state
    val wizardSteps = remember(state.wizardCompletedSteps, state.app.deckEntries.isNotEmpty(), state.app.matches.isNotEmpty()) {
        listOf(
            Step(
                number = 1,
                title = "Import Deck",
                description = "Paste your decklist",
                state = when {
                    state.wizardCompletedSteps.contains(1) -> StepState.COMPLETED
                    state.wizardCompletedSteps.isEmpty() -> StepState.ACTIVE
                    else -> StepState.LOCKED
                }
            ),
            Step(
                number = 2,
                title = "Configure",
                description = "Set preferences",
                state = when {
                    state.wizardCompletedSteps.contains(2) -> StepState.COMPLETED
                    state.wizardCompletedSteps.contains(1) -> StepState.ACTIVE
                    else -> StepState.LOCKED
                }
            ),
            Step(
                number = 3,
                title = "Review Results",
                description = "Select cards & export",
                state = when {
                    state.wizardCompletedSteps.contains(3) -> StepState.COMPLETED
                    state.wizardCompletedSteps.contains(2) -> StepState.ACTIVE
                    else -> StepState.LOCKED
                }
            )
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "MTG Pirate",
        state = windowState,
        icon = windowIcon,
        undecorated = true
    ) {
        AppTheme(darkTheme = state.isDarkTheme) {
            val navController = rememberNavController()

            Column(modifier = Modifier.fillMaxSize()) {
                // Custom Title Bar with window controls and navigation
                CustomTitleBar(
                    windowState = windowState,
                    onClose = ::exitApplication,
                    isDarkTheme = state.isDarkTheme,
                    onToggleTheme = { store.dispatch(MainIntent.ToggleTheme) },
                    loadingCatalog = state.loadingCatalog,
                    hasCatalog = state.app.catalog != null,
                    hasMatches = state.app.matches.any { it.selectedVariant != null },
                    onCatalogClick = {
                        if (state.app.catalog != null) {
                            if (navController.currentDestination?.route != "catalog") {
                                navController.navigate("catalog") {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        } else {
                            store.dispatch(MainIntent.LoadCatalog(true))
                        }
                    },
                    onExportClick = { store.dispatch(MainIntent.ExportCsv) },
                    onMatchesClick = {
                        if (navController.currentDestination?.route != "matches") {
                            navController.navigate("matches") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    },
                    onResultsClick = {
                        if (navController.currentDestination?.route != "results") {
                            navController.navigate("results") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    }
                )

                // Main content
                Box(modifier = Modifier.fillMaxSize()) {

            // Track current route to determine active step with stability
            val currentBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry)
            val currentRoute by remember { derivedStateOf { currentBackStackEntry?.destination?.route } }
            val currentStep = remember(currentRoute) {
                when (currentRoute) {
                    "import" -> 1
                    "preferences" -> 2
                    "results" -> 3
                    else -> 1
                }
            }

            Scaffold(
                topBar = {
                    // Show stepper only for wizard routes
                    if (currentRoute in listOf("import", "preferences", "results")) {
                        Column {
                            AnimatedStepper(
                                steps = wizardSteps,
                                currentStep = currentStep,
                                onStepClick = { step ->
                                    when (step) {
                                        1 -> navController.navigate("import") {
                                            popUpTo("import") { inclusive = false }
                                        }
                                        2 -> if (state.wizardCompletedSteps.contains(1)) {
                                            navController.navigate("preferences") {
                                                popUpTo("import") { inclusive = false }
                                            }
                                        }
                                        3 -> if (state.wizardCompletedSteps.contains(2)) {
                                            navController.navigate("results") {
                                                popUpTo("import") { inclusive = false }
                                            }
                                        }
                                    }
                                }
                            )
                            PixelDivider(animated = true, thickness = 3.dp)
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background scanline effect for entire app
                    ScanlineEffect(alpha = 0.02f)

                    NavHost(
                        navController = navController,
                        startDestination = "import",
                        modifier = Modifier.fillMaxSize().padding(padding),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                    composable(
                        "import",
                        enterTransition = {
                            when (initialState.destination.route) {
                                "preferences" -> slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(400))
                                else -> EnterTransition.None
                            }
                        },
                        exitTransition = {
                            when (targetState.destination.route) {
                                "preferences" -> slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400))
                                else -> ExitTransition.None
                            }
                        }
                    ) {
                        // Step 1: Deck Import (Wizard Start)
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Scanline effect overlay
                            ScanlineEffect(alpha = 0.03f)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Title with pixel styling - compact layout
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "▸ DECK IMPORT",
                                        style = MaterialTheme.typography.h4,
                                        color = MaterialTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    PixelBadge(
                                        text = "STEP 1/3",
                                        color = MaterialTheme.colors.secondary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    BlinkingCursor()
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    "└─ Paste your decklist below. Format: [quantity] [cardname]",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )

                                Spacer(Modifier.height(16.dp))

                                PixelCard(glowing = state.deckText.isBlank()) {
                                    PixelTextField(
                                        value = state.deckText,
                                        onValueChange = { store.dispatch(MainIntent.UpdateDeckText(it)) },
                                        label = "DECKLIST.TXT",
                                        placeholder = "4 Lightning Bolt\n2 Brainstorm\n1 Black Lotus",
                                        modifier = Modifier.fillMaxWidth().height(400.dp)
                                    )
                                }

                                Spacer(Modifier.height(24.dp))

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    PixelButton(
                                        text = "📚 View Saved Imports",
                                        onClick = {
                                            store.dispatch(MainIntent.SetShowSavedImportsWindow(true))
                                        },
                                        modifier = Modifier.weight(1f),
                                        variant = PixelButtonVariant.SURFACE
                                    )

                                    PixelButton(
                                        text = "Next: Configure →",
                                        onClick = {
                                            // Auto-save the import (dedup happens in saveCurrentImport)
                                            val autoName = "" // Name will be auto-generated from commander or timestamp
                                            store.dispatch(MainIntent.SaveCurrentImport(autoName))

                                            store.dispatch(MainIntent.ParseDeck)
                                            store.dispatch(MainIntent.CompleteWizardStep(1))
                                            navController.navigate("preferences") {
                                                launchSingleTop = true
                                            }
                                        },
                                        enabled = state.deckText.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        variant = PixelButtonVariant.SECONDARY
                                    )
                                }
                            }
                        }
                    }
                    composable(
                        "preferences",
                        enterTransition = {
                            when (initialState.destination.route) {
                                "import" -> slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(400))
                                "results" -> slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(400))
                                else -> EnterTransition.None
                            }
                        },
                        exitTransition = {
                            when (targetState.destination.route) {
                                "import" -> slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400))
                                "results" -> slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400))
                                else -> ExitTransition.None
                            }
                        }
                    ) {
                        // Step 2: Preferences (Wizard Middle)
                        PreferencesWizardScreen(
                            includeSideboard = state.includeSideboard,
                            includeCommanders = state.includeCommanders,
                            includeTokens = state.includeTokens,
                            variantPriority = state.app.preferences.variantPriority,
                            onIncludeSideboardChange = { store.dispatch(MainIntent.ToggleIncludeSideboard(it)) },
                            onIncludeCommandersChange = { store.dispatch(MainIntent.ToggleIncludeCommanders(it)) },
                            onIncludeTokensChange = { store.dispatch(MainIntent.ToggleIncludeTokens(it)) },
                            onVariantPriorityChange = { store.dispatch(MainIntent.UpdateVariantPriority(it)) },
                            onBack = { navController.navigateUp() },
                            onNext = {
                                store.dispatch(MainIntent.CompleteWizardStep(2))
                                store.dispatch(MainIntent.RunMatch)
                                navController.navigate("results") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(
                        "results",
                        enterTransition = {
                            when (initialState.destination.route) {
                                "preferences" -> slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(400))
                                "resolve" -> EnterTransition.None
                                else -> EnterTransition.None
                            }
                        },
                        exitTransition = {
                            when (targetState.destination.route) {
                                "preferences" -> slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400))
                                "resolve" -> ExitTransition.None
                                else -> ExitTransition.None
                            }
                        }
                    ) {
                        // Step 3: Results (Wizard End)
                        ResultsScreen(
                            matches = state.app.matches,
                            onResolve = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve") {
                                    launchSingleTop = true
                                }
                            },
                            onShowAllCandidates = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve") {
                                    launchSingleTop = true
                                }
                            },
                            onClose = { navController.navigateUp() },
                            onExport = { store.dispatch(MainIntent.ExportWizardResults) }
                        )
                    }
                    composable(
                        "catalog",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {
                        val catalog = state.app.catalog
                        if (catalog != null) {
                            CatalogScreen(catalog = catalog) { navController.navigateUp() }
                        } else {
                            Text("No catalog loaded.", modifier = Modifier.padding(16.dp))
                        }
                    }
                    composable(
                        "matches",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {
                        MatchesScreen(matches = state.app.matches) { navController.navigateUp() }
                    }
                    composable(
                        "resolve",
                        enterTransition = {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            )
                        },
                        exitTransition = {
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            )
                        }
                    ) {
                        val index = state.showCandidatesFor
                        val match = index?.let { state.app.matches.getOrNull(it) }
                        if (index != null && match != null) {
                            ResolveScreen(
                                match = match,
                                onSelect = { variant ->
                                    store.dispatch(MainIntent.ResolveCandidate(index, variant))
                                    store.dispatch(MainIntent.CloseResolve)
                                    navController.navigateUp()
                                },
                                onBack = {
                                    store.dispatch(MainIntent.CloseResolve)
                                    navController.navigateUp()
                                }
                            )
                        } else {
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Nothing to resolve")
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { navController.navigateUp() }) { Text("Back") }
                            }
                        }
                    }
                }
                }
            }

            // Show SavedImportsDialog when state indicates it should be shown
            if (state.showSavedImportsWindow) {
                SavedImportsDialog(
                    savedImports = state.app.savedImports,
                    onDismiss = {
                        store.dispatch(MainIntent.SetShowSavedImportsWindow(false))
                    },
                    onSelectImport = { importId ->
                        store.dispatch(MainIntent.LoadSavedImport(importId))
                        // After loading, the wizard will open automatically
                    },
                    onDeleteImport = { importId ->
                        store.dispatch(MainIntent.DeleteSavedImport(importId))
                    }
                )
            }
                } // Close Box
            } // Close Column
        }
    }
}
