# MVI (Model-View-Intent) Architecture

## Overview

This project now includes an MVI (Model-View-Intent) unidirectional data flow architecture alongside the existing MainStore pattern. The MVI implementation uses the **database as the single source of truth**, with reactive flows propagating changes to the UI.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                           UI Layer                          │
│  - Observes ViewState (StateFlow)                          │
│  - Sends ViewIntents to ViewModel                          │
│  - Reacts to ViewEffects (one-time events)                 │
└────────────────────────┬────────────────────────────────────┘
                         │ ViewIntent
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                        MviViewModel                         │
│  - Processes ViewIntents (user actions)                    │
│  - Reduces state and triggers database updates             │
│  - Combines database Flows into ViewState                  │
│  - Emits ViewEffects for side effects                      │
└────────────────────────┬────────────────────────────────────┘
                         │ DB Operations
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    Database Layer (SQLDelight)              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ CatalogStore │  │ ImportsStore │  │ Preferences  │     │
│  │   (Flows)    │  │   (Flows)    │  │   (Flows)    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                             │
│  - CardVariants (catalog data)                             │
│  - SavedImports (user imports)                             │
│  - Preferences (user settings)                             │
│  - LogEntries (application logs)                           │
└────────────────────────┬────────────────────────────────────┘
                         │ Reactive Flows
                         ↓
                    ViewState Update
                         │
                         ↓
                      UI Layer
```

## Data Flow

### 1. UI → ViewModel (User Action)

```kotlin
// User clicks "Load Catalog" button
viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
```

### 2. ViewModel → Database (State Update)

```kotlin
// ViewModel fetches from remote and stores in database
val catalog = platformServices.fetchCatalogFromRemote { log(it) }
catalogStore.replaceCatalog(catalog) // Updates database
```

### 3. Database → ViewModel → UI (Reactive Update)

```kotlin
// Database change triggers Flow emission
database.observeCatalog() // Flow<Catalog>
    .map { catalog -> ViewState(catalog = catalog, ...) }
    .collect { newState -> 
        _viewState.value = newState // UI automatically updates
    }
```

## Key Components

### MviViewModel

The central coordinator that:
- Receives user intents (`ViewIntent`)
- Processes business logic
- Updates the database (source of truth)
- Combines database flows into `ViewState`
- Emits one-time side effects via `ViewEffect`

**Location**: `src/commonMain/kotlin/state/MviViewModel.kt`

### ViewState

Immutable state representing the entire UI state:

```kotlin
data class ViewState(
    val catalog: Catalog? = null,           // From database
    val preferences: Preferences = ...,     // From database
    val savedImports: List<SavedImport>,    // From database
    val logs: List<LogEntry>,               // From database
    val deckText: String = "",              // Transient UI state
    val deckEntries: List<DeckEntry>,       // Computed from deckText
    val matches: List<DeckEntryMatch>,      // Computed from entries + catalog
    // ... UI flags and transient state
)
```

### ViewIntent

Sealed class representing all possible user actions:

```kotlin
sealed class ViewIntent {
    data object Init : ViewIntent()
    data class UpdateDeckText(val text: String) : ViewIntent()
    data class LoadCatalog(val force: Boolean) : ViewIntent()
    data object ParseAndMatch : ViewIntent()
    data class ResolveCandidate(val index: Int, val variant: CardVariant) : ViewIntent()
    // ... more intents
}
```

### ViewEffect

One-time side effects (navigation, toasts, dialogs):

```kotlin
sealed class ViewEffect {
    data class ShowMessage(val message: String) : ViewEffect()
    data class NavigateTo(val route: String) : ViewEffect()
    data class ShowError(val error: String) : ViewEffect()
}
```

### CatalogStore

Database store for managing catalog card variants:

```kotlin
class CatalogStore(private val database: Database) {
    fun observeCatalog(): Flow<Catalog>
    suspend fun replaceCatalog(catalog: Catalog)
    suspend fun clearCatalog()
}
```

**Location**: `src/commonMain/kotlin/database/CatalogStore.kt`

### MviPlatformServices

Interface for platform-specific operations:

```kotlin
interface MviPlatformServices {
    suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog?
    suspend fun updatePreferences(update: (Preferences) -> Preferences)
    suspend fun addLog(log: LogEntry)
    suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit)
}
```

**Desktop Implementation**: `src/desktopMain/kotlin/platform/DesktopMviPlatformServices.kt`

## Usage Example

### Setting Up the ViewModel

```kotlin
import database.Database
import database.CatalogStore
import database.ImportsStore
import platform.DesktopMviPlatformServices
import state.MviViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// Create database and stores
val database = Database(databaseDriverFactory)
val catalogStore = CatalogStore(database)
val importsStore = ImportsStore(database)

