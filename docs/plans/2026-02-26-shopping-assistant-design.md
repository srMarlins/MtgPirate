# MTG Pirate Shopping Assistant — Design Document

**Date:** 2026-02-26
**Status:** Approved

## Overview

Transform MTG Pirate from a single-seller CSV export tool into a multi-seller shopping assistant that finds the best deals across proxy sellers and real card marketplaces, optimizes orders for bulk discounts and shipping, and provides one-tap checkout helpers for each seller.

### Design Principles

- **Proxy-first**: Always prefer proxy cards. Fall back to real cards only when no proxy is available.
- **Cheapest within preference**: Apply variant priority (Foil > Holo > Regular per user config), then pick cheapest among matching variants across sellers.
- **Threshold-aware optimizer**: Factor in bulk discount tiers and shipping thresholds — sometimes it's cheaper to consolidate at one seller than split optimally by unit price.
- **Shopping assistant, not automated buyer**: The app does all the intelligence. Checkout is a handoff to the user's browser with everything pre-filled.
- **Mobile + Desktop**: All features work on both platforms. No browser automation dependency.

## Seller Support

### Phase 1 (This Design)

| Seller | Type | Catalog Source | Checkout Method |
|--------|------|---------------|-----------------|
| **USEA MTG Proxy** | Proxy | CSV/HTML scrape (existing) | Copy CSV / Email order |
| **Bootleg Mage** | Proxy | WooCommerce scrape + cache | Open deck import in browser |
| **Scryfall → TCGPlayer** | Real | Scryfall `/cards/collection` API | Open TCGPlayer mass entry / product links |

### Future Phases

- MPC Autofill (XML pipeline for MakePlayingCards.com)
- ProxyPrintery (accepts MPC Autofill XML)
- Mana Pool (real card marketplace, has API)
- Additional proxy sellers via `CatalogSource` interface

## Data Model Changes

### New Types

```kotlin
enum class Seller(val displayName: String, val isProxy: Boolean) {
    USEA("USEA MTG Proxy", true),
    BOOTLEG_MAGE("Bootleg Mage", true),
    TCGPLAYER("TCGPlayer", false),
}

interface CatalogSource {
    val seller: Seller
    suspend fun fetchCatalog(): List<CardVariant>
    suspend fun search(cardName: String): List<CardVariant>
    fun checkoutUrl(items: List<OrderItem>): String?
    fun formatForExport(items: List<OrderItem>): String
}
```

### Extended CardVariant

Add to existing `CardVariant`:
- `seller: Seller` — which seller offers this variant
- `purchaseUri: String?` — direct buy link (from Scryfall for real cards)

### New Models

```kotlin
data class MultiMatch(
    val deckEntry: DeckEntry,
    val bestOption: MatchOption,
    val alternatives: List<MatchOption>,
    val realCardFallback: MatchOption?,
)

data class MatchOption(
    val variant: CardVariant,
    val seller: Seller,
    val priceCents: Int,
    val isProxy: Boolean,
    val matchScore: Int,
)

data class ShoppingPlan(
    val orders: List<SellerOrder>,
    val totalPriceCents: Int,
    val savingsVsSingleSeller: Int,
)

data class SellerOrder(
    val seller: Seller,
    val items: List<OrderItem>,
    val subtotalCents: Int,
    val discountPercent: Int,
    val shippingCents: Int,
    val totalCents: Int,
)

data class OrderItem(
    val variant: CardVariant,
    val qty: Int,
    val isProxy: Boolean,
)
```

### Database Changes

Add `seller TEXT NOT NULL DEFAULT 'USEA'` column to `CardVariantEntity`.
Add `purchaseUri TEXT` column to `CardVariantEntity`.

## Matching Engine Changes

### Current Flow
```
DeckEntry → match against USEA catalog → DeckEntryMatch
```

### New Flow
```
DeckEntry → match against ALL catalogs → MultiMatch
```

### Selection Priority (per card)

1. Search all proxy catalogs (USEA, Bootleg Mage) for the card
2. Apply user's variant priority (e.g., Foil > Holo > Regular)
3. Among matching variant type, pick cheapest across sellers
4. If no proxy found → query Scryfall for cheapest real card printing
5. User can override any selection in the results UI

### Multi-Catalog Matching Strategy

- Load all catalogs into a unified index (keyed by normalized card name)
- Existing 5-pass matching algorithm runs against unified index
- Results tagged with `seller` field
- Ambiguity resolution now shows options across sellers

## Shopping Optimizer

### Algorithm

1. **Naive assignment**: Assign each card to its cheapest seller
2. **Threshold check**: For each seller near a discount tier boundary, calculate cost of "pulling" cards from other sellers to reach the threshold
3. **Shipping optimization**: Factor in free shipping thresholds
4. **Evaluate all viable splits**: Compare total cost (subtotal − discount + shipping) across candidate plans
5. **Select minimum cost plan**

### Discount Tiers (Configured Per Seller)

**USEA:**
- >$400 → 50% off
- >$300 → 35% off
- >$200 → 30% off
- >$160 → 25% off
- >$100 → 15% off
- >$60 → 5% off

