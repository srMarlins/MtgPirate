# Streaming Search Loading UI — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace bulk catalog loading with a hybrid per-card search that streams results progressively, showing per-supplier progress and cards appearing as they're found.

**Architecture:** New `DeckSearchUseCase` orchestrates parallel supplier searches via `channelFlow`. Scryfall batches (1 request), Bootleg Mage paginates full catalog, ManaPool bulk loads — all concurrently. Results filter to deck entries, store per-seller in DB, and re-run multi-matching after each supplier completes. A `SearchProgressPanel` composable replaces `AnimatedLoadingDots` with per-supplier status and a progress bar.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.coroutines (channelFlow, Mutex), SQLDelight, Ktor HTTP client.

**Design Doc:** `docs/plans/2026-02-27-streaming-search-loading-design.md`

---

### Task 1: Add Search Progress State Types

**Files:**
- Create: `src/commonMain/kotlin/state/SearchProgress.kt`
- Test: `src/commonTest/kotlin/state/SearchProgressTest.kt`

**Step 1: Write the test**

```kotlin
// src/commonTest/kotlin/state/SearchProgressTest.kt
package state

import model.Seller
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchProgressTest {

    @Test
    fun isSearching_trueWhenNotComplete() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 10,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 10),
                Seller.MANAPOOL to SellerSearchStatus(Seller.MANAPOOL, SearchState.SEARCHING),
            ),
            multiMatches = emptyList(),
            isComplete = false,
        )
        assertTrue(progress.isSearching)
        assertFalse(progress.isComplete)
    }

    @Test
    fun isSearching_falseWhenComplete() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 58,
            sellerStatuses = mapOf(
                Seller.TCGPLAYER to SellerSearchStatus(Seller.TCGPLAYER, SearchState.DONE, 58),
            ),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertFalse(progress.isSearching)
    }

    @Test
    fun progressFraction_calculatesCorrectly() {
        val progress = SearchProgress(
            totalCards = 60,
            cardsWithResults = 30,
            sellerStatuses = emptyMap(),
            multiMatches = emptyList(),
            isComplete = false,
        )
        assertEquals(0.5f, progress.progressFraction)
    }

    @Test
    fun progressFraction_zeroTotalCards() {
        val progress = SearchProgress(
            totalCards = 0,
            cardsWithResults = 0,
            sellerStatuses = emptyMap(),
            multiMatches = emptyList(),
            isComplete = true,
        )
        assertEquals(1f, progress.progressFraction)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew desktopTest --tests "state.SearchProgressTest" --info`
Expected: FAIL — classes don't exist yet

**Step 3: Write the implementation**

```kotlin
// src/commonMain/kotlin/state/SearchProgress.kt
package state

import model.MultiMatch
import model.Seller

/** State of a single supplier's search. */
enum class SearchState { PENDING, SEARCHING, DONE, ERROR }

/** Progress for a single supplier. */
data class SellerSearchStatus(
    val seller: Seller,
    val state: SearchState,
    val cardsFound: Int = 0,
    val message: String? = null,
)

/** Overall search progress emitted by DeckSearchUseCase. */
data class SearchProgress(
    val totalCards: Int,
    val cardsWithResults: Int,
    val sellerStatuses: Map<Seller, SellerSearchStatus>,
    val multiMatches: List<MultiMatch>,
    val isComplete: Boolean,
) {
    val isSearching: Boolean get() = !isComplete
    val progressFraction: Float
        get() = if (totalCards == 0) 1f else cardsWithResults.toFloat() / totalCards
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew desktopTest --tests "state.SearchProgressTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/state/SearchProgress.kt src/commonTest/kotlin/state/SearchProgressTest.kt
git commit -m "feat: add SearchProgress state types for streaming search"
```

---

### Task 2: Add Seller Cache Metadata to Database

**Files:**
- Create: `src/commonMain/sqldelight/database/SellerCache.sq`
- Modify: `src/commonMain/kotlin/database/Database.kt`
- Modify: `src/commonMain/kotlin/database/CatalogStore.kt`
- Test: `src/commonTest/kotlin/database/SellerCacheTtlTest.kt`

**Step 1: Write the test**

