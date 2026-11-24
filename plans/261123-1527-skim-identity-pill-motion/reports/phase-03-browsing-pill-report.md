# Phase 3 report: Browsing pill

Status: done. Both gates pass. Size is not measured; that is left to Dan.

## What changed

The pill of a page is now one row: `[address field] [Reader view] [Save video] [Tabs] [More options] [Ask Skim]`. Back and Forward are gone. The lock, globe or error icon sits inside the address field, centered together with the host. Every button outside the field is a 40 dp slot, with no gap between slots. Ask Skim is the Sunny mark at 32 dp with an 18 dp icon. It is drawn disabled in incognito and on a failed or warned page.

When the page scrolls down, the pill becomes a 40 dp chip: site mark, host, and the Sunny mark while AI is on. One `Surface` holds an `AnimatedContent` with a `SizeTransform`. The size follows `defaultSpatialSpec`, the fade follows `defaultEffectsSpec`, and both are `snap()` with Remove animations on. The 0.70 scale is gone.

## Files

Main:
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingChrome.kt`: rewritten. Adds `collapsed` and `onExpand`, plus `FullPill`, `CollapsedChip`, `PillButton` and `AskSkimButton`. `NavIcon` is removed.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingScreen.kt`:
  - `onBack` and `onForward` are removed from `BrowsingActions`.
  - Adds `onExpandChrome`.
  - The scale and `CollapsedChromeScale` are removed.
  - The collapse condition is now `internal fun showsChip(...)`, which can be tested without a device.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingRoute.kt`: `onBack` and `onForward` are gone. `onExpandChrome = { chromeCollapsed = false }`. The KDoc is updated. The `BackHandler`s are unchanged.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingViewModel.kt`:
  - `backEnabled`, `forwardEnabled` and `goForward` are removed.
  - `aiFab` is renamed to `aiMode` (see deviations).
- `app/src/main/java/com/dantech/skimbrowser/ui/components/AddressBar.kt`:
  - `CompactAddressField` now holds the site mark (20 dp icon, 28 dp slot) inside the field, centered with the host, which ellipsizes.
  - Reader view is a 40 dp slot.
  - The error icon is inert and is its own semantics node, so the field's label stays the address.
- `app/src/main/java/com/dantech/skimbrowser/ui/components/FloatingToolbars.kt`: adds `ToolbarMetrics.BrowsingSlot` (40 dp), `BrowsingChipHeight` (40 dp) and `BrowsingChipMaxWidth` (280 dp).
- `app/src/main/res/values/strings.xml`: `cd_show_browser_controls` = "Show browser controls". `cd_back` and `cd_forward` stay.
- Deleted: `app/src/main/res/drawable/ic_history_back.xml` and `ic_history_forward.xml`.

Tests:
- `BrowsingScreenTest.kt`, `BrowsingRouteTest.kt`, `BrowsingViewModelTest.kt`, `BrowsingScreenshotTest.kt`, `BrowsingOverlaysScreenshotTest.kt`, `ui/components/OverlaysScreenshotTest.kt`.
- New file `app/src/test/java/com/dantech/skimbrowser/testing/TouchHelpers.kt`, with `clickNearEnd` and `clickNearStart` (see "Tests that tap the address").

Docs:
- `docs/design-guidelines.md`: the two pill rows are rewritten. They cite Dan 2026-11-23 and list the new goldens.
- `design/spec/navigation-map.md`: the bar's controls are updated for 05, 05c and 11. The Reader row no longer says "Same as 05" for Back and Forward.
- `design/spec/components.md`: the Address bar section is updated. It described the old pill; this file was not in the brief.
- `plans/.../phase-03-browsing-pill.md`: Todo boxes are ticked except "Deviation rows, size measured".

## Removed or retargeted tests

Removed, because the product decision removed what they tested:
- `BrowsingViewModelTest.backIsOnWhenSystemBackHasSomethingToDoInTheTab`: it tested `backEnabled`, the pill's Back enabled state. The rule behind it, what system Back does, is still covered by `BrowserBackTest`, one case per branch.
- `BrowsingViewModelTest.forwardFollowsTheTabsHistoryAndGoesForwardInIt`: it tested `forwardEnabled` and `goForward`, both removed. The tab engine's `goForward` stays and is still tested in `:browser`.
- `BrowsingViewModelTest.aScamWarningDoesNotOfferForward`: it tested `forwardEnabled` and `goForward` on a warned page. There is no Forward on a page any more.
- `BrowsingRouteTest.forwardGoesForwardInTheTab`: its only subject was a click on the pill's Forward.

