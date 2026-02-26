# Shopping Assistant Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform MTG Pirate from a single-seller CSV export tool into a multi-seller shopping assistant with price comparison, bulk discount optimization, and per-seller checkout helpers.

**Architecture:** Extend the existing MVI architecture with a `CatalogSource` interface for seller abstraction, a unified multi-catalog matching engine, and a threshold-aware shopping optimizer. Two new sellers (Bootleg Mage, Scryfall/TCGPlayer) join the existing USEA integration. A merged Shopping Plan + Checkout screen replaces the Export screen.

**Tech Stack:** Kotlin Multiplatform, Compose, SQLDelight, Ktor, Scryfall API, WooCommerce scraping (Bootleg Mage)

**Design Doc:** `docs/plans/2026-02-26-shopping-assistant-design.md`

---

## Task 1: Add Seller Enum and CatalogSource Interface

**Files:**
- Modify: `src/commonMain/kotlin/model/Models.kt` (add Seller enum, extend CardVariant)
- Create: `src/commonMain/kotlin/catalog/CatalogSource.kt` (new interface — NOT the existing CatalogDataSource.kt)

**Step 1: Add Seller enum to Models.kt**

After the `VariantType` enum (line 20), add:

```kotlin
enum class Seller(val displayName: String, val isProxy: Boolean) {
    USEA("USEA MTG Proxy", true),
    BOOTLEG_MAGE("Bootleg Mage", true),
    TCGPLAYER("TCGPlayer", false);
}
```

**Step 2: Add seller and purchaseUri to CardVariant**

Extend the `CardVariant` data class (line 23) to include:

```kotlin
data class CardVariant(
    val nameOriginal: String,
    val nameNormalized: String,
    val setCode: String,
    val sku: String,
    val variantType: VariantType,
    val priceInCents: Int,
    val collectorNumber: String? = null,
    val imageUrl: String? = null,
    val seller: Seller = Seller.USEA,
    val purchaseUri: String? = null,
)
```

Default `seller = Seller.USEA` ensures backward compatibility.

**Step 3: Add new shopping models to Models.kt**

After `SavedImport` (end of file), add:

```kotlin
data class MatchOption(
    val variant: CardVariant,
    val seller: Seller,
    val priceCents: Int,
    val isProxy: Boolean,
    val matchScore: Int,
)

data class MultiMatch(
    val deckEntry: DeckEntry,
    val bestOption: MatchOption?,
    val alternatives: List<MatchOption>,
    val realCardFallback: MatchOption?,
)

data class OrderItem(
    val variant: CardVariant,
    val qty: Int,
    val isProxy: Boolean,
)

data class SellerOrder(
    val seller: Seller,
    val items: List<OrderItem>,
    val subtotalCents: Int,
    val discountPercent: Int,
    val shippingCents: Int,
    val totalCents: Int,
)

data class ShoppingPlan(
    val orders: List<SellerOrder>,
    val totalPriceCents: Int,
    val savingsVsSingleSeller: Int,
)
```

**Step 4: Create CatalogSource interface**

Create `src/commonMain/kotlin/catalog/CatalogSource.kt`:

```kotlin
package catalog

import model.CardVariant
import model.OrderItem
import model.Seller

interface CatalogSource {
    val seller: Seller
    suspend fun fetchCatalog(log: (String) -> Unit = {}): List<CardVariant>
    suspend fun search(cardName: String): List<CardVariant>
    fun checkoutUrl(items: List<OrderItem>): String?
    fun formatForExport(items: List<OrderItem>): String
}
```

**Step 5: Compile check**

Run: `./gradlew build`
Expected: Compiles with no errors (defaults on new fields ensure backward compat)

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/model/Models.kt src/commonMain/kotlin/catalog/CatalogSource.kt
git commit -m "feat: add Seller enum, CatalogSource interface, and shopping models"
```

---

## Task 2: Database Schema Migration

**Files:**
- Modify: `src/commonMain/sqldelight/database/CardVariant.sq`
- Modify: `src/commonMain/kotlin/database/EntityMappers.kt` (line 25-36)
- Modify: `src/commonMain/kotlin/database/Database.kt` (lines 61-72)
- Modify: `src/commonMain/kotlin/database/CatalogStore.kt`

**Step 1: Add seller and purchaseUri columns to CardVariant.sq**

Add two new columns to the `CardVariantEntity` CREATE TABLE:

```sql
CREATE TABLE CardVariantEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nameOriginal TEXT NOT NULL,
    nameNormalized TEXT NOT NULL,
    setCode TEXT NOT NULL,
    sku TEXT NOT NULL UNIQUE,
    variantType TEXT NOT NULL,
    priceInCents INTEGER NOT NULL,
    collectorNumber TEXT,
    imageUrl TEXT,
    seller TEXT NOT NULL DEFAULT 'USEA',
    purchaseUri TEXT
);
CREATE INDEX idx_nameNormalized ON CardVariantEntity(nameNormalized);
CREATE UNIQUE INDEX idx_variant_sku ON CardVariantEntity(sku);
CREATE INDEX idx_seller ON CardVariantEntity(seller);
```

Also update the `insertVariant` query to include the new columns:

```sql
insertVariant:
INSERT OR REPLACE INTO CardVariantEntity(nameOriginal, nameNormalized, setCode, sku, variantType, priceInCents, collectorNumber, imageUrl, seller, purchaseUri)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Add a query to select by seller:

```sql
selectBySeller:
SELECT * FROM CardVariantEntity WHERE seller = ?;

deleteAllBySeller:
DELETE FROM CardVariantEntity WHERE seller = ?;
```

**Step 2: Update EntityMappers.kt**

Update the `CardVariantEntity.toDomain()` mapper (line 25) to map the new fields:

```kotlin
fun CardVariantEntity.toDomain(): CardVariant = CardVariant(
    nameOriginal = nameOriginal,
    nameNormalized = nameNormalized,
    setCode = setCode,
    sku = sku,
    variantType = VariantType.fromString(variantType),
    priceInCents = priceInCents.toInt(),
    collectorNumber = collectorNumber,
    imageUrl = imageUrl,
    seller = Seller.valueOf(seller),
    purchaseUri = purchaseUri,
)
```

**Step 3: Update Database.kt insertVariant()**

Update `insertVariant()` (line 61) to pass seller and purchaseUri:

```kotlin
fun insertVariant(variant: CardVariant) {
    queries.insertVariant(
        nameOriginal = variant.nameOriginal,
        nameNormalized = variant.nameNormalized,
        setCode = variant.setCode,
        sku = variant.sku,
        variantType = variant.variantType.name,
        priceInCents = variant.priceInCents.toLong(),
        collectorNumber = variant.collectorNumber,
        imageUrl = variant.imageUrl,
        seller = variant.seller.name,
        purchaseUri = variant.purchaseUri,
    )
}
```

**Step 4: Update CatalogStore**

Add a `replaceCatalogForSeller()` method that only clears+replaces variants for a specific seller (instead of wiping the entire catalog):