```kotlin
// src/commonTest/kotlin/database/SellerCacheTtlTest.kt
package database

import model.Seller
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SellerCacheTtlTest {

    @Test
    fun isCacheFresh_withinTtl() {
        val nowMillis = System.currentTimeMillis()
        val fetchedAt = nowMillis - 1.hours.inWholeMilliseconds // 1 hour ago
        assertTrue(CacheUtils.isFresh(fetchedAt, ttl = 24.hours, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_expiredTtl() {
        val nowMillis = System.currentTimeMillis()
        val fetchedAt = nowMillis - 25.hours.inWholeMilliseconds // 25 hours ago
        assertFalse(CacheUtils.isFresh(fetchedAt, ttl = 24.hours, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_proxySeller7DayTtl() {
        val nowMillis = System.currentTimeMillis()
        val fetchedAt = nowMillis - 5.days.inWholeMilliseconds // 5 days ago
        assertTrue(CacheUtils.isFresh(fetchedAt, ttl = 7.days, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_proxySeller7DayExpired() {
        val nowMillis = System.currentTimeMillis()
        val fetchedAt = nowMillis - 8.days.inWholeMilliseconds // 8 days ago
        assertFalse(CacheUtils.isFresh(fetchedAt, ttl = 7.days, nowMillis = nowMillis))
    }

    @Test
    fun ttlForSeller_proxyIs7Days() {
        assertTrue(CacheUtils.ttlForSeller(Seller.BOOTLEG_MAGE) == 7.days)
        assertTrue(CacheUtils.ttlForSeller(Seller.USEA) == 7.days)
    }

    @Test
    fun ttlForSeller_realIs24Hours() {
        assertTrue(CacheUtils.ttlForSeller(Seller.MANAPOOL) == 24.hours)
        assertTrue(CacheUtils.ttlForSeller(Seller.TCGPLAYER) == 24.hours)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew desktopTest --tests "database.SellerCacheTtlTest" --info`
Expected: FAIL — CacheUtils doesn't exist

**Step 3: Create SQLDelight schema**

```sql
-- src/commonMain/sqldelight/database/SellerCache.sq

CREATE TABLE SellerCacheEntity (
    seller TEXT NOT NULL PRIMARY KEY,
    lastFetchedAtMillis INTEGER NOT NULL
);

upsertCache:
INSERT OR REPLACE INTO SellerCacheEntity (seller, lastFetchedAtMillis) VALUES (?, ?);

getCache:
SELECT lastFetchedAtMillis FROM SellerCacheEntity WHERE seller = ?;

deleteCache:
DELETE FROM SellerCacheEntity WHERE seller = ?;

deleteAllCache:
DELETE FROM SellerCacheEntity;
```

**Step 4: Add Database methods**

Add to `src/commonMain/kotlin/database/Database.kt` after `replaceCatalogForSellerTransaction`:

```kotlin
fun upsertSellerCache(seller: String, lastFetchedAtMillis: Long) {
    db.sellerCacheQueries.upsertCache(seller, lastFetchedAtMillis)
}

fun getSellerCacheTimestamp(seller: String): Long? {
    return db.sellerCacheQueries.getCache(seller).executeAsOneOrNull()
}
```

**Step 5: Add CacheUtils and CatalogStore methods**

Add to `src/commonMain/kotlin/database/CatalogStore.kt`:

```kotlin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object CacheUtils {
    fun ttlForSeller(seller: Seller): Duration =
        if (seller.isProxy) 7.days else 24.hours

    fun isFresh(
        fetchedAtMillis: Long,
        ttl: Duration,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = (nowMillis - fetchedAtMillis) < ttl.inWholeMilliseconds
}
```

Add methods to `CatalogStore`:

```kotlin
fun isSellerCacheFresh(seller: Seller): Boolean {
    val fetchedAt = database.getSellerCacheTimestamp(seller.name) ?: return false
    return CacheUtils.isFresh(fetchedAt, CacheUtils.ttlForSeller(seller))
}

fun markSellerFetched(seller: Seller) {
    database.upsertSellerCache(seller.name, System.currentTimeMillis())
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew desktopTest --tests "database.SellerCacheTtlTest" --info`
Expected: PASS

**Step 7: Commit**

```bash
git add src/commonMain/sqldelight/database/SellerCache.sq \
        src/commonMain/kotlin/database/Database.kt \
        src/commonMain/kotlin/database/CatalogStore.kt \
        src/commonTest/kotlin/database/SellerCacheTtlTest.kt
git commit -m "feat: add seller cache metadata table with TTL (7d proxy, 24h real)"
```

---

### Task 3: Create DeckSearchUseCase

**Files:**
- Create: `src/commonMain/kotlin/state/DeckSearchUseCase.kt`
- Test: `src/commonTest/kotlin/state/DeckSearchUseCaseTest.kt`

**Step 1: Write the test**

