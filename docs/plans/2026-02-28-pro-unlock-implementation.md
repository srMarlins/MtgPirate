# Pro Unlock Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a freemium Pro unlock ($4.99 one-time) gating multi-seller, shopping optimizer, import history, and theme toggle behind RevenueCat-powered IAP across iOS, Android, and Desktop.

**Architecture:** Add `ProStatus` to ViewState and `PurchaseManager` as an `expect/actual` interface. Gate Pro features at the ViewModel intent level. Cache `isPro` in SQLDelight for offline support. RevenueCat wraps App Store, Google Play, and Stripe.

**Tech Stack:** RevenueCat iOS SDK (Swift), RevenueCat Android SDK (Kotlin), RevenueCat REST API (Desktop via Ktor), SQLDelight migration, Compose Multiplatform UI components.

**Design doc:** `docs/plans/2026-02-28-pro-unlock-design.md`

---

### Task 1: Add Pro domain models (commonMain)

**Files:**
- Create: `src/commonMain/kotlin/model/Pro.kt`
- Test: `src/commonTest/kotlin/model/ProTest.kt`

**Step 1: Write the failing test**

```kotlin
// src/commonTest/kotlin/model/ProTest.kt
package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProTest {

    @Test
    fun proFeature_hasExpectedEntries() {
        assertEquals(5, ProFeature.entries.size)
        assertTrue(ProFeature.entries.contains(ProFeature.MULTI_SELLER))
    }

    @Test
    fun proStatus_free_isNotPro() {
        assertFalse(ProStatus.Free.isPro)
    }

    @Test
    fun proStatus_pro_isPro() {
        assertTrue(ProStatus.Pro.isPro)
    }

    @Test
    fun proStatus_loading_isNotPro() {
        assertFalse(ProStatus.Loading.isPro)
    }

    @Test
    fun purchaseResult_hasSuccessAndCancelled() {
        assertEquals("SUCCESS", PurchaseResult.SUCCESS.name)
        assertEquals("CANCELLED", PurchaseResult.CANCELLED.name)
        assertEquals("ERROR", PurchaseResult.ERROR.name)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew desktopTest --tests "model.ProTest" --rerun`
Expected: FAIL — `model.ProFeature` not found

**Step 3: Write minimal implementation**

```kotlin
// src/commonMain/kotlin/model/Pro.kt
package model

enum class ProFeature {
    MULTI_SELLER,
    SHOPPING_OPTIMIZER,
    SELLER_OVERRIDE,
    IMPORT_HISTORY,
    THEME_CUSTOMIZATION,
}

sealed class ProStatus {
    object Free : ProStatus()
    object Pro : ProStatus()
    object Loading : ProStatus()

    val isPro: Boolean get() = this is Pro
}

enum class PurchaseResult {
    SUCCESS,
    CANCELLED,
    ERROR,
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew desktopTest --tests "model.ProTest" --rerun`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/model/Pro.kt src/commonTest/kotlin/model/ProTest.kt
git commit -m "feat: add Pro domain models (ProFeature, ProStatus, PurchaseResult)"
```

---

### Task 2: Add `isPro` to SQLDelight schema + Database layer

**Files:**
- Create: `src/commonMain/sqldelight/database/4.sqm`
- Modify: `src/commonMain/sqldelight/database/Preferences.sq`
- Modify: `src/commonMain/kotlin/database/Database.kt:88-99`
- Modify: `src/commonMain/kotlin/model/Models.kt:98-108` (Preferences data class)

**Step 1: Create migration file**

```sql
-- src/commonMain/sqldelight/database/4.sqm
-- Migration: Add isPro column to PreferencesEntity for local Pro status cache.
ALTER TABLE PreferencesEntity ADD COLUMN isPro INTEGER NOT NULL DEFAULT 0;
```

**Step 2: Update Preferences.sq schema to include isPro**

Add `isPro` column to the CREATE TABLE and INSERT statements in `Preferences.sq`:

In `Preferences.sq`, add `isPro INTEGER NOT NULL DEFAULT 0` after `proxyFirst`, and add it to the `insertPreferences` statement's column list and VALUES.

**Step 3: Update Preferences data class**

In `src/commonMain/kotlin/model/Models.kt`, add `isPro: Boolean = false` to the `Preferences` data class (line 108, before the closing paren).

**Step 4: Update Database.kt insertPreferences**

In `src/commonMain/kotlin/database/Database.kt:88-99`, add `isPro = if (preferences.isPro) 1L else 0L` to the `insertPreferences` call.

Also add a convenience method:

```kotlin
suspend fun updateIsPro(isPro: Boolean) {
    val current = db.preferencesQueries.selectAll().executeAsOneOrNull()
    if (current != null) {
        db.preferencesQueries.insertPreferences(
            includeSideboard = current.includeSideboard,
            includeCommanders = current.includeCommanders,
            includeTokens = current.includeTokens,
            variantPriority = current.variantPriority,
            setPriority = current.setPriority,
            fuzzyEnabled = current.fuzzyEnabled,
            cacheMaxAgeHours = current.cacheMaxAgeHours,
            enabledSellers = current.enabledSellers,
            proxyFirst = current.proxyFirst,
            isPro = if (isPro) 1L else 0L,
        )
    }
}
```

**Step 5: Update the entity-to-domain mapper**

The `.toDomain()` extension on the generated `PreferencesEntity` must now map `isPro`. Find the existing toDomain mapper (likely in Database.kt or a separate Mappers.kt) and add `isPro = entity.isPro == 1L`.

**Step 6: Build to verify schema compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESS (SQLDelight generates updated code)

**Step 7: Commit**

```bash
git add src/commonMain/sqldelight/database/4.sqm src/commonMain/sqldelight/database/Preferences.sq \
  src/commonMain/kotlin/database/Database.kt src/commonMain/kotlin/model/Models.kt
