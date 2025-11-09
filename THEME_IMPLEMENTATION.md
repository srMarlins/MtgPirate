# Theme Implementation

## Overview
The application now supports both dark and light themes with a modern color palette. Dark theme is set as the default.

## Features

### Theme Toggle
- A theme toggle button (☀️/🌙) has been added to the top app bar
- Click the button to switch between dark and light themes
- The theme preference persists during the application session

### Dark Theme (Default)
Inspired by GitHub Dark and VS Code Dark+:
- **Primary**: `#6B9EFF` (Soft blue) - Used for primary buttons and accents
- **Secondary**: `#7DD3C0` (Mint/teal) - Used for secondary actions and highlights
- **Background**: `#0D1117` (Deep dark) - Main window background
- **Surface**: `#161B22` (Dark gray) - Cards and elevated surfaces
- **Error**: `#FF6B6B` (Soft red) - Error states
- **Text**: `#E6EDF3` (Light gray) - Main text color

### Light Theme
Clean and modern, inspired by GitHub's light mode:
- **Primary**: `#0969DA` (GitHub blue) - Primary actions
- **Secondary**: `#1F883D` (Green) - Secondary actions
- **Background**: `#FFFFFF` (Pure white) - Main background
- **Surface**: `#F6F8FA` (Light gray) - Cards and panels
- **Error**: `#CF222E` (Red) - Error states
- **Text**: `#1F2328` (Dark gray) - Main text

## Files Modified

### New Files
1. **`src/desktopMain/kotlin/ui/Theme.kt`**
   - Defines `AppDarkColors` and `AppLightColors` color schemes
   - Exports `AppTheme` composable that applies the selected theme
   - Dark theme is the default

### Modified Files
1. **`src/desktopMain/kotlin/app/MainStore.kt`**
   - Added `isDarkTheme: Boolean = true` to `MainState` (defaults to dark)
   - Added `MainIntent.ToggleTheme` intent
   - Added theme toggle handler in dispatch method

2. **`src/desktopMain/kotlin/app/Main.kt`**
   - Replaced `MaterialTheme` with `AppTheme(darkTheme = state.isDarkTheme)`
   - Added theme toggle IconButton with sun/moon emoji to TopAppBar
   - Button shows ☀️ (sun) in dark mode and 🌙 (moon) in light mode

## Usage

### For Users
1. Launch the application - it will start in dark theme by default
2. Click the sun/moon icon in the top-right corner of the app bar to toggle themes
3. The theme will update instantly across all screens

### For Developers
To customize the color palette, edit the color constants in `src/desktopMain/kotlin/ui/Theme.kt`:

```kotlin
// Example: Change the dark primary color
private val DarkPrimary = Color(0xFF6B9EFF) // Change this hex value
```

To change the default theme, modify the `isDarkTheme` default value in `MainStore.kt`:

```kotlin
data class MainState(
    // ... other properties ...
    val isDarkTheme: Boolean = true // Set to false for light theme default
)
```

## Color Accessibility
All colors have been chosen to meet WCAG 2.1 AA contrast requirements for accessibility:
- Dark theme: Light text (#E6EDF3) on dark backgrounds
- Light theme: Dark text (#1F2328) on light backgrounds
- Primary and secondary colors provide sufficient contrast against their backgrounds

## Future Enhancements
Potential improvements for the theming system:
- [ ] Persist theme preference to disk using PreferencesStore
- [ ] Add system theme detection (follow OS theme)
- [ ] Add more theme variants (e.g., high contrast, custom colors)
- [ ] Theme preview in preferences screen
- [ ] Per-component color customization