```kotlin
suspend fun replaceCatalogForSeller(seller: Seller, variants: List<CardVariant>) {
    database.transaction {
        queries.deleteAllBySeller(seller.name)
        variants.forEach { database.insertVariant(it) }
    }
}
```

Keep the existing `replaceCatalog()` for backward compatibility but have it pass `Seller.USEA`.

**Step 5: Update the Catalog domain model**

The `Catalog` class in Models.kt (line 37) currently wraps a single list. It needs to hold variants from all sellers. The `indexByName` already groups by normalized name across all variants, so the existing structure works — just ensure the index includes the seller field for filtering. No structural change needed, but verify the lazy index still works when variants from multiple sellers share the same normalized name.

**Step 6: Compile and test**

Run: `./gradlew build && ./gradlew allTests`
Expected: All pass. The schema change requires a fresh database (SQLDelight handles this via version incrementing or drop-and-recreate for dev).

**Step 7: Commit**

```bash
git add src/commonMain/sqldelight/database/CardVariant.sq \
        src/commonMain/kotlin/database/EntityMappers.kt \
        src/commonMain/kotlin/database/Database.kt \
        src/commonMain/kotlin/database/CatalogStore.kt
git commit -m "feat: add seller and purchaseUri columns to database schema"
```

---

## Task 3: USEA CatalogSource Adapter

**Files:**
- Create: `src/commonMain/kotlin/catalog/UseaCatalogSource.kt`
- Modify: `src/commonMain/kotlin/catalog/KtorRemoteCatalogDataSource.kt` (extract logic)

**Step 1: Write test for USEA adapter**

Create `src/commonTest/kotlin/catalog/UseaCatalogSourceTest.kt`:

```kotlin
package catalog

import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals

class UseaCatalogSourceTest {
    @Test
    fun `seller is USEA`() {
        val source = UseaCatalogSource(mockDataSource)
        assertEquals(Seller.USEA, source.seller)
    }

    @Test
    fun `fetched variants have USEA seller`() = runTest {
        val source = UseaCatalogSource(mockDataSource)
        val variants = source.fetchCatalog()
        variants.forEach { assertEquals(Seller.USEA, it.seller) }
    }

    @Test
    fun `formatForExport generates CSV`() {
        val source = UseaCatalogSource(mockDataSource)
        val csv = source.formatForExport(testItems)
        assert(csv.startsWith("Card Name,Set,SKU,Card Type,Quantity,Base Price"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew allTests`
Expected: FAIL — UseaCatalogSource doesn't exist yet

**Step 3: Implement UseaCatalogSource**

Create `src/commonMain/kotlin/catalog/UseaCatalogSource.kt`:

```kotlin
package catalog

import model.CardVariant
import model.OrderItem
import model.Seller
import export.CsvGenerator

class UseaCatalogSource(
    private val remoteCatalogDataSource: CatalogDataSource,
) : CatalogSource {
    override val seller = Seller.USEA

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        val catalog = remoteCatalogDataSource.load(forceRefresh = true, log = log)
        return catalog?.variants?.map { it.copy(seller = Seller.USEA) } ?: emptyList()
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        // USEA doesn't support on-demand search, return empty
        // All matching happens against the cached catalog
        return emptyList()
    }

    override fun checkoutUrl(items: List<OrderItem>): String? = null // Email-based ordering

    override fun formatForExport(items: List<OrderItem>): String {
        return CsvGenerator.generateFoundCardsCsv(
            items.map { it.variant to it.qty }
        )
    }
}
```

**Step 4: Run tests**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/catalog/UseaCatalogSource.kt \
        src/commonTest/kotlin/catalog/UseaCatalogSourceTest.kt
git commit -m "feat: wrap USEA catalog loading in CatalogSource adapter"
```

---

## Task 4: Bootleg Mage CatalogSource

**Files:**
- Create: `src/commonMain/kotlin/catalog/BootlegMageCatalogSource.kt`
- Create: `src/commonTest/kotlin/catalog/BootlegMageCatalogSourceTest.kt`

**Step 1: Write test for Bootleg Mage catalog parsing**

```kotlin
package catalog

import model.Seller
import kotlin.test.Test
import kotlin.test.assertEquals

class BootlegMageCatalogSourceTest {
    @Test
    fun `seller is BOOTLEG_MAGE`() {
        val source = BootlegMageCatalogSource(mockHttpClient)
        assertEquals(Seller.BOOTLEG_MAGE, source.seller)
    }

    @Test
    fun `parseProductJson extracts card variants`() {
        val json = """[{
            "id": 12345,
            "name": "Lightning Bolt - M11 - Foil",
            "price": "3.50",
            "sku": "BM-12345",
            "categories": [{"name": "Foil"}],
            "stock_status": "instock"
        }]"""
        val variants = BootlegMageCatalogSource.parseProductJson(json)
        assertEquals(1, variants.size)
        assertEquals("Lightning Bolt", variants[0].nameOriginal)
        assertEquals(Seller.BOOTLEG_MAGE, variants[0].seller)
        assertEquals(350, variants[0].priceInCents)
    }

    @Test
    fun `formatForExport generates deck list format`() {
        val source = BootlegMageCatalogSource(mockHttpClient)
        val output = source.formatForExport(testItems)
        // Bootleg Mage deck import expects "4 Lightning Bolt" format
        assert(output.contains("4 Lightning Bolt"))
    }

    @Test
    fun `checkoutUrl returns deck import page`() {
        val source = BootlegMageCatalogSource(mockHttpClient)
        val url = source.checkoutUrl(testItems)
        assertEquals("https://bootlegmage.com/deck-import/", url)
    }
}
```

**Step 2: Run test to verify failure**

Run: `./gradlew allTests`
Expected: FAIL

**Step 3: Implement BootlegMageCatalogSource**

Create `src/commonMain/kotlin/catalog/BootlegMageCatalogSource.kt`:

```kotlin
package catalog

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import model.*
import match.NameNormalizer