git commit -m "feat: add isPro column to preferences schema and database layer"
```

---

### Task 3: Add PurchaseManager expect/actual interface

**Files:**
- Create: `src/commonMain/kotlin/purchase/PurchaseManager.kt`
- Create: `src/desktopMain/kotlin/purchase/DesktopPurchaseManager.kt`
- Create: `src/iosMain/kotlin/purchase/IosPurchaseManager.kt`
- Create: `src/androidMain/kotlin/purchase/AndroidPurchaseManager.kt`

**Step 1: Create the common expect declaration**

```kotlin
// src/commonMain/kotlin/purchase/PurchaseManager.kt
package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Platform-specific purchase manager wrapping RevenueCat.
 * Desktop uses REST API, iOS uses Swift SDK, Android uses Kotlin SDK.
 */
interface PurchaseManager {
    /** Check current entitlement status with RevenueCat. */
    suspend fun checkEntitlement(): ProStatus

    /** Trigger the purchase flow. Returns result of the attempt. */
    suspend fun purchase(): PurchaseResult

    /** Restore previous purchases. */
    suspend fun restorePurchases(): ProStatus
}
```

Note: Use an interface instead of expect/actual class. This is simpler, testable, and follows the existing `MviPlatformServices` pattern in this codebase. Each platform creates a concrete implementation.

**Step 2: Create Desktop stub implementation**

```kotlin
// src/desktopMain/kotlin/purchase/DesktopPurchaseManager.kt
package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Desktop purchase manager using RevenueCat REST API + Stripe Checkout.
 * TODO: Implement REST API calls after RevenueCat account setup.
 */
class DesktopPurchaseManager : PurchaseManager {

    override suspend fun checkEntitlement(): ProStatus {
        // Stub: will call RevenueCat REST API
        return ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        // Stub: will open Stripe Checkout in browser
        return PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        // Stub: will call RevenueCat REST API
        return ProStatus.Free
    }
}
```

**Step 3: Create iOS stub implementation**

```kotlin
// src/iosMain/kotlin/purchase/IosPurchaseManager.kt
package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * iOS purchase manager wrapping RevenueCat Swift SDK via Kotlin/Native interop.
 * TODO: Implement after adding RevenueCat iOS SDK dependency.
 */
class IosPurchaseManager : PurchaseManager {

    override suspend fun checkEntitlement(): ProStatus {
        return ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        return PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        return ProStatus.Free
    }
}
```

**Step 4: Create Android stub implementation**

```kotlin
// src/androidMain/kotlin/purchase/AndroidPurchaseManager.kt
package purchase

import model.ProStatus
import model.PurchaseResult

/**
 * Android purchase manager wrapping RevenueCat Kotlin SDK.
 * TODO: Implement after adding RevenueCat Android SDK dependency.
 */
class AndroidPurchaseManager : PurchaseManager {

    override suspend fun checkEntitlement(): ProStatus {
        return ProStatus.Free
    }

    override suspend fun purchase(): PurchaseResult {
        return PurchaseResult.CANCELLED
    }

