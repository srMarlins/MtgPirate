# Dynamic Island Integration Guide

## Overview

MtgPirate now supports **Dynamic Island** on iPhone 14 Pro and later! Track your card matching progress in real-time without leaving your workflow, with beautiful live updates displayed in the Dynamic Island.

<div align="center">
  <img src="docs/assets/dynamic-island-demo.png" alt="Dynamic Island Demo" width="800">
</div>

## Features

### 🎯 Real-Time Progress Tracking

Watch your deck import progress unfold in the Dynamic Island with:
- **Live card counting** - See cards being parsed and matched in real-time
- **Phase indicators** - Know exactly what stage you're at (Parsing → Matching → Resolving → Exporting)
- **Progress visualization** - Beautiful progress bars and animations
- **Price tracking** - Watch your total order value calculate live

### 📱 Three Dynamic Island Views

#### Minimal View (Multi-Activity)
When multiple Live Activities are running, MtgPirate shows a compact emoji indicator.

#### Compact View (Default)
The standard Dynamic Island display shows:
- Phase emoji (📋 🔍 ⚠️ 📤 ✅)
- Current progress (e.g., "12/45")
- Color-coded by phase

#### Expanded View (Tap to Expand)
Tap the Dynamic Island to see detailed information:
- Current card being processed
- Full progress bar
- Ambiguous card warnings
- Total price calculated
- Animated phase transitions

## What Gets Tracked

### 1. **Parsing Phase** 📋
- Counts cards as your decklist is parsed
- Shows total cards detected
- Blue color theme

### 2. **Matching Phase** 🔍
- Displays each card as it's matched against the catalog
- Shows current card name
- Progress through the deck
- Purple color theme

### 3. **Resolving Phase** ⚠️
- Alerts you to ambiguous cards needing manual selection
- Counts how many cards need resolution
- Orange color theme

### 4. **Exporting Phase** 📤
- Shows CSV generation progress
- Displays final card count and price
- Green color theme

### 5. **Completion** ✅
- Success animation
- Final statistics
- Auto-dismisses after 3 seconds

## Requirements

- **iOS 16.2 or later**
- **iPhone 14 Pro or iPhone 14 Pro Max** (or newer with Dynamic Island)
- **Live Activities enabled** in Settings → Face ID & Passcode → Live Activities

## How It Works

### Architecture

```
┌─────────────────────┐
│   Kotlin MVI        │
│   ViewModel         │
└──────────┬──────────┘
           │
           ├─ ViewState changes
           │
┌──────────▼──────────┐
│  LiveActivityService│ (Kotlin)
└──────────┬──────────┘
           │
           ├─ Swift Interop
           │
┌──────────▼──────────┐
│ LiveActivityManager │ (Swift)
└──────────┬──────────┘
           │
           ├─ ActivityKit API
           │
┌──────────▼──────────┐
│   Dynamic Island    │
└─────────────────────┘
```

### State Flow Integration

The Dynamic Island updates automatically by observing the MVI ViewState:

1. **Deck parsing** → Starts Live Activity
2. **Match updates** → Updates progress and card info
3. **Ambiguous cards** → Shows warning badge
4. **Wizard steps** → Transitions between phases
5. **Export complete** → Shows success and dismisses

## Implementation Details

### Swift Components

#### `CardMatchingActivity.swift`
Defines the Live Activity structure with:
- `CardMatchingActivityAttributes` - Static session data
- `ContentState` - Dynamic state (phase, progress, cards)
- `CardMatchingActivityWidget` - Widget configuration
- Dynamic Island view layouts (minimal, compact, expanded)
- Lock screen view

#### `LiveActivityManager.swift`
Singleton manager for controlling activities:
- `startActivity(sessionId:totalCards:)` - Start new activity
- `updateActivity(...)` - Update with full state
- `updatePhase(_:)` - Quick phase updates
- `updateProgress(currentIndex:cardName:)` - Progress updates
- `completeActivity(success:finalMessage:)` - End with final state
- `endActivity()` - Dismiss immediately

### Kotlin Integration

#### `LiveActivityService.kt` (Common)
Platform-agnostic interface using `expect/actual` pattern.

#### `LiveActivityService.kt` (iOS)
iOS-specific implementation bridging to Swift's `LiveActivityManager`.

#### `LiveActivityService.kt` (Desktop)
No-op stub for desktop platform.