class BootlegMageCatalogSource(
    private val httpClient: HttpClient,
) : CatalogSource {
    override val seller = Seller.BOOTLEG_MAGE

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        log("Fetching Bootleg Mage catalog...")
        val variants = mutableListOf<CardVariant>()
        var page = 1
        val perPage = 100

        while (true) {
            val response = httpClient.get("https://bootlegmage.com/wp-json/wc/v3/products") {
                parameter("per_page", perPage)
                parameter("page", page)
                parameter("status", "publish")
            }
            val body = response.bodyAsText()
            if (body == "[]" || body.isBlank()) break

            val parsed = parseProductJson(body)
            if (parsed.isEmpty()) break
            variants.addAll(parsed)
            log("  Page $page: ${parsed.size} products")
            page++
        }

        log("Bootleg Mage catalog: ${variants.size} variants loaded")
        return variants
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        // Fallback: use WooCommerce search endpoint
        val response = httpClient.get("https://bootlegmage.com/wp-json/wc/v3/products") {
            parameter("search", cardName)
            parameter("per_page", 20)
        }
        return parseProductJson(response.bodyAsText())
    }

    override fun checkoutUrl(items: List<OrderItem>): String =
        "https://bootlegmage.com/deck-import/"

    override fun formatForExport(items: List<OrderItem>): String {
        // Bootleg Mage deck import format: "qty CardName"
        return items.joinToString("\n") { "${it.qty} ${it.variant.nameOriginal}" }
    }

    companion object {
        fun parseProductJson(json: String): List<CardVariant> {
            val jsonArray = Json.parseToJsonElement(json).jsonArray
            return jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val price = obj["price"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val sku = obj["sku"]?.jsonPrimitive?.content ?: "BM-${obj["id"]}"
                val stockStatus = obj["stock_status"]?.jsonPrimitive?.content
                if (stockStatus == "outofstock") return@mapNotNull null

                // Parse card name — strip set code and variant from product name
                // Format: "Card Name - SET - Variant" or "Card Name - SET"
                val parts = name.split(" - ").map { it.trim() }
                val cardName = parts.firstOrNull() ?: return@mapNotNull null
                val setCode = if (parts.size > 1) parts[1] else ""

                // Determine variant type from categories or name
                val categories = obj["categories"]?.jsonArray
                    ?.map { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: emptyList()
                val variantType = when {
                    categories.any { it.contains("Foil", ignoreCase = true) } -> VariantType.FOIL
                    categories.any { it.contains("Holo", ignoreCase = true) } -> VariantType.HOLO
                    name.contains("Foil", ignoreCase = true) -> VariantType.FOIL
                    name.contains("Holo", ignoreCase = true) -> VariantType.HOLO
                    else -> VariantType.REGULAR
                }

                val priceCents = ((price.toDoubleOrNull() ?: 0.0) * 100).toInt()

                CardVariant(
                    nameOriginal = cardName,
                    nameNormalized = NameNormalizer.normalize(cardName),
                    setCode = setCode,
                    sku = sku,
                    variantType = variantType,
                    priceInCents = priceCents,
                    seller = Seller.BOOTLEG_MAGE,
                )
            }
        }
    }
}
```

**Important:** The WooCommerce REST API at `/wp-json/wc/v3/products` may require authentication or may be disabled. If it returns 401/403, we need a fallback strategy — either scraping product pages with Ksoup (already a dependency) or using the deck import AJAX endpoint for on-demand search only. Build the adapter to handle both paths gracefully.

**Step 4: Run tests**

Run: `./gradlew allTests`
Expected: PASS (unit tests with mock data pass; integration with real site tested manually)

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/catalog/BootlegMageCatalogSource.kt \
        src/commonTest/kotlin/catalog/BootlegMageCatalogSourceTest.kt
git commit -m "feat: add Bootleg Mage catalog source with WooCommerce integration"
```

---

## Task 5: Scryfall Pricing CatalogSource (Real Card Fallback)

**Files:**
- Create: `src/commonMain/kotlin/catalog/ScryfallPricingSource.kt`
- Modify: `src/commonMain/kotlin/catalog/ScryfallApi.kt` (extend with bulk collection endpoint and pricing)
- Create: `src/commonTest/kotlin/catalog/ScryfallPricingSourceTest.kt`

**Step 1: Extend ScryfallApi with bulk collection endpoint**

The existing `ScryfallApi.kt` (line 97+) has `getCardImageUrl()` and `searchCard()`. Add a `getCollection()` method for bulk price lookups:

```kotlin
// Add to ScryfallApi.kt

@Serializable
data class ScryfallPrices(
    val usd: String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
)

@Serializable
data class ScryfallPurchaseUris(
    val tcgplayer: String? = null,
    val cardmarket: String? = null,
    val cardhoarder: String? = null,
)

// Extend existing ScryfallCard to include prices and purchase_uris:
// val prices: ScryfallPrices? = null,
// val purchaseUris: ScryfallPurchaseUris? = null,

suspend fun getCollection(identifiers: List<Map<String, String>>): List<ScryfallCard> {
    // POST https://api.scryfall.com/cards/collection
    // Body: { "identifiers": [{"name": "Lightning Bolt"}, ...] }
    // Max 75 identifiers per request
    val allCards = mutableListOf<ScryfallCard>()
    identifiers.chunked(75).forEach { chunk ->
        delay(75) // Scryfall rate limit
        val response = httpClient.post("https://api.scryfall.com/cards/collection") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonArray("identifiers") {
                    chunk.forEach { id ->
                        addJsonObject {
                            id.forEach { (k, v) -> put(k, v) }
                        }
                    }
                }
            })
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonArray ?: return@forEach
        // Parse each card
        allCards.addAll(data.map { Json.decodeFromJsonElement<ScryfallCard>(it) })
    }
    return allCards
}
```

**Step 2: Create ScryfallPricingSource**

Create `src/commonMain/kotlin/catalog/ScryfallPricingSource.kt`:

```kotlin
package catalog

import model.*
import match.NameNormalizer

class ScryfallPricingSource(
    private val scryfallApi: ScryfallApi,
) : CatalogSource {
    override val seller = Seller.TCGPLAYER

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        // Scryfall is on-demand only — no full catalog fetch
        return emptyList()
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        val cards = scryfallApi.getCollection(
            listOf(mapOf("name" to cardName))
        )
        return cards.flatMap { card -> cardToVariants(card) }
    }

    suspend fun searchBulk(cardNames: List<String>): Map<String, List<CardVariant>> {
        val identifiers = cardNames.map { mapOf("name" to it) }
        val cards = scryfallApi.getCollection(identifiers)
        return cards.groupBy(
            keySelector = { NameNormalizer.normalize(it.name) },
            valueTransform = { card -> cardToVariants(card) }
        ).mapValues { it.value.flatten() }
    }

    private fun cardToVariants(card: ScryfallCard): List<CardVariant> {
        val variants = mutableListOf<CardVariant>()
        val usdPrice = card.prices?.usd?.toDoubleOrNull()
        val usdFoilPrice = card.prices?.usdFoil?.toDoubleOrNull()

        if (usdPrice != null) {
            variants.add(CardVariant(
                nameOriginal = card.name,
                nameNormalized = NameNormalizer.normalize(card.name),
                setCode = card.set.uppercase(),
                sku = "SCRY-${card.set}-${card.collectorNumber}",
                variantType = VariantType.REGULAR,
                priceInCents = (usdPrice * 100).toInt(),
                collectorNumber = card.collectorNumber,
                imageUrl = extractImageUrl(card),
                seller = Seller.TCGPLAYER,
                purchaseUri = card.purchaseUris?.tcgplayer,
            ))
        }
        if (usdFoilPrice != null) {
            variants.add(CardVariant(
                nameOriginal = card.name,
                nameNormalized = NameNormalizer.normalize(card.name),
                setCode = card.set.uppercase(),
                sku = "SCRY-${card.set}-${card.collectorNumber}-FOIL",
                variantType = VariantType.FOIL,
                priceInCents = (usdFoilPrice * 100).toInt(),
                collectorNumber = card.collectorNumber,
                imageUrl = extractImageUrl(card),
                seller = Seller.TCGPLAYER,
                purchaseUri = card.purchaseUris?.tcgplayer,
            ))
        }
        return variants
    }

    override fun checkoutUrl(items: List<OrderItem>): String? {
        // Build TCGPlayer mass entry URL or return first purchase URI
        return items.firstOrNull()?.variant?.purchaseUri
    }

    override fun formatForExport(items: List<OrderItem>): String {
        // TCGPlayer mass entry format: "1 Lightning Bolt [M11]"
        return items.joinToString("\n") {
            "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
        }
    }

    private fun extractImageUrl(card: ScryfallCard): String? {
        return card.imageUris?.normal ?: card.cardFaces?.firstOrNull()?.imageUris?.normal
    }
}
```