```kotlin
// src/commonTest/kotlin/state/DeckSearchUseCaseTest.kt
package state

import catalog.CatalogSource
import database.CatalogStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.MultiMatch
import model.OrderItem
import model.Section
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckSearchUseCaseTest {

    // -- Fake CatalogSource for testing --
    private class FakeCatalogSource(
        override val seller: Seller,
        private val catalogVariants: List<CardVariant> = emptyList(),
        private val searchResults: Map<String, List<CardVariant>> = emptyMap(),
    ) : CatalogSource {
        override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
            log("Fake ${seller.displayName}: loading ${catalogVariants.size} variants")
            return catalogVariants
        }

        override suspend fun search(cardName: String): List<CardVariant> =
            searchResults[cardName] ?: emptyList()

        override fun checkoutUrl(items: List<OrderItem>): String? = null
        override fun formatForExport(items: List<OrderItem>): String = ""
    }

    // -- Fake CatalogStore for testing --
    private class FakeCatalogStore : CatalogStore {
        val storedVariants = mutableMapOf<Seller, List<CardVariant>>()
        val cacheTimestamps = mutableMapOf<Seller, Long>()
        private var allVariants: List<CardVariant> = emptyList()

        override suspend fun replaceCatalogForSeller(seller: Seller, variants: List<CardVariant>) {
            storedVariants[seller] = variants
            allVariants = storedVariants.values.flatten()
        }

        override fun isSellerCacheFresh(seller: Seller): Boolean {
            val ts = cacheTimestamps[seller] ?: return false
            return (System.currentTimeMillis() - ts) < 1000 * 60 * 60 // 1 hour for test
        }

        override fun markSellerFetched(seller: Seller) {
            cacheTimestamps[seller] = System.currentTimeMillis()
        }

        override fun getAllVariants(): List<CardVariant> = allVariants
    }

    private fun entry(name: String, qty: Int = 1) = DeckEntry(
        id = name.lowercase().replace(" ", "-"),
        originalLine = "$qty $name",
        qty = qty,
        cardName = name,
        section = Section.MAIN,
        include = true,
    )

    private fun variant(
        name: String,
        seller: Seller,
        priceCents: Int = 220,
    ) = CardVariant(
        nameOriginal = name,
        nameNormalized = NameNormalizer.normalize(name),
        setCode = "TST",
        sku = "${seller.name}-${name.replace(" ", "")}",
        variantType = VariantType.REGULAR,
        priceInCents = priceCents,
        seller = seller,
    )

    @Test
    fun searchDeck_emitsProgressAndCompletes() = runTest {
        val bmSource = FakeCatalogSource(
            seller = Seller.BOOTLEG_MAGE,
            catalogVariants = listOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180)),
        )
        val store = FakeCatalogStore()

        val useCase = DeckSearchUseCase(
            sources = listOf(bmSource),
            catalogStore = store,
        )

        val entries = listOf(entry("Lightning Bolt", 4))
        val config = MultiCatalogMatcher.Config()

        val emissions = useCase.searchDeck(entries, config) { _, _ -> }.toList()

        // Should have at least: initial PENDING + SEARCHING update + DONE update + final complete
        assertTrue(emissions.size >= 2, "Expected at least 2 emissions, got ${emissions.size}")

        // Last emission should be complete
        val last = emissions.last()
        assertTrue(last.isComplete)
        assertEquals(1, last.totalCards)

        // BM should be DONE
        val bmStatus = last.sellerStatuses[Seller.BOOTLEG_MAGE]
        assertEquals(SearchState.DONE, bmStatus?.state)
    }

    @Test
    fun searchDeck_filtersToMatchingCards() = runTest {
        val bmSource = FakeCatalogSource(
            seller = Seller.BOOTLEG_MAGE,
            catalogVariants = listOf(
                variant("Lightning Bolt", Seller.BOOTLEG_MAGE),
                variant("Counterspell", Seller.BOOTLEG_MAGE),
                variant("Tarmogoyf", Seller.BOOTLEG_MAGE),
            ),
        )
        val store = FakeCatalogStore()

        val useCase = DeckSearchUseCase(
            sources = listOf(bmSource),
            catalogStore = store,
        )

        // Only searching for Lightning Bolt
        val entries = listOf(entry("Lightning Bolt"))
        val emissions = useCase.searchDeck(entries, MultiCatalogMatcher.Config()) { _, _ -> }.toList()

        // Store should only contain the matching card, not all 3
        val storedBm = store.storedVariants[Seller.BOOTLEG_MAGE] ?: emptyList()
        assertEquals(1, storedBm.size)
        assertEquals("Lightning Bolt", storedBm[0].nameOriginal)
    }

    @Test
    fun searchDeck_supplierFailureDoesNotBlockOthers() = runTest {
        val failingSource = FakeCatalogSource(
            seller = Seller.MANAPOOL,
            catalogVariants = emptyList(), // Will simulate failure
        )
        val bmSource = FakeCatalogSource(
            seller = Seller.BOOTLEG_MAGE,
            catalogVariants = listOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE)),
        )
        val store = FakeCatalogStore()

        val useCase = DeckSearchUseCase(
            sources = listOf(failingSource, bmSource),
            catalogStore = store,
        )

        val entries = listOf(entry("Lightning Bolt"))
        val emissions = useCase.searchDeck(entries, MultiCatalogMatcher.Config()) { _, _ -> }.toList()

        val last = emissions.last()
        assertTrue(last.isComplete)

        // BM should succeed
        assertEquals(SearchState.DONE, last.sellerStatuses[Seller.BOOTLEG_MAGE]?.state)
    }

    @Test
    fun searchDeck_emptyDeckReturnsImmediately() = runTest {
        val store = FakeCatalogStore()
        val useCase = DeckSearchUseCase(sources = emptyList(), catalogStore = store)

        val emissions = useCase.searchDeck(emptyList(), MultiCatalogMatcher.Config()) { _, _ -> }.toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0].isComplete)
        assertEquals(0, emissions[0].totalCards)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew desktopTest --tests "state.DeckSearchUseCaseTest" --info`
