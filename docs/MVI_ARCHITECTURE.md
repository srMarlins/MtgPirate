# MVI Architecture Documentation

## Overview

MtgPirate implements the **MVI (Model-View-Intent)** architectural pattern for state management. This provides a unidirectional data flow architecture that is:

- ✅ **Reactive** - UI automatically updates when state changes
- ✅ **Predictable** - All state changes flow through a single point
- ✅ **Testable** - Business logic is isolated and easy to mock
- ✅ **Platform-agnostic** - Works across Desktop, iOS, and Android
- ✅ **Persistent** - Database serves as the single source of truth

## Architecture Components

### 1. ViewState

`ViewState` is an immutable data class representing the entire UI state:

```kotlin
data class ViewState(
    val deckText: String = "",
    val matches: List<DeckEntryMatch> = emptyList(),
    val catalog: Catalog? = null,
    val preferences: Preferences = Preferences(),
    val isDarkTheme: Boolean = false,
    val showSavedImportsWindow: Boolean = false,
    // ... other UI state
)
```

**Key characteristics:**
- Immutable - new state is created for every change
- Complete - contains all information needed to render the UI
- Single source of truth - derived from database + local UI state

### 2. ViewIntent

`ViewIntent` is a sealed class representing all possible user actions:

```kotlin
sealed class ViewIntent {
    // Initialization
    object Init : ViewIntent()
    
    // Deck operations
    data class UpdateDeckText(val text: String) : ViewIntent()
    object ParseDeck : ViewIntent()
    object RunMatch : ViewIntent()
    
    // Catalog operations
    object RefreshCatalog : ViewIntent()
    
    // Resolution
    data class OpenResolve(val matchIndex: Int) : ViewIntent()
    data class ResolveCandidate(val matchIndex: Int, val variant: CardVariant) : ViewIntent()
    object CloseResolve : ViewIntent()
    
    // Export
    object ExportCsv : ViewIntent()
    
    // Preferences
    data class ToggleIncludeSideboard(val include: Boolean) : ViewIntent()
    object ToggleTheme : ViewIntent()
    
    // ... other intents
}
```

**Key characteristics:**
- Sealed class - compiler ensures all cases are handled
- Descriptive names - clearly communicate user intention
- Complete parameters - contain all data needed for the action

### 3. ViewEffect

`ViewEffect` represents one-time side effects (not part of state):

```kotlin
sealed class ViewEffect {
    data class ShowToast(val message: String) : ViewEffect()
    data class ShowError(val message: String) : ViewEffect()
    object NavigateToResults : ViewEffect()
    // ... other effects
}
```

**Key characteristics:**
- One-time events - consumed once by UI
- Not persisted - temporary notifications or navigation
- Collected separately from state

### 4. MviViewModel

The `MviViewModel` is the central coordinator that:

1. Receives intents from the UI
2. Processes business logic
3. Updates the database
4. Emits new state via Flows
5. Produces side effects

```kotlin
class MviViewModel(
    private val scope: CoroutineScope,
    private val database: Database,
    private val catalogStore: CatalogStore,
    private val importsStore: ImportsStore,
    private val platformServices: MviPlatformServices
) {
    val viewState: StateFlow<ViewState>
    val viewEffects: SharedFlow<ViewEffect>
    
    fun processIntent(intent: ViewIntent) {
        // Process intent and update state
    }
}
```

### 5. Database

The database (SQLDelight) serves as the single source of truth:

```
┌─────────────────┐
│   Database      │
│  (SQLDelight)   │
├─────────────────┤
│ CardVariant     │ ← Catalog data
│ Preferences     │ ← User settings  
│ SavedImport     │ ← Import history
│ LogEntry        │ ← Debug logs
└─────────────────┘
```

**Database tables:**
- `CardVariant` - Card catalog with prices and metadata
- `Preferences` - User preferences (theme, filters, etc.)
- `SavedImport` - Historical decklist imports
- `LogEntry` - Application logs for debugging

## Data Flow

