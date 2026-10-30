# One bottom cluster

`assertA11y()` requires a 48 dp touch target (`A11yAssertions.kt:12`). The toolbar row is four 48 dp buttons inside 64 dp (`FloatingToolbars.kt:50-53`, `components.md:9-10`). The address row is a 56 dp pill (`AddressBar.kt:55-56`). A single 64 dp row cannot hold the address, Back, Forward, Tabs, More, and the FAB without shrinking a target. That layout is rejected.

The cluster is one bottom overlay:

- One `Surface`, full corner radius (`CircleShape`), toolbar container color: `surfaceContainer` in light, `surfaceContainerHigh` in dark (`FloatingToolbars.kt:282-284`).
- Top row, 56 dp: today's address controls, or Find in page when find is open. No second full-width `surface` frame.
- Bottom row, 64 dp: the same four buttons, centered. The AI FAB stays outside the surface, 8 dp to the right, vertically centered on this 64 dp row.
- The surface and the FAB are one `Row` at the bottom. They share one inset. They are not placed independently.

In light theme the address row moves from `surfaceContainerHigh` to `surfaceContainer`, because a nested pill would be a second bar. In dark theme those two roles are already the same token (`design/spec/tokens.md`).

On a 412 dp phone the card width is about 316 dp after 16 dp side margins, the 8 dp FAB gap, and the 56 dp FAB. Four buttons at 48 dp plus the toolbar's 8 dp horizontal padding fit. Find keeps Previous, Next, and Close; the field gets the remaining width and ellipsizes. On a 360 dp phone the card is about 264 dp, still wider than the icon row.

TalkBack hits this cluster before the WebView: `traversalIndex = -1f` on the cluster. Inside it, the address row comes before the icon row, matching the current order (address in the column, toolbar after the page).

`BrowserToolbar`'s public signature and the `HorizontalFloatingToolbar` metrics stay. Home, Reader, Tabs, and the incognito New tab page keep calling `BrowserToolbar`. The browsing screen stops calling it and calls the new cluster, which reuses the extracted button row.

`PageBehind` (`OverlaysScreenshotTest.kt:299-344`) is a hand-drawn copy of the old top address bar. It has to call the same cluster or the sheet goldens and the real screen drift.

Snackbar clearance today is the 64 dp toolbar plus 24 dp plus 8 dp (`SnackbarController.kt:59-60`). A 120 dp cluster would cover a snackbar that still uses that offset. `BrowserKey` is also the New tab page (`SkimKeys.kt:43-47`), whose toolbar stays 64 dp, so the global `AboveToolbar` value must not grow.
