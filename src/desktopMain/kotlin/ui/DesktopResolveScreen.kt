package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.MatchCandidate
import model.DeckEntryMatch
import model.CardVariant
import util.formatPrice

/**
 * Desktop-specific ResolveScreen with image preview support
 */
@Composable
fun DesktopResolveScreen(
    match: DeckEntryMatch,
    onSelect: (CardVariant) -> Unit,
    onBack: () -> Unit,
    onEnrichVariant: ((CardVariant) -> Unit)? = null,
) {
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedCardName by remember { mutableStateOf("") }
    var selectedSetCode by remember { mutableStateOf("") }
    var selectedVariantType by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Scanline effect
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header with pixel styling - compact layout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▸ RESOLVE: ${match.deckEntry.cardName.uppercase()}",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                BlinkingCursor()
            }
            Spacer(Modifier.height(4.dp))
            PixelBadge(
                text = "CANDIDATE SELECTION",
                color = MaterialTheme.colors.secondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "└─ Select the correct variant for this card",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(16.dp))

            if (match.candidates.isEmpty()) {
                PixelCard(glowing = true) {
                    Text(
                        "⚠ NO CANDIDATES AVAILABLE",
                        style = MaterialTheme.typography.h6,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No matching cards were found in the catalog.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                PixelButton(
                    text = "← Back",
                    onClick = onBack,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(200.dp)
                )
            } else {
                // Candidates count badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CANDIDATES",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    PixelBadge(
                        text = "${match.candidates.size}",
                        color = MaterialTheme.colors.primary
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Table Header - now includes IMAGE column
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f), shape = PixelShape(cornerSize = 6.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("IMAGE", Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("ACTION", Modifier.width(100.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("CARD NAME", Modifier.weight(0.3f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("SET", Modifier.width(70.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("VARIANT", Modifier.width(100.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("PRICE", Modifier.width(80.dp), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                        Text("MATCH INFO", Modifier.weight(0.3f), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Candidates List
                PixelCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    glowing = false
                ) {
                    val sorted = remember(match.candidates) { match.candidates.sortedBy { it.score } }
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            items(sorted) { cand: MatchCandidate ->
                                val variant = cand.variant
                                
                                // Trigger image enrichment when variant comes into view
                                LaunchedEffect(variant.sku) {
                                    if (variant.imageUrl == null) {
                                        onEnrichVariant?.invoke(variant)
                                    }
                                }
                                
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Image Preview
                                    PixelImagePreview(
                                        imageUrl = variant.imageUrl,
                                        cardName = variant.nameOriginal,
                                        modifier = Modifier.width(70.dp),
                                        onClick = {
                                            selectedImageUrl = variant.imageUrl
                                            selectedCardName = variant.nameOriginal
                                            selectedSetCode = variant.setCode
                                            selectedVariantType = variant.variantType
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    
                                    // Select Button
                                    PixelButton(
                                        text = "Select",
                                        onClick = { onSelect(variant) },
                                        variant = PixelButtonVariant.SECONDARY,
                                        modifier = Modifier.width(100.dp).height(40.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    
                                    // Card Name
                                    Column(Modifier.weight(0.3f)) {
                                        Text(
                                            variant.nameOriginal,
                                            style = MaterialTheme.typography.body1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    // Set Code
                                    Text(
                                        variant.setCode,
                                        Modifier.width(70.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    
                                    // Variant Type
                                    Text(
                                        variant.variantType,
                                        Modifier.width(100.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    
                                    // Price
                                    Text(
                                        formatPrice(variant.priceInCents),
                                        Modifier.width(80.dp),
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // Match Info
                                    Column(Modifier.weight(0.3f)) {
                                        Text(
                                            "Reason: ${cand.reason}",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "Score: ${cand.score} | SKU: ${variant.sku}",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                PixelDivider()
                            }
                        }
                        LazyListScrollIndicators(state = listState, modifier = Modifier.matchParentSize())
                    }
                }

                Spacer(Modifier.height(16.dp))

                PixelButton(
                    text = "← Back",
                    onClick = onBack,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(200.dp)
                )
            }
        }
        
        // Image Modal Dialog
        if (selectedImageUrl != null || selectedCardName.isNotEmpty()) {
            PixelImageModal(
                imageUrl = selectedImageUrl,
                cardName = selectedCardName,
                setCode = selectedSetCode,
                variantType = selectedVariantType,
                onDismiss = {
                    selectedImageUrl = null
                    selectedCardName = ""
                    selectedSetCode = ""
                    selectedVariantType = ""
                }
            )
        }
    }
}