```
┌──────────┐
│   User   │
└────┬─────┘
     │ 1. User Action
     ▼
┌──────────────────┐
│   UI (Compose)   │
└────┬─────────────┘
     │ 2. ViewIntent
     ▼
┌──────────────────┐
│  MviViewModel    │
└────┬─────────────┘
     │ 3. Process Intent
     ▼
┌──────────────────┐
│  Business Logic  │
│  + Services      │
└────┬─────────────┘
     │ 4. Update Database
     ▼
┌──────────────────┐
│   Database       │
│  (Single Source  │
│   of Truth)      │
└────┬─────────────┘
     │ 5. Emit Flow
     ▼
┌──────────────────┐
│   ViewState      │
└────┬─────────────┘
     │ 6. Observe State
     ▼
┌──────────────────┐
│   UI (Compose)   │
│   Re-renders     │
└──────────────────┘
```

### Example Flow: Refreshing Catalog

1. **User clicks "Refresh Catalog"** → UI sends `ViewIntent.RefreshCatalog`
2. **ViewModel processes intent:**
   - Calls `platformServices.fetchCatalogFromRemote()`
   - Parses CSV data
   - Stores variants in database via `catalogStore.insertVariants()`
3. **Database emits change** → `database.observeCatalog()` flow updates
4. **ViewModel combines flows** → New `ViewState` with updated catalog
5. **UI observes state** → Compose re-renders with new catalog data

## Platform Services

Platform-specific operations are abstracted via `MviPlatformServices`:

```kotlin
interface MviPlatformServices {
    suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog?
    suspend fun updatePreferences(update: (Preferences) -> Preferences)
    suspend fun addLog(log: LogEntry)
    suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit)
    suspend fun copyToClipboard(text: String)
}
```

**Implementations:**
- `DesktopMviPlatformServices` - Desktop-specific (file I/O, HTTP)
- `IosMviPlatformServices` - iOS-specific (clipboard, database)

This allows the core `MviViewModel` to remain platform-agnostic while delegating platform-specific operations.

## State Management Strategies

### Combining Database Flows

The ViewModel combines multiple database flows into a single `ViewState`:

```kotlin
val viewState: StateFlow<ViewState> = combine(
    database.observeCatalog(),
    database.observePreferences(),
    database.observeSavedImports(),
    localUiState
) { catalog, prefs, imports, localState ->
    ViewState(
        catalog = catalog,
        preferences = prefs,
        savedImports = imports,
        deckText = localState.deckText,
        matches = localState.matches,
        // ...
    )
}.stateIn(scope, SharingStarted.Eagerly, ViewState())
```

### Local UI State

Some state is ephemeral and doesn't need persistence:

- `deckText` - Current text in the decklist input
- `matches` - Temporary matching results before saving
- `showResolveDialog` - Dialog visibility flags

This local state is managed in-memory and combined with database state.

### Intent Processing

Intents are processed sequentially in a coroutine:

```kotlin
fun processIntent(intent: ViewIntent) {
    scope.launch {
        when (intent) {
            is ViewIntent.UpdateDeckText -> {
                _localState.update { it.copy(deckText = intent.text) }
            }
            is ViewIntent.RefreshCatalog -> {
                val catalog = platformServices.fetchCatalogFromRemote { msg ->
                    addLog(LogEntry.info(msg))
                }
                if (catalog != null) {
                    catalogStore.insertVariants(catalog.variants)
                    _effects.emit(ViewEffect.ShowToast("Catalog refreshed"))
                }
            }
            // ... other intents
        }
    }
}
```

## Testing

The MVI architecture makes testing straightforward:

### Unit Testing ViewModels

```kotlin
@Test
fun `test catalog refresh updates state`() = runTest {
    // Given
    val mockServices = MockPlatformServices()
    val viewModel = MviViewModel(
        scope = this,
        database = testDatabase,
        catalogStore = catalogStore,
        importsStore = importsStore,
        platformServices = mockServices
    )
    
    // When
    viewModel.processIntent(ViewIntent.RefreshCatalog)
    advanceUntilIdle()
    
    // Then
    val state = viewModel.viewState.value
    assertNotNull(state.catalog)
    assertEquals(100, state.catalog?.variants?.size)
}
```