**Step 3: Write tests**

```kotlin
package catalog

import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScryfallPricingSourceTest {
    @Test
    fun `seller is TCGPLAYER`() {
        val source = ScryfallPricingSource(mockScryfallApi)
        assertEquals(Seller.TCGPLAYER, source.seller)
    }

    @Test
    fun `cardToVariants creates regular and foil variants`() {
        // Test with a card that has both USD and USD foil prices
        val card = mockScryfallCard(
            name = "Lightning Bolt",
            set = "m11",
            usd = "1.50",
            usdFoil = "5.00",
            tcgplayerUri = "https://tcgplayer.com/..."
        )
        val variants = ScryfallPricingSource.cardToVariants(card)
        assertEquals(2, variants.size)
        assertEquals(VariantType.REGULAR, variants[0].variantType)
        assertEquals(150, variants[0].priceInCents)
        assertEquals(VariantType.FOIL, variants[1].variantType)
        assertEquals(500, variants[1].priceInCents)
        variants.forEach {
            assertEquals(Seller.TCGPLAYER, it.seller)
            assertNotNull(it.purchaseUri)
        }
    }

    @Test
    fun `formatForExport uses TCGPlayer mass entry format`() {
        val source = ScryfallPricingSource(mockScryfallApi)
        val output = source.formatForExport(testItems)
        assert(output.contains("4 Lightning Bolt [M11]"))
    }
}
```

**Step 4: Run tests**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/catalog/ScryfallPricingSource.kt \
        src/commonMain/kotlin/catalog/ScryfallApi.kt \
        src/commonTest/kotlin/catalog/ScryfallPricingSourceTest.kt
git commit -m "feat: add Scryfall pricing source for real card fallback"
```

---

## Task 6: Multi-Catalog Matching Engine

**Files:**
- Create: `src/commonMain/kotlin/match/MultiCatalogMatcher.kt`
- Create: `src/commonTest/kotlin/match/MultiCatalogMatcherTest.kt`
- Modify: `src/commonMain/kotlin/match/Matcher.kt` (reuse existing algorithm)

**Step 1: Write tests for multi-catalog matching**

```kotlin
package match

import model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiCatalogMatcherTest {

    @Test
    fun `proxy-first - picks proxy when available in both`() {
        val useaVariant = testVariant("Lightning Bolt", Seller.USEA, 220)
        val realVariant = testVariant("Lightning Bolt", Seller.TCGPLAYER, 150)
        val catalogs = mapOf(
            Seller.USEA to catalogOf(useaVariant),
            Seller.TCGPLAYER to catalogOf(realVariant),
        )
        val entry = testDeckEntry("Lightning Bolt", 4)

        val result = MultiCatalogMatcher.match(
            entries = listOf(entry),
            catalogs = catalogs,
            config = defaultConfig(),
        )

        assertEquals(1, result.size)
        assertTrue(result[0].bestOption!!.isProxy)
        assertEquals(Seller.USEA, result[0].bestOption!!.seller)
    }

    @Test
    fun `falls back to real card when no proxy available`() {
        val realVariant = testVariant("Tarmogoyf", Seller.TCGPLAYER, 5000)
        val catalogs = mapOf(
            Seller.USEA to emptyCatalog(),
            Seller.TCGPLAYER to catalogOf(realVariant),
        )
        val entry = testDeckEntry("Tarmogoyf", 1)

        val result = MultiCatalogMatcher.match(
            entries = listOf(entry),
            catalogs = catalogs,
            config = defaultConfig(),
        )

        assertEquals(Seller.TCGPLAYER, result[0].bestOption!!.seller)
        assertEquals(false, result[0].bestOption!!.isProxy)
        assertEquals(result[0].bestOption, result[0].realCardFallback)
    }

    @Test
    fun `picks cheapest proxy across sellers`() {
        val useaVariant = testVariant("Lightning Bolt", Seller.USEA, 220)
        val bmVariant = testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)
        val catalogs = mapOf(
            Seller.USEA to catalogOf(useaVariant),
            Seller.BOOTLEG_MAGE to catalogOf(bmVariant),
        )
        val entry = testDeckEntry("Lightning Bolt", 4)

        val result = MultiCatalogMatcher.match(
            entries = listOf(entry),
            catalogs = catalogs,
            config = defaultConfig(),
        )

        assertEquals(Seller.BOOTLEG_MAGE, result[0].bestOption!!.seller)
        assertEquals(180, result[0].bestOption!!.priceCents)
    }

    @Test
    fun `alternatives include all sellers`() {
        val useaVariant = testVariant("Lightning Bolt", Seller.USEA, 220)
        val bmVariant = testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)
        val realVariant = testVariant("Lightning Bolt", Seller.TCGPLAYER, 150)
        val catalogs = mapOf(
            Seller.USEA to catalogOf(useaVariant),
            Seller.BOOTLEG_MAGE to catalogOf(bmVariant),
            Seller.TCGPLAYER to catalogOf(realVariant),
        )
        val entry = testDeckEntry("Lightning Bolt", 4)

        val result = MultiCatalogMatcher.match(
            entries = listOf(entry),
            catalogs = catalogs,
            config = defaultConfig(),
        )

        assertEquals(3, result[0].alternatives.size)
    }
}
```

**Step 2: Run tests to verify failure**

Run: `./gradlew allTests`
Expected: FAIL

**Step 3: Implement MultiCatalogMatcher**

Create `src/commonMain/kotlin/match/MultiCatalogMatcher.kt`:

```kotlin
package match

import model.*

object MultiCatalogMatcher {

    data class Config(
        val variantPriority: List<String> = listOf("Foil", "Holo", "Regular"),
        val setPriority: List<String> = emptyList(),
        val fuzzyEnabled: Boolean = true,
        val proxyFirst: Boolean = true,
    )

