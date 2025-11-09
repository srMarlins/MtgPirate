package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.Catalog
import util.formatPrice

@Composable
fun CatalogScreen(catalog: Catalog, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var variantFilter by remember { mutableStateOf("All") }
    val variants = catalog.variants
    val variantTypes = remember { listOf("All") + variants.map { it.variantType }.distinct().sorted() }

    val filtered = variants.filter { v ->
        (variantFilter == "All" || v.variantType.equals(variantFilter, true)) &&
        (query.isBlank() || v.nameOriginal.contains(query, true) || v.setCode.contains(query, true) || v.sku.contains(query, true))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▸ CATALOG",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PixelBadge(
                    text = "${variants.size} VARIANTS",
                    color = MaterialTheme.colors.secondary
                )
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "└─ Browse all available cards in the catalog",
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
                PixelDropdownSelector(
                    label = "VARIANT",
                    options = variantTypes,
                    selected = variantFilter,
                    onSelect = { variantFilter = it }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Table Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f), shape = PixelShape(cornerSize = 6.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NAME", modifier = Modifier.weight(0.40f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("SET", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("TYPE", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("SKU", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Text("PRICE", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Results List
            PixelCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                glowing = false
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered) { v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(v.nameOriginal, Modifier.weight(0.40f), style = MaterialTheme.typography.body2)
                            Text(v.setCode, Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                            Text(v.variantType, Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                            Text(v.sku, Modifier.weight(0.18f), style = MaterialTheme.typography.body2)
                            Text(
                                formatPrice(v.priceInCents),
                                Modifier.weight(0.10f),
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        PixelDivider()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer with stats and close button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Showing ${filtered.size} of ${variants.size}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                PixelButton(
                    text = "Close",
                    onClick = onClose,
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.width(150.dp)
                )
            }
        }
    }
}

@Composable
private fun PixelDropdownSelector(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = if (expanded) 0.5f else 0.2f)
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

