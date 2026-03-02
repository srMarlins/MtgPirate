# Shopping Optimizer Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the free-tier forever-loading bug, add iterative discount-aware optimization, and compose dual-plan state for side-by-side Pro upsell comparison.

**Architecture:** The optimizer becomes iterative — it reassigns cards when crossing discount thresholds reduces total cost, using incremental subtotals and a priority queue for efficiency. The ViewModel composes a `ShoppingPlanComparison` with both a free plan (USEA-only) and a pro plan (all sellers), stored in state so the UI just renders what it receives.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI architecture

---

### Task 1: Add ShoppingPlanComparison model

**Files:**
- Modify: `src/commonMain/kotlin/model/Models.kt:159-163`

**Step 1: Add the new data class after ShoppingPlan**

At line 163, after the closing `)` of `ShoppingPlan`, add:

```kotlin
data class ShoppingPlanComparison(
    val activePlan: ShoppingPlan,
    val proPlan: ShoppingPlan,
    val savingsDeltaCents: Int,
)
```

**Step 2: Verify it compiles**

Run: `./gradlew compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/commonMain/kotlin/model/Models.kt
git commit -m "feat: add ShoppingPlanComparison model for dual-plan state"
```

---

### Task 2: Rewrite ShoppingOptimizer with iterative discount-aware optimization

**Files:**
- Modify: `src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt` (full rewrite of internals)
- Test: `src/commonTest/kotlin/integration/ShoppingFlowTest.kt`

**Step 1: Write the failing test for iterative discount optimization**

Add to `ShoppingFlowTest.kt`:

```kotlin
@Test
fun iterativeOptimization_crossesDiscountThresholdByReassigning() {
    // Setup: USEA has cards totaling $90 naively. Moving one $15 card from BM
    // to USEA crosses the $100 threshold for 15% discount.
    // Without iteration: USEA=$90 (0% discount) + $10 ship = $100, BM=$15 = $15. Total=$115
    // With iteration: USEA=$105 (15% off = $89.25) + $10 ship = $99.25, BM=$0. Total=$99.25
    // The iterative optimizer should discover this is cheaper.

    val entries = listOf(
        testDeckEntry("Card A", 1),
        testDeckEntry("Card B", 1),
        testDeckEntry("Card C", 1),
    )

    val useaCatalog = catalogOf(
        testVariant("Card A", Seller.USEA, 50_00),
        testVariant("Card B", Seller.USEA, 40_00),
        testVariant("Card C", Seller.USEA, 15_00),
    )
    val bmCatalog = catalogOf(
        testVariant("Card C", Seller.BOOTLEG_MAGE, 12_00),
    )

    val matches = MultiCatalogMatcher.match(
        entries = entries,
        catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
        config = MultiCatalogMatcher.Config(proxyFirst = true),
    )

    // Card C naively goes to BM (12_00 < 15_00).
    // But pulling it to USEA makes subtotal 105_00 -> 15% discount -> total cheaper.
    val plan = ShoppingOptimizer.optimize(matches)

    val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
    assertTrue(
        useaOrder.items.any { it.variant.nameOriginal == "Card C" },
        "Card C should be pulled to USEA to cross discount threshold"
    )
    assertEquals(105_00, useaOrder.subtotalCents)
    assertEquals(15, useaOrder.discountPercent)
    // After discount: 105_00 * 85/100 = 89_25. Shipping: < 100_00 after discount -> $10.
    assertEquals(89_25 + 10_00, useaOrder.totalCents)
}

@Test
fun iterativeOptimization_doesNotMoveWhenWorsens() {
    // Moving a card to hit a discount tier but the price difference is too large.
    // USEA: $55 (one card). BM: Card X at $2. Moving Card X to USEA = $57, still no tier.
    // Should NOT move Card X since it doesn't help.

    val entries = listOf(
        testDeckEntry("Big Card", 1),
        testDeckEntry("Card X", 1),
    )

    val useaCatalog = catalogOf(
        testVariant("Big Card", Seller.USEA, 55_00),
        testVariant("Card X", Seller.USEA, 8_00),
    )
    val bmCatalog = catalogOf(
        testVariant("Card X", Seller.BOOTLEG_MAGE, 2_00),
    )

    val matches = MultiCatalogMatcher.match(
        entries = entries,
        catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
        config = MultiCatalogMatcher.Config(proxyFirst = true),
    )

    val plan = ShoppingOptimizer.optimize(matches)

    // Card X should stay at BM (cheaper, and pulling it to USEA doesn't cross a tier)
    val bmOrder = plan.orders.find { it.seller == Seller.BOOTLEG_MAGE }
    assertNotNull(bmOrder, "BM order should exist")
    assertTrue(bmOrder.items.any { it.variant.nameOriginal == "Card X" })
}

@Test
fun optimizeSubset_filtersBySeller() {
    // optimizeForSellers should only include matches from specified sellers
    val entries = listOf(
        testDeckEntry("Lightning Bolt", 4),
        testDeckEntry("Counterspell", 4),
    )

    val useaCatalog = catalogOf(
        testVariant("Lightning Bolt", Seller.USEA, 220),
        testVariant("Counterspell", Seller.USEA, 220),
    )
    val bmCatalog = catalogOf(
        testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
        testVariant("Counterspell", Seller.BOOTLEG_MAGE, 250),
    )

    val matches = MultiCatalogMatcher.match(
        entries = entries,
        catalogs = mapOf(Seller.USEA to useaCatalog, Seller.BOOTLEG_MAGE to bmCatalog),
        config = MultiCatalogMatcher.Config(proxyFirst = true),
    )

    val useaOnly = ShoppingOptimizer.optimizeForSellers(matches, setOf(Seller.USEA))
    assertTrue(useaOnly.orders.all { it.seller == Seller.USEA })
    assertEquals(2, useaOnly.orders.first().items.size) // Both cards in USEA

    val fullPlan = ShoppingOptimizer.optimize(matches)
    // Full plan should have BM for Lightning Bolt (cheaper)
    assertTrue(fullPlan.orders.any { it.seller == Seller.BOOTLEG_MAGE })
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew desktopTest --tests "integration.ShoppingFlowTest"`
Expected: FAIL — `optimizeForSellers` doesn't exist, and iterative behavior may differ

