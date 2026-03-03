# USEA WebView Checkout Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** One-tap checkout for USEA orders on mobile via agamecardshop.com WebView with auto-built cart and coupon application. Desktop falls back to clipboard + Google Sheet instructions.

**Architecture:** Add `wcProductId` column to card variants, async-map USEA SKUs to WooCommerce product IDs via WordPress REST API at catalog refresh time, inject JavaScript into a platform-native WebView to build the cart and apply coupon codes, and replace the USEA "Email Order" button with a "Checkout on USEA" flow.

**Tech Stack:** Kotlin Multiplatform, Ktor HTTP client, SQLDelight, Android WebView, iOS WKWebView, WooCommerce AJAX API.

---

## Task 1: Database Migration — Add wcProductId Column

**Files:**
- Create: `src/commonMain/sqldelight/database/6.sqm`
- Modify: `src/commonMain/sqldelight/database/CardVariant.sq`
- Modify: `src/commonMain/kotlin/model/Models.kt:30-44`
- Modify: `src/commonMain/kotlin/database/EntityMappers.kt:26-40`
- Modify: `src/commonMain/kotlin/database/Database.kt:61-74` (insertVariant)
- Modify: `src/commonMain/kotlin/database/Database.kt:142-161` (replaceCatalogTransaction)
- Modify: `src/commonMain/kotlin/database/Database.kt:163-181` (insertVariantBatch)
- Modify: `src/commonMain/kotlin/database/Database.kt:187-205` (replaceCatalogForSellerTransaction)
- Modify: `src/commonMain/kotlin/database/CatalogStore.kt`

**Step 1: Create migration 6.sqm**

Create `src/commonMain/sqldelight/database/6.sqm`:
```sql
ALTER TABLE CardVariantEntity ADD COLUMN wcProductId TEXT;
```

**Step 2: Add updateWcProductId query to CardVariant.sq**

Append to `src/commonMain/sqldelight/database/CardVariant.sq` after line 49:
```sql
updateWcProductId:
UPDATE CardVariantEntity SET wcProductId = ? WHERE seller = ? AND sku = ?;

selectUseaVariantsWithoutWcProductId:
SELECT * FROM CardVariantEntity WHERE seller = 'USEA' AND wcProductId IS NULL;
```

**Step 3: Update insertVariant query in CardVariant.sq**

Replace the `insertVariant` query (lines 25-28) to include `wcProductId`:
```sql
insertVariant:
INSERT OR REPLACE INTO CardVariantEntity (
    nameOriginal, nameNormalized, setCode, sku, variantType, priceInCents, collectorNumber, imageUrl, smallImageUrl, seller, purchaseUri, wcProductId
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

**Step 4: Add wcProductId to CardVariant model**

In `src/commonMain/kotlin/model/Models.kt`, add field to `CardVariant` data class after `purchaseUri` (line 41):
```kotlin
val wcProductId: Int? = null,
```

**Step 5: Update EntityMappers.kt**

In `src/commonMain/kotlin/database/EntityMappers.kt:26-40`, add `wcProductId` to the `toDomain()` mapper:
```kotlin
fun CardVariantEntity.toDomain(): model.CardVariant {
    return model.CardVariant(
        nameOriginal = this.nameOriginal,
        nameNormalized = this.nameNormalized,
        setCode = this.setCode,
        sku = this.sku,
        variantType = VariantType.fromString(this.variantType),
        priceInCents = this.priceInCents.toInt(),
        collectorNumber = this.collectorNumber,
        imageUrl = this.imageUrl,
        smallImageUrl = this.smallImageUrl,
        seller = Seller.valueOf(this.seller),
        purchaseUri = this.purchaseUri,
        wcProductId = this.wcProductId?.toInt(),
    )
}
```

**Step 6: Update Database.kt insert functions**

In `src/commonMain/kotlin/database/Database.kt`, add `wcProductId` parameter to every call to `db.cardVariantQueries.insertVariant()`. There are four call sites:

1. `insertVariant()` (line 62): add `wcProductId = variant.wcProductId?.toLong()`
2. `replaceCatalogTransaction()` (line 146): add `wcProductId = variant.wcProductId?.toLong()`
3. `insertVariantBatch()` (line 166): add `wcProductId = variant.wcProductId?.toLong()`
4. `replaceCatalogForSellerTransaction()` (line 191): add `wcProductId = variant.wcProductId?.toLong()`

Also add two new functions at the bottom of `Database.kt` (before closing brace):
```kotlin
fun updateWcProductId(wcProductId: Int, seller: String, sku: String) {
    db.cardVariantQueries.updateWcProductId(
        wcProductId = wcProductId.toLong(),
        seller = seller,
        sku = sku,
    )
}

