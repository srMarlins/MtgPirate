# iOS Implementation Guide

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Limitations](#limitations)
- [Building](#building)
- [Database Setup](#database-setup)
- [Testing](#testing)
- [Extending](#extending)
- [Troubleshooting](#troubleshooting)

## Overview

iOS implementation built with:
- **Kotlin Multiplatform** - Shared business logic
- **Compose Multiplatform** - Declarative UI
- **MVI Architecture** - Reactive state management
- **SQLDelight** - Type-safe persistence

The iOS app provides a wizard-style workflow for importing MTG decklists, matching cards, and exporting CSV orders.

## Architecture

### Source Structure

```
src/
├── commonMain/           # Shared code (UI, models, logic)
│   ├── ui/              # Compose UI components
│   ├── state/           # MVI ViewModel
│   ├── model/           # Data models
│   ├── catalog/         # Catalog parsing
│   └── deck/            # Decklist parser
│
└── iosMain/             # iOS-specific
    ├── app/             # Entry point and navigation
    ├── platform/        # Platform utilities
    ├── catalog/         # Scryfall API stub
    └── database/        # SQLite driver
```

### Platform-Specific Implementation

iOS-specific code provides:
1. **Database Driver** - SQLDelight's `NativeSqliteDriver`
2. **Platform Services** - Catalog, CSV export, preferences, logging
3. **Platform Utilities** - Time, decimal formatting, clipboard
4. **App Entry Point** - Navigation and wizard flow

## Features

### Wizard Flow

4-step wizard:
1. **Import** - Paste decklist
2. **Preferences** - Configure options (sideboard, tokens)
3. **Results** - Review matches, resolve ambiguities
4. **Export** - Export CSV to clipboard

### Navigation

- **Bottom Nav Bar** - Quick access to Import, Catalog, Matches
- **Theme Toggle FAB** - Switch light/dark themes
- **Wizard Progress** - Visual step indicators
- **Back Navigation** - Return to previous steps

### UI Features

**Pixel Design System:**
- ✨ Retro pixel-style borders
- 💫 Scanline effects for CRT aesthetic
- 🌈 Glowing borders and animations
- 🎯 Consistent spacing and typography
- 🎨 Dark/light theme support

## Limitations

### Network Operations (Not Implemented)

iOS app uses **cached catalog data** only. Network fetching not implemented.

**Workarounds:**
- Pre-populate database with catalog data
- Use Desktop app to fetch and sync
- Implement NSURLSession networking if needed

**Files:** `catalog/ScryfallApiImpl.kt`, `platform/IosMviPlatformServices.kt`

### Clipboard Operations (Stub)

Clipboard defined but not fully implemented.

**Production implementation:**
```kotlin
import platform.UIKit.UIPasteboard

actual suspend fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
```

**File:** `platform/PlatformUtils.kt`

### Time Functions (Simplified)

`currentTimeMillis()` returns fixed value. For production:
```kotlin
import platform.Foundation.NSDate

actual fun currentTimeMillis(): Long {
    return (NSDate.timeIntervalSince1970() * 1000).toLong()
}
```

**File:** `platform/PlatformUtils.kt`

### File Export (Clipboard Only)

CSV export copies to clipboard. For production, use `UIActivityViewController` to save/share files.

## Building

### Prerequisites

- **macOS** - Required for iOS development
- **Xcode** - Latest version
- **Kotlin Multiplatform Plugin** - For Kotlin/Native
- **CocoaPods** (optional) - For dependency management

### Build Configuration

Kotlin/Native targets:
```kotlin
iosX64()               // iOS simulator (Intel)
iosArm64()             // iOS devices (64-bit)
iosSimulatorArm64()    // iOS simulator (Apple Silicon)
```

### Build Commands

```bash
# Build iOS framework for simulator
./gradlew :linkDebugFrameworkIosSimulatorArm64

# Build for device
./gradlew :linkReleaseFrameworkIosArm64
```

### Xcode Integration

```swift
import shared

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea()
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

Options for populating catalog:

1. **Use Desktop App:**
   ```bash
   ./gradlew run  # Fetch catalog
   cp data/pirate.db ios/resources/
   ```

2. **Bundle Pre-filled Database:**
   - Include `pirate.db` in iOS app bundle
   - Copy to documents on first launch

3. **Backend Sync:**
   - Create API endpoint serving catalog
   - Fetch and populate on app launch

### Database Location

```kotlin
val documentsDir = NSFileManager.defaultManager.URLForDirectory(
    NSDocumentDirectory, NSUserDomainMask, null, true, null
)
val dbPath = "${documentsDir?.path}/pirate.db"
```

## Testing

### Unit Testing

```bash
# Run common tests
./gradlew cleanAllTests allTests

# Run iOS-specific tests
./gradlew iosSimulatorArm64Test
```

### UI Testing (Xcode)

```swift
class MtgPirateUITests: XCTestCase {
    func testImportFlow() {
        let app = XCUIApplication()
        app.launch()
        
        let importField = app.textFields["DECKLIST.TXT"]
        importField.tap()
        importField.typeText("4 Lightning Bolt\n")
        
        app.buttons["Next →"].tap()
        XCTAssertTrue(app.staticTexts["STEP 2/4"].exists)
    }
}
```

### Performance

- **Memory** - Efficient SQLite, optimized Compose recomposition
- **Battery** - Efficient database, no background networking
- **App Size** - Base ~5-10 MB, with database ~15-20 MB

## Extending

### Adding Network Support

```kotlin
import platform.Foundation.*

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

### Adding File Export

```kotlin
import platform.UIKit.*

fun exportCsvToFile(csvContent: String, viewController: UIViewController) {
    val tempDir = NSTemporaryDirectory()
    val filePath = "$tempDir/mtg-order.csv"
    
    csvContent.writeToFile(filePath, true, NSUTF8StringEncoding)
    
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val activityVC = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null
    )
    
    viewController.presentViewController(activityVC, true, null)
}
```

### Adding Biometric Auth

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

**Database not found**
- Ensure database is bundled or created on first launch
- Check `DatabaseDriverFactory` creates database if missing

**Compose UI not rendering**
- Verify Xcode links Kotlin framework correctly
- Check framework search paths in Build Settings

**App crashes on device**
- Check code signing and provisioning profiles
- Review Xcode logs for missing entitlements

**Catalog is empty**
- Pre-populate database with catalog data
- Verify database contains `CardVariant` entries

## Future Enhancements

- ✨ Widget support - Recent imports on home screen
- 📸 Camera scanner - Scan physical cards
- 🔔 Push notifications - Catalog update alerts
- 🌐 CloudKit sync - Sync across devices
- 🎙️ Siri integration - Voice commands
- ⌚ watchOS app - Quick catalog lookup

## Resources

- [Kotlin Multiplatform Mobile](https://kotlinlang.org/lp/mobile/)
- [Compose Multiplatform iOS](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-ios-overview.html)
- [SQLDelight on iOS](https://cashapp.github.io/sqldelight/native_sqlite_driver/)
- [Apple Developer Docs](https://developer.apple.com/documentation/)