    fun match(
        entries: List<DeckEntry>,
        catalogs: Map<Seller, Catalog>,
        config: Config,
    ): List<MultiMatch> {
        val matchConfig = MatchConfig(
            variantPriority = config.variantPriority,
            setPriority = config.setPriority,
            fuzzyEnabled = config.fuzzyEnabled,
        )

        return entries.map { entry ->
            val allOptions = mutableListOf<MatchOption>()

            // Match against each catalog
            for ((seller, catalog) in catalogs) {
                val results = Matcher.matchAll(listOf(entry), catalog, matchConfig)
                val result = results.firstOrNull() ?: continue

                if (result.status == MatchStatus.NOT_FOUND) continue

                // Add all candidates as options
                result.candidates.forEach { candidate ->
                    allOptions.add(MatchOption(
                        variant = candidate.variant,
                        seller = seller,
                        priceCents = candidate.variant.priceInCents,
                        isProxy = seller.isProxy,
                        matchScore = candidate.score,
                    ))
                }

                // If auto-matched, add the selected variant
                if (result.selectedVariant != null) {
                    val existing = allOptions.any {
                        it.variant.sku == result.selectedVariant.sku
                    }
                    if (!existing) {
                        allOptions.add(MatchOption(
                            variant = result.selectedVariant,
                            seller = seller,
                            priceCents = result.selectedVariant.priceInCents,
                            isProxy = seller.isProxy,
                            matchScore = 0,
                        ))
                    }
                }
            }

            // Sort: proxy first (if proxyFirst), then by price
            val sorted = allOptions.sortedWith(
                compareBy<MatchOption> { if (config.proxyFirst && it.isProxy) 0 else 1 }
                    .thenBy { it.priceCents }
                    .thenBy { it.matchScore }
            )

            val bestOption = sorted.firstOrNull()
            val realCardFallback = sorted.firstOrNull { !it.isProxy }

            MultiMatch(
                deckEntry = entry,
                bestOption = bestOption,
                alternatives = sorted,
                realCardFallback = if (bestOption?.isProxy == true) realCardFallback else null,
            )
        }
    }
}
```

**Step 4: Run tests**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/match/MultiCatalogMatcher.kt \
        src/commonTest/kotlin/match/MultiCatalogMatcherTest.kt
git commit -m "feat: add multi-catalog matching engine with proxy-first priority"
```

---

## Task 7: Shopping Optimizer

**Files:**
- Create: `src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt`
- Create: `src/commonTest/kotlin/optimizer/ShoppingOptimizerTest.kt`
- Modify: `src/commonMain/kotlin/util/Promotions.kt` (extract discount config)

**Step 1: Extract seller discount configs**

Create `src/commonMain/kotlin/optimizer/SellerDiscountConfig.kt`:

```kotlin
package optimizer

import model.Seller

data class DiscountTier(val minCents: Int, val discountPercent: Int)
data class ShippingTier(val minCents: Int, val shippingCents: Int, val label: String)

data class SellerDiscountConfig(
    val seller: Seller,
    val discountTiers: List<DiscountTier>,  // Sorted descending by minCents
    val shippingTiers: List<ShippingTier>,  // Sorted descending by minCents
)

val USEA_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.USEA,
    discountTiers = listOf(
        DiscountTier(40000, 50),  // >$400 → 50%
        DiscountTier(30000, 35),  // >$300 → 35%
        DiscountTier(20000, 30),  // >$200 → 30%
        DiscountTier(16000, 25),  // >$160 → 25%
        DiscountTier(10000, 15),  // >$100 → 15%
        DiscountTier(6000, 5),    // >$60 → 5%
    ),
    shippingTiers = listOf(
        ShippingTier(30000, 0, "Express Free"),    // >$300 after discount
        ShippingTier(10000, 0, "Normal Free"),     // >$100 after discount
        ShippingTier(0, 1000, "Normal $10"),       // Otherwise $10
    ),
)

val BOOTLEG_MAGE_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.BOOTLEG_MAGE,
    discountTiers = emptyList(),  // TODO: scrape their discount structure
    shippingTiers = listOf(
        ShippingTier(0, 0, "Free Shipping"),  // Bootleg Mage offers free shipping
    ),
)

val TCGPLAYER_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.TCGPLAYER,
    discountTiers = emptyList(),  // No bulk discounts for individual TCGPlayer purchases
    shippingTiers = listOf(
        ShippingTier(0, 0, "Varies by seller"),
    ),
)

fun getDiscountConfig(seller: Seller): SellerDiscountConfig = when (seller) {
    Seller.USEA -> USEA_DISCOUNT_CONFIG
    Seller.BOOTLEG_MAGE -> BOOTLEG_MAGE_DISCOUNT_CONFIG
    Seller.TCGPLAYER -> TCGPLAYER_DISCOUNT_CONFIG
}
```

**Step 2: Write optimizer tests**

```kotlin
package optimizer

import model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingOptimizerTest {

    @Test
    fun `single seller — applies discount tier correctly`() {
        val items = listOf(
            testMultiMatch("Card A", Seller.USEA, 10000, 4),  // $100 × 4 = $400
        )
        val plan = ShoppingOptimizer.optimize(items)
        assertEquals(1, plan.orders.size)
        assertEquals(50, plan.orders[0].discountPercent)  // >$400 = 50%
        assertEquals(20000, plan.orders[0].totalCents)    // $400 × 50% = $200 + $0 ship
    }

    @Test
    fun `threshold pull — moves cards to hit better discount tier`() {
        // USEA: $380 (30% tier), BM: $40
        // Optimizer should pull $20 from BM to USEA to hit $400 (50% tier)
        val items = listOf(
            // 19 cards at $20 each from USEA = $380
            testMultiMatch("USEA Card", Seller.USEA, 2000, 19),
            // 2 cards at $20 each, available from both sellers
            testMultiMatch("Shared Card", bestSeller = Seller.BOOTLEG_MAGE,
                bestPrice = 2000, qty = 2,
                alternatives = listOf(
                    MatchOption(testVariant("Shared Card", Seller.USEA, 2000), Seller.USEA, 2000, true, 0),
                    MatchOption(testVariant("Shared Card", Seller.BOOTLEG_MAGE, 2000), Seller.BOOTLEG_MAGE, 2000, true, 0),
                )),
        )
        val plan = ShoppingOptimizer.optimize(items)
        // Should have pulled shared cards to USEA to reach $400 threshold
        val useaOrder = plan.orders.find { it.seller == Seller.USEA }!!
        assertTrue(useaOrder.subtotalCents >= 40000)
        assertEquals(50, useaOrder.discountPercent)
    }

    @Test
    fun `savings calculated correctly`() {
        val items = listOf(
            testMultiMatch("Card A", Seller.USEA, 5000, 10),  // $50 × 10 = $500
        )
        val plan = ShoppingOptimizer.optimize(items)
        // 50% discount on $500 = $250
        // Without discount: $500 + $10 shipping = $510 (under $60 threshold for 5%)
        // Actually $500 is >$400, so even single-seller gets 50%
        // savingsVsSingleSeller compares against no-discount baseline
        assertTrue(plan.savingsVsSingleSeller >= 0)
    }
}
```

**Step 3: Implement ShoppingOptimizer**

Create `src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt`:

```kotlin
package optimizer

import model.*

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

        // Calculate savings vs worst-case (everything at face price + shipping per seller)
        val naiveTotal = naiveAssignment.map { (seller, items) ->
            buildSellerOrder(seller, items)
        }.sumOf { it.totalCents }
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

        val discountPercent = config.discountTiers
            .firstOrNull { subtotal > it.minCents }?.discountPercent ?: 0
        val afterDiscount = subtotal * (100 - discountPercent) / 100

        val shippingCents = config.shippingTiers
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
            val currentSubtotal = naiveAssignment[seller]?.sumOf {
                it.variant.priceInCents * it.qty
            } ?: 0

            // Find next discount tier above current subtotal
            val nextTier = config.discountTiers
                .filter { it.minCents > currentSubtotal }
                .minByOrNull { it.minCents }
                ?: continue

            val deficit = nextTier.minCents - currentSubtotal + 1 // Need to exceed threshold

            // Find cards from other sellers that are also available from this seller
            val pullable = matches.filter { mm ->
                val currentBest = mm.bestOption ?: return@filter false
                currentBest.seller != seller &&
                    mm.alternatives.any { it.seller == seller }
            }.sortedBy { mm ->
                // Prefer pulling cards with smallest price difference
                val altPrice = mm.alternatives.first { it.seller == seller }.priceCents
                val bestPrice = mm.bestOption!!.priceCents
                (altPrice - bestPrice) * mm.deckEntry.qty
            }

            // Try pulling cards until we hit the threshold
            var pulled = 0
            val candidatePlan = bestPlan.mapValues { it.value.toMutableList() }

            for (mm in pullable) {
                if (pulled >= deficit) break
                val alt = mm.alternatives.first { it.seller == seller }
                val item = OrderItem(alt.variant, mm.deckEntry.qty, alt.isProxy)

                // Remove from current seller
                val currentSeller = mm.bestOption!!.seller
                candidatePlan[currentSeller]?.removeAll {
                    it.variant.nameNormalized == mm.deckEntry.cardName.lowercase()
                }

                // Add to target seller
                candidatePlan.getOrPut(seller) { mutableListOf() }.add(item)
                pulled += alt.priceCents * mm.deckEntry.qty
            }

            val candidateTotal = calculateTotal(candidatePlan)
            if (candidateTotal < bestTotal) {
                bestPlan = candidatePlan
                bestTotal = candidateTotal
            }
        }

        return bestPlan
    }

    private fun calculateTotal(plan: Map<Seller, List<OrderItem>>): Int {
        return plan.map { (seller, items) ->
            buildSellerOrder(seller, items).totalCents
        }.sum()
    }
}
```

**Step 4: Run tests**

Run: `./gradlew allTests`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/optimizer/ShoppingOptimizer.kt \
        src/commonMain/kotlin/optimizer/SellerDiscountConfig.kt \
        src/commonTest/kotlin/optimizer/ShoppingOptimizerTest.kt
git commit -m "feat: add threshold-aware shopping optimizer with bulk discount logic"
```

---

## Task 8: Wire Multi-Catalog into ViewModel

**Files:**
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt`
- Modify: `src/commonMain/kotlin/state/CatalogUseCase.kt`
- Modify: `src/commonMain/kotlin/state/MatchingUseCase.kt`

**Step 1: Update CatalogUseCase to manage multiple sources**

Add a `CatalogSourceRegistry` that holds all catalog sources and coordinates loading:

```kotlin
// Add to CatalogUseCase.kt

class CatalogSourceRegistry(
    private val sources: List<CatalogSource>,
) {
    suspend fun loadAll(log: (String) -> Unit): Map<Seller, Catalog> {
        val result = mutableMapOf<Seller, Catalog>()
        for (source in sources) {
            try {
                log("Loading ${source.seller.displayName} catalog...")
                val variants = source.fetchCatalog(log)
                if (variants.isNotEmpty()) {
                    result[source.seller] = Catalog(variants)
                    log("${source.seller.displayName}: ${variants.size} variants loaded")
                }
            } catch (e: Exception) {
                log("Failed to load ${source.seller.displayName}: ${e.message}")
            }
        }
        return result
    }
}
```

**Step 2: Update MatchingUseCase to use MultiCatalogMatcher**

```kotlin
// In MatchingUseCase.kt, add:

fun matchEntriesMulti(
    entries: List<DeckEntry>,
    catalogs: Map<Seller, Catalog>,
    config: MultiCatalogMatcher.Config,
): List<MultiMatch> {
    return MultiCatalogMatcher.match(entries, catalogs, config)
}
```

**Step 3: Update ViewState to include multi-match results and shopping plan**

In `MviViewModel.kt`, extend `ViewState` (line 518):

```kotlin
// Add to ViewState:
val multiMatches: List<MultiMatch> = emptyList(),
val shoppingPlan: ShoppingPlan? = null,
val availableSellers: List<Seller> = emptyList(),
```

**Step 4: Add new ViewIntents**

```kotlin
// Add to ViewIntent sealed class (line 575):
data class LoadAllCatalogs(val forceRefresh: Boolean = false) : ViewIntent()
data class RunMultiMatch(val proxyFirst: Boolean = true) : ViewIntent()
data class OptimizeShoppingPlan(val multiMatches: List<MultiMatch>) : ViewIntent()
data class OverrideCardSeller(val entryId: String, val option: MatchOption) : ViewIntent()
```

**Step 5: Add intent handlers**

Wire the new intents to their use cases in the `processIntent()` function. The `LoadAllCatalogs` intent triggers parallel catalog loading from all registered sources. `RunMultiMatch` runs the multi-catalog matcher. `OptimizeShoppingPlan` runs the optimizer.

**Step 6: Compile and test**

Run: `./gradlew build && ./gradlew allTests`
Expected: PASS

**Step 7: Commit**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt \
        src/commonMain/kotlin/state/CatalogUseCase.kt \
        src/commonMain/kotlin/state/MatchingUseCase.kt
git commit -m "feat: wire multi-catalog matching and optimizer into MVI state"
```

---

## Task 9: Shopping Plan + Checkout Screen

**Files:**
- Create: `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`
- Modify: `src/desktopMain/kotlin/app/Main.kt` (add navigation route)

**Step 1: Create ShoppingPlanScreen composable**

Create `src/commonMain/kotlin/ui/ShoppingPlanScreen.kt`:

```kotlin
package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.*
import util.Price

