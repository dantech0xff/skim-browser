---
phase: 1
title: "Edge-to-edge page and bottom cluster"
status: pending
priority: P1
effort: "4h"
dependencies: []
---

# Phase 1: Edge-to-edge page and bottom cluster

## Goal

The browsing page fills the screen, and address, find, and the toolbar buttons are one floating cluster at the bottom.

## Context

- [Insets](./research/researcher-01-insets.md)
- [Chrome](./research/researcher-02-chrome.md)
- [Scout](./reports/scout-report.md)

## Requirements

- Remove the status-bar spacer and the full-width `surface` frame from the browsing screen.
- Keep every control `AddressBar` and `BrowserToolbar` already expose, including wavy progress and find-in-page focus.
- Do not change the pixels of `BrowserToolbar` on Home, Reader, Tabs, or the incognito New tab page.
- At rest the WebView is full bleed. While the IME is visible, the page box ends at the top of the cluster and the cluster sits above the keyboard.

## Architecture

`BrowsingScreen` becomes a `Box`. The first child is the page, `fillMaxSize`, with IME and cluster padding only while the IME inset is greater than 0. The second child is `BrowsingChrome`, aligned to the bottom.

`BrowsingChrome` is a `Row` with `verticalAlignment = Bottom`:

1. A `Surface` (`CircleShape`, toolbar container color, `Modifier.weight(1f)`) whose column is the embedded address or find row (56 dp) and then `BrowserToolbarActions` centered in a 64 dp box.
2. An 8 dp gap (`ToolbarMetrics.FabGap`).
3. A 64 dp box that centers the existing `aiFab` slot.

Side inset is 16 dp, the same margin the address frame uses today. Bottom inset is `ToolbarMetrics.BottomOffset` after this union:

`WindowInsets.navigationBars.union(WindowInsets.ime).union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))`

`ToolbarMetrics` gains `AddressRowHeight = 56.dp` and `BrowsingClusterHeight = AddressRowHeight + Height` (120 dp). `AddressBar` and `FindInPageBar` use `AddressRowHeight` instead of their private `56.dp`.

The cluster sets `isTraversalGroup = true` and `traversalIndex = -1f` so TalkBack reaches it before the WebView. The address row is composed first.

## Related Code Files

- Create: `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingChrome.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/components/FloatingToolbars.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/components/AddressBar.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/browsing/FindInPageBar.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingScreen.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/browsing/LoadErrorContent.kt`
- Modify: `app/src/test/java/com/dantech/skimbrowser/ui/browsing/BrowsingScreenTest.kt`
- Do not modify: `WebViewHost.kt`, `SystemBarsEffect.kt`, `BrowserToolbar`'s public parameters, Home, Reader, Tabs, New tab page.

## Implementation Steps

1. In `ToolbarMetrics`, add `AddressRowHeight` and `BrowsingClusterHeight` as above. In `FloatingToolbars.kt`, extract the four buttons (Back, Forward, Tabs, More) into `internal fun BrowserToolbarActions` with the parameters `BrowserToolbar` already uses for them. `BrowserToolbar` calls that function from the existing `SkimFloatingToolbar` content lambda. Leave height, colors, `contentPadding`, the FAB slot, and the "Browser controls" semantics row where they are.
2. Add `embedded: Boolean = false` to `AddressBar` and `FindInPageBar`. `false` is today's frame and is what `BarsScreenshotTest` and the previews call. `true` draws only the 56 dp control row, no outer `surface` background and no 8×16 padding. The wavy progress stays on the bottom edge of the address row, drawn over the icon row. `Surface` clips to the cluster outline, so the progress must stay inside the cluster, not below it.
3. Add `BrowsingChrome` with the same state and callbacks `BrowsingScreen` already passes to `AddressBar`, `FindInPageBar`, and `BrowserToolbar` (`embedded = true`). Container color matches `SkimFloatingToolbar`: `surfaceContainerHigh` when `isDarkScheme()`, otherwise `surfaceContainer`. Content color for the icon row stays `onSurfaceVariant`. Address text stays `onSurface`.
4. Rewrite `BrowsingScreen`'s body. Delete the status spacer, the column, and the horizontal `safeDrawing` inset on the page. Page child: `PullToRefreshBox` and `LoadErrorContent` as they are now, inside a box that is `fillMaxSize` and, only when `WindowInsets.ime.getBottom(LocalDensity.current) > 0`, also `windowInsetsPadding(WindowInsets.ime)` and `padding(bottom = ToolbarMetrics.BrowsingClusterHeight + ToolbarMetrics.BottomOffset)`. Keep `page()` as the caller passes it (`fillMaxSize`). Do not put the IME check inside `WebViewHost`'s `key()`.
5. Put `RefreshIndicator`'s modifier on `windowInsetsPadding(WindowInsets.statusBars)` as well as `align(TopCenter)`.
6. Overlay `BrowsingChrome` with the union insets and `padding(bottom = ToolbarMetrics.BottomOffset)`, plus 16 dp horizontal padding. It is the later child, so it receives touches on the cluster.
7. In `LoadErrorContent`, add status-bar padding on the scrolling column. Change `AboveToolbar` to `ToolbarMetrics.BottomOffset + ToolbarMetrics.BrowsingClusterHeight + 32.dp`. Keep `navigationBarsPadding()`. Do not add `imePadding()` here.
8. In `BrowsingScreenTest`, keep the existing click tests. Add one assertion on the loaded state: the "Edit address, daylightjournal.com" bounds sit in the lower half of the root, and their bottom is at or above the top of "Back".

## Todo

- [x] Extract `BrowserToolbarActions` without moving toolbar pixels.
- [x] Add the embedded rows and `BrowsingChrome`.
- [x] Make the page full bleed, and shorten it only while the IME is visible.
- [x] Clear the status bar in the refresh indicator and the load-error column.
- [x] Assert the address node is in the bottom half, above Back.

## Success Criteria

- `BrowsingScreenTest` passes, including the new bounds assertion.
- `ToolbarScreenshotTest` and `BarsScreenshotTest` pass without `recordRoborazzi`.
- No top spacer remains in `BrowsingScreen`.

## Verification

```bash
./gradlew :app:testDebugUnitTest --no-configuration-cache \
  --tests 'com.dantech.skimbrowser.ui.browsing.BrowsingScreenTest' \
  --tests 'com.dantech.skimbrowser.ui.components.ToolbarScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.BarsScreenshotTest'
```

Do not run `BrowsingScreenshotTest` in this phase. Its goldens still show the top band and are updated in phase 2.

## Risk Assessment

- **IME union forgotten.** Signal: with the keyboard open, the cluster floats about one navigation-bar height too high. Response: switch that padding to the union in this phase. Do not add a second inset modifier.
- **Button extraction shifts the FAB.** Signal: `ToolbarScreenshotTest` fails. Response: put the buttons back in the same lambda shape. Do not re-record that golden.
- **WebView height collapses.** Signal: pages that use `height: 100%` go blank. Response: the page box stays `fillMaxSize` at rest. Do not add a height key.

## Security Considerations

No new permissions, WebView settings, or page script. Find in incognito still goes through `NoKeyboardLearning`. Do not inject JavaScript to pad the document.

## Next Steps

Phase 2 depends on `BrowsingChrome` and `BrowsingClusterHeight`.
