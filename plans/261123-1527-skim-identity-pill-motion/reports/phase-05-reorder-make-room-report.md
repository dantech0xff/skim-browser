# Reorder: neighbours make room

Status: done. While a dragged tab card is over a card of its own group, the other cards of that group move aside to show the order a drop would give. The drop commits that order through the existing `onMove`. Both final gates pass, and no golden changed.

## Changes by file

- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/DragReorder.kt`
  - New `ReorderPreview(tabId, toIndex)` and the pure `previewSections(sections, preview)`. The function takes the dragged tab out of its section and puts it back at the clamped index. Every other section, and the whole list when nothing moves, is returned as the same instance.
  - `tabDragReorder` takes two new parameters: `indexInGroup` (a tab's index in its group as the grid shows it now) and `onPreview(toIndex: Int?)`.
    - At pick up it reports the card's own index.
    - On each drag event it evaluates `targetIndexInGroup` against the slots as they are laid out now. The pointer used is the card's centre at drag start plus the drag distance. `onPreview` is called only when the index changes, and over another group it gets the original index back.
    - Before each preview write it calls `requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)`.
  - The dragged card's translation is `(drag − (slotNow − slotAtStart)) × hold`. It is computed in `graphicsLayer {}`, and the slot is read from `gridState.layoutInfo` there.
    - `hold` is a new `Animatable`. It is 1 while the card is dragged, and on release it animates to 0 with `defaultSpatialSpec`, or snaps there with Remove animations.
    - Because `slotNow` is read live, the card settles into whichever slot it ends up in.
  - Drop: if the previewed index differs from the original, `onMove` is called once. The lift and `hold` then settle, and after that the preview is cleared, with the scroll position kept.
  - Cancel: the preview goes back to the original index at once, the card settles, then the preview is cleared. Nothing is called.
  - Haptics and lift values are unchanged. `dropIndex` was replaced by `keyUnder`.
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/TabsScreen.kt`
  - The screen holds `reorder: ReorderPreview?` and renders `previewSections(state.sections, reorder)`. It passes `indexInGroup`, built from the order it shows, to each card.
  - The dragged card, and every card when Remove animations is on, uses `animateItem(placementSpec = null)`. The others keep plain `animateItem()`.
  - When a card finishes settling, it clears the preview only if that preview is still its own.
  - A `LaunchedEffect(tabKeys)` drops a preview whose tab has closed.
- `docs/design-guidelines.md`: the row "Tabs (09): thẻ được nhấc lên để sắp xếp" now describes the make-room behaviour (in Vietnamese) and lists the new tests.
- `plans/261123-1527-skim-identity-pill-motion/phase-05-touch-feedback.md`: I ticked the make-room requirement (with an "As built" note) and the Todo boxes "Reorder and swipe to close" and "Tests and goldens". "Size measured" stays open.

## Deviations from the decided design

1. **Group index taken from the hit card's key.** `targetIndexInGroup` (unchanged) counts only the visible cards of the group. `keyUnder` turns its result into the key of the card under the pointer, and the index comes from the group order the grid shows (`indexInGroup`). The old `dropIndex` passed the index among visible cards straight to `onMove`. That was wrong when the top of a group was scrolled off screen.
2. **`pointerInput` now sits before `graphicsLayer` in the modifier chain.** Inside the layer, the 1.04 scale shrinks drag deltas by about 4%, so the card lagged behind the finger. Outside the layer, the deltas are real finger movement and the card stays exactly under the finger. Hit testing at the down event is the same, because the card is at rest then.
3. **A cancelled drag now settles back with the spatial spring instead of snapping.** The neighbours animate back on a cancel, so a snapping card would look broken next to them. With Remove animations it still snaps.
4. **The scroll position is also kept when the preview is cleared after settling.** This matters only when the ViewModel did not apply the move and the real order comes back.

## Tests

- `app/src/test/java/com/dantech/skimbrowser/ui/tabs/ReorderPreviewTest.kt` (pure JVM, 8 tests) covers:
  - no preview
  - moving within a group
  - first and last positions
  - an index past the end
  - the card's own index
  - a group of one
  - an id that is in no section
  - other sections untouched (the same instances)
- `app/src/test/java/com/dantech/skimbrowser/ui/tabs/TabsReorderTest.kt` (Robolectric, on the real `TabsScreen` grid, 6 tests):
  - With the finger down over a neighbour, the neighbour sits in the dragged card's original slot and the dragged card's centre is under the finger.
  - The drop calls `onMove` once with the previewed index (`move:a:2`). The state is updated the way the ViewModel would, and after settling all three cards are in their new slots.
  - When the ViewModel does not apply the move, the cards return to the real order.
  - Over a card of another group the original order shows again, after a preview first, and the drop calls nothing.
  - A cancelled drag restores the order and calls nothing.
  - With Remove animations on, the neighbour is in its new slot within 4 frames, with the clock paused.
- Proof that the tests depend on the change:
  - With `previewSections` bypassed in `TabsScreen`, all 6 `TabsReorderTest` tests failed.
  - With the Remove-animations branch removed, only the Remove-animations test failed.
  - The source was restored after each check.
- `DragReorderTest` and `TabsTouchFeedbackTest` are unchanged and pass.

## Gates

- `./gradlew :app:testPlayDebugUnitTest --no-configuration-cache` ended with `BUILD SUCCESSFUL in 2m 11s` and `211 actionable tasks: 8 executed, 203 up-to-date`, with no failed tests.
- `./gradlew :app:verifyRoborazziPlayDebug --continue --no-configuration-cache` ended with `BUILD SUCCESSFUL in 2m 10s` and `212 actionable tasks: 2 executed, 210 up-to-date`.
- `git status --short -- app/src/test/screenshots` printed nothing.

## Not verified without a device

- How the motion feels on a device: the neighbours' `animateItem` spring next to the dragged card's spatial settle, and any frame drops while the preview changes.
- That the gesture holds through slot changes under real touch input. Robolectric shows it does, and the item keeps its composition because keys are stable.
- Whether `requestScrollToItem` keeps the position when the first visible item is the one that moves. No test covers this.
- Remove animations through the system setting on a device, and how `animateItem`'s fade behaves there.

## Unresolved questions

- The cancel now settles with a spring instead of snapping (deviation 3). Is that acceptable, or should a cancel snap as it did before?
- When the preview changes, every visible card item recomposes once, because the item content reads `reorder`. This happens once per index change, not per pointer event. Should it be narrowed further, for example by passing a per-card boolean?

## Review follow-up

- The four departures are accepted. The spring on a cancel stays: the neighbours move back at the same time.
- The recomposition of the visible cards once per previewed index stays as it is: the grid re-emits its items for a new order anyway.
- **Departure 1 fixed a defect that was already there.** With the top of a group scrolled away, a drop asked for the card's index among the cards on screen, so the tab landed too early in the group. `TabsReorderTest.withTheTopOfTheGroupScrolledAwayTheDropAsksForTheIndexInTheWholeGroup` covers it: with the old index put back for one run it failed with `move:t14:9` where `move:t14:15` is right.
- The gates were run again on the whole tree after that test: `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass, and no golden changed.