fun getUseaVariantsWithoutWcProductId(): List<CardVariant> {
    return db.cardVariantQueries.selectUseaVariantsWithoutWcProductId()
        .executeAsList().map { it.toDomain() }
}
```

**Step 7: Add CatalogStore helper**

Add to `src/commonMain/kotlin/database/CatalogStore.kt` after `markSellerFetched()` (line 118):
```kotlin
fun updateWcProductId(wcProductId: Int, seller: String, sku: String) {
    database.updateWcProductId(wcProductId, seller, sku)
}

fun getUseaVariantsWithoutWcProductId(): List<CardVariant> {
    return database.getUseaVariantsWithoutWcProductId()
}
```

**Step 8: Build to verify migration compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (SQLDelight generates updated code from migration + queries)

**Step 9: Commit**

```bash
git add src/commonMain/sqldelight/database/6.sqm \
        src/commonMain/sqldelight/database/CardVariant.sq \
        src/commonMain/kotlin/model/Models.kt \
        src/commonMain/kotlin/database/EntityMappers.kt \
        src/commonMain/kotlin/database/Database.kt \
        src/commonMain/kotlin/database/CatalogStore.kt
git commit -m "feat: add wcProductId column for agamecardshop product mapping"
```

---

## Task 2: AgamecardshopProductMapper — API Client + Fuzzy Matching

**Files:**
- Create: `src/commonMain/kotlin/catalog/AgamecardshopProductMapper.kt`
- Create: `src/commonTest/kotlin/catalog/AgamecardshopProductMapperTest.kt`

**Step 1: Write the test**

Create `src/commonTest/kotlin/catalog/AgamecardshopProductMapperTest.kt`:
```kotlin
package catalog