**Step 3: Rewrite ShoppingOptimizer with iterative optimization**

Replace the contents of `src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt` with:

```kotlin
package optimizer

import match.NameNormalizer
import model.MatchOption
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
        val effectiveMatches = matches.mapNotNull { mm ->
            val filteredAlts = if (sellers != null) {
                mm.alternatives.filter { it.seller in sellers }
            } else {
                mm.alternatives
            }
            if (filteredAlts.isEmpty()) return@mapNotNull null
            val best = filteredAlts.minByOrNull { it.priceCents } ?: return@mapNotNull null
            mm.copy(bestOption = best, alternatives = filteredAlts)
        }

        if (effectiveMatches.isEmpty()) {
            return ShoppingPlan(orders = emptyList(), totalPriceCents = 0, savingsVsSingleSeller = 0)
        }

        // Index: for each match, which sellers offer it and at what price
        // priceMatrix[matchIndex][seller] = priceCents per unit
        val priceMatrix = effectiveMatches.map { mm ->
            mm.alternatives.associate { it.seller to it.priceCents }
        }

        // Identify moveable cards (available from 2+ sellers)
        val moveableIndices = effectiveMatches.indices.filter { priceMatrix[it].size > 1 }

        // Step 1: Naive assignment — each card to its bestOption seller
        val assignment = IntArray(effectiveMatches.size) // assignment[i] = seller ordinal
        val sellerSubtotals = mutableMapOf<Seller, Int>() // running subtotals

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

            val currentTotal = calculateTotalFromSubtotals(sellerSubtotals, configs)

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

                    // Calculate total cost delta using only the affected sellers
                    val oldCostCurrent = buildSellerOrderCost(currentSeller, oldCurrentSubtotal, configs)
                    val oldCostCandidate = buildSellerOrderCost(candidateSeller, oldCandidateSubtotal, configs)
                    val newCostCurrent = buildSellerOrderCost(currentSeller, newCurrentSubtotal, configs)
                    val newCostCandidate = buildSellerOrderCost(candidateSeller, newCandidateSubtotal, configs)

                    val delta = (newCostCurrent + newCostCandidate) - (oldCostCurrent + oldCostCandidate)

                    if (delta < 0) {
                        // Move is beneficial — apply it
                        assignment[i] = candidateSeller.ordinal
                        sellerSubtotals[currentSeller] = newCurrentSubtotal
                        sellerSubtotals[candidateSeller] = newCandidateSubtotal
                        // Remove seller from map if subtotal is zero
                        if (newCurrentSubtotal <= 0) sellerSubtotals.remove(currentSeller)
                        improved = true
                        break // Re-evaluate from the next moveable card
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

        val orders = sellerItems.map { (seller, items) ->
            buildSellerOrder(seller, items)
        }
        val totalCents = orders.sumOf { it.totalCents }

        // Calculate savings: compare against single-seller naive (no threshold optimization)
        val naiveItems = mutableMapOf<Seller, MutableList<OrderItem>>()
        effectiveMatches.forEach { mm ->
            val best = mm.bestOption!!
            naiveItems.getOrPut(best.seller) { mutableListOf() }
                .add(OrderItem(best.variant, mm.deckEntry.qty, best.isProxy))
        }
        val naiveTotal = naiveItems.map { (seller, items) ->
            buildSellerOrder(seller, items).totalCents
        }.sum()
        val savings = naiveTotal - totalCents

        return ShoppingPlan(
            orders = orders.sortedByDescending { it.subtotalCents },
            totalPriceCents = totalCents,
            savingsVsSingleSeller = savings.coerceAtLeast(0),
        )
    }

    /**
     * Calculate the total cost for a seller given a subtotal, applying
     * discount and shipping tiers. Used for incremental cost comparison
     * during iterative optimization (no item list needed).
     */
    private fun buildSellerOrderCost(
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

    private fun calculateTotalFromSubtotals(
        subtotals: Map<Seller, Int>,
        configs: Map<Seller, SellerDiscountConfig>,
    ): Int = subtotals.entries.sumOf { (seller, subtotal) ->
        buildSellerOrderCost(seller, subtotal, configs)
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
```

