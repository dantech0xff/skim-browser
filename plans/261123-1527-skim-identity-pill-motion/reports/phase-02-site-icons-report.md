# Phase 2: Site icons, implementation report

Status: done with concerns (see the end). Nothing was committed; every change is in the working tree.

## Scout findings

- Icons go through the thumbnail path. `SkimChromeClient` is built in `WebViewPool.webViewFor`. `ThumbnailStore` is the pattern for the store: `SerialWriter`, a cache directory, `awaitIdle`. `BrowsingDataCleaner.clearHistory` clears thumbnails. `TabsScreen` reads the store through `ShellEntryPoint.thumbnails()`.
- The incognito session ends in `IncognitoSession.end`, which `TabManager.endIncognitoSession` calls. Its `@Inject` constructor already does in-memory incognito cleanup in its `onEnded` lambda (`shield.clearIncognito()`), so the icon cleanup goes there too.
- `DomainBoundaryTest` does not let a `ui/` file name `com.dantech.skimbrowser.browser`, except the route files on its fixed list. See the first deviation below.
- No file in `docs/codebase-summary.md` or `docs/system-architecture.md` names `ThumbnailStore`, so neither file was changed.

## Files changed

New:
- `browser/src/main/java/com/dantech/skimbrowser/browser/SiteIconStore.kt`: the store
- `browser/src/test/java/com/dantech/skimbrowser/browser/SiteIconStoreTest.kt`: 13 tests
- `app/src/main/java/com/dantech/skimbrowser/ui/components/SiteIcons.kt`: `SiteIcons`, `LocalSiteIcons`, `siteIcon()`
- `app/src/test/java/com/dantech/skimbrowser/ui/components/FaviconTilesScreenshotTest.kt`: 6 pixel checks and 2 goldens
- `app/src/test/screenshots/components/favicon-tiles.png`, `favicon-tiles-dark.png`

Modified:
- `data/.../AppStorage.kt`: adds `siteIconDir` (`cacheDir/site-icons`), also in `temp()`
- `browser/.../SkimChromeClient.kt`: overrides `onReceivedIcon` and adds an `onIcon` lambda whose default does nothing. It reports nothing after `detach` or for a null icon.
- `browser/.../WebViewPool.kt`: adds `siteIcons: SiteIconStore? = null` and passes `onIcon` with the tab's incognito flag
- `app/.../di/browser/ActivityBrowserModule.kt`: passes the store to the pool
- `browser/.../BrowsingDataCleaner.kt`: adds the `siteIcons` parameter. `clearHistory` calls `clearAll` and then `awaitIdle`, right after the thumbnails.
- `browser/.../IncognitoSession.kt`: the `@Inject` constructor's `onEnded` calls `siteIcons.clearIncognito()`
- `browser/src/testFixtures/.../TabManagerFixture.kt`: adds `siteIconDir` and `siteIcons`, and `settle()` waits for the store
- `app/.../di/ShellEntryPoint.kt`: adds `siteIcons()`
- `app/.../ui/shell/SkimApp.kt`: provides `LocalSiteIcons` once, inside `SkimTheme`
- `app/.../ui/components/ShapeTiles.kt`: `FaviconLetterTile(host, incognito)` and `ShortcutTile(host)`, plus a private `IconImageTile`
- Call sites:
  - `TabCard.kt`: new `host` parameter, and incognito is passed to the tile
  - `TabsViewModel.kt`: `TabCardState.host`, taken from the tab URL
  - `TabsScreen.kt`
  - `HistoryScreen.kt`: `entry.host`
  - `BookmarksScreen.kt`: `bookmark.host`
  - `SkimHistoryViewModel.kt` and `SkimHistoryScreen.kt`: `SkimHistoryRow.host`
  - `MoreMenuSheet.kt`: `state.host` and `state.incognito`
  - `SiteInfoSheet.kt`: `state.siteKey` and `state.incognito`
  - `HomeScreen.kt`: Continue cards use `card.host`. Shortcuts use `remember(shortcut.url) { hostOf(shortcut.url) }`.
- Tests: `SkimChromeClientTest.kt` (forwarding test), `BrowsingDataCleanerTest.kt` (icons cleared with history), `SiteInfoViewModelTest.kt` (constructor argument only)
- `docs/design-guidelines.md`: one row appended to the known-deviations table
- `phase-02-site-icons.md`: Todo boxes ticked, except the size decision

## Deviations from the decided design

1. **`LocalSiteIcons` is typed `SiteIcons?`, not `SiteIconStore?`.** `DomainBoundaryTest.layersStayInward` failed on `ui/components/SiteIcons.kt contains com.dantech.skimbrowser.browser`. AGENTS.md says not to add an exception, so `ui/components/SiteIcons.kt` now declares `fun interface SiteIcons { fun observe(host: String, incognito: Boolean): StateFlow<ImageBitmap?> }`. `SkimApp` adapts the store with `remember(store) { SiteIcons(store::observe) }`. It never names the browser package, because the type is inferred from `rememberShell().siteIcons()`. The store, the `siteIcons()` accessor, `siteIcon()` and the tile behaviour are as decided.
2. **Bookmarks:** `BookmarkRow` already had `host`, so its ViewModel did not change.
3. **`clearAll` also forgets incognito icons**, the same as `ThumbnailStore.clearAll`. `clearIncognito` keeps normal icons.
4. **Small additions inside the decided API:**
   - A normal put whose pixels match the icon already in memory (`Bitmap.sameAs`) does not rewrite the file. Every page load sends the icon again.
   - Cache entries for hosts that do not have an icon yet count 1 byte each.
   - An incognito entry that falls back on the normal icon is updated when that icon arrives later, from a put or from a read off disk.