@Composable
fun ShoppingPlanScreen(
    plan: ShoppingPlan?,
    onViewCards: (SellerOrder) -> Unit,
    onCopyExport: (SellerOrder) -> Unit,
    onOpenInBrowser: (SellerOrder) -> Unit,
    onBack: () -> Unit,
    isDarkTheme: Boolean,
) {
    if (plan == null) {
        // Loading or no plan yet
        PixelBorderContainer(isDarkTheme = isDarkTheme) {
            Text("Optimizing your order...", color = Theme.textColor(isDarkTheme))
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Header: Total and savings
        PixelBorderContainer(isDarkTheme = isDarkTheme) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Total: ${Price.formatCents(plan.totalPriceCents)} across ${plan.orders.size} seller${if (plan.orders.size != 1) "s" else ""}",
                    color = Theme.textColor(isDarkTheme),
                    style = Theme.heading(isDarkTheme),
                )
                if (plan.savingsVsSingleSeller > 0) {
                    Text(
                        "Saving ${Price.formatCents(plan.savingsVsSingleSeller)} with optimized split!",
                        color = Theme.goldenTreasure,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Per-seller order cards
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(plan.orders) { order ->
                SellerOrderCard(
                    order = order,
                    onViewCards = { onViewCards(order) },
                    onCopyExport = { onCopyExport(order) },
                    onOpenInBrowser = { onOpenInBrowser(order) },
                    isDarkTheme = isDarkTheme,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Back button
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            PixelButton("Back", onClick = onBack, isDarkTheme = isDarkTheme)
        }
    }
}

@Composable
private fun SellerOrderCard(
    order: SellerOrder,
    onViewCards: () -> Unit,
    onCopyExport: () -> Unit,
    onOpenInBrowser: () -> Unit,
    isDarkTheme: Boolean,
) {
    val sellerColor = when (order.seller) {
        Seller.USEA -> Theme.mysticalPurple
        Seller.BOOTLEG_MAGE -> Theme.crystalBlue
        Seller.TCGPLAYER -> Theme.goldenTreasure
    }

    var expanded by remember { mutableStateOf(false) }

    PixelBorderContainer(
        borderColor = sellerColor,
        isDarkTheme = isDarkTheme,
    ) {
        Column(Modifier.padding(16.dp)) {
            // Seller header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "${order.seller.displayName} (${order.items.sumOf { it.qty }} cards)",
                        color = sellerColor,
                        style = Theme.subheading(isDarkTheme),
                    )
                    Text(
                        buildString {
                            append(Price.formatCents(order.subtotalCents))
                            if (order.discountPercent > 0) {
                                append(" → ${Price.formatCents(order.totalCents - order.shippingCents)}")
                                append(" (${order.discountPercent}% bulk discount!)")
                            }
                            if (order.shippingCents > 0) {
                                append(" + ${Price.formatCents(order.shippingCents)} shipping")
                            } else {
                                append(" + Free shipping")
                            }
                        },
                        color = Theme.textColor(isDarkTheme),
                    )
                }
                Text(
                    Price.formatCents(order.totalCents),
                    color = sellerColor,
                    style = Theme.heading(isDarkTheme),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Expandable card list
            PixelButton(
                if (expanded) "▾ Hide cards" else "▸ View cards",
                onClick = { expanded = !expanded },
                isDarkTheme = isDarkTheme,
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                order.items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${item.qty}× ${item.variant.nameOriginal}",
                            color = Theme.textColor(isDarkTheme),
                        )
                        Text(
                            "${Price.formatCents(item.variant.priceInCents * item.qty)}",
                            color = Theme.mutedGrey,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (order.seller) {
                    Seller.USEA -> {
                        PixelButton("Copy CSV", onClick = onCopyExport, isDarkTheme = isDarkTheme)
                        PixelButton("Email Order", onClick = onOpenInBrowser, isDarkTheme = isDarkTheme)
                    }
                    Seller.BOOTLEG_MAGE -> {
                        PixelButton("Open Deck Import ↗", onClick = onOpenInBrowser, isDarkTheme = isDarkTheme)
                    }
                    Seller.TCGPLAYER -> {
                        PixelButton("Open Mass Entry ↗", onClick = onOpenInBrowser, isDarkTheme = isDarkTheme)
                        PixelButton("Copy List", onClick = onCopyExport, isDarkTheme = isDarkTheme)
                    }
                }
            }
        }
    }
}
```

**Step 2: Add navigation route in Main.kt**

Add a new route for the shopping plan screen in the NavHost (around line 719 in Main.kt, replacing or adding alongside the export route):

```kotlin
composable("shopping-plan") {
    ShoppingPlanScreen(
        plan = state.shoppingPlan,
        onViewCards = { /* expand in place */ },
        onCopyExport = { order ->
            val source = catalogSources.first { it.seller == order.seller }
            val text = source.formatForExport(order.items)
            viewModel.processIntent(ViewIntent.CopyToClipboard(text))
        },
        onOpenInBrowser = { order ->
            val source = catalogSources.first { it.seller == order.seller }
            val url = source.checkoutUrl(order.items)
            if (url != null) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            }
        },
        onBack = { navController.popBackStack() },
        isDarkTheme = state.isDarkTheme,
    )
}
```

Update the wizard stepper to include this as Step 5 (or replace the existing export step).

**Step 3: Compile and manual test**

Run: `./gradlew build && ./gradlew run`
Manual test: Import a decklist, run matching, navigate to shopping plan, verify seller cards display correctly.

**Step 4: Commit**

```bash
git add src/commonMain/kotlin/ui/ShoppingPlanScreen.kt \
        src/desktopMain/kotlin/app/Main.kt
git commit -m "feat: add ShoppingPlan+Checkout screen with per-seller action cards"
```

---

## Task 10: Enhanced Results Screen with Seller Badges

**Files:**
- Modify: `src/commonMain/kotlin/ui/ResultsScreen.kt`

**Step 1: Add seller badge to result rows**

In `ResultsScreen.kt`, update the row layout (around line 425) to show a seller badge next to each card's status. When multi-matches are available, show the seller name as a colored badge:

```kotlin
// In the row layout, after status badge:
if (match.seller != null) {
    PixelBadge(
        text = match.seller.displayName.take(4),  // "USEA", "Boot", "TCGP"
        color = when (match.seller) {
            Seller.USEA -> Theme.mysticalPurple
            Seller.BOOTLEG_MAGE -> Theme.crystalBlue
            Seller.TCGPLAYER -> Theme.goldenTreasure
        },
        isDarkTheme = isDarkTheme,
    )
}
```

**Step 2: Add "alternatives" expand for multi-seller options**

When a card has alternatives from other sellers, show a clickable indicator that expands to show all options with their prices:

```kotlin
// After the main row, if alternatives exist:
if (multiMatch.alternatives.size > 1) {
    // Show expandable row with all options
    AnimatedVisibility(visible = isExpanded) {
        Column(Modifier.padding(start = 32.dp)) {
            multiMatch.alternatives.forEach { option ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onOverrideSelection(option) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${option.seller.displayName} · ${option.variant.variantType.displayName} · ${option.variant.setCode}",
                        color = if (option == multiMatch.bestOption) sellerColor else Theme.mutedGrey,
                    )
                    Text(
                        Price.formatCents(option.priceCents),
                        color = Theme.textColor(isDarkTheme),
                    )
                }
            }
        }
    }
}
```

**Step 3: Add filter by seller**

Add seller filter options alongside the existing status filters (Matched/Unmatched/Ambiguous):

```kotlin
// After existing filter chips:
Seller.entries.forEach { seller ->
    PixelBadge(
        text = seller.displayName,
        isSelected = selectedSellerFilter == seller,
        onClick = { selectedSellerFilter = if (selectedSellerFilter == seller) null else seller },
        isDarkTheme = isDarkTheme,
    )
}
```

**Step 4: Compile and manual test**

Run: `./gradlew build && ./gradlew run`
Manual test: Import decklist, match against multiple catalogs, verify seller badges appear, verify filter works.

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/ui/ResultsScreen.kt
git commit -m "feat: add seller badges, alternatives view, and seller filter to ResultsScreen"
```

