# MTG PIRATE

A Kotlin Multiplatform application for importing Magic: The Gathering decklists, matching cards against the USEA MTG Proxy catalog, and exporting optimized CSV orders.

**Platforms:** Desktop (primary) • iOS (experimental)

<div align="center">
  <img src="https://github.com/user-attachments/assets/2f6e4147-6a47-4de0-8e95-25e556a46ab9" alt="MtgPirate Demo" style="max-width: 70%;">
</div>



- 📋 **Decklist Import** - Paste decklists from various formats (MTGO, Arena, MTGGoldfish, etc.)
- 🎯 **Intelligent Card Matching** - Fuzzy matching with set code hints and collector number support
- 💰 **Price Calculation** - Real-time pricing from USEA MTG Proxy catalog
- 🔍 **Ambiguity Resolution** - Interactive UI for resolving multiple card variants
- 📊 **CSV Export** - Generate ready-to-order CSV files with aggregated quantities
- 💾 **Import History** - Save and reload previous imports
- 🎨 **Dark Theme** - Eye-friendly interface with modern Compose UI
- ⚙️ **Flexible Options** - Include/exclude sideboard, commanders, and tokens

## Tech Stack

- **Kotlin Multiplatform** - Cross-platform codebase (Desktop + iOS)
- **Compose Multiplatform** - Modern declarative UI framework
- **Kotlin Coroutines** - Async operations and state management
- **Kotlin Serialization** - JSON persistence
- **KSoup** - HTML parsing for catalog fetching
- **SQLDelight** - Type-safe SQL database (MVI architecture)
- **Detekt** - Static code analysis for code quality

## Development

### Code Quality

