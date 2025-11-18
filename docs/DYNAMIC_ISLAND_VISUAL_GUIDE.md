# Dynamic Island Visual States for MtgPirate

## Overview
This document shows the various states and visual representations of the Dynamic Island Live Activity during card matching.

## Dynamic Island States

### 1. Minimal View (Multiple Activities)
```
┌─────────────────────────────────┐
│  📋                             │  <- Just emoji when multiple activities
└─────────────────────────────────┘
```

### 2. Compact View (Standard)
```
┌─────────────────────────────────┐
│  📋  •  •  •         12/45      │  <- Phase emoji on left, progress on right
└─────────────────────────────────┘
   ↑               ↑
   Phase        Progress Count
```

### 3. Expanded View (User Taps)
```
┌────────────────────────────────────────────────┐
│  📋 Parsing                           12/45    │
│     Lightning Bolt                             │
│                                                │
│  ▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░  26%         │
│                                                │
│  💵 $34.50                 ⚠️ 3               │
└────────────────────────────────────────────────┘
   ↑                           ↑     ↑
   Current Card              Price  Ambiguous
```

## Phase Progression

### Phase 1: Parsing 📋
**Color:** Blue
**When:** Decklist is being parsed into individual card entries
**Displays:**
- Total cards detected
- Current card name being parsed

```
Compact:  📋  •  •  •  45/45
Expanded: 
  📋 Parsing                    45/45
     Black Lotus
  
  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  100%
```

### Phase 2: Matching 🔍
**Color:** Purple
**When:** Cards are being matched against the catalog
**Displays:**
- Current card being matched
- Progress through deck
- Running price total

```
Compact:  🔍  •  •  •  23/45
Expanded: 
  🔍 Matching                   23/45
     Thoughtseize
  
  ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░  51%
  
  💵 $127.60
```

### Phase 3: Resolving ⚠️
**Color:** Orange
**When:** Ambiguous cards need manual selection
**Displays:**
- Number of cards needing resolution
- Ambiguity warning badge
- Current total

```
Compact:  ⚠️  •  •  •  40/45
Expanded: 
  ⚠️ Resolving                  40/45
     Force of Will (multiple variants)
  
  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░  89%
  
  💵 $234.80              ⚠️ 5
```

### Phase 4: Exporting 📤
**Color:** Green
**When:** Generating CSV export
**Displays:**
- Export progress
- Final card count
- Final price

```
Compact:  📤  •  •  •  45/45
Expanded: 
  📤 Exporting                  45/45
     Preparing CSV...
  
  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  100%
  
  💵 $287.40
```

### Phase 5: Complete ✅
**Color:** Green
**When:** Export finished successfully
**Displays:**
- Success indicator
- Final statistics
- Auto-dismisses after 3 seconds

```
Compact:  ✅  •  •  •  Complete
Expanded: 
  ✅ Complete                   45/45
     $287.40 - 45 cards
  
  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  100%
  
  💵 $287.40              ✓ Done
```

### Error State ❌
**Color:** Red
**When:** An error occurs during processing
**Displays:**
- Error indicator
- Error message

```
Compact:  ❌  •  •  •  Error
Expanded: 
  ❌ Error
     Failed to match cards
  
  See app for details
```

## Lock Screen View

The lock screen shows a more traditional notification-style Live Activity:

```
┌──────────────────────────────────────────┐
│  🏴‍☠️ MtgPirate                            │
│                                          │
│  🔍 Matching                    23/45    │
│     Thoughtseize                         │
│                                          │
│  ▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░  51%      │
│                                          │
│  💵 $127.60                              │
└──────────────────────────────────────────┘
```

## Interaction Model

### Tap Behavior
- **Tap Minimal/Compact View:** Expands to show full details
- **Tap Expanded View:** Opens MtgPirate app to the current screen
- **Swipe Away:** Dismisses the Live Activity (ends tracking)

### Automatic Updates
Live Activity updates automatically when:
1. New cards are parsed
2. Matches are found
3. User resolves ambiguities
4. Wizard steps advance
5. Export completes

### Battery & Performance
- **Update Frequency:** On state change only (not polling)
- **Memory Usage:** ~1-2 MB
- **Battery Impact:** Minimal (native ActivityKit)
- **Max Duration:** 8 hours (iOS limit)

## Color Scheme

### Phase Colors
- **Parsing:** `Color.blue` (#007AFF)
- **Matching:** `Color.purple` (#AF52DE)
- **Resolving:** `Color.orange` (#FF9500)
- **Exporting:** `Color.green` (#34C759)
- **Complete:** `Color.green` (#34C759)
- **Error:** `Color.red` (#FF3B30)

### Progress Bar
- Background: `Color.gray.opacity(0.3)`
- Fill: Phase-specific color
- Height: 6dp (compact), 8dp (lock screen)
- Corner Radius: 4dp

## Typography

### Dynamic Island
- **Phase Name:** `.headline` / `.semibold`
- **Card Name:** `.subheadline` / `.regular`
- **Progress Count:** `.title2` / `.bold`
- **Price:** `.caption` / `.medium`

### Lock Screen
- **Phase Name:** `.headline` / `.semibold`
- **Card Name:** `.subheadline` / `.regular`
- **Progress Count:** `.title2` / `.bold`

## Accessibility

- All states support VoiceOver
- Progress updates announced
- Phase transitions spoken
- High contrast mode compatible
- Supports Dynamic Type (text scaling)

## Future Enhancements

Potential additions:
- [ ] Card art thumbnail in expanded view
- [ ] Set symbol icons
- [ ] Custom animations for phase transitions
- [ ] Haptic feedback on phase changes
- [ ] Deep link taps to specific wizard steps
- [ ] Share sheet integration from expanded view
