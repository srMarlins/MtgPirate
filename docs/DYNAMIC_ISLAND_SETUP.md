# Setting Up Dynamic Island in Xcode

This guide walks you through the steps to enable the Dynamic Island Live Activity feature in the MtgPirate iOS app.

## Prerequisites

- **Xcode 14.1** or later
- **iOS 16.2+** SDK
- **iPhone 14 Pro** simulator or device (for testing Dynamic Island)
- Completed basic iOS app setup (see `README_XCODE_SETUP.md`)

## Step 1: Add Swift Files to Xcode Project

1. Open the Xcode project:
   ```bash
   open mtgPirate/mtgPirate.xcodeproj
   ```

2. In the Project Navigator, right-click the `mtgPirate` folder
3. Select **Add Files to "mtgPirate"...**
4. Navigate to and add these files:
   - `CardMatchingActivity.swift`
   - `LiveActivityManager.swift`
5. Ensure **"Copy items if needed"** is checked
6. Ensure the target **"mtgPirate"** is selected

## Step 2: Import ActivityKit Framework

1. Select the **mtgPirate** project in the navigator
2. Select the **mtgPirate** target
3. Go to the **General** tab
4. Scroll to **Frameworks, Libraries, and Embedded Content**
5. Click the **+** button
6. Search for and add **ActivityKit.framework**
7. Set it to **"Do Not Embed"** (it's a system framework)

## Step 3: Configure Build Settings

### Set Deployment Target

1. In **Build Settings**, find **iOS Deployment Target**
2. Set it to **iOS 16.2** or later
   ```
   iOS Deployment Target: 16.2
   ```

### Enable Live Activities Capability (Optional)

For production apps, you may need to add the capability:
1. Select the target
2. Go to **Signing & Capabilities**
3. Click **+ Capability**
4. Add **"Background Modes"** (if not already present)
5. No specific mode needed for Live Activities (they work without background modes)

## Step 4: Verify Info.plist Settings

The `Info.plist` should already contain:
```xml
<key>NSSupportsLiveActivities</key>
<true/>
<key>NSSupportsLiveActivitiesFrequentUpdates</key>
<true/>
```

If not, add them manually in the Info.plist file.

## Step 5: Implement Swift-Kotlin Bridge (Optional)

The current implementation uses stub functions. To connect Swift's `LiveActivityManager` with Kotlin:

### Option A: Use @objc Exports (Recommended for Simple Cases)

The `LiveActivityManager` is already marked with `@objc` and `public`, making it accessible from Objective-C and Kotlin/Native.

In your Kotlin code, you can theoretically access it via:
```kotlin
// This is a placeholder - actual implementation requires cinterop setup
```

### Option B: Create a Bridging Module

For more complex integration:

1. Create `mtgPirate-Bridging-Header.h`:
   ```objc
   #import <Foundation/Foundation.h>
   #import "LiveActivityManager-Swift.h"
   ```

2. In **Build Settings**, set:
   ```
   Objective-C Bridging Header: mtgPirate-Bridging-Header.h
   ```

### Option C: Use Kotlin/Native cinterop (Full Integration)

Create `src/nativeInterop/cinterop/LiveActivity.def`:
```properties
language = Objective-C
headers = LiveActivityManager-Swift.h
compilerOpts = -framework Foundation -framework ActivityKit
```

Update `build.gradle.kts`:
```kotlin
iosTarget.compilations.getByName("main") {
    cinterops {
        val liveActivity by creating {
            defFile(project.file("src/nativeInterop/cinterop/LiveActivity.def"))
        }
    }
}
```

**For this PR**: We're leaving the Swift-Kotlin bridge as stubs for now, since full integration requires more advanced cinterop setup.

## Step 6: Test the Implementation

### Testing in Simulator

1. **Select iPhone 14 Pro** or **iPhone 15 Pro** simulator
2. Build and run the app (`Cmd+R`)
3. The Dynamic Island should appear when you start importing a deck

### Testing on Device

1. Connect an iPhone 14 Pro or later
2. Ensure code signing is configured
3. Build and run on the device
4. Test with Live Activities enabled in Settings

### Debugging

Enable debug logging in `LiveActivityManager.swift`:
```swift
print("✅ Live Activity started: \(activity.id)")
print("🔄 Live Activity updated: \(phase)")
```

View logs in **Xcode Console** or **Instruments**.

## Step 7: Known Limitations

### Current Implementation Status

✅ **Completed:**
- Swift Live Activity widget code
- ActivityKit integration
- Dynamic Island view layouts
- Lock screen view
- Kotlin platform service interface

⚠️ **Stubbed:**
- Swift-to-Kotlin bridging (uses print statements)
- Actual Live Activity updates from Kotlin

❌ **Not Implemented:**
- Full cinterop configuration
- Automatic Swift function calling from Kotlin
- Widget Extension target (activities work in main app for now)

### What Works Now

With the current implementation:
1. Swift code compiles in Xcode ✅
2. Kotlin code compiles in Gradle ✅
3. App launches without crashes ✅
4. Architecture is ready for full integration ✅

### What Needs Manual Testing

To fully test Live Activities:
1. Uncomment Swift function calls in `LiveActivityService.kt` (iOS)
2. Configure cinterop for ActivityKit
3. Call `LiveActivityManager.shared.startActivity(...)` from Kotlin
4. Test on iPhone 14 Pro+ device or simulator

## Step 8: Production Considerations

### App Store Requirements

- **Privacy**: Declare Live Activity usage in App Privacy section
- **Testing**: Test on multiple devices (14 Pro, 15 Pro, etc.)
- **Permissions**: No additional permissions needed
- **Battery**: Live Activities are efficient but consider user experience

### Best Practices

1. **Throttle Updates**: Don't update too frequently (current implementation updates on state changes only)
2. **Handle Errors**: Gracefully handle cases where Live Activities fail
3. **Dismiss When Done**: Always dismiss completed activities (we do this automatically after 3s)
4. **Test Timeout**: Live Activities expire after 8 hours
5. **Fallback**: App works fine without Dynamic Island (older devices)

## Troubleshooting

### Build Errors

**Error: "Module 'ActivityKit' not found"**
- Ensure iOS Deployment Target is 16.2+
- Clean build folder (`Cmd+Shift+K`)
- Rebuild project (`Cmd+B`)

**Error: "Unknown attribute 'activityBackgroundTint'"**
- Update to Xcode 14.1+ with iOS 16.2 SDK

### Runtime Issues

**Live Activity Doesn't Appear**
- Check device compatibility (iPhone 14 Pro+)
- Verify iOS version (16.2+)
- Enable Live Activities in Settings → Face ID & Passcode
- Check console for errors

**Activity Stuck or Not Updating**
- Check if activity exceeded 8-hour limit
- Restart app to clear stale activities
- Verify update calls are being made (check logs)

**Simulator Issues**
- Use iPhone 14 Pro or 15 Pro simulator
- Restart simulator if Dynamic Island doesn't appear
- Check Simulator → Features → Dynamic Island

## Next Steps

Once basic setup is complete:

1. **Test the Swift Code**: Run the app and manually trigger activities from Swift
2. **Complete cinterop**: Set up full Kotlin-Swift bridging
3. **Test End-to-End**: Verify automatic updates from MVI state changes
4. **Polish UI**: Adjust colors, fonts, and animations
5. **Add Features**: Consider card art previews, tap actions, etc.

## Resources

- [Apple ActivityKit Documentation](https://developer.apple.com/documentation/activitykit)
- [Live Activities Programming Guide](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities)
- [Dynamic Island HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities)
- [Kotlin/Native iOS Interop](https://kotlinlang.org/docs/native-objc-interop.html)
- [Sample Project](https://github.com/apple/sample-food-truck)

## Support

If you encounter issues:
1. Check the Troubleshooting section above
2. Review console logs for error messages
3. Verify all setup steps were completed
4. Test on a physical iPhone 14 Pro+ device (simulators can be quirky)

---

**Happy Dynamic Island Development! 🏝️**
