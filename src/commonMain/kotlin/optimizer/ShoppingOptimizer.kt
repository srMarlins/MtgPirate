package optimizer

import model.MultiMatch
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan

/**
 * Optimizer that assigns card matches to sellers to minimize total order cost.
 * Uses iterative discount-aware optimization: reassigns cards when crossing
 * discount thresholds reduces total cost.
 */
object ShoppingOptimizer {

    private const val MAX_ITERATIONS = 10

    /**
     * Optimize across all sellers in the match list.
     */
    fun optimize(matches: List<MultiMatch>): ShoppingPlan =
        optimizeForSellers(matches, sellers = null)

    /**
     * Optimize restricting to a subset of sellers.
     * Cards without any alternative in [sellers] are dropped from the plan.
     * Pass `null` to use all sellers (same as [optimize]).
     */
    fun optimizeForSellers(
        matches: List<MultiMatch>,
        sellers: Set<Seller>?,
    ): ShoppingPlan {
        // Build the effective match list: filter alternatives to allowed sellers
        // and recompute bestOption within that subset.
        // When sellers is null (optimize all), preserve the original bestOption
        // to respect user overrides from the UI.
        val effectiveMatches = matches.mapNotNull { mm ->
            if (sellers == null) {
                // No filtering — keep original bestOption (may be a user override)
                if (mm.bestOption == null || mm.alternatives.isEmpty()) return@mapNotNull null
                mm
            } else {
                val filteredAlts = mm.alternatives.filter { it.seller in sellers }
                if (filteredAlts.isEmpty()) return@mapNotNull null
                val best = filteredAlts.minByOrNull { it.priceCents } ?: return@mapNotNull null
                mm.copy(bestOption = best, alternatives = filteredAlts)
            }
        }

        if (effectiveMatches.isEmpty()) {
            return ShoppingPlan(orders = emptyList(), totalPriceCents = 0, savingsVsSingleSeller = 0)
        }

        // Index: for each match, which sellers offer it and at what price
        val priceMatrix = effectiveMatches.map { mm ->
            mm.alternatives.associate { it.seller to it.priceCents }
        }

        // Identify moveable cards (available from 2+ sellers and not user-pinned).
        // A card is pinned if its bestOption is NOT the cheapest alternative,
        // indicating the user explicitly chose a more expensive seller.
        val moveableIndices = effectiveMatches.indices.filter { i ->
            val mm = effectiveMatches[i]
            val cheapest = priceMatrix[i].values.minOrNull() ?: return@filter false
            priceMatrix[i].size > 1 && mm.bestOption!!.priceCents <= cheapest
        }

        // Step 1: Naive assignment — each card to its bestOption seller
        val assignment = IntArray(effectiveMatches.size)
        val sellerSubtotals = mutableMapOf<Seller, Int>()

        effectiveMatches.forEachIndexed { i, mm ->
            val seller = mm.bestOption!!.seller
            assignment[i] = seller.ordinal
            val amount = mm.bestOption!!.priceCents * mm.deckEntry.qty
            sellerSubtotals[seller] = (sellerSubtotals[seller] ?: 0) + amount
        }

        // Cache discount configs
        val configs = Seller.entries.associateWith { getDiscountConfig(it) }

        // Step 2: Iterative optimization
        var improved = true
        var iterations = 0

        while (improved && iterations < MAX_ITERATIONS) {
            improved = false
            iterations++

            for (i in moveableIndices) {
                val mm = effectiveMatches[i]
                val currentSeller = Seller.entries[assignment[i]]
                val qty = mm.deckEntry.qty
                val currentPrice = priceMatrix[i][currentSeller]!! * qty

                for ((candidateSeller, unitPrice) in priceMatrix[i]) {
                    if (candidateSeller == currentSeller) continue
                    val candidatePrice = unitPrice * qty

                    // Simulate the move: subtract from current, add to candidate
                    val oldCurrentSubtotal = sellerSubtotals[currentSeller] ?: 0
                    val oldCandidateSubtotal = sellerSubtotals[candidateSeller] ?: 0
                    val newCurrentSubtotal = oldCurrentSubtotal - currentPrice
                    val newCandidateSubtotal = oldCandidateSubtotal + candidatePrice

                    // Calculate cost delta using only affected sellers
                    val oldCost = sellerOrderCost(currentSeller, oldCurrentSubtotal, configs) +
                        sellerOrderCost(candidateSeller, oldCandidateSubtotal, configs)
                    val newCost = sellerOrderCost(currentSeller, newCurrentSubtotal, configs) +
                        sellerOrderCost(candidateSeller, newCandidateSubtotal, configs)

                    if (newCost < oldCost) {
                        assignment[i] = candidateSeller.ordinal
                        sellerSubtotals[currentSeller] = newCurrentSubtotal
                        sellerSubtotals[candidateSeller] = newCandidateSubtotal
                        if (newCurrentSubtotal <= 0) sellerSubtotals.remove(currentSeller)
                        improved = true
                        break
                    }
                }
            }
        }

        // Step 3: Build final plan from assignments
        val sellerItems = mutableMapOf<Seller, MutableList<OrderItem>>()
        effectiveMatches.forEachIndexed { i, mm ->
            val seller = Seller.entries[assignment[i]]
            val alt = mm.alternatives.first { it.seller == seller }
            sellerItems.getOrPut(seller) { mutableListOf() }
                .add(OrderItem(alt.variant, mm.deckEntry.qty, alt.isProxy))
        }

        val orders = sellerItems.map { (seller, items) -> buildSellerOrder(seller, items) }
        val totalCents = orders.sumOf { it.totalCents }

        // Savings vs naive (no optimization)
        val naiveItems = mutableMapOf<Seller, MutableList<OrderItem>>()
        effectiveMatches.forEach { mm ->
            val best = mm.bestOption!!
            naiveItems.getOrPut(best.seller) { mutableListOf() }
                .add(OrderItem(best.variant, mm.deckEntry.qty, best.isProxy))
        }
        val naiveTotal = naiveItems.map { (seller, items) ->
            buildSellerOrder(seller, items).totalCents
        }.sum()

        return ShoppingPlan(
            orders = orders.sortedByDescending { it.subtotalCents },
            totalPriceCents = totalCents,
            savingsVsSingleSeller = (naiveTotal - totalCents).coerceAtLeast(0),
        )
    }

    /**
     * Calculate total cost for a seller at a given subtotal (discount + shipping).
     * Used for incremental cost comparison during iterative optimization.
     */
    private fun sellerOrderCost(
        seller: Seller,
        subtotalCents: Int,
        configs: Map<Seller, SellerDiscountConfig>,
    ): Int {
        if (subtotalCents <= 0) return 0
        val config = configs[seller] ?: return subtotalCents
        val discountPercent = config.discountTiers
            .sortedByDescending { it.minCents }
            .firstOrNull { subtotalCents >= it.minCents }?.discountPercent ?: 0
        val afterDiscount = subtotalCents * (100 - discountPercent) / 100
        val shippingCents = config.shippingTiers
            .sortedByDescending { it.minCents }
            .firstOrNull { afterDiscount >= it.minCents }?.shippingCents ?: 0
        return afterDiscount + shippingCents
    }

    private fun buildSellerOrder(seller: Seller, items: List<OrderItem>): SellerOrder {
        val config = getDiscountConfig(seller)
        val subtotal = items.sumOf { it.variant.priceInCents * it.qty }

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
}