Expected: FAIL — DeckSearchUseCase doesn't exist

**Step 3: Write the implementation**

Note: `CatalogStore` needs a small interface extraction for testability. Add a `CatalogStoreInterface` or make `FakeCatalogStore` implement the same contract. For simplicity, we'll make `DeckSearchUseCase` depend on the concrete class and use constructor injection for the parts we need.

```kotlin
// src/commonMain/kotlin/state/DeckSearchUseCase.kt
package state

import catalog.CatalogSource
import database.CatalogStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import match.MultiCatalogMatcher
import match.NameNormalizer
import model.Catalog
import model.DeckEntry
import model.MultiMatch
import model.Seller

/**
 * Orchestrates parallel searches across all catalog suppliers for a given deck.
 *
 * Instead of loading entire catalogs, this use case:
 * 1. Checks cache freshness per seller (skip if within TTL)
 * 2. Loads catalogs in parallel (Scryfall batch, BM paginated, ManaPool bulk)
 * 3. Filters results to only cards in the deck
 * 4. Re-runs multi-matching after each supplier completes
 * 5. Emits [SearchProgress] via a [Flow] for progressive UI updates
 */
class DeckSearchUseCase(
    private val sources: List<CatalogSource>,
    private val catalogStore: CatalogStore,
) {
    /**
     * Search all suppliers for the given deck entries.
     * Emits [SearchProgress] updates as each supplier completes.
     */
    fun searchDeck(
        entries: List<DeckEntry>,
        matchConfig: MultiCatalogMatcher.Config,
        log: (String, String) -> Unit,
    ): Flow<SearchProgress> = channelFlow {
        val includedEntries = entries.filter { it.include }
        val totalCards = includedEntries.size

        if (includedEntries.isEmpty()) {
            send(SearchProgress(0, 0, emptyMap(), emptyList(), isComplete = true))
            return@channelFlow
        }

        // Build normalized name set for filtering bulk catalogs
        val deckNamesNormalized = includedEntries
            .map { NameNormalizer.normalize(it.cardName) }
            .toSet()

        val sellerStatuses = mutableMapOf<Seller, SellerSearchStatus>()
        val mutex = Mutex()

        // Initialize all sellers as PENDING
        sources.forEach { source ->
            sellerStatuses[source.seller] = SellerSearchStatus(source.seller, SearchState.PENDING)
        }
        send(buildProgress(totalCards, sellerStatuses, emptyList(), false))

        // Launch all suppliers in parallel
        coroutineScope {
            for (source in sources) {
                launch {
                    try {
                        // Update status to SEARCHING
                        mutex.withLock {
                            sellerStatuses[source.seller] = SellerSearchStatus(
                                source.seller, SearchState.SEARCHING, message = "Loading..."
                            )
                        }
                        send(buildProgress(totalCards, sellerStatuses, emptyList(), false))

                        // Check cache freshness
                        if (catalogStore.isSellerCacheFresh(source.seller)) {
                            log("${source.seller.displayName}: using cached data", "INFO")
                            mutex.withLock {
                                sellerStatuses[source.seller] = SellerSearchStatus(
                                    source.seller, SearchState.DONE, message = "cached"
                                )
                            }
                        } else {
                            // Fetch catalog from supplier
                            log("${source.seller.displayName}: fetching...", "INFO")
                            val allVariants = source.fetchCatalog { msg ->
                                log(msg, "INFO")
                            }

                            // Filter to only cards in the deck
                            val matchingVariants = allVariants.filter { variant ->
                                variant.nameNormalized in deckNamesNormalized
                            }

                            log(
                                "${source.seller.displayName}: ${matchingVariants.size}/${allVariants.size} " +
                                    "variants match deck entries",
                                "INFO"
                            )

                            // Store filtered results
                            catalogStore.replaceCatalogForSeller(source.seller, matchingVariants)
                            catalogStore.markSellerFetched(source.seller)

                            mutex.withLock {
                                sellerStatuses[source.seller] = SellerSearchStatus(
                                    source.seller, SearchState.DONE,
                                    cardsFound = matchingVariants.size,
                                )
                            }
                        }

                        // Re-run multi-match with all available data
                        val currentMultiMatches = runMultiMatch(includedEntries, matchConfig)
                        val cardsWithResults = currentMultiMatches.count { it.bestOption != null }

                        send(buildProgress(totalCards, sellerStatuses, currentMultiMatches, false))

                    } catch (e: Exception) {
                        log("${source.seller.displayName}: failed — ${e.message}", "ERROR")
                        mutex.withLock {
                            sellerStatuses[source.seller] = SellerSearchStatus(
                                source.seller, SearchState.ERROR, message = e.message
                            )
                        }
                        // Re-run match with whatever we have so far
                        val currentMultiMatches = runMultiMatch(includedEntries, matchConfig)
                        send(buildProgress(totalCards, sellerStatuses, currentMultiMatches, false))
                    }
                }
            }
        }

        // Final emission after all suppliers complete
        val finalMatches = runMultiMatch(includedEntries, matchConfig)
        val finalCardsWithResults = finalMatches.count { it.bestOption != null }
        send(
            SearchProgress(
                totalCards = totalCards,
                cardsWithResults = finalCardsWithResults,
                sellerStatuses = sellerStatuses.toMap(),
                multiMatches = finalMatches,
                isComplete = true,
            )
        )
    }

    private fun runMultiMatch(
        entries: List<DeckEntry>,
        config: MultiCatalogMatcher.Config,
    ): List<MultiMatch> {
        val allVariants = catalogStore.getAllVariants()
        if (allVariants.isEmpty()) return emptyList()

        val perSeller = allVariants
            .groupBy { it.seller }
            .mapValues { (_, variants) -> Catalog(variants) }

        return MultiCatalogMatcher.match(entries, perSeller, config)
    }

    private fun buildProgress(
        totalCards: Int,
        sellerStatuses: Map<Seller, SellerSearchStatus>,
        multiMatches: List<MultiMatch>,
        isComplete: Boolean,
    ): SearchProgress {
        val cardsWithResults = multiMatches.count { it.bestOption != null }
        return SearchProgress(
            totalCards = totalCards,
            cardsWithResults = cardsWithResults,
            sellerStatuses = sellerStatuses.toMap(),
            multiMatches = multiMatches,
            isComplete = isComplete,
        )
    }
}
```

