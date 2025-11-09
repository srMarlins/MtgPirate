# Animated Stepper Component

## Overview
An interactive, animated stepper component has been implemented to guide users through the wizard flow. The stepper provides visual feedback about progress and allows navigation between completed steps.

## Features

### 1. **Visual States**
- **COMPLETED**: Steps that have been finished (shows checkmark ✓)
- **ACTIVE**: The current step being worked on (pulsing animation, highlighted)
- **LOCKED**: Steps that haven't been unlocked yet (greyed out, disabled)

### 2. **Animations**
- **Scale Animation**: Active steps scale up slightly for emphasis
- **Pulse Effect**: Active steps have a continuous pulsing animation
- **Color Transitions**: Smooth color transitions between states
- **Progress Line**: Animated connector lines that fill as you progress

### 3. **Interactive Navigation**
- Click on completed or active steps to navigate
- Locked steps cannot be clicked (prevents skipping ahead)
- Work is preserved when navigating between steps

### 4. **Modern Design**
- Radial gradient backgrounds for active steps
- Elevated cards with shadows when dragging
- Smooth spring animations for a bouncy feel
- Color-coded states for quick visual recognition

## Implementation Details

### Files Created/Modified

#### **StepperComponent.kt** (NEW)
Contains the stepper UI components:
- `AnimatedStepper`: Main container component
- `StepNode`: Individual step circles with animations
- `StepConnector`: Animated progress lines between steps
- `Step` data class and `StepState` enum

#### **MainStore.kt** (MODIFIED)
Added state management:
- `wizardCompletedSteps: Set<Int>` - Tracks which steps are completed
- `CompleteWizardStep(step: Int)` intent - Marks steps as complete
- Preserves all form data in state (deckText, preferences, etc.)

#### **Main.kt** (MODIFIED)
Integrated stepper into UI:
- Wizard steps defined based on state
- Stepper shown only on wizard routes (import, preferences, results)
- Steps marked as complete when navigating forward
- Navigation respects step completion status

## Usage

The stepper automatically:
1. Shows on wizard screens (import, preferences, results)
2. Updates as you progress through the flow
3. Prevents navigation to incomplete steps
4. Allows going back to review/edit previous steps

## Step Flow

```
Step 1: Import Deck
   ↓ (Parse deck, mark complete)
Step 2: Configure
   ↓ (Set preferences, mark complete)
Step 3: Review Results
   ↓ (Export when ready)
```

## State Preservation

All work is preserved in the `MainState`:
- `deckText`: Your imported decklist
- `includeCommanders`, `includeTokens`: Card inclusion preferences
- `variantPriority`: Preferred variant order
- `app.deckEntries`: Parsed deck entries
- `app.matches`: Match results

You can freely navigate between steps without losing any work!

## Visual Design

- **Primary Color**: Used for completed steps and progress lines
- **Secondary Color**: Used for the active step
- **Gray**: Used for locked steps
- **Smooth Animations**: 500ms transitions with FastOutSlowInEasing
- **Spring Physics**: Bouncy scale animations for interactivity