import model.CardVariant
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgamecardshopProductMapperTest {

    @Test
    fun `matchProductId finds exact match by set code and variant type`() {
        val searchResults = listOf(
            WpProduct(id = 42, title = "Lightning Bolt M11 normal", slug = "lightning-bolt-m11-normal"),
            WpProduct(id = 381, title = "Lightning Bolt Magic Player Rewards 2010 foil", slug = "lightning-bolt-mpr-foil"),
        )
        val variant = testVariant("Lightning Bolt", "M11", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(42, result)
    }

    @Test
    fun `matchProductId maps REGULAR to normal`() {
        val searchResults = listOf(
            WpProduct(id = 959, title = "Bitterblossom MM2 hologram", slug = "bitterblossom-mm2-hologram"),
            WpProduct(id = 166, title = "Bitterblossom Morningtide normal", slug = "bitterblossom-morningtide-normal"),
        )
        val variant = testVariant("Bitterblossom", "MM2", VariantType.HOLO)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(959, result)
    }

    @Test
    fun `matchProductId returns null when no match found`() {
        val searchResults = listOf(
            WpProduct(id = 959, title = "Bitterblossom MM2 hologram", slug = "bitterblossom-mm2-hologram"),
        )
        val variant = testVariant("Bitterblossom", "MM2", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertNull(result)
    }

    @Test
    fun `matchProductId handles case-insensitive set codes`() {
        val searchResults = listOf(
            WpProduct(id = 42, title = "Lightning Bolt m11 normal", slug = "lightning-bolt-m11-normal"),
        )
        val variant = testVariant("Lightning Bolt", "M11", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(42, result)
    }

    @Test
    fun `variantTypeToWcSuffix maps correctly`() {
        assertEquals("normal", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.REGULAR))
        assertEquals("hologram", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.HOLO))
        assertEquals("foil", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.FOIL))
    }

    private fun testVariant(name: String, set: String, type: VariantType) = CardVariant(
        nameOriginal = name,
        nameNormalized = name.lowercase(),
        setCode = set,
        sku = "XMC-TEST",
        variantType = type,
        priceInCents = 220,
        seller = Seller.USEA,
    )
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew allTests`
Expected: FAIL — `AgamecardshopProductMapper` and `WpProduct` don't exist yet

**Step 3: Implement the mapper**

Create `src/commonMain/kotlin/catalog/AgamecardshopProductMapper.kt`:
```kotlin
package catalog

import database.CatalogStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.CardVariant
import model.VariantType

@Serializable
data class WpProduct(
    val id: Int,
    val title: WpRendered? = null,
    val slug: String = "",
) {
    // Constructor for tests (title as plain string)
    constructor(id: Int, title: String, slug: String) : this(id, WpRendered(title), slug)

    val titleText: String get() = title?.rendered ?: ""
}

@Serializable
data class WpRendered(val rendered: String)

private const val BASE_URL = "https://www.agamecardshop.com"
private const val SEARCH_ENDPOINT = "$BASE_URL/wp-json/wp/v2/product"
private const val THROTTLE_MS = 50L

private val json = Json { ignoreUnknownKeys = true }

/**
 * Maps USEA card variants to agamecardshop.com WooCommerce product IDs
 * by searching the WordPress REST API and fuzzy-matching on set code + variant type.
 */
object AgamecardshopProductMapper {

    fun variantTypeToWcSuffix(type: VariantType): String = when (type) {
        VariantType.REGULAR -> "normal"
        VariantType.HOLO -> "hologram"
        VariantType.FOIL -> "foil"
    }

    /**
     * Match a card variant to a WooCommerce product ID from search results.
     * Returns null if no match found.
     */
    fun matchProductId(variant: CardVariant, searchResults: List<WpProduct>): Int? {
        val setCode = variant.setCode.lowercase()
        val wcSuffix = variantTypeToWcSuffix(variant.variantType)

        // Try exact match: title contains set code AND ends with variant type suffix
        return searchResults.firstOrNull { product ->
            val title = product.titleText.lowercase().ifEmpty { product.slug.lowercase() }
            title.contains(setCode) && title.contains(wcSuffix)
        }?.id
    }

    /**
     * Map all unmapped USEA variants to WooCommerce product IDs.
     * Runs async, non-blocking. Updates DB as results arrive.
     */
    suspend fun mapAll(
        httpClient: HttpClient,
        catalogStore: CatalogStore,
        log: (String, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val unmapped = catalogStore.getUseaVariantsWithoutWcProductId()
        if (unmapped.isEmpty()) {
            log("WC mapper: all USEA variants already mapped", "INFO")
            return@withContext
        }

        log("WC mapper: ${unmapped.size} unmapped USEA variants, starting mapping...", "INFO")

        // Group by card name to batch API calls (one search per unique name)
        val byName = unmapped.groupBy { it.nameNormalized }
        var mapped = 0
        var failed = 0

        for ((name, variants) in byName) {
            try {
                val searchResults = searchProducts(httpClient, variants.first().nameOriginal)
                for (variant in variants) {
                    val productId = matchProductId(variant, searchResults)
                    if (productId != null) {
                        catalogStore.updateWcProductId(productId, variant.seller.name, variant.sku)
                        mapped++
                    } else {
                        failed++
                    }
                }
                delay(THROTTLE_MS)
            } catch (e: Exception) {
                log("WC mapper: failed to search '$name': ${e.message}", "WARNING")
                failed += variants.size
            }
        }

        log("WC mapper: done — $mapped mapped, $failed unmatched out of ${unmapped.size}", "INFO")
    }

    /**
     * Search agamecardshop.com for products matching a card name.
     */
    internal suspend fun searchProducts(httpClient: HttpClient, cardName: String): List<WpProduct> {
        val response = httpClient.get(SEARCH_ENDPOINT) {
            parameter("search", cardName)
            parameter("per_page", "20")
            header(HttpHeaders.UserAgent, "DeckLoot/1.0 (KMP)")
        }
        val body = response.bodyAsText()
        return json.decodeFromString<List<WpProduct>>(body)
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/catalog/AgamecardshopProductMapper.kt \
        src/commonTest/kotlin/catalog/AgamecardshopProductMapperTest.kt
git commit -m "feat: add agamecardshop.com product ID mapper with fuzzy matching"
```

---

## Task 3: Hook Mapper Into Catalog Refresh

**Files:**
- Modify: `src/commonMain/kotlin/state/CatalogUseCase.kt:174-230`
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt` (ViewModel constructor area)

**Step 1: Add mapper call after USEA catalog load**

In `src/commonMain/kotlin/state/CatalogUseCase.kt`, add an import at the top:
```kotlin
import catalog.AgamecardshopProductMapper
```

In `loadAllCatalogs()`, after line 217 (`loadedSellers.add(seller)`), add a fire-and-forget coroutine:
```kotlin
// Fire-and-forget: map WooCommerce product IDs in background
scope.launch {
    try {
        AgamecardshopProductMapper.mapAll(httpClient, catalogStore, log)
    } catch (e: Exception) {
        log("WC product mapping failed: ${e.message}", "WARNING")
    }
}
```

This requires `scope` and `httpClient` to be available. Add them as constructor parameters to `CatalogUseCase`.

**Step 2: Add httpClient and scope to CatalogUseCase**

In `src/commonMain/kotlin/state/CatalogUseCase.kt`, update the class constructor (around line 112-115) to accept:
```kotlin
class CatalogUseCase(
    private val platformServices: MviPlatformServices,
    private val catalogStore: CatalogStore,
    private val sourceRegistry: CatalogSourceRegistry,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
) {
```

**Step 3: Update MviViewModel to pass httpClient and scope**

In `src/commonMain/kotlin/state/MviViewModel.kt`, where `CatalogUseCase` is constructed, pass the additional parameters. Find the construction site and add:
- `httpClient` — create or reuse a Ktor `HttpClient` at the ViewModel level
- `scope` — pass the ViewModel's `scope`

**Step 4: Build to verify compilation**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/state/CatalogUseCase.kt \
        src/commonMain/kotlin/state/MviViewModel.kt
git commit -m "feat: hook agamecardshop product mapper into catalog refresh"
```

---

## Task 4: Google Sheet TSV Format + Desktop Fallback

**Files:**
- Modify: `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt:51-84` (formatForExport)
- Modify: `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt:530-570` (SellerActionButtons)
- Create: `src/commonTest/kotlin/ui/UseaExportFormatTest.kt`

**Step 1: Write the test for Sheet format**

Create `src/commonTest/kotlin/ui/UseaExportFormatTest.kt`:
```kotlin
package ui

import model.CardVariant
import model.OrderItem
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UseaExportFormatTest {

    @Test
    fun `formatForGoogleSheet produces tab-separated format`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Bitterblossom",
                    nameNormalized = "bitterblossom",
                    setCode = "MM2",
                    sku = "XMC00003",
                    variantType = VariantType.REGULAR,
                    priceInCents = 220,
                    seller = Seller.USEA,
                ),
                qty = 1,
                isProxy = true,
            )
        )
        val result = formatForGoogleSheet(items)
        val lines = result.split("\n")
        assertEquals("Card Name\tSet\tSKU\tCard Type\tQty\tBase Price", lines[0])
        assertEquals("Bitterblossom MM2\tMM2\tXMC00003\t- Regular\t1\t\$2.20", lines[1])
    }

    @Test
    fun `formatForGoogleSheet handles foil and holo types`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "XMC00042",
                    variantType = VariantType.FOIL,
                    priceInCents = 350,
                    seller = Seller.USEA,
                ),
                qty = 4,
                isProxy = true,
            )
        )
        val result = formatForGoogleSheet(items)
        val lines = result.split("\n")
        assertTrue(lines[1].contains("- Foil"))
        assertTrue(lines[1].contains("\$3.50"))
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew allTests`
Expected: FAIL — `formatForGoogleSheet` doesn't exist

**Step 3: Implement formatForGoogleSheet**

In `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`, add after `formatForExport()` (around line 84):
```kotlin
/**
 * Format order items as tab-separated values matching the USEA Google Sheet Cart tab columns.
 * Columns: Card Name (with set), Set, SKU, Card Type (with dash prefix), Qty, Base Price
 */
