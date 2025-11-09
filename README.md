<h1>MtgPirate<img src="src/desktopMain/resources/scimitar_logo.svg" alt="MtgPirate Logo" width="48" height="48" style="vertical-align: middle; margin: 0 0 0 -4px; padding: 0; display: inline-block;"></h1>

A Kotlin Multiplatform desktop application for importing Magic: The Gathering decklists, matching cards against the USEA MTG Proxy catalog, and exporting optimized CSV orders.

<!-- Once you record a demo GIF, uncomment the line below -->
<!-- ![App Demo](./demo.gif) -->
<!-- *Demo of MtgPirate in action* -->

> 💡 **Quick Demo**: See [DEMO-GUIDE.md](./DEMO-GUIDE.md) for instructions on creating a demo GIF of the application.

## Logo Design

The MtgPirate logo features a fantasy pirate skull wearing a captain's hat with magical elements:
- **Gradient skull** - Purple to pink gradient representing the mystical nature of Magic: The Gathering
- **Glowing cyan eyes** - Magical energy and the digital nature of the tool
- **Crossed swords** - Classic pirate symbolism
- **Sparkles & stars** - Fantasy/magical theme elements
- **Modern flat design** - Clean, simple, and professional appearance
- **Pixelated variant** - Retro pixel-art version displayed in the app's title bar

The logo is available in SVG format for scalability and can be found in `src/desktopMain/resources/`. A pixelated Compose Canvas version is rendered in the UI for a retro aesthetic.

- 📋 **Decklist Import** - Paste decklists from various formats (MTGO, Arena, MTGGoldfish, etc.)
- 🎯 **Intelligent Card Matching** - Fuzzy matching with set code hints and collector number support
- 💰 **Price Calculation** - Real-time pricing from USEA MTG Proxy catalog
- 🔍 **Ambiguity Resolution** - Interactive UI for resolving multiple card variants
- 📊 **CSV Export** - Generate ready-to-order CSV files with aggregated quantities
- 💾 **Import History** - Save and reload previous imports
- 🎨 **Dark Theme** - Eye-friendly interface with modern Compose UI
- ⚙️ **Flexible Options** - Include/exclude sideboard, commanders, and tokens

## Tech Stack

- **Kotlin Multiplatform** - Cross-platform codebase
- **Compose Desktop** - Modern declarative UI framework
- **Kotlin Coroutines** - Async operations and state management
- **Kotlin Serialization** - JSON persistence
- **KSoup** - HTML parsing for catalog fetching

## Getting Started

### Prerequisites

- JDK 17 or higher
- Gradle 8.x (included via wrapper)

### Building and Running

1. Clone the repository:
```bash
git clone <repository-url>
cd MtgPirate
```

2. Run the application:
```bash
./gradlew run
```

3. Build a distribution package:
```bash
./gradlew packageDistributionForCurrentOS
```

The packaged application will be available in `build/compose/binaries/main/`.

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
│   ├── catalog/              # Catalog parsing
│   ├── deck/                 # Decklist parser
│   ├── match/                # Card matching algorithms
│   ├── model/                # Data models
│   ├── state/                # State management
│   ├── ui/                   # UI components
│   └── util/                 # Utilities
└── desktopMain/kotlin/       # Desktop-specific implementations
    ├── app/                  # Application entry point
    ├── catalog/              # Catalog fetching
    ├── export/               # CSV export
    ├── persistence/          # File-based storage
    └── platform/             # Platform services
```

## Data Storage

Application data is stored in the `data/` directory:

- `catalog.json` - Cached card catalog from USEA
- `preferences.json` - User preferences
- `saved-imports.json` - Import history
- `image-url-mappings.json` - Card image URLs

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

## Development

### Running Tests

```bash
./gradlew test
```

### Code Style

The project follows Kotlin official code style guidelines.

### Adding Features

The architecture follows a unidirectional data flow pattern:

1. User actions → `MainIntent`
2. Intents processed by → `MainStore`
3. State updates → `MainState`
4. UI reacts to state changes

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

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

[Add your license here]

## Acknowledgments

- Card data sourced from [USEA MTG Proxy](https://www.usmtgproxy.com/)
- Built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)

## Disclaimer

This tool is for personal use with proxy card services. Magic: The Gathering is trademarked by Wizards of the Coast. This project is not affiliated with or endorsed by Wizards of the Coast.

