package app

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

            // Track current route to determine active step
            val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry)
            val currentStep = when (currentRoute.value?.destination?.route) {
                "import" -> 1
                "preferences" -> 2
                "results" -> 3
                else -> 1
            }

            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text("US MTG Proxy Tool") },
                            actions = {
                                IconButton(onClick = { store.dispatch(MainIntent.ToggleTheme) }) {
                                    Text(if (state.isDarkTheme) "☀️" else "🌙", style = MaterialTheme.typography.h6)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { store.dispatch(MainIntent.LoadCatalog(true)) }) {
                                    Text(if (state.loadingCatalog) "Loading..." else "Load Catalog")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { navController.navigate("catalog") }, enabled = state.app.catalog != null) { Text("Catalog") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { store.dispatch(MainIntent.ExportCsv) }, enabled = state.app.matches.any { it.selectedVariant != null }) { Text("Export CSV") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { navController.navigate("matches") }, enabled = state.app.matches.isNotEmpty()) { Text("Matches") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { navController.navigate("results") }, enabled = state.app.matches.isNotEmpty()) { Text("Results") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { navController.navigate("prefs") }) { Text("Prefs") }
                            }
                        )

                        // Show stepper only for wizard routes
                        if (currentRoute.value?.destination?.route in listOf("import", "preferences", "results")) {
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
                            Divider()
                        }
                    }
                }
            ) { padding ->
                NavHost(navController = navController, startDestination = "import", modifier = Modifier.fillMaxSize().padding(padding)) {
                    composable("import") {
                        // Step 1: Deck Import (Wizard Start)
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Step 1: Import Your Decklist", style = MaterialTheme.typography.h4)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Paste your decklist below. Each line should be in the format: quantity cardname",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = state.deckText,
                                onValueChange = { store.dispatch(MainIntent.UpdateDeckText(it)) },
                                label = { Text("Decklist") },
                                placeholder = { Text("4 Lightning Bolt\n2 Brainstorm\n1 Black Lotus") },
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = {
                                        store.dispatch(MainIntent.ParseDeck)
                                        store.dispatch(MainIntent.CompleteWizardStep(1))
                                        navController.navigate("preferences")
                                    },
                                    enabled = state.deckText.isNotBlank(),
                                    modifier = Modifier.height(48.dp).width(200.dp)
                                ) {
                                    Text("Next: Configure Options →")
                                }
                            }
                        }
                    }
                    composable("preferences") {
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
                                navController.navigate("results")
                            }
                        )
                    }
                    composable("results") {
                        // Step 3: Results (Wizard End)
                        ResultsScreen(
                            matches = state.app.matches,
                            onResolve = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve")
                            },
                            onShowAllCandidates = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve")
                            },
                            onClose = { navController.navigateUp() },
                            onExport = { store.dispatch(MainIntent.ExportWizardResults) }
                        )
                    }
                    composable("catalog") {
                        val catalog = state.app.catalog
                        if (catalog != null) {
                            CatalogScreen(catalog = catalog) { navController.navigateUp() }
                        } else {
                            Text("No catalog loaded.", modifier = Modifier.padding(16.dp))
                        }
                    }
                    composable("matches") {
                        MatchesScreen(matches = state.app.matches) { navController.navigateUp() }
                    }
                    composable("results") {
                        ResultsScreen(
                            matches = state.app.matches,
                            onResolve = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve")
                            },
                            onShowAllCandidates = { idx ->
                                store.dispatch(MainIntent.OpenResolve(idx))
                                navController.navigate("resolve")
                            },
                            onClose = { navController.navigateUp() },
                            onExport = { store.dispatch(MainIntent.ExportWizardResults) }
                        )
                    }
                    composable("resolve") {
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
                    composable("prefs") {
                        ui.PreferencesScreen(
                            initialVariantPriority = state.app.preferences.variantPriority,
                            initialSetPriority = state.app.preferences.setPriority,
                            initialFuzzy = state.app.preferences.fuzzyEnabled,
                            onSave = { vp, sp, fuzzy ->
                                store.dispatch(MainIntent.SavePreferences(vp, sp, fuzzy))
                                store.dispatch(MainIntent.RunMatch)
                                navController.navigate("results")
                            },
                            onCancel = { navController.navigateUp() }
                        )
                    }
                }
            }
        }
    }
}