5. **`WebViewPool`'s new parameter defaults to null**, so the existing pool tests did not need changes. The Hilt module always passes the store.

## Test and verify results

Every command ran with `export ANDROID_HOME=/root/android-sdk`.

- `./gradlew :browser:testDebugUnitTest --tests 'com.dantech.skimbrowser.browser.SiteIconStoreTest' --tests 'com.dantech.skimbrowser.browser.SkimChromeClientTest' --no-configuration-cache`: pass. Later, as part of the app run, the whole `:browser:testDebugUnitTest` passed: 679/679.
- `./gradlew :app:testPlayDebugUnitTest --tests '...FaviconTilesScreenshotTest' --tests '...BrowsingDataCleanerTest' --tests '...SiteInfoViewModelTest' --no-configuration-cache`: 16/16 pass.
- `./gradlew :app:recordRoborazziPlayDebug --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.components.FaviconTilesScreenshotTest'`: 8/8. Afterwards `git status --short -- app/src/test/screenshots` shows only the two new PNGs.
- `./gradlew :app:testPlayDebugUnitTest --no-configuration-cache`: 916/916 pass. The first run failed `DomainBoundaryTest` (deviation 1); it was fixed by the adapter, not by an exception.
- `./gradlew :app:verifyRoborazziPlayDebug --no-configuration-cache`: BUILD SUCCESSFUL, 916/916. No existing golden changed.
- `./gradlew :app:assemblePlayReleaseSmoke --no-configuration-cache` then `python3 tools/check_apk_budget.py --baseline size-budget/releaseSmoke.json`:
  `apk 4397565 bytes, baseline 4396917, growth 648, allowed 8192, limit 4405109` (exit 0). The baseline was not touched.

## Not verified

- No device run, as instructed. That leaves three things unchecked: that WebView really calls `onReceivedIcon` on every page, the icon sizes real sites send, and how upscaled 16 px icons look on a 40 dp tile.
- Clearing incognito icons when the session ends goes through the `@Inject` constructor's `onEnded` lambda, the same way `shield.clearIncognito()` does. No JVM test drives that production lambda. `SiteIconStoreTest.clearIncognitoKeepsTheNormalIcons` covers what `clearIncognito` does, but not that the session calls it. The phase file asks for a `BrowsingDataCleaner` test of the incognito session. That test does not exist, because the cleaner does not end the session.

## Review follow-up

- The gap above is closed: `IncognitoSessionTest.endingTheSessionForgetsTheIconsItsTabsReceivedAndKeepsTheNormalOnes` builds the session through its injected constructor and ends it.
- The gates were run again after that test: `:app:testPlayDebugUnitTest`, `:app:verifyRoborazziPlayDebug` and the size check pass.
- The APK file size did not move with this phase because the new code fit in the alignment padding before the native libraries. The next change may cross a 16 KB page.

## Independent review, after the phase was pushed

An independent review of the pushed commit found defects the tests above did not catch. Every clear test called `put` before `observe`, so no entry changed size while it was in the cache.

Fixed in a follow-up commit:

- **Clearing threw.** An entry already in the cache changed size in place: an icon arriving for a tile already drawn, an icon read from disk, or a larger icon for the same host. `LruCache` kept counting the entry as one byte, so `clearAll` and `clearIncognito` threw `IllegalStateException` from `evictAll`. Clear history then left the icon files on disk, and the end of an incognito session did not forget its icons. `EntryCache.resize` now takes the entry out, changes it and puts it back. Five tests in `SiteIconStoreTest` fail without it.
- **A put racing a clear could leave a file.** The write and the delete were queued outside the lock. Both are now queued under it, so the file work keeps the order of the changes in memory. There is no test: the window is between two statements and cannot be opened on purpose.
- **A tile asked for its icon on every recomposition.** `siteIcon` now remembers the flow for its store, host and incognito flag. `FaviconTilesScreenshotTest.aTileAsksForItsIconOnceWhileItsSiteStaysTheSame` fails without it.
- **`MAX_ICON_PX` was 96**, under the 120 px a 40 dp tile draws at xxhdpi. It is 128.

Left as it is:

- An icon that arrives after the person moved on to another site is filed under the new page's host, because `onReceivedIcon` carries no page URL. The new page's own icon replaces it when it arrives; a site that sends none keeps the wrong one. To be watched on a device.
- The review also said `onReceivedIcon` never fires without `WebIconDatabase.open`. WebView turns icon downloads on by itself for apps that target Android 4.4 or later, so nothing was added. To be confirmed on a device with the first page that loads.
