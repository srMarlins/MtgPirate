package app

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.*

/**
 * iOS Import Screen - Step 1 of the wizard.
 * Allows users to paste their decklist with pixel design styling.
 */
@Composable
fun IosImportScreen(
    deckText: String,
    onDeckTextChange: (String) -> Unit,
    onNext: () -> Unit,
    onShowSavedImports: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect overlay
        ScanlineEffect(alpha = 0.03f)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 80.dp), // Space for bottom nav
            verticalArrangement = Arrangement.Top
        ) {
            // Title with pixel styling
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    "▸ DECK IMPORT",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PixelBadge(
                    text = "STEP 1/4",
                    color = MaterialTheme.colors.secondary
                )
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            
            Text(
                "└─ Paste your decklist below",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Deck text input with pixel card
            PixelCard(
                glowing = deckText.isBlank(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PixelTextField(
                    value = deckText,
                    onValueChange = onDeckTextChange,
                    label = "DECKLIST.TXT",
                    placeholder = "4 Lightning Bolt\n2 Brainstorm\n1 Black Lotus",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelButton(
                    text = "📚 Saved",
                    onClick = onShowSavedImports,
                    modifier = Modifier.weight(1f),
                    variant = PixelButtonVariant.SURFACE
                )
                
                PixelButton(
                    text = "Next →",
                    onClick = onNext,
                    enabled = deckText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    variant = PixelButtonVariant.SECONDARY
                )
            }
        }
    }
}

/**
 * iOS Preferences Screen - Step 2 of the wizard.
 */
@Composable
fun IosPreferencesScreen(
    includeSideboard: Boolean,
    includeCommanders: Boolean,
    includeTokens: Boolean,
    variantPriority: List<String>,
    onIncludeSideboardChange: (Boolean) -> Unit,
    onIncludeCommandersChange: (Boolean) -> Unit,
    onIncludeTokensChange: (Boolean) -> Unit,
    onVariantPriorityChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    PreferencesWizardScreen(
        includeSideboard = includeSideboard,
        includeCommanders = includeCommanders,
        includeTokens = includeTokens,
        variantPriority = variantPriority,
        onIncludeSideboardChange = onIncludeSideboardChange,
        onIncludeCommandersChange = onIncludeCommandersChange,
        onIncludeTokensChange = onIncludeTokensChange,
        onVariantPriorityChange = onVariantPriorityChange,
        onBack = onBack,
        onNext = onNext
    )
}

/**
 * iOS Results Screen - Step 3 of the wizard.
 */
@Composable
fun IosResultsScreen(
    matches: List<model.DeckEntryMatch>,
    onResolve: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    ResultsScreen(
        matches = matches,
        onResolve = onResolve,
        onShowAllCandidates = onResolve,
        onClose = onBack,
        onExport = onNext
    )
}

/**
 * iOS Resolve Screen - Card variant selection.
 */
@Composable
fun IosResolveScreen(
    match: model.DeckEntryMatch,
    onSelect: (model.CardVariant) -> Unit,
    onBack: () -> Unit
) {
    ResolveScreen(
        match = match,
        onSelect = onSelect,
        onBack = onBack
    )
}

/**
 * iOS Export Screen - Step 4 of the wizard.
 */
@Composable
fun IosExportScreen(
    matches: List<model.DeckEntryMatch>,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    ExportScreen(
        matches = matches,
        onBack = onBack,
        onExport = onExport
    )
}

/**
 * iOS Catalog Screen - View all catalog entries.
 */
@Composable
fun IosCatalogScreen(
    catalog: model.Catalog,
    onBack: () -> Unit
) {
    CatalogScreen(
        catalog = catalog,
        onClose = onBack
    )
}

/**
 * iOS Matches Screen - View all matches.
 */
@Composable
fun IosMatchesScreen(
    matches: List<model.DeckEntryMatch>,
    onBack: () -> Unit
) {
    MatchesScreen(
        matches = matches,
        onClose = onBack
    )
}