// Create platform services
val platformServices = DesktopMviPlatformServices(database)

// Create ViewModel
val viewModel = MviViewModel(
    scope = CoroutineScope(Dispatchers.Main),
    database = database,
    catalogStore = catalogStore,
    importsStore = importsStore,
    platformServices = platformServices
)

// Initialize
viewModel.processIntent(ViewIntent.Init)
```

### Observing State in UI

```kotlin
@Composable
fun MyScreen(viewModel: MviViewModel) {
    val viewState by viewModel.viewState.collectAsState()
    
    // Observe effects
    LaunchedEffect(Unit) {
        viewModel.viewEffects.collect { effect ->
            when (effect) {
                is ViewEffect.ShowMessage -> showToast(effect.message)
                is ViewEffect.ShowError -> showErrorDialog(effect.error)
                else -> {}
            }
        }
    }
    
    // Render UI based on viewState
    Column {
        if (viewState.loadingCatalog) {
            CircularProgressIndicator()
        }
        
        Text("Catalog: ${viewState.catalog?.variants?.size} variants")
        
        Button(onClick = {
            viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
        }) {
            Text("Refresh Catalog")
        }
    }
}
```

### Sending Intents

```kotlin
// User types in deck text area
TextField(
    value = viewState.deckText,
    onValueChange = { text ->
        viewModel.processIntent(ViewIntent.UpdateDeckText(text))
    }
)

// User clicks Parse & Match button
Button(onClick = {
    viewModel.processIntent(ViewIntent.ParseAndMatch)
}) {
    Text("Parse & Match")
}

