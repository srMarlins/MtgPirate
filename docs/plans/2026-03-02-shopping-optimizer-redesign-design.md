# Design: Shopping Plan for All Tiers + Iterative Coupon Optimization

**Date:** 2026-03-02
**Issue:** Free-tier shopping plan hangs forever; optimizer ignores discount tiers during assignment; no side-by-side Pro upsell

## Problem Statement

Three connected issues:

1. **Forever-loading bug:** `optimizeShoppingPlan()` calls `requirePro()` which returns early for free users without setting `shoppingPlan` or clearing loading. `ShoppingPlanScreen` waits forever.
2. **Discount-blind assignment:** Optimizer assigns cards to sellers, then applies discounts after. It doesn't know that moving a few cards to USEA could cross a discount threshold making the whole order cheaper.
3. **No Pro upsell in shopping plan:** Free users see nothing. Should see a side-by-side comparison showing how much they'd save with Pro.

## Design

### 1. Remove Pro Gate from Optimizer, Compose Two Plans in ViewModel

Drop `requirePro()` from `optimizeShoppingPlan()`. Always run two optimizations:

- **`freePlan`** — optimizer with only USEA-seller matches
- **`proPlan`** — optimizer with all seller matches

Both stored in state via a new model:

```kotlin
data class ShoppingPlanComparison(
    val activePlan: ShoppingPlan,    // What user can act on (free=USEA, pro=full)
    val proPlan: ShoppingPlan,       // Full optimized plan (always all sellers)
    val savingsDeltaCents: Int,      // activePlan.total - proPlan.total
)
```

- **Pro users:** `activePlan == proPlan`, `savingsDeltaCents == 0` — UI shows normal plan
- **Free users:** `activePlan` is USEA-only, `proPlan` is fully optimized — UI shows side-by-side

Business logic stays in ViewModel. UI receives composed state and renders.

Also fix `wizardResultsToExport()` which skips optimization for free users — remove the `if (isPro)` guard.

### 2. Iterative Coupon-Aware Optimization

Replace the current single-pass threshold optimization with an iterative loop:

1. **Initial assignment** — bestOption per card (same as today)
2. **Apply discounts** — calculate each seller's discount tier from current subtotal
3. **Compute effective prices** — for each moveable card, `listPrice * (100 - discount%) / 100`
4. **Re-assign** — move cards where effective price at another seller beats current assignment
5. **Repeat 2-4** until stable or max iterations (cap at 10, expect 2-3 in practice)

Convergence guaranteed: each iteration can only decrease total cost, and finite discount tiers mean finite possible states.

### 3. Computational Efficiency

Key data structures:

- **Per-seller running subtotals** (`MutableMap<Seller, Int>`) — updated incrementally O(1) per card move, not re-summed
- **Discount tier lookup** — tiers pre-sorted descending, binary search for current tier O(log T) where T ≤ 7
- **Effective price matrix** — pre-compute `cards × sellers` raw prices from alternatives at startup. Each iteration multiplies by discount factor — no re-scanning alternatives
- **Move candidates as priority queue** — min-heap of (savings-delta, cardIndex). After tier changes, only re-evaluate cards at affected sellers

**Active set filtering:** Most cards are only available from one seller and can never move. Filter to moveable cards (available from 2+ sellers) once upfront. Iterate only over this smaller set.

### 4. Side-by-Side Comparison UI

- **"Your Plan" column** — renders `activePlan` with full interactivity (export, copy, buy buttons)
- **"Pro Plan" column** — renders `proPlan` with locked/blurred seller orders for non-USEA sellers
- **Savings callout** between columns: "Save $X.XX with DeckLoot Pro"
- For Pro users, comparison section hidden since `savingsDeltaCents == 0`

### 5. USEA Coupon Codes

Already mirror `SellerDiscountConfig` tiers. The `useaCouponCode()` function in `ShoppingPlanScreen` automatically reflects whatever tier the iterative optimizer lands on. No separate coupon logic needed.

`Promotions.kt` is legacy (single-seller flow) and untouched by this work.

## Files Affected

| File | Change |
|------|--------|
| `model/Models.kt` | Add `ShoppingPlanComparison` data class |
| `optimizer/ShoppingOptimizer.kt` | Iterative optimization with efficient data structures |
| `state/MviViewModel.kt` | Remove Pro gate, compose two plans, update state type |
| `ui/ShoppingPlanScreen.kt` | Side-by-side layout consuming `ShoppingPlanComparison` |

## Out of Scope

- Adding discount tiers for non-USEA sellers (Bootleg Mage, TCGPlayer, ManaPool configs stay as-is)
- Changes to `Promotions.kt` (legacy single-seller utility)
- Changes to multi-match generation or seller filtering in `searchDeck()`
