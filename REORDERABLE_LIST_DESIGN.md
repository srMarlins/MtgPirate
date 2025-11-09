# Reorderable Variant Priority List - Implementation Details

## Visual Design

Each variant item in the list appears as a card with the following elements:

```
┌─────────────────────────────────────────────────────────────┐
│  ☰  1. Regular                              ↑  ↓  ×         │
├─────────────────────────────────────────────────────────────┤
│  ☰  2. Foil                                 ↑  ↓  ×         │
├─────────────────────────────────────────────────────────────┤
│  ☰  3. Holo                                 ↑  ↓  ×         │
└─────────────────────────────────────────────────────────────┘

[Add Variant Field]  [Add Button]
```

## Components

### Drag Handle (☰)
- **FULLY FUNCTIONAL** drag-and-drop indicator
- Click and drag anywhere on the card to reorder
- Changes to primary color when dragging
- Positioned on the left side
- Semi-transparent when not dragging

### Index Number (1., 2., 3.)
- Shows the priority order (1 = highest priority)
- Auto-updates when items are reordered
- Caption style, semi-transparent

### Variant Name
- Main text showing the variant type (e.g., "Regular", "Foil")
- Body1 typography style

### Action Buttons
- **↑ (Move Up)**: Swaps item with the one above it
- **↓ (Move Down)**: Swaps item with the one below it
- **× (Remove)**: Removes the item from the list (red color)

### Add Variant Section
- Text field for entering new variant names
- "Add" button to append to the list
- Validates that variant name isn't blank or duplicate

## User Interactions

1. **Drag-and-Drop Reordering** ✅ IMPLEMENTED:
   - Click and hold anywhere on the card
   - Drag up or down to move the item
   - Items swap positions as you drag
   - Card elevation increases while dragging
   - Background highlight shows active drag
   - Release to drop in new position

2. **Arrow Button Reordering**:
   - Click ↑ to move item up in priority
   - Click ↓ to move item down in priority
   - Instant feedback with immediate reordering

3. **Adding**:
   - Type variant name in the "Add Variant" field
   - Click "Add" button
   - New variant appears at the bottom of the list

4. **Removing**:
   - Click × button on any item
   - Item is removed from the list immediately

## Technical Implementation

### Drag-and-Drop System
- Uses `detectDragGestures` from Compose foundation
- Tracks `draggedIndex` and `dragOffset` state
- Calculates target index based on drag distance
- Animates elevation with `animateDpAsState`
- Uses `zIndex` to keep dragged item on top
- Background color changes during drag for visual feedback

### State Management
- `variantList` holds the current order
- `draggedIndex` tracks which item is being dragged
- `dragOffset` accumulates drag distance
- Changes propagated via `onVariantPriorityChange` callback
- Default values: ["Regular", "Foil", "Holo"]

### Visual Feedback
- Elevation animates from 1dp to 8dp when dragging
- Background changes to primary color with alpha when dragging
- Drag handle (☰) changes to primary color when active
- Smooth transitions with Compose animations

## UX Features

- ✅ **True drag-and-drop** - Click and drag anywhere on the card
- ✅ **Visual feedback** - Elevation, color, and position changes
- ✅ **Dual input methods** - Drag OR use arrow buttons
- ✅ **Smooth animations** - Animated elevation and color transitions
- ✅ **Auto-numbering** - Clear priority indication
- ✅ **Duplicate prevention** - When adding variants
- ✅ **Real-time updates** - Changes apply immediately