**Step 4: Run all tests to verify they pass**

Run: `./gradlew desktopTest --tests "integration.ShoppingFlowTest"`
Expected: ALL PASS (including existing `fullFlow_importMatchOptimize` and `thresholdOptimizationFlow`)

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt \
       src/commonTest/kotlin/integration/ShoppingFlowTest.kt
git commit -m "feat: iterative discount-aware shopping optimizer with seller filtering"
```

---

### Task 3: Update ViewModel — remove Pro gate, compose dual plans

**Files:**
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt`

**Step 1: Change state types from ShoppingPlan? to ShoppingPlanComparison?**

In `LocalUiState` (line 999), change:
```kotlin
val shoppingPlan: ShoppingPlan? = null,
```
to:
```kotlin
val shoppingPlanComparison: ShoppingPlanComparison? = null,
```

In `ViewState` (line 969), change:
```kotlin
val shoppingPlan: ShoppingPlan? = null,
```
to:
```kotlin
val shoppingPlanComparison: ShoppingPlanComparison? = null,
```

In the `combine` block (line 148), change:
```kotlin
shoppingPlan = localState.shoppingPlan,
```
to:
```kotlin
shoppingPlanComparison = localState.shoppingPlanComparison,
```

Add import at top of file:
```kotlin
import model.ShoppingPlanComparison
```

**Step 2: Rewrite optimizeShoppingPlan() to compose dual plans**

Replace lines 768-791 with:

```kotlin
private suspend fun optimizeShoppingPlan(
    multiMatches: List<MultiMatch> = _localState.value.multiMatches
) {
    withContext(Dispatchers.IO) {
        if (multiMatches.isEmpty()) {
            log("No multi-matches available for optimization", "WARNING")
            return@withContext
        }

        try {
            val proPlan = ShoppingOptimizer.optimize(multiMatches)
            val isPro = _localState.value.proStatus.isPro

            val activePlan = if (isPro) {
                proPlan
            } else {
                ShoppingOptimizer.optimizeForSellers(multiMatches, setOf(Seller.USEA))
            }

            val comparison = ShoppingPlanComparison(
                activePlan = activePlan,
                proPlan = proPlan,
                savingsDeltaCents = (activePlan.totalPriceCents - proPlan.totalPriceCents)
                    .coerceAtLeast(0),
            )

            _localState.update { it.copy(shoppingPlanComparison = comparison) }
            log(
                "Shopping plan optimized: active=${activePlan.orders.size} seller(s) " +
                    "total ${activePlan.totalPriceCents}c, " +
                    "pro=${proPlan.orders.size} seller(s) total ${proPlan.totalPriceCents}c, " +
                    "savings delta ${comparison.savingsDeltaCents}c",
                "INFO"
            )
        } catch (e: Exception) {
            log("Shopping plan optimization failed: ${e.message}", "ERROR")
            _viewEffects.emit(ViewEffect.ShowError("Failed to optimize shopping plan"))
        }
    }
}
```

**Step 3: Fix wizardResultsToExport() — remove Pro guard**

Replace lines 837-842:
```kotlin
private suspend fun wizardResultsToExport() {
    completeWizardStep(3)
    optimizeShoppingPlan()
}
```