internal fun formatForGoogleSheet(items: List<OrderItem>): String {
    val header = "Card Name\tSet\tSKU\tCard Type\tQty\tBase Price"
    val rows = items.joinToString("\n") { item ->
        val v = item.variant
        val cardType = "- ${v.variantType.displayName}"
        "${v.nameOriginal} ${v.setCode}\t${v.setCode}\t${v.sku}\t$cardType\t${item.qty}\t${formatPrice(v.priceInCents)}"
    }
    return "$header\n$rows"
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Replace USEA buttons in SellerActionButtons**

In `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`, replace the USEA case in `SellerActionButtons` (lines 530-570) with:
```kotlin
Seller.USEA -> {
    val hasWebViewSupport = getPlatform() != Platform.DESKTOP
    val mappedCount = order.items.count { it.variant.wcProductId != null }
    val totalCount = order.items.size

    if (hasWebViewSupport && mappedCount > 0) {
        // Mobile: WebView checkout (Task 6 will implement onCheckoutUsea)
        PixelButton(
            text = "Checkout on USEA",
            onClick = { onCheckoutUsea(order) },
            variant = PixelButtonVariant.PRIMARY,
            modifier = Modifier.weight(1f)
        )
    } else {
        // Desktop / no mapped products: clipboard + Google Sheet
        PixelButton(
            text = "Copy for Sheet",
            onClick = {
                onCopyToClipboard(formatForGoogleSheet(order.items))
                copied = true
            },
            variant = PixelButtonVariant.PRIMARY,
            modifier = Modifier.weight(1f)
        )
    }
    PixelButton(
        text = if (copied) "Copied!" else "Copy CSV",
        onClick = {
            onCopyToClipboard(formatForExport(Seller.USEA, order.items))
            copied = true
        },
        variant = PixelButtonVariant.SURFACE,
        modifier = Modifier.weight(1f)
    )
}
```

Note: `onCheckoutUsea` will be a placeholder lambda for now — wired up in Task 6. `getPlatform()` uses the existing platform detection. If no platform helper exists, use a simple `expect fun isDesktop(): Boolean` or pass it as a parameter from the screen.

**Step 6: Build to verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/commonMain/kotlin/ui/ShoppingPlanScreen.kt \
        src/commonTest/kotlin/ui/UseaExportFormatTest.kt
git commit -m "feat: add Google Sheet TSV format and desktop clipboard fallback for USEA"
```

---

## Task 5: Platform WebView Bridge (expect/actual)

**Files:**
- Create: `src/commonMain/kotlin/platform/WebViewCheckout.kt`
- Create: `src/androidMain/kotlin/platform/AndroidWebViewCheckout.kt`
- Create: `src/iosMain/kotlin/platform/IosWebViewCheckout.kt`
- Create: `src/desktopMain/kotlin/platform/DesktopWebViewCheckout.kt`

**Step 1: Define common interface**

Create `src/commonMain/kotlin/platform/WebViewCheckout.kt`:
```kotlin
package platform

import model.OrderItem

/**
 * Data needed to build a cart on agamecardshop.com via WebView.
 */
data class UseaCheckoutRequest(
    val items: List<CheckoutItem>,
    val couponCode: String?,
    val unmatchedItems: List<OrderItem>,
)

data class CheckoutItem(
    val wcProductId: Int,
    val quantity: Int,
    val cardName: String,
)

/**
 * Platform-specific WebView checkout launcher.
 * Returns true if WebView was opened, false if platform doesn't support it (desktop).
 */
expect class WebViewCheckoutLauncher {
    fun isSupported(): Boolean
}
```

**Step 2: Android implementation**

Create `src/androidMain/kotlin/platform/AndroidWebViewCheckout.kt`:
```kotlin
package platform

actual class WebViewCheckoutLauncher {
    actual fun isSupported(): Boolean = true
}
```

**Step 3: iOS implementation**

Create `src/iosMain/kotlin/platform/IosWebViewCheckout.kt`:
```kotlin
package platform

actual class WebViewCheckoutLauncher {
    actual fun isSupported(): Boolean = true
}
```

**Step 4: Desktop implementation**

Create `src/desktopMain/kotlin/platform/DesktopWebViewCheckout.kt`:
```kotlin
package platform

actual class WebViewCheckoutLauncher {
    actual fun isSupported(): Boolean = false
}
```

**Step 5: Build to verify expect/actual compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/platform/WebViewCheckout.kt \
        src/androidMain/kotlin/platform/AndroidWebViewCheckout.kt \
        src/iosMain/kotlin/platform/IosWebViewCheckout.kt \
        src/desktopMain/kotlin/platform/DesktopWebViewCheckout.kt
git commit -m "feat: add expect/actual WebView checkout launcher for all platforms"
```

---

## Task 6: Android WebView Checkout Screen

**Files:**
- Create: `src/androidMain/kotlin/ui/UseaWebViewCheckoutActivity.kt`

**Step 1: Create the Android WebView Activity**

Create `src/androidMain/kotlin/ui/UseaWebViewCheckoutActivity.kt`:
```kotlin
package ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class UseaWebViewCheckoutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEMS_JSON = "items_json"
        const val EXTRA_COUPON_CODE = "coupon_code"
    }

    private val progress = mutableIntStateOf(0)
    private val total = mutableIntStateOf(0)
    private val isBuilding = mutableStateOf(true)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemsJson = intent.getStringExtra(EXTRA_ITEMS_JSON) ?: "[]"
        val couponCode = intent.getStringExtra(EXTRA_COUPON_CODE) ?: ""

        setContent {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            addJavascriptInterface(DeckLootBridge(), "DeckLoot")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (isBuilding.value) {
                                        val js = buildCartJs(itemsJson, couponCode)
                                        view?.evaluateJavascript(js, null)
                                    }
                                }
                            }
                            loadUrl("https://www.agamecardshop.com/cart/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isBuilding.value) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Text(
                            "Building your cart... (${progress.intValue}/${total.intValue})",
                            color = Color.White,
                        )
                        if (total.intValue > 0) {
                            LinearProgressIndicator(
                                progress = progress.intValue.toFloat() / total.intValue,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildCartJs(itemsJson: String, couponCode: String): String = """
        (async () => {
            const items = $itemsJson;
            const couponCode = '$couponCode';
            let added = 0;
            DeckLoot.onTotal(items.length);

            for (const item of items) {
                const formData = new FormData();
                formData.append('product_id', item.id);
                formData.append('quantity', item.qty);
                await fetch('/?wc-ajax=add_to_cart', { method: 'POST', body: formData });
                added++;
                DeckLoot.onProgress(added);
            }

            if (couponCode) {
                const couponData = new FormData();
                couponData.append('coupon_code', couponCode);
                await fetch('/?wc-ajax=apply_coupon', { method: 'POST', body: couponData });
            }

            DeckLoot.onComplete();
            window.location.reload();
        })();
    """.trimIndent()

    inner class DeckLootBridge {
        @JavascriptInterface
        fun onTotal(count: Int) { total.intValue = count }

        @JavascriptInterface
        fun onProgress(count: Int) { progress.intValue = count }

        @JavascriptInterface
        fun onComplete() { isBuilding.value = false }
    }
}
```

**Step 2: Register Activity in AndroidManifest.xml**

Add inside the `<application>` tag:
```xml
<activity
    android:name="ui.UseaWebViewCheckoutActivity"
    android:label="USEA Checkout"
    android:exported="false" />
```

**Step 3: Build to verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/androidMain/kotlin/ui/UseaWebViewCheckoutActivity.kt
git commit -m "feat: add Android WebView checkout activity for USEA"
```

---

## Task 7: iOS WebView Checkout Screen

**Files:**
- Create: `src/iosMain/kotlin/ui/UseaWebViewCheckout.kt`

**Step 1: Create iOS WebView wrapper**

Create `src/iosMain/kotlin/ui/UseaWebViewCheckout.kt`:
```kotlin
package ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Creates a WKWebView configured for USEA cart building.
 * Call loadCart() after the view is added to the hierarchy.
 */
@OptIn(ExperimentalForeignApi::class)
class UseaWebViewCheckoutController(
    private val itemsJson: String,
    private val couponCode: String,
    private val onProgress: (current: Int, total: Int) -> Unit,
    private val onComplete: () -> Unit,
) {
    private val messageHandler = DeckLootMessageHandler(onProgress, onComplete)
    private var hasInjected = false

    val webView: WKWebView by lazy {
        val config = WKWebViewConfiguration()
        config.userContentController.addScriptMessageHandler(messageHandler, "DeckLoot")
        WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = config).apply {
            navigationDelegate = NavigationDelegate { view ->
                if (!hasInjected) {
                    hasInjected = true
                    val js = buildCartJs(itemsJson, couponCode)
                    view.evaluateJavaScript(js, null)
                }
            }
        }
    }

    fun loadCart() {
        val url = NSURL(string = "https://www.agamecardshop.com/cart/")
        val request = NSMutableURLRequest(url)
        webView.loadRequest(request)
    }

    private fun buildCartJs(itemsJson: String, couponCode: String): String = """
        (async () => {
            const items = $itemsJson;
            const couponCode = '$couponCode';
            let added = 0;
            window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'total', count:items.length}));

            for (const item of items) {
                const formData = new FormData();
                formData.append('product_id', item.id);
                formData.append('quantity', item.qty);
                await fetch('/?wc-ajax=add_to_cart', { method: 'POST', body: formData });
                added++;
                window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'progress', count:added}));
            }

            if (couponCode) {
                const couponData = new FormData();
                couponData.append('coupon_code', couponCode);
                await fetch('/?wc-ajax=apply_coupon', { method: 'POST', body: couponData });
            }

            window.webkit.messageHandlers.DeckLoot.postMessage(JSON.stringify({type:'complete'}));
            window.location.reload();
        })();
    """.trimIndent()
}