// User toggles include sideboard
Checkbox(
    checked = viewState.includeSideboard,
    onCheckedChange = { checked ->
        viewModel.processIntent(ViewIntent.ToggleIncludeSideboard(checked))
    }
)
```

## Benefits of MVI Architecture

### 1. Single Source of Truth
- Database is the authoritative source for all persistent data
- No state synchronization issues between UI and storage
- Consistent state across app restarts

### 2. Unidirectional Data Flow
- Easy to trace and debug state changes
- Predictable state updates
- Clear separation of concerns

### 3. Reactive UI
- UI automatically updates when database changes
- No manual refresh logic needed
- Real-time updates via Flows

### 4. Testability
- ViewState is immutable - easy to test
- Intent processing can be tested in isolation
- Database can be mocked for unit tests

### 5. Scalability
- Easy to add new intents and effects
- State composition scales well
- Platform-specific logic is isolated

## Database Schema

The database uses SQLDelight for type-safe SQL queries:

### CardVariantEntity
```sql
CREATE TABLE CardVariantEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nameOriginal TEXT NOT NULL,
    nameNormalized TEXT NOT NULL,
    setCode TEXT NOT NULL,
    sku TEXT NOT NULL,
    variantType TEXT NOT NULL,
    priceInCents INTEGER NOT NULL,
    collectorNumber TEXT,
    imageUrl TEXT
);
CREATE INDEX idx_nameNormalized ON CardVariantEntity(nameNormalized);
```

### PreferencesEntity
```sql
CREATE TABLE PreferencesEntity (
    id INTEGER PRIMARY KEY DEFAULT 1,
    includeSideboard INTEGER NOT NULL,
    includeCommanders INTEGER NOT NULL,
    includeTokens INTEGER NOT NULL,
    variantPriority TEXT NOT NULL,
    setPriority TEXT NOT NULL,
    fuzzyEnabled INTEGER NOT NULL,
    cacheMaxAgeHours INTEGER NOT NULL,
    CHECK (id = 1)
);
```

### SavedImportEntity
```sql
CREATE TABLE SavedImportEntity (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    deckText TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    cardCount INTEGER NOT NULL,
    includeSideboard INTEGER NOT NULL,
    includeCommanders INTEGER NOT NULL,
    includeTokens INTEGER NOT NULL
);
```

### LogEntryEntity
```sql
CREATE TABLE LogEntryEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    timestamp TEXT NOT NULL
);
```

## Complete Data Flow Example

### Scenario: User loads catalog and matches a deck

1. **User clicks "Load Catalog"**
   ```kotlin
   viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
   ```

2. **ViewModel fetches from remote API**
   ```kotlin
   val catalog = platformServices.fetchCatalogFromRemote { log(it) }
   // Returns Catalog with 10,000+ variants
   ```

3. **ViewModel stores catalog in database**
   ```kotlin
   catalogStore.replaceCatalog(catalog)
   // Clears old variants, inserts new ones
   ```

4. **Database emits update via Flow**
   ```kotlin
   database.observeCatalog() // Emits new Catalog
   ```

5. **ViewModel combines flows into ViewState**
   ```kotlin
   combine(
       database.observeCatalog(),
       database.observePreferences(),
       // ... other flows
   ) { catalog, prefs, ... ->
       ViewState(catalog = catalog, preferences = prefs, ...)
   }
   ```

6. **UI automatically updates**
   ```kotlin
   // Compose recomposes with new viewState
   Text("Catalog: ${viewState.catalog?.variants?.size} variants")
   // Shows "Catalog: 10,234 variants"
   ```

7. **User pastes deck and clicks "Parse & Match"**
   ```kotlin
   viewModel.processIntent(ViewIntent.ParseAndMatch)
   ```

8. **ViewModel parses deck and matches against catalog**
   ```kotlin
   val entries = DecklistParser.parse(deckText, ...)
   val matches = Matcher.matchAll(entries, catalog, config)
   // Updates local state (not persisted)
   _localState.update { it.copy(matches = matches) }
   ```

9. **UI shows matches**
   ```kotlin
   // Compose recomposes with matched cards
   matches.forEach { match ->
       MatchCard(match)
   }
   ```

## Migration from MainStore

Both `MainStore` (legacy) and `MviViewModel` (new) can coexist:

- **MainStore**: Current implementation using in-memory state
- **MviViewModel**: New database-backed implementation

To migrate:
1. Use `MviViewModel` for new features
2. Gradually refactor existing screens to use MviViewModel
3. Eventually deprecate MainStore once migration is complete

## Testing

### Testing Intents

```kotlin
@Test
fun `processIntent UpdateDeckText updates state`() = runTest {
    val viewModel = createTestViewModel()
    
    viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt"))
    
    assertEquals("4 Lightning Bolt", viewModel.viewState.value.deckText)
}
```

### Testing Database Integration

```kotlin
@Test
fun `catalog loads from database reactively`() = runTest {
    val database = createTestDatabase()
    val viewModel = createTestViewModel(database)
    
    // Insert catalog directly into database
    database.insertVariant(testVariant)
    
    // Wait for flow to emit
    advanceUntilIdle()
    
    // ViewState should have the catalog
    assertEquals(1, viewModel.viewState.value.catalog?.variants?.size)
}
```

### Mocking Platform Services

```kotlin
class MockMviPlatformServices : MviPlatformServices {
    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit) = 
        Catalog(variants = listOf(testVariant))
    
    override suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        // No-op for tests
    }
    
    // ... implement other methods
}
```

## Best Practices

1. **Keep ViewState immutable** - Use data classes and copy()
2. **Process intents, don't mutate state directly** - All state changes go through processIntent()
3. **Use database for persistent state** - Preferences, catalog, imports
4. **Use local state for transient UI state** - Loading flags, dialog visibility
5. **Emit effects for one-time events** - Toasts, navigation, dialogs
6. **Log important state changes** - Use ViewIntent.Log for debugging
7. **Clean up old logs periodically** - Prevent database bloat

## References

- [MVI Architecture Pattern](https://hannesdorfmann.com/android/model-view-intent/)
- [Unidirectional Data Flow](https://developer.android.com/topic/architecture/ui-layer#udf)
- [Kotlin Flows](https://kotlinlang.org/docs/flow.html)
- [SQLDelight Documentation](https://cashapp.github.io/sqldelight/)

## Comparison: MainStore vs MviViewModel

| Feature | MainStore | MviViewModel |
|---------|-----------|--------------|
| State Management | In-memory StateFlow | Database + Flows |
| Source of Truth | Memory | Database |
| Persistence | Manual (PlatformServices) | Automatic (Flows) |
| State Survival | Lost on restart | Survives restarts |
| UI Updates | Manual state updates | Reactive flows |
| Testing | Mock PlatformServices | Mock database + services |
| Complexity | Simple | Moderate |
| Scalability | Good | Excellent |

## Future Enhancements

- [ ] Add offline-first sync strategy
- [ ] Implement state restoration on app restart
- [ ] Add database migrations for schema changes
- [ ] Create common test utilities for MVI
- [ ] Add performance monitoring for database queries
- [ ] Implement pagination for large datasets
- [ ] Add conflict resolution for concurrent updates