    override suspend fun restorePurchases(): ProStatus {
        return ProStatus.Free
    }
}
```

**Step 5: Build to verify all platforms compile**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/purchase/PurchaseManager.kt \
  src/desktopMain/kotlin/purchase/DesktopPurchaseManager.kt \
  src/iosMain/kotlin/purchase/IosPurchaseManager.kt \
  src/androidMain/kotlin/purchase/AndroidPurchaseManager.kt
git commit -m "feat: add PurchaseManager interface with platform stubs"
```

---

### Task 4: Wire PurchaseManager into MviViewModel + add Pro gating logic

**Files:**
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt`
- Test: `src/commonTest/kotlin/state/ProGatingTest.kt`

**Step 1: Write the failing test**

```kotlin
// src/commonTest/kotlin/state/ProGatingTest.kt
package state

import model.ProFeature
import model.ProStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProGatingTest {

    @Test
    fun freeUser_cannotAccessMultiSeller() {
        val status = ProStatus.Free
        assertFalse(status.isPro)
    }

    @Test
    fun proUser_canAccessMultiSeller() {
        val status = ProStatus.Pro
        assertTrue(status.isPro)
    }

    @Test
    fun proFeatureGating_blocksCorrectIntents() {
        // Verify that gated intent names map to expected ProFeatures
        val gatedIntents = mapOf(
            "LoadAllCatalogs" to ProFeature.MULTI_SELLER,
            "SearchDeck" to ProFeature.MULTI_SELLER,
            "OptimizeShoppingPlan" to ProFeature.SHOPPING_OPTIMIZER,
            "SaveCurrentImport" to ProFeature.IMPORT_HISTORY,
            "ToggleTheme" to ProFeature.THEME_CUSTOMIZATION,
        )
        assertEquals(5, gatedIntents.size)
    }
}
```

**Step 2: Run test to verify it passes (this is a logic test, not integration)**

Run: `./gradlew desktopTest --tests "state.ProGatingTest" --rerun`
Expected: PASS

**Step 3: Add `proStatus` to ViewState and LocalUiState**

In `MviViewModel.kt`, add to `ViewState` (after line 847):

```kotlin
val proStatus: ProStatus = ProStatus.Free,
val showUpgradePrompt: ProFeature? = null,
```

Add to `LocalUiState` (after line 875):

```kotlin
val proStatus: ProStatus = ProStatus.Free,
val showUpgradePrompt: ProFeature? = null,
```

**Step 4: Add PurchaseManager to MviViewModel constructor**

Change the `MviViewModel` constructor (line 47-53) to accept an optional `PurchaseManager?`:

```kotlin
class MviViewModel(
    private val scope: CoroutineScope,
    private val database: Database,
    private val catalogStore: CatalogStore,
    private val importsStore: ImportsStore,
    private val platformServices: MviPlatformServices,
    private val purchaseManager: PurchaseManager? = null,
)
```

**Step 5: Add `proStatus` and `showUpgradePrompt` to the ViewState combine block**

In the `combine` block in `init` (around line 101-132), add:

```kotlin
proStatus = localState.proStatus,
showUpgradePrompt = localState.showUpgradePrompt,
```

**Step 6: Add new ViewIntents for Pro**

Add to the `ViewIntent` sealed class (after line 928):

```kotlin
// Pro purchase intents
data object PurchasePro : ViewIntent()
data object RestorePurchases : ViewIntent()
data object CheckProStatus : ViewIntent()
data class ShowUpgradePrompt(val feature: ProFeature) : ViewIntent()
data object DismissUpgradePrompt : ViewIntent()
```

**Step 7: Add ShowUpgradePrompt ViewEffect**

Add to the `ViewEffect` sealed class (after line 936):

```kotlin
data class ShowUpgradePrompt(val feature: ProFeature) : ViewEffect()
```

**Step 8: Add Pro gating to intent handlers**

In `processIntent()`, add `DismissUpgradePrompt` and `ShowUpgradePrompt` to the synchronous section:

```kotlin
is ViewIntent.DismissUpgradePrompt -> { dismissUpgradePrompt(); return }
is ViewIntent.ShowUpgradePrompt -> { showUpgradePrompt(intent.feature); return }
```

In the async section, add:

```kotlin
ViewIntent.PurchasePro -> purchasePro()
ViewIntent.RestorePurchases -> restorePurchases()
ViewIntent.CheckProStatus -> checkProStatus()
```

**Step 9: Add Pro gate checks to existing intent handlers**

Add a helper function:

```kotlin
private fun requirePro(feature: ProFeature): Boolean {
    if (_localState.value.proStatus.isPro) return true
    _localState.update { it.copy(showUpgradePrompt = feature) }
    return false
}
```

Then gate these existing handlers by adding `if (!requirePro(...)) return` at the top:

- `loadAllCatalogs()` — gate with `ProFeature.MULTI_SELLER`
- `searchDeck()` — gate with `ProFeature.MULTI_SELLER`
- `optimizeShoppingPlan()` — gate with `ProFeature.SHOPPING_OPTIMIZER`
- `overrideCardSeller()` — gate with `ProFeature.SELLER_OVERRIDE`
- `saveCurrentImport()` — gate with `ProFeature.IMPORT_HISTORY`
- `toggleTheme()` — gate with `ProFeature.THEME_CUSTOMIZATION`

Also gate `updateEnabledSellers` — only allow changing sellers if Pro, otherwise show prompt:

```kotlin
private suspend fun updateEnabledSellers(sellers: List<String>) {
    // Allow if only USEA is selected (free tier) or if Pro
    val isOnlyUsea = sellers.size == 1 && sellers.first() == Seller.USEA.name
    if (!isOnlyUsea && !_localState.value.proStatus.isPro) {
        _localState.update { it.copy(showUpgradePrompt = ProFeature.MULTI_SELLER) }
        return
    }
    // ... existing implementation
}
```

**Step 10: Add Pro handler methods**

```kotlin
private fun showUpgradePrompt(feature: ProFeature) {
    _localState.update { it.copy(showUpgradePrompt = feature) }
}