private class DeckLootMessageHandler(
    private val onProgress: (Int, Int) -> Unit,
    private val onComplete: () -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    private var total = 0

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? String ?: return
        // Simple JSON parsing without kotlinx.serialization to avoid overhead
        when {
            body.contains("\"total\"") -> {
                total = body.substringAfter("count\":").substringBefore("}").trim().toIntOrNull() ?: 0
            }
            body.contains("\"progress\"") -> {
                val current = body.substringAfter("count\":").substringBefore("}").trim().toIntOrNull() ?: 0
                onProgress(current, total)
            }
            body.contains("\"complete\"") -> onComplete()
        }
    }
}

private class NavigationDelegate(
    private val onPageFinished: (WKWebView) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onPageFinished(webView)
    }
}
```

**Step 2: Build iOS target**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (or at least no commonMain/iosMain compilation errors)

**Step 3: Commit**

```bash
git add src/iosMain/kotlin/ui/UseaWebViewCheckout.kt
git commit -m "feat: add iOS WKWebView checkout controller for USEA"
```

---

## Task 8: Wire Checkout Intent Through MVI

**Files:**
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt`
- Modify: `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`

**Step 1: Add ViewIntent and ViewEffect**

In `src/commonMain/kotlin/state/MviViewModel.kt`, add to `ViewIntent` sealed class (before line 1068 closing brace):
```kotlin
data class CheckoutUsea(val order: SellerOrder) : ViewIntent()
```

