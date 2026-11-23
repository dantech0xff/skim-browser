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

- [x] Haptics through `LocalHapticFeedback`, with no permission. As built:
  - A threshold tick each time a drag passes the point where a release acts: closing a tab card by a swipe, switching tab by a swipe on the pill, and reloading by a pull. The release gives nothing more, and neither does the card's Close button, which is a plain tap.
  - Picking a card up gives a long press, dropping it a gesture end, a cancelled drag nothing.
  - A long press that opens a menu already gave one: Compose's `combinedClickable` asks for it. A test now proves it for the Home shortcut, the News card and a list row.
  - Android before 14 does not know the threshold tick, and before 11 not the gesture end. androidx.core falls back to a context click there, so no wrapper was added; `HapticFallbackTest` fails if a library update drops the fallback.
- [x] Reorder: the dragged card scales to 1.04, gets a shadow and is drawn above the others.
- [x] Reorder: the other cards of its group move aside while it is over them. A drop outside the group still does nothing. As built: the Tabs screen previews the order a drop would give and the grid's placement animation moves the neighbours; the dragged card stays under the finger, the drop calls `onMove` once and the card settles with the spatial spring. A cancel, or a card over another group, shows the real order. Remove animations puts the neighbours in place at once.
- [x] Swipe to close shows a close icon on `errorContainer` behind the card.
- [x] The tab count changes with a short vertical roll.
- [x] Ask Skim: suggested questions arrive one after another, and a question and its answer fade and rise in. The chat goes to a question asked here and follows its answer, and follows while the person is at the end of a chat longer than the sheet. A summary that streams in does not move a reader at the top. Before, the chat never moved by itself, so an answer grew out of view below.
- [x] The tab swipe caption fades in and out instead of popping.
- [x] Remove animations: no motion, haptics stay.

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

- [x] Scout report
- [x] Haptics
- [x] Reorder and swipe to close
- [x] Ask Skim and tab count
- [x] Tests and goldens
- [ ] Size measured

## Success Criteria

- Each listed gesture asks for exactly one haptic, proven by a test with a fake `HapticFeedback`.
- Settled frames match the goldens, with and without Remove animations.
- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.
