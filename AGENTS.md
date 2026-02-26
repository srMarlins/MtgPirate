# AGENTS.md

## Project Overview

MtgPirate is a Kotlin Multiplatform (Desktop + iOS) application for Magic: The Gathering proxy card ordering. It parses decklists, matches cards against the USEA proxy catalog using fuzzy matching, resolves ambiguities, and exports CSV orders.

**Stack:** Kotlin 2.x, Compose Multiplatform, SQLDelight, Ktor, Coil 3
**Architecture:** MVI (Model-View-Intent) with SQLDelight as single source of truth
**Platforms:** Desktop (Windows/macOS/Linux), iOS (experimental)

## Build & Test

```bash
# Build
./gradlew build

# Run desktop app
./gradlew run

# Run all tests
./gradlew allTests

# Lint (detekt)
./gradlew detekt

# Package installers
./gradlew packageExe       # Windows
./gradlew packageDmg       # macOS
./gradlew packageDeb       # Linux
```

## Project Structure

```
src/
  commonMain/kotlin/         # Shared multiplatform code
    catalog/                 # Catalog data sources, parsers, Scryfall API
    database/                # SQLDelight stores and entity mappers
    deck/                    # Decklist parsing (MTGO, Arena, MTGGoldfish)
    match/                   # Fuzzy matching engine (Levenshtein distance)
    model/                   # Domain models (CardVariant, Catalog, DeckEntry)
    state/                   # MVI ViewModel, ViewState, ViewIntent, ViewEffect
    ui/                      # Compose screens and Pixel Design System
    util/                    # Logging, pricing, promotions
  commonTest/kotlin/         # Shared tests
  desktopMain/kotlin/        # Desktop JVM (SQLite JDBC, file I/O, window management)
  iosMain/kotlin/            # iOS Native (haptics, clipboard, stubs)
  commonMain/sqldelight/     # SQL schema files (.sq)
build.gradle.kts             # Gradle build with KMP + Compose
gradle/libs.versions.toml   # Version catalog (all dependency versions here)
detekt.yml                   # Static analysis configuration
compose-desktop.pro          # ProGuard rules for release builds
```

## Code Style

- Kotlin with Compose Multiplatform conventions
- Composable functions use PascalCase (e.g., `CatalogScreen`)
- State management follows MVI: ViewIntent -> ViewModel -> ViewState -> UI
- Platform-specific code uses expect/actual pattern
- Named constants for magic numbers and colors (defined in Theme.kt)
- No wildcard imports

## Git Workflow

- Branch naming: `feature/<name>`, `fix/<name>`, `refactor/<name>`
- Commit messages: concise, describe the "why" not the "what"
- All changes go through pull requests targeting `main`
- Link PRs to GitHub issues with `Closes #<number>`
- Small, focused PRs preferred over large omnibus changes

## Testing

- Test framework: kotlin-test with kotlinx-coroutines-test
- Tests live in `src/commonTest/kotlin/` mirroring the main source structure
- Critical paths to test: decklist parsing, card matching, price calculations
- Run `./gradlew allTests` before submitting changes

## Key Domain Concepts

- **Catalog**: The USEA proxy card catalog (CSV from usmtgproxy.com)
- **DeckEntry**: A parsed line from a decklist (card name, quantity, section)
- **CardVariant**: A specific card in the catalog (name, set, SKU, type, price)
- **MatchCandidate**: A potential match between a DeckEntry and CardVariant
- **Matching pipeline**: normalize -> exact -> set-filtered -> case-insensitive -> fuzzy (Levenshtein)
- **Variant types**: Regular ($2.20), Holo ($3.00), Foil ($3.50)

## Security Notes

- Never commit API keys or credentials
- CSV export must escape fields to prevent formula injection
- HTTP User-Agent should identify the app honestly
- UIPasteboard (iOS) must be accessed on the main thread