This project uses [Detekt](https://detekt.dev/) for static code analysis. Detekt runs automatically on all pull requests via GitHub Actions.

**Running locally:**
```bash
./gradlew detekt
```

**Generate HTML report:**
```bash
./gradlew detekt
# Open build/reports/detekt/detekt.html
```

**Update baseline** (if you need to update the baseline for existing issues):
```bash
./gradlew detektBaseline
```

The detekt configuration is in `detekt.yml` and includes rules for:
- Code complexity
- Potential bugs
- Code style
- Performance issues
- Coroutines best practices

## Architecture

This project implements two state management approaches:

1. **Legacy: MainStore** - In-memory state with manual persistence
2. **New: MVI (Model-View-Intent)** - Database-backed reactive architecture ✨

The **MVI architecture** (recommended for new features) provides:
- 🗄️ **Database as source of truth** - State persists across restarts
- 🔄 **Reactive UI** - Automatic updates via Kotlin Flows
- 🎯 **Unidirectional data flow** - Easy to debug and test
- 🧪 **Testable** - Mock services for unit testing
- 🔌 **Platform-agnostic** - Works on Desktop, iOS, Android

See [MVI Architecture Documentation](docs/MVI_ARCHITECTURE.md) for details.

## How to Use

### Step 1: Import Your Decklist

1. Launch the application
2. Paste your decklist in the text area
3. Configure options:
   - ✅ Include Sideboard
   - ✅ Include Commanders
   - ✅ Include Tokens
4. Click **Parse & Match**

### Step 2: Resolve Ambiguities

- Cards with multiple variants will be highlighted
- Click **Resolve** to choose the specific version you want
- Options include different sets, foil variants, and special editions

### Step 3: Review & Export

- Review matched cards and pricing summary
- Click **Export CSV** to generate your order file
- Import the CSV to USEA MTG Proxy for ordering

### Supported Decklist Formats

The parser supports various formats including:

```
# Standard format with quantities
4 Lightning Bolt
1 Black Lotus

# With set codes
4 Lightning Bolt (M11)
1 Black Lotus (LEA)

# With collector numbers
4 Lightning Bolt (M11 148)

# Sideboard markers
SIDEBOARD:
3 Thoughtseize

# MTGO format
SB: 3 Thoughtseize

# Commander section (after blank line following sideboard)
1 Commander Name
```

## Project Structure

```
src/
├── commonMain/kotlin/        # Platform-agnostic business logic
│   ├── catalog/              # Catalog data source abstraction
│   ├── deck/                 # Decklist parser
│   ├── match/                # Card matching algorithms
│   ├── model/                # Data models
│   ├── state/                # State management
│   ├── ui/                   # UI components
│   └── util/                 # Utilities
└── desktopMain/kotlin/       # Desktop-specific implementations
    ├── app/                  # Application entry point
    ├── catalog/              # Catalog data sources (Remote, Database)
    ├── export/               # CSV export
    ├── persistence/          # File-based storage
    └── platform/             # Platform services
```

### Catalog Data Source Architecture

The catalog system uses a pluggable architecture that allows swapping between different data sources:

- **Current**: `RemoteCatalogDataSource` - Fetches from USEA HTTP/CSV endpoints
- **Future**: `DatabaseCatalogDataSource` - Template for database integration
- **Testing**: Mock data sources for unit testing

For details on implementing a database backend, see:
- [Catalog Data Source Architecture](docs/CATALOG_DATA_SOURCE.md)
- [Quick Start: Database Integration](docs/QUICK_START_DATABASE.md)

## Platform Support

### Desktop (Primary)

Full-featured desktop application with:
- ✅ Complete catalog fetching from USEA
- ✅ File system access for imports/exports
- ✅ Native file dialogs
- ✅ Window management

### iOS (Experimental)

iOS app with wizard-style workflow:
- ✅ MVI architecture with SQLDelight persistence
- ✅ Pixel-style retro UI design
- ✅ Decklist parsing and card matching
- ✅ Clipboard export
- ⚠️ Uses cached catalog data (no live fetching)
- ⚠️ Limited platform integration

See [iOS Implementation Guide](docs/IOS_IMPLEMENTATION.md) for details.

### State Management - MVI Architecture

The project includes a **new MVI (Model-View-Intent) unidirectional architecture** that uses the database as the single source of truth:

- **ViewState**: Immutable UI state derived from database flows and local UI state
- **ViewIntent**: Sealed class representing all possible user actions
- **ViewEffect**: One-time side effects (toasts, navigation, dialogs)
- **Database**: SQLDelight database stores catalog, preferences, imports, and logs
- **Reactive Flows**: Database changes automatically propagate to UI

**Data Flow**: Remote API → Database → ViewModel (Flows) → UI

The MVI architecture provides:
- ✅ State persists across app restarts (database-backed)
- ✅ Reactive UI updates via Kotlin Flows
- ✅ Unidirectional data flow (easy to debug)
- ✅ Platform-agnostic design (works across Desktop, iOS, Android)
- ✅ Testable architecture with mock services

For details, see:
- [MVI Architecture Documentation](docs/MVI_ARCHITECTURE.md)

**Note**: Both `MainStore` (legacy) and `MviViewModel` (new) coexist in the codebase. The MVI implementation is the recommended approach for new features.

## Data Storage

Application data is stored in the `data/` directory:

- `catalog.json` - Cached card catalog from USEA
- `preferences.json` - User preferences
- `saved-imports.json` - Import history

## Matching Algorithm

MtgPirate uses an intelligent matching system:

1. **Normalization** - Card names are normalized (removing punctuation, lowercase)
2. **Exact Match** - First attempts exact name match
3. **Set Code Hints** - Uses provided set codes to narrow options
4. **Fuzzy Matching** - Levenshtein distance for typos and variations
5. **Variant Selection** - Auto-selects cheapest Regular variant when unique
6. **Manual Resolution** - User resolves ambiguous matches

## Export Format

The exported CSV includes:

```csv
Card Name,Set,SKU,Card Type,Quantity,Base Price
Lightning Bolt,M11,XMC00123,Regular,4,2.20
Black Lotus,LEA,XMC00456,Regular,1,2.20

--- Summary ---
Regular Cards,5
Holo Cards,0
Foil Cards,0
Total Price,10.80
```

## Configuration

### Catalog Update

The catalog is automatically fetched from USEA on first run. To force a refresh:

1. Open the Catalog window from the menu
2. Click **Refresh Catalog**

### Preferences

Access preferences via the settings menu to configure:

- Default include/exclude options
- Theme settings (coming soon)
- Export directory defaults

## Troubleshooting

### Catalog won't load

- Check internet connection
- Verify USEA website is accessible
- Try clearing `data/catalog.json` and restarting

### Cards not matching

- Check spelling in your decklist
- Add set codes in parentheses: `Card Name (SET)`
- Use the manual resolution UI for ambiguous matches

### Export fails

- Ensure you have write permissions in the export directory
- Check that all cards are resolved (no red highlights)

## Acknowledgments

- Card data sourced from [USEA MTG Proxy](https://www.usmtgproxy.com/)
- Built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)

## Documentation

- [MVI Architecture](docs/MVI_ARCHITECTURE.md) - State management and reactive design
- [iOS Implementation](docs/IOS_IMPLEMENTATION.md) - iOS platform guide and limitations
- [Catalog Data Source](docs/CATALOG_DATA_SOURCE.md) - Pluggable catalog architecture
- [Database Quick Start](docs/QUICK_START_DATABASE.md) - Database integration guide

## Disclaimer

This tool is for personal use with proxy card services. Magic: The Gathering is trademarked by Wizards of the Coast. This project is not affiliated with or endorsed by Wizards of the Coast.