#### Integration in `Main.kt`
`LaunchedEffect` blocks observe state changes and trigger Live Activity updates:
- Monitors `state.deckEntries` to start activity
- Tracks `state.matches` for progress updates
- Watches `state.wizardCompletedSteps` for phase transitions

## Usage Example

```kotlin
// The integration is automatic! Just use the app normally:

// 1. Paste your decklist → Live Activity starts
viewModel.processIntent(ViewIntent.UpdateDeckText(deckText))
viewModel.processIntent(ViewIntent.ParseDeck)

// 2. Configure preferences → Transitions to Matching phase
viewModel.processIntent(ViewIntent.CompleteWizardStep(2))
viewModel.processIntent(ViewIntent.RunMatch)

// 3. Resolve ambiguities → Shows warning in Dynamic Island
viewModel.processIntent(ViewIntent.ResolveCandidate(index, variant))

// 4. Export → Shows completion and dismisses
viewModel.processIntent(ViewIntent.ExportCsv)
viewModel.processIntent(ViewIntent.CompleteWizardStep(4))
```

## Customization

### Adjusting Update Frequency

Live Activities update based on state changes. To throttle updates:

```kotlin
// In Main.kt, add debouncing to LaunchedEffect
LaunchedEffect(state.matches) {
    snapshotFlow { state.matches }
        .debounce(500) // Update every 500ms max
        .collect { matches ->
            liveActivityService.updateActivity(...)
        }
}
```

### Custom Phases

Add new phases in `CardMatchingActivity.swift`:

```swift
enum MatchingPhase: String, Codable, Hashable {
    case parsing = "Parsing"
    case matching = "Matching"
    case resolving = "Resolving"
    case exporting = "Exporting"
    case completed = "Complete"
    case error = "Error"
    // Add your custom phase:
    case validating = "Validating"
    
    var emoji: String {
        case .validating: return "✓"
        // ...
    }
}
```

### Styling

Modify colors, fonts, and layouts in `CardMatchingActivity.swift`:

```swift
var color: Color {
    switch self {
    case .matching: return .purple  // Change to your brand color
    // ...
    }
}
```

## Troubleshooting

### Live Activity Not Appearing

1. **Check device compatibility**: Dynamic Island requires iPhone 14 Pro or newer
2. **Verify iOS version**: Requires iOS 16.2+
3. **Enable Live Activities**: Settings → Face ID & Passcode → Live Activities (ON)
4. **Check app permissions**: Settings → MtgPirate → Allow Live Activities

### Activity Stuck or Not Updating

- Live Activities have a time limit (default 8 hours)
- If the app crashes, the activity may persist - relaunch to dismiss
- Check console logs for errors

### Building Issues

If Xcode shows errors about ActivityKit:
1. Ensure deployment target is iOS 16.2+
2. Add `import ActivityKit` to files using Live Activities
3. Check that the Widget target is properly configured

## Performance Considerations

- **Battery Impact**: Minimal - Live Activities use efficient updates
- **Update Frequency**: Updates occur on state changes only
- **Memory**: ~1-2 MB for the Live Activity
- **Network**: No network calls - all data from MVI state

## Future Enhancements

Potential features to add:
- [ ] Card art preview in expanded view
- [ ] Tap actions to jump to specific steps
- [ ] Set icons in the Dynamic Island
- [ ] Custom animations for phase transitions
- [ ] Push Token support for remote updates
- [ ] Historical import tracking
- [ ] Price change alerts

## Testing

### Simulator Testing

Dynamic Island can be tested in Xcode Simulator:
1. Select iPhone 14 Pro or iPhone 15 Pro simulator
2. Run the app and start importing a deck
3. The Dynamic Island will appear at the top of the simulator

### Device Testing

For best results, test on a physical device:
```bash
# Build for device
./gradlew linkReleaseFrameworkIosArm64

# Open in Xcode and run on connected iPhone 14 Pro+
open mtgPirate/mtgPirate.xcodeproj
```

### Debug Logging

Enable verbose logging in `LiveActivityManager.swift`:
```swift
print("🔄 Live Activity updated: \(phase) - \(currentIndex)/\(totalCards)")
```

## Resources

- [Apple ActivityKit Documentation](https://developer.apple.com/documentation/activitykit)
- [Dynamic Island Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/live-activities)
- [Kotlin/Native iOS Interop](https://kotlinlang.org/docs/native-objc-interop.html)

## License

This Dynamic Island integration is part of MtgPirate and follows the same license as the main project.

---

**Enjoy tracking your MTG deck imports in the Dynamic Island! 🏴‍☠️✨**
