package app

import androidx.compose.material.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Modifier
import util.formatPrice
import model.MatchStatus
import androidx.compose.ui.window.Dialog
import ui.CatalogScreen

fun main() = application {
    val scope = rememberCoroutineScope()
    val store = remember { MainStore(scope) }
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) { store.dispatch(MainIntent.Init) }

    Window(onCloseRequest = ::exitApplication, title = "US MTG Proxy Tool", state = rememberWindowState(width = 1200.dp, height = 900.dp)) {
        MaterialTheme {
            Scaffold(topBar = {
                TopAppBar(title = { Text("US MTG Proxy Tool") }, actions = {
                    Button(onClick = { store.dispatch(MainIntent.LoadCatalog(true)) }) { Text(if (state.loadingCatalog) "Loading..." else "Load Catalog") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { store.dispatch(MainIntent.SetShowCatalogWindow(true)) }, enabled = state.app.catalog != null) { Text("View Catalog") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { store.dispatch(MainIntent.ExportCsv) }, enabled = state.app.matches.any { it.selectedVariant != null }) { Text("Export CSV") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { store.dispatch(MainIntent.SetShowMatchesWindow(true)) }, enabled = state.app.matches.isNotEmpty()) { Text("View Matches") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { store.dispatch(MainIntent.SetShowPreferences(true)) }) { Text("Prefs") }
                })
            }) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    if (state.app.catalog != null) {
                        Text("Catalog loaded: ${state.app.catalog!!.variants.size} variants")
                    }
                    state.catalogError?.let { Text("Catalog error: $it", color = androidx.compose.ui.graphics.Color.Red) }
                    Spacer(Modifier.height(8.dp))
                    Text("Paste Decklist:")
                    OutlinedTextField(state.deckText, onValueChange = { store.dispatch(MainIntent.UpdateDeckText(it)) }, modifier = Modifier.fillMaxWidth().height(160.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = state.includeSideboard, onCheckedChange = { store.dispatch(MainIntent.ToggleIncludeSideboard(it)) })
                        Text("Include Sideboard")
                        Spacer(Modifier.width(16.dp))
                        Checkbox(checked = state.includeCommanders, onCheckedChange = { store.dispatch(MainIntent.ToggleIncludeCommanders(it)) })
                        Text("Include Commanders")
                    }
                    Button(onClick = { store.dispatch(MainIntent.ParseAndMatch) }, enabled = state.app.catalog != null) { Text("Parse & Match") }

                    if (state.app.deckEntries.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Entries (${state.app.deckEntries.size}):")
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(state.app.matches) { idx, m ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("${m.deckEntry.qty}x", Modifier.width(40.dp))
                                    Text(m.deckEntry.cardName, Modifier.weight(1f))
                                    val statusText = when (m.status) {
                                        MatchStatus.AUTO_MATCHED -> m.selectedVariant?.sku ?: "AUTO"
                                        MatchStatus.AMBIGUOUS -> "AMBIGUOUS(${m.candidates.size})"
                                        MatchStatus.NOT_FOUND -> "NOT FOUND"
                                        MatchStatus.UNRESOLVED -> "UNRESOLVED"
                                        MatchStatus.MANUAL_SELECTED -> m.selectedVariant?.sku ?: "MANUAL"
                                    }
                                    Text(statusText, Modifier.width(140.dp))
                                    val priceDisplay = m.selectedVariant?.let { formatPrice(it.priceInCents) } ?: "-"
                                    Text(priceDisplay, Modifier.width(70.dp))
                                    if (m.status == MatchStatus.AMBIGUOUS || m.status == MatchStatus.NOT_FOUND) {
                                        Button(onClick = { store.dispatch(MainIntent.OpenResolve(idx)) }) { Text("Resolve") }
                                    }
                                }
                                Divider()
                            }
                        }
                        val totalCents = state.app.matches.filter { it.selectedVariant != null }.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }
                        Spacer(Modifier.height(8.dp))
                        Text("Total (matched only): ${formatPrice(totalCents)}")
                    }
                    state.showExportResult?.let { Text("Exported: ${it.toAbsolutePath()}") }

                    if (state.app.logs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Logs:", style = MaterialTheme.typography.subtitle2)
                        Surface(border = ButtonDefaults.outlinedBorder, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                                itemsIndexed(state.app.logs.takeLast(500)) { _, l ->
                                    Text("[${l.level}] ${l.timestamp}: ${l.message}", style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }
                }

                if (state.showCandidatesFor != null) {
                    val match = state.app.matches[state.showCandidatesFor!!]
                    Dialog(onCloseRequest = { store.dispatch(MainIntent.CloseResolve) }) {
                        Surface(shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(16.dp).width(480.dp)) {
                                Text("Resolve: ${match.deckEntry.cardName}", style = MaterialTheme.typography.h6)
                                Spacer(Modifier.height(8.dp))
                                if (match.candidates.isEmpty()) {
                                    Text("No candidates available.")
                                } else {
                                    match.candidates.sortedBy { it.score }.forEach { cand ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            val variant = cand.variant
                                            Button(onClick = {
                                                store.dispatch(MainIntent.ResolveCandidate(state.showCandidatesFor!!, variant))
                                            }) { Text("Select") }
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("${variant.nameOriginal} [${variant.setCode}] ${variant.variantType}")
                                                Text("SKU: ${variant.sku} | Price: ${formatPrice(variant.priceInCents)} | Reason: ${cand.reason} | Score: ${cand.score}", style = MaterialTheme.typography.caption)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { store.dispatch(MainIntent.CloseResolve) }) { Text("Close") }
                            }
                        }
                    }
                }

                if (state.showPreferences) {
                    Dialog(onCloseRequest = { store.dispatch(MainIntent.SetShowPreferences(false)) }) {
                        Surface(shape = MaterialTheme.shapes.medium) {
                            var variantPriorityText by remember { mutableStateOf(state.app.preferences.variantPriority.joinToString(",")) }
                            var setPriorityText by remember { mutableStateOf(state.app.preferences.setPriority.joinToString(",")) }
                            var fuzzyEnabled by remember { mutableStateOf(state.app.preferences.fuzzyEnabled) }
                            Column(Modifier.padding(16.dp).width(420.dp)) {
                                Text("Preferences", style = MaterialTheme.typography.h6)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(variantPriorityText, onValueChange = { variantPriorityText = it }, label = { Text("Variant Priority (comma-separated)") })
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(setPriorityText, onValueChange = { setPriorityText = it }, label = { Text("Set Priority (comma-separated)") })
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(fuzzyEnabled, onCheckedChange = { fuzzyEnabled = it })
                                    Text("Fuzzy Matching Enabled")
                                }
                                Spacer(Modifier.height(12.dp))
                                Row {
                                    Button(onClick = {
                                        store.dispatch(
                                            MainIntent.SavePreferences(
                                                variantPriority = variantPriorityText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                                setPriority = setPriorityText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                                fuzzyEnabled = fuzzyEnabled
                                            )
                                        )
                                    }) { Text("Save") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { store.dispatch(MainIntent.SetShowPreferences(false)) }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.showCatalogWindow && state.app.catalog != null) {
        Window(onCloseRequest = { store.dispatch(MainIntent.SetShowCatalogWindow(false)) }, title = "Catalog Viewer", state = rememberWindowState(width = 1000.dp, height = 800.dp)) {
            MaterialTheme {
                CatalogScreen(catalog = state.app.catalog!!) { store.dispatch(MainIntent.SetShowCatalogWindow(false)) }
            }
        }
    }
    if (state.showMatchesWindow) {
        Window(onCloseRequest = { store.dispatch(MainIntent.SetShowMatchesWindow(false)) }, title = "Matches Viewer", state = rememberWindowState(width = 1100.dp, height = 800.dp)) {
            MaterialTheme {
                ui.MatchesScreen(matches = state.app.matches) { store.dispatch(MainIntent.SetShowMatchesWindow(false)) }
            }
        }
    }
}
