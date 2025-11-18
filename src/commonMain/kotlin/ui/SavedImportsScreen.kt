@file:OptIn(kotlin.time.ExperimentalTime::class)
package ui

import androidx.compose.runtime.Composable
import model.SavedImport

/**
 * Platform-specific saved imports dialog.
 * Desktop uses fixed dimensions, iOS uses full-screen mobile layout.
 */
@Composable
expect fun SavedImportsDialog(
    savedImports: List<SavedImport>,
    onDismiss: () -> Unit,
    onSelectImport: (String) -> Unit,
    onDeleteImport: (String) -> Unit
)

