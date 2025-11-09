# USEACardScraper (Compose Multiplatform)

This project is a Kotlin Multiplatform application using JetBrains Compose Multiplatform for the desktop (JVM) UI. Common business logic (parsing, matching) resides in `commonMain` while the desktop UI lives in the `desktopMain` source set.

## Validation Against Official Guide
Following: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html

Key points implemented:
- `plugins { kotlin("multiplatform"), id("org.jetbrains.compose") }` present.
- JVM target declared as `jvm("desktop")` with `withJava()`.
- Desktop distribution configured in `compose.desktop { application { ... } }`.
- Dependencies split: commonMain (runtime, foundation) vs desktopMain (material, desktop.currentOs).
- Entry point `main()` in `app.MainKt` uses Compose Desktop APIs only.

## Source Set Layout
```
src/commonMain/kotlin/...  // shared models & logic
src/desktopMain/kotlin/... // desktop UI (will migrate from jvmMain naming)
```
Currently the UI file resides under `src/jvmMain/kotlin/app/Main.kt`; you can rename the folder to `desktopMain` to match the named target for consistency.

## Running
On Windows (cmd):
```cmd
gradlew.bat run
```
This will launch the desktop Compose window.

## Next Steps
1. Create `src/desktopMain/kotlin` directory and move `Main.kt` there.
2. Implement `CatalogFetcher` using ksoup in commonMain.
3. Wire catalog loading + matching into UI with status display.
4. Add CSV export functionality.

## Dependencies
- Compose Multiplatform 1.6.10
- Kotlin Multiplatform 1.9.23
- kotlinx-serialization-json 1.6.3
- kotlinx-datetime 0.5.0
- ksoup 0.2.5 (HTML parsing)


./gradlew to run gradle wrapper commands

## Notes
- Avoid Android-specific imports: using only compose material/runtime/foundation for desktop.
- Name normalization simplified for multiplatform.