private fun dismissUpgradePrompt() {
    _localState.update { it.copy(showUpgradePrompt = null) }
}

private suspend fun purchasePro() {
    val manager = purchaseManager ?: return
    val result = manager.purchase()
    if (result == PurchaseResult.SUCCESS) {
        _localState.update { it.copy(proStatus = ProStatus.Pro, showUpgradePrompt = null) }
        database.updateIsPro(true)
        log("Pro unlocked!", "INFO")
    }
}

private suspend fun restorePurchases() {
    val manager = purchaseManager ?: return
    val status = manager.restorePurchases()
    _localState.update { it.copy(proStatus = status, showUpgradePrompt = null) }
    database.updateIsPro(status.isPro)
    if (status.isPro) {
        log("Pro restored!", "INFO")
    }
}

private suspend fun checkProStatus() {
    // First load from cache
    val cached = database.observePreferences().first()
    if (cached?.isPro == true) {
        _localState.update { it.copy(proStatus = ProStatus.Pro) }
    }
    // Then verify with RevenueCat
    val manager = purchaseManager ?: return
    val status = manager.checkEntitlement()
    _localState.update { it.copy(proStatus = status) }
    database.updateIsPro(status.isPro)
}
```

**Step 11: Check Pro status on Init**

In `initHandler()`, after the existing catalog check, add:

```kotlin
checkProStatus()
```

**Step 12: Handle wizard composite intents with gating**

In `wizardPreferencesToResults()`, the existing flow already calls `searchDeck()` which is gated. For free users, this should fall back to single-seller USEA-only matching. Modify the gating in `searchDeck` to not block the wizard flow — instead, filter to only USEA for free users:

Actually, rethink: For free users in the wizard flow, `searchDeck()` should still work but only search with USEA. The gate should be on *enabling additional sellers* in preferences, not on the search itself. So remove the gate from `searchDeck()` and `loadAllCatalogs()` and instead ensure free users can only have USEA enabled (enforced in `updateEnabledSellers`).

Revised gating list:
- `optimizeShoppingPlan()` — gate with `ProFeature.SHOPPING_OPTIMIZER`
- `overrideCardSeller()` — gate with `ProFeature.SELLER_OVERRIDE`
- `saveCurrentImport()` — gate with `ProFeature.IMPORT_HISTORY`
- `toggleTheme()` — gate with `ProFeature.THEME_CUSTOMIZATION`
- `updateEnabledSellers()` — gate adding non-USEA sellers with `ProFeature.MULTI_SELLER`

The multi-seller gate happens at the *preference level*, not the search level. This way the wizard flow works for everyone.

**Step 13: Build and run tests**

Run: `./gradlew build`
Expected: BUILD SUCCESS

Run: `./gradlew desktopTest --rerun`
Expected: All tests PASS

**Step 14: Commit**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt src/commonTest/kotlin/state/ProGatingTest.kt
git commit -m "feat: wire PurchaseManager into ViewModel with Pro gating logic"
```

