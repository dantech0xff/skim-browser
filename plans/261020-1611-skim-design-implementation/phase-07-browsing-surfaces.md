---
phase: 7
title: "Browsing surfaces"
status: done-with-concerns
priority: P1
effort: "29h"
dependencies: [4, 5, 6]
---

# Phase 7: Browsing surfaces

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Scout `plans/reports/scout-261020-2319-design-inventory.md` §1 (address bar, sheet, menu, snackbar), §2 (05, 05b, 05c, 05d, 11, 15), §3(d) (Site permission, Edit bookmark, Move to folder, Site information sheet, snackbar Bookmark added/Opened in a new tab/downloaded/Copied), §3(e) #7.
- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §A4 (lỗi, find, nhấn giữ, desktop, download), §A5 (pull to refresh), §B10 (Internet panel, clipboard, share, share ảnh).
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-security-adversary.md` Finding 5, 8; `red-team-assumption-destroyer.md` Finding 6, 8; `red-team-scope-complexity-critic.md` Finding 8, 9, 10; `red-team-failure-mode-analyst.md` Finding 4.
- `design/spec/navigation-map.md` mục 05, 05b, 05c, 05d, 11, rule "Reload", "Edge to edge", "Floating toolbar", system UI "Share", "Internet panel", "Site permissions", "Clipboard"; `design/spec/dialogs.md` (Site permission, Edit bookmark, Move to folder, Site information sheet, snackbar).
- Board: `design/screens/05-browsing.png`, `05-browsing--loading.png`, `05b-more-menu.png`, `05c-find-in-page.png`, `05d-link-image-options.png`, `05d-link-image-options--image.png`, `11-page-failed-to-load.png`, `11-page-failed-to-load--no-internet.png`, `11-page-failed-to-load--timed-out.png`, `15-dialogs-menus-snackbars.png` (tất cả trong `design/screens/`).

## Overview

Xây mọi bề mặt quanh trang web: 05 (đã tải và đang tải, pull to refresh, trang chạy dưới toolbar), 05b More menu (biến thể trang và biến thể New tab), 05c Find in page, 05d Link và Image options, 11 (ba trạng thái thiết kế + trạng thái renderer crash D8), sheet Site information, dialog Site permission, Add/Edit bookmark (snackbar "Bookmark added › Edit"), dialog Edit bookmark và Move to folder, snackbar tải xong, copy, share trang/link/ảnh. Thay slot `page` của `BrowserRoute`. Kết thúc bằng cổng thiết bị (pull to refresh và các luồng nhấn giữ được kiểm trên emulator trước khi xây tiếp).

Ghi chú phạm vi: dialog Edit bookmark và Move to folder được chuyển từ phase 8 sang đây vì 05b và snackbar "Bookmark added › Edit" cần chúng trước; phase 8 dùng lại cho Bookmark menu.

## Key insights

- Trang phải chạy dưới floating toolbar và nav bar (rule Edge to edge): `WebViewHost` chiếm tới cạnh dưới, toolbar đè lên.
- Lỗi tải hiển thị bằng UI Compose đè lên WebView (WebView vẫn sống) để không mất history (researcher B §A4).
- Màu highlight find-in-page và highlight link/ảnh khi nhấn giữ do Chromium vẽ, không theo được `primary`/`primaryContainer` của bản vẽ `[UNVERIFIED]`; ghi vào "Known deviations".
- Địa chỉ: bản vẽ 05 hiện host ("daylightjournal.com") còn 11 hiện host + path ("nightbus-kansai.jp/timetable"). Quyết định nhỏ của planner: trang đã tải hiện host (bỏ `www.`), trạng thái lỗi hiện URL bỏ scheme.
- Clipboard: Android 13+ tự hiện xác nhận, nên snackbar "Copied to clipboard" chỉ hiện khi `SDK_INT <= 32`.
- Snackbar từ sheet: hành động trong 05b/05d đóng sheet trước rồi mới hiện snackbar (host của cửa sổ chính).
<!-- Red Team: Hợp đồng Activity-scoped — dùng LocalBrowserHost — 2026-10-21 -->
- Đối tượng Activity-scoped (`SitePermissionBridge`, `DownloadRouter`, `BlobBridge`, pool) lấy từ `LocalBrowserHost` (phase 4), không lấy từ `AppContainer`. `BrowsingDataCleaner` có constructor tường minh và nhận `TabEngine` theo từng lời gọi.
<!-- Red Team: Ranh giới dữ liệu incognito — 2026-10-21 -->
- Từ tab incognito: copy link gắn `ClipDescription.EXTRA_IS_SENSITIVE` (để bàn phím không lưu vào lịch sử clipboard); ảnh share ghi vào `cacheDir/shared/incognito/` (xoá khi phiên kết thúc và lúc khởi động); download do router phase 4 chạy trong process.
<!-- Red Team: Một đường duy nhất cho Move to folder — 2026-10-21 -->
- Chỉ có một đường đổi thư mục: dialog `MoveToFolderKey(bookmarkId)`, chọn là lưu ngay. Ô Folder trong Edit bookmark mở chính dialog đó; Edit bookmark hiển thị thư mục hiện tại từ DB và Save chỉ lưu Name/URL (quyết định nhỏ của planner).

## Requirements

Chức năng:
- 05: address bar (`SiteIndicator.Secure` với https, `NotSecure` với http `[DEFAULT — chờ validate]` D8, `Error` với 11), địa chỉ là nút → 04b với URL được chọn sẵn, nút Reader view hiện khi `runtime.readerable` (đích ở phase 10), wavy progress khi đang tải; WebView; pull to refresh ở đỉnh trang (không có nút reload); nhấn giữ link/ảnh → 05d; toolbar nổi (rule "Floating toolbar" cho 05, 05c, 11): Back/Forward theo history của tab (Back bật khi Back hệ thống có việc để làm trong tab), Tabs → 09/09b, More → 05b; AI FAB (đích ở phase 11).
- 05b (sheet): header (favicon, title, domain, sao Add/Edit bookmark, Share page), hàng Page actions (Find in page, Desktop site dạng toggle), nhóm [New tab, New incognito tab], [Bookmarks, History], [Settings]. Mở từ trang New tab (04/09c) → chỉ ba nhóm, ẩn header và page actions (quyết định nhỏ đã chốt). Trên tab incognito vẽ theo scheme tối.
- Add bookmark → lưu vào Mobile bookmarks + snackbar "Bookmark added" + Edit (`SnackbarAction.EditBookmark(id)`). Sao filled (đã bookmark, cd `edit_bookmark_title`) → dialog Edit bookmark.
- Desktop site bật/tắt theo tab rồi reload. Trạng thái bật chưa được vẽ: nút dùng màu `primary`/`onPrimary` và icon `ic_check` như nút được chọn của connected group (quyết định nhỏ của planner).
- 05c: thanh Find thay address bar; gõ → highlight + đếm `n/m` (`liveRegion = Polite`); Previous/Next vòng quanh; Close và Back xoá highlight. Không có match → "0/0" (thiết kế chưa có, giữ mặc định).
- 05d Link: Open in new tab (mở nền + snackbar "Opened in a new tab" + Switch = `SwitchToTab(id)`), Open in incognito tab (tab incognito tối có link; ẩn khi tab hiện tại đã là incognito, khi đó Open in new tab mở tab incognito — quyết định nhỏ của planner), Copy link (incognito → cờ sensitive), Share link. 05d Image (cả ảnh nằm trong link): Open image in new tab (+ snackbar Switch), Download image (D5 đã chốt: cùng `DownloadRouter` của phase 4), Share image. Header ảnh: ô 56 bo 12 với `ic_image` như bản vẽ. Vuốt xuống, chạm ngoài để đóng; mọi hành động đóng sheet.
- 11: ba trạng thái theo `LoadError`: art (SoftBurst + `ic_public_off` / Cookie12Sided + `ic_wifi_off` / Clover8Leaf + `ic_schedule`), tiêu đề, body có host, Try again (tải lại), Internet settings (chỉ No internet: `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` API 29+, `Settings.ACTION_WIRELESS_SETTINGS` 26–28), mã lỗi `bodySmall`; No internet tự tải lại khi có mạng (engine phase 4, chỉ tab đang hiển thị); Back/Forward/Tabs/More như 05; FAB tắt với cd `cd_ask_skim_unavailable`.
<!-- Red Team: Trạng thái renderer crash vượt ngân sách — 2026-10-21 -->
- `LoadError.Crashed` (renderer crash vượt ngân sách, phase 4): cùng bố cục 11, art SoftBurst + `ic_error`, copy `error_crashed_title`/`error_crashed_body` `[DEFAULT — chờ validate]` D8, chỉ có Try again (không tự tải lại).
- Site information sheet: favicon + host + (https) `ic_lock` 16 "Connection is secure"; mục Permissions: Skim AI + switch (ẩn với tab incognito; ngữ nghĩa ở phase 3), Location (+ Camera/Microphone) chỉ khi site đã xin, phụ đề "Blocked"/"Allowed", Cookies and site data + nút xoá (cd `cd_delete_site_data`). Tab incognito đọc quyết định từ `IncognitoSession`.
- Dialog Site permission (từ `LocalBrowserHost.current.sitePermissions.pending`): icon `ic_location_on` `primary`, tiêu đề theo loại (camera/mic dùng copy D8, camera không có icon vì bộ icon thiết kế không có camera), body chung, Block / Allow. Đóng bằng Back/scrim = từ chối lần này, không lưu.
- Dialog Edit bookmark (`EditBookmarkKey(bookmarkId)`): Name, URL, Folder (field chỉ đọc + `ic_arrow_drop_down`, cd `cd_folder_field`, hiện thư mục hiện tại) → `MoveToFolderKey(bookmarkId)`; Cancel/Save (Save lưu Name/URL). Dialog Move to folder (`MoveToFolderKey(bookmarkId)`): radio các thư mục cấp 1, chọn là `move` + đóng.
- Snackbar: "%s downloaded" + Open (`OpenDownload(uri, mime)`, MIME lấy từ MediaStore/DownloadManager), lỗi tải `snackbar_download_failed` 4 s (D8; gồm cả download tự động bị chặn), "Copied to clipboard" (05d, SDK ≤ 32), "Opened in a new tab" + Switch, "Bookmark added" + Edit.
- Share: page (`ACTION_SEND` text/plain, `EXTRA_TEXT` = URL, `EXTRA_TITLE` = title, chooser), link, ảnh (byte ảnh lấy bằng OkHttp với cookie của profile tab, `data:` giải mã, `blob:` qua `BlobBridge`; ghi `cacheDir/shared/` hoặc `cacheDir/shared/incognito/`; `FileProvider` + `EXTRA_STREAM` + `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`).

Phi chức năng:
- Thanh Find, sheet và dialog không chặn WebView tiếp tục tải.
- Ảnh share tối đa 25 MB; file trong `cacheDir/shared/` (thường) cũ hơn 1 ngày bị xoá lúc khởi động; `cacheDir/shared/incognito/` xoá toàn bộ ở `IncognitoSession.end()` và lúc khởi động.

## Architecture

```kotlin
// ui/browsing
@Composable fun BrowsingRoute(tab: Tab, runtime: TabRuntime, container: AppContainer, navigator: SkimNavigator)
@Composable fun BrowsingScreen(state: BrowsingUiState, actions: BrowsingActions, page: @Composable () -> Unit)
data class BrowsingUiState(val address: String, val indicator: SiteIndicator, val readerAvailable: Boolean, val progress: Float?,
    val loadError: LoadError?, val backEnabled: Boolean, val forwardEnabled: Boolean, val tabCount: Int, val incognito: Boolean,
    val aiFab: AiFabMode, val find: FindUiState?)
