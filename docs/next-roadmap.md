# Next roadmap

A gap audit against `docs/product-vision.md` and the goal of a lightweight open-source Android browser people can use every day. Product rules on that page still win. This page only ranks work that is not shipped yet.

Checked on master after the tab-swipe change (`6ded431`).

## Already in the app

These are the daily-driver pieces that already have code. They are not units below.

| Need | Where it lives |
|---|---|
| Downloads | `browser/.../downloads/DownloadRouter.kt`. Normal tabs use DownloadManager. Incognito downloads stay in the process. A finished file opens from the snackbar (`SnackbarActions.kt`). |
| Find in page | `BrowsingViewModel` and `FindInPageBar.kt`. The count is `0/0` when nothing matches. |
| History and bookmark search | `HistoryScreen.kt`, `BookmarksScreen.kt`. The query filters the list in place. |
| Desktop site | `MoreMenuViewModel.toggleDesktop` and `DesktopUserAgent.kt`. |
| Site permissions | Location, camera, and microphone (`SitePermissionKind`). Decisions are per host. |
| Share a page, open a link | `ShareSheet.sharePage`. `IncomingIntents` opens an http(s) `ACTION_VIEW`. The manifest holds the browser role filters. |
| Default browser | `DefaultBrowser` and the Settings row that starts the system role request. |
| Text size in Reader | `ReaderTextScale` on the reader panel only. It does not change the WebView. |
| Tab restore | `TabManager.restore`. Normal tabs, groups, and WebView state come back. Incognito tabs do not, on purpose (`TabRules.kt`). |
| Renderer crash | `TabManager.onRendererGone` restores the WebView a few times, then shows `LoadError.Crashed` with Try again. |
| File upload, pinch zoom, third-party cookies off, HTTPS-first | `SkimChromeClient.onShowFileChooser`, `builtInZoomControls` in `WebViewPool.configure`, `setAcceptThirdPartyCookies(false)`, `httpsUpgrade`. |
| Cosmetic element hiding | `CosmeticFilters` and `SkimWebViewClient`. `##` and `#@#` rules from the same EasyList and EasyPrivacy snapshot become one `display:none` stylesheet. A site that turns blocking off is not hidden. Procedural rules are not applied. |

## Product vision, still open

- Frame time on a device is not written down. Macrobenchmark code exists (`macrobenchmark/`, `baselineprofile/`). A green unit test is not that measurement.
- Release is still blocked by `docs/deployment-guide.md` (proxy, legal URLs, signing). No unit here asks for a secret or a store listing.

## Ranked units

Each unit ships on its own. None of them add a library. Size is an estimate against the releaseSmoke file, whose allowance is 8,192 bytes (`size-budget/releaseSmoke.json`).

### 1. Page text follows the system font size

**Missing.** `WebViewPool.configure` never sets `WebSettings.textZoom`. It stays at 100. `AndroidManifest.xml` lists `fontScale` in `configChanges`, so a font-size change does not recreate the activity, and nothing applies the new scale to a live WebView. Reader text size does not cover this.

**Why.** The system font size is how people make pages readable. Pinch zoom scales the whole page and does not reflow the text.

**Size.** A few hundred bytes of bytecode. No asset and no dependency. Far under 8,192 bytes.

**Acceptance.**

- `PageTextZoom.ofFontScale` maps 1 to 100, 1.3 to 130, and clamps to 50..200. A non-finite or non-positive scale maps to 100.
- A WebView shown by the pool uses the activity's current font scale.
- `Activity.onConfigurationChanged` with a new `fontScale` updates a WebView that is already showing.
- releaseSmoke stays within the existing budget.

### 2. Open one shared link

**Missing.** `IncomingIntents.urlFrom` accepts only `ACTION_VIEW`. A share sheet that sends `ACTION_SEND` with `text/plain` and one http(s) URL is ignored (`IncomingIntentsTest`).

**Why.** Sharing a link to the browser is a normal way to open a page. View intents already work.

**Size.** A few hundred bytes. No new screen.

**Acceptance.**

- `ACTION_SEND` `text/plain` whose text is one http(s) URL opens that URL.
- Any other extra, a second URL, or a non-http scheme is still ignored.
- An intent restored from Recents is still not opened again.

### 3. Retry a failed download

**Missing.** `snackbar_download_failed` has no action. `DownloadRouter` reports the failure and stops.

**Why.** A failed save is otherwise a dead end. The file name is already on the snackbar.

**Size.** A string and a small retry path. Under 8,192 bytes.

**Acceptance.**

- The failed snackbar has a Retry action.
- Retry calls the same download once more, still under the existing automatic-download limit for a page-started download.
- A download the user asked for (an image from the link sheet) retries without that limit, as it does today.

### 4. Dark pages follow the theme

**Missing.** The app theme can be dark while the WebView stays light. `androidx.webkit` is already in the APK for client hints. Algorithmic darkening is not turned on.

**Why.** A dark browser that shows a bright page is the daily mismatch. This uses the theme the person already chose. It does not add a second switch.

**Size.** No new dependency. A few hundred bytes.

**Acceptance.**

- Dark theme sets algorithmic darkening on each WebView. Light theme turns it off. System theme follows `uiMode`.
- A theme change updates WebViews that are already showing, the same way unit 1 updates font scale.
- A WebView that does not support the API stays unchanged and does not crash.

### 5. A page text-size control

**Missing.** Unit 1 only follows the system. There is no Skim control for page text, separate from Reader.

**Why.** Some people want larger page text without changing every other app.

**Size.** One settings row and a stored integer. Reuse the reader size slider. No new board; record the row in `docs/design-guidelines.md`. Under 8,192 bytes.

**Acceptance.**

- The stored percent overrides the system scale until it is reset.
- Reset follows the system scale again.
- The value survives process death and is applied to every WebView, including one created later.

### 6. Not next, until someone decides

- An in-app Downloads screen. `design/README.md` lists it as an open decision. The system Downloads UI already receives normal-tab files.
- Web notifications. They need a permission prompt that does not exist.
- A certificate interstitial with a way to continue. That is a security decision. The WebView's own handling stays.