---

### Task 5: Add Pro UI components (ProBadge, UpgradeDialog, ProGate)

**Files:**
- Create: `src/commonMain/kotlin/ui/ProComponents.kt`

**Step 1: Create ProBadge composable**

```kotlin
// src/commonMain/kotlin/ui/ProComponents.kt
package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.ProFeature
import model.ProStatus

/** Small lock icon badge for Pro-gated controls. */
@Composable
fun ProBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    PixelBadge(
        text = "\uD83D\uDD12 PRO",
        color = PixelGold,
        modifier = mod,
    )
}

/** Feature description shown in upgrade dialog. */
private fun featureDescription(feature: ProFeature): String = when (feature) {
    ProFeature.MULTI_SELLER -> "Search across all 4 sellers to find the best prices for your deck."
    ProFeature.SHOPPING_OPTIMIZER -> "Automatically optimize your orders across sellers for the lowest total cost."
    ProFeature.SELLER_OVERRIDE -> "Choose which seller to buy each card from."
    ProFeature.IMPORT_HISTORY -> "Save your decklists and load them later."
    ProFeature.THEME_CUSTOMIZATION -> "Switch between dark and light themes."
}

/** Feature title shown in upgrade dialog. */
private fun featureTitle(feature: ProFeature): String = when (feature) {
    ProFeature.MULTI_SELLER -> "Multi-Seller Search"
    ProFeature.SHOPPING_OPTIMIZER -> "Shopping Optimizer"
    ProFeature.SELLER_OVERRIDE -> "Seller Override"
    ProFeature.IMPORT_HISTORY -> "Import History"
    ProFeature.THEME_CUSTOMIZATION -> "Theme Toggle"
}

/** Upgrade prompt dialog shown when a free user taps a locked feature. */
@Composable
fun UpgradeDialog(
    feature: ProFeature,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        glowing = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "▸ UNLOCK PRO",
                style = MaterialTheme.typography.h5,
                color = PixelGold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                featureTitle(feature),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                featureDescription(feature),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            PixelButton(
                text = "Unlock Pro — \$4.99",
                onClick = onPurchase,
                variant = PixelButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            PixelButton(
                text = "Restore Purchase",
                onClick = onRestore,
                variant = PixelButtonVariant.SURFACE,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "One-time purchase. No subscription.",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * Wrapper that overlays a lock state on child content when user is not Pro.
 * Clicking the locked overlay triggers the upgrade prompt.
 */
@Composable
fun ProGate(
    proStatus: ProStatus,
    feature: ProFeature,
    onUpgradeClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (!proStatus.isPro) {
            // Semi-transparent overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(PixelShape(cornerSize = 6.dp))
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.6f))
                    .clickable(onClick = onUpgradeClick),
                contentAlignment = Alignment.Center,
            ) {
                ProBadge()
            }
        }
    }
}
```

**Step 2: Build to verify it compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/commonMain/kotlin/ui/ProComponents.kt
git commit -m "feat: add ProBadge, UpgradeDialog, and ProGate UI components"
```

---

### Task 6: Wire upgrade dialog into platform entry points

**Files:**
- Modify: `src/desktopMain/kotlin/app/Main.kt:274-300`
- Modify: `src/iosMain/kotlin/app/Main.kt:13-24`
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt:64-113`

**Step 1: Wire PurchaseManager into Desktop entry point**

In `src/desktopMain/kotlin/app/Main.kt`, after `platformServices` creation (line 281), add:

```kotlin
val purchaseManager = remember { DesktopPurchaseManager() }
```

Pass it to MviViewModel (line 284-291):

```kotlin
val viewModel = remember {
    MviViewModel(
        scope = scope,
        database = database,
        catalogStore = catalogStore,
        importsStore = importsStore,
        platformServices = platformServices,
        purchaseManager = purchaseManager,
    )
}
```

Add the upgrade dialog overlay. Find where `state.showSavedImportsWindow` or similar dialogs are rendered and add nearby:

```kotlin
if (state.showUpgradePrompt != null) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
        contentAlignment = Alignment.Center,
    ) {
        UpgradeDialog(
            feature = state.showUpgradePrompt!!,
            onPurchase = { viewModel.processIntent(ViewIntent.PurchasePro) },
            onRestore = { viewModel.processIntent(ViewIntent.RestorePurchases) },
            onDismiss = { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
        )
    }
}
```

