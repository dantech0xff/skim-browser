# Insets for an edge-to-edge WebView

The activity is already edge-to-edge (`MainActivity.kt:74`, `SystemBarsEffect.kt:25-28`). The browsing page is not. `BrowsingScreen` paints a `surface` spacer the height of `WindowInsets.safeDrawing` and then the address frame, so the WebView's top is the bottom of that chrome.

## What to change

At rest the page box is `fillMaxSize` and has no status-bar, navigation-bar, or horizontal inset. The WebView keeps `Modifier.fillMaxSize()` inside that box (`BrowsingRoute.kt:93`). `WebViewPool.kt:388-390` documents that a wrap-content WebView lays the page out at height 0, so the host box must keep a real height. Do not add IME or inset state to `WebViewHost`'s `key()`.

The floating cluster is a sibling overlay, later in the `Box`, so it draws above the page. Its padding is one union, not stacked modifiers:

`WindowInsets.navigationBars.union(WindowInsets.ime).union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))`

then `padding(bottom = ToolbarMetrics.BottomOffset)`.

`navigationBarsPadding()` plus `imePadding()` double-counts while the keyboard is open, because the IME inset already covers the navigation-bar band. Compose's `WindowInsets.union` is the operation that keeps the larger inset.

## Keyboard

While the IME inset is 0, the page box stays full bleed. Content scrolls under the cluster, which is the rule already written for the toolbar (`design/spec/navigation-map.md:22`).

While the IME inset is greater than 0, shorten the page box from the bottom by the cluster height plus `BottomOffset`, and shorten it by the IME inset. The cluster sits above the keyboard. A focused field is scrolled into the WebView's bounds; those bounds must end at the top of the cluster or the field sits under it. Read the IME with `WindowInsets.ime.getBottom(LocalDensity.current) > 0`. Do not put a second `imePadding()` on `LoadErrorContent`.

## Status bar and pull to refresh

Do not add a scrim. The request is a full-bleed page. `SystemBarsEffect` already picks light or dark icons from the app theme. A dark page in the light theme can sit under dark icons. That is accepted.

The pull-to-refresh indicator is aligned to the top of the page box (`BrowsingScreen.kt:168-171`). Add `windowInsetsPadding(WindowInsets.statusBars)` to that indicator only, so it clears the clock. The gesture stays on the full-bleed page.

## What not to do

Do not inject CSS or JavaScript to set `env(safe-area-inset-*)`. Android WebView does not reliably map those to the app's floating chrome, and this screen already lets the page scroll under the toolbar. Official edge-to-edge guidance is to inset the controls, not the WebView view, and to keep drawing behind the system bars: [Display content edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

Do not change `SystemBarsEffect` or `FullscreenController`.
