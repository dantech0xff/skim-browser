# Phase 5 report: touch feedback, first part

Status: done with concerns. Scope A to F is delivered and both gates pass. One item, the shell-level haptic fallback (A6), is not added: the pinned libraries already do that fallback, and a test now locks it in. Making room for the dragged card is left for the later step, as the brief says.

## What changed, per file

- `ui/browsing/TabStripSwipe.kt`
  - Each time the drag passes the switching threshold toward a neighbour that exists, it asks for `GestureThresholdActivate`. It tracks whether the drag is over the threshold, so falling back under it and passing it again asks again.
  - A drag toward a missing neighbour asks for nothing, and so does the release.
  - The threshold test is one function, `committedNeighbor`. The haptic and the release decision both use it.
  - The caption now sits in `AnimatedVisibility` and fades in and out with `defaultEffectsSpec`. While it fades out, the last caption shown stays on screen. With Remove animations on, the caption has no transition.
- `ui/browsing/BrowsingScreen.kt`: new `PullThresholdHaptic`. It watches `PullToRefreshState.distanceFraction >= 1` through `snapshotFlow` and asks for `GestureThresholdActivate` each time the pull reaches the threshold. It asks for nothing while the page is already refreshing or while the pull is off (a covered page).
- `ui/tabs/TabsScreen.kt` (`SwipeCard`)
  - It asks for `GestureThresholdActivate` each time the swipe offset passes 40% of the card's width. That 40% is the same `SwipeCloseFraction` that Material's positional threshold uses.
  - New `SwipeCloseBackground`: an `errorContainer` box in the card's shape, with `ic_close` in `onErrorContainer`, on the side the card leaves. It reads `dismissDirection` through `derivedStateOf`, so it recomposes only when the direction changes, and it draws nothing while the card is settled.
  - The card gets `zIndex(1f)` while it is lifted.
- `ui/tabs/DragReorder.kt`
  - The long press that picks the card up asks for `LongPress`, and a drop asks for `GestureEnd`. A cancelled drag asks for nothing.
  - New `lift` `Animatable`, read only inside `graphicsLayer`. The card scales to `LiftScale` (1.04) and gets a 6 dp shadow in `TabCardShape`, using `defaultSpatialSpec`. With Remove animations on, it snaps.
  - New `onLiftChange` parameter, so `SwipeCard` can draw the card above the others until it has settled back.
  - `targetIndexInGroup`, `dropIndex` and `DragReorderTest` are unchanged.
- `ui/components/TabCard.kt`: the private `CardShape` is now `internal val TabCardShape`. The swipe background and the lift shadow use the same outline. Drawing is unchanged.
- `ui/components/FloatingToolbars.kt` (`TabsCountIcon`)
  - The number is in `AnimatedContent`. It slides in vertically and fades (`fastSpatialSpec` and `fastEffectsSpec`): up when the count grows, down when it shrinks. The size change uses `SizeTransform`, so "99+" still widens the square.
  - `contentKey` treats every count from 100 up as the same, so "99+" does not roll between hundreds. The first composition has no motion. With Remove animations on, there is no transition and the size snaps.
- `ui/components/Chat.kt`: two shared helpers.
  - `Modifier.entrance(play, delayMillis)` fades the item in (`defaultEffectsSpec`) and lifts it 8 dp into place (`defaultSpatialSpec`). It decides at first composition and is drawn only in `graphicsLayer`. The hold-back before it starts is counted in frames. With Remove animations on, the item is in place at once.
  - `rememberChatScrollState()` is a `ScrollState` that moves to the new end when the content grows while it was at the end. Otherwise it leaves the position alone.
- `ui/ai/AskPageSheet.kt`
  - The scroll region uses `rememberChatScrollState()`.
  - The suggested questions stagger 40 ms apart. This happens once per sheet: a flag in the sheet stops them from playing again on recomposition or when they are hidden and shown again.
  - A turn plays its entrance only if two things hold: its index is at or past the turn count the sheet opened with, and the sheet is generating or waiting. The question and the answer each play when they first appear.
- `ui/ai/AskTabsSheet.kt`: the same pattern for the follow-up chips, the follow-up turns and the scroll.
- `docs/design-guidelines.md`: five rows at the end of the known-deviations table, in Vietnamese: swipe background, lifted card, tab count roll, caption fade, Ask Skim entrances. The haptics are mentioned in the swipe, lift and caption rows.
- `design/spec/tokens.md`: the Motion table's "Used for" column lists the new uses. The Remove-animations line now says that every other motion snaps and haptics stay.
- `plans/.../phase-05-touch-feedback.md`: "Scout report", "Haptics" and "Ask Skim and tab count" are ticked. "Reorder and swipe to close" and "Tests and goldens" stay open because making room is still to come. "Size measured" stays open.