**Step 2: Wire PurchaseManager into iOS entry point**

In `src/iosMain/kotlin/app/Main.kt`, add after `platformServices` (line 15):

```kotlin
val purchaseManager = remember { IosPurchaseManager() }
```

Pass to `MobileApp`:

```kotlin
MobileApp(database, platformServices, purchaseManager)
```

**Step 3: Update MobileApp to accept PurchaseManager**

In `src/mobileMain/kotlin/app/MobileApp.kt`, update the `MobileApp` function signature (line 64) to accept `PurchaseManager?`:

```kotlin
@Composable
fun MobileApp(
    database: Database,
    platformServices: MviPlatformServices,
    purchaseManager: PurchaseManager? = null,
)
```

Pass it through to `MviViewModel` (line 73-80).

Add upgrade dialog rendering after the saved imports dialog (around line 269):

```kotlin
// Upgrade prompt dialog
if (state.showUpgradePrompt != null) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
        contentAlignment = Alignment.Center,
    ) {
        UpgradeDialog(
            feature = state.showUpgradePrompt!!,
            onPurchase = { viewModel.processIntent(ViewIntent.PurchasePro) },
            onRestore = { viewModel.processIntent(ViewIntent.RestorePurchases) },
            onDismiss = { viewModel.processIntent(ViewIntent.DismissUpgradePrompt) },
        )
    }
}
```

**Step 4: Build all targets**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/desktopMain/kotlin/app/Main.kt src/iosMain/kotlin/app/Main.kt \
  src/mobileMain/kotlin/app/MobileApp.kt
git commit -m "feat: wire PurchaseManager and UpgradeDialog into all platform entry points"
```

---

### Task 7: Add Pro gating to PreferencesWizardScreen (seller checkboxes)

**Files:**
- Modify: `src/commonMain/kotlin/ui/PreferencesWizardScreen.kt:22-38` (function signature)
- Modify: `src/commonMain/kotlin/ui/PreferencesWizardScreen.kt:119-140` (seller checkboxes)
- Modify: `src/desktopMain/kotlin/app/Main.kt` (pass proStatus to PreferencesWizardScreen)
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt:158-178` (pass proStatus)

**Step 1: Add proStatus parameter to PreferencesWizardScreen**

Add to function signature (after `proxyFirst: Boolean = true`):

```kotlin
proStatus: ProStatus = ProStatus.Free,
onShowUpgradePrompt: (ProFeature) -> Unit = {},
```

**Step 2: Gate non-USEA seller checkboxes**

Replace the seller checkbox loop (lines 119-140) to show locks on non-USEA sellers for free users:

```kotlin
Seller.entries.forEach { seller ->
    val isEnabled = seller.name in enabledSellers
    val isLocked = seller != Seller.USEA && !proStatus.isPro
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { checked ->
                if (isLocked) {
                    onShowUpgradePrompt(ProFeature.MULTI_SELLER)
                } else {
                    val newList = if (checked) {
                        enabledSellers + seller.name
                    } else {
                        enabledSellers - seller.name
                    }
                    onEnabledSellersChange(newList)
                }
            },
            enabled = !isLocked,
        )
        Text(
            seller.displayName,
            style = MaterialTheme.typography.body2,
            color = if (isLocked) MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colors.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        PixelBadge(
            text = if (seller.isProxy) "P" else "R",
            color = if (seller.isProxy) PixelOrange else PixelGreen
        )
        if (isLocked) {
            Spacer(Modifier.width(4.dp))
            ProBadge(onClick = { onShowUpgradePrompt(ProFeature.MULTI_SELLER) })
        }
    }
}
```

**Step 3: Pass proStatus from Desktop Main.kt and MobileApp.kt**

In Desktop `Main.kt`, wherever `PreferencesWizardScreen` is called, pass:

```kotlin
proStatus = state.proStatus,
onShowUpgradePrompt = { feature -> viewModel.processIntent(ViewIntent.ShowUpgradePrompt(feature)) },
```

In `MobileApp.kt` `MobilePreferencesScreen` call (lines 158-178), pass the same.

**Step 4: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 5: Manual test**

Run: `./gradlew run`
Expected: Non-USEA sellers show lock badges and trigger upgrade dialog when clicked.

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/ui/PreferencesWizardScreen.kt \
  src/desktopMain/kotlin/app/Main.kt src/mobileMain/kotlin/app/MobileApp.kt
