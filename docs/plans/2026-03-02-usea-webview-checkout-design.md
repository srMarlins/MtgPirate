# USEA WebView Checkout Design

**Date:** 2026-03-02
**Goal:** One-tap checkout for USEA orders via agamecardshop.com — build the cart, apply the coupon, and land on checkout with Stripe/PayPal ready.

## Background

USEA's current ordering process requires manually copying cards into a Google Sheet, sending it via Discord, and paying on a separate site (advanstore.net). The app can automate most of this by building a cart on agamecardshop.com (USEA's WooCommerce storefront) via JavaScript injection in a WebView.

**agamecardshop.com technical facts:**
- WordPress/WooCommerce site
- Product search: `GET /wp-json/wp/v2/product?search=TERM` returns product IDs, slugs, titles
- Add-to-cart: `POST /?wc-ajax=add_to_cart` with `product_id` and `quantity`
- Apply coupon: `POST /?wc-ajax=apply_coupon` with `coupon_code`
- No authentication required for cart operations (session-based)
- SKUs differ from USEA catalog (site uses `Z127`, `FF786R`; USEA uses `XMC*`)

**USEA coupon codes (from agamecardshop.com/coupon-code/):**

| Code | Discount | Threshold |
|------|----------|-----------|
| c5for60 | 5% | >$60 |
| c15for100 | 15% | >$100 |
| c25for160 | 25% | >$160 |
| c30for200 | 30% | >$200 |
| c35for300 | 35% | >$300 |
| c50for400 | 50% | >$400 |
| c56for1000 | 56% | >$1,000 |

## Approach: WebView + JS Injection

Open a WebView to `agamecardshop.com/cart/`, inject JavaScript that calls WooCommerce AJAX endpoints to add each item, apply the coupon, then navigate to checkout. User sees a pre-filled cart and pays with Stripe/PayPal.

**Rejected alternatives:**
- **Sequential URL navigation** — One page load per card. Too slow for 50+ card orders.
- **API pre-build + cookie transfer** — Build cart via Ktor, transfer PHP session cookie to WebView. Unreliable across platforms.

## Section 1: Product ID Mapping

### Problem

Our USEA SKUs (`XMC00003`) don't exist on agamecardshop.com. We need to map each `CardVariant` to a WooCommerce product ID.

### Solution

Search the WordPress REST API by card name, then fuzzy-match on set code + variant type.

**Matching strategy:**
```
Our data:   "Lightning Bolt", setCode="M11", variantType=REGULAR
API search: GET /wp-json/wp/v2/product?search=lightning+bolt
Results:    "Lightning Bolt M11 normal" (42), "Lightning Bolt STA Japanese foil" (4219), ...
Match on:   title contains "M11" AND title contains "normal"
             (Regular→"normal", Holo→"hologram", Foil→"foil")
Result:     wcProductId = 42
```

### Timing

- Runs async after catalog refresh completes (fire-and-forget coroutine)
- Non-blocking — catalog is usable immediately, product IDs populate in background
- Stored as `wcProductId TEXT` column on `CardVariantEntity` (migration 6.sqm)

### Rate Limiting

- Batch by unique card name (one API call returns all variants for a name)
- ~2000 unique card names in USEA catalog
- Throttle requests to avoid hammering the site (e.g., 50ms delay between calls)
- Skip cards that already have a `wcProductId` from a previous mapping run

## Section 2: WebView Cart Builder

### Flow

1. User taps "Checkout on USEA" on the Shopping Plan screen
2. App checks `wcProductId` coverage for the order items
3. If some items are unmapped → warning: "X of Y cards weren't found on the store. These will be copied to clipboard for the Google Sheet. Continue?"
4. Open WebView to `https://www.agamecardshop.com/cart/`
5. On page load, inject JavaScript:

```javascript
(async () => {
  const items = [/* injected from app: {wcProductId, qty} pairs */];
  const couponCode = '/* injected from app */';
  let added = 0;

  // Phase 1: Add items to cart
  for (const item of items) {
    const formData = new FormData();
    formData.append('product_id', item.id);
    formData.append('quantity', item.qty);
    await fetch('/?wc-ajax=add_to_cart', { method: 'POST', body: formData });
    added++;
    window.DeckLoot.onProgress(added, items.length);
  }

  // Phase 2: Apply coupon
  if (couponCode) {
    const couponData = new FormData();
    couponData.append('coupon_code', couponCode);
    await fetch('/?wc-ajax=apply_coupon', { method: 'POST', body: couponData });
  }

  // Phase 3: Refresh to show populated cart
  window.DeckLoot.onComplete();
  window.location.reload();
})();
```