class BrowsingViewModel(tabId: String, tabs: TabManager, bookmarks: BookmarkRepository, snackbar: SnackbarController)
@Composable fun FindInPageBar(state: FindUiState, onQuery: (String) -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit)
@Composable fun LoadErrorContent(error: LoadError, onTryAgain: () -> Unit, onInternetSettings: () -> Unit)
@Composable fun MoreMenuSheet(tabId: String, ...)          // entry MoreMenuKey
@Composable fun LinkOptionsSheet(tabId: String, target: HitTarget, ...)   // entry LinkOptionsKey
@Composable fun SiteInfoSheet(tabId: String, ...)          // entry SiteInfoKey
@Composable fun SitePermissionDialog(pending: PendingSitePermission, onAllow: () -> Unit, onBlock: () -> Unit)
@Composable fun DownloadSnackbarEffect(tracker: DownloadTracker, snackbar: SnackbarController)
// ui/bookmarks/BookmarkDialogs.kt
@Composable fun EditBookmarkDialog(bookmarkId: Long, ...)                 // entry EditBookmarkKey
@Composable fun MoveToFolderDialog(bookmarkId: Long, ...)                 // entry MoveToFolderKey, dùng ChoiceDialog phase 2
// system
object ShareSheet { fun sharePage(ctx: Context, title: String, url: String); fun shareText(ctx: Context, text: String); fun shareImage(ctx: Context, uri: Uri, mime: String) }
object Clipboard { fun copy(ctx: Context, text: String, sensitive: Boolean): Boolean }   // true = app nên tự hiện xác nhận (SDK ≤ 32); caller hiện snackbar trên host của mình
object InternetPanel { fun intent(): Intent }
class ShareImage(httpClient: OkHttpClient, cacheDir: File, incognito: IncognitoSession) {
    suspend fun prepare(tab: Tab, src: String, blob: BlobBridge, webView: WebView?): Uri?   // blob lấy từ LocalBrowserHost theo lời gọi
}
// browser/BrowsingDataCleaner.kt (process-scoped, constructor tường minh; phase 8 bổ sung clearAll)
class BrowsingDataCleaner(history: HistoryRepository, tabRepo: TabRepository, tabs: TabManager, stateStore: TabStateStore,
    thumbnails: ThumbnailStore, incognito: IncognitoSession, undo: UndoRegistry, snackbar: SnackbarController, db: SkimDatabase) {
    suspend fun clearSite(host: String, incognitoTab: Boolean)
}
```

- `BrowsingRoute` nằm trong slot `page` của `BrowserRoute`: `Box { Column { statusBar padding; AddressBar hoặc FindInPageBar; PullToRefreshBox(enabled = loadError == null) { WebViewHost(tab.id) } }; if (loadError != null) LoadErrorContent(...); BrowserToolbar(...) căn đáy }`. `SitePermissionDialog` hiển thị khi `host.sitePermissions.pending != null` và tab của request là tab hiện tại.
- Pull to refresh: `PullToRefreshBox(isRefreshing = refreshRequested && runtime.progress != null, onRefresh = { refreshRequested = true; tabs.reload(id) }, indicator = { PullToRefreshDefaults.LoadingIndicator(state, isRefreshing, Modifier.align(TopCenter)) })`; reduced motion → indicator tĩnh.
- Nhấn giữ: `tabs.hitTargets.filter { it.first == tab.id }` → `navigator.openLinkOptions(tab.id, target)`.
- Hành động sheet dùng `navigator.back()` trước rồi mới thao tác/hiện snackbar; đi tới màn khác dùng `navigator.replaceTop(key)`.
- `DownloadSnackbarEffect` đặt ở `SkimApp` để báo tải xong ở bất kỳ màn nào.
- `BrowsingDataCleaner.clearSite`: `WebStorage.deleteOrigin` cho `https://host` và `http://host`; cookie: đọc `getCookie("https://host")`, ghi đè từng tên với `Max-Age=0` cho `path=/` và `domain=.host` rồi `flush()` `[UNVERIFIED: không xoá được cookie path khác/HttpOnly khác domain]`; tab incognito dùng cookie manager/web storage của profile phiên.
- `Clipboard.copy(sensitive = true)`: `ClipData.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }` (API 33+; API 24–32 dùng khoá chuỗi `"android.content.extra.IS_SENSITIVE"`).

