# Drag-and-Drop Implementation Summary

## What Was Implemented

### ✅ Full Drag-and-Drop Functionality
The variant priority list now supports **true drag-and-drop reordering**. Users can:
1. Click and hold anywhere on a card
2. Drag it up or down  
3. Watch as items swap positions in real-time
4. Release to drop it in the new position

### Key Features

#### Visual Feedback
- **Elevation Animation**: Card rises from 1dp to 8dp when dragging
- **Background Highlight**: Primary color with alpha when active
- **Drag Handle Color**: ☰ changes to primary color during drag
- **Z-Index Layering**: Dragged item stays on top of others

#### Drag Detection
- Uses `detectDragGestures` from Compose Foundation
- This is a standard, stable API that works across Compose platforms
- Tracks vertical drag distance (y-axis only)
- Calculates target position based on accumulated drag offset
- Swaps items when drag crosses 56dp threshold (item height)

#### Dual Control Methods
Users can choose between:
1. **Drag-and-Drop**: Natural, direct manipulation - click and drag anywhere on the card
2. **Arrow Buttons**: Precise, single-step movements

### Technical Details

#### Why detectDragGestures Instead of dragAndDropSource/Target?
The implementation uses `detectDragGestures` (from `androidx.compose.foundation.gestures`) instead of the newer `dragAndDropSource`/`dragAndDropTarget` API because:

1. **Stability**: `detectDragGestures` is a stable, well-tested API
2. **Compatibility**: Works reliably across Compose Desktop/Multiplatform
3. **Simplicity**: Simpler API for reordering within a single list
4. **No External Dependencies**: Part of the core Compose Foundation library

The `dragAndDropSource`/`dragAndDropTarget` API (marked `@ExperimentalFoundationApi`) is better suited for:
- Cross-application drag and drop
- Drag and drop between different windows
- Platform-specific clipboard integration
- Complex drag data transfer scenarios

For our use case (reordering items within a single list), `detectDragGestures` is the ideal choice.

#### State Management
```kotlin
var draggedIndex: Int? = null        // Currently dragging item
var dragOffset: Float = 0f            // Accumulated drag distance
var variantList: List<String>        // The ordered list
```

#### Drag Calculation
```kotlin
onDrag = { delta ->
    dragOffset += delta
    val itemHeight = 56 // Approximate item height in dp
    val targetIndex = (index + (dragOffset / itemHeight).toInt())
        .coerceIn(0, variantList.size - 1)
    
    if (targetIndex != index) {
        // Swap items and update state
        // Reset offset after swap
    }
}
```

#### Animation
```kotlin
val elevation by animateDpAsState(
    if (isDragging) 8.dp else 1.dp
)
```

### Files Modified
- ✅ `src/desktopMain/kotlin/ui/PreferencesWizardScreen.kt`
  - Added drag gesture detection using `pointerInput` + `detectDragGestures`
  - Implemented state tracking for drag operations
  - Added visual feedback for dragging state
  - Uses Column (not LazyColumn) for simpler drag handling

### Build Status
- ✅ Compiles successfully
- ✅ No errors or warnings (except unused import)
- ✅ Ready for testing and use

### Usage
Run the application with:
```bash
./gradlew run
```

Navigate to Step 2 (Preferences) and try:
1. **Dragging**: Click and drag any card up or down (works immediately, no long-press needed)
2. **Arrow Buttons**: Click ↑ or ↓ to move items
3. **Adding**: Type in "Add Variant" field and click "Add"
4. **Removing**: Click × on any item to remove it

### Visual Indicators
- **☰ (Drag Handle)**: Shows item is draggable, turns blue when dragging
- **Priority Numbers**: Auto-update as you reorder (1., 2., 3.)
- **Elevated Card**: Rises and highlights when being dragged
- **Smooth Transitions**: Animated elevation changes

## Implementation Notes

### Why Column Instead of LazyColumn?
The drag-and-drop implementation uses a regular `Column` instead of `LazyColumn` because:
1. Small fixed number of items (typically 3-10 variants)
2. Simpler state management for drag operations
3. Immediate visual feedback without complex scroll position tracking
4. All items visible at once (200dp container height)

### Future Enhancements (Optional)
- Add haptic feedback on drag start/end (if platform supports)
- Animate items sliding into new positions
- Add long-press delay before drag activates (currently instant)
- Support for horizontal drag gestures
- Snap-to-position animation when releasing drag