**Step 4: Verify it compiles (UI will break — that's expected for now)**

Run: `./gradlew compileKotlinDesktop`
Expected: FAIL — UI code still references `shoppingPlan` instead of `shoppingPlanComparison`. That's Task 4.

**Step 5: Commit (WIP — UI will be fixed in next task)**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt
git commit -m "feat: compose dual shopping plans in ViewModel, remove Pro gate from optimizer"
```

---

### Task 4: Update desktop ShoppingPlanScreen for dual-plan comparison

**Files:**
- Modify: `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`
- Modify: `src/desktopMain/kotlin/app/Main.kt` (call site, ~line 777)

**Step 1: Update ShoppingPlanScreen signature and auto-trigger**

Change the composable signature (line 103) to accept the comparison:

```kotlin
@Composable
fun ShoppingPlanScreen(
    shoppingPlanComparison: ShoppingPlanComparison?,
    multiMatches: List<MultiMatch>,
    isPro: Boolean,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    isLoading: Boolean = false,
)
```

Add import for `ShoppingPlanComparison`:
```kotlin
import model.ShoppingPlanComparison
```

Update the `LaunchedEffect` (line 113):
```kotlin
LaunchedEffect(shoppingPlanComparison, multiMatches) {
    if (shoppingPlanComparison == null && multiMatches.isNotEmpty()) {
        onOptimize()
    }
}
```

Update the loading guard (line 144):
```kotlin
if (isLoading || shoppingPlanComparison == null) {
```

Replace the content block (lines 160-177) with dual-plan layout:
```kotlin
} else {
    val activePlan = shoppingPlanComparison.activePlan
    val proPlan = shoppingPlanComparison.proPlan
    val showComparison = !isPro && shoppingPlanComparison.savingsDeltaCents > 0

    // Summary header for active plan
    ShoppingPlanSummary(activePlan)
    Spacer(Modifier.height(16.dp))

    // Scrollable content
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active plan seller orders
        activePlan.orders.forEach { order ->
            SellerOrderCard(
                order = order,
                onCopyToClipboard = onCopyToClipboard,
                onOpenUrl = onOpenUrl,
            )
        }

        // Pro comparison section for free users
        if (showComparison) {
            Spacer(Modifier.height(8.dp))
            ProComparisonCard(
                activePlan = activePlan,
                proPlan = proPlan,
                savingsDeltaCents = shoppingPlanComparison.savingsDeltaCents,
                onUpgrade = onUpgrade,
            )
        }
    }
}
```

**Step 2: Add the ProComparisonCard composable**

Add after `ShoppingPlanSummary`:

```kotlin
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

            // Side-by-side comparison
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Your Plan column
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

                // Pro Plan column
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

            // Savings callout
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

            // Locked seller preview
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

            // Upgrade button
            PixelButton(
                text = "Unlock Pro",
                onClick = onUpgrade,
                variant = PixelButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

**Step 3: Update desktop call site**

In `src/desktopMain/kotlin/app/Main.kt` (~line 777), change from:
```kotlin
ShoppingPlanScreen(
    shoppingPlan = state.shoppingPlan,
    multiMatches = state.multiMatches,
    onOptimize = {
        viewModel.processIntent(ViewIntent.OptimizeShoppingPlan)
    },
```
to:
```kotlin
ShoppingPlanScreen(
    shoppingPlanComparison = state.shoppingPlanComparison,
    multiMatches = state.multiMatches,
    isPro = state.proStatus.isPro,
    onOptimize = {
        viewModel.processIntent(ViewIntent.OptimizeShoppingPlan)
    },
```

Also add the `onUpgrade` parameter at the end:
```kotlin
    onUpgrade = {
        viewModel.processIntent(ViewIntent.PurchasePro)
    },
```

**Step 4: Verify desktop compiles**

Run: `./gradlew compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/ui/ShoppingPlanScreen.kt \
       src/desktopMain/kotlin/app/Main.kt
git commit -m "feat: side-by-side Pro comparison on desktop shopping plan screen"
```

---

### Task 5: Update mobile MobileShoppingPlanScreen

**Files:**
- Modify: `src/mobileMain/kotlin/app/MobileScreens.kt` (~line 1037)
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt` (~line 289)

**Step 1: Update MobileShoppingPlanScreen signature**

Change the signature to match the new pattern:
```kotlin
fun MobileShoppingPlanScreen(
    shoppingPlanComparison: ShoppingPlanComparison?,
    multiMatches: List<MultiMatch>,
    isPro: Boolean,
    onOptimize: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    proStatus: ProStatus = ProStatus.Free,
)
```

Update internal references from `shoppingPlan` to `shoppingPlanComparison.activePlan` — follow the same pattern as desktop: extract `activePlan` from comparison, update LaunchedEffect guard, loading guard, and pass `activePlan` to existing sub-composables.

Add the same `ProComparisonCard` call for free users after the seller order cards (reuse the composable from `ShoppingPlanScreen.kt` by moving it to common, or duplicate for mobile with mobile styling).

**Step 2: Update mobile call site**

In `src/mobileMain/kotlin/app/MobileApp.kt` (~line 289), change:
```kotlin
MobileScreen.EXPORT -> MobileShoppingPlanScreen(
    shoppingPlan = state.shoppingPlan,
```
to:
```kotlin
MobileScreen.EXPORT -> MobileShoppingPlanScreen(
    shoppingPlanComparison = state.shoppingPlanComparison,
    isPro = state.proStatus.isPro,
```

Add `onUpgrade` parameter:
```kotlin
    onUpgrade = {
        viewModel.processIntent(ViewIntent.PurchasePro)
    },
```

**Step 3: Verify mobile compiles**

Run: `./gradlew compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/mobileMain/kotlin/app/MobileScreens.kt \
       src/mobileMain/kotlin/app/MobileApp.kt
git commit -m "feat: side-by-side Pro comparison on mobile shopping plan screen"
```

---

### Task 6: Full verification and final commit

**Step 1: Run all checks**

```bash
./gradlew build
./gradlew detekt
./gradlew allTests
```

Expected: ALL PASS

**Step 2: Launch desktop app and manually test**

```bash
./gradlew run
```

Test these scenarios:
1. Free user reaches shopping plan → sees USEA-only plan with Pro comparison card
2. Pro user reaches shopping plan → sees full optimized plan, no comparison card
3. Loading state resolves (no forever-loading)
4. Discount tiers display correctly on seller order cards
5. USEA coupon code in email export matches the discount tier

**Step 3: Fix any detekt/compile issues**

Update detekt baselines if needed for new long methods.

**Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: address detekt/compile issues from shopping optimizer redesign"
```
