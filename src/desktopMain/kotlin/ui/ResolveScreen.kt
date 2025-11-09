package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.MatchCandidate
import model.DeckEntryMatch
import model.CardVariant
import util.formatPrice

@Composable
fun ResolveScreen(
    match: DeckEntryMatch,
    onSelect: (CardVariant) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve: ${match.deckEntry.cardName}") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (match.candidates.isEmpty()) {
                Text("No candidates available.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Close") }
            } else {
                Text("Candidates (${match.candidates.size})", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                val sorted = remember(match.candidates) { match.candidates.sortedBy { it.score } }
                sorted.forEach { cand: MatchCandidate ->
                    val variant = cand.variant
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Button(onClick = { onSelect(variant) }) { Text("Select") }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${variant.nameOriginal} [${variant.setCode}] ${variant.variantType}")
                            Text(
                                "SKU: ${variant.sku} | Price: ${formatPrice(variant.priceInCents)} | Reason: ${cand.reason} | Score: ${cand.score}",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                    Divider()
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Done") }
            }
        }
    }
}