Retargeted:
- `BrowsingRouteTest.theToolbarsBackDoesWhatSystemBackDoesInTheTab` is now `systemBackGoesBackInTheTabThenToTheNewTabPage`. It has the same two assertions, the page before and then back to the New tab page, driven by `Espresso.pressBack()`.
- `BrowsingRouteTest.theBarAndTheToolbarOpenTheirDestinationsForThisTab` is renamed `thePillsControlsOpenTheirDestinationsForThisTab`. The body is unchanged.
- `BrowsingScreenTest.theAddressSitsInTheBottomClusterAboveBack` is now `theAddressSitsInTheBottomPillWithTheLockInsideItsField`. The address is still in the bottom half and on the same row as a control, now Tabs instead of Back. It adds that the lock is inside the field.
- `BrowsingScreenTest.theToolbarFollowsTheTabAndDoesWhatItSays` is now `thePillHasNoBackOrForwardAndAskSkimAsksAboutThePage`. Tabs is kept. The Back and Forward clicks became checks that neither exists. The old check that Ask Skim was absent became a click that calls `onAskPage`.
- `BrowsingScreenTest.anIncognitoTabsToolbarCountsIncognitoTabsAndItsAiFabIsOff` is now `anIncognitoTabsPillCountsIncognitoTabsAndItsAskSkimIsOff`. The old assertion that "Ask Skim, off in incognito" was absent is inverted: the node exists, is disabled, and has no click action.
- `BrowsingScreenTest.whileThePageFailedToLoadTheAiFabIsUnavailable` is now `whileThePageFailedToLoadAskSkimIsUnavailable`. It is inverted the same way.
- `BrowsingViewModelTest.theAiFabIsOnForAPage...` is renamed `askSkimIsOnForAPageOffInIncognitoAndUnavailableUntilAFailedPageLoads` and reads `aiMode`. It covers enabled, incognito and a failed page, and the certificate test also checks `aiMode`.

Tests that tap the address. Five clicks on "Edit address" now tap near the end of the field: one in `BrowsingScreenTest` and four in `BrowsingRouteTest`. The chip expand test taps near the chip's start. The reason is that without native graphics, Robolectric gives text no width. The centered lock then sits at the field's center, where `performClick` lands. Under `GraphicsMode.NATIVE` the host fills the center (see the goldens). The spec says "anywhere else in the field", so a tap at the end is the honest version of that test.

## New tests

`BrowsingScreenTest`:
- `thePillHasNoBackOrForwardAndAskSkimAsksAboutThePage`
- `theButtonsOutsideTheFieldAre40DpSlotsSideBySide`: Reader, Download video, Tabs, More and Ask Skim are each 40 dp wide, with no gaps.
- `scrolledDownThePillIsAChipWithTheHostThatBringsThePillBack`: checks the `onClickLabel` "Show browser controls" and `Role.Button`, and that a tap calls `onExpandChrome`.
- `theChipsMarkAsksAboutThePage`
- `withSkimAiOffTheChipHasNoMark`
- `findInPageAndACoveredPageKeepTheFullPill`
- `theChipShowsOnlyWhileNothingNeedsTheFullPill`: tests `showsChip` with the keyboard open, find, a failed page and a certificate warning. Robolectric cannot open an IME.
- `thePillMorphsIntoTheChipOverSeveralFrames`: after 4 frames the full pill is still there.
- `withRemoveAnimationsThePillAndTheChipSwitchAtOnce`: after 4 frames the chip has replaced the pill, in both directions.

`BrowsingRouteTest.askSkimInThePillAsksAboutThisPageInOneTap`: one tap on a loaded page pushes `AiPermissionKey` or `AskPageKey` for the tab.

`BrowsingScreenshotTest`: `collapsedChip`, `collapsedChipInTheDarkTheme` and `pillOfAnIncognitoTab`. All three call `assertA11y()`.

## Goldens

Re-recorded, each of which draws the pill:
- boards: `05-browsing`, `05-browsing--loading`, `05c-find-in-page`, `06d-ai-permission-first-time-per-site`, `11-page-failed-to-load`, `11-page-failed-to-load--no-internet`, `11-page-failed-to-load--timed-out`
- components: `browsing-11-crashed`, `certificate-error`, `certificate-error-advanced`, `dialog-06d`, `dialog-06d-dark`, `site-permission-15`

