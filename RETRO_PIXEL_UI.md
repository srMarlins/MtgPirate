# 🎮 Retro Pixel UI System

## Overview
Your MTG Proxy Tool now features a completely custom retro pixelated UI with a modern artsy aesthetic! The design combines elements from classic arcade cabinets, CRT monitors, and early handheld gaming systems.

## 🎨 Color Schemes

### Dark Theme - "CRT Monitor / Arcade Cabinet"
- **Primary**: Bright cyan (#00FFFF) - Classic terminal green
- **Secondary**: Hot magenta (#FF00FF) - Neon accent
- **Background**: Deep space blue-black (#0A0E27)
- **Surface**: Slightly lighter (#1A1F3A)
- **Text**: Bright green (#00FF88) - Terminal-style text

### Light Theme - "Game Boy / Retro Handheld"
- **Primary**: Dark green (#0F380F) - Game Boy dark
- **Secondary**: Lime green (#8BAC0F) - Classic accent
- **Background**: Game Boy light green (#9BBC0F)
- **Surface**: Slightly darker (#8BAC0F)
- **Text**: Dark green (#0F380F) - High contrast

## 🎯 Custom Components

### PixelButton
Retro-styled buttons with:
- Thick pixel borders (3dp)
- Animated glow effect when enabled
- Three variants: PRIMARY, SECONDARY, SURFACE
- Uppercase text with monospace font
- Pulse animation on hover

Usage:
```kotlin
PixelButton(
    text = "NEXT",
    onClick = { /* action */ },
    variant = PixelButtonVariant.PRIMARY,
    enabled = true
)
```

### PixelTextField
Monospace text fields with:
- Pixel art borders
- Label with arrow prefix (▸)
- Glowing borders
- Custom cursor styling

Usage:
```kotlin
PixelTextField(
    value = text,
    onValueChange = { text = it },
    label = "INPUT.TXT",
    placeholder = "Enter text..."
)
```

### PixelCard
Container component with:
- Pixel borders with corner details
- Optional glowing animation
- Surface background
- Padding included

Usage:
```kotlin
PixelCard(glowing = true) {
    // Content here
}
```

### PixelBadge
Small labeled tags with:
- Pixel borders
- Custom background color
- Uppercase text

Usage:
```kotlin
PixelBadge(
    text = "STEP 1/3",
    color = MaterialTheme.colors.secondary
)
```

### PixelDivider
Animated or static dividing lines:
- Animated dashed line option
- Customizable thickness
- Retro dotted pattern

Usage:
```kotlin
PixelDivider(animated = true, thickness = 3.dp)
```

### PixelProgressBar
Retro-styled progress indicator:
- Animated fill with gradient
- Percentage display
- Pixel borders

Usage:
```kotlin
PixelProgressBar(
    progress = 0.75f,
    height = 24.dp,
    showPercentage = true
)
```

### ScanlineEffect
CRT monitor scanline overlay:
- Subtle horizontal lines
- Adjustable alpha/transparency
- Adds authentic retro feel

Usage:
```kotlin
ScanlineEffect(alpha = 0.03f)
```

### BlinkingCursor
Animated terminal-style cursor:
- Classic █ block character
- Smooth fade animation
- Perfect for terminal aesthetics

Usage:
```kotlin
BlinkingCursor()
```

## 🎭 Typography

All text uses **monospace fonts** to emulate pixel font appearance:
- Headers: Bold, increased letter spacing
- Body: Standard weight with subtle spacing
- Buttons: Bold, uppercase, wide letter spacing
- Captions: Smaller size with tight spacing

Letter spacing creates that authentic "pixel font" feel even with standard fonts.

## ✨ Visual Effects

### Pixel Borders
Custom border drawing function that creates pixel-art style borders with:
- Chunky corners (pixel art style)
- Optional glow effect
- Inner shadow for depth
- Animated on active elements

### Animations
- **Pulse**: Breathing effect on active elements
- **Bounce**: Spring animation for interactions
- **Glow rotation**: Infinite rotation for special effects
- **Progress fill**: Smooth horizontal gradient animation
- **Scanlines**: Moving horizontal patterns

## 🎪 UI Screens

### Import Screen
- Large pixel-bordered card for text input
- Animated cursor for title
- Pixel badge showing step number
- Scanline effects overlay

### Stepper Component
- Pixel art nodes with numbers/checkmarks
- Animated connectors with moving dots
- Glow effects on active step
- Spring bounce animations
- Clickable step navigation

### Top Bar
- Gradient background
- Pixel art decorations (█▓▒░)
- Custom theme toggle button
- All buttons use PixelButton component
- Animated divider below stepper

## 🎨 Design Philosophy

The retro pixel UI combines:
1. **Nostalgia**: Classic gaming aesthetics from 80s-90s
2. **Functionality**: Modern usability and accessibility
3. **Animation**: Smooth, delightful interactions
4. **Contrast**: High visibility and readability
5. **Personality**: Unique character that stands out

## 🚀 Extending the UI

To add new pixel-styled components:

1. Use the `pixelBorder()` modifier for consistent borders
2. Apply monospace typography from theme
3. Add glow animations for interactive elements
4. Use colors from MaterialTheme.colors
5. Include uppercase text for button-like elements

Example:
```kotlin
@Composable
fun CustomPixelComponent() {
    Box(
        modifier = Modifier
            .pixelBorder(borderWidth = 2.dp, enabled = true, glowAlpha = 0.3f)
            .background(MaterialTheme.colors.surface)
            .padding(16.dp)
    ) {
        Text(
            "CUSTOM COMPONENT",
            style = MaterialTheme.typography.button,
            color = MaterialTheme.colors.primary
        )
    }
}
```

## 🎮 Theme Toggle

Press the sun/moon button in the top bar to switch between:
- **Dark Mode**: CRT/Arcade aesthetic (default)
- **Light Mode**: Game Boy aesthetic

Both themes maintain the pixel art styling with appropriate color adjustments.

## 📦 Files Modified

- `ui/Theme.kt` - Color schemes and typography
- `ui/PixelComponents.kt` - Custom pixel art components
- `ui/StepperComponent.kt` - Retro stepper with animations
- `app/Main.kt` - Updated to use pixel components

## 🎯 Future Enhancements

Potential additions:
- Custom pixel font integration
- More sound effects triggers
- Particle effects on interactions
- Pixel art icons/sprites
- Additional color schemes (NES, Commodore 64, etc.)
- Screen shake effects
- Pixel art backgrounds/patterns

---

**Enjoy your retro gaming-inspired MTG Proxy Tool! 🎮✨**

