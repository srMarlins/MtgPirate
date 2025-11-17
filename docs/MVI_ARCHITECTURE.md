# MVI Architecture Documentation

## Table of Contents

- [Overview](#overview)
- [Architecture Components](#architecture-components)
- [Data Flow](#data-flow)
- [Platform Services](#platform-services)
- [State Management](#state-management)
- [Testing](#testing)
- [Benefits](#benefits)
- [Migration Guide](#migration-guide)
- [Best Practices](#best-practices)

## Overview

MtgPirate implements **MVI (Model-View-Intent)** for state management, providing:

- ✅ **Reactive** - UI automatically updates when state changes
- ✅ **Predictable** - Single source of truth with unidirectional flow
- ✅ **Testable** - Isolated business logic, easy to mock
- ✅ **Platform-agnostic** - Works across Desktop, iOS, Android
- ✅ **Persistent** - Database-backed state

## Architecture Components

### 1. ViewState

Immutable data class representing complete UI state:

```kotlin
data class ViewState(
    val deckText: String = "",
    val matches: List<DeckEntryMatch> = emptyList(),
    val catalog: Catalog? = null,
    val preferences: Preferences = Preferences(),
    val isDarkTheme: Boolean = false
    // ... other UI state
)
```

**Characteristics:**
- Immutable - new state created for every change
- Complete - contains all UI rendering information
- Single source of truth - derived from database + local state

### 2. ViewIntent

Sealed class representing all user actions:

```kotlin
sealed class ViewIntent {
    object Init : ViewIntent()
    data class UpdateDeckText(val text: String) : ViewIntent()
    object ParseDeck : ViewIntent()
    object RefreshCatalog : ViewIntent()
    data class ResolveCandidate(val matchIndex: Int, val variant: CardVariant) : ViewIntent()
    object ExportCsv : ViewIntent()
    // ... other intents
}
```

**Characteristics:**
- Sealed class - compiler ensures all cases handled
- Descriptive names - clearly communicate intention
- Complete parameters - contain all necessary data

### 3. ViewEffect

One-time side effects (not part of state):

```kotlin
sealed class ViewEffect {
    data class ShowToast(val message: String) : ViewEffect()
    data class ShowError(val message: String) : ViewEffect()
    object NavigateToResults : ViewEffect()
}
```

**Characteristics:**
- One-time events - consumed once by UI
- Not persisted - temporary notifications
- Collected separately from state

### 4. MviViewModel

Central coordinator that:
1. Receives intents from UI
2. Processes business logic
3. Updates database
4. Emits state via Flows
5. Produces side effects

```kotlin
class MviViewModel(
    private val scope: CoroutineScope,
    private val database: Database,
    private val catalogStore: CatalogStore,
    private val platformServices: MviPlatformServices
) {
    val viewState: StateFlow<ViewState>
    val viewEffects: SharedFlow<ViewEffect>
    
    fun processIntent(intent: ViewIntent) { /* ... */ }
}
```

### 5. Database

SQLDelight database as single source of truth:

**Tables:**
- `CardVariant` - Catalog data with prices
- `Preferences` - User settings
- `SavedImport` - Import history
- `LogEntry` - Debug logs

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

## State Management

### Combining Database Flows

ViewModel combines multiple database flows into single `ViewState`:

```kotlin
val viewState: StateFlow<ViewState> = combine(
    database.observeCatalog(),
    database.observePreferences(),
    localUiState
) { catalog, prefs, localState ->
    ViewState(
        catalog = catalog,
        preferences = prefs,
        deckText = localState.deckText,
        matches = localState.matches
    )
}.stateIn(scope, SharingStarted.Eagerly, ViewState())
```

### Local UI State

Ephemeral state not requiring persistence:
- `deckText` - Current input text
- `matches` - Temporary matching results
- `showResolveDialog` - Dialog visibility flags

### Intent Processing

Intents processed sequentially in coroutines:

```kotlin
fun processIntent(intent: ViewIntent) {
    scope.launch {
        when (intent) {
            is ViewIntent.UpdateDeckText -> {
                _localState.update { it.copy(deckText = intent.text) }
            }
            is ViewIntent.RefreshCatalog -> {
                val catalog = platformServices.fetchCatalogFromRemote()
                catalogStore.insertVariants(catalog.variants)
                _effects.emit(ViewEffect.ShowToast("Catalog refreshed"))
            }
        }
    }
}
```

## Testing

### Unit Testing ViewModels

```kotlin
@Test
fun `catalog refresh updates state`() = runTest {
    val mockServices = MockPlatformServices()
    val viewModel = MviViewModel(this, testDatabase, catalogStore, mockServices)
    
    viewModel.processIntent(ViewIntent.RefreshCatalog)
    advanceUntilIdle()
    
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
    // ... other methods
}
```

## Benefits

### Predictable State Management
- Single source of truth (database)
- Unidirectional data flow
- No race conditions

### Reactive UI
- Automatic updates via Compose
- No manual refresh needed
- Efficient recomposition

### Persistence
- State survives restarts
- No manual save/load
- Type-safe queries

### Platform Independence
- Core ViewModel works everywhere
- Platform-specific code isolated
- Easy to add platforms

### Testability & Debugging
- Isolated business logic
- Easy to mock dependencies
- All state changes logged
- Time-travel debugging possible

## Migration Guide

| Aspect | MainStore (Legacy) | MviViewModel (New) |
|--------|-------------------|-------------------|
| State Storage | In-memory | Database (SQLDelight) |
| Persistence | Manual JSON files | Automatic |
| Reactivity | Manual updates | Kotlin Flows |
| Platform Support | Desktop only | Desktop + iOS + Android |
| Testing | Harder to mock | Easy to mock |

**Migration path:**
1. New features use MVI architecture
2. Legacy code migrates gradually
3. Both architectures can coexist

## Best Practices

### Keep ViewState Immutable

```kotlin
// ✅ Good - creates new state
_localState.update { it.copy(deckText = newText) }

// ❌ Bad - mutates state
state.deckText = newText
```

### Process All Intents in ViewModel

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

### Use Effects for One-Time Events

```kotlin
// ✅ Good - effect for toast
_effects.emit(ViewEffect.ShowToast("Import saved!"))

// ❌ Bad - state for toast
_state.update { it.copy(toastMessage = "Import saved!") }
```

### Combine Database Flows Efficiently

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

**Desktop**
- Full platform services
- File system access for CSV export
- HTTP client for catalog fetching

**iOS**
- Simplified platform services
- Cached catalog data (no live fetching)
- Clipboard export only

**Android** (Future)
- Android-specific services
- File picker for export
- Android networking APIs

## Resources

- [Kotlin Flows](https://kotlinlang.org/docs/flow.html)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [MVI Pattern](https://hannesdorfmann.com/android/model-view-intent/)