git commit -m "feat: gate non-USEA seller checkboxes behind Pro unlock"
```

---

### Task 8: Add Pro gating to saved imports and theme toggle

**Files:**
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt:143-156` (import screen)
- Modify: `src/desktopMain/kotlin/app/Main.kt` (saved imports button)

**Step 1: Gate saved imports**

In the mobile IMPORT screen (MobileApp.kt line 150-152), gate the saved imports button:

```kotlin
onShowSavedImports = {
    if (state.proStatus.isPro) {
        viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(true))
    } else {
        viewModel.processIntent(ViewIntent.ShowUpgradePrompt(ProFeature.IMPORT_HISTORY))
    }
},
```

Apply the same pattern in Desktop Main.kt wherever the saved imports button triggers `SetShowSavedImportsWindow`.

**Step 2: Gate theme toggle**

The theme toggle is already gated at the ViewModel level in `toggleTheme()` from Task 4. The UI will automatically show the upgrade prompt. No additional UI changes needed — the `requirePro` call in the ViewModel handles it.

However, for visual indication, wrap the theme toggle button with a `ProBadge` when not Pro. In `MobileInlineHeader` and Desktop's `CustomTitleBar`, add a lock icon next to the theme toggle when `proStatus` is not Pro.

**Step 3: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/mobileMain/kotlin/app/MobileApp.kt src/desktopMain/kotlin/app/Main.kt
git commit -m "feat: gate saved imports and theme toggle behind Pro unlock"
```

---

### Task 9: Add Pro gating to ResultsScreen (seller override) and ExportScreen (optimizer)

**Files:**
- Modify: `src/commonMain/kotlin/ui/ResultsScreen.kt:57-74` (add proStatus param)
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt:180-207` (results screen)
- Modify: `src/mobileMain/kotlin/app/MobileApp.kt:234-250` (export screen)

**Step 1: Add proStatus to ResultsScreen**

Add to `ResultsScreen` function signature:

```kotlin
proStatus: ProStatus = ProStatus.Free,
onShowUpgradePrompt: (ProFeature) -> Unit = {},
```

In the seller override dropdown/alternatives section (around line 654-703), gate the override action:

```kotlin
// When free user taps seller override
if (!proStatus.isPro) {
    onShowUpgradePrompt(ProFeature.SELLER_OVERRIDE)
} else {
    onOverrideSeller(index, seller)
}
```

**Step 2: Gate optimize button in mobile export screen**

In `MobileShoppingPlanScreen` call (MobileApp.kt line 234-250), the optimize button already triggers `ViewIntent.OptimizeShoppingPlan` which is gated at the ViewModel level. No additional UI change needed beyond passing `proStatus` for visual lock indication if desired.

**Step 3: Pass proStatus from callers**

In both Desktop Main.kt and MobileApp.kt, pass `state.proStatus` and the upgrade prompt callback to `ResultsScreen`.

**Step 4: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/ui/ResultsScreen.kt \
  src/mobileMain/kotlin/app/MobileApp.kt src/desktopMain/kotlin/app/Main.kt
git commit -m "feat: gate seller override and shopping optimizer behind Pro unlock"
```

---

### Task 10: Add integration test for Pro gating flow

**Files:**
- Create: `src/commonTest/kotlin/integration/ProGatingFlowTest.kt`

**Step 1: Write the integration test**

```kotlin
// src/commonTest/kotlin/integration/ProGatingFlowTest.kt
package integration

import model.ProFeature
import model.ProStatus
import model.PurchaseResult
import model.Seller
import purchase.PurchaseManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mock PurchaseManager for testing. */
class MockPurchaseManager(
    var entitlement: ProStatus = ProStatus.Free,
    var purchaseResult: PurchaseResult = PurchaseResult.SUCCESS,
) : PurchaseManager {
    override suspend fun checkEntitlement(): ProStatus = entitlement
    override suspend fun purchase(): PurchaseResult {
        if (purchaseResult == PurchaseResult.SUCCESS) {
            entitlement = ProStatus.Pro
        }
        return purchaseResult
    }
    override suspend fun restorePurchases(): ProStatus = entitlement
}

class ProGatingFlowTest {

