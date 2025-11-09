package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import model.DeckEntryMatch
import model.MatchStatus
import util.formatPrice

@Composable
fun ResultsScreen(
    matches: List<DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit = {}
) {
    val totalMatched = matches.filter { it.selectedVariant != null }
    val totalCents = totalMatched.sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }
    val missed = matches.count { it.selectedVariant == null && it.deckEntry.include }

    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All (${matches.size})", "Matched (${totalMatched.size})", "Unmatched ($missed)")

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // Header
        Text("Step 3: Results", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(4.dp))
        Text("Review your matched cards and resolve any issues", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))

        // Summary Cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total Cards", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("${matches.size}", style = MaterialTheme.typography.h5)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Matched", style = MaterialTheme.typography.caption, color = Color(0xFF4CAF50))
                    Text("${totalMatched.size}", style = MaterialTheme.typography.h5, color = Color(0xFF4CAF50))
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                backgroundColor = if (missed > 0) Color(0xFFF44336).copy(alpha = 0.1f) else Color.Transparent
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Unmatched", style = MaterialTheme.typography.caption, color = if (missed > 0) Color(0xFFF44336) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("$missed", style = MaterialTheme.typography.h5, color = if (missed > 0) Color(0xFFF44336) else MaterialTheme.colors.onSurface)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total Price", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text(formatPrice(totalCents), style = MaterialTheme.typography.h5)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Tabs
        TabRow(selectedTabIndex = tabIndex, backgroundColor = MaterialTheme.colors.surface) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }) {
                    Text(title, Modifier.padding(vertical = 12.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Table Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Qty", Modifier.width(50.dp), style = MaterialTheme.typography.subtitle2)
                Text("Card Name", Modifier.weight(0.35f), style = MaterialTheme.typography.subtitle2)
                Text("Status", Modifier.weight(0.15f), style = MaterialTheme.typography.subtitle2)
                Text("Variant", Modifier.weight(0.15f), style = MaterialTheme.typography.subtitle2)
                Text("Unit", Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2)
                Text("Total", Modifier.width(80.dp), style = MaterialTheme.typography.subtitle2)
                Text("Actions", Modifier.width(180.dp), style = MaterialTheme.typography.subtitle2)
            }
        }

        val filtered = when (tabIndex) {
            1 -> matches.filter { it.selectedVariant != null }
            2 -> matches.filter { it.selectedVariant == null && it.deckEntry.include }
            else -> matches
        }

        // Results List
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            elevation = 1.dp,
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered) { _, m ->
                    val globalIndex = matches.indexOf(m)
                    val variant = m.selectedVariant
                    val rowTotal = variant?.priceInCents?.let { it * m.deckEntry.qty }

                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${m.deckEntry.qty}", Modifier.width(50.dp), style = MaterialTheme.typography.body1)
                        Text(m.deckEntry.cardName, Modifier.weight(0.35f), style = MaterialTheme.typography.body1)

                        // Status badge
                        val (statusText, statusColor) = when (m.status) {
                            MatchStatus.AUTO_MATCHED -> "Auto" to Color(0xFF4CAF50)
                            MatchStatus.MANUAL_SELECTED -> "Manual" to Color(0xFF2196F3)
                            MatchStatus.AMBIGUOUS -> "Ambiguous" to Color(0xFFFF9800)
                            MatchStatus.NOT_FOUND -> "Not Found" to Color(0xFFF44336)
                            MatchStatus.UNRESOLVED -> "Pending" to Color(0xFF9E9E9E)
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(0.15f).padding(end = 8.dp)
                        ) {
                            Text(
                                statusText,
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.caption,
                                color = statusColor
                            )
                        }

                        Text(variant?.variantType ?: "-", Modifier.weight(0.15f), style = MaterialTheme.typography.body2)
                        Text(variant?.let { formatPrice(it.priceInCents) } ?: "-", Modifier.width(70.dp), style = MaterialTheme.typography.body2)
                        Text(rowTotal?.let { formatPrice(it) } ?: "-", Modifier.width(80.dp), style = MaterialTheme.typography.body2)

                        Row(Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.status == MatchStatus.AMBIGUOUS || m.status == MatchStatus.NOT_FOUND) {
                                Button(
                                    onClick = { onResolve(globalIndex) },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF9800))
                                ) {
                                    Text("Resolve", color = Color.White)
                                }
                            }
                            if (m.candidates.isNotEmpty()) {
                                OutlinedButton(onClick = { onShowAllCandidates(globalIndex) }) {
                                    Text("View All")
                                }
                            }
                        }
                    }
                    Divider()
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Footer Actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose, colors = ButtonDefaults.outlinedButtonColors()) {
                Text("← Back to Start")
            }
            if (matches.isNotEmpty()) {
                Button(onClick = onExport) {
                    Text("Export Results →")
                }
            }
        }
    }
}

