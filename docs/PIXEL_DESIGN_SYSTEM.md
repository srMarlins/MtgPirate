# Pixel Design System

> **Philosophy**: "Retro Soul, Modern Body"

The MtgPirate design system combines the nostalgic aesthetic of 8-bit/16-bit RPGs with the fluidity and responsiveness of modern UI frameworks. It is not a low-resolution emulation; rather, it is a high-fidelity reinterpretation of pixel art principles using vector paths and smooth animations.

## Core Principles

1.  **Chamfered, Not Rounded**: We avoid standard rounded corners. Instead, we use "chamfered" (cut) corners to simulate pixel stepping while maintaining vector sharpness.
2.  **Alive & Glowing**: The UI should feel magical. Borders glow, buttons pulse, and sparkles appear. Static is boring.
3.  **Deep & Layered**: Use borders, shadows, and scanlines to create depth. The interface should feel like a CRT screen or a magical tome.
4.  **Precise**: Borders are drawn using custom paths to ensure perfect alignment with clipping shapes. No anti-aliasing bleed.

## Design Tokens

### Colors

The palette is inspired by magical rarities and retro fantasy consoles.

| Name | Hex | Usage |
|------|-----|-------|
| **Mystical Purple** | `#B794F6` | Primary brand color, borders, active states |
| **Crystal Blue** | `#63B3ED` | Secondary accents, gradients |
| **Golden Treasure** | `#FBD38D` | Highlights, stars, rare items |
| **Muted Grey** | `#6B7280` | Inactive borders, placeholders |
| **Error Red** | `#F44336` | Error states, alerts |

### Shapes

The fundamental building block is the **Pixel Shape**.

-   **Corner Size**: Standard is `6.dp` or `9.dp`.
-   **Border Width**: Standard is `2.dp` or `3.dp`.

```kotlin
// The single source of truth for shape geometry
PixelShape(cornerSize = 9.dp)
```

## Components

### 1. Pixel Border Container

The foundational container for almost all UI elements. It handles clipping, background, and the custom pixel border drawing.

```kotlin
PixelBorderContainer(
    borderWidth = 2.dp,
    cornerSize = 6.dp,
    glowAlpha = 0.3f, // Adds a magical outer glow
    backgroundColor = MaterialTheme.colors.surface
) {
    // Content goes here
}
```

### 2. Pixel Button

A highly interactive button with pulse animations and haptic feedback (on iOS).

-   **Variants**: Primary, Secondary, Surface.
-   **Behavior**: Pulses when enabled, greys out when disabled.

```kotlin
PixelButton(
    text = "IMPORT DECK",
    onClick = { ... },
    variant = PixelButtonVariant.PRIMARY
)
```

### 3. Pixel Text Field

A custom input field that eschews the standard Material underline for a contained, pixel-bordered box.

-   **Features**: Custom "block" cursor support, label prefix (`✦ Label`).

```kotlin
PixelTextField(
    value = text,
    onValueChange = { ... },
    label = "DECKLIST",
    placeholder = "Paste cards here..."
)
```

### 4. Pixel Card

Used for grouping content. Can be set to `glowing = true` to indicate active or highlighted state.

```kotlin
PixelCard(glowing = true) {
    Text("This card is glowing!")
}
```

### 5. Pixel Badge

Small status indicators for set codes, card types, or quantities.

```kotlin
PixelBadge(text = "M11", color = MaterialTheme.colors.secondary)
```

## Visual Effects

### Scanlines

Simulates a CRT monitor effect. Usually placed as an overlay on the entire screen or specific containers.

```kotlin
ScanlineEffect(alpha = 0.05f)
```

### Magical Sparkles

Particle effect used for loading states or celebrating success.

```kotlin
MagicalSparkles(count = 5, color = Color(0xFFB794F6))
```

### Fantasy Star

A rotating, twinkling 4-pointed star used in headers and dividers.

```kotlin
FantasyStarIcon(animated = true)
```

## Implementation Details

### The `pixelBorder` Modifier

We do not use the standard Compose `BorderStroke`. Instead, we use a custom `Modifier.pixelBorder` that draws a path exactly matching the `PixelShape`.

**Why?**
Standard borders in Compose are drawn centered on the layout bounds. When using complex shapes like chamfered corners, this can lead to gaps between the background clip and the border stroke. Our custom modifier draws the border *inside* the path to ensure perfect alignment.

```kotlin
fun Modifier.pixelBorder(
    borderWidth: Dp,
    cornerSize: Dp,
    ...
) = this.drawWithContent {
    drawContent()
    // Draw custom path overlay...
}
```

### iOS Integration

On iOS, we blend this pixel aesthetic with native behaviors:
-   **Haptics**: Pixel buttons trigger `UIImpactFeedbackGenerator`.
-   **Gestures**: Drag-and-drop uses native-feeling spring animations but renders with pixel borders.
-   **Hybrid Components**: `ModernIosReorderableListWithPixelStyle` allows toggling between "Modern" (rounded) and "Pixel" (chamfered) styles for A/B testing or user preference.
