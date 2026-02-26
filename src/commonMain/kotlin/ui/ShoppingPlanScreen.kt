package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.MultiMatch
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan
import util.formatPrice

private const val TCGPLAYER_MASS_ENTRY_URL = "https://www.tcgplayer.com/massentry?productline=Magic"
private const val BOOTLEG_MAGE_DECK_IMPORT_URL = "https://bootlegmage.com/deck-import/"

/**
 * Returns the themed color for a given seller.
 */
fun sellerColor(seller: Seller): Color = when (seller) {
    Seller.USEA -> SellerUsea
    Seller.BOOTLEG_MAGE -> SellerBootlegMage
    Seller.TCGPLAYER -> SellerTcgPlayer
}

/**
 * Format order items for export to a given seller.
 * Duplicates the logic from CatalogSource implementations so the UI layer
 * can generate export text without needing a CatalogSource reference.
 */
private fun formatForExport(seller: Seller, items: List<OrderItem>): String = when (seller) {
    Seller.USEA -> {
        // CSV format: "Card Name,Set,SKU,Type,Qty,Price"
        val header = "Card Name,Set,SKU,Type,Qty,Price"
        val rows = items.joinToString("\n") { item ->
            val v = item.variant
            "${v.nameOriginal},${v.setCode},${v.sku},${v.variantType.displayName},${item.qty},${formatPrice(v.priceInCents)}"
        }
        "$header\n$rows"
    }
    Seller.BOOTLEG_MAGE -> {
        // Bootleg Mage deck import format: "qty CardName" per line
        items.joinToString("\n") { "${it.qty} ${it.variant.nameOriginal}" }
    }
    Seller.TCGPLAYER -> {
        // TCGPlayer mass entry format: "1 Lightning Bolt [M11]"
        items.joinToString("\n") {
            "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
        }
    }
}

/**
 * Shopping Plan screen (Step 4) - shows the optimized multi-seller shopping plan
 * with per-seller order cards, expandable item lists, and checkout action buttons.
 */
@Composable
fun ShoppingPlanScreen(
    shoppingPlan: ShoppingPlan?,
    multiMatches: List<MultiMatch>,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
) {
    // Trigger optimization if we have multi-matches but no plan yet
    LaunchedEffect(shoppingPlan, multiMatches) {
        if (shoppingPlan == null && multiMatches.isNotEmpty()) {
            onOptimize()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScanlineEffect(alpha = 0.03f)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SHOPPING PLAN",
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
                "Optimized order across sellers with discounts and shipping",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(16.dp))

            if (isLoading || shoppingPlan == null) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Optimizing shopping plan",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        AnimatedLoadingDots()
                    }
                }
            } else {
                // Total summary header
                ShoppingPlanSummary(shoppingPlan)
                Spacer(Modifier.height(16.dp))

                // Per-seller order cards (scrollable)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    shoppingPlan.orders.forEach { order ->
                        SellerOrderCard(
                            order = order,
                            onCopyToClipboard = onCopyToClipboard,
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer navigation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelButton(
                    text = "Back to Results",
                    onClick = onBack,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

/**
 * Summary card showing total price and savings across all sellers.
 */
@Composable
private fun ShoppingPlanSummary(plan: ShoppingPlan) {
    PixelCard(glowing = true) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Grand Total",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatPrice(plan.totalPriceCents),
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (plan.savingsVsSingleSeller > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Savings vs. single seller",
                        style = MaterialTheme.typography.body2,
                        color = PixelGreen
                    )
                    Text(
                        "- ${formatPrice(plan.savingsVsSingleSeller)}",
                        style = MaterialTheme.typography.body1,
                        color = PixelGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            PixelDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                plan.orders.forEach { order ->
                    PixelBadge(
                        text = "${order.seller.displayName}: ${formatPrice(order.totalCents)}",
                        color = sellerColor(order.seller)
                    )
                }
            }
        }
    }
}

/**
 * Expandable card for a single seller's order.
 */
@Composable
private fun SellerOrderCard(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = sellerColor(order.seller)
    val totalItems = order.items.sumOf { it.qty }

    PixelCard(glowing = false) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Seller header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelBadge(text = order.seller.displayName, color = color)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "$totalItems cards",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    formatPrice(order.totalCents),
                    style = MaterialTheme.typography.h6,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Order details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Subtotal",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        formatPrice(order.subtotalCents),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface
                    )
                }

                if (order.discountPercent > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Discount (${order.discountPercent}%)",
                            style = MaterialTheme.typography.body2,
                            color = PixelGreen
                        )
                        val discountAmount = order.subtotalCents * order.discountPercent / 100
                        Text(
                            "- ${formatPrice(discountAmount)}",
                            style = MaterialTheme.typography.body2,
                            color = PixelGreen
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Shipping",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        if (order.shippingCents == 0) "Free" else formatPrice(order.shippingCents),
                        style = MaterialTheme.typography.body2,
                        color = if (order.shippingCents == 0) PixelGreen
                        else MaterialTheme.colors.onSurface
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            PixelDivider()
            Spacer(Modifier.height(8.dp))

            // Expand/collapse toggle for item list
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "Hide cards" else "Show cards ($totalItems)",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Expandable item list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Item list header
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "QTY",
                            Modifier.width(40.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "CARD",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "SET",
                            Modifier.width(50.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "TYPE",
                            Modifier.width(60.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            "PRICE",
                            Modifier.width(70.dp),
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    PixelDivider()

                    // Constrain item list height with LazyColumn
                    val itemListState = rememberLazyListState()
                    Box(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        LazyColumn(
                            Modifier.fillMaxWidth(),
                            state = itemListState
                        ) {
                            items(
                                order.items,
                                key = { it.variant.uniqueIdentifier }
                            ) { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${item.qty}",
                                        Modifier.width(40.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        item.variant.nameOriginal,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        item.variant.setCode,
                                        Modifier.width(50.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        item.variant.variantType.displayName,
                                        Modifier.width(60.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        formatPrice(item.variant.priceInCents),
                                        Modifier.width(70.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                                PixelDivider()
                            }
                        }
                        LazyListScrollIndicators(
                            state = itemListState,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons per seller
            SellerActionButtons(
                order = order,
                onCopyToClipboard = onCopyToClipboard,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

/**
 * Per-seller action buttons for checkout and export.
 */
@Composable
private fun SellerActionButtons(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (order.seller) {
            Seller.USEA -> {
                PixelButton(
                    text = "Copy CSV",
                    onClick = {
                        val exportText = formatForExport(Seller.USEA, order.items)
                        onCopyToClipboard(exportText)
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = "Email Order",
                    onClick = {
                        val exportText = formatForExport(Seller.USEA, order.items)
                        val subject = "MTG Proxy Order - ${order.items.sumOf { it.qty }} cards"
                        val mailtoUrl = "mailto:?subject=$subject&body=$exportText"
                        onOpenUrl(mailtoUrl)
                    },
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
            Seller.BOOTLEG_MAGE -> {
                PixelButton(
                    text = "Open Deck Import",
                    onClick = { onOpenUrl(BOOTLEG_MAGE_DECK_IMPORT_URL) },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }
            Seller.TCGPLAYER -> {
                PixelButton(
                    text = "Open Mass Entry",
                    onClick = { onOpenUrl(TCGPLAYER_MASS_ENTRY_URL) },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = "Copy List",
                    onClick = {
                        val exportText = formatForExport(Seller.TCGPLAYER, order.items)
                        onCopyToClipboard(exportText)
                    },
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
