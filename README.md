# MTG PIRATE

A Kotlin Multiplatform application for importing Magic: The Gathering decklists, matching cards against the USEA MTG Proxy catalog, and exporting optimized CSV orders.

**Platforms:** Desktop (primary) • iOS (experimental)

<div align="center">
  <img src="https://github.com/user-attachments/assets/2f6e4147-6a47-4de0-8e95-25e556a46ab9" alt="MtgPirate Demo" style="max-width: 70%;">
</div>

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [How to Use](#how-to-use)
- [Development](#development)
- [Architecture](#architecture)
- [Platform Support](#platform-support)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Documentation](#documentation)

## Features

- 📋 **Decklist Import** - Paste decklists from various formats (MTGO, Arena, MTGGoldfish)
- 🎯 **Intelligent Matching** - Fuzzy matching with set codes and collector numbers
- 💰 **Price Calculation** - Real-time pricing from USEA MTG Proxy catalog
- 🔍 **Ambiguity Resolution** - Interactive UI for multiple card variants
- 📊 **CSV Export** - Ready-to-order CSV files with aggregated quantities
- 💾 **Import History** - Save and reload previous imports
- 🎨 **Dark Theme** - Modern Compose UI with dark mode
- ⚙️ **Flexible Options** - Include/exclude sideboard, commanders, and tokens
- 📱 **iOS Support** - Full native iOS app with Compose Multiplatform

## Platforms

- **Desktop**: macOS (Intel & Apple Silicon), Windows, Linux
- **iOS**: iPhone and iPad (iOS 14+)
- **Coming Soon**: Android

## Discalimer
- **Entirely coded with agentic ai** - This is just a hobby project to test the bounds of cross platform engineering using agentic ai.

## Tech Stack

- **Kotlin Multiplatform** - Cross-platform codebase (Desktop + iOS)
- **Compose Multiplatform** - Modern declarative UI framework
- **Kotlin Coroutines** - Async operations and state management
- **Kotlin Serialization** - JSON persistence
- **KSoup** - HTML parsing for catalog fetching
- **SQLDelight** - Type-safe SQL database (MVI architecture)

## Architecture

MtgPirate implements two state management approaches:

1. **MainStore (Legacy)** - In-memory state with manual persistence
2. **MVI (Model-View-Intent)** - Database-backed reactive architecture ✨ *Recommended*

The **MVI architecture** provides:
- 🗄️ Database as source of truth
- 🔄 Reactive UI via Kotlin Flows
- 🎯 Unidirectional data flow
- 🧪 Testable with mock services
- 🔌 Platform-agnostic (Desktop, iOS, Android)

📖 See [MVI Architecture Documentation](docs/MVI_ARCHITECTURE.md) for details.

## How to Use

### Import Your Decklist

1. Launch the application
2. Paste your decklist in the text area
3. Configure options (sideboard, commanders, tokens)
4. Click **Parse & Match**

### Resolve Ambiguities

- Cards with multiple variants are highlighted
- Click **Resolve** to choose the specific version
- Select from different sets, foil variants, or special editions

### Review & Export

- Review matched cards and pricing summary
- Click **Export CSV** to generate your order file
- Import to USEA MTG Proxy for ordering

### Supported Decklist Formats

```
# Standard format
4 Lightning Bolt
1 Black Lotus

# With set codes
4 Lightning Bolt (M11)

# With collector numbers
4 Lightning Bolt (M11 148)

# Sideboard markers
SIDEBOARD:
3 Thoughtseize
# or
SB: 3 Thoughtseize

# Commander (after sideboard)
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

### Catalog Data Sources

The catalog system supports pluggable data sources:
- **RemoteCatalogDataSource** (current) - Fetches from USEA HTTP/CSV
- **DatabaseCatalogDataSource** (template) - For database integration
- **Mock sources** - For unit testing

📖 See [Catalog Data Source Architecture](CATALOG_DATA_SOURCE.md)

## Platform Support

### Desktop (Primary)

Full-featured desktop application:
- ✅ Complete catalog fetching from USEA
- ✅ File system access for imports/exports
- ✅ Native file dialogs
- ✅ Window management

**Running the Desktop App:**
```bash
./gradlew run
```

**Building Distributables:**
```bash
./gradlew packageDmg  # macOS
./gradlew packageExe  # Windows
```

### iOS (Experimental)

Wizard-style iOS app:
- ✅ MVI architecture with SQLDelight
- ✅ Pixel-style retro UI
- ✅ Decklist parsing and matching
- ✅ Clipboard export
- ⚠️ Uses cached catalog (no live fetching)
- ⚠️ Limited platform integration

**Running the iOS App:**

The iOS app is located in the `mtgPirate/` directory.

1. Build the framework:
   ```bash
   ./gradlew linkDebugFrameworkIosSimulatorArm64
   ```
2. Open Xcode:
   ```bash
   open mtgPirate/mtgPirate.xcodeproj
   ```
3. Follow setup instructions in `mtgPirate/README_XCODE_SETUP.md`
4. Click Run in Xcode

**Note**: iOS apps cannot be run from IntelliJ - they require Xcode to run on simulators or devices.

📖 See [iOS Implementation Guide](docs/IOS_IMPLEMENTATION.md).

## Matching Algorithm

Intelligent matching system:
1. **Normalization** - Remove punctuation, lowercase
2. **Exact Match** - Attempt exact name match first
3. **Set Code Hints** - Narrow options using provided set codes
4. **Fuzzy Matching** - Levenshtein distance for typos
5. **Auto-Selection** - Choose cheapest Regular variant when unique
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

## Acknowledgments

- Card data sourced from [USEA MTG Proxy](https://www.usmtgproxy.com/)
- Built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)

## Documentation

- [MVI Architecture](docs/MVI_ARCHITECTURE.md) - State management and reactive design
- [iOS Implementation](docs/IOS_IMPLEMENTATION.md) - iOS platform guide and limitations
- [Catalog Data Source](docs/CATALOG_DATA_SOURCE.md) - Pluggable catalog architecture
- [Database Quick Start](docs/QUICK_START_DATABASE.md) - Database integration guide

## Disclaimer

This tool is for personal use with USEA proxy card services. Magic: The Gathering is trademarked by Wizards of the Coast. This project is not affiliated with or endorsed by Wizards of the Coast.