Shipping: >$300 free express, >$100 free normal, otherwise $10.

**Bootleg Mage:** TBD (scrape their discount structure).

### Threshold Optimization Example

```
Naive: USEA $380 (30% tier), BM $40
  → USEA: $380 × 0.70 + $0 ship = $266
  → BM: $40 + $10 ship = $50
  → Total: $316

Optimized: Move $20 of BM cards → USEA to hit $400 (50% tier)
  → USEA: $400 × 0.50 + $0 ship = $200
  → BM: $20 + $10 ship = $30
  → Total: $230 (saves $86)
```

## UI Flow

```
[1. IMPORT]  Paste decklist (existing, unchanged)
      ↓
[2. PREFERENCES]  Variant priority, proxy-first toggle (existing, enhanced)
      ↓
[3. MATCH]  Multi-catalog matching with progress indicator
      ├── "Matching against USEA..."
      ├── "Matching against Bootleg Mage..."
      └── "Checking real card prices..."
      ↓
[4. RESULTS]  Per-card results (enhanced)
      - Each card shows: best option + seller badge + price
      - Tap to see alternatives from all sellers
      - "Not found" cards show real card fallback price
      - Filter: by seller, by proxy/real, by match status
      ↓
[5. SHOPPING PLAN + CHECKOUT]  ← NEW MERGED SCREEN
      - Order split visualization per seller
      - Discount tiers applied, shipping, grand total
      - "Saving $X vs single-seller" callout
      - Per-seller expandable cards with item list + action buttons
      - Actions: Copy CSV, Open in Browser, Copy List, Email Order
```

### Shopping Plan Screen Layout

```
┌─── SHOPPING PLAN ──────────────────────────┐
│  Total: $253 across 3 sellers               │
│  Saving $86 vs single-seller ordering       │
│                                             │
│  ┌─ USEA (45 cards) ──────────────────┐    │
│  │ $200 (50% bulk discount applied!)   │    │
│  │ Free express shipping               │    │
│  │ [▸ View cards]                      │    │
│  │ [Copy CSV]  [Email Order]           │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─ Bootleg Mage (12 cards) ──────────┐    │
│  │ $35 + $10 shipping                  │    │
│  │ [▸ View cards]                      │    │
│  │ [Open Deck Import ↗]               │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─ TCGPlayer (3 real cards) ─────────┐    │
│  │ $18 (cheapest printings)            │    │
│  │ [▸ View cards]                      │    │
│  │ [Open Mass Entry ↗]  [Copy List]   │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

## Catalog Integration Details

### USEA (Enhanced Existing)

- Keep current CSV/HTML scraping from `usmtgproxy.com`
- Tag all variants with `Seller.USEA`
- Existing `Promotions.kt` discount calculator already handles their tiers
- Checkout: Copy CSV (existing) or email `undergroundsea@outlook.com`

### Bootleg Mage (New)

- **Catalog fetch**: Try `/wp-json/wc/v3/products` (WooCommerce REST). If blocked, scrape product pages.
- **On-demand search fallback**: POST to `/?wc-ajax=bm_deck_importer_search` with card names
- **Cache**: Store in `CardVariantEntity` table with `seller = 'BOOTLEG_MAGE'`
- **Refresh**: Same cadence as USEA (user-triggered or 24-hour cache)
- **Checkout**: Copy formatted deck list to clipboard, open `bootlegmage.com/deck-import/` in browser

### Scryfall Real Card Fallback (New)

- **Endpoint**: `POST /cards/collection` — bulk lookup, 75 cards per request
- **Data used**: `prices.usd`, `prices.usd_foil`, `purchase_uris.tcgplayer`, `set`, `collector_number`
- **Cache**: 24 hours in a new `RealCardPrice` table or inline in `CardVariantEntity`
- **Rate limit**: 75ms between requests (already respected by existing `ScryfallApi`)
- **Checkout**: Open `purchase_uris.tcgplayer` links, or build TCGPlayer mass entry URL

## Error Handling

- **Catalog fetch fails**: Show cached data with "last updated X hours ago" warning. Never block the flow.
- **Partial catalog**: If one seller fails, proceed with available sellers. Show which sellers were unavailable.
- **No proxy match**: Gracefully fall back to Scryfall real card. Never leave a card without an option unless it truly doesn't exist.
- **Scryfall rate limit**: Queue requests with 75ms delay. Show progress indicator.

## Testing Strategy

- Unit test the shopping optimizer with known discount tier scenarios
- Unit test multi-catalog matching with mock catalogs
- Test threshold optimization (verify it finds the $86 savings in the example above)
- Test graceful degradation when a catalog source is unavailable
- Integration test Scryfall bulk lookup with real API (small batches)
- Manual test the full flow: import → match → plan → checkout actions

## Migration Path

- Existing USEA-only flow continues to work unchanged
- Multi-seller features are additive (new code paths, not replacing old ones)
- Database migration adds `seller` and `purchaseUri` columns with defaults
- Existing saved imports and preferences remain compatible