**Step 4: Adjust CatalogStore to support DeckSearchUseCase**

Add `getAllVariants()` to `CatalogStore` (sync read for use inside `channelFlow`):

```kotlin
// In CatalogStore.kt, add:
fun getAllVariants(): List<CardVariant> {
    return database.getAllVariants()
}
```

Add to `Database.kt`:

```kotlin
// In Database.kt, add:
fun getAllVariants(): List<CardVariant> {
    return db.cardVariantQueries.selectAll().executeAsList().map { it.toDomain() }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew desktopTest --tests "state.DeckSearchUseCaseTest" --info`
Expected: PASS

Note: The `FakeCatalogStore` in the test implements the interface directly. If tests fail due to interface mismatch, extract an interface from `CatalogStore` and have both the real and fake implementations conform.

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/state/DeckSearchUseCase.kt \
        src/commonMain/kotlin/database/CatalogStore.kt \
        src/commonMain/kotlin/database/Database.kt \
        src/commonTest/kotlin/state/DeckSearchUseCaseTest.kt
git commit -m "feat: add DeckSearchUseCase with channelFlow-based parallel search"
```

---

### Task 4: Update MviViewModel for Streaming Search

**Files:**
- Modify: `src/commonMain/kotlin/state/MviViewModel.kt`

**Step 1: Add SearchProgress to LocalUiState**

In `LocalUiState` (around line 740-762), add:

```kotlin
val searchProgress: SearchProgress? = null,
```

**Step 2: Add SearchProgress to ViewState**

In `ViewState` (around line 703-735), add:

```kotlin
val searchProgress: SearchProgress? = null,
```

**Step 3: Wire searchProgress into the combine block**

In the `combine` block in `init` (around line 91-130), map `localState.searchProgress` into the ViewState:

```kotlin
searchProgress = localState.searchProgress,
```

Also update the `loadingMultiCatalogs` derivation so it stays backward-compatible:

```kotlin
loadingMultiCatalogs = localState.searchProgress?.isSearching ?: localState.loadingMultiCatalogs,
```

**Step 4: Create DeckSearchUseCase in ViewModel**

Add the use case as a field (after `preferencesUseCase` around line 58):

```kotlin
private val deckSearchUseCase = DeckSearchUseCase(
    sources = catalogUseCase.sourceRegistry.allSources,
    catalogStore = catalogStore,
)
```

**Step 5: Add SearchDeck intent**

In `ViewIntent` sealed class (around line 767), add:

```kotlin
data object SearchDeck : ViewIntent()
```

Add to the `when` block in `processIntent` async section:

```kotlin
ViewIntent.SearchDeck -> searchDeck()
```

**Step 6: Write searchDeck handler**

Add new handler after `loadAllCatalogs()` (around line 537):

```kotlin
private suspend fun searchDeck() {
    val entries = _localState.value.deckEntries
    if (entries.isEmpty()) {
        log("No deck entries to search", "WARNING")
        return
    }

    val preferences = _viewState.value.preferences
    val config = MultiCatalogMatcher.Config(
        variantPriority = preferences.variantPriority,
        setPriority = preferences.setPriority,
        fuzzyEnabled = preferences.fuzzyEnabled,
    )

    // Collect search progress flow
    deckSearchUseCase.searchDeck(entries, config) { msg, level -> log(msg, level) }
        .collect { progress ->
            _localState.update { state ->
                state.copy(
                    searchProgress = progress,
                    multiMatches = progress.multiMatches,
                    availableSellers = progress.sellerStatuses
                        .filter { it.value.state == SearchState.DONE }
                        .map { it.key },
                )
            }

            // When complete, also run single-seller matching for backward compat
            if (progress.isComplete) {
                val catalog = withContext(Dispatchers.IO) { database.observeCatalog().first() }
                if (catalog.variants.isNotEmpty() && entries.isNotEmpty()) {
                    runMatchInternal(entries, catalog, _viewState.value.preferences)
                }
                _localState.update { it.copy(catalogsLoadedThisSession = true) }
            }
        }
}
```

**Step 7: Update wizard flow to use searchDeck**

In `wizardPreferencesToResults()` (around line 652-663), replace `loadAllCatalogs()` with `searchDeck()`:

```kotlin
private suspend fun wizardPreferencesToResults() {
    completeWizardStep(2)
    parseDeck()

    if (!_localState.value.catalogsLoadedThisSession) {
        searchDeck()
    } else {
        log("Catalogs already loaded this session, running match only", "INFO")
        runAllMatching()
    }
}
```

**Step 8: Run build to verify compilation**

Run: `./gradlew build`
Expected: PASS

**Step 9: Commit**

```bash
git add src/commonMain/kotlin/state/MviViewModel.kt
git commit -m "feat: wire DeckSearchUseCase into MviViewModel with SearchDeck intent"
```

---

### Task 5: Create SearchProgressPanel UI Component

**Files:**
- Modify: `src/commonMain/kotlin/ui/PixelComponents.kt`

**Step 1: Add PixelProgressBar composable**

Add after `AnimatedLoadingDots` (around line 1593) in `PixelComponents.kt`:

```kotlin
/**
 * Pixel-art styled progress bar.
 * @param progress 0f..1f fraction complete
 */