Luồng dữ liệu bookmark: 05b sao → `BookmarkRepository.addToMobile(title, url)` → id → `snackbar.show(snackbar_bookmark_added, SnackbarAction.EditBookmark(id))`; trạng thái sao lấy từ `observeByUrl(url)`.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingRoute.kt`, `BrowsingScreen.kt`, `BrowsingViewModel.kt`, `FindInPageBar.kt`, `LoadErrorContent.kt`, `MoreMenuSheet.kt`, `LinkOptionsSheet.kt`, `SiteInfoSheet.kt`, `SitePermissionDialog.kt`, `DownloadSnackbarEffect.kt` (cùng thư mục `ui/browsing/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/bookmarks/BookmarkDialogs.kt`
- `app/src/main/java/com/dantech/skimbrowser/system/ShareSheet.kt`, `Clipboard.kt`, `InternetPanel.kt`, `ShareImage.kt` (cùng thư mục `system/`)
- `app/src/main/java/com/dantech/skimbrowser/browser/BrowsingDataCleaner.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/ui/browsing/BrowsingViewModelTest.kt`, `BrowsingScreenshotTest.kt` (05, 05 loading, 05c, 11 ×3, 11 Crashed; `captureBoard` cho 05, 05--loading, 05c, 11 ×3), `BrowsingOverlaysScreenshotTest.kt` (05b trang, 05b New tab, 05b tối, 05d Link, 05d Image, Site information, Site permission, snackbar) — đều gọi `assertA11y()`; `app/src/test/java/com/dantech/skimbrowser/ui/bookmarks/BookmarkDialogsTest.kt` (+ ảnh Edit bookmark, Move to folder; Folder field mở `MoveToFolderKey`); `app/src/test/java/com/dantech/skimbrowser/system/ClipboardTest.kt` (`@Config(sdk = [32, 33])`; cờ sensitive khi incognito), `ShareImageTest.kt` (MockWebServer: header Cookie, giới hạn 25 MB, `data:`, tab incognito ghi vào `shared/incognito/`)
- Instrumented: `app/src/androidTest/java/com/dantech/skimbrowser/ui/browsing/BrowsingFlowsTest.kt` (pull to refresh ở đỉnh reload, cuộn giữa trang không reload; nhấn giữ link mở 05d; find đếm đúng; lỗi DNS → 11 Unreachable; tab incognito: copy link có cờ sensitive)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: slot `page = { tab, runtime -> BrowsingRoute(tab, runtime, container, navigator) }`; entry sheet `MoreMenuKey`, `LinkOptionsKey`, `SiteInfoKey`; entry dialog `EditBookmarkKey`, `MoveToFolderKey`.
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt`: thêm `DownloadSnackbarEffect(container.downloadTracker, container.snackbarController)`.
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `shareImage`, `browsingDataCleaner` (constructor tường minh); dọn `cacheDir/shared/` cũ hơn 1 ngày và toàn bộ `cacheDir/shared/incognito/` khi tạo container.

