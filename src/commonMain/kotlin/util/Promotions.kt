package util

import model.DeckEntryMatch

/**
 * Promotion & shipping calculator for export flow.
 * Pure Kotlin (common) so it can be used from UI and exporter.
 */
object Promotions {
    data class Result(
        val baseTotalCents: Int,
        val discountPercent: Int, // 0, 5, 15, 25, 30, 35, 50
        val discountAmountCents: Int,
        val subtotalAfterDiscountCents: Int,
        val shippingType: ShippingType,
        val shippingCostCents: Int,
        val grandTotalCents: Int
    )

    enum class ShippingType { NORMAL, EXPRESS }

    /**
     * Calculate promotion tiers based on the provided selected matches.
     * Only counts matches with a selected variant.
     */
    fun calculate(matches: List<DeckEntryMatch>): Result {
        val baseTotalCents = matches
            .filter { it.selectedVariant != null }
            .sumOf { it.selectedVariant!!.priceInCents * it.deckEntry.qty }

        val discountPercent = when {
            baseTotalCents > 400_00 -> 50
            baseTotalCents > 300_00 -> 35
            baseTotalCents > 200_00 -> 30
            baseTotalCents > 160_00 -> 25
            baseTotalCents > 100_00 -> 15
            baseTotalCents > 60_00 -> 5
            else -> 0
        }
        val discountAmountCents = (baseTotalCents * discountPercent) / 100
        val subtotalAfterDiscountCents = baseTotalCents - discountAmountCents

        // Shipping rules based on subtotal after discount
        val (shippingType, shippingCostCents) = when {
            subtotalAfterDiscountCents > 300_00 -> ShippingType.EXPRESS to 0
            subtotalAfterDiscountCents > 100_00 -> ShippingType.NORMAL to 0
            else -> ShippingType.NORMAL to 10_00
        }
        val grandTotalCents = subtotalAfterDiscountCents + shippingCostCents

        return Result(
            baseTotalCents = baseTotalCents,
            discountPercent = discountPercent,
            discountAmountCents = discountAmountCents,
            subtotalAfterDiscountCents = subtotalAfterDiscountCents,
            shippingType = shippingType,
            shippingCostCents = shippingCostCents,
            grandTotalCents = grandTotalCents
        )
    }
}