@Composable
fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = MaterialTheme.colors.surface,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(backgroundColor, PixelShape(cornerSize = 2.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.3f), PixelShape(cornerSize = 2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(color, PixelShape(cornerSize = 2.dp))
        )
    }
}
```

**Step 2: Add SearchProgressPanel composable**

Add after `PixelProgressBar`:

```kotlin
/**
 * Panel showing per-supplier search progress with an overall progress bar.
 * Replaces [AnimatedLoadingDots] during streaming search.
 */
@Composable
fun SearchProgressPanel(
    searchProgress: SearchProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colors.surface,
                PixelShape(cornerSize = 4.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                PixelShape(cornerSize = 4.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header with progress text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (searchProgress.isComplete) {
                        "Found ${searchProgress.cardsWithResults}/${searchProgress.totalCards} cards"
                    } else {
                        "Searching ${searchProgress.cardsWithResults}/${searchProgress.totalCards} cards..."
                    },
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                )
                if (searchProgress.isSearching) {
                    AnimatedLoadingDots()
                }
            }

            Text(
                text = "${(searchProgress.progressFraction * 100).toInt()}%",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }

        // Progress bar
        PixelProgressBar(
            progress = searchProgress.progressFraction,
            color = if (searchProgress.isComplete) PixelGreen else MaterialTheme.colors.primary,
        )

        // Per-supplier status rows
        searchProgress.sellerStatuses.forEach { (_, status) ->
            SellerStatusRow(status)
        }
    }
}

