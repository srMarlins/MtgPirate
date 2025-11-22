# 🏴‍☠️ MTG PIRATE

> _A Kotlin Multiplatform application for importing Magic: The Gathering decklists, intelligently matching cards against the USEA MTG Proxy catalog, and exporting optimized CSV orders._

**Platforms:** Desktop (Windows • macOS • Linux) • iOS (iPhone • iPad)

<div align="center">
  <img src="https://github.com/user-attachments/assets/2f6e4147-6a47-4de0-8e95-25e556a46ab9" alt="MtgPirate Demo" style="max-width: 70%;">
</div>

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.9.3-blue.svg)](https://www.jetbrains.com/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 📋 Table of Contents

- [✨ Features](#-features)
- [🚀 Quick Start](#-quick-start)
- [🎯 How It Works](#-how-it-works)
- [💻 Installation](#-installation)
- [🔧 Building From Source](#-building-from-source)
- [📱 Platform Support](#-platform-support)
- [🏗️ Architecture](#️-architecture)
- [🧩 Project Structure](#-project-structure)
- [🎨 Design System](#-design-system)
- [🧪 Testing](#-testing)
- [🤝 Contributing](#-contributing)
- [📚 Documentation](#-documentation)
- [⚖️ License](#️-license)

---

## ✨ Features

### Core Functionality
- 📋 **Multi-Format Decklist Import** - Supports MTGO, Arena, MTGGoldfish, and custom formats
- 🎯 **Intelligent Card Matching** - Advanced fuzzy matching with Levenshtein distance algorithm
- 🔍 **Set Code & Collector Number Support** - Precise matching with optional set hints
- 💰 **Real-Time Pricing** - Automatic price calculation from USEA MTG Proxy catalog
- 🔀 **Ambiguity Resolution** - Interactive UI for selecting from multiple card variants
- 📊 **Smart CSV Export** - Aggregates quantities, deduplicates, generates order-ready files
- 💾 **Import History** - Save, name, and reload previous imports
- ⚙️ **Flexible Preferences** - Toggle sideboard, commanders, tokens inclusion

### User Experience
- 🎨 **Pixel-Perfect Retro UI** - Custom "Pixel Design System" with chamfered corners and glowing borders
- 🌓 **Dark/Light Themes** - Fully themed interface with instant switching
- ✨ **Magical Animations** - Scanlines, sparkles, pulsing buttons, and smooth transitions
- 📱 **Wizard-Style Flow (iOS)** - Step-by-step mobile workflow with bottom navigation
- 🖱️ **Custom Title Bar (Desktop)** - Unified window controls and navigation
- 🔄 **Reactive State Updates** - Instant UI updates via Kotlin Flows

### Technical Features
- 🗄️ **SQLDelight Database** - Type-safe persistence with reactive queries
- 🔌 **Pluggable Data Sources** - Abstracted catalog loading (Remote, Database, Mock)
- 🌐 **Scryfall Integration** - Automatic card image enrichment
- 📦 **Ktor HTTP Client** - Multiplatform networking
- 🧬 **MVI Architecture** - Unidirectional data flow with database as source of truth
- 🎮 **Haptic Feedback (iOS)** - Native tactile responses
- 📋 **Clipboard Support** - Copy CSV output on platforms without file system

---

## 🚀 Quick Start

### Desktop (All Platforms)

```bash
# Clone the repository
git clone https://github.com/srMarlins/MtgPirate.git
cd MtgPirate

# Run the application
./gradlew run
```

### iOS

```bash
# Build the iOS framework
./gradlew linkDebugFrameworkIosSimulatorArm64

# Open Xcode project
open mtgPirate/mtgPirate.xcodeproj

# Click Run in Xcode (⌘R)
```

---

## 🎯 How It Works

### 1. Import Your Decklist

Paste your decklist from any source:

```
4 Lightning Bolt (M11)
3 Brainstorm (EMA 40)
1 Black Lotus

SIDEBOARD:
3 Thoughtseize
```

Supported formats:
- **Standard**: `4 Lightning Bolt`
- **With Set Code**: `4 Lightning Bolt (M11)`
- **With Collector Number**: `4 Lightning Bolt (M11 148)`
- **Sideboard Markers**: `SIDEBOARD:` or `SB:`
- **Commander Section**: Cards after a blank line following sideboard
- **HTML/Rich Text**: Automatically strips HTML tags and entities

### 2. Configure Preferences

- ✅ **Include Sideboard** - Add sideboard cards to export
- ✅ **Include Commanders** - Add commander cards to export
- ✅ **Include Tokens** - Add token cards to export
- 🎲 **Variant Priority** - Prefer Foil, Holo, or Regular
- 🃏 **Set Priority** - Prefer specific sets (e.g., Alpha, Beta)
- 🔍 **Fuzzy Matching** - Enable/disable typo tolerance

### 3. Match Cards

The matching engine uses a multi-stage algorithm:

1. **Normalization**: `"Juzám Djinn"` → `"juzam djinn"`
2. **Exact Match**: Direct name lookup in catalog
3. **Set Code Filtering**: Narrows results by `(M11)` hints
4. **Case-Insensitive Match**: Handles capitalization differences
5. **Fuzzy Matching**: Levenshtein distance ≤ 2 for typos
6. **Auto-Selection**: Chooses cheapest Regular variant if unique
7. **Manual Resolution**: User disambiguates when multiple matches exist

### 4. Resolve Ambiguities

When multiple card variants exist:
- **Exact Matches**: Different sets or foil variants
- **Fuzzy Matches**: Similar names (e.g., "Bolt" matches "Lightning Bolt")
- **Interactive Resolution**: Click to view all candidates with images, set, price
- **Bulk Resolution**: Resolve all ambiguities at once

### 5. Export CSV

Generated CSV includes:
- **Header**: `Card Name,Set,SKU,Card Type,Quantity,Base Price`
- **Aggregated Rows**: Combines identical cards across deck sections
- **Summary**: Counts by card type (Regular/Holo/Foil) and total price

Example output:
```csv
Card Name,Set,SKU,Card Type,Quantity,Base Price
Lightning Bolt,M11,XMC00123,Regular,4,2.20
Brainstorm,EMA,XMC00456,Regular,3,2.20

--- Summary ---
Regular Cards,7
Holo Cards,0
Foil Cards,0
Total Price,15.40
```

---

## 💻 Installation

### Prerequisites

- **JDK 17+** (for Kotlin/JVM)
- **Gradle 8.0+** (included via wrapper)
- **Xcode 15+** (for iOS, macOS only)

### Desktop

No installation required! Run directly:

```bash
./gradlew run
```

Or build a native package:

```bash
# macOS
./gradlew packageDmg

# Windows
./gradlew packageExe

# Linux
./gradlew packageDeb
```

Packages are output to `build/compose/binaries/main/`.

### iOS

1. Build the Kotlin framework:
   ```bash
   ./gradlew linkDebugFrameworkIosSimulatorArm64  # Simulator
   ./gradlew linkReleaseFrameworkIosArm64          # Device
   ```

2. Open `mtgPirate/mtgPirate.xcodeproj` in Xcode

3. Select target device/simulator and click Run

**Note**: iOS requires macOS and Xcode. The app cannot run from IntelliJ IDEA.

---

## 🔧 Building From Source

### Development Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/srMarlins/MtgPirate.git
   cd MtgPirate
   ```

2. **Open in IntelliJ IDEA**:
   - Install **Kotlin Multiplatform Plugin**
   - Open project (Gradle will sync automatically)

3. **Run the desktop app**:
   ```bash
   ./gradlew run
   ```

4. **Run linters**:
   ```bash
   ./gradlew detekt
   ```

### Build Tasks

| Task | Description |
|------|-------------|
| `./gradlew run` | Run desktop app |
| `./gradlew packageDmg` | Build macOS DMG |
| `./gradlew packageExe` | Build Windows EXE |
| `./gradlew packageDeb` | Build Linux DEB |
| `./gradlew detekt` | Run code quality checks |
| `./gradlew linkDebugFrameworkIosSimulatorArm64` | Build iOS framework (simulator) |
| `./gradlew linkReleaseFrameworkIosArm64` | Build iOS framework (device) |

---

## 📱 Platform Support

### Desktop (Primary Platform)

**Supported OS**:
- ✅ macOS 10.14+ (Intel & Apple Silicon)
- ✅ Windows 10/11 (x64)
- ✅ Linux (x64, Debian-based)

**Features**:
- Full catalog fetching from USEA (HTTP + CSV)
- File system access for CSV export
- Native file picker dialogs
- Custom window title bar with draggable area
- Keyboard shortcuts and native menus

**Tech Stack**:
- Kotlin/JVM (JDK 17)
- Compose for Desktop
- SQLDelight (SQLite JDBC driver)
- Ktor CIO engine

### iOS (Experimental Platform)

**Supported Devices**:
- ✅ iPhone (iOS 14+)
- ✅ iPad (iPadOS 14+)

**Features**:
- MVI architecture with SQLDelight
- Wizard-style 4-step workflow
- Bottom navigation bar
- Haptic feedback on button taps
- Clipboard export (no file system access)
- **Cached catalog only** (no live HTTP fetching)

**Limitations**:
- ⚠️ Network operations stubbed (use cached catalog)
- ⚠️ No file export (clipboard only)
- ⚠️ Time functions simplified (use static values)

**Tech Stack**:
- Kotlin/Native (Xcode 15+)
- Compose Multiplatform for iOS
- SQLDelight (Native driver)
- Ktor Darwin engine (stubbed)

📖 **[Full iOS Documentation →](docs/IOS_IMPLEMENTATION.md)**

### Android (Coming Soon)

Planned features:
- Material You design
- File picker integration
- Shared Element Transitions
- Widget support

---

## 🏗️ Architecture

MtgPirate uses **MVI (Model-View-Intent)** architecture with **SQLDelight** as the single source of truth.

### MVI Pattern

```
┌──────────────────────────────────────────────────────────┐
│                         User                             │
└────────────────┬─────────────────────────────────────────┘
                 │ 1. Interaction (Click, Type)
                 ▼
┌──────────────────────────────────────────────────────────┐
│                    UI (Compose)                          │
│  - Observes ViewState                                    │
│  - Sends ViewIntents                                     │
│  - Collects ViewEffects                                  │
└────────────────┬─────────────────────────────────────────┘
                 │ 2. ViewIntent (e.g., ParseDeck)
                 ▼
┌──────────────────────────────────────────────────────────┐
│                   MviViewModel                           │
│  - Processes intents                                     │
│  - Calls business logic                                  │
│  - Updates database                                      │
│  - Emits ViewEffects                                     │
└────────────────┬─────────────────────────────────────────┘
                 │ 3. Update Database
                 ▼
┌──────────────────────────────────────────────────────────┐
│              SQLDelight Database                         │
│  - Single source of truth                                │
│  - Reactive Flows                                        │
│  - Type-safe queries                                     │
└────────────────┬─────────────────────────────────────────┘
                 │ 4. Flow<Catalog>, Flow<Preferences>
                 ▼
┌──────────────────────────────────────────────────────────┐
│                 ViewState (Combined)                     │
│  - Catalog                                               │
│  - Matches                                               │
│  - Preferences                                           │
│  - UI flags                                              │
└────────────────┬─────────────────────────────────────────┘
                 │ 5. StateFlow<ViewState>
                 ▼
┌──────────────────────────────────────────────────────────┐
│              UI Re-renders (Compose)                     │
└──────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Responsibility |
|-----------|----------------|
| **ViewState** | Immutable UI state (catalog, matches, preferences) |
| **ViewIntent** | User actions (ParseDeck, ExportCsv, ResolveCard) |
| **ViewEffect** | One-time events (ShowToast, NavigateToResults) |
| **MviViewModel** | Intent processor, database updater, state emitter |
| **Database** | SQLDelight reactive database (CardVariant, Preferences, SavedImport) |
| **Platform Services** | Platform-specific operations (HTTP, file I/O, clipboard) |

### Benefits

- ✅ **Predictable**: Unidirectional data flow
- ✅ **Testable**: Easy to mock dependencies
- ✅ **Reactive**: Automatic UI updates
- ✅ **Persistent**: State survives app restarts
- ✅ **Platform-agnostic**: Works on Desktop, iOS, Android

📖 **[Full MVI Documentation →](docs/MVI_ARCHITECTURE.md)**

---

## 🧩 Project Structure

```
MtgPirate/
├── src/
│   ├── commonMain/kotlin/           # Shared business logic (12,500+ LOC)
│   │   ├── catalog/                 # Catalog data sources
│   │   │   ├── CatalogDataSource.kt        # Abstraction interface
│   │   │   ├── CatalogParser.kt            # HTML/CSV parser
│   │   │   ├── CatalogCsvParser.kt         # CSV-specific parser
│   │   │   ├── KtorRemoteCatalogDataSource.kt  # HTTP fetcher
│   │   │   ├── ScryfallApi.kt              # Scryfall API client
│   │   │   └── ScryfallImageEnricher.kt    # Image URL enrichment
│   │   ├── database/                # SQLDelight database
│   │   │   ├── Database.kt                 # Database facade
│   │   │   ├── CatalogStore.kt             # Catalog CRUD
│   │   │   ├── ImportsStore.kt             # Import history CRUD
│   │   │   └── EntityMappers.kt            # Domain ↔ Entity
│   │   ├── deck/                    # Decklist parsing
│   │   │   └── DecklistParser.kt           # Multi-format parser
│   │   ├── match/                   # Matching algorithms
│   │   │   ├── Matcher.kt                  # Main matching engine
│   │   │   ├── Levenshtein.kt              # Edit distance algorithm
│   │   │   └── NameNormalizer.kt           # Name normalization
│   │   ├── model/                   # Domain models
│   │   │   └── Models.kt                   # CardVariant, Catalog, DeckEntry, etc.
│   │   ├── state/                   # MVI ViewModel
│   │   │   └── MviViewModel.kt             # State management
│   │   ├── ui/                      # Compose UI components
│   │   │   ├── CatalogScreen.kt            # Catalog management
│   │   │   ├── MatchesScreen.kt            # Decklist input
│   │   │   ├── ResolveScreen.kt            # Ambiguity resolution
│   │   │   ├── ResultsScreen.kt            # Match results
│   │   │   ├── ExportScreen.kt             # CSV export
│   │   │   ├── SavedImportsScreen.kt       # Import history
│   │   │   ├── PreferencesWizardScreen.kt  # Settings
│   │   │   ├── PixelComponents.kt          # Pixel design system
│   │   │   ├── MobilePixelImageComponents.kt  # Mobile image cards
│   │   │   ├── StepperComponent.kt         # Wizard stepper
│   │   │   ├── Theme.kt                    # Color/typography
│   │   │   └── PlatformUI.kt               # Platform expect/actual
│   │   └── util/                    # Utilities
│   │       ├── Logging.kt                  # Logging abstraction
│   │       ├── Price.kt                    # Price formatting
│   │       └── Promotions.kt               # Promotional logic
│   │
│   ├── commonMain/sqldelight/       # SQL schemas
│   │   └── database/
│   │       ├── CardVariant.sq              # Catalog table
│   │       ├── Preferences.sq              # User settings
│   │       ├── SavedImport.sq              # Import history
│   │       └── LogEntry.sq                 # Debug logs
│   │
│   ├── desktopMain/kotlin/          # Desktop-specific (JVM)
│   │   ├── app/
│   │   │   └── Main.kt                     # Desktop entry point
│   │   ├── catalog/
│   │   │   ├── CatalogFetcher.kt           # Facade for catalog loading
│   │   │   ├── RemoteCatalogDataSource.kt  # HTTP + cache implementation
│   │   │   └── DatabaseCatalogDataSource.kt  # Template for DB source
│   │   ├── database/
│   │   │   └── DatabaseDriverFactory.kt    # SQLite JDBC driver
│   │   ├── export/
│   │   │   └── CsvExporter.kt              # CSV file writer
│   │   ├── persistence/
│   │   │   ├── ImportsStore.kt             # JSON import storage
│   │   │   └── PreferencesStore.kt         # JSON preferences
│   │   ├── platform/
│   │   │   ├── AppDirectories.kt           # File paths
│   │   │   ├── DesktopMviPlatformServices.kt  # Desktop services
│   │   │   └── PlatformUtils.kt            # Desktop utilities
│   │   └── ui/
│   │       ├── DesktopResolveScreen.kt     # Desktop-specific resolve
│   │       ├── DesktopSavedImportsDialog.kt  # Import dialog
│   │       ├── PixelImageComponents.kt     # Desktop image cards
│   │       └── PlatformUI.kt               # Desktop UI actual
│   │
│   └── iosMain/kotlin/              # iOS-specific (Kotlin/Native)
│       ├── app/
│       │   ├── Main.kt                     # iOS entry point
│       │   ├── IosScreens.kt               # iOS wizard screens
│       │   ├── IosCompactStepper.kt        # Mobile stepper
│       │   └── IosExportHelpers.kt         # CSV export helpers
│       ├── database/
│       │   └── DatabaseDriverFactory.kt    # SQLite Native driver
│       ├── platform/
│       │   ├── IosMviPlatformServices.kt   # iOS services (stubbed)
│       │   ├── IosHapticFeedback.kt        # Haptic feedback
│       │   └── PlatformUtils.kt            # iOS utilities
│       └── ui/
│           ├── IosSavedImportsDialog.kt    # iOS import dialog
│           ├── MobileResultsScreen.kt      # iOS results
│           ├── IosMobileWrappers.kt        # iOS UI wrappers
│           ├── ModernIosReorderableListHybrid.kt  # Reorderable list
│           └── PlatformUI.kt               # iOS UI actual
│
├── mtgPirate/                       # iOS Xcode project
│   ├── mtgPirate.xcodeproj/
│   └── mtgPirate/
│       ├── mtgPirateApp.swift              # Swift app entry
│       └── ContentView.swift               # SwiftUI wrapper
│
├── docs/                            # Documentation
│   ├── MVI_ARCHITECTURE.md                 # MVI pattern guide
│   ├── IOS_IMPLEMENTATION.md               # iOS platform guide
│   ├── CATALOG_DATA_SOURCE.md              # Data source architecture
│   └── PIXEL_DESIGN_SYSTEM.md              # Design system spec
│
├── build.gradle.kts                 # Build configuration
├── gradle.properties                # Gradle settings
├── settings.gradle.kts              # Project settings
├── detekt.yml                       # Detekt linter config
├── detekt-baseline.xml              # Detekt baseline
├── qodana.yaml                      # Qodana config
├── example-input.txt                # Sample decklist
├── example-output.csv               # Sample CSV output
├── CONTRIBUTING.md                  # Contribution guide
└── LICENSE                          # MIT License
```

### Key Directories

- **`commonMain/`**: Platform-agnostic Kotlin code (UI, logic, models)
- **`desktopMain/`**: JVM-specific implementations (file I/O, HTTP)
- **`iosMain/`**: iOS-specific implementations (haptics, clipboard)
- **`sqldelight/`**: Type-safe SQL schemas
- **`docs/`**: Architecture and implementation guides

---

## 🎨 Design System

MtgPirate uses a custom **Pixel Design System** inspired by 8-bit/16-bit RPGs.

### Philosophy: "Retro Soul, Modern Body"

High-fidelity reinterpretation of pixel art principles using vector paths and smooth animations.

### Key Principles

1. **Chamfered Corners** - Cut corners instead of rounded for pixel stepping
2. **Glowing Borders** - Magical pulsing borders with outer glow
3. **Layered Depth** - Scanlines, shadows, and borders create CRT aesthetic
4. **Precise Alignment** - Custom `pixelBorder` modifier for perfect clipping

### Components

| Component | Purpose |
|-----------|---------|
| `PixelBorderContainer` | Foundation container with chamfered borders |
| `PixelButton` | Interactive button with pulse animations |
| `PixelTextField` | Input field with block cursor and custom borders |
| `PixelCard` | Grouping container with optional glow |
| `PixelBadge` | Small status indicators (set codes, quantities) |
| `ScanlineEffect` | CRT monitor overlay effect |
| `MagicalSparkles` | Particle effects for loading states |
| `FantasyStarIcon` | Rotating 4-pointed star |

### Color Palette

| Name | Hex | Usage |
|------|-----|-------|
| **Mystical Purple** | `#B794F6` | Primary borders, active states |
| **Crystal Blue** | `#63B3ED` | Secondary accents, gradients |
| **Golden Treasure** | `#FBD38D` | Highlights, rare items |
| **Muted Grey** | `#6B7280` | Inactive borders, placeholders |
| **Error Red** | `#F44336` | Error states, alerts |

📖 **[Full Design System Spec →](docs/PIXEL_DESIGN_SYSTEM.md)**

---

## 🧪 Testing

### Unit Tests

```bash
# Run all tests
./gradlew cleanAllTests allTests

# Run iOS tests
./gradlew iosSimulatorArm64Test
```

### Manual Testing

1. **Import Test Deck**:
   ```bash
   cat example-input.txt
   ```

2. **Expected Output**:
   ```bash
   cat example-output.csv
   ```

3. **Test Cases**:
   - ✅ Standard format (`4 Lightning Bolt`)
   - ✅ Set codes (`4 Lightning Bolt (M11)`)
   - ✅ Collector numbers (`4 Lightning Bolt (M11 148)`)
   - ✅ Sideboard markers (`SIDEBOARD:`, `SB:`)
   - ✅ Commander section (blank line after sideboard)
   - ✅ HTML tags and entities (auto-stripped)
   - ✅ Fuzzy matching (typos)
   - ✅ Ambiguity resolution (multiple variants)
   - ✅ CSV aggregation (duplicate cards)

### Mocking

```kotlin
// Mock platform services for unit tests
class MockPlatformServices : MviPlatformServices {
    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog {
        return Catalog(variants = listOf(
            CardVariant("Lightning Bolt", "lightning bolt", "M11", "XMC00123", "Regular", 220)
        ))
    }
}
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

### Reporting Bugs

Open an issue with:
- Clear title and description
- Steps to reproduce
- Expected vs. actual behavior
- Screenshots/logs
- OS and version

### Suggesting Features

Open an issue to discuss before implementing. Include:
- Use case and motivation
- Proposed API/UI changes
- Platform considerations

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run linters (`./gradlew detekt`)
5. Test on all platforms
6. Commit with clear messages
7. Push and open a PR

### Code Style

- **Kotlin**: Follow [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Detekt**: All code must pass `./gradlew detekt`
- **Comments**: Document public APIs and complex logic
- **Compose**: Use `remember`, `LaunchedEffect` correctly
- **MVI**: Keep ViewState immutable, process all intents in ViewModel

📖 **[Full Contributing Guide →](CONTRIBUTING.md)**

---

## 📚 Documentation

### Core Documentation

- **[MVI Architecture](docs/MVI_ARCHITECTURE.md)** - State management pattern, ViewState, ViewIntent, ViewEffect
- **[iOS Implementation](docs/IOS_IMPLEMENTATION.md)** - iOS platform guide, limitations, building, testing
- **[Catalog Data Source](docs/CATALOG_DATA_SOURCE.md)** - Pluggable data source architecture, custom implementations
- **[Pixel Design System](docs/PIXEL_DESIGN_SYSTEM.md)** - Design tokens, components, implementation

### Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| **Language** | Kotlin | 2.2.21 |
| **UI Framework** | Compose Multiplatform | 1.9.3 |
| **Database** | SQLDelight | 2.2.1 |
| **HTTP Client** | Ktor | 3.3.2 |
| **Serialization** | Kotlinx Serialization | 1.9.0 |
| **HTML Parsing** | KSoup | 0.2.5 |
| **Coroutines** | Kotlinx Coroutines | 1.10.2 |
| **Navigation** | Compose Navigation | 2.9.1 |
| **Image Loading** | Coil 3 | 3.3.0 |
| **Linter** | Detekt | 1.23.8 |

### External Resources

- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [Ktor](https://ktor.io/)
- [USEA MTG Proxy](https://www.usmtgproxy.com/)
- [Scryfall API](https://scryfall.com/docs/api)

---

## ⚖️ License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

### Copyright

Copyright © 2024 MtgPirate Contributors

### Third-Party Acknowledgments

- **Card Data**: Sourced from [USEA MTG Proxy](https://www.usmtgproxy.com/)
- **Card Images**: Provided by [Scryfall](https://scryfall.com/) API
- **Framework**: Built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) by JetBrains

### Disclaimer

This tool is for **personal use** with USEA proxy card services. **Magic: The Gathering** is a trademark of **Wizards of the Coast LLC**, a subsidiary of Hasbro, Inc. This project is **not affiliated with, endorsed by, or sponsored by** Wizards of the Coast or Hasbro.

### AI Disclosure

> **Entirely coded with agentic AI** - This is a hobby project to explore the boundaries of cross-platform engineering using agentic AI tools.

---

<div align="center">
  
**Made with ❤️ and 🤖 by the MtgPirate Team**

[Report Bug](https://github.com/srMarlins/MtgPirate/issues) • [Request Feature](https://github.com/srMarlins/MtgPirate/issues) • [Documentation](docs/)

</div>

