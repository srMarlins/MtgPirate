package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.DeckEntryMatch
import util.Promotions
import util.formatPrice

@Composable
fun ExportScreen(
    matches: List<DeckEntryMatch>,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val resolved = matches.filter { it.selectedVariant != null }
    val unresolved = matches.filter { it.selectedVariant == null && it.deckEntry.include }
    val ambiguousCount = matches.count { it.status == MatchStatus.AMBIGUOUS }

    val promo = Promotions.calculate(matches)

    // Shipping selection state derived from promotion eligibility
    var selectedShipping by remember { mutableStateOf(promo.shippingType) }
    val expressEligible = promo.subtotalAfterDiscountCents > 300_00
    // Coerce selection if express becomes ineligible
    LaunchedEffect(expressEligible) {
        if (!expressEligible) selectedShipping = Promotions.ShippingType.NORMAL
    }

    val normalShippingCost = if (promo.subtotalAfterDiscountCents > 100_00) 0 else 10_00
    val selectedShippingCost = when (selectedShipping) {
        Promotions.ShippingType.EXPRESS -> if (expressEligible) 0 else normalShippingCost // safeguard
        Promotions.ShippingType.NORMAL -> normalShippingCost
    }
    val grandTotal = promo.subtotalAfterDiscountCents + selectedShippingCost

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // Scroll states for indicators
        val matchedListState = rememberLazyListState()
        val unmatchedListState = rememberLazyListState()
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "▸ EXPORT",
                style = MaterialTheme.typography.h4,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            PixelBadge(text = "STEP 4/4", color = MaterialTheme.colors.secondary)
            Spacer(Modifier.width(8.dp))
            BlinkingCursor()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "└─ Review export preview, applied coupons & shipping",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(16.dp))

        // Ambiguous guard
        if (ambiguousCount > 0) {
            PixelCard(glowing = true) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelBadge(text = "Resolve Required", color = Color(0xFFFF9800))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "There are $ambiguousCount ambiguous cards. Please resolve them before exporting.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left: Matched preview
            Column(Modifier.weight(1f)) {
                FantasySectionHeader(text = "Matched Cards Preview")
                PixelCard(modifier = Modifier.fillMaxWidth().weight(1f), glowing = false) {
                    HeaderRow()
                    PixelDivider()
                    val grouped = resolved.groupBy {
                        val v = it.selectedVariant!!
                        Triple(v.nameOriginal, v.setCode, v.sku)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f, fill = true)) {
                        LazyColumn(Modifier.fillMaxSize(), state = matchedListState) {
                            items(grouped.values.toList()) { group ->
                                val first = group.first().selectedVariant!!
                                val qtyTotal = group.sumOf { it.deckEntry.qty }
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(first.nameOriginal, Modifier.weight(0.40f), style = MaterialTheme.typography.body2)
                                    Text(first.setCode, Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                                    Text(first.sku, Modifier.weight(0.20f), style = MaterialTheme.typography.body2)
                                    Text(first.variantType, Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                                    Text(qtyTotal.toString(), Modifier.weight(0.07f), style = MaterialTheme.typography.body2)
                                    Text(formatPrice(first.priceInCents), Modifier.weight(0.09f), style = MaterialTheme.typography.body2)
                                }
                                PixelDivider()
                            }
                        }
                        LazyListScrollIndicators(state = matchedListState, modifier = Modifier.matchParentSize())
                    }
                }

                Spacer(Modifier.height(16.dp))

                FantasySectionHeader(text = "Unmatched Cards Preview")
                PixelCard(glowing = false) {
                    if (unresolved.isEmpty()) {
                        Text(
                            "All included cards are matched.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            LazyColumn(Modifier.fillMaxSize(), state = unmatchedListState) {
                                items(unresolved) { m ->
                                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("${m.deckEntry.qty}", Modifier.width(40.dp))
                                        Text(m.deckEntry.cardName, Modifier.weight(1f))
                                    }
                                    PixelDivider()
                                }
                            }
                            LazyListScrollIndicators(state = unmatchedListState, modifier = Modifier.matchParentSize())
                        }
                    }
                }
            }

            // Right: Totals & actions
            Column(Modifier.weight(0.9f)) {
                FantasySectionHeader(text = "Totals & Promotions")
                PixelCard(glowing = false) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Order Value (Before Discount)")
                            Text(formatPrice(promo.baseTotalCents), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Applied Discount")
                            Text(if (promo.discountPercent > 0) "${promo.discountPercent}%" else "—")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Discount Amount")
                            Text(if (promo.discountPercent > 0) "-${formatPrice(promo.discountAmountCents)}" else "—")
                        }
                        PixelDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal After Discount")
                            Text(formatPrice(promo.subtotalAfterDiscountCents), fontWeight = FontWeight.Bold)
                        }
                        PixelDivider()
                        // Shipping options selector
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Shipping Options", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedShipping == Promotions.ShippingType.NORMAL,
                                    onClick = { selectedShipping = Promotions.ShippingType.NORMAL }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Normal (15-40 days) — " + if (normalShippingCost == 0) "Free" else formatPrice(normalShippingCost),
                                    style = MaterialTheme.typography.body2
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedShipping == Promotions.ShippingType.EXPRESS,
                                    onClick = { if (expressEligible) selectedShipping = Promotions.ShippingType.EXPRESS },
                                    enabled = expressEligible
                                )
                                Spacer(Modifier.width(6.dp))
                                val expressLabel = if (expressEligible) {
                                    "Express (3-7 days) — Free"
                                } else {
                                    "Express (3-7 days) — unlocks at ${formatPrice(300_00)} subtotal"
                                }
                                Text(expressLabel, style = MaterialTheme.typography.body2, color = if (expressEligible) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        PixelDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val shippingText = when (selectedShipping) {
                                Promotions.ShippingType.EXPRESS -> if (selectedShippingCost == 0) "Express Free Shipping (3-7 days)" else "Express Shipping"
                                Promotions.ShippingType.NORMAL -> if (selectedShippingCost == 0) "Normal Free Shipping (15-40 days)" else "Normal Shipping"
                            }
                            Text("Shipping")
                            Text(if (selectedShippingCost == 0) shippingText else "$shippingText: ${formatPrice(selectedShippingCost)}")
                        }
                        PixelDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total")
                            Text(formatPrice(grandTotal), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelButton(
                        text = "← Back to Results",
                        onClick = onBack,
                        variant = PixelButtonVariant.SURFACE,
                        modifier = Modifier.width(220.dp)
                    )
                    PixelButton(
                        text = "Export Files →",
                        onClick = onExport,
                        enabled = ambiguousCount == 0 && resolved.isNotEmpty(),
                        variant = PixelButtonVariant.SECONDARY,
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colors.surface.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Card Name", Modifier.weight(0.40f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
        Text("Set", Modifier.weight(0.12f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
        Text("SKU", Modifier.weight(0.20f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
        Text("Type", Modifier.weight(0.12f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
        Text("Qty", Modifier.weight(0.07f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
        Text("Price", Modifier.weight(0.09f), style = MaterialTheme.typography.overline, color = MaterialTheme.colors.primary)
    }
}