## Implementation steps

1. `BrowsingViewModel`: trạng thái từ `tabs.state` + `tabs.runtime(tabId)` + `bookmarks.observeByUrl`; quy tắc `address`, `indicator`, `backEnabled`; hành động find (debounce 100 ms), desktop, bookmark, reload, try again. Test các quy tắc.
2. `BrowsingScreen` + `BrowsingRoute` (Back hệ thống khi Find đang mở → đóng Find trước). Toolbar dùng `BrowserToolbar` + `TabAiFab(tab, runtime) { navigator.openAskPage(tab.id) }`.
3. `FindInPageBar` theo scout §2 05c (khung padding 8×16, thanh 56 bo 28 padding 0 4 0 20, input `bodyLarge` label ẩn `find_in_page`, đếm `find_match_count`, ba IconButton 48 với cd `cd_previous_match`, `cd_next_match`, `cd_close_find_in_page`).
4. `LoadErrorContent` theo scout §2 11 (art 128 `primaryContainer`/`onPrimaryContainer` icon 56, tiêu đề `headlineMediumEmphasized`, body `bodyLarge onSurfaceVariant`, cột nút max 280 gap 12, nút M filled `ic_refresh` + Try again, nút M tonal `ic_wifi` + Internet settings, mã lỗi `bodySmall`), thêm nhánh `Crashed`.
5. `MoreMenuSheet` (header padding 0 8 16 16 gap 12, page actions tonal M 56 bo 28 `titleMedium`, ba nhóm segmented 56 icon `onSurfaceVariant`).
6. `LinkOptionsSheet` (header Link: tile 40 Cookie4Sided `surfaceContainerHighest` + `ic_link` 20; Image: ô 56 bo 12 + `ic_image`; nhóm segmented 56). Open in new tab: `navigator.openInBackground(url, tab.incognito)` → `snackbar.show(snackbar_opened_in_new_tab, SwitchToTab(id))`. Download image: `host.downloads.start(...)`. Share image: `shareImage.prepare(tab, src, host.blob, webView)` → `ShareSheet.shareImage`; lỗi → `snackbar_share_failed` (D8). Copy link: `if (Clipboard.copy(ctx, url, sensitive = tab.incognito)) snackbar.show(snackbar_copied)`.
7. `SiteInfoSheet` + ViewModel nhỏ (tab thường: `SitePermissionRepository.observe(host)` + `settings.askBeforeSending`; tab incognito: `IncognitoSession.decision`); switch Skim AI ghi `ALLOWED`/`ASK`; switch Location ghi `ALLOWED`/`BLOCKED`; nút xoá gọi `browsingDataCleaner.clearSite(host, tab.incognito)`.
8. `SitePermissionDialog` dùng `SkimAlertDialog` (icon `ic_location_on` tint `primary`).
9. `BookmarkDialogs.kt`: Edit bookmark (ViewModel nhỏ: `observe(id)` cho Name/URL/thư mục hiện tại; Folder → `navigator.openDialog(MoveToFolderKey(id))`; Save → `update(id, title, url)`), Move to folder (`topLevelFolders`, chọn → `move` + đóng); tên thư mục MOBILE/IMPORTED lấy từ string `bookmarks_folder_mobile`/`bookmarks_folder_imported`.
10. `system/*` và `BrowsingDataCleaner` (constructor + `clearSite`).
11. `DownloadSnackbarEffect`: `DownloadCompleted(fileName, uri, mime)` → `snackbar.show(snackbar_downloaded(fileName), OpenDownload(uri, mime))`; `DownloadFailed` → snackbar lỗi 4 s.
12. Sửa `SkimNavDisplay.kt`, `SkimApp.kt`, `AppContainer.kt`.
13. Ảnh Roborazzi với slot trang là `Box` rỗng (WebView không vẽ trên Robolectric); duyệt so với 9 PNG, ghi lệch (gồm highlight do Chromium vẽ) vào `docs/design-guidelines.md`.
14. Cổng thiết bị (quy trình chung ở `plan.md`): `BrowsingFlowsTest` trên `skim_api36` và `skim_api28`; nếu pull to refresh không đạt trên emulator thì chuyển ngay sang `SwipeRefreshLayout` (phương án dự phòng) trước khi sang phase 8.
15. Commit cuối phase: `feat(browsing): add browsing screen, menus, find, link options and load errors`.

