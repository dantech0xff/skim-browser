# Scout Report

Written in the main session. Researcher and scout subagents did not start (monthly usage limit). Paths below were read directly.

## Relevant Files

- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingScreen.kt` — browsing chrome. Lines 86–133 are a `Column`: a `surface` status-bar spacer (`windowInsetsTopHeight(WindowInsets.safeDrawing)`), then `FindInPageBar` or `AddressBar`, then the page in `Modifier.weight(1f).imePadding()`. Lines 134–157 overlay `BrowserToolbar` at the bottom with `navigationBarsPadding()` and `ToolbarMetrics.BottomOffset`.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingRoute.kt:93` — `page = { WebViewHost(tab.id, Modifier.fillMaxSize()) }`.
- `browser/src/main/java/com/dantech/skimbrowser/browser/ui/WebViewHost.kt:22-31` — `AndroidView` of the pooled `SkimWebView`. The composable does not apply insets.
- `browser/src/main/java/com/dantech/skimbrowser/browser/WebViewPool.kt:388-390` — a `wrap_content` WebView lays the page out at height 0. The host must be `fillMaxSize`.
- `app/src/main/java/com/dantech/skimbrowser/ui/components/AddressBar.kt:54-167` — 56 dp pill inside an 8×16 frame on `surface`. Production caller is only `BrowsingScreen`.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/FindInPageBar.kt:47-122` — replaces the address bar. Same full-width `surface` frame. Only `BrowsingScreen` calls it.
- `app/src/main/java/com/dantech/skimbrowser/ui/components/FloatingToolbars.kt:50-60` — `ToolbarMetrics.Height` 64 dp, `BottomOffset` 24 dp, `FabGap` 8 dp. `BrowserToolbar` (line 85) is the shared icon row plus AI FAB.
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SystemBarsEffect.kt:21-29` — status bar is already transparent. Icon color follows the app theme, not the page. Navigation bar keeps the androidx scrim.
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt:74` — `enableEdgeToEdge()` once at start. `SystemBarsEffect` calls it again.
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/LoadErrorContent.kt:165-166` — bottom clearance is `BottomOffset + Height + 32.dp`. The error sits in the page box, which today starts under the address bar.
- `app/src/main/java/com/dantech/skimbrowser/ui/snackbar/SnackbarController.kt:57-64` — `AboveToolbar` is `BottomOffset + Height + 8.dp`.
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt:86-89` — the only snackbar host. Placement is `topScreen(keys)?.placement`.
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimKeys.kt:43-47` — `BrowserKey.placement` is `AboveToolbar` for both the New tab page and a loaded page. `topScreen` (line 169) skips sheets and dialogs.
- `app/src/main/java/com/dantech/skimbrowser/ui/browser/BrowserRoute.kt:98-99` — `tab.url == null` is the New tab page. A non-null URL is the browsing screen.
- `domain/src/main/kotlin/com/dantech/skimbrowser/domain/tab/Tab.kt:57-63` — `TabsState.currentTab`. `SkimAppViewModel.tabs` is `StateFlow<TabsState>`.
- `design/spec/navigation-map.md:13` — floating toolbar on 04, 05, 05c, 06d, 08, 09c, 11.
- `design/spec/navigation-map.md:22` — "Pad status and nav bars with WindowInsets. Pages scroll under the floating toolbar."
- `design/spec/components.md:5-51` — toolbar is a 64 dp bottom bar. Address bar is a separate 56 dp pill in an 8×16 frame.
- `docs/design-guidelines.md:16-19` — Roborazzi goldens are the parity evidence. `designParity` is not part of `check`.

## Callers

- `AddressBar(` — `BrowsingScreen.kt`, `AddressBar.kt` previews, `BarsScreenshotTest.kt`, `OverlaysScreenshotTest.kt` `PageBehind`.
- `FindInPageBar(` — `BrowsingScreen.kt` and its preview.
- `BrowserToolbar(` — `BrowsingScreen.kt`, `ReaderScreen.kt`, `NewTabPageRoute.kt`, `FloatingToolbars.kt` previews, `HomeFixtures.kt`, `SnackbarScreenshotTest.kt`, `ToolbarScreenshotTest.kt`, `PageBehind` in `OverlaysScreenshotTest.kt`.
- `PageBehind(` — `OverlaysScreenshotTest` (sheet 06, dialog 06d) and `AskPageScreenshotTest` (boards 06, 06c, 06d).
- `BrowsingScreen(` — `BrowsingRoute`, `BrowsingScreenTest`, `BrowsingScreenshotTest`, `BrowsingOverlaysScreenshotTest`.

Home, Reader, Tabs, and the incognito New tab page draw `BrowserToolbar` themselves. They do not use `AddressBar`.

## Tests and goldens

- `BrowsingScreenTest` — which bar shows, toolbar actions, find replacing the address. No position assertion.
- `BrowsingScreenshotTest` — boards `05-browsing`, `05-browsing--loading`, `05c-find-in-page`, `11-page-failed-to-load` and its two variants, plus component golden `browsing-11-crashed`.
- `BrowsingOverlaysScreenshotTest` — `05b`, `05d`, and component goldens whose backdrop is `BrowsingScreen`.
- `AskPageScreenshotTest` and `AiPermissionDialogScreenshotTest` — backdrop is `PageBehind`.
- `OverlaysScreenshotTest.askPageSheet*` and `aiPermissionDialog*` — backdrop is `PageBehind`. Other tests in that class use `ScreenBehind` and must not be re-recorded.
- `BarsScreenshotTest` — top 120 dp strip of the framed `AddressBar`. Must stay pixel-identical.
- `ToolbarScreenshotTest` — bottom strip of `BrowserToolbar`. Must stay pixel-identical.
- `assertA11y()` requires a 48 dp touch target (`app/src/test/java/com/dantech/skimbrowser/testing/A11yAssertions.kt:12`).

Phone goldens use `w412dp-h915dp-xhdpi` (`ScreenshotDefaults.kt`).

## Insets and WebView host

Status-bar spacer and address bar consume layout. The WebView fills only what is left, then `imePadding()` shrinks that box. The toolbar overlays the bottom of that box and does not move with the IME. Horizontal `safeDrawing` is on the column, not on the toolbar.

## Unresolved Questions

None for planning. The 48 dp target rules out putting the address and the four icons in the existing 64 dp row.
