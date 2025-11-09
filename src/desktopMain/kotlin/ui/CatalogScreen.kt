package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Catalog (${variants.size} variants)", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search (name/set/SKU)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            DropdownSelector(label = "Variant", options = variantTypes, selected = variantFilter, onSelect = { variantFilter = it })
        }
        Spacer(Modifier.height(8.dp))
        Surface(border = ButtonDefaults.outlinedBorder) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Name", modifier = Modifier.weight(0.40f), style = MaterialTheme.typography.subtitle2)
                Text("Set", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2)
                Text("Type", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.subtitle2)
                Text("SKU", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.subtitle2)
                Text("Price", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.subtitle2)
            }
        }
        Spacer(Modifier.height(4.dp))
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(filtered) { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(v.nameOriginal, Modifier.weight(0.40f))
                    Text(v.setCode, Modifier.weight(0.12f))
                    Text(v.variantType, Modifier.weight(0.12f))
                    Text(v.sku, Modifier.weight(0.18f))
                    Text(formatPrice(v.priceInCents), Modifier.weight(0.10f))
                }
                Divider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Showing ${filtered.size} of ${variants.size}")
            Button(onClick = onClose) { Text("Close") }
        }
    }
}

@Composable
private fun DropdownSelector(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
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

