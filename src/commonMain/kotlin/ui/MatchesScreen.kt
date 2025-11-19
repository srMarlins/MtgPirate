package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.CardVariant
import model.DeckEntryMatch
import model.MatchStatus
import util.formatPrice

@Composable
fun MatchesScreen(
    matches: List<DeckEntryMatch>,
    onClose: () -> Unit,
    onEnrichVariant: ((CardVariant) -> Unit)? = null
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▸ MATCHES VIEWER",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PixelBadge(
                    text = "${matches.size} TOTAL",
                    color = MaterialTheme.colors.secondary
                )
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "└─ Browse and filter all card matches",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(12.dp))

            // Search and Filter
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PixelTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "SEARCH",
                    placeholder = "Search by name, set, or SKU...",
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                PixelStatusDropdown(
                    label = "STATUS",
                    options = statusOptions,
                    selected = statusFilter,
                    onSelect = { statusFilter = it }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Table Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = 0.3f)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f), shape = PixelShape(cornerSize = 6.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("QTY", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("NAME", modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("SET", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("VARIANT", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("SKU", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("STATUS", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("PRICE", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("TOTAL", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Results List
            PixelCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                glowing = false
            ) {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(filtered) { index, m ->
                            val v = m.selectedVariant
                            val price = v?.priceInCents
                            val rowTotal = price?.let { it * m.deckEntry.qty }
                            
                            // Trigger image enrichment when variant comes into view
                            v?.let { variant ->
                                androidx.compose.runtime.LaunchedEffect(variant.sku) {
                                    if (variant.imageUrl == null) {
                                        onEnrichVariant?.invoke(variant)
                                    }
                                }
                            }
                            
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("${m.deckEntry.qty}", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.body2)
                                Text(m.deckEntry.cardName, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.body2)
                                Text(v?.setCode ?: "-", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.body2)
                                Text(v?.variantType ?: "-", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                                Text(v?.sku ?: "-", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.body2)

                                // Status badge
                                Box(modifier = Modifier.weight(0.12f)) {
                                    val statusText = when (m.status) {
                                        MatchStatus.AUTO_MATCHED -> "AUTO"
                                        MatchStatus.MANUAL_SELECTED -> "MANUAL"
                                        MatchStatus.AMBIGUOUS -> "AMBIG(${m.candidates.size})"
                                        MatchStatus.NOT_FOUND -> "NOT FOUND"
                                        MatchStatus.UNRESOLVED -> "UNRESOLVED"
                                    }
                                    val statusColor = when (m.status) {
                                        MatchStatus.AUTO_MATCHED -> Color(0xFF4CAF50)
                                        MatchStatus.MANUAL_SELECTED -> Color(0xFF2196F3)
                                        MatchStatus.AMBIGUOUS -> Color(0xFFFF9800)
                                        MatchStatus.NOT_FOUND -> Color(0xFFF44336)
                                        MatchStatus.UNRESOLVED -> Color(0xFF9E9E9E)
                                    }
                                    PixelBadge(text = statusText, color = statusColor)
                                }

                                Text(
                                    price?.let { formatPrice(it) } ?: "-",
                                    modifier = Modifier.width(70.dp),
                                    style = MaterialTheme.typography.body2,
                                    color = if (price != null) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                    fontWeight = if (price != null) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    rowTotal?.let { formatPrice(it) } ?: "-",
                                    modifier = Modifier.width(70.dp),
                                    style = MaterialTheme.typography.body2,
                                    color = if (rowTotal != null) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                    fontWeight = if (rowTotal != null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            PixelDivider()
                        }
                    }
                    LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer with stats and buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Showing ${filtered.size} of ${matches.size} | Matched: ${matches.count { it.selectedVariant != null }} | Unmatched: $unmatchedCount",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Subtotal: ",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            formatPrice(totalCents),
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (unmatchedCount > 0) {
                        val scope = rememberCoroutineScope()
                        PixelButton(
                            text = "Open in TCGplayer",
                            onClick = {
                                val content = unmatched.joinToString("\n") { "${it.deckEntry.qty} ${it.deckEntry.cardName}" }
                                scope.launch {
                                    platform.copyToClipboard(content)
                                }
                                // Open TCGplayer Mass Entry page for Magic
                                uriHandler.openUri("https://www.tcgplayer.com/massentry?productline=Magic")
                            },
                            variant = PixelButtonVariant.SECONDARY,
                            modifier = Modifier.width(280.dp)
                        )
                    }
                    PixelButton(
                        text = "Close",
                        onClick = onClose,
                        variant = PixelButtonVariant.PRIMARY,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelStatusDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .pixelBorder(borderWidth = 2.dp, cornerSize = 6.dp, enabled = true, glowAlpha = if (expanded) 0.5f else 0.2f)
                .background(MaterialTheme.colors.surface, shape = PixelShape(cornerSize = 6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "$label: ${selected.uppercase()}",
                style = MaterialTheme.typography.button,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(onClick = {
                    onSelect(opt)
                    expanded = false
                }) {
                    Text(opt, fontWeight = if (opt == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