---

## Task 11: Platform Integration (Desktop + iOS)

**Files:**
- Modify: `src/desktopMain/kotlin/platform/DesktopMviPlatformServices.kt`
- Modify: `src/iosMain/kotlin/platform/IosMviPlatformServices.kt`

**Step 1: Desktop — browser open for checkout URLs**

Add a `openUrl(url: String)` method to `MviPlatformServices` interface and implement for desktop:

```kotlin
// In MviPlatformServices interface (MviViewModel.kt line 622):
suspend fun openUrl(url: String)

// In DesktopMviPlatformServices:
override suspend fun openUrl(url: String) {
    if (java.awt.Desktop.isDesktopSupported()) {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    }
}
```

**Step 2: iOS — URL open via UIApplication**

```kotlin
// In IosMviPlatformServices:
override suspend fun openUrl(url: String) {
    platform.UIKit.UIApplication.sharedApplication.openURL(
        platform.Foundation.NSURL(string = url)
    )
}
```

**Step 3: Test on both platforms**

Run: `./gradlew run` (desktop) and verify browser opens correctly for checkout URLs.

**Step 4: Commit**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt \
        src/desktopMain/kotlin/platform/DesktopMviPlatformServices.kt \
        src/iosMain/kotlin/platform/IosMviPlatformServices.kt
git commit -m "feat: add cross-platform URL opening for checkout links"
```

---

## Task 12: Integration Test — Full Flow

**Files:**
- Create: `src/commonTest/kotlin/integration/ShoppingFlowTest.kt`

**Step 1: Write end-to-end test**

```kotlin
package integration

import catalog.*
import match.*
import model.*
import optimizer.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingFlowTest {

    @Test
    fun `full flow - import deck, match multi-catalog, optimize, get plan`() {
        // 1. Parse deck
        val deckText = """
            4 Lightning Bolt
            4 Counterspell
            2 Tarmogoyf
            1 Black Lotus
        """.trimIndent()
        val entries = DecklistParser.parse(deckText)
        assertEquals(4, entries.size)

        // 2. Build catalogs
        val useaCatalog = Catalog(listOf(
            testVariant("Lightning Bolt", Seller.USEA, 220),
            testVariant("Counterspell", Seller.USEA, 220),
        ))
        val bmCatalog = Catalog(listOf(
            testVariant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180),
            testVariant("Counterspell", Seller.BOOTLEG_MAGE, 250),
            testVariant("Black Lotus", Seller.BOOTLEG_MAGE, 500),
        ))
        val scryfallCatalog = Catalog(listOf(
            testVariant("Tarmogoyf", Seller.TCGPLAYER, 5000),
        ))

        // 3. Multi-match
        val matches = MultiCatalogMatcher.match(
            entries = entries,
            catalogs = mapOf(
                Seller.USEA to useaCatalog,
                Seller.BOOTLEG_MAGE to bmCatalog,
                Seller.TCGPLAYER to scryfallCatalog,
            ),
            config = MultiCatalogMatcher.Config(),
        )

        // Lightning Bolt: cheapest proxy is BM (180)
        assertEquals(Seller.BOOTLEG_MAGE, matches[0].bestOption?.seller)
        // Counterspell: cheapest proxy is USEA (220 vs 250)
        assertEquals(Seller.USEA, matches[1].bestOption?.seller)
        // Tarmogoyf: only available as real card
        assertEquals(Seller.TCGPLAYER, matches[2].bestOption?.seller)
        // Black Lotus: only proxy available from BM
        assertEquals(Seller.BOOTLEG_MAGE, matches[3].bestOption?.seller)

        // 4. Optimize
        val plan = ShoppingOptimizer.optimize(matches)
        assertTrue(plan.orders.isNotEmpty())
        assertTrue(plan.totalPriceCents > 0)
    }
}
```

**Step 2: Run all tests**

Run: `./gradlew allTests`
Expected: PASS

**Step 3: Run static analysis**

Run: `./gradlew detekt`
Expected: No violations

**Step 4: Manual smoke test**

Run: `./gradlew run`
Import a real decklist, load catalogs, verify the full flow works end-to-end.

**Step 5: Commit**

```bash
git add src/commonTest/kotlin/integration/ShoppingFlowTest.kt
git commit -m "test: add end-to-end integration test for multi-seller shopping flow"
```

---

## Dependency Graph

```
Task 1 (Models + Interface)
   ├──→ Task 2 (Database Schema)
   │       └──→ Task 3 (USEA Adapter)
   │       └──→ Task 4 (Bootleg Mage Adapter)
   │       └──→ Task 5 (Scryfall Pricing)
   │
   └──→ Task 6 (Multi-Catalog Matcher)  [depends on 1]
           └──→ Task 7 (Shopping Optimizer)  [depends on 6]
                   └──→ Task 8 (Wire into ViewModel)  [depends on 2,3,4,5,6,7]
                           ├──→ Task 9 (Shopping Plan Screen)  [depends on 8]
                           ├──→ Task 10 (Enhanced Results Screen)  [depends on 8]
                           ├──→ Task 11 (Platform Integration)  [depends on 8]
                           └──→ Task 12 (Integration Test)  [depends on all]
```

**Parallelizable work streams:**
- Tasks 3, 4, 5 can run in parallel (independent catalog adapters)
- Tasks 9, 10, 11 can run in parallel (independent UI work)

---

## Notes for Implementer

1. **Bootleg Mage API access is uncertain.** Their WooCommerce REST API may require auth or be disabled. Build the adapter with a fallback to HTML scraping using Ksoup. Test against the real site early in Task 4.

2. **Database migration.** SQLDelight doesn't have automatic migrations in dev. You may need to clear the database file on first run after schema changes. The default values (`seller = 'USEA'`) ensure any cached data still loads.

3. **Scryfall rate limiting.** The bulk `/cards/collection` endpoint accepts 75 cards per request. For a 100-card decklist, that's 2 requests with 75ms delay — negligible. But if fetching card images too, those are individual requests at 75ms each.

4. **Optimizer complexity.** With 2-3 sellers, brute-force threshold checking is fast. If seller count grows beyond 5-6, consider a more efficient algorithm. For now, the simple approach is fine.

5. **Testing against real sites.** Write unit tests with mock data for CI. Manual integration tests against real seller sites for development. Don't hit real APIs in automated tests.

6. **Keep the existing Export screen working.** The ShoppingPlan screen supplements but doesn't immediately replace it. Users who only use USEA should see no change in their workflow until they enable multi-seller mode.
