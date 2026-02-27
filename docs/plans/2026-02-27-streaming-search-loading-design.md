# Streaming Search Loading UI

**Date:** 2026-02-27
**Status:** Design

## Problem

Loading catalogs from ManaPool (95K singles) and Bootleg Mage (1-2K products) takes 15-30+ seconds. During this time, users see only bouncing dots with no indication of progress. Additionally, loading 95K cards when matching a 60-100 card deck is wasteful.

## Solution

Replace bulk catalog loading with a hybrid per-card search approach that streams results progressively. Each supplier is searched in parallel, and results appear in the UI as they're found.

## Supplier Strategy

| Supplier | Strategy | Expected Speed |
|----------|----------|---------------|
| Scryfall/TCGPlayer | Batch collection API (1 request for ≤75 cards) | ~1 second |
| Bootleg Mage | Paginate full catalog via Store API, filter to deck | ~5-10 seconds |
| ManaPool | Bulk load all 95K singles, filter to deck | ~15-30 seconds |
| USEA | Existing platform services path | varies |

All suppliers search concurrently via `coroutineScope { launch {} }`.

## Caching

Variants are stored in `CatalogStore` (SQLite) with a `lastFetchedAt` timestamp per seller.

| Seller Type | TTL | Rationale |
|-------------|-----|-----------|
| Proxy (BM, USEA) | 7 days | Inventory changes infrequently |
| Real cards (ManaPool, Scryfall/TCGPlayer) | 24 hours | Market prices fluctuate daily |

On search, check cached data freshness. If within TTL, skip API call for that seller and use cached results instantly.

## Data Flow

```
User pastes deck (60-100 cards)
         ↓
    Parse deck entries
         ↓
    DeckSearchUseCase.searchDeck(entries) → Flow<SearchProgress>
      ├─ Check cache per seller (skip if fresh)
      ├─ Scryfall: getCollection batch API → emit results
      ├─ Bootleg Mage: paginate Store API → emit per page
      └─ ManaPool: fetchAllSingles → filter to deck → emit results
         ↓  (all parallel via coroutineScope)
    ViewModel collects Flow, updates ViewState incrementally
         ↓
    ResultsScreen shows cards appearing as found
```

## State Model

### New Types

```kotlin
data class SellerSearchStatus(
    val seller: Seller,
    val state: SearchState,
    val cardsFound: Int = 0,
    val message: String? = null,
)

enum class SearchState { PENDING, SEARCHING, DONE, ERROR }

data class SearchProgress(
    val totalCards: Int,
    val cardsWithResults: Int,
    val sellerStatuses: Map<Seller, SellerSearchStatus>,
    val newResults: List<MultiMatch>,
    val isComplete: Boolean,
)
```

### ViewState Changes

```kotlin
// Replace loadingMultiCatalogs: Boolean with:
val searchProgress: SearchProgress? = null  // null = not searching
```

`loadingMultiCatalogs` becomes derived: `searchProgress != null && !searchProgress.isComplete`.

## Use Case Architecture

New `DeckSearchUseCase` uses `channelFlow` to emit progress from parallel coroutines:

```kotlin
class DeckSearchUseCase(
    private val sources: List<CatalogSource>,
    private val catalogStore: CatalogStore,
) {
    fun searchDeck(
        entries: List<DeckEntry>,
        log: (String, String) -> Unit,
    ): Flow<SearchProgress> = channelFlow {
        val sellerStatuses = ConcurrentHashMap<Seller, SellerSearchStatus>()
        val allResults = ConcurrentHashMap<String, MultiMatch>()

        // Initialize all sellers as PENDING
        sources.forEach { sellerStatuses[it.seller] = SellerSearchStatus(it.seller, SearchState.PENDING) }
        send(buildProgress(..., isComplete = false))

        // Launch all suppliers in parallel
        coroutineScope {
            for (source in sources) {
                launch { searchSupplier(source, entries, ...) }
            }
        }

        // Final emission
        send(buildProgress(..., isComplete = true))
    }
}
```

Each supplier search:
- Updates its `SellerSearchStatus` in the shared map
- Calls `send()` on the channel after each batch of results
- Stores results in `CatalogStore` per-seller for caching

## Loading Progress UI

Replaces `AnimatedLoadingDots()` in `ResultsScreen` with `SearchProgressPanel`:

```
┌──────────────────────────────────────────────────┐
│  Searching 14/60 cards...          ████████░░ 23% │
│                                                   │
│  ✓ Scryfall/TCGPlayer    58 cards    done          │
│  ◉ Bootleg Mage          12 cards    page 3/20...  │
│  ◉ ManaPool              0 cards     loading...    │
│  · USEA                  —           pending       │
└──────────────────────────────────────────────────┘
```

- Progress bar: `cardsWithResults / totalCards`
- Per-supplier rows with seller color badges
- Results list renders incrementally below (cards appear as found)
- On completion: panel collapses to summary line

## Error Handling

- **Supplier fails**: Mark as `SearchState.ERROR`. Other suppliers continue. UI shows error badge.
- **Partial results**: User sees whatever succeeded. Not a fatal error.
- **Empty deck**: Skip search. Show "No cards to search" message.
- **Cancel/re-search**: Cancel in-flight `Flow` via `Job.cancel()` before starting new search.
- **Not Found status**: Only mark cards "Not Found" after ALL suppliers complete.

## Files to Modify

| File | Change |
|------|--------|
| `state/DeckSearchUseCase.kt` | **New** — orchestrates parallel search with `channelFlow` |
| `state/MviViewModel.kt` | Add `SearchProgress` to state, new intent handler, replace `loadAllCatalogs` flow |
| `state/CatalogUseCase.kt` | Add cache TTL checking, `lastFetchedAt` per seller |
| `catalog/CatalogSource.kt` | No change (interface already has `search()`) |
| `catalog/ScryfallPricingSource.kt` | Use existing `searchBulk` for batch search |
| `catalog/BootlegMageCatalogSource.kt` | Use existing `fetchCatalog` (paginated) + filter |
| `catalog/ManaPoolCatalogSource.kt` | Use existing `fetchCatalog` (bulk) + filter |
| `database/CatalogStore.kt` | Add `lastFetchedAt` timestamp per seller |
| `ui/ResultsScreen.kt` | Replace `AnimatedLoadingDots` with `SearchProgressPanel` |
| `ui/PixelComponents.kt` | Add `SearchProgressPanel` composable, `PixelProgressBar` |