## Todo list

- [x] BrowsingViewModel (địa chỉ, indicator, back/forward, find, desktop, bookmark) + test
- [x] BrowsingScreen/Route: address bar, WebView dưới toolbar, pull to refresh, toolbar, FAB, nhấn giữ → 05d
- [x] 05c FindInPageBar
- [x] 11 LoadErrorContent ba trạng thái + Crashed (D8) + Internet panel
- [x] 05b MoreMenuSheet (biến thể trang, New tab, tối)
- [x] 05d LinkOptionsSheet (Link, Image) + snackbar Switch dạng dữ liệu
- [x] Site information sheet (thường/incognito) + BrowsingDataCleaner (constructor tường minh, clearSite)
- [x] Site permission dialog từ LocalBrowserHost
- [x] Edit bookmark + Move to folder (một đường duy nhất qua MoveToFolderKey)
- [x] Share trang/link/ảnh (shared/incognito/), Clipboard (sensitive khi incognito, snackbar ≤ 32), DownloadSnackbarEffect (OpenDownload)
- [x] Thay slot page + đăng ký 5 entry
- [x] Ảnh Roborazzi + `assertA11y` + lệch ghi vào docs
- [ ] Cổng thiết bị `skim_api36` + `skim_api28` (quyết định pull to refresh) — chưa chạy: máy này không có system image
- [x] Commit cuối phase