## Deviations from the brief, with reasons

1. **A6: no shell `HapticFeedback` wrapper.** The brief assumed that below API 34 (or 30) the platform does nothing. That is not true for the pinned versions:
   - Compose UI 1.12.1's `PlatformHapticFeedback` maps each type to a `HapticFeedbackConstants` value and calls `ViewCompat.performHapticFeedback`.
   - In androidx.core 1.19.1, that function first applies `HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback`. Below API 34, `GESTURE_THRESHOLD_ACTIVATE` becomes `CONTEXT_CLICK`. Below API 30, `GESTURE_END` becomes `CONTEXT_CLICK`, `CONFIRM` becomes `VIRTUAL_KEY` and `REJECT` becomes `LONG_PRESS`.
   - I checked this in the bytecode of the cached AARs.
   - A wrapper would repeat that mapping and add nothing, so `SkimApp.kt` is unchanged. Instead, `HapticFallbackTest` sets `Build.VERSION.SDK_INT` to 26, 29, 30, 33 and 34 (the Robolectric SDK stays the same). For each level it checks the constant the hosting view receives for `GestureThresholdActivate`, `GestureEnd` and `LongPress`. If a library update drops the fallback, the test fails.
   - If you still want an explicit wrapper and pure function, it is a small addition next to `LocalSiteIcons`. See the questions below.
2. **A4: no code change.** `combinedClickable` in foundation 1.12.1 asks for `LongPress` by default (`hapticFeedbackEnabled = true`), and Material's `SegmentedListItem` asks for it too. `LongPressHapticsTest` proves exactly one `LongPress` on the Home shortcut, the News card and a `SkimListItem` row with `onLongClick`, and none on a tap.
3. **B has no Remove-animations case of its own.** The background has no motion of its own: it shows the space the card leaves, and the card's spring back is Material's `SwipeToDismissBox` animation, which has no spec parameter. The test that fails without the change works like this. With the clock paused after a release under the threshold, the `errorContainer` stays for several frames while the card returns, then goes. Other tests check that nothing is drawn at rest and which side the mark is on.
4. **The pull-to-refresh and long-press haptics have no deviations-table row.** They change nothing visible. The table rows follow the brief's list of visual changes.

## The chat list while an answer streams

I measured this by putting the plain `rememberScrollState()` back in both sheets for one run of `AskSkimMotionTest`, then restoring the new code.

- **Before**, the scroll region was a `Column` with `verticalScroll(rememberScrollState())`.
  - At the end: the value stayed at 272 px while the end grew to 792 px. The streaming answer grew below the visible area and out of view.
  - Across tabs, with content that first fit: the value stayed at 0 while the end grew to 432 px.
  - Scrolled up: the value held at 152 px.
- **Now**, at the end, the list follows the new end (value equals the end after the answer grows). Scrolled up, the value stays where the person left it. A newly asked question below the fold also scrolls into view now, because that is growth at the end.

## Tests added (43, all passing)

- `testing/RecordingHapticFeedback.kt`: the shared recording fake, plus `ProvideHaptics`.
- `ui/browsing/TabStripSwipeTest` (5 tests):
  - exactly one haptic per threshold crossing, again after falling back under it, none on release;
  - none toward a missing neighbour;
  - the haptic stays with Remove animations on;
  - the caption fades out over several frames while the pill keeps its top;
  - with Remove animations on, the caption goes at once.
- `ui/browsing/PullToRefreshHapticsTest` (5 tests): one haptic past the threshold, none on a short pull, none while refreshing, none on a failed page, and the haptic stays with Remove animations on.
- `ui/tabs/TabsTouchFeedbackTest` (11 tests):
  - swipe: a haptic per crossing and none on release; Close tab gives no haptic;
  - the close mark on the side the card leaves, and nothing at rest; the mark stays while the card springs back;
  - two goldens;
  - reorder: `LongPress` then `GestureEnd`, only `LongPress` when cancelled, nothing where reorder is off;
  - the lift grows over several frames, reaches 1.04, and settles back; with Remove animations on it is immediate and the haptics stay.
