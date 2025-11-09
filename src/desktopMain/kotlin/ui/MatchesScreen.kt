package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import model.DeckEntryMatch
import model.MatchStatus
import util.formatPrice
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun MatchesScreen(
    matches: List<DeckEntryMatch>,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Matched") }
    val statusOptions = remember { listOf("All", "Matched", "Unmatched", "Ambiguous", "Not Found", "Unresolved") }

    val filtered = matches.filter { m ->
        val name = m.deckEntry.cardName
        val set = m.selectedVariant?.setCode ?: m.candidates.firstOrNull()?.variant?.setCode ?: ""
        val sku = m.selectedVariant?.sku ?: m.candidates.firstOrNull()?.variant?.sku ?: ""
        val statusOkay = when (statusFilter) {
            "All" -> true
            "Matched" -> m.status == MatchStatus.AUTO_MATCHED || m.status == MatchStatus.MANUAL_SELECTED
            "Unmatched" -> m.selectedVariant == null
            "Ambiguous" -> m.status == MatchStatus.AMBIGUOUS
            "Not Found" -> m.status == MatchStatus.NOT_FOUND
            "Unresolved" -> m.status == MatchStatus.UNRESOLVED
            else -> true
        }
        val q = query.trim()
        val queryOkay = q.isBlank() || name.contains(q, true) || set.contains(q, true) || sku.contains(q, true)
        statusOkay && queryOkay
    }

    val totalCents = filtered.filter { it.selectedVariant != null }
        .sumOf { it.deckEntry.qty * it.selectedVariant!!.priceInCents }
    val unmatched = matches.filter { it.selectedVariant == null && it.deckEntry.include }
    val unmatchedCount = unmatched.size
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Matches Viewer (${matches.size})", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search (name/set/SKU)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            StatusDropdown(label = "Status", options = statusOptions, selected = statusFilter, onSelect = { statusFilter = it })
        }
        Spacer(Modifier.height(8.dp))
        Surface(border = ButtonDefaults.outlinedBorder) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Qty", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.subtitle2)
                Text("Name", modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.subtitle2)
                Text("Set", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.subtitle2)
                Text("Variant", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2)
                Text("SKU", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.subtitle2)
                Text("Status", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2)
                Text("Price", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2)
                Text("Total", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2)
            }
        }
        Spacer(Modifier.height(4.dp))
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(filtered) { index, m ->
                val v = m.selectedVariant
                val price = v?.priceInCents
                val rowTotal = price?.let { it * m.deckEntry.qty }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${m.deckEntry.qty}", modifier = Modifier.width(40.dp))
                    Text(m.deckEntry.cardName, modifier = Modifier.weight(0.35f))
                    Text(v?.setCode ?: "-", modifier = Modifier.weight(0.10f))
                    Text(v?.variantType ?: "-", modifier = Modifier.weight(0.12f))
                    Text(v?.sku ?: "-", modifier = Modifier.weight(0.18f))
                    Text(
                        when (m.status) {
                            MatchStatus.AUTO_MATCHED -> "AUTO"
                            MatchStatus.MANUAL_SELECTED -> "MANUAL"
                            MatchStatus.AMBIGUOUS -> "AMBIGUOUS(${m.candidates.size})"
                            MatchStatus.NOT_FOUND -> "NOT FOUND"
                            MatchStatus.UNRESOLVED -> "UNRESOLVED"
                        },
                        modifier = Modifier.weight(0.12f)
                    )
                    Text(price?.let { formatPrice(it) } ?: "-", modifier = Modifier.width(70.dp))
                    Text(rowTotal?.let { formatPrice(it) } ?: "-", modifier = Modifier.width(70.dp))
                }
                Divider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Showing ${filtered.size} of ${matches.size} | Matched: ${matches.count { it.selectedVariant != null }} | Unmatched: $unmatchedCount")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (unmatchedCount > 0) {
                    OutlinedButton(onClick = {
                        val content = unmatched.joinToString("\n") { "${it.deckEntry.qty} ${it.deckEntry.cardName}" }
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(content), null)
                        } catch (e: Exception) {
                            // ignore clipboard errors on headless/non-awt
                        }
                        // Open TCGplayer Mass Entry page for Magic
                        uriHandler.openUri("https://www.tcgplayer.com/massentry?productline=Magic")
                    }) { Text("Open Unmatched in TCGplayer (copied)") }
                    Spacer(Modifier.width(12.dp))
                }
                Text("Subtotal: ${formatPrice(totalCents)}")
                Spacer(Modifier.width(12.dp))
                Button(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun StatusDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(onClick = {
                    onSelect(opt)
                    expanded = false
                }) {
                    Text(opt)
                }
            }
        }
    }
}
