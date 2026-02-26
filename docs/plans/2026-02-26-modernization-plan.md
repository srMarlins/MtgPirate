# MtgPirate Modernization Plan

**Date:** 2026-02-26
**Scope:** Full code review findings — 130 issues across 4 areas
**Goal:** Bring the codebase to production-ready quality

---

## Summary

| Area | Critical | High | Medium | Low | Total |
|------|----------|------|--------|-----|-------|
| Common/Shared | 5 | 8 | 15 | 10 | 38 |
| Desktop | 2 | 6 | 10 | 11 | 29 |
| iOS | 3 | 5 | 8 | 6 | 22 |
| Build/Infra | 5 | 12 | 14 | 10 | 41 |
| **Total** | **15** | **31** | **47** | **37** | **130** |

---

## Phase 1: Critical Fixes (Safety & Correctness)

### 1.1 Resource Leaks & Thread Safety
- **ScryfallImageEnricher race condition**: `lastRequestTime` is a mutable var with no synchronization. Use `Mutex` to serialize rate-limited access. (`commonMain/catalog/ScryfallImageEnricher.kt:13-14`)
- **ScryfallApi HttpClient singleton leak**: Never closed, mutable var not thread-safe. Inject or manage lifecycle properly. (`commonMain/catalog/ScryfallApi.kt:33-44`)
- **runBlocking inside coroutine**: `KtorRemoteCatalogDataSource.fetchAllCsvPages` uses `runBlocking` inside `withContext(Dispatchers.Default)`. Make it `suspend`. (`commonMain/catalog/KtorRemoteCatalogDataSource.kt:85-120`)
- **iOS CoroutineScope never cancelled**: Created in `remember` with no `DisposableEffect`. Leaks on view teardown. (`iosMain/app/Main.kt:36`)
- **iOS HttpClient(Darwin) never closed**: Leaks `NSURLSession` on every hierarchy rebuild. (`iosMain/platform/IosMviPlatformServices.kt:33-35`)
- **Desktop HttpURLConnection never disconnected**: Missing `try/finally` with `conn.disconnect()`. (`desktopMain/catalog/RemoteCatalogDataSource.kt:188-204`)

### 1.2 Data Integrity
- **Non-atomic catalog replacement**: `CatalogStore.replaceCatalog` clears then inserts without a transaction. Crash = empty catalog. (`commonMain/database/CatalogStore.kt:27-35`)
- **Floating-point price truncation**: `(priceDollars * 100.0).toInt()` truncates instead of rounding. $2.20 becomes 219 cents. (`commonMain/catalog/CatalogCsvParser.kt:121`, `CatalogParser.kt:145`)

### 1.3 Security
- **CSV injection in CsvExporter**: Card names written unescaped. Values starting with `=`, `+`, `-`, `@` execute as formulas in Excel. (`desktopMain/export/CsvExporter.kt:32-39`)
- **iOS UIPasteboard from background thread**: `copyToClipboard` dispatches to `Dispatchers.Default` but `UIPasteboard` requires main thread. Can crash. (`iosMain/platform/PlatformUtils.kt:26-28`)

### 1.4 Build Infrastructure
- **Detekt plugin never applied**: Config files exist but plugin is missing from `build.gradle.kts`. Zero static analysis runs. (`build.gradle.kts` plugins block)
- **CI runs no tests or analysis**: `./gradlew build` compiles only. No detekt, no test step. (`.github/workflows/ci.yml:26`)
- **No test framework configured**: Zero test source sets, zero test dependencies, zero tests. (`build.gradle.kts`, `libs.versions.toml`)

---

## Phase 2: Architecture & High-Priority Fixes

### 2.1 God Class Decomposition
- **MviViewModel (758 lines)**: Handles catalog, parsing, matching, import/export, preferences, images, themes, wizard state, logging. Extract into use-case classes: `CatalogUseCase`, `MatchingUseCase`, `ImportExportUseCase`, `PreferencesUseCase`.

