---
title: "Phase 5: Touch feedback"
status: todo
priority: P2
effort: "8h"
dependencies: [3]
---

# Phase 5: Touch feedback

## Overview

Give touch an answer. The UI has no haptics, a dragged tab card does not lift and its neighbors do not make room, swiping a card away shows nothing behind it, and an AI answer appears all at once.

## Requirements

- [ ] Haptics through `LocalHapticFeedback`, with no permission: closing a tab, a tab swipe that reaches its threshold, picking up and dropping a card, a long press that opens a menu, and the end of pull to refresh.
- [ ] Reorder: the dragged card scales up and gets a shadow, and the other cards of its group move aside while it is over them. A drop outside the group still does nothing.
- [ ] Swipe to close shows a close icon on `errorContainer` behind the card.
- [ ] The tab count changes with a short vertical roll.
- [ ] Ask Skim: suggested questions arrive one after another, a question and its answer fade and rise in, and streamed text does not jump the scroll.
- [ ] The tab swipe caption fades in and out instead of popping.
- [ ] Remove animations: no motion, haptics stay.

## Related files

- Scout first: `app/.../ui/tabs/DragReorder.kt`, `TabsScreen.kt`, `ui/browsing/TabStripSwipe.kt`, `ui/ai/AskPageSheet.kt`, `ui/components/Chat.kt`, `ui/components/FloatingToolbars.kt`
- Tests: `DragReorder` index tests stay; add tests for the make-room order and for which events ask for a haptic

## Implementation Steps

1. Scout the gesture code and decide where each haptic is requested, so a composable sends an event and does not decide tab layout or deletion.
2. Haptics first: they need no golden.
3. Reorder lift and make-room, then swipe-to-close.
4. Ask Skim entrances and the tab count roll.
5. Re-record only goldens whose settled frame changed, and add the rows to the deviations table.

## Todo

- [ ] Scout report
- [ ] Haptics
- [ ] Reorder and swipe to close
- [ ] Ask Skim and tab count
- [ ] Tests and goldens
- [ ] Size measured

## Success Criteria

- Each listed gesture asks for exactly one haptic, proven by a test with a fake `HapticFeedback`.
- Settled frames match the goldens, with and without Remove animations.
- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.