Add to `ViewEffect` sealed class (before line 1076 closing brace):
```kotlin
data class OpenUseaCheckout(
    val itemsJson: String,
    val couponCode: String,
    val unmatchedItems: List<OrderItem>,
) : ViewEffect()
```

**Step 2: Add intent routing**

In `processIntent()` async block (around line 214), add:
```kotlin
is ViewIntent.CheckoutUsea -> checkoutUsea(intent.order)
```

**Step 3: Implement the handler**

Add a new handler function in the intent handlers section:
```kotlin
private suspend fun checkoutUsea(order: SellerOrder) {
    withContext(Dispatchers.IO) {
        val mapped = order.items.filter { it.variant.wcProductId != null }
        val unmapped = order.items.filter { it.variant.wcProductId == null }

        if (mapped.isEmpty()) {
            // No products mapped — fall back to clipboard
            _viewEffects.emit(ViewEffect.ShowError("No products could be matched on the store. Use 'Copy for Sheet' instead."))
            return@withContext
        }

        // Build JSON array for WebView JS injection
        val itemsJson = mapped.joinToString(",", "[", "]") { item ->
            """{"id":${item.variant.wcProductId},"qty":${item.qty}}"""
        }

        val couponCode = useaCouponCode(order.subtotalCents) ?: ""

        _viewEffects.emit(ViewEffect.OpenUseaCheckout(
            itemsJson = itemsJson,
            couponCode = couponCode,
            unmatchedItems = unmapped,
        ))
    }
}
```

