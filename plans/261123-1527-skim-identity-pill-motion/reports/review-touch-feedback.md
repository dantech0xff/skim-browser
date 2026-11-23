# Review: touch feedback (6abae3f) and make-room reorder (ded171c)

Read-only review. No Gradle run. Library behaviour checked by reading the bytecode of material3-android 1.5.0-beta01 and foundation-android 1.12.1 from the Gradle cache.

## Findings

### 1. Medium: the Ask sheets treat turns loaded at open as "asked here" and jump to the end
- `app/src/main/java/com/dantech/skimbrowser/ui/components/Chat.kt:212,222-229`, `ui/ai/AskPageSheet.kt:114`, `ui/ai/AskTabsSheet.kt:150`
- Both VMs start from an empty state (`MutableStateFlow(AskPageUiState())`, `AskTabsViewModel.kt:120`) and fill `turns` later from `open()` (`adopt`/`openSaved`/`restore`). So the first composition has `turns == 0`, and `asked` and `openedTurns` are both 0.
- Steps: ask a long question on a page, close the sheet, then open Ask Skim again on that tab (or open a saved chat). When `publish` delivers N turns, `LaunchedEffect(turns)` sees `N > 0`, sets `following = true` and scrolls to the end. The reader lands below the summary and the chat stays pinned to the end, although the KDoc says only "a question asked here" moves the chat. `openedTurns == 0` also marks every restored turn as one that can arrive. Today that does not show only because `generating` is false on those paths.
- No test catches this. `AskSkimMotionTest.showPage` puts the full state in before the first composition, so a VM-shaped load never happens in the tests.
- Smallest fix: take the baseline when the first loaded state arrives, not at first composition. One way is a `loaded` flag in the UI state, with `asked` and `openedTurns` set once it turns true. Another is for the VM to expose the turn count it opened with. Add a test that starts from `AskPageUiState()` and then sets turns.

### 2. Medium: a swipe that switches tab drops the caption at once instead of fading it
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/TabStripSwipe.kt:80,88,99,261-262`
- `heldCaption` looks up `heldCaptionId` again among the current `previous`/`next`. On a committed swipe, `onSelect(chosen.id)` (it is `tabs::select`, synchronous) makes the chosen tab current. `BrowserRoute` does not key the page by tab, so this `TabStripSwipe` stays in composition. Its new neighbours are never the chosen id. On the next frame `heldCaption` is null, and the exiting `AnimatedVisibility` draws nothing.
- The fade only plays on a cancelled swipe, which is the one path `theCaptionFadesOutOverSeveralFramesAndThePillStaysPut` tests (30 dp is short of the threshold and the neighbours are fixed).
- Fix: hold the `TabNeighbor` itself, plus its `forward` flag, in state. Set it during the drag and keep it until the exit ends. Add a test where `onSelect` swaps the neighbours.

### 3. Low: the drop's settle cancels a scroll the person has already started
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/DragReorder.kt:172,248-250`
- `settle` always calls `grid.keepScrollPosition()` once the spring ends. `LazyGridState.requestScrollToItem` launches an empty `scroll {}` when `isScrollInProgress` (checked in the 1.12.1 bytecode), and its KDoc says it cancels any scroll in progress.
- Steps: drop a card, then fling the grid before the expressive spring has settled. When `settle` reaches line 172, the fling stops dead.
- Fix: call `keepScrollPosition()` only when clearing the preview will change the order (`state.sections != previewSections(...)`), or skip it while `grid.isScrollInProgress`.

