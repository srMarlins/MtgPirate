package optimizer

import match.NameNormalizer
import model.MatchOption
import model.MultiMatch
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan

/**
 * Optimizer that assigns card matches to sellers to minimize the total order cost.
 * Handles bulk discounts and shipping thresholds.
 */
object ShoppingOptimizer {

    fun optimize(matches: List<MultiMatch>): ShoppingPlan {
        // Step 1: Naive assignment — each card to its bestOption seller
        val naiveAssignment = mutableMapOf<Seller, MutableList<OrderItem>>()
        matches.forEach { mm ->
            val best = mm.bestOption ?: return@forEach
            naiveAssignment.getOrPut(best.seller) { mutableListOf() }
                .add(OrderItem(best.variant, mm.deckEntry.qty, best.isProxy))
        }

        // Step 2: Try threshold optimization
        val optimized = tryThresholdOptimization(matches, naiveAssignment)

        // Step 3: Build SellerOrders with discount calculations
        val orders = optimized.map { (seller, items) ->
            buildSellerOrder(seller, items)
        }

        val totalCents = orders.sumOf { it.totalCents }

        // Calculate savings vs worst-case (naive assignment without threshold optimization)
        val naiveOrders = naiveAssignment.map { (seller, items) ->
            buildSellerOrder(seller, items)
        }
        val naiveTotal = naiveOrders.sumOf { it.totalCents }
        val savings = naiveTotal - totalCents

        return ShoppingPlan(
            orders = orders.sortedByDescending { it.subtotalCents },
            totalPriceCents = totalCents,
            savingsVsSingleSeller = savings.coerceAtLeast(0),
        )
    }

    private fun buildSellerOrder(seller: Seller, items: List<OrderItem>): SellerOrder {
        val config = getDiscountConfig(seller)
        val subtotal = items.sumOf { it.variant.priceInCents * it.qty }

        // >= so hitting exactly the threshold qualifies (e.g., $400.00 gets 50%)
        val discountPercent = config.discountTiers
            .sortedByDescending { it.minCents }
            .firstOrNull { subtotal >= it.minCents }?.discountPercent ?: 0
        val afterDiscount = subtotal * (100 - discountPercent) / 100

        val shippingCents = config.shippingTiers
            .sortedByDescending { it.minCents }
            .firstOrNull { afterDiscount >= it.minCents }?.shippingCents ?: 0

        return SellerOrder(
            seller = seller,
            items = items,
            subtotalCents = subtotal,
            discountPercent = discountPercent,
            shippingCents = shippingCents,
            totalCents = afterDiscount + shippingCents,
        )
    }

    private fun tryThresholdOptimization(
        matches: List<MultiMatch>,
        naiveAssignment: Map<Seller, List<OrderItem>>,
    ): Map<Seller, List<OrderItem>> {
        var bestPlan = naiveAssignment.mapValues { it.value.toMutableList() }
        var bestTotal = calculateTotal(bestPlan)

        // For each seller, check if pulling cards from other sellers
        // would push it to a better discount tier
        for (seller in naiveAssignment.keys) {
            val config = getDiscountConfig(seller)
            val currentSubtotal = bestPlan[seller]?.sumOf {
                it.variant.priceInCents * it.qty
            } ?: 0

            // Find all potential discount tiers above current subtotal
            val betterTiers = config.discountTiers
                .filter { it.minCents > currentSubtotal }
                .sortedBy { it.minCents }

            for (nextTier in betterTiers) {
                val deficit = nextTier.minCents - currentSubtotal // Need to reach threshold

                // Find cards from other sellers that are also available from this seller
                val pullable = matches.filter { mm ->
                    val currentBest = mm.bestOption ?: return@filter false
                    // Find which seller this card is currently assigned to in bestPlan
                    val assignedSeller = findAssignedSeller(mm, bestPlan)
                    assignedSeller != null && assignedSeller != seller &&
                        mm.alternatives.any { it.seller == seller }
                }.sortedBy { mm ->
                    // Prefer pulling cards with smallest price difference
                    val altPrice = mm.alternatives.first { it.seller == seller }.priceCents
                    val currentAssignedSeller = findAssignedSeller(mm, bestPlan)!!
                    val currentPrice = mm.alternatives.first { it.seller == currentAssignedSeller }.priceCents
                    (altPrice - currentPrice) * mm.deckEntry.qty
                }

                // Try pulling cards until we hit the threshold
                var pulledAmount = 0
                val candidatePlan = bestPlan.mapValues { it.value.toMutableList() }.toMutableMap()
                val cardsToMove = mutableListOf<Pair<MultiMatch, MatchOption>>()

                for (mm in pullable) {
                    if (pulledAmount >= deficit) break
                    val alt = mm.alternatives.first { it.seller == seller }
                    cardsToMove.add(mm to alt)
                    pulledAmount += alt.priceCents * mm.deckEntry.qty
                }

                if (pulledAmount >= deficit) {
                    // Apply the moves
                    for ((mm, alt) in cardsToMove) {
                        val currentAssignedSeller = findAssignedSeller(mm, candidatePlan)!!
                        candidatePlan[currentAssignedSeller]?.removeAll {
                            it.variant.nameNormalized == NameNormalizer.normalize(mm.deckEntry.cardName) &&
                            it.variant.seller == currentAssignedSeller
                        }
                        val targetList = candidatePlan[seller] ?: mutableListOf<OrderItem>().also {
                            candidatePlan[seller] = it
                        }
                        targetList.add(OrderItem(alt.variant, mm.deckEntry.qty, alt.isProxy))
                    }

                    val candidateTotal = calculateTotal(candidatePlan)
                    if (candidateTotal < bestTotal) {
                        bestPlan = candidatePlan
                        bestTotal = candidateTotal
                    }
                }
            }
        }

        return bestPlan
    }

    private fun findAssignedSeller(mm: MultiMatch, plan: Map<Seller, List<OrderItem>>): Seller? {
        val normalizedName = NameNormalizer.normalize(mm.deckEntry.cardName)
        return plan.entries.find { (_, items) ->
            items.any { it.variant.nameNormalized == normalizedName }
        }?.key
    }

    private fun calculateTotal(plan: Map<Seller, List<OrderItem>>): Int {
        return plan.map { (seller, items) ->
            buildSellerOrder(seller, items).totalCents
        }.sum()
    }
}
