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
import androidx.compose.ui.unit.sp
import model.MultiMatch
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan
import model.ShoppingPlanComparison
import util.buildManaPoolUrl
import util.buildTcgPlayerUrl
import util.encodeUrlParameter
import util.formatPrice
import util.sellerCheckoutUrl

/**
 * Returns the themed color for a given seller.
 */
fun sellerColor(seller: Seller): Color = when (seller) {
    Seller.USEA -> SellerUsea
    Seller.BOOTLEG_MAGE -> SellerBootlegMage
    Seller.TCGPLAYER -> SellerTcgPlayer
    Seller.MANAPOOL -> SellerManaPool
}

/**
 * Format order items for export to a given seller.
 * Duplicates the logic from CatalogSource implementations so the UI layer
 * can generate export text without needing a CatalogSource reference.
 */
internal fun formatForExport(seller: Seller, items: List<OrderItem>): String = when (seller) {
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
    Seller.TCGPLAYER,
    Seller.MANAPOOL -> {
        // Mass entry format: "1 Lightning Bolt [M11]"
        items.joinToString("\n") {
            "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
        }
    }
}

/**
 * Select the best USEA coupon code for the given subtotal (in cents).
 * Returns null if no coupon threshold is met.
 */
internal fun useaCouponCode(subtotalCents: Int): String? = when {
    subtotalCents > 1000_00 -> "c56for1000"
    subtotalCents > 400_00 -> "c50for400"
    subtotalCents > 300_00 -> "c35for300"
    subtotalCents > 200_00 -> "c30for200"
    subtotalCents > 160_00 -> "c25for160"
    subtotalCents > 100_00 -> "c15for100"
    subtotalCents > 60_00 -> "c5for60"
    else -> null
}

/**
 * Format order items as tab-separated values matching the USEA Google Sheet Cart tab columns.
 * Columns: Card Name (with set), Set, SKU, Card Type (with dash prefix), Qty, Base Price
 */
internal fun formatForGoogleSheet(items: List<OrderItem>): String {
    val header = "Card Name\tSet\tSKU\tCard Type\tQty\tBase Price"
    val rows = items.joinToString("\n") { item ->
        val v = item.variant
        val cardType = "- ${v.variantType.displayName}"
        "${v.nameOriginal} ${v.setCode}\t${v.setCode}\t${v.sku}\t$cardType\t${item.qty}\t${formatPrice(v.priceInCents)}"
    }
    return "$header\n$rows"
}

/**
 * Shopping Plan screen (Step 4) - shows the optimized multi-seller shopping plan
 * with per-seller order cards, expandable item lists, and checkout action buttons.
 */