- `ui/components/TabsCountRollTest` (5 tests): no motion on first composition; growing rolls up and shrinking rolls down (checked by where the new and old numbers sit mid-transition); immediate with Remove animations on; "99+" widens and does not roll between hundreds.
- `ui/LongPressHapticsTest` (3 tests): Home shortcut, News card, segmented list row.
- `ui/shell/HapticFallbackTest` (5 tests): API 26, 29, 30, 33, 34.
- `ui/ai/AskSkimMotionTest` (9 tests):
  - the suggested questions stagger, do not replay on recomposition or when shown again, and are in place at once with Remove animations on;
  - an added question and answer rise in, but turns present at open do not; with Remove animations on they are in place at once;
  - an AskTabs follow-up rises in;
  - pinned at the end on both sheets; scrolled up, the position does not move.

## Goldens added

- `app/src/test/screenshots/components/tab-card-swipe-close.png`
- `app/src/test/screenshots/components/tab-card-swipe-close-dark.png`

Both show the second card held 64 dp toward the start, short of the closing point. No existing golden changed: after recording, `git status -- app/src/test/screenshots` lists only these two files.

## Gate results

- `./gradlew :app:testPlayDebugUnitTest --no-configuration-cache`: `BUILD SUCCESSFUL in 2m 2s` / `211 actionable tasks: 1 executed, 210 up-to-date`
- `./gradlew :app:verifyRoborazziPlayDebug --continue --no-configuration-cache`: `BUILD SUCCESSFUL in 2m 26s` / `212 actionable tasks: 2 executed, 210 up-to-date`
- Extra check: `./gradlew :app:lintPlayDebug --no-configuration-cache`: `BUILD SUCCESSFUL in 2m 34s`

## Not verifiable without a device

- How each haptic feels, and the `CONTEXT_CLICK` fallback on a real API 26 to 33 device. On Robolectric, only the constant handed to the view is checked.
- The lifted card's shadow. Robolectric does not draw `graphicsLayer` shadows reliably, so the tests check scale only.
- Frame time while dragging, swiping and streaming (Smooth Compose asks for Macrobenchmark or JankStats numbers). The animated values are read only in `graphicsLayer` or layout, and the swipe background recomposes only on a direction change, but nothing was measured. "Size measured" stays open.
- Whether the user's own touch-feedback setting in system settings suppresses these haptics as expected.

## Observations

- `SwipeCard`'s `confirmValueChange` called `onClose` four times for one dismissal in the test, where the grid's state is static and the card stays. In the app the card leaves the grid after the first close, so this may not show on a device, but it is worth confirming that a repeated `TabsViewModel.close` for the same id (it calls `closeIds`) does no harm, such as a second Undo snackbar. This behaviour predates this change; the `SwipeToDismissBox` callback is deprecated in material3 1.5.0-beta01 in favour of dynamic anchors.

## Unresolved questions

1. A6: is the library's fallback, locked in by `HapticFallbackTest`, acceptable? Or do you want the explicit wrapper next to `LocalSiteIcons` anyway, which would duplicate `HapticFeedbackConstantsCompat`?
2. The chat now follows the end whenever the content grows while it is at the end. That includes a summary that streams in while the person is at the top of a short sheet whose content still fits. Is following there intended, or should following start only once the content first overflows?


## Review follow-up

- **A6 is accepted as built.** The fallback is the library's, and `HapticFallbackTest` guards it. No wrapper.
- **The chat scroll was reworked.** As first built, a chat that still fit counted as "at the end", so a summary streaming in pulled a reader at the top down to the end (2,264 px in the new test). And a question asked after scrolling up stayed out of view. `rememberChatScrollState(turns)` now keeps a following flag: a question asked here sets it and brings the chat to its end, scrolling up clears it, and reaching the end of a chat longer than the sheet sets it again. A chat that still fits does not follow when it grows.
  - New tests: `aSummaryThatGrowsPastTheSheetDoesNotMoveAReaderAtTheTop` and `aQuestionAskedAfterScrollingUpBringsTheChatBackToItsEnd` fail on the first version; `aQuestionAskedWhileTheChatFitsIsFollowedWhenItsAnswerGrowsPastTheSheet` guards the case the first version already handled.
  - `atTheEndAStreamingAnswerAcrossTabsStaysInView` started from a chat that fit, so it tested following from the top. It now starts from a chat longer than the sheet and asserts that.
- **`onClose` called more than once by Material's swipe box:** `TabsViewModel.close` ignores a tab that is no longer open, so a repeat does nothing. Not changed.
- The gates were run again on the whole tree: `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.