## Success criteria

- Mọi row của 05 (trừ Reader view: phase 10; Ask Skim: phase 11; Back on the first page: phase 5; File input/fullscreen và tel:/mailto:/intent:: phase 4), 05b, 05c (trừ Ask Skim: phase 11), 05d và toàn bộ 11 (kể cả FAB tắt "unavailable until the page loads") chạy đúng theo coverage-matrix.md.
- Overlay Site information, Site permission, Edit bookmark, Move to folder và snackbar Bookmark added, Opened in a new tab, downloaded, Copied to clipboard (05d) đúng dialogs.md.
- System UI "Share", "Internet panel", "Site permissions", "Clipboard" chạy đúng trên emulator.
- Rule "Reload", phần "Pages scroll under the floating toolbar" của rule Edge to edge, và rule "Floating toolbar" trên 05/05c/11 đạt.
- Ảnh Roborazzi đã duyệt so với 9 PNG của 05/05b/05c/05d/11 (phần trang mẫu bỏ qua), lệch đã ghi.

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.browsing.*' --tests 'com.dantech.skimbrowser.ui.bookmarks.*' --tests 'com.dantech.skimbrowser.system.*'
./gradlew :app:recordRoborazziDebug --tests '*BrowsingScreenshotTest*' --tests '*BrowsingOverlaysScreenshotTest*' --tests '*BookmarkDialogsTest*'
./gradlew :app:verifyRoborazziDebug
# Cổng thiết bị (plan.md): mỗi AVD
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.ui.browsing.BrowsingFlowsTest
```

Thủ công trên emulator: bật chế độ máy bay khi đang ở trang → mở link → 11 No internet → tắt chế độ máy bay → trang tự tải lại; trang có `navigator.geolocation` → dialog Site permission → Allow → prompt Android; tải ảnh → snackbar "… downloaded" → Open.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Nested scroll của `SkimWebView` đủ cho `PullToRefreshBox` | Kéo không refresh / refresh nhầm ở cổng thiết bị | Chuyển `SwipeRefreshLayout` bọc WebView trong `AndroidView` ngay trong phase này (dự phòng đã chốt) |
| `requestFocusNodeHref` trả anchor text | Header 05d trống | Hiện URL ở cả hai dòng (dòng 1 là host) |
| Xoá cookie theo host bằng cách ghi đè hết hạn đủ | Site vẫn còn đăng nhập | Ghi nhận giới hạn; xoá toàn bộ vẫn có ở Clear browsing data |
| Share ảnh cần cookie của profile đúng | Ảnh riêng tư trả 403 | Dùng cookie manager của profile tab; lỗi → snackbar share lỗi |
| Bàn phím tôn trọng `EXTRA_IS_SENSITIVE` trên API < 33 `[UNVERIFIED]` | URL incognito vẫn vào lịch sử clipboard | Chấp nhận trên API < 33 (không có API hệ thống); ghi ở roadmap |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 7 (slot `page` về `WebViewHost`; gỡ entry đã đăng ký).

## Security considerations

- Dialog quyền site luôn đi trước runtime permission; quyết định của tab incognito chỉ nằm trong `IncognitoSession`.
- Share ảnh chỉ qua `FileProvider` với quyền đọc tạm thời; file incognito nằm riêng `shared/incognito/` và bị xoá khi phiên kết thúc.
- Copy link không log nội dung clipboard; copy từ incognito đánh dấu sensitive.
- Mở file đã tải dùng `FLAG_GRANT_READ_URI_PERMISSION` trên URI của DownloadManager/MediaStore với MIME do hệ thống trả, không dùng MIME do trang khai.

## Next steps

- Phase 8 dùng `EditBookmarkDialog`/`MoveToFolderDialog` cho Bookmark menu và bổ sung `BrowsingDataCleaner.clearAll`.
- Phase 10 làm nút Reader view chạy end-to-end; phase 11 nối AI FAB qua cổng 06d.
