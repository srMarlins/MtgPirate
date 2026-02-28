# DeckLoot Pro Unlock Design

## Overview

DeckLoot adopts a freemium model with a one-time $4.99 "Pro" in-app purchase across all platforms (iOS, Android, Desktop). RevenueCat manages purchases, receipt validation, and cross-platform entitlement sync.

## Monetization Model

- **One-time purchase:** $4.99 unlocks Pro forever
- **No subscription:** No recurring charges
- **Cross-platform:** Purchase on one platform, optionally link to unlock on others
- **Payment providers:** App Store (iOS), Google Play (Android), Stripe via RevenueCat (Desktop)

## Free vs Pro Feature Split

| Feature | Free | Pro |
|---------|------|-----|
| Decklist import (all formats) | Yes | Yes |
| Full matching quality (fuzzy, set codes) | Yes | Yes |
| Single seller (USEA proxy) | Yes | Yes |
| Basic CSV export | Yes | Yes |
| Multi-seller matching (4 sellers) | Locked | Yes |
| Shopping plan optimizer | Locked | Yes |
| Seller override per card | Locked | Yes |
| Saved import history | Locked | Yes |
| Theme toggle | Locked | Yes |

**Rationale:** The free tier provides a complete single-seller workflow so users experience the core value. Pro unlocks the multi-seller aggregation and convenience features that eliminate the most tedious manual work.

## Architecture

### ProFeature Enum (commonMain)

```kotlin
enum class ProFeature {
    MULTI_SELLER,
    SHOPPING_OPTIMIZER,
    SELLER_OVERRIDE,
    IMPORT_HISTORY,
    THEME_CUSTOMIZATION
}
```

### ProStatus (commonMain)

```kotlin
sealed class ProStatus {
    object Free : ProStatus()
    object Pro : ProStatus()
    object Loading : ProStatus()
}
```

Added to `ViewState.proStatus`. All UI composables and ViewModel intent handlers read this value.

### PurchaseManager (expect/actual)

```kotlin
// commonMain
expect class PurchaseManager {
    fun configure(apiKey: String)
    suspend fun checkEntitlement(): ProStatus
    suspend fun purchase(): PurchaseResult
    suspend fun restorePurchases(): ProStatus
}
```

Platform implementations:
- **iOS:** Swift bridge wrapping RevenueCat `Purchases` SDK
- **Android:** RevenueCat Kotlin SDK directly
- **Desktop:** RevenueCat REST API (entitlement check) + Stripe Checkout (purchase via browser)

### Local Cache

`isPro: Boolean` column added to `PreferencesEntity` in SQLDelight. On launch: read cached value for instant UI, then async-check RevenueCat and update if changed. Offline-friendly.

## Feature Gating

Enforcement happens at the **ViewModel intent level**. When a user triggers a Pro-gated intent, the ViewModel checks `proStatus`:

```kotlin
is ViewIntent.LoadAllCatalogs -> {
    if (state.proStatus != ProStatus.Pro) {
        emitEffect(ViewEffect.ShowUpgradePrompt(ProFeature.MULTI_SELLER))
        return
    }
    loadAllCatalogs()
}
```

The UI shows visual lock indicators but the ViewModel is the single enforcement point.

## Platform Purchase Flows

### iOS

- RevenueCat `Purchases.configure(apiKey)` at app launch
- Swift `RevenueCatBridge` class exposes `checkEntitlement()`, `purchase()`, `restore()` to Kotlin/Native
- `actual class PurchaseManager` calls through the bridge
- App Store handles the native payment sheet

### Android

- RevenueCat Kotlin SDK — first-class support, simplest integration
- `actual class PurchaseManager` uses `Purchases.sharedInstance`
- Google Play handles the payment sheet
- `Activity` reference passed via `PlatformServices`

### Desktop

- **Entitlement check:** Ktor GET to `https://api.revenuecat.com/v1/subscribers/{app_user_id}`
- **Purchase:** Opens Stripe Checkout in system browser (RevenueCat Stripe integration). App polls for entitlement update after redirect
- **App user ID:** Generated once on first launch, stored in SQLDelight preferences
- **API key:** RevenueCat public API key (safe for client-side, read-only access to own entitlements)

### Cross-Platform Sync

RevenueCat syncs via `app_user_id`. Default behavior:
- **Anonymous:** Each install gets a random ID. No sync.
- **Optional "Link Devices":** User enters a shared identifier in settings. RevenueCat merges entitlements across devices.

V1 ships with anonymous by default + optional linking.

## UI Changes

### Locked Feature Appearance

Pro features are **visible but locked** for free users. Lock icons and badges indicate gated features. Tapping a locked control triggers the upgrade prompt.

### New UI Components

1. **`ProBadge`** — Small pixel-styled lock icon placed next to locked controls
2. **`UpgradeDialog`** — Modal with feature description, $4.99 price, "Unlock Pro" button, "Restore Purchase" link. Pixel design system styling.
3. **`ProGate`** — Wrapper composable that overlays lock state on any child composable

### Screen-by-Screen Changes

**Preferences Screen (Step 2):**
- USEA checkbox: enabled
- Bootleg Mage, TCGPlayer, ManaPool checkboxes: grayed out with lock icon, tap triggers upgrade

**Results Screen (Step 3):**
- "Search All Sellers" button: lock badge for free users
- "Optimize Shopping Plan" button: lock badge for free users
- Seller override dropdown: disabled with lock icon

**Import History:**
- "Saved Imports" button: lock badge, shows upgrade prompt instead of dialog

**Theme Toggle:**
- Lock badge, shows upgrade prompt

**Settings (new section):**
- "DeckLoot Pro" section showing current status
- "Upgrade to Pro" or "You're a Pro!" message
- "Restore Purchase" button
- "Link Devices" option

## Dependencies

- RevenueCat iOS SDK (Swift Package Manager)
- RevenueCat Android SDK (via gradle, `com.revenuecat.purchases:purchases`)
- RevenueCat REST API (no dependency for desktop, uses existing Ktor)
- Stripe account (connected to RevenueCat for desktop payments)

## Testing Strategy

- Unit test `ProFeature` gating logic in ViewModel (mock PurchaseManager)
- Test `ProGate` composable renders lock state correctly
- Manual test purchase flows on each platform using RevenueCat sandbox
- Test offline behavior (cached `isPro` should persist)
- Test restore purchases flow