@Composable
private fun SellerStatusRow(status: SellerSearchStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator
            val indicatorColor = when (status.state) {
                SearchState.PENDING -> PixelGrey
                SearchState.SEARCHING -> PixelOrange
                SearchState.DONE -> PixelGreen
                SearchState.ERROR -> PixelRed
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(indicatorColor, PixelShape(cornerSize = 2.dp))
            )

            Text(
                text = status.seller.displayName,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.cardsFound > 0) {
                Text(
                    text = "${status.cardsFound} cards",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }

            Text(
                text = when (status.state) {
                    SearchState.PENDING -> "pending"
                    SearchState.SEARCHING -> status.message ?: "searching..."
                    SearchState.DONE -> status.message ?: "done"
                    SearchState.ERROR -> status.message ?: "error"
                },
                style = MaterialTheme.typography.caption,
                color = when (status.state) {
                    SearchState.ERROR -> PixelRed
                    SearchState.DONE -> PixelGreen
                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                },
            )
        }
    }
}
```

**Step 3: Add necessary imports to PixelComponents.kt**

Add at the top of the file:

```kotlin
import state.SearchProgress
import state.SearchState
import state.SellerSearchStatus
```

**Step 4: Run build to verify compilation**

Run: `./gradlew build`
Expected: PASS

**Step 5: Commit**

```bash
git add src/commonMain/kotlin/ui/PixelComponents.kt
git commit -m "feat: add SearchProgressPanel and PixelProgressBar composables"
```

---

### Task 6: Update ResultsScreen for Streaming

**Files:**
- Modify: `src/commonMain/kotlin/ui/ResultsScreen.kt`

**Step 1: Add searchProgress parameter**

Update the `ResultsScreen` signature (around line 56-71) to accept `SearchProgress`:

```kotlin
@Composable
fun ResultsScreen(
    matches: List<DeckEntryMatch>,
    multiMatches: List<MultiMatch> = emptyList(),
    availableSellers: List<Seller> = emptyList(),
    onResolve: (Int) -> Unit,
    onShowAllCandidates: (Int) -> Unit,
    onOverrideSeller: (Int, Seller) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onEnrichVariant: ((CardVariant) -> Unit)? = null,
    isLoading: Boolean = false,
    searchProgress: SearchProgress? = null,  // NEW
    matchedCount: Int = 0,
    unmatchedCount: Int = 0,
    ambiguousCount: Int = 0,
    totalPriceCents: Int = 0,
)
```

**Step 2: Replace AnimatedLoadingDots with SearchProgressPanel**

Replace the loading section (around lines 351-360):

```kotlin
// OLD:
if (isLoading) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedLoadingDots()
    }
    Spacer(Modifier.height(16.dp))
}

// NEW:
if (searchProgress != null && searchProgress.isSearching) {
    SearchProgressPanel(searchProgress = searchProgress)
    Spacer(Modifier.height(16.dp))
} else if (isLoading) {
    // Fallback for non-search loading (e.g., matching only)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedLoadingDots()
    }
    Spacer(Modifier.height(16.dp))
}
```

**Step 3: Show partial results during streaming**

Change the condition that hides results (around line 362):

```kotlin
// OLD:
if (!isLoading) {
    // Table Header and results list
}