6. User sees a fully populated cart with discount applied
7. User clicks checkout → pays with Stripe/PayPal

### Loading UX

While JS runs, show an overlay on the WebView: "Building your cart... (32/45 cards added)". Progress posted back via `window.DeckLoot.onProgress()` JavaScript bridge.

### Platform Details

- **Android:** `android.webkit.WebView` + `evaluateJavascript()` + `addJavascriptInterface()` for progress callbacks
- **iOS:** `WKWebView` + `evaluateJavaScript()` + `WKScriptMessageHandler` for progress callbacks
- **Desktop:** No WebView — falls back to clipboard approach (Section 3)

## Section 3: Desktop & Unmatched Cards Fallback

### Desktop Fallback

Two buttons on the Shopping Plan screen for USEA:

1. **"Copy Order" (Primary)** — Copies the card list in the exact Google Sheet tab-separated format:
   ```
   Card Name\tSet\tSKU\tCard Type\tQty\tBase Price
   Bitterblossom MM2\tMM2\tXMC00003\t- Regular\t1\t$2.20
   Lightning Bolt M11\tM11\tXMC00042\t- Regular\t4\t$2.20
   ```

2. **"Open Sheet Template" (Secondary)** — Opens the Google Sheet copy link in browser

Plus inline instructions: "Paste into the Cart tab of your USEA Google Sheet, then send to @longalone on Discord. Pay on advanstore.net."

### Unmatched Cards (Mobile)

After WebView cart is built, if any cards couldn't be mapped:
- Show a bottom sheet listing unmatched cards
- Auto-copy them to clipboard in the same Google Sheet TSV format
- Message: "X cards weren't found on the store and have been copied to clipboard. Add them manually via the Google Sheet."

### Google Sheet Format

Matches the Cart tab columns exactly:

| Column | Field | Example |
|--------|-------|---------|
| A | Card Name + Set | Bitterblossom MM2 |
| B | Set | MM2 |
| C | SKU | XMC00003 |
| D | Card Type | - Regular |
| E | Qty | 1 |
| F | Base Price | $2.20 |

Card Type uses the Sheet convention: `- Regular`, `- Foil`, `- Holo` (leading dash + space).

## Section 4: Architecture

### New Components

1. **`AgamecardshopProductMapper`** (`src/commonMain/kotlin/catalog/`)
   - Ktor-based service; reuses existing HTTP client setup
   - Searches WordPress API, fuzzy-matches to card variants
   - Writes `wcProductId` to database

2. **`UseaCheckoutScreen`** (`src/commonMain/kotlin/ui/`)
   - Composable wrapping the platform WebView
   - Shows loading overlay with progress
   - Manages unmatched-cards bottom sheet

3. **`WebViewBridge`** (expect/actual in platform source sets)
   - Minimal interface for JS injection and progress callbacks
   - `actual android`: `android.webkit.WebView` + `JavascriptInterface`
   - `actual ios`: `WKWebView` + `WKScriptMessageHandler`
   - `actual desktop`: no-op (clipboard fallback)

### MVI Integration

- New intent: `ViewIntent.CheckoutUsea(order: SellerOrder)`
- ViewModel checks `wcProductId` coverage, emits:
  - `ViewEffect.OpenUseaWebViewCheckout(items, couponCode, unmatchedItems)` on mobile
  - `ViewEffect.OpenUseaClipboardCheckout(formattedText, sheetUrl)` on desktop

### Catalog Refresh Hook

- After `CatalogUseCase.loadCatalog()` succeeds, launch `AgamecardshopProductMapper.mapAll()` on a background coroutine
- Updates `wcProductId` in database as results arrive
- UI doesn't wait — checkout button shows coverage indicator (e.g., "42/45 mapped") if mapping is in progress

### Database Changes

Migration 6.sqm:
```sql
ALTER TABLE CardVariantEntity ADD COLUMN wcProductId TEXT;
```

No new tables needed. Product IDs are stored directly on the variant.

### Dependencies

- No new third-party libraries for WebView — use platform-native APIs with expect/actual
- Ktor client (already available) for WordPress API calls

## Summary

| Platform | Checkout UX | Fallback |
|----------|------------|----------|
| Android | WebView → auto-built cart → Stripe/PayPal | Clipboard + Google Sheet instructions |
| iOS | WebView → auto-built cart → Stripe/PayPal | Clipboard + Google Sheet instructions |
| Desktop | Clipboard + Google Sheet instructions | N/A (this is the primary flow) |