New components: `browsing-pill-collapsed.png`, `browsing-pill-collapsed-dark.png`, `browsing-pill-incognito.png`.

Recorded classes: `BrowsingScreenshotTest`, `BrowsingOverlaysScreenshotTest`, `OverlaysScreenshotTest`, `AskPageScreenshotTest` and `AiPermissionDialogScreenshotTest`. They are the only screenshot classes that draw `BrowsingScreen`, `BrowsingChrome` or `PageBehind`.

Board 06, the sheets (`sheet-06*`) and 05b/05d came out byte-identical: the sheet covers the pill. Nothing else was rewritten, so nothing needed `git checkout`.

I looked at `05-browsing`, `05-browsing--loading`, `05c-find-in-page`, `11-page-failed-to-load`, both chip goldens, `browsing-pill-incognito` and `dialog-06d-dark`. They match the spec:
- The field has the lock and host. Reader, Tabs, More and the Sunny mark come after it.
- Find starts at the pill's start edge.
- 11 shows the error icon in the field, a disabled Sunny, and the URL ellipsized.
- The chip is centered, sized to its content, and has the mark.

## Deviations

- **`aiMode` replaces `aiFab`.** `BrowsingUiState` already had `aiFab: AiFabMode`, computed with the same `aiFabMode(tab, runtime)`. Adding `aiMode` beside it would duplicate the field, so I renamed it. No main code read the old name.
- **The phase file and the brief disagree on placement.** The phase file says Ask Skim is "the AI FAB beside the pill". I followed your brief: Ask Skim is inside the pill as its last control. The Todo box "AI FAB on the page" is ticked on that basis.
- **The chip rests on the pill's bottom edge.** It is horizontally centered and sits 8 dp above the navigation bar, as the old scale did with its bottom origin. It is not vertically centered in the old 56 dp box. The bar's bounds now wrap the chip, so beside it the page is not covered and the tab swipe does not catch touches.
- **Padding, read off the goldens.** The pill row starts 8 dp from the start edge (it was 4) so the field's hairline clears the pill's curve. The end stays at 4 dp.
- **The chip's site mark is decoration.** The chip's label is its host text, and the mark has no content description.
- **The chip has no load progress.** The wavy progress stays in the full pill only. The spec did not mention progress in the chip.
- **`design/spec/components.md` was updated.** It was not in the brief, but it described Back and Forward in the pill.

## Gates

- `./gradlew :app:testPlayDebugUnitTest --no-configuration-cache` passed: BUILD SUCCESSFUL in 2m 7s.
- `./gradlew :app:verifyRoborazziPlayDebug --no-configuration-cache` passed: BUILD SUCCESSFUL in 2m 13s.
- `./gradlew :app:compilePlayDebugAndroidTestKotlin --no-configuration-cache` passed. No instrumented test refers to the removed symbols.
- Narrow runs during the work: `BrowsingScreenTest`, `BrowsingRouteTest`, `BrowsingViewModelTest` and `DomainBoundaryTest` all pass.

## Not verified

- The release size against `size-budget/`. You measure it.
- Device behaviour:
  - the real morph timing
  - how the 28 dp lock slot's 48 dp touch area, which overlaps the field, feels on a short host
  - TalkBack order inside the field
- The connected suite, Macrobenchmark and `designParity`. They were not run, as AGENTS.md says.
- Open question, unchanged: whether the 64 dp `BrowserToolbar` of Home, Reader and the incognito New tab page also loses Back and Forward.

## Review follow-up

- **A pill brought back by a tap collapsed again on the next pixel.** `BrowsingChromeScroll` kept adding the moves down made while the chip showed, so after a tap on the chip a 1 px move was already past the threshold. Only a move toward the other state counts now: down while the pill is full, up while it is the chip. `BrowsingChromeScrollTest.aPillBroughtBackByATapStaysUntilThePageMovesDownThatFarAgain` fails without the change.
- The gates were run again on the whole tree after that change and the icon store fix: `:app:testPlayDebugUnitTest`, `:browser:testDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.
- Size: `apk 4396921 bytes, baseline 4396917, growth 4, allowed 8192`. The baseline was not touched.
- The 40 dp slots are narrower than the 48 dp the outline asked for. The phase file records why.