### Mocking Platform Services

```kotlin
class MockPlatformServices : MviPlatformServices {
    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog {
        return Catalog(variants = listOf(/* test data */))
    }
    
    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        onComplete("Test export successful")
    }
    
    // ... other methods
}
```

## Benefits of MVI Architecture

### 1. Predictable State Management

- Single source of truth (database)
- Unidirectional data flow
- No race conditions or inconsistent state

### 2. Reactive UI

- Compose UI automatically updates when state changes
- No manual refresh logic needed
- Efficient recomposition (only changed parts re-render)

### 3. Persistence by Default

- State survives app restarts
- No need for manual save/load logic
- SQLDelight provides type-safe queries

### 4. Platform Independence

- Core ViewModel works on all platforms
- Platform-specific code isolated to services
- Easy to add new platforms (Android, Web)

### 5. Testability

- Business logic isolated in ViewModel
- Easy to mock dependencies
- Deterministic behavior

### 6. Debugging

- All state changes logged in database
- Easy to trace intent → state transitions
- Time-travel debugging possible

## Migration from Legacy MainStore

The project includes both **MVI** (new) and **MainStore** (legacy) architectures:

| Aspect | MainStore (Legacy) | MviViewModel (New) |
|--------|-------------------|-------------------|
| State Storage | In-memory | Database (SQLDelight) |
| Persistence | Manual JSON files | Automatic via database |
| Reactivity | Manual updates | Kotlin Flows |
| Platform Support | Desktop only | Desktop + iOS + Android |
| Testing | Harder to mock | Easy to mock services |

**Migration path:**
1. New features should use MVI architecture
2. Legacy code can gradually migrate to MVI
3. Both can coexist during transition

## Best Practices

### 1. Keep ViewState Immutable

```kotlin
// ✅ Good - creates new state
_localState.update { it.copy(deckText = newText) }

// ❌ Bad - mutates state
state.deckText = newText
```

### 2. Process All Intents in ViewModel

```kotlin
// ✅ Good - business logic in ViewModel
fun processIntent(intent: ViewIntent.ParseDeck) {
    val entries = decklistParser.parse(state.deckText)
    _localState.update { it.copy(parsedEntries = entries) }
}

// ❌ Bad - business logic in UI
val entries = decklistParser.parse(state.deckText)
viewModel.updateParsedEntries(entries)
```

### 3. Use Effects for One-Time Events

```kotlin
// ✅ Good - effect for toast
_effects.emit(ViewEffect.ShowToast("Import saved!"))

// ❌ Bad - state for toast (gets re-emitted on every state collection)
_state.update { it.copy(toastMessage = "Import saved!") }
```

### 4. Combine Database Flows Efficiently

```kotlin
// ✅ Good - combine at source
combine(
    database.observeCatalog(),
    database.observePreferences()
) { catalog, prefs -> ViewState(catalog, prefs) }

// ❌ Bad - multiple separate observers
database.observeCatalog().collect { catalog = it }
database.observePreferences().collect { prefs = it }
```

## Platform-Specific Considerations

### Desktop

- Full platform services implementation
- File system access for CSV export
- HTTP client for catalog fetching

### iOS

- Simplified platform services
- Relies on cached catalog data
- CSV export copies to clipboard
- Network fetching not implemented (uses cached DB)

### Android (Future)

- Will use Android-specific services
- File picker for export
- Android networking APIs

## Conclusion

The MVI architecture provides a robust, scalable foundation for MtgPirate:

- **Reliable** - Database as single source of truth
- **Reactive** - UI updates automatically
- **Cross-platform** - Works on Desktop, iOS, Android
- **Maintainable** - Clear separation of concerns
- **Testable** - Easy to mock and test

For new features, always use the MVI architecture. The legacy MainStore will be gradually phased out.

## Resources

- [Kotlin Flows Documentation](https://kotlinlang.org/docs/flow.html)
- [SQLDelight Documentation](https://cashapp.github.io/sqldelight/)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [MVI Pattern Explained](https://hannesdorfmann.com/android/model-view-intent/)
