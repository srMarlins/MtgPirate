package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.*

/**
 * Calculate shipping cost based on selection and eligibility.
 */
fun calculateShippingCost(
    selectedShipping: util.Promotions.ShippingType,
    expressEligible: Boolean,
    normalShippingCost: Int
): Int {
    return when (selectedShipping) {
        util.Promotions.ShippingType.EXPRESS -> if (expressEligible) 0 else normalShippingCost
        util.Promotions.ShippingType.NORMAL -> normalShippingCost
        else -> normalShippingCost
    }
}

/**
 * Summary card showing matched/unmatched cards count.
 */
@Composable
fun exportSummaryCard(
    resolved: List<model.DeckEntryMatch>,
    unresolved: List<model.DeckEntryMatch>
) {
    FantasySectionHeader(text = "Order Summary")
    PixelCard(glowing = false) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Matched Cards:", style = MaterialTheme.typography.body2)
                Text(
                    "${resolved.size}",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Unmatched Cards:", style = MaterialTheme.typography.body2)
                Text(
                    "${unresolved.size}",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = if (unresolved.isNotEmpty()) {
                        Color(0xFFF44336)
                    } else {
                        MaterialTheme.colors.onSurface
                    }
                )
            }
            PixelDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Cards:", style = MaterialTheme.typography.body2)
                Text(
                    "${resolved.size + unresolved.size}",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Pricing card showing order value, discount, and subtotal.
 */
@Composable
fun exportPricingCard(promo: util.Promotions.Result) {
    FantasySectionHeader(text = "Pricing & Promotions")
    PixelCard(glowing = false) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Order Value:", style = MaterialTheme.typography.body2)
                Text(
                    util.formatPrice(promo.baseTotalCents),
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
            if (promo.discountPercent > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Discount (${promo.discountPercent}%):",
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        "-${util.formatPrice(promo.discountAmountCents)}",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            PixelDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Subtotal:", style = MaterialTheme.typography.body2)
                Text(
                    util.formatPrice(promo.subtotalAfterDiscountCents),
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Shipping option card with radio button selection.
 */
@Composable
fun exportShippingCard(
    selectedShipping: util.Promotions.ShippingType,
    onShippingChange: (util.Promotions.ShippingType) -> Unit,
    normalShippingCost: Int,
    expressEligible: Boolean
) {
    FantasySectionHeader(text = "Shipping Options")
    PixelCard(glowing = false) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Normal Shipping option
            shippingOptionRow(
                isSelected = selectedShipping == util.Promotions.ShippingType.NORMAL,
                onClick = { onShippingChange(util.Promotions.ShippingType.NORMAL) },
                title = "Normal Shipping",
                subtitle = buildNormalShippingSubtitle(normalShippingCost),
                isEnabled = true,
                isPrimary = true
            )

            // Express Shipping option
            shippingOptionRow(
                isSelected = selectedShipping == util.Promotions.ShippingType.EXPRESS,
                onClick = {
                    if (expressEligible) {
                        onShippingChange(util.Promotions.ShippingType.EXPRESS)
                    }
                },
                title = "Express Shipping",
                subtitle = buildExpressShippingSubtitle(expressEligible),
                isEnabled = expressEligible,
                isPrimary = false
            )
        }
    }
}

private fun buildNormalShippingSubtitle(normalShippingCost: Int): String {
    return "15-40 days • ${if (normalShippingCost == 0) "Free" else util.formatPrice(normalShippingCost)}"
}

private fun buildExpressShippingSubtitle(expressEligible: Boolean): String {
    return if (expressEligible) {
        "3-7 days • Free"
    } else {
        "Unlocks at ${util.formatPrice(300_00)} subtotal"
    }
}

/**
 * Single shipping option row with radio button.
 */
@Composable
private fun shippingOptionRow(
    isSelected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    isPrimary: Boolean
) {
    val borderColor = if (isPrimary) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pixelBorder(
                borderWidth = if (isSelected) 2.dp else 1.dp,
                enabled = isEnabled,
                glowAlpha = if (isSelected) 0.4f else 0.1f
            )
            .background(
                if (isSelected) {
                    borderColor.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colors.surface.copy(
                        alpha = if (isEnabled) 1f else 0.5f
                    )
                },
                shape = PixelShape(cornerSize = 6.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            enabled = isEnabled
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) {
                    MaterialTheme.colors.onSurface
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(
                    alpha = if (isEnabled) 0.7f else 0.5f
                )
            )
        }
    }
}
