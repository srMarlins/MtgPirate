# iOS Implementation Guide

## Overview

MtgPirate includes an iOS implementation built with:

- **Kotlin Multiplatform** - Shared business logic and UI code
- **Compose Multiplatform** - Declarative UI framework
- **MVI Architecture** - Reactive state management
- **SQLDelight** - Type-safe database persistence

The iOS app provides a wizard-style workflow for importing MTG decklists, matching cards against the catalog, and exporting CSV orders.

## Architecture

### Source Structure

```
src/
├── commonMain/           # Shared code (UI, models, business logic)
│   ├── kotlin/
│   │   ├── ui/          # Compose UI components
│   │   ├── state/       # MVI ViewModel
│   │   ├── model/       # Data models
│   │   ├── catalog/     # Catalog parsing
│   │   ├── deck/        # Decklist parser
│   │   └── match/       # Card matching algorithm
│   └── sqldelight/      # Database schema
│
└── iosMain/             # iOS-specific implementations
    └── kotlin/
        ├── app/         # iOS app entry point and navigation
        ├── platform/    # Platform utilities (clipboard, time)
        ├── catalog/     # Scryfall API stub
        └── database/    # SQLite driver factory
```

### Platform-Specific Implementation

iOS-specific code provides platform implementations for:

1. **Database Driver** (`DatabaseDriverFactory.kt`)
   - Uses SQLDelight's `NativeSqliteDriver` for iOS
   - Stores database in app's document directory

2. **Platform Services** (`IosMviPlatformServices.kt`)
   - Catalog operations (uses cached data)
   - CSV export (copies to clipboard)
   - Preferences management
   - Logging

3. **Platform Utilities** (`PlatformUtils.kt`)
   - Time functions
   - Decimal formatting
   - Clipboard operations (stubs)

4. **App Entry Point** (`Main.kt`)
   - iOS navigation host
   - Wizard flow management
   - Screen routing

## Features

### Wizard Flow

The iOS app implements a 4-step wizard:

```
┌──────────────┐
│ 1. Import    │ ← Paste decklist
└──────┬───────┘
       │
┌──────▼───────┐
│ 2. Prefs     │ ← Configure options (sideboard, tokens, etc.)
└──────┬───────┘
       │
┌──────▼───────┐
│ 3. Results   │ ← Review matches, resolve ambiguities
└──────┬───────┘
       │
┌──────▼───────┐
│ 4. Export    │ ← Export CSV to clipboard
└──────────────┘
```

### Navigation

The iOS app includes:

- **Bottom Navigation Bar** - Quick access to Import, Catalog, and Matches
- **Theme Toggle FAB** - Switch between light/dark themes
- **Wizard Progress** - Visual indicators for current step
- **Back Navigation** - Return to previous steps

### UI Features

All UI components use the **Pixel Design System** with:

- ✨ Retro pixel-style borders and corners
- 💫 Scanline effects for CRT aesthetic
- 🌈 Glowing borders and animations
- 🎯 Consistent spacing and typography
- 🎨 Dark/light theme support

## Current Limitations

The iOS implementation has some intentional limitations:

### 1. Network Operations

**Status:** Not implemented

The iOS app relies on **cached catalog data** in the SQLDelight database. Network fetching via Scryfall or USEA APIs is not implemented.

**Workarounds:**
- Pre-populate the database with catalog data
- Use the Desktop app to fetch and sync catalog
- Implement NSURLSession networking if needed

**Files affected:**
- `src/iosMain/kotlin/catalog/ScryfallApiImpl.kt`
- `src/iosMain/kotlin/platform/IosMviPlatformServices.kt`

### 2. Clipboard Operations

**Status:** Stub implementation

Clipboard operations are defined but not fully implemented with iOS platform APIs.

**Production implementation:**
```kotlin
import platform.UIKit.UIPasteboard

actual suspend fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
```

**Files affected:**
- `src/iosMain/kotlin/platform/PlatformUtils.kt`

### 3. Time Functions

**Status:** Simplified implementation

`currentTimeMillis()` returns a fixed value. For production:

```kotlin
import platform.Foundation.NSDate

actual fun currentTimeMillis(): Long {
    return (NSDate.timeIntervalSince1970() * 1000).toLong()
}
```

**Files affected:**
- `src/iosMain/kotlin/platform/PlatformUtils.kt`

### 4. File Export

**Status:** Clipboard-only

CSV export copies data to clipboard. For production, implement with `UIActivityViewController` to save to Files app or share.

## Building for iOS

### Prerequisites

1. **macOS** - Required for iOS development
2. **Xcode** - Latest version
3. **Kotlin Multiplatform Plugin** - For Kotlin/Native compilation
4. **CocoaPods** (optional) - For dependency management

### Build Configuration

The iOS build uses Kotlin/Native to compile to native iOS binaries:

```kotlin
// build.gradle.kts
iosX64()      // iOS simulator (Intel)
iosArm64()    // iOS devices (64-bit)
iosSimulatorArm64()  // iOS simulator (Apple Silicon)
```

### Build Commands

```bash
# Build iOS framework
./gradlew :linkDebugFrameworkIosSimulatorArm64

# Build for device
./gradlew :linkReleaseFrameworkIosArm64
```

### Integration with Xcode

1. Create an Xcode project
2. Add the compiled Kotlin framework
3. Implement Swift wrapper for the Compose UI
4. Configure app bundle and provisioning

Example Swift integration:

```swift
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainKt.MainViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

## Database Setup

### Pre-populating Catalog

The iOS app requires catalog data in the database. Options:

1. **Use Desktop to Fetch:**
   ```bash
   # Run desktop app to fetch catalog
   ./gradlew run
   # Copy database from desktop to iOS resources
   cp data/pirate.db ios/resources/
   ```

2. **Bundle Pre-filled Database:**
   - Include `pirate.db` in iOS app bundle
   - Copy to documents directory on first launch

3. **Implement Backend Sync:**
   - Create API endpoint that serves catalog data
   - Fetch and populate database on iOS app launch

### Database Location

```kotlin
// iOS database location
val documentsDir = NSFileManager.defaultManager.URLForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask,
    null,
    true,
    null
)
val dbPath = "${documentsDir?.path}/pirate.db"
```

## Testing

### Unit Testing

Shared code can be tested on any platform:

```bash
# Run common tests
./gradlew cleanAllTests allTests

# Run iOS-specific tests
./gradlew iosSimulatorArm64Test
```

### UI Testing

Use Xcode's UI testing framework:

```swift
class MtgPirateUITests: XCTestCase {
    func testImportFlow() {
        let app = XCUIApplication()
        app.launch()
        
        // Test import screen
        let importField = app.textFields["DECKLIST.TXT"]
        importField.tap()
        importField.typeText("4 Lightning Bolt\n")
        
        // Test next button
        app.buttons["Next →"].tap()
        
        // Verify preferences screen
        XCTAssertTrue(app.staticTexts["STEP 2/4"].exists)
    }
}
```

## Performance Considerations

### Memory

- SQLDelight uses efficient native SQLite
- Compose Multiplatform optimizes recomposition
- Large catalogs (10k+ cards) work smoothly

### Battery

- Database operations are efficient
- No background networking (since not implemented)
- UI animations use hardware acceleration

### App Size

- Base app: ~5-10 MB
- With database: ~15-20 MB (depends on catalog size)
- Kotlin/Native produces compact binaries

## Extending the iOS Implementation

### Adding Network Support

To implement catalog fetching:

1. **Update ScryfallApiImpl.kt:**
```kotlin
import platform.Foundation.*
import kotlinx.cinterop.*

actual suspend fun fetchUrl(url: String): String = suspendCoroutine { cont ->
    val nsUrl = NSURL.URLWithString(url)!!
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        if (error != null) {
            cont.resumeWithException(Exception(error.localizedDescription))
        } else if (data != null) {
            val text = NSString.create(data, NSUTF8StringEncoding).toString()
            cont.resume(text)
        }
    }
    task.resume()
}
```

2. **Update IosMviPlatformServices.kt:**
```kotlin
override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
    return try {
        log("Fetching from USEA API...")
        val csvText = fetchUrl(urlApi)
        val catalog = CatalogCsvParser.parse(csvText)
        log("Fetched ${catalog.variants.size} variants")
        catalog
    } catch (e: Exception) {
        log("Error: ${e.message}")
        null
    }
}
```

### Adding File Export

To support saving files:

```kotlin
import platform.UIKit.*
import platform.Foundation.*

fun exportCsvToFile(csvContent: String, viewController: UIViewController) {
    val tempDir = NSTemporaryDirectory()
    val filePath = "$tempDir/mtg-order.csv"
    
    // Write to file
    csvContent.writeToFile(filePath, true, NSUTF8StringEncoding)
    
    // Share with UIActivityViewController
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val activityVC = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null
    )
    
    viewController.presentViewController(activityVC, true, null)
}
```

### Adding Biometric Authentication

To secure catalog data:

```kotlin
import platform.LocalAuthentication.*

suspend fun authenticateUser(): Boolean = suspendCoroutine { cont ->
    val context = LAContext()
    var error: NSError? = null
    
    if (context.canEvaluatePolicy(LAPolicy.DeviceOwnerAuthenticationWithBiometrics, &error)) {
        context.evaluatePolicy(
            LAPolicy.DeviceOwnerAuthenticationWithBiometrics,
            localizedReason = "Access MTG catalog"
        ) { success, error ->
            cont.resume(success)
        }
    } else {
        cont.resume(false)
    }
}
```

## Troubleshooting

### Common Issues

**Problem:** Database not found
```
Solution: Ensure database is bundled or created on first launch
Check: DatabaseDriverFactory creates the database if missing
```

**Problem:** Compose UI not rendering
```
Solution: Verify Xcode project links the Kotlin framework correctly
Check: Framework search paths in Build Settings
```

**Problem:** App crashes on device
```
Solution: Check code signing and provisioning profiles
Check: Xcode logs for missing entitlements
```

**Problem:** Catalog is empty
```
Solution: Pre-populate database with catalog data
Check: Database contains CardVariant entries
```

## Future Enhancements

Potential iOS-specific features:

- ✨ **Widget Support** - Display recent imports on home screen
- 📸 **Camera Scanner** - Scan physical cards to add to decklist
- 🔔 **Push Notifications** - Alert when catalog updates
- 🌐 **CloudKit Sync** - Sync imports across devices
- 🎙️ **Siri Integration** - Voice commands for imports
- ⌚ **watchOS App** - Quick catalog lookup on Apple Watch

## Resources

- [Kotlin Multiplatform Mobile](https://kotlinlang.org/lp/mobile/)
- [Compose Multiplatform iOS](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-ios-overview.html)
- [SQLDelight on iOS](https://cashapp.github.io/sqldelight/native_sqlite_driver/)
- [Apple Developer Documentation](https://developer.apple.com/documentation/)

## Support

For iOS-specific issues:

1. Check this documentation first
2. Review the [MVI Architecture](MVI_ARCHITECTURE.md) docs
3. Examine the Desktop implementation for reference
4. Open an issue on GitHub with:
   - iOS version
   - Device model
   - Xcode version
   - Error logs