// NEW: Show results when we have any data, even during loading
val hasResults = multiMatches.isNotEmpty() || matches.isNotEmpty()
if (!isLoading || hasResults) {
    // Table Header and results list
}
```

**Step 4: Add import**

Add at the top:

```kotlin
import state.SearchProgress
```

**Step 5: Run build to verify compilation**

Run: `./gradlew build`
Expected: PASS

**Step 6: Commit**

```bash
git add src/commonMain/kotlin/ui/ResultsScreen.kt
git commit -m "feat: replace AnimatedLoadingDots with SearchProgressPanel in ResultsScreen"
```

---

### Task 7: Wire Up in Desktop Main.kt

**Files:**
- Modify: `src/desktopMain/kotlin/app/Main.kt`

**Step 1: Pass searchProgress to ResultsScreen**

Find the `ResultsScreen` call (around line 690-719) and add the `searchProgress` parameter:

```kotlin
ResultsScreen(
    matches = state.matches,
    multiMatches = state.multiMatches,
    availableSellers = state.availableSellers,
    onResolve = { idx ->
        viewModel.processIntent(ViewIntent.OpenResolve(idx))
        navController.navigate("resolve") { launchSingleTop = true }
    },
    onShowAllCandidates = { idx ->
        viewModel.processIntent(ViewIntent.OpenResolve(idx))
        navController.navigate("resolve") { launchSingleTop = true }
    },
    onOverrideSeller = { index, seller ->
        viewModel.processIntent(ViewIntent.OverrideCardSeller(index, seller))
    },
    onClose = { navController.navigateUp() },
    onExport = {
        viewModel.processIntent(ViewIntent.WizardResultsToExport)
        navController.navigate("export") { launchSingleTop = true }
    },
    isLoading = state.isMatching || state.loadingMultiCatalogs,
    searchProgress = state.searchProgress,  // NEW
    matchedCount = state.matchedCount,
    unmatchedCount = state.unmatchedCount,
    ambiguousCount = state.ambiguousCount,
    totalPriceCents = state.totalPriceCents
)
```

**Step 2: Update Init intent to use SearchDeck**

In `MviViewModel.kt`, update `initHandler()` (around line 207-223) to use `searchDeck()` instead of `loadAllCatalogs()`:

```kotlin
private suspend fun initHandler() {
    withContext(Dispatchers.IO) {
        log("Initializing MVI ViewModel...", "INFO")
        try {
            val variantCount = catalogUseCase.getVariantCount()
            if (variantCount == 0L) {
                log("Catalog is empty, searching from all sources...", "INFO")
                searchDeck()
            } else {
                log("Catalog already loaded: $variantCount variants", "INFO")
            }
        } catch (e: Exception) {
            log("Failed to check catalog: ${e.message}", "ERROR")
            _viewEffects.emit(ViewEffect.ShowError("Failed to initialize catalog"))
        }
    }
}
```

**Step 3: Run full build**

Run: `./gradlew build`
Expected: PASS

**Step 4: Manual test**

Run: `./gradlew run`

Test workflow:
1. Launch app → paste a deck list
2. Click through wizard to Preferences → Results
3. Observe: SearchProgressPanel appears with per-supplier status
4. Observe: Cards stream into results as each supplier completes
5. Observe: Progress bar fills, then collapses to summary

**Step 5: Commit**

```bash
git add src/desktopMain/kotlin/app/Main.kt src/commonMain/kotlin/state/MviViewModel.kt
git commit -m "feat: wire streaming search into desktop UI and wizard flow"
```

---

### Task 8: Run Full Test Suite and Fix Issues

**Step 1: Run all tests**

Run: `./gradlew allTests`
Expected: PASS (existing tests should still pass)

**Step 2: Run static analysis**

Run: `./gradlew detekt`
Expected: PASS (no new violations)

**Step 3: Run desktop app for manual verification**

Run: `./gradlew run`

Test plan:
- [ ] Empty deck → no crash, search skipped gracefully
- [ ] 1-card deck → all suppliers searched, single result appears
- [ ] 60-card deck → progress bar shows, cards stream in
- [ ] Network error → error badge on failed supplier, other suppliers still work
- [ ] Re-search same deck → cached data used (instant for proxy sellers)
- [ ] Dark/light theme → SearchProgressPanel renders correctly in both

**Step 4: Fix any issues found**

**Step 5: Final commit**

```bash
git add -A
git commit -m "fix: address test and UI issues from streaming search integration"
```

---

## Summary of Files Changed

| File | Action |
|------|--------|
| `src/commonMain/kotlin/state/SearchProgress.kt` | **Create** — state types |
| `src/commonMain/sqldelight/database/SellerCache.sq` | **Create** — cache table schema |
| `src/commonMain/kotlin/database/Database.kt` | **Modify** — cache methods, getAllVariants |
| `src/commonMain/kotlin/database/CatalogStore.kt` | **Modify** — cache checking, getAllVariants |
| `src/commonMain/kotlin/state/DeckSearchUseCase.kt` | **Create** — core orchestration |
| `src/commonMain/kotlin/state/MviViewModel.kt` | **Modify** — new intent, state wiring, wizard flow |
| `src/commonMain/kotlin/ui/PixelComponents.kt` | **Modify** — PixelProgressBar, SearchProgressPanel |
| `src/commonMain/kotlin/ui/ResultsScreen.kt` | **Modify** — streaming progress UI |
| `src/desktopMain/kotlin/app/Main.kt` | **Modify** — pass searchProgress to ResultsScreen |
| `src/commonTest/kotlin/state/SearchProgressTest.kt` | **Create** — state type tests |
| `src/commonTest/kotlin/database/SellerCacheTtlTest.kt` | **Create** — cache TTL tests |
| `src/commonTest/kotlin/state/DeckSearchUseCaseTest.kt` | **Create** — use case tests |
