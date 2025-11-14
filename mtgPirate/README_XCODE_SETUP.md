# iOS App Setup - Final Steps

Great job! Your iOS app is almost ready. Here's what I've done and what you need to do:

## ✅ What's Been Set Up

1. **Kotlin Framework Built** - Located at: `build/bin/iosSimulatorArm64/debugFramework/shared.framework`
2. **iOS App Updated** - `ContentView.swift` now calls your Kotlin Compose UI
3. **Xcode Path Fixed** - Command line tools now point to full Xcode installation
4. **Build Script Created** - `mtgPirate/build-framework.sh` for automatic builds
5. **Framework Linked** - ✅ Framework is already added to Xcode project with proper search paths
6. **Auto-Build Script** - Kotlin framework rebuilds automatically when you run in Xcode

## 📋 Next Steps in Xcode

### Step 1: Open and Run!

Since the framework is already linked and configured, you just need to:

1. Open Xcode: `open mtgPirate/mtgPirate.xcodeproj`
2. Select a simulator (iPhone 15, iPad, etc.)
3. Click the **Run** button (▶️) or press `Cmd+R`
4. Your Kotlin Compose UI should appear! 🎉

That's it! The framework will automatically rebuild when needed thanks to the "Kotlin MP" build phase.

### Step 2: (Optional) Verify Framework Configuration

If you want to verify everything is set up correctly:

1. In the project navigator, select the **mtgPirate** project (top item)
2. Select the **mtgPirate** target
3. Go to the **General** tab
4. Scroll down to **Frameworks, Libraries, and Embedded Content**
5. You should see **shared.framework** listed with "Embed & Sign"

### Step 3: (Optional) Check Build Phases

To see the auto-build script:

1. Go to the **Build Phases** tab
2. You should see a "Kotlin MP" script phase
3. This runs before compilation and ensures the framework is always up to date

## 🏃 Quick Run From Terminal

You can also run the app from the terminal:

```bash
# Build the framework
./gradlew linkDebugFrameworkIosSimulatorArm64

# Run the app in simulator
cd mtgPirate
xcodebuild -project mtgPirate.xcodeproj \
  -scheme mtgPirate \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build

# Or use this simplified script
./build-framework.sh && open mtgPirate.xcodeproj
```

## 🎯 What You'll See

When you run the app, you should see:
- 🏴‍☠️ MtgPirate with pixel-styled UI
- Import screen for pasting decklists
- Dark/light theme toggle
- Full navigation between screens
- All the features from your desktop app!

## 🔧 Troubleshooting

### "Module 'shared' not found"
- Make sure you've added the framework in Step 1
- Clean build folder: `Product` → `Clean Build Folder` (Cmd+Shift+K)
- Rebuild: `Product` → `Build` (Cmd+B)

### "Framework not found"
- Check Framework Search Paths in Build Settings
- Verify the framework exists: `ls ../build/bin/iosSimulatorArm64/debugFramework/`
- Rebuild framework: `./gradlew linkDebugFrameworkIosSimulatorArm64`

### Build Errors
- Make sure Xcode command line tools are configured:
  ```bash
  sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
  ```

### Simulator Issues
- Ensure you've selected an iOS Simulator (not "My Mac")
- Try a different simulator device
- Restart Xcode if needed

## 📱 Running on Different Targets

### For iOS Device (Physical iPhone/iPad)
```bash
./gradlew linkDebugFrameworkIosArm64
```
Then update Framework Search Path in Xcode to: `../build/bin/iosArm64/debugFramework`

### For Intel Mac Simulator
```bash
./gradlew linkDebugFrameworkIosX64
```
Then update Framework Search Path in Xcode to: `../build/bin/iosX64/debugFramework`

## 📚 Additional Resources

- Your iOS app code: `mtgPirate/mtgPirate/`
- Kotlin iOS code: `src/iosMain/kotlin/`
- Build script: `mtgPirate/build-framework.sh`
- Framework location: `build/bin/iosSimulatorArm64/debugFramework/shared.framework`

## 🎉 Success!

Once you complete these steps in Xcode, you'll be able to run your iOS app directly from IntelliJ by:
1. Building the framework: `./gradlew linkDebugFrameworkIosSimulatorArm64`
2. Opening Xcode and running the app

The iOS app will show your beautiful pixel-styled MtgPirate UI with full functionality!

