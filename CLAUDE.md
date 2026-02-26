# MtgPirate - Claude Development Guide

## Project Overview

MtgPirate is a **Kotlin Multiplatform** (Desktop + iOS) application for Magic: The Gathering proxy card ordering. It parses decklists, matches cards against the USEA proxy catalog, resolves ambiguities, and exports CSV orders.

**Tech Stack:** Kotlin 2.x, Compose Multiplatform, SQLDelight, Ktor, Coil 3, Navigation Compose
**Build:** Gradle 8.x with version catalog (`gradle/libs.versions.toml`)
**Architecture:** MVI (Model-View-Intent) with SQLDelight as single source of truth

## Development Workflow

### Git Worktrees (Required for Feature Work)

All feature development MUST use git worktrees for isolation:

```bash
# Create worktree for a feature
git worktree add ../MtgPirate-<feature-name> -b feature/<feature-name>

# When done, clean up
git worktree remove ../MtgPirate-<feature-name>
```

- Never work directly on `main` for features or refactoring
- Each worktree gets its own branch: `feature/<name>`, `fix/<name>`, or `refactor/<name>`
- Clean up worktrees after PRs are merged

### GitHub Issues (Required Before Starting Work)

**Before starting ANY work, create a GitHub issue first:**

```bash
# Create an issue for the work
gh issue create --title "Brief description" --body "Details of the change"

# For simple/mechanical tasks, add the 'jules' label so Google Jules agents can pick them up
gh issue create --title "Brief description" --body "Details" --label "jules"
```

**Labeling guidelines:**
- **Tag with `jules`**: Simple, mechanical, low-risk tasks (dependency bumps, adding missing imports, formatting fixes, adding basic tests for existing code, documentation updates, simple lint fixes)
- **Do NOT tag with `jules`**: Architectural changes, complex refactoring, business logic changes, matching algorithm work, UI redesigns, database migrations, multi-file coordinated changes

### Pull Requests (Required for All Changes)

Every change goes through a PR:

```bash
# Push branch and create PR
git push -u origin feature/<name>
gh pr create --title "Short title" --body "## Summary\n- What changed\n\n## Test plan\n- How it was tested"
```

- Link PRs to their GitHub issues: `Closes #<issue-number>`
- PRs must have a summary and test plan
- Prefer small, focused PRs over large omnibus changes

### Testing Your Work

**Desktop (Primary - always test here):**
```bash
./gradlew run
```
The app launches as a desktop window. Test the full workflow: paste decklist -> match -> resolve -> export.

**Android Emulator (when Android support is added):**
```bash
./gradlew :composeApp:installDebug
# Or run from Android Studio with an emulator
```

**iOS (requires macOS + Xcode):**
```bash
./gradlew iosSimulatorArm64Test
# Or open mtgPirate/mtgPirate.xcodeproj in Xcode
```

**Lint/Static Analysis:**
```bash
./gradlew detekt          # Kotlin linter
./gradlew build           # Full build check
```

### Parallel Subagents

Use subagents for independent work streams:
- Code review and analysis tasks
- Independent refactoring across different modules
- Running builds/tests while working on other changes
- Exploring the codebase for patterns across files

When multiple issues can be worked on independently, dispatch parallel subagent worktrees.

## Project Structure

```
src/
  commonMain/kotlin/     # Shared KMP code (models, matching, state, UI)
    catalog/             # Data source abstraction, parsing, Scryfall API
    database/            # SQLDelight stores and mappers
    deck/                # Decklist parsing (MTGO, Arena, MTGGoldfish)
    match/               # Fuzzy matching engine (Levenshtein)
    model/               # Domain models (CardVariant, Catalog, DeckEntry)
    state/               # MVI ViewModel, ViewState, ViewIntent, ViewEffect
    ui/                  # Compose screens and Pixel Design System components
    util/                # Logging, pricing, promotions
  desktopMain/kotlin/    # Desktop-specific (JVM, SQLite JDBC, file I/O)
  iosMain/kotlin/        # iOS-specific (Native, haptics, stubs)
```

## Key Architecture Decisions

- **MVI pattern**: All state flows through `MviViewModel` -> single `ViewState` -> Compose UI
- **SQLDelight**: Type-safe SQL, schema in `src/commonMain/sqldelight/database/*.sq`
- **Platform services**: `MviPlatformServices` interface abstracts platform-specific I/O
- **Pixel Design System**: Custom retro RPG aesthetic with chamfered corners, glow effects, scanlines

## Catalog & Matching

- **Catalog source**: USEA proxy catalog at `usmtgproxy.com/wp-content/uploads/single-card-list.csv`
- **Reference implementation**: `Z:\User\Playground\mtg-order` has a Python CLI tool with proven matching logic
- **Matching pipeline**: Normalize -> exact match -> set-filtered -> case-insensitive -> fuzzy (Levenshtein) -> manual resolve
- **Pricing tiers**: Regular $2.20, Holo $3.00, Foil $3.50, Metal $5.00
- **Volume discounts**: $60+ (5%), $100+ (15%), $160+ (25%), $200+ (30%), $300+ (35%), $400+ (50%)

## Code Quality

- **Detekt**: Configured in `detekt.yml`, max issues = 0 (strict), run with `./gradlew detekt`
- **ProGuard**: Release builds use `compose-desktop.pro` for minification
- **No tests yet**: Adding tests is a priority - use `src/commonTest/`, `src/desktopTest/`

## Common Commands

```bash
./gradlew run              # Run desktop app
./gradlew build            # Full build
./gradlew detekt           # Lint check
./gradlew allTests         # Run all tests
./gradlew packageExe       # Windows installer
./gradlew packageDmg       # macOS installer
```

## CI/CD

- **CI**: GitHub Actions on push/PR to main - builds desktop (Ubuntu) + iOS framework (macOS)
- **Release**: Triggered by GitHub release - builds Windows .exe, macOS .dmg, iOS .ipa
