package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreferencesScreen(
    initialVariantPriority: List<String>,
    initialSetPriority: List<String>,
    initialFuzzy: Boolean,
    onSave: (List<String>, List<String>, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var variantPriorityText by remember { mutableStateOf(initialVariantPriority.joinToString(",")) }
    var setPriorityText by remember { mutableStateOf(initialSetPriority.joinToString(",")) }
    var fuzzyEnabled by remember { mutableStateOf(initialFuzzy) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = variantPriorityText,
                onValueChange = { variantPriorityText = it },
                label = { Text("Variant Priority (comma-separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = setPriorityText,
                onValueChange = { setPriorityText = it },
                label = { Text("Set Priority (comma-separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = fuzzyEnabled, onCheckedChange = { fuzzyEnabled = it })
                Text("Fuzzy Matching Enabled")
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    val vp = variantPriorityText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val sp = setPriorityText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(vp, sp, fuzzyEnabled)
                }) { Text("Save") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
