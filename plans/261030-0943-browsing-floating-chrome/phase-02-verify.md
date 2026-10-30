---
phase: 2
title: "Snackbar, spec, and goldens"
status: pending
priority: P1
effort: "3h"
dependencies: [1]
---

# Phase 2: Snackbar, spec, and goldens

## Goal

Snackbars clear the taller cluster only on a browsing page, the written spec matches that chrome, and only the goldens that show it are re-recorded.

## Context

- Phase 1 cluster height: `ToolbarMetrics.BrowsingClusterHeight` (120 dp).
- [Scout](./reports/scout-report.md) lists every backdrop that draws the old address band.

## Requirements

- `SnackbarPlacement.AboveToolbar` stays `BottomOffset + Height + 8.dp`.
- A loaded page with nothing over it uses a new placement: `BottomOffset + BrowsingClusterHeight + 8.dp` (152 dp).
- The New tab page, Reader, Tabs, and any sheet or dialog keep the placement they have today.
- `PageBehind` draws `BrowsingChrome`, not a top `AddressBar`.
- `design/screens/` PNGs are left as drawn. The deviation is written into `docs/design-guidelines.md`.

## Architecture

Add `SnackbarPlacement.AboveBrowsingChrome` next to `AboveToolbar` in `SnackbarController.kt`.

Add `snackbarPlacement(keys, pageShowing)` beside `topScreen` in `SkimKeys.kt`:

- `pageShowing` is `tabs.currentTab?.url != null` from the `TabsState` `SkimApp` already collects.
- If the top screen is `BrowserKey`, `pageShowing` is true, and the last key is not an overlay (`SkimKey.isOverlay`), return `AboveBrowsingChrome`.
- Otherwise return `topScreen(keys)?.placement ?: SnackbarPlacement.Bottom`.

`SkimApp.kt` is the only host. Replace `topScreen(keys)?.placement` with that function.

`PageBehind` drops the `toolbar` flag's separate `BrowserToolbar` and the top `AddressBar`. It fills the screen with the page color and overlays `BrowsingChrome` the way `BrowsingScreen` does. Callers that passed `toolbar = true` or `toolbar = false` both get the cluster. The sheet still covers it when the sheet reaches the bottom.

## Related Code Files

- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/snackbar/SnackbarController.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimKeys.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt`
- Modify: `app/src/test/java/com/dantech/skimbrowser/ui/navigation/SkimKeyHelpersTest.kt`
- Modify: `app/src/test/java/com/dantech/skimbrowser/ui/components/OverlaysScreenshotTest.kt` (`PageBehind` only)
- Modify: `design/spec/navigation-map.md`
- Modify: `design/spec/components.md`
- Modify: `docs/design-guidelines.md`
- Re-record goldens for the tests named in Verification. Do not hand-edit PNGs.

## Implementation Steps

1. Add the enum value and `snackbarPlacement`. In `SkimKeyHelpersTest`, assert four cases: New tab page (`BrowserKey`, `pageShowing` false) is `AboveToolbar`; a page with no overlay is `AboveBrowsingChrome`; a page under `MoreMenuKey` is `AboveToolbar`; `ReaderKey` is `AboveToolbar` even when `pageShowing` is true.
2. Point `SkimApp`'s snackbar host at `snackbarPlacement(keys, tabs.currentTab?.url != null)`.
3. Rebuild `PageBehind` on `BrowsingChrome`. Remove the unused `toolbar` parameter if nothing reads it after the call sites in this file and `AskPageScreenshotTest` are updated.
4. In `design/spec/navigation-map.md`, change the edge-to-edge rule and the floating-toolbar line so 05, 05c, and 11 use the bottom cluster, and other screens still pad the system bars and keep `BrowserToolbar`. In `design/spec/components.md`, say the address row is the top row of that cluster, in the toolbar container color, not a band under the status bar.
5. In `docs/design-guidelines.md`, record the deviation: boards 05, 05c, 11, and the page behind 06 and 06d no longer match `design/screens/` at the top band. `designParity` will show that region in red. That task is not part of `check`. Note the light-theme address row is `surfaceContainer`.
6. Re-record only the tests in Verification. Then verify those classes and the frozen toolbar, address, snackbar, home, and reader classes.

## Todo

- [x] Add `AboveBrowsingChrome` and the four placement tests.
- [x] Wire it in `SkimApp`.
- [x] Point `PageBehind` at `BrowsingChrome`.
- [x] Update the two spec files and the design-guidelines deviation.
- [x] Re-record the listed goldens and verify the frozen ones still match.

## Success Criteria

- Placement tests cover the four cases above.
- `SnackbarScreenshotTest` passes without a new golden.
- The re-recorded browsing, overlay, and ask-page goldens pass `verifyRoborazziDebug`.
- `ToolbarScreenshotTest`, `BarsScreenshotTest`, `HomeScreenshotTest`, and `ReaderScreenshotTest` pass without recording.

## Verification

Record:

```bash
./gradlew :app:recordRoborazziDebug --no-configuration-cache \
  --tests 'com.dantech.skimbrowser.ui.browsing.BrowsingScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.browsing.BrowsingOverlaysScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.ai.AskPageScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.ai.AiPermissionDialogScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest.askPageSheet' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest.askPageSheetInTheDarkTheme' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest.askPageSheetAtHalfHeight' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest.aiPermissionDialog' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest.aiPermissionDialogInTheDarkTheme'
```

Then verify those plus the frozen classes, and the unit tests:

```bash
./gradlew :app:testDebugUnitTest --no-configuration-cache \
  --tests 'com.dantech.skimbrowser.ui.browsing.*' \
  --tests 'com.dantech.skimbrowser.ui.ai.AskPageScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.ai.AiPermissionDialogScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.OverlaysScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.ToolbarScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.BarsScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.components.SnackbarScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.home.HomeScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.reader.ReaderScreenshotTest' \
  --tests 'com.dantech.skimbrowser.ui.navigation.SkimKeyHelpersTest'
```

`verifyRoborazziDebug` is the check inside the screenshot tests. Do not require `./gradlew :app:designParity` to be clean.

## Risk Assessment

- **Half-height sheet golden shows the cluster.** `sheet-06-half` leaves the bottom of the page visible if the sheet starts above the cluster. Response: re-record that method. Do not hide the cluster in `PageBehind`.
- **A snackbar over a sheet sits on the sheet's lower area.** That is the current `AboveToolbar` behavior. Response: keep it. Do not invent a sheet-height offset.
- **Recording the whole `OverlaysScreenshotTest` class** would rewrite dialogs that use `ScreenBehind`. Response: pass the six method filters above, not the class.

## Security Considerations

No new data in the snackbar path. Placement is derived from keys already on the back stack and from `Tab.url`, which the screen already shows.

## Next Steps

None. After both phases, run `/ak:cook` on this plan.