Import `useaCouponCode` from `ui.useaCouponCode` (or move it to a util file).

**Step 4: Add exhaustive match entry**

In the sync-intents-already-handled block at the bottom of the async `when` (around line 225-241), add:
```kotlin
is ViewIntent.CheckoutUsea -> {}  // not needed here, handled above
```

Wait — `CheckoutUsea` is async, so it goes in the async block (step 2). Just make sure the exhaustive match compiles.

**Step 5: Wire ShoppingPlanScreen to emit the intent**

In `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`, the `SellerActionButtons` composable needs an `onCheckoutUsea` callback. Thread it from the parent `ShoppingPlanScreen` composable down through `SellerOrderCard` to `SellerActionButtons`. Follow the same pattern as `onCopyToClipboard` and `onOpenUrl`.

**Step 6: Handle the ViewEffect in the platform-specific main screen**

In the main screen composable (where ViewEffects are collected), add a handler for `ViewEffect.OpenUseaCheckout`:
- **Android**: Launch `UseaWebViewCheckoutActivity` with the `itemsJson` and `couponCode` extras
- **iOS**: Present the `UseaWebViewCheckoutController` in a sheet or full-screen modal
- **Desktop**: Ignored (button won't appear — uses clipboard fallback)

If there are unmatched items, copy them to clipboard and show a message.

**Step 7: Build to verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt \
        src/commonMain/kotlin/ui/ShoppingPlanScreen.kt
git commit -m "feat: wire USEA checkout intent through MVI with WebView effect"
```

---

## Task 9: End-to-End Manual Testing

**Step 1: Run desktop app**

Run: `./gradlew run`

Verify:
- USEA order card shows "Copy for Sheet" button (not "Checkout on USEA")
- Clicking "Copy for Sheet" copies TSV to clipboard
- TSV format matches: `Card Name\tSet\tSKU\tCard Type\tQty\tBase Price`
- Card Type has leading dash: `- Regular`, `- Foil`, `- Holo`

**Step 2: Check catalog refresh logs**

In the app logs, verify:
- After catalog loads, "WC mapper: X unmapped USEA variants, starting mapping..." appears
- Mapping completes with "WC mapper: done — X mapped, Y unmatched out of Z"

**Step 3: Run Android emulator (if available)**

Run: `./gradlew installDebug`

Verify:
- USEA order card shows "Checkout on USEA" button
- Tapping it opens a WebView with progress overlay
- Cart is populated on agamecardshop.com
- Coupon code is applied
- Checkout page is reachable

**Step 4: Commit any fixes**

```bash
git commit -m "fix: address issues found during manual testing"
```

---

## Task Dependency Graph

```
Task 1 (DB migration)
  ├──→ Task 2 (Product mapper)
  │      └──→ Task 3 (Hook into catalog refresh)
  ├──→ Task 4 (Sheet format + desktop fallback)
  ├──→ Task 5 (expect/actual bridge)
  │      ├──→ Task 6 (Android WebView)
  │      └──→ Task 7 (iOS WebView)
  └──→ Task 8 (MVI wiring) — depends on Tasks 4, 5, 6, 7
         └──→ Task 9 (Manual testing) — depends on all
```

**Parallelizable:** Tasks 2+4+5 can run in parallel after Task 1. Tasks 6+7 can run in parallel after Task 5.