@Composable
fun ShoppingPlanScreen(
    shoppingPlanComparison: ShoppingPlanComparison?,
    multiMatches: List<MultiMatch>,
    isPro: Boolean,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckoutUsea: (SellerOrder) -> Unit = {},
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    isLoading: Boolean = false,
) {
    // Trigger optimization if we have multi-matches but no plan yet
    LaunchedEffect(shoppingPlanComparison, multiMatches) {
        if (shoppingPlanComparison == null && multiMatches.isNotEmpty()) {
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

            if (isLoading || shoppingPlanComparison == null) {
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
                val activePlan = shoppingPlanComparison.activePlan
                val proPlan = shoppingPlanComparison.proPlan

                ShoppingPlanSummary(activePlan)

                // Missing card warning for free users
                if (activePlan.droppedCardCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    PixelCard {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("\u26A0", fontSize = 18.sp)
                            Column {
                                Text(
                                    "${activePlan.droppedCardCount} card(s) not available at your seller",
                                    style = MaterialTheme.typography.body2,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.error,
                                )
                                Text(
                                    "Upgrade to Pro for multi-seller support",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isPro) {
                    // Pro users: single-column layout
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activePlan.orders.forEach { order ->
                            SellerOrderCard(
                                order = order,
                                onCopyToClipboard = onCopyToClipboard,
                                onOpenUrl = onOpenUrl,
                                onCheckoutUsea = onCheckoutUsea,
                            )
                        }
                    }
                } else {
                    // Free users: side-by-side comparison layout
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left column: user's current plan (functional)
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "YOUR PLAN",
                                style = MaterialTheme.typography.subtitle1,
                                color = MaterialTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            activePlan.orders.forEach { order ->
                                SellerOrderCard(
                                    order = order,
                                    onCopyToClipboard = onCopyToClipboard,
                                    onOpenUrl = onOpenUrl,
                                    onCheckoutUsea = onCheckoutUsea,
                                )
                            }
                        }

                        // Right column: Pro plan (dimmed with overlay)
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "PRO PLAN",
                                        style = MaterialTheme.typography.subtitle1,
                                        color = PixelGreen,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    ProBadge()
                                }
                                proPlan.orders.forEach { order ->
                                    SellerOrderCard(
                                        order = order,
                                        onCopyToClipboard = {},
                                        onOpenUrl = {},
                                        onCheckoutUsea = {},
                                    )
                                }
                            }

                            // Dimming overlay
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colors.background.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelButton(
                                    text = "Unlock Pro — Save ${formatPrice(shoppingPlanComparison.savingsDeltaCents)}",
                                    onClick = onUpgrade,
                                    variant = PixelButtonVariant.PRIMARY,
                                )
                            }
                        }
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
 * Comparison card showing potential savings with Pro.
 * Displays side-by-side plan totals, savings callout, locked seller previews,
 * and an upgrade button.
 */
@Composable
private fun ProComparisonCard(
    activePlan: ShoppingPlan,
    proPlan: ShoppingPlan,
    savingsDeltaCents: Int,
    onUpgrade: () -> Unit,
) {
    PixelCard(glowing = true) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "SAVE WITH DECKLOOT PRO",
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold
            )

            PixelDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Your Plan",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        formatPrice(activePlan.totalPriceCents),
                        style = MaterialTheme.typography.h5,
                        color = MaterialTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${activePlan.orders.size} seller(s)",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Pro Plan",
                        style = MaterialTheme.typography.subtitle2,
                        color = PixelGreen
                    )
                    Text(
                        formatPrice(proPlan.totalPriceCents),
                        style = MaterialTheme.typography.h5,
                        color = PixelGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${proPlan.orders.size} seller(s)",
                        style = MaterialTheme.typography.caption,
                        color = PixelGreen.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(PixelGreen.copy(alpha = 0.1f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "You could save ${formatPrice(savingsDeltaCents)} with Pro",
                    style = MaterialTheme.typography.body1,
                    color = PixelGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            proPlan.orders
                .filter { order -> activePlan.orders.none { it.seller == order.seller } }
                .forEach { lockedOrder ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelBadge(
                                text = lockedOrder.seller.displayName,
                                color = sellerColor(lockedOrder.seller)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${lockedOrder.items.sumOf { it.qty }} cards",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            formatPrice(lockedOrder.totalCents),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

            PixelButton(
                text = "Unlock Pro",
                onClick = onUpgrade,
                variant = PixelButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
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
    onCheckoutUsea: (SellerOrder) -> Unit = {},
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
                                val hasLink = item.variant.purchaseUri != null
                                Row(
                                    Modifier.fillMaxWidth()
                                        .then(
                                            if (hasLink) Modifier.clickable {
                                                onOpenUrl(item.variant.purchaseUri!!)
                                            } else Modifier
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${item.qty}",
                                        Modifier.width(40.dp),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.variant.nameOriginal,
                                            style = MaterialTheme.typography.body2,
                                            color = if (hasLink) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                                        )
                                        if (hasLink) {
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "\u2197",
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.primary.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
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
                onCheckoutUsea = onCheckoutUsea,
            )
        }
    }
}

/**
 * Per-seller action buttons for checkout and export.
 * Primary "Buy" button copies the formatted list to clipboard AND opens the seller's page.
 */
@Composable
private fun SellerActionButtons(
    order: SellerOrder,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckoutUsea: (SellerOrder) -> Unit = {},
) {
    var copied by remember { mutableStateOf(false) }

    // Auto-reset "Copied!" feedback after 2 seconds
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (order.seller) {
            Seller.USEA -> {
                // Desktop: clipboard + Google Sheet fallback
                PixelButton(
                    text = "Copy for Sheet",
                    onClick = {
                        onCopyToClipboard(formatForGoogleSheet(order.items))
                        copied = true
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = if (copied) "Copied!" else "Copy CSV",
                    onClick = {
                        onCopyToClipboard(formatForExport(Seller.USEA, order.items))
                        copied = true
                    },
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f)
                )
            }
            Seller.BOOTLEG_MAGE -> {
                PixelButton(
                    text = "Buy on Bootleg Mage",
                    onClick = {
                        onCopyToClipboard(formatForExport(Seller.BOOTLEG_MAGE, order.items))
                        sellerCheckoutUrl(Seller.BOOTLEG_MAGE, order.items)?.let { onOpenUrl(it) }
                    },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = if (copied) "Copied!" else "Copy List",
                    onClick = {
                        onCopyToClipboard(formatForExport(Seller.BOOTLEG_MAGE, order.items))
                        copied = true
                    },
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f)
                )
            }
            Seller.TCGPLAYER -> {
                PixelButton(
                    text = "Buy on TCGPlayer",
                    onClick = { onOpenUrl(buildTcgPlayerUrl(order.items)) },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = if (copied) "Copied!" else "Copy List",
                    onClick = {
                        onCopyToClipboard(formatForExport(Seller.TCGPLAYER, order.items))
                        copied = true
                    },
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f)
                )
            }
            Seller.MANAPOOL -> {
                PixelButton(
                    text = "Buy on ManaPool",
                    onClick = { onOpenUrl(buildManaPoolUrl(order.items)) },
                    variant = PixelButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    text = if (copied) "Copied!" else "Copy List",
                    onClick = {
                        onCopyToClipboard(formatForExport(Seller.MANAPOOL, order.items))
                        copied = true
                    },
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