### 2.2 DRY Violations
- **`canonicalType()` implemented 3 times**: In `CatalogCsvParser`, `CatalogParser`, `KtorRemoteCatalogDataSource`. Extract to shared utility with `VariantType` enum.
- **Default prices duplicated 3+ times**: In cents (220, 300, 350) and dollars (2.2, 3.0, 3.5) across files. Create a `Pricing` constants object.
- **CSV export logic duplicated**: Desktop `CsvExporter` and iOS `generateCsvContent` have drifted (iOS doesn't aggregate, uses locale-sensitive formatting). Extract to `commonMain`.
- **Desktop `RemoteCatalogDataSource` duplicates `KtorRemoteCatalogDataSource`**: Two parallel HTTP implementations. Consolidate to Ktor in commonMain.
- **Clipboard copy duplicated on desktop**: `PlatformUtils.copyToClipboard` and `DesktopMviPlatformServices.copyToClipboard` are identical.

### 2.3 Performance
- **Matcher ignores `Catalog.indexByName`**: Does O(N*M) full scans instead of O(1) map lookups. (`commonMain/match/Matcher.kt:16-56`)
- **N+1 database insertions**: `CatalogStore.replaceCatalog` inserts one-by-one. Use batch insertion. (`commonMain/database/CatalogStore.kt:27-35`)
- **Regex compiled on every call**: `NameNormalizer.normalize()` creates 5 Regex objects per invocation. Compile once as companion constants. (`commonMain/match/NameNormalizer.kt:5-16`)
- **Database suspend functions missing IO dispatcher**: Write operations run on caller's dispatcher. (`commonMain/database/Database.kt:44-108`)

### 2.4 Database
- **No SQLDelight migration strategy**: Schema changes = data loss for users. Configure `verifyMigrations` and add `.sqm` files. (`build.gradle.kts:136-142`)
- **Missing index on `CardVariantEntity.sku`**: `updateImageUrl` WHERE clause does full table scan. (`CardVariant.sq:33`)
- **Missing index on `LogEntryEntity.timestamp`**: `deleteOldLogs` ORDER BY requires full sort. (`LogEntry.sq:17-19`)

### 2.5 ProGuard
- **Dead Room keep rule**: Project uses SQLDelight, not Room. (`compose-desktop.pro:13-15`)
- **Missing Ktor ProGuard rules**: CIO engine uses reflection. Release builds will crash. (`compose-desktop.pro`)
- **Overly broad Compose keep rules**: `-keep class androidx.compose.** { *; }` defeats optimization. Remove — Compose plugin handles this.

### 2.6 Dead Code Removal
- **Desktop `persistence/ImportsStore.kt`** and **`persistence/PreferencesStore.kt`**: Never referenced. App uses database-backed stores.
- **Desktop `catalog/CatalogFetcher.kt`**: Never referenced. Has mutable thread-unsafe state.
- **Desktop `catalog/DatabaseCatalogDataSource.kt`** (including `HybridCatalogDataSource`): Template code, never used.
- **Common `Catalog.indexByName`**: Lazy map declared but never used anywhere.
- **Common `loadSavedImports()`**: No-op method, just logs.
- **Common `EntityMappers.toEntity()` functions**: Defined but never called.
- **iOS `IosBottomNavBar` and `IosThemeToggleFab`**: Dead composables.
- **iOS `IosMobileWrappers.kt`**: Entire file unused.

### 2.7 Error Handling
- **Missing try/catch on database operations** throughout MviViewModel.
- **Silent exception swallowing** in desktop persistence layer.
- **Missing error handling** around ScryfallApi image enrichment map lookup.

---

## Phase 3: Modernization & Code Quality

### 3.1 Type Safety
- **`variantType: String` → `VariantType` enum**: Eliminates 3 `canonicalType()` functions and prevents invalid values. (`commonMain/model/Models.kt:11`)
- **`Section` enum missing `TOKEN`**: `Preferences.includeTokens` exists but no `TOKEN` section. Either add it or remove the preference. (`commonMain/model/Models.kt:27`)
- **`headerSize` as String → `TextStyle`**: Desktop `PlatformUI.kt:14` uses `"h4"` string.

### 3.2 iOS Platform Quality
- **Double `safeDrawingPadding()`**: Applied in navigation host AND every screen. Remove from screens. (`iosMain/app/Main.kt:94`)
- **Haptic feedback generators recreated every call**: Cache generators per Apple docs. (`iosMain/platform/IosHapticFeedback.kt`)
- **No swipe-to-go-back gesture**: Using simple `Crossfade` instead of iOS-native navigation transitions.
- **No keyboard dismissal**: Import screen text field has no way to dismiss keyboard.
- **Locale-sensitive price formatting**: `NSNumberFormatterDecimalStyle` uses device locale, producing `$1.234,56` in European locales.
- **Multiple intents fired without sequencing**: `processIntent` calls launch concurrent coroutines with no ordering guarantee.

### 3.3 Build & CI Improvements
- **Apply detekt plugin** and configure `FunctionNaming` to ignore `@Composable`.
- **Add test dependencies**: kotlin-test, kotlinx-coroutines-test, turbine, ktor-client-mock.
- **Add code formatter**: ktlint or ktfmt via Spotless plugin.
- **Add code coverage**: Kover plugin with thresholds.
- **Add Dependabot/Renovate** for automated dependency updates.
- **Add missing Linux build** to release workflow.
- **Add CI concurrency control** and job timeouts.
- **Centralize repositories** in `settings.gradle.kts` with `dependencyResolutionManagement`.
- **Remove deprecated `-Xexpect-actual-classes`** compiler flag.
- **Remove unnecessary `maven.pkg.jetbrains.space`** repository.
- **Remove `org.jetbrains.compose.experimental.macos.enabled`** (no macOS targets).
- **Remove `org.gradle.configureondemand`** (single-module, no benefit).

### 3.4 Cleanup
- **Remove unused imports** across all files (Main.kt has 7 unused).
- **Remove unused `maxOf`/`minOf`/`abs` expect/actual**: Use `kotlin.math` directly.
- **Remove stale debug comments** in CatalogCsvParser.
- **Remove unused `sqldelight-driver-android`** from version catalog.
- **Remove `changed` flag no-op** in `fillZeroPrices`.
- **Extract hardcoded colors** to Theme.kt (duplicated across 5+ UI files).
- **Add `key` parameter** to `LazyColumn.items` calls.
- **Add stable `key` to list items** in resolve and saved imports screens.

---

## Implementation Order

1. **Phase 1** (Critical): Fix in priority order — security, data integrity, resource leaks, then build infra
2. **Phase 2** (Architecture): Start with dead code removal, then DRY extraction, then God class decomposition
3. **Phase 3** (Quality): Build tooling first (formatter, detekt, tests), then type safety, then iOS polish

---

## Jules-Eligible Tasks (Simple/Mechanical)

These issues are tagged with `jules` on GitHub for Google Jules agents:
- Remove dead code files
- Remove unused imports
- Remove deprecated compiler flag
- Add `key` params to LazyColumn items
- Remove unnecessary repository
- Remove unused expect/actual functions
- Compile regex patterns as constants
- Remove stale debug comments
- Add CI concurrency control and timeouts
- Extract hardcoded colors to theme constants
- Remove unused version catalog entries
- Remove `changed` flag no-op code