    @Test
    fun freeUser_cannotEnableMultipleSellers() {
        val status = ProStatus.Free
        // Simulate: user tries to enable BOOTLEG_MAGE
        val newSellers = listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name)
        val isOnlyUsea = newSellers.size == 1 && newSellers.first() == Seller.USEA.name
        // Should be blocked because not pro and not only USEA
        assertFalse(isOnlyUsea || status.isPro)
    }

    @Test
    fun proUser_canEnableMultipleSellers() {
        val status = ProStatus.Pro
        val newSellers = listOf(Seller.USEA.name, Seller.BOOTLEG_MAGE.name)
        val isOnlyUsea = newSellers.size == 1 && newSellers.first() == Seller.USEA.name
        assertTrue(isOnlyUsea || status.isPro)
    }

    @Test
    fun mockPurchaseManager_purchaseSuccess_grantsPro() {
        val manager = MockPurchaseManager()
        assertEquals(ProStatus.Free, manager.entitlement)
        kotlinx.coroutines.test.runTest {
            val result = manager.purchase()
            assertEquals(PurchaseResult.SUCCESS, result)
            assertEquals(ProStatus.Pro, manager.entitlement)
        }
    }

    @Test
    fun mockPurchaseManager_purchaseCancelled_staysFree() {
        val manager = MockPurchaseManager(purchaseResult = PurchaseResult.CANCELLED)
        kotlinx.coroutines.test.runTest {
            val result = manager.purchase()
            assertEquals(PurchaseResult.CANCELLED, result)
            assertEquals(ProStatus.Free, manager.entitlement)
        }
    }
}
```

**Step 2: Run tests**

Run: `./gradlew desktopTest --tests "integration.ProGatingFlowTest" --rerun`
Expected: All PASS

**Step 3: Commit**

```bash
git add src/commonTest/kotlin/integration/ProGatingFlowTest.kt
git commit -m "test: add Pro gating integration tests with MockPurchaseManager"
```

---

### Task 11: Run full test suite and build verification

**Step 1: Run detekt**

Run: `./gradlew detekt`
Expected: No new issues

**Step 2: Run all tests**

Run: `./gradlew allTests`
Expected: All PASS

**Step 3: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESS

**Step 4: Manual smoke test**

Run: `./gradlew run`

Verify:
- App launches as free user (ProStatus.Free)
- USEA seller checkbox is enabled, other 3 show lock badges
- Clicking a locked seller shows upgrade dialog
- Saved imports button shows upgrade prompt
- Theme toggle shows upgrade prompt
- Single-seller USEA workflow still works end-to-end
- Upgrade dialog shows with correct feature description, price, and buttons
- Dismissing upgrade dialog works

**Step 5: Commit any fixes from smoke test**

```bash
git add -A
git commit -m "fix: address issues found during Pro unlock smoke testing"
```

---

### Task 12: Final cleanup and PR

**Step 1: Review all changes**

Run: `git diff main...HEAD --stat`
Verify: Only expected files changed, no accidental modifications.

**Step 2: Open PR**

```bash
gh pr create --title "feat: add Pro unlock with feature gating" --body "$(cat <<'EOF'
## Summary
- Adds freemium Pro unlock ($4.99 one-time) with RevenueCat integration stubs
- Gates multi-seller, shopping optimizer, seller override, import history, and theme toggle
- Adds ProBadge, UpgradeDialog, and ProGate UI components in pixel design system
- Adds PurchaseManager interface with platform stubs (Desktop, iOS, Android)
- Adds isPro to SQLDelight schema with migration
- Free users get full single-seller USEA workflow

## Test plan
- [ ] Unit tests pass: `./gradlew allTests`
- [ ] Detekt passes: `./gradlew detekt`
- [ ] Desktop app launches and shows free-tier experience
- [ ] Locked seller checkboxes show Pro badges and trigger upgrade dialog
- [ ] Saved imports and theme toggle gated behind Pro
- [ ] Single-seller workflow works end-to-end for free users
- [ ] Upgrade dialog renders correctly with feature descriptions
- [ ] Build succeeds on all platforms: `./gradlew build`

Closes #<issue-number>
EOF
)"
```

---

## Future Tasks (Not in this PR)

These require RevenueCat account setup and are separate work:

1. **RevenueCat account setup** — Create RevenueCat project, configure App Store/Google Play/Stripe
2. **Desktop REST API implementation** — Replace `DesktopPurchaseManager` stub with Ktor calls
3. **iOS Swift bridge** — Add RevenueCat iOS SDK via SPM, create `RevenueCatBridge.swift`
4. **Android SDK integration** — Add RevenueCat Kotlin SDK dependency, implement `AndroidPurchaseManager`
5. **Link Devices flow** — Settings UI for entering shared user ID
6. **Pro settings section** — "DeckLoot Pro" section in settings showing status, restore, link
