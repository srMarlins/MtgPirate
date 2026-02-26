# AGENTS.md

## Build & Test

```bash
./gradlew build            # Compile
./gradlew detekt           # Static analysis
./gradlew allTests         # Unit tests
./gradlew run              # Run desktop app
```

Run all four before submitting changes.

## Code Style

- No wildcard imports
- Composable functions use PascalCase
- Named constants for magic numbers and colors (defined in Theme.kt)
- All dependency versions in `gradle/libs.versions.toml` — never hardcode versions in build.gradle.kts
- Platform-specific code uses Kotlin expect/actual pattern

## Git Workflow

- Branch from `main`: `feature/<name>`, `fix/<name>`, `refactor/<name>`
- Commit messages: concise, describe the "why" not the "what"
- All changes go through pull requests targeting `main`
- Link PRs to GitHub issues with `Closes #<number>`
- Small, focused PRs preferred

## Security

- Never commit API keys or credentials
- CSV export must escape fields to prevent formula injection
- UIPasteboard (iOS) must be accessed on the main thread only