### 4. Low: a dragged card that leaves composition leaves `reorder` set
- `DragReorder.kt:199-202` with `TabsScreen.kt:243-246,324-331`
- When the item is disposed mid-drag, the pointer coroutine's cancel runs `detectDragGesturesAfterLongPress`'s `onDragCancel`, and that is dispatched after `LaunchedEffect(tabKeys)` has already run. `show(startIndex)` writes `reorder = ReorderPreview(id, startIndex)` again. `settle()` then launches on the disposed `rememberCoroutineScope`, so `preview(null)` never runs. This happens when the tab closes during the drag (page `window.close()`, or the card's Close tapped with a second finger).
- Effect: `reorder` keeps the dead id until the next pick-up. If Undo brings the tab back under the same id, it is drawn at `startIndex` rather than its real position, and with `placementSpec = null`.
- Fix: clear in a `DisposableEffect(itemKey) { onDispose { if (startSlot != null) preview(null) } }`, or do the `tabKeys` check in a `snapshotFlow` that also watches `reorder`.

## Categories with no finding
- **tabDragReorder state.** Pick up, drag, drop, cancel and a second pick-up while the card settles (`pickUp` cancels `settling`) all end with no card translated or lifted. `onMove` is called at most once, from `onDragEnd` only. A change of `groupId` or `enabled` restarts `pointerInput` and goes through the cancel path. State is `remember(itemKey)` inside each lazy item, so nothing leaks between cards. Another card's settle does not clear a newer preview (the `tabId` guard).
- **previewSections and the index mapping.** Remove-then-insert matches `TabsViewModel.moved`. Headers are not slots. A group of one is handled. With part of the group off screen, `keyUnder` returns a key and `indexInGroup` turns it into the index in the whole group. A card of another group gives `from`, so the drop calls nothing.
- **Haptics.** None fires twice for one crossing. The tab strip needs a neighbour that exists. The pull is gated on `enabled` and `refreshing`, and `refreshing` is set synchronously in `refresh()`. `requireOffset()` sits behind `width > 0`, and `onSizeChanged` only reports after the measure that sets the anchors, so it cannot throw before layout.
- **Modifier.entrance.** `remember` keeps the values, so recomposition or a flip of `play` does not replay it. It is not inside a lazy list, so scrolling does not replay it. Switching Remove animations restarts the effect, which snaps to 1/0. There is no path where it stays at alpha 0 while still composed.
- **TabsCountIcon.** It rolls up as the count grows and down as it shrinks. 99 to 100 widens through `SizeTransform`. At 100 and above, `contentKey` prevents the roll. `clearAndSetSemantics` keeps both numbers hidden during the transition.
- **SwipeCloseBackground.** It is not drawn at rest (`offset == 0`/NaN gives Settled). It recomposes only when the direction changes (`derivedStateOf`). In RTL, the anchors are not mirrored but the offset is applied relative to layout direction, so `StartToEnd` with `CenterStart` lands on the uncovered side. I read this in the bytecode but did not check it on a device. No RTL test exists.

## Tests that prove less than their name
- `AskSkimMotionTest`: every case seeds the state before composition (see finding 1).
- `TabStripSwipeTest.theCaptionFadesOut...`: checks only that the node exists, not its alpha, and only for a cancel (see finding 2).
- `TabsTouchFeedbackTest`: "drawn above the others" (`zIndex`) has no assertion. The mark is tested in LTR only.
- `TabsReorderTest`: there is no case for a second pick-up during the settle, or for the dragged tab leaving the list.

## Unresolved questions
1. On reopen, should the chat open at the summary or at the end? Finding 1 assumes the KDoc is right (open in place).
2. Is closing the dragged tab from outside (page script) reachable from the tab switcher? That decides whether finding 4 can be ignored.

## Follow-up

All four findings are fixed in one commit.

- **Finding 1.** A question counts as asked here only when the turn count grows while Skim is answering, which is how the view models publish a question. A saved chat that loads after the first composition is not answering, so it opens at its top, as it did before this work. The entrance now plays only for the newest turn. `AskSkimMotionTest.aChatThatLoadsAfterTheSheetOpensStaysAtItsTop` starts from the empty state and failed before the fix (792 px instead of 0).
- **Finding 2.** The caption that fades out is the neighbour it showed, held with its direction, not a lookup among the new neighbours. `TabStripSwipeTest.afterASwipeThatSwitchesTabTheCaptionStillFadesOut` swaps the neighbours in `onSelect` and failed before the fix.
- **Finding 3.** The settle keeps the scroll position only while the grid is not being scrolled. No test: it needs a fling started inside the settle.
- **Finding 4.** A preview whose tab is gone is dropped whenever it is written, not only when the tabs change. No test of its own.
- Question 1: reopening shows the chat from its top, as before this work. Question 2: a page can close its own tab while the tab switcher shows, so finding 4 was reachable.

