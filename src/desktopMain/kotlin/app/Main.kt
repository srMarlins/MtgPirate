package app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ui.*

fun main() = application {
    val scope = rememberCoroutineScope()
    val store = remember { MainStore(scope) }
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) { store.dispatch(MainIntent.Init) }

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
        title = "US MTG Proxy Tool",
        state = rememberWindowState(width = 1200.dp, height = 900.dp)
    ) {
        AppTheme(darkTheme = state.isDarkTheme) {
            val navController = rememberNavController()

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
                    Column {
                        // Retro pixel art styled top bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colors.surface,
                                            MaterialTheme.colors.surface.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                                .pixelBorder(borderWidth = 3.dp, enabled = true, glowAlpha = 0.3f)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side - Title with retro styling
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "█▓▒░",
                                        style = MaterialTheme.typography.h5,
                                        color = MaterialTheme.colors.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "MTG PROXY TOOL",
                                        style = MaterialTheme.typography.h6,
                                        color = MaterialTheme.colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "░▒▓█",
                                        style = MaterialTheme.typography.h5,
                                        color = MaterialTheme.colors.secondary
                                    )
                                }

                                // Right side - Action buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Theme toggle with pixel styling
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.4f)
                                            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f), shape = PixelShape(cornerSize = 6.dp))
                                            .clickable { store.dispatch(MainIntent.ToggleTheme) }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (state.isDarkTheme) "☀" else "☾",
                                            style = MaterialTheme.typography.h6
                                        )
                                    }

                                    PixelButton(
                                        text = if (state.loadingCatalog) "LOADING..." else "CATALOG",
                                        onClick = { store.dispatch(MainIntent.LoadCatalog(true)) },
                                        variant = PixelButtonVariant.PRIMARY,
                                        enabled = !state.loadingCatalog
                                    )

                                    PixelButton(
                                        text = "BROWSE",
                                        onClick = {
                                            if (navController.currentDestination?.route != "catalog") {
                                                navController.navigate("catalog") {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                        },
                                        enabled = state.app.catalog != null,
                                        variant = PixelButtonVariant.SURFACE
                                    )

                                    PixelButton(
                                        text = "EXPORT",
                                        onClick = { store.dispatch(MainIntent.ExportCsv) },
                                        enabled = state.app.matches.any { it.selectedVariant != null },
                                        variant = PixelButtonVariant.SECONDARY
                                    )

                                    PixelButton(
                                        text = "MATCHES",
                                        onClick = {
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
                                        enabled = state.app.matches.isNotEmpty(),
                                        variant = PixelButtonVariant.SURFACE
                                    )

                                    PixelButton(
                                        text = "RESULTS",
                                        onClick = {
                                            if (navController.currentDestination?.route != "results") {
                                                navController.navigate("results") {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                        },
                                        enabled = state.app.matches.isNotEmpty(),
                                        variant = PixelButtonVariant.SURFACE
                                    )

                                    PixelButton(
                                        text = "CONFIG",
                                        onClick = {
                                            if (navController.currentDestination?.route != "prefs") {
                                                navController.navigate("prefs") {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                        },
                                        variant = PixelButtonVariant.SURFACE
                                    )
                                }
                            }
                        }

                        // Show stepper only for wizard routes
                        if (currentRoute in listOf("import", "preferences", "results")) {
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
                                // Title with pixel styling
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "▸ DECK IMPORT",
                                        style = MaterialTheme.typography.h3,
                                        color = MaterialTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    BlinkingCursor()
                                }

                                Spacer(Modifier.height(8.dp))

                                PixelBadge(
                                    text = "STEP 1/3",
                                    color = MaterialTheme.colors.secondary
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "└─ Paste your decklist below. Format: [quantity] [cardname]",
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )

                                Spacer(Modifier.height(24.dp))

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
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    PixelButton(
                                        text = "Next: Configure →",
                                        onClick = {
                                            store.dispatch(MainIntent.ParseDeck)
                                            store.dispatch(MainIntent.CompleteWizardStep(1))
                                            navController.navigate("preferences") {
                                                launchSingleTop = true
                                            }
                                        },
                                        enabled = state.deckText.isNotBlank(),
                                        modifier = Modifier.width(240.dp),
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
                            includeCommanders = state.includeCommanders,
                            includeTokens = state.includeTokens,
                            variantPriority = state.app.preferences.variantPriority,
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
                    composable(
                        "prefs",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {
                        PreferencesScreen(
                            initialVariantPriority = state.app.preferences.variantPriority,
                            initialSetPriority = state.app.preferences.setPriority,
                            initialFuzzy = state.app.preferences.fuzzyEnabled,
                            onSave = { vp, sp, fuzzy ->
                                store.dispatch(MainIntent.SavePreferences(vp, sp, fuzzy))
                                store.dispatch(MainIntent.RunMatch)
                                navController.navigate("results") {
                                    launchSingleTop = true
                                }
                            },
                            onCancel = { navController.navigateUp() }
                        )
                    }
                }
                }
            }
        }
    }
}
