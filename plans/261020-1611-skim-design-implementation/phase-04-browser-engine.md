---
phase: 4
title: "Browser engine"
status: completed
priority: P1
effort: "44h"
dependencies: [1, 3]
---

# Phase 4: Browser engine

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §A1–§A8 (một WebView mỗi tab, profile incognito, FLAG_SECURE, client, pull to refresh, thumbnail, Readability, extractor + highlight), §B10 (share ảnh), bảng rủi ro, mục "Giới hạn".
- Red team (thư mục `plans/261020-1611-skim-design-implementation/reports/`): `red-team-failure-mode-analyst.md` Finding 1, 3, 4, 5, 6, 7, 10; `red-team-security-adversary.md` Finding 2, 5, 8; `red-team-assumption-destroyer.md` Finding 1, 2, 5, 6, 8; `red-team-scope-complexity-critic.md` Finding 5, 8, 9.
- `design/spec/navigation-map.md`: Rules (Incognito, Process death, Reload), 05 (Download, File input, tel:/mailto:/intent:), 09c (Storage, Last incognito tab closes), 11 (Which state shows, Connection comes back), "Android system UI, not drawn".
- `design/spec/components.md` mục "Web content", "Pull to refresh".
- Thực thể: `plans/reports/scout-261020-2319-design-inventory.md` §3(c) (`Tab`, `PageLoadError`, `FindInPageState`, `LinkContext`/`ImageContext`, `DownloadResult`).

## Overview

Xây package `com.dantech.skimbrowser.browser`: quản lý tab (state + lệnh, sống theo process), pool WebView sống theo Activity (LRU 3, ghim cặp opener ↔ popup), `SkimWebView` hỗ trợ nested scroll cho pull to refresh, các client, lưu/khôi phục state nguyên tử, chờ trang sẵn sàng cho tab không hiển thị, phiên incognito có vòng đời rõ ràng (Active → Ending → kết thúc), FLAG_SECURE, ánh xạ lỗi tải + tự tải lại có giới hạn, tải xuống đã gia cố (D5), cầu nối quyền site, intent ra ngoài, tìm trong trang, nhấn giữ, desktop UA, popup, thumbnail, ngân sách renderer crash, và script trang (Readability + extractor bỏ phần tử ẩn + highlight có kiểm trang). Mọi đối tượng sống theo Activity lộ ra qua một composition local duy nhất `LocalBrowserHost`. Phase kết thúc bằng cổng thiết bị.

## Key insights

- WebView phải tạo bằng context của Activity, còn ViewModel sống lâu hơn Activity khi Activity bị tạo lại. Vì vậy tách hai lớp: `TabManager` (process, trong `AppContainer`, giữ state và lệnh) và `WebViewPool` (Activity, giữ WebView sống). `TabManager.attach(pool)` ở `onCreate`, `detach()` ở `onDestroy`.
<!-- Red Team: Hợp đồng Activity-scoped thiếu đường tới consumer — 2026-10-21 -->
- Mọi đối tượng sống theo Activity (pool, `SitePermissionBridge`, `DownloadRouter`, `BlobBridge`, `ActivityHost`, `FullscreenController`, `WindowSecureController`) gom vào `BrowserHost` và lộ qua một `staticCompositionLocalOf<BrowserHost>` duy nhất là `LocalBrowserHost`. Không đối tượng nào trong số này được đặt vào `AppContainer`. Quyết định quyền site của phiên incognito nằm trong `IncognitoSession` (process), không nằm trong bridge.
- `WebViewCompat.setProfile` phải gọi trước mọi thao tác khác trên WebView; profile đã load không xoá được trong cùng process; không có cờ off-the-record (researcher B §A2). Phương án được giữ (đã bác bỏ đề xuất dùng một profile cố định cho cả process): tên profile mới mỗi phiên `incognito-<ts>`, xoá dữ liệu khi phiên kết thúc, purge profile cũ lúc khởi động.
<!-- Red Team: Phiên incognito bị dùng lại trong 10 s Undo — 2026-10-21 -->
- Kết thúc phiên incognito không được phụ thuộc vào snackbar Undo. Khi tab incognito cuối cùng bị đóng mềm, phiên vào trạng thái `Ending`. Mở tab incognito mới trong lúc `Ending` sẽ bắt đầu một phiên **mới** (tên profile mới) và finalize ngay token đóng đang chờ của phiên cũ (Undo của nó trở nên vô hiệu).
<!-- Red Team: Dữ liệu incognito chạm đĩa và kho hệ thống — 2026-10-21 -->
- Sửa một tuyên bố sai trước đây: Skim không tự ghi state, thumbnail hay history của tab incognito ra đĩa, nhưng **thư mục profile `incognito-<ts>` của WebView có nằm trên đĩa** (cookie, storage, cache). Vì vậy `end()` phải xoá cookie, web storage, geolocation, gọi `clearCache(true)` trên một WebView của profile đó, và xoá `cacheDir/shared/incognito/`; các thư mục profile cũ được purge ở lần khởi động sau. Tải xuống từ tab incognito chạy trong process (OkHttp + cookie của profile) rồi ghi MediaStore, không giao cookie cho DownloadManager.
<!-- Red Team: Khôi phục Reader/06 không có WebView — 2026-10-21 -->
- WebView chỉ được tạo khi `WebViewHost` vào composition, nhưng Reader và 06 khôi phục sau process death cần text trang khi tab không hiển thị. `TabManager.awaitPageReady(tabId, timeout)` bảo đảm có WebView (pool tạo trong một host offscreen gắn vào cửa sổ Activity nếu tab không hiển thị) và chờ `PageFinished` của URL hiện tại trước khi trích.
<!-- Red Team: Threading/atomicity của restore và state file — 2026-10-21 -->
- `ProfileStore` là `@UiThread`; `restore()` chạy phần Room trên `Dispatchers.IO`, phần profile trên `Dispatchers.Main` sau frame đầu tiên; có timeout. State file ghi bằng `AtomicFile` với header (version WebView, `SDK_INT`, CRC32); mọi lỗi đọc/unmarshall/`restoreState` → xoá file + `loadUrl`.
<!-- Red Team: UXSS qua highlightBlock — 2026-10-21 -->
- `highlightBlock` không bao giờ nội suy chuỗi vào JS: tham số mã hoá bằng `Json.encodeToString`, `blockId` khớp `^\d+$` và lấy từ danh sách block đã lưu của phiên chat, URL đã commit và nonce theo lần tải phải khớp phiên; dùng `JavaScriptExecutionWorld` khi WebView hỗ trợ.
<!-- Red Team: Vòng lặp renderer crash và reload mạng — 2026-10-21 -->
- Tự phục hồi phải có giới hạn: tối đa 2 lần renderer crash trong 60 s cho một tab, vượt quá thì hiện trạng thái lỗi kiểu 11 với Try again, không tự tải lại. `NoInternet` chỉ khi `ERR_INTERNET_DISCONNECTED` hoặc không có active network (không chỉ vì chưa validated); tự tải lại chỉ tab đang hiển thị, debounce.
<!-- Red Team: Trim memory phá popup đăng nhập — 2026-10-21 -->
- `UI_HIDDEN`/`BACKGROUND` chỉ lưu state; chỉ evict khi `RUNNING_CRITICAL`/`COMPLETE`, khi `isLowRamDevice` hoặc `MemoryInfo.lowMemory`. Tab có popup con đang mở không bao giờ bị evict. WebView không hiển thị gọi `onPause()`; `pauseTimers()`/`resumeTimers()` ở `onStop`/`onStart`. `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` có thể không còn được gửi tới app target API 34+ `[UNVERIFIED]`.
- Popup (`onCreateWindow`) là quyết định nhỏ của planner, không có board: giữ vì các luồng đăng nhập OAuth/thanh toán dạng popup sẽ hỏng nếu tắt `setSupportMultipleWindows`; đánh đổi là thêm nhánh tạo WebView và ghim cặp opener ↔ popup.
- `ERR_INTERNET_DISCONNECTED` và lỗi DNS cùng mã `ERROR_HOST_LOOKUP`; phân biệt bằng chuỗi `description`.
- `WebView` không implement `NestedScrollingChild`; `PullToRefreshBox` chỉ nhận cuộn khi view con báo nested scroll `TYPE_TOUCH` và refresh chạy trong `onPreFling` → `SkimWebView : NestedScrollingChild3` (bản phác của researcher B §A5).
- Màu highlight của find-in-page và của link/ảnh đang nhấn giữ do Chromium vẽ, không có API public để đổi sang `primary`/`primaryContainer` như bản vẽ 05c/05d `[UNVERIFIED]`; ghi ở "Known deviations".

## Requirements

Chức năng:
- Mỗi tab có một `SkimWebView`; tối đa 3 WebView sống (1 khi low-RAM hoặc trim mức critical/complete); tab bị đẩy khỏi LRU được lưu state rồi `destroy()`; mở lại thì khôi phục. Tab hiện tại và tab có popup con đang mở không bị evict.
- State tab thường ghi `filesDir/tabstate/<tabId>.bin` bằng `AtomicFile` (header + payload `WebViewCompat.saveState(maxSize ≈ 256 KB, includeForwardState = true)`), URL/title/nhóm/vị trí trong Room, tab hiện tại trong DataStore. State và thumbnail của tab incognito chỉ trong bộ nhớ.
- `[DEFAULT — chờ validate]` D2: sau khi process chết, tab incognito không được khôi phục; khởi động chỉ đọc tab thường.
- Incognito: profile riêng mỗi phiên; vòng đời `None → Active → Ending → None` (chi tiết ở Architecture); không hỗ trợ `MULTI_PROFILE` → `IncognitoSession.isSupported = false` (UI phase 5/6/7 hiện lời nhắc), tuyệt đối không rơi về profile mặc định. Không có `HTTP_CACHE_MANAGER` vẫn hỗ trợ incognito, nhưng `end()` bắt buộc `clearCache(true)`.
- Lịch sử: chỉ tab thường, chỉ URL http/https, ghi ở `doUpdateVisitedHistory`, cập nhật title ở `onReceivedTitle`.
- Sự kiện trang: `PageStarted(tabId, url, loadNonce)`, `Navigated(tabId, url, isSameDocument)` (same-document khi không có `onPageStarted` nào giữa hai lần `doUpdateVisitedHistory`), `PageFinished(tabId, url, loadNonce)`.
- `awaitPageReady(tabId, timeout = 15 s)` và `extractPage(tabId)` trả `PageContent` kèm `url` đã commit và `loadNonce` lúc trích.
- Lỗi tải main frame → `LoadError.{Unreachable, NoInternet, TimedOut, Crashed}` kèm mã `ERR_*`; `NoInternet` chỉ với `ERR_INTERNET_DISCONNECTED` hoặc `activeNetwork == null`; khi có mạng trở lại (cạnh lên, debounce 2 s) chỉ tự `reload()` tab đang hiển thị ở `NoInternet`; tab khác tải lại khi được hiển thị.
- `shouldOverrideUrlLoading`: http/https cho WebView xử lý; chặn `file:`, `content:`, `javascript:`; scheme khác chỉ mở khi có gesture, intent được làm sạch (BROWSABLE, bỏ component/selector/cờ grant), `browser_fallback_url` chỉ http/https.
<!-- Updated: Validation Session 1 - D5 đã chốt "Đủ cả ba loại" + gia cố -->
- D5 (đã chốt — Validation Session 1: đủ http/https, `data:`, `blob:`): tab thường http/https → `DownloadManager` (cookie của profile mặc định + User-Agent, thông báo hệ thống); tab incognito http/https → OkHttp trong process với cookie của profile incognito → MediaStore Downloads; `data:` giải mã (trần 25 MB) → MediaStore; `blob:` lấy trong trang qua `WebViewCompat.addWebMessageListener` cài riêng cho origin của blob khi có download đang chờ rồi gỡ ngay (`removeWebMessageListener`), trần 25 MB → MediaStore.
<!-- Red Team: Gia cố tải xuống — 2026-10-21 -->
- Gia cố tải xuống: tên tệp chỉ lấy basename, bỏ ký tự điều khiển, bidi và zero-width, phần mở rộng lấy từ MIME đã sniff (byte đầu) hoặc MIME của server qua `MimeTypeMap`; download không có thao tác người dùng gần đây (5 s trên WebView đó) tối đa 1 lần mỗi lần tải trang, thêm nữa bị bỏ và báo `snackbar_download_failed`; mở file đã tải dùng MIME do MediaStore/DownloadManager trả.
- File chooser (`onShowFileChooser` → `FileChooserParams.createIntent()`; luôn trả `null` khi huỷ), video toàn màn hình (`onShowCustomView` 2 tham số, ẩn system bars, Back thoát).
- Quyền site: geolocation, camera/mic đi qua `SitePermissionBridge` (dialog Skim ở phase 7, rồi runtime permission). Quyết định tab thường lưu `SitePermissionRepository`; quyết định tab incognito lưu trong `IncognitoSession` và mất khi phiên kết thúc.
- Find in page, nhấn giữ link/ảnh (`HitTestResult` + `requestFocusNodeHref`), desktop site theo tab, popup (`onCreateWindow` chỉ khi có gesture; popup thành tab foreground; ghim với opener), thumbnail, `onRenderProcessGone` (trả `true`, khôi phục trong ngân sách), Safe Browsing mặc định bật, SSL lỗi giữ mặc định `cancel()`.
- Script trang: Readability.js 0.6.0 + Readability-readerable.js làm asset; extractor trả block lá có `data-skim-i`, bỏ phần tử ẩn (`display:none`, `visibility:hidden`, `aria-hidden="true"`, `hidden`, kích thước 0; dùng `checkVisibility()` khi có) và xoá ký tự zero-width/bidi khỏi text; highlight theo block id đã lưu (dự phòng tìm theo `cited_text`); kiểm `isProbablyReaderable` sau mỗi lần tải xong (script nhỏ, không chạy Readability đầy đủ).

Phi chức năng:
- Mọi thao tác WebView/`ProfileStore` trên main thread; I/O file trên `Dispatchers.IO`, đọc state file xong mới gọi `restoreState`.
- Không `addJavascriptInterface`.
- Khởi động không bao giờ treo màn trống: `restore()` có timeout 5 s; quá hạn hoặc lỗi thì mở tab New tab và log.

## Architecture

```
browser/Tab.kt                Tab, TabGroup, TabRuntime, LoadError, FindState, HitTarget(@Serializable), PageEvent, CloseToken, GroupLayout
browser/TabEngine.kt          interface TabEngine (lệnh tới WebView; WebViewPool implement; fake trong test)
browser/TabManager.kt         process-scoped: StateFlow<TabsState>, runtime(tabId), pageEvents, lệnh
browser/WebViewPool.kt        Activity-scoped: LRU SkimWebView, host offscreen, ghim popup, implement TabEngine
browser/BrowserHost.kt        data class BrowserHost + LocalBrowserHost (composition local duy nhất cho đối tượng Activity-scoped)
browser/SkimWebView.kt        WebView + NestedScrollingChild3 + nhấn giữ + mốc thao tác gần nhất
browser/SkimWebViewClient.kt  điều hướng, lỗi, history, nonce theo lần tải, renderer gone
browser/SkimChromeClient.kt   progress, title, quyền, file chooser, fullscreen, cửa sổ mới
browser/TabStateStore.kt      AtomicFile + header (thường) / bộ nhớ (incognito)
browser/IncognitoSession.kt   ProfileStore, vòng đời phiên, quyết định quyền theo phiên
browser/WindowSecureController.kt  FLAG_SECURE + setRecentsScreenshotEnabled (API 33+)
browser/LoadErrors.kt         loadErrorOf(errorCode, description, url, hasActiveNetwork): LoadError (thuần)
browser/NetworkMonitor.kt     hasActiveNetwork, validated: StateFlow<Boolean>
browser/RendererCrashBudget.kt  allow(tabId, now): Boolean (thuần; 2 lần / 60 s)
browser/ExternalIntents.kt    externalNavigation(uri, hasGesture): ExternalNav (thuần)
browser/DesktopUserAgent.kt   desktopUserAgent(defaultUa): String (thuần) + applyDesktopMode(webView, on)
browser/ThumbnailStore.kt     capture(tab, webView) (nội bộ), observe(tabId): StateFlow<ImageBitmap?>, delete, clearAll
browser/ActivityHost.kt       interface: chooseFiles, requestPermissions, startExternal, showCustomView/hideCustomView
browser/FullscreenController.kt
browser/downloads/DownloadRouter.kt, DownloadTracker.kt, InProcessDownloader.kt, MediaStoreDownloads.kt, DataUrls.kt, BlobBridge.kt, DownloadNames.kt
browser/permissions/SitePermissionBridge.kt
browser/page/PageScripts.kt, PageContent.kt, PageContentExtractor.kt, HighlightRequest.kt
browser/ui/WebViewHost.kt     composable WebViewHost(tabId)
```

Mô hình chính:

```kotlin
data class Tab(val id: String, val incognito: Boolean, val url: String?, val title: String, val groupId: Long?, val position: Int,
    val desktopSite: Boolean, val openedByExternalApp: Boolean, val closeOnBack: Boolean, val fromNewTabPage: Boolean,
    val chatId: Long?, val openerTabId: String?, val lastAccessedAt: Long)
data class TabRuntime(val progress: Float? = null, val canGoBack: Boolean = false, val canGoForward: Boolean = false,
    val isSecure: Boolean = false, val loadError: LoadError? = null, val readerable: Boolean = false,
    val find: FindState? = null, val pageLoaded: Boolean = false, val committedUrl: String? = null, val loadNonce: Long = 0)
sealed interface LoadError { val url: String; val codeName: String
    data class Unreachable(...) : LoadError; data class NoInternet(...) : LoadError; data class TimedOut(...) : LoadError
    data class Crashed(...) : LoadError }                       // vượt ngân sách renderer crash
@Serializable sealed interface HitTarget {
    @Serializable data class Link(val url: String, val text: String?) : HitTarget
    @Serializable data class Image(val src: String, val alt: String?, val linkUrl: String?) : HitTarget }
sealed interface PageEvent { val tabId: String
    data class PageStarted(override val tabId: String, val url: String, val loadNonce: Long) : PageEvent
    data class Navigated(override val tabId: String, val url: String, val isSameDocument: Boolean) : PageEvent
    data class PageFinished(override val tabId: String, val url: String, val loadNonce: Long) : PageEvent }
@Serializable data class PageContent(val url: String, val loadNonce: Long, val title: String, val byline: String?, val siteName: String?,
    val lang: String?, val readerable: Boolean, val blocks: List<PageBlock>)
data class HighlightRequest(val expectedUrl: String, val expectedNonce: Long, val blockId: String, val citedText: String?)
```

API `TabManager` (dùng bởi phase 5–12):

```kotlin
class TabManager(tabRepo: TabRepository, history: HistoryRepository, settings: SettingsRepository, incognito: IncognitoSession,
                 stateStore: TabStateStore, thumbnails: ThumbnailStore, network: NetworkMonitor, appScope: CoroutineScope) {
    val state: StateFlow<TabsState>                     // tabs (thường + incognito, theo thứ tự), groups, currentTabId, layoutVersion
    fun runtime(tabId: String): StateFlow<TabRuntime>
    val pageEvents: SharedFlow<PageEvent>
    val hitTargets: SharedFlow<Pair<String, HitTarget>> // nhấn giữ → phase 7 mở 05d
    suspend fun restore(): RestoreResult                // Room trên IO; timeout 5 s; lỗi → New tab
    fun newTab(incognito: Boolean, url: String? = null, background: Boolean = false, openedByExternalApp: Boolean = false,
               closeOnBack: Boolean = false, openerTabId: String? = null): String
    fun select(tabId: String); fun load(tabId: String, url: String)   // load trên tab New tab → fromNewTabPage = true
    fun returnToNewTabPage(tabId: String)               // url = null, fromNewTabPage = false, destroy WebView và xoá state file
    fun goBack(tabId: String): Boolean; fun goForward(tabId: String)
    fun reload(tabId: String); fun setDesktopSite(tabId: String, on: Boolean)
    fun find(tabId: String, query: String); fun findNext(tabId: String, forward: Boolean); fun clearFind(tabId: String)
    fun close(tabIds: List<String>): CloseToken; fun undoClose(token: CloseToken); fun finalizeClose(token: CloseToken)
    fun finalizePendingCloses()                         // Activity isFinishing: hoàn tất mọi token đang chờ Undo
    fun pendingCloseIds(): Set<String>                  // tab đang chờ Undo (phase 12 loại khỏi gom nhóm)
    fun applyLayout(layout: GroupLayout, expectedVersion: Long? = null, snapshot: TabGroupingSnapshotEntity? = null): Boolean
        // đường ghi layout duy nhất; sai version → false; layout + snapshot ghi một transaction (TabRepository.saveLayout)
    fun bindChat(tabId: String, chatId: Long?)
    suspend fun awaitPageReady(tabId: String, timeoutMs: Long = 15_000): Boolean
    suspend fun extractPage(tabId: String): PageContent?            // gọi awaitPageReady trước
    suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean   // false khi URL/nonce không khớp
    fun attach(engine: TabEngine); fun detach()
}
```

<!-- Red Team: Vòng đời phiên incognito — 2026-10-21 -->
`IncognitoSession` (process-scoped, mọi hàm `@MainThread`):

```kotlin
class IncognitoSession(appScope: CoroutineScope, sharedDir: File) {
    val isSupported: Boolean                           // WebViewFeature.MULTI_PROFILE
    val hasHttpCacheManager: Boolean                   // WebViewFeature.HTTP_CACHE_MANAGER
    sealed interface State { data object None : State; data class Active(val profile: String) : State
        data class Ending(val profile: String, val pendingToken: CloseToken) : State }
    val state: StateFlow<State>
    fun profileForNewTab(finalizePending: (CloseToken) -> Unit): String   // None → mới; Ending → finalize token cũ + end() + phiên mới
    fun markEnding(token: CloseToken); fun cancelEnding()                 // đóng mềm tab cuối / Undo
    fun end(engine: TabEngine?)        // clearCache(true) qua WebView tạm của profile (nếu có engine), cookie, webStorage, geolocation, quyền phiên, shared/incognito/
    fun purgeStale()                   // gọi trên Main sau frame đầu: deleteProfile mọi incognito-* (không đang dùng) + xoá shared/incognito/
    fun decision(host: String, kind: SitePermissionKind): PermissionState; fun setDecision(host: String, kind: SitePermissionKind, s: PermissionState)
}
```

- Khi phiên bắt đầu: `getOrCreateProfile(name)`; nếu có `HTTP_CACHE_MANAGER` thì `httpCache.setQuotaBytes(0)`.
- `end()`: nếu có `TabEngine` (Activity còn sống) thì pool tạo một WebView tạm, `setProfile(name)` trước tiên, `clearCache(true)`, destroy; rồi `cookieManager.removeAllCookies` + `flush()`, `webStorage.deleteAllData()` (kỳ vọng gồm dữ liệu service worker `[UNVERIFIED: deleteAllData có xoá đăng ký service worker không; kiểm trong IncognitoProfileTest]`), `geolocationPermissions.clearAll()`, xoá quyết định quyền của phiên, xoá `cacheDir/shared/incognito/`. Không có Activity → để `purgeStale()` của lần khởi động sau xử lý.

<!-- Red Team: BrowserHost — composition local duy nhất — 2026-10-21 -->
`BrowserHost`:

```kotlin
data class BrowserHost(val pool: WebViewPool, val sitePermissions: SitePermissionBridge, val downloads: DownloadRouter,
                       val blob: BlobBridge, val activityHost: ActivityHost, val fullscreen: FullscreenController,
                       val secure: WindowSecureController)
val LocalBrowserHost = staticCompositionLocalOf<BrowserHost> { error("BrowserHost not provided") }
```

Luồng dữ liệu:
1. Khởi động: `SkimApplication` → `appScope.launch { tabManager.restore() }`: `withContext(Dispatchers.IO)` đọc Room (`TabRepository.loadOpen`) + `lastTabId`, purge state/thumbnail mồ côi; kết quả đưa về `Main`. `IncognitoSession.purgeStale()` do `MainActivity` gọi trên Main sau frame đầu (`window.decorView.post`). Timeout 5 s → `RestoreResult.Fallback` (tab New tab).
2. Hiển thị tab: `WebViewHost(tabId)` → `pool.obtain(tab)`: (incognito: `setProfile(incognito.profileForNewTab(...))` trước tiên) → `configure()` → đọc state file trên IO → `restoreState` trong `try/catch(Throwable)`; lỗi hoặc `null` → xoá file + `loadUrl(url)` → đánh dấu MRU, evict LRU (lưu state → destroy), trừ tab hiện tại và tab có popup con. WebView không hiển thị gọi `onPause()`, WebView được hiển thị gọi `onResume()`.
3. `awaitPageReady(tabId)`: nếu WebView của tab không có trong pool thì `pool.obtainOffscreen(tab)` gắn WebView vào một `FrameLayout` offscreen trong cây view của Activity (kích thước màn hình, `translationX` ra ngoài, không focus, `importantForAccessibility = no`); chờ `PageFinished` có `loadNonce` của lần tải hiện tại; hết timeout → `false`.
4. Sự kiện WebView → client → `TabManager` cập nhật `TabRuntime` (progress, back/forward, lỗi, readerable, find, `committedUrl`, `loadNonce`) → UI collect. `onPageStarted` → nonce mới + `PageStarted`. `doUpdateVisitedHistory` → `HistoryRepository.recordVisit` (chỉ tab thường) + `Navigated(isSameDocument)`. `onPageFinished`/progress 100 → readerable check, thumbnail (debounce 500 ms), lưu state (debounce 1 s, tab thường), `PageFinished`.
5. Lệnh từ UI → `TabManager` → `TabEngine` (pool) nếu attach; khi chưa attach, lệnh `load`/`reload` chỉ cập nhật `tab.url`/cờ tải lại để lần hiển thị sau xử lý.
6. Đóng tab: `close` = ẩn khỏi danh sách, lưu state (đĩa hoặc bộ nhớ), destroy WebView, Room `closedAt`; nếu tab hiện tại bị đóng thì chọn tab cùng chế độ truy cập gần nhất, không còn thì `currentTabId = null` (`BrowserRoute` tạo tab New tab). Nếu không còn tab incognito nào mở → `incognito.markEnding(token)`. `undoClose` khôi phục vị trí và nhóm (kiểm lại `groupId` với nhóm hiện có; nhóm không còn → Other) và `incognito.cancelEnding()`; token đã finalize thì bỏ qua. `finalizeClose` xoá Room/state/thumbnail; nếu phiên đang `Ending` với token này → `incognito.end(engine)`.
7. Activity `onStart` → `resumeTimers()`; `onStop` → `pool.saveAll()` + `pauseTimers()`; `onDestroy` → `saveAll()` + `destroyAll()` + `tabManager.detach()`; `isFinishing` → `finalizePendingCloses()`.

## Related code files

Tạo (đều dưới `app/src/main/java/com/dantech/skimbrowser/browser/`):
- `Tab.kt`, `TabEngine.kt`, `TabManager.kt`, `WebViewPool.kt`, `BrowserHost.kt`, `SkimWebView.kt`, `SkimWebViewClient.kt`, `SkimChromeClient.kt`, `TabStateStore.kt`, `IncognitoSession.kt`, `WindowSecureController.kt`, `LoadErrors.kt`, `NetworkMonitor.kt`, `RendererCrashBudget.kt`, `ExternalIntents.kt`, `DesktopUserAgent.kt`, `ThumbnailStore.kt`, `ActivityHost.kt`, `FullscreenController.kt`
- `downloads/DownloadRouter.kt`, `downloads/DownloadTracker.kt`, `downloads/InProcessDownloader.kt`, `downloads/MediaStoreDownloads.kt`, `downloads/DataUrls.kt`, `downloads/BlobBridge.kt`, `downloads/DownloadNames.kt`
- `permissions/SitePermissionBridge.kt`
- `page/PageScripts.kt`, `page/PageContent.kt`, `page/PageContentExtractor.kt`, `page/HighlightRequest.kt`
- `ui/WebViewHost.kt`

Asset:
- `app/src/main/assets/js/readability/Readability.js`, `app/src/main/assets/js/readability/Readability-readerable.js` (tarball npm `@mozilla/readability` 0.6.0, kiểm `dist.integrity`), `app/src/main/assets/js/readability/LICENSE.md` (Apache-2.0)
- `app/src/main/assets/js/skim-extract.js` (extractor researcher B §A8 + `byline`, `siteName`, bỏ phần tử ẩn, xoá zero-width/bidi; giới hạn 400 block / 100k ký tự), `app/src/main/assets/js/skim-highlight.js` (nhận một đối tượng JSON `{id, cited}`), `app/src/main/assets/js/skim-readerable.js`, `app/src/main/assets/js/skim-blob-fetch.js`

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `httpClient: OkHttpClient` (dùng chung), `networkMonitor`, `incognitoSession`, `tabStateStore`, `thumbnails`, `downloadTracker`, `tabManager` (không có đối tượng Activity-scoped).
- `app/src/main/java/com/dantech/skimbrowser/SkimApplication.kt`: `appScope.launch { container.tabManager.restore() }`.
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt`: implement `ActivityHost` (ActivityResult launcher cho file chooser, runtime permission, `startActivity` an toàn); tạo `WebViewPool`, `WindowSecureController`, `FullscreenController`, `SitePermissionBridge`, `DownloadRouter`, `BlobBridge` và gom thành `BrowserHost`; `attach/detach`; `purgeStale()` sau frame đầu; `resumeTimers()/pauseTimers()`; đăng ký receiver `ACTION_DOWNLOAD_COMPLETE` ở `onStart`/`onStop` với `ContextCompat.registerReceiver(..., "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS", null, RECEIVER_EXPORTED)`; `onTrimMemory` chuyển cho pool; cung cấp `LocalBrowserHost` khi `setContent`.

Test:
- `app/src/test/java/com/dantech/skimbrowser/browser/LoadErrorsTest.kt`, `ExternalIntentsTest.kt` (Robolectric), `DesktopUserAgentTest.kt`, `RendererCrashBudgetTest.kt`, `TabManagerTest.kt` (với `FakeTabEngine`), `TabStateStoreTest.kt` (Robolectric: header sai version/CRC, file bị cắt, Parcel hỏng → xoá file + `null`, không ném; incognito không tạo file), `ThumbnailStoreTest.kt`, `IncognitoSessionTest.kt` (vòng đời với `ProfileStore` giả qua interface mỏng)
- `app/src/test/java/com/dantech/skimbrowser/browser/downloads/DataUrlsTest.kt`, `DownloadNamesTest.kt` (basename, `../`, RTL override `U+202E`, zero-width, phần mở rộng theo MIME)
- `app/src/test/java/com/dantech/skimbrowser/browser/page/PageContentParsingTest.kt` (JSON mẫu → `PageContent`, cắt giới hạn), `HighlightRequestTest.kt` (id `^\d+$`, chuỗi chứa `` ` ``, `${`, `'`, `"]`, `</script>` được mã hoá JSON, không thể thoát khỏi chuỗi)
- `app/src/test/java/com/dantech/skimbrowser/browser/FakeTabEngine.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/SkimWebViewEngineTest.kt` (MockWebServer: extractor bỏ phần tử ẩn, highlight đúng/sai nonce, readerable, find, save/restore, desktop UA, khôi phục sau `WebViewCompat.getWebViewRenderProcess(webView)?.terminate()` và hết ngân sách → `Crashed`, `awaitPageReady` cho tab không hiển thị)
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/IncognitoProfileTest.kt` (cookie tách profile; đóng tab cuối rồi mở tab mới trong lúc `Ending` → tên profile khác; sau `end()` không còn cookie/storage và thư mục cache của profile rỗng; `assumeTrue(MULTI_PROFILE)`)
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/IncognitoUnavailableTest.kt` (chỉ chạy khi `!MULTI_PROFILE`: `isSupported == false`, không tạo tab incognito)
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/PopupAndTrimTest.kt` (mở popup → `am send-trim-memory … BACKGROUND` → opener còn sống)
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/PullToRefreshNestedScrollTest.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/DeviceCapabilitiesReportTest.kt` (ghi `MULTI_PROFILE`, `HTTP_CACHE_MANAGER`, version WebView qua `Instrumentation.sendStatus` và log tag `SkimGate`)
- `app/src/androidTest/assets/article.html` (trang bài viết cố định; có đoạn ẩn bằng `display:none` và `aria-hidden` để kiểm extractor)

## Implementation steps

1. Mô hình (`Tab.kt`), `TabEngine`, `BrowserHost`:
   ```kotlin
   interface TabEngine { fun load(tabId: String, url: String); fun goBack(tabId: String): Boolean; fun goForward(tabId: String); fun reload(tabId: String)
       fun setDesktop(tabId: String, on: Boolean); fun find(tabId: String, q: String); fun findNext(tabId: String, forward: Boolean); fun clearFind(tabId: String)
       fun saveAndDestroy(tabId: String); fun clearHistory(tabId: String); fun ensureWebView(tabId: String, offscreen: Boolean); suspend fun evaluate(tabId: String, script: String): String?
       fun withTemporaryWebView(profile: String?, block: (WebView) -> Unit) }
   ```
2. `TabStateStore`: ghi bằng `AtomicFile(startWrite/finishWrite)`: header `magic`, `formatVersion`, WebView `versionName` (`WebViewCompat.getCurrentWebViewPackage`), `SDK_INT`, CRC32 của payload, rồi payload `Parcel.marshall()` của Bundle lấy từ `WebViewCompat.saveState(webView, bundle, 256 * 1024, true)` (feature `SAVE_STATE`, ngược lại `webView.saveState`). `load(tabId)` chạy trên IO, kiểm header/CRC; lệch hoặc lỗi → xoá file, trả `null`. Incognito → `Map` trong bộ nhớ. `purgeExcept(ids)`.
3. `IncognitoSession` như Architecture; `profileForNewTab` khi `Ending` gọi `finalizePending(token)` (TabManager finalize token đó, Undo vô hiệu) rồi `end()` và bắt đầu tên mới.
4. `SkimWebView`: nested scroll theo researcher B §A5; chỉ bắt đầu kéo khi đã ở đỉnh và đã nhận `onOverScrolled(clampedY = true)`; ghi `lastUserGestureAt` ở `ACTION_DOWN`; `setOnLongClickListener` đọc `hitTestResult` (`SRC_ANCHOR_TYPE` → `Link`; `IMAGE_TYPE` → `Image`; `SRC_IMAGE_ANCHOR_TYPE` → `Image(src, alt = null, linkUrl từ requestFocusNodeHref)`), trả `true` chỉ cho ba loại này.
5. `SkimWebViewClient`: `onPageStarted` → nonce mới; `onReceivedError` chỉ main frame, `loadErrorOf(error.errorCode, error.description, url, network.hasActiveNetwork)`; `onRenderProcessGone` → `pool.onRendererGone(tabId, detail.didCrash())`: destroy WebView; `RendererCrashBudget.allow(tabId)` → khôi phục từ state khi tab hiển thị lại; không → `LoadError.Crashed`, không tự tải; `!didCrash()` (OOM) → capacity 1; trả `true`. Không override `onReceivedSslError`.
6. `SkimChromeClient`: `onProgressChanged`, `onReceivedTitle`, quyền → `SitePermissionBridge`, `onShowFileChooser` → `ActivityHost.chooseFiles`, `onShowCustomView`/`onHideCustomView` → `FullscreenController`, `onCreateWindow(view, isDialog, isUserGesture, msg)`: `isUserGesture == false` → `false`; ngược lại `tabManager.newTab(incognito = parent.incognito, openerTabId = parent.id)`, `pool.obtainForPopup(tab)` (setProfile trước nếu incognito, không load), gán `WebViewTransport`, `sendToTarget()`, chọn tab mới, ghim opener; `onCloseWindow` → đóng tab popup (không Undo), bỏ ghim.
7. `WebViewPool`: `LinkedHashMap<String, SkimWebView>(16, .75f, true)`; `configure()` đặt `javaScriptEnabled = true`, `domStorageEnabled = true`, `setSupportMultipleWindows(true)`, `builtInZoomControls = true`, `displayZoomControls = false`, `allowFileAccess = false`, `allowContentAccess = false`, `setGeolocationEnabled(true)`, `setDownloadListener` → `DownloadRouter`, `setFindListener` → runtime; capacity 3 (1 khi `isLowRamDevice`); `onTrimMemory(level)`: `UI_HIDDEN`/`BACKGROUND` → `saveAll()` (không evict); `RUNNING_CRITICAL`/`COMPLETE` hoặc `ActivityManager.MemoryInfo.lowMemory` → capacity 1 và evict (trừ tab hiện tại và tab ghim); `onStart` (không low-RAM) trả về 3. Host offscreen cho `obtainOffscreen`.
8. `TabManager`: state trong `MutableStateFlow` (+ `layoutVersion` tăng mỗi `applyLayout`), ghi Room qua `TabRepository` trên `Dispatchers.IO.limitedParallelism(1)`; `lastTabId` chỉ cho tab thường; `restore()` như Luồng dữ liệu 1; theo dõi `network`: cạnh lên của mạng (debounce 2 s) → `reload` tab hiện tại nếu đang `NoInternet`, đánh dấu các tab khác để tải lại khi hiển thị. `awaitPageReady`/`extractPage`/`highlightBlock` như Architecture; `highlightBlock` so `runtime.committedUrl` và `loadNonce` với request trước khi `evaluate`.
9. `LoadErrors.kt`, `ExternalIntents.kt`, `DesktopUserAgent.kt`, `RendererCrashBudget.kt` là hàm thuần; `MainActivity.startExternal` bắt `ActivityNotFoundException` rồi tải `fallbackUrl` nếu http/https.
10. Tải xuống: `DownloadRouter.start(tab, url, ua, contentDisposition, mime, webView)` → kiểm gesture gần đây (rate limit) → nhánh theo bảng D5. `DownloadNames.sanitize(raw, mime)`; `InProcessDownloader(httpClient)` (OkHttp `AppContainer.httpClient` tạo ở phase này, timeout 15 s; phase 6, 7 dùng lại) ghi stream thẳng vào `MediaStoreDownloads` (API 29+: `MediaStore.Downloads` + `IS_PENDING`; 26–28: thư mục Downloads công khai với `WRITE_EXTERNAL_STORAGE` xin qua `ActivityHost`); `DownloadTracker` phát `DownloadCompleted(fileName, uri, mime)`/`DownloadFailed(fileName)` (mime lấy từ MediaStore/DownloadManager); `BlobBridge.fetch(webView, blobUrl)`: `addWebMessageListener(webView, "skimBlob", setOf(originOf(blobUrl)))` → chạy `skim-blob-fetch.js` với nonce (tham số mã hoá JSON) → nhận message đúng nonce và `sourceOrigin` → `removeWebMessageListener(webView, "skimBlob")` trong `finally`; không có `WEB_MESSAGE_LISTENER`, hoặc object không xuất hiện trong document hiện tại `[UNVERIFIED: listener chỉ tiêm vào document tạo sau khi đăng ký]` → `DownloadFailed`.
11. `SitePermissionBridge` (Activity-scoped, chỉ giữ request đang chờ + callback WebView): `pending: StateFlow<PendingSitePermission?>`; quyết định đọc/ghi qua `SitePermissionRepository` (tab thường) hoặc `IncognitoSession.decision/setDecision` (tab incognito); `ALLOWED` → runtime permission nếu thiếu → grant; `BLOCKED` → deny; `UNSET` → chờ UI; Activity bị huỷ → deny mọi request đang chờ.
12. `ThumbnailStore` theo researcher B §A6 (scale 0.35, crop phần trên theo khung preview của tab card: rộng (412 − 2·16 − 12)/2 − 2·8 = 168dp, cao 92dp theo scout §2 09; WebP 80 vào `cacheDir/thumbs/`; incognito `LruCache`); chụp khi progress 100 (debounce 500 ms) và trong `onRelease` của `WebViewHost`. Không có API chụp công khai trên `TabManager`.
13. Script trang: tải tarball npm 0.6.0, kiểm SHA-512 của `dist.integrity`, copy hai file JS + LICENSE; viết bốn script Skim; `PageScripts` đọc asset một lần; `PageContentExtractor.extract()` inject Readability + extractor bằng `evaluateJavascript` (dùng `JavaScriptExecutionWorld` khi WebView hỗ trợ `[UNVERIFIED: tên feature trong webkit 1.17.1]`), parse JSON thành `PageContent` kèm `committedUrl` và `loadNonce`; `skim-highlight.js` nhận đúng một đối số là chuỗi JSON từ `Json.encodeToString(HighlightArgs(id, cited))`.
14. `WebViewHost`: `key(tabId) { AndroidView(factory = { host.pool.obtain(tab).also { (it.parent as? ViewGroup)?.removeView(it) } }, onRelease = { thumbnails.capture(tab, it); it.onPause(); (it.parent as? ViewGroup)?.removeView(it) }) }`.
15. `MainActivity`, `AppContainer`, `SkimApplication` như phần Sửa.
16. Test đơn vị: `TabManagerTest` khẳng định tab incognito không gọi `HistoryRepository` và không ghi Room; `restore()` bỏ qua incognito (D2) và trả `Fallback` khi repository treo quá 5 s; `close`/`undoClose`/`finalizeClose` đúng thứ tự; đóng tab incognito cuối → `Ending`; mở tab incognito mới khi `Ending` → profile khác, token cũ đã finalize, `undoClose(token cũ)` không làm gì; `undoClose` với nhóm đã bị xoá → tab về Other; `applyLayout(expectedVersion sai)` → `false`.
17. Cổng thiết bị (quy trình chung ở `plan.md`, mục "Cổng thiết bị"): chạy `DeviceCapabilitiesReportTest` + `connectedDebugAndroidTest` cho package `browser` trên `skim_api36` và `skim_api28`.
18. Commit cuối phase: `feat(browser): add tab manager, webview pool and incognito profiles`.

## Todo list

- [x] Mô hình tab, TabEngine, BrowserHost/LocalBrowserHost
- [x] TabManager (state, lệnh, layoutVersion, persistence, restore IO/Main + timeout, D2, awaitPageReady, extractPage có URL/nonce, highlight có kiểm)
- [x] WebViewPool LRU 3/1, host offscreen, ghim popup, onPause/pauseTimers, chính sách trim mới
- [x] SkimWebView nested scroll + nhấn giữ + mốc gesture
- [x] WebViewClient (điều hướng an toàn, lỗi mới, history, nonce, ngân sách crash) và ChromeClient (progress, title, quyền, file chooser, fullscreen, popup)
- [x] TabStateStore (AtomicFile + header + phục hồi lỗi; incognito trong bộ nhớ)
- [x] IncognitoSession (Active/Ending, phiên mới khi Ending, end() xoá cache/cookie/storage/quyền/shared, purgeStale trên Main)
- [x] WindowSecureController (FLAG_SECURE + Recents API 33+), FullscreenController, ActivityHost trong MainActivity
- [x] LoadErrors + NetworkMonitor + tự reload tab hiển thị có debounce
- [x] ExternalIntents + DesktopUserAgent + RendererCrashBudget
- [x] Tải xuống D5 đã gia cố (DownloadManager cho tab thường, OkHttp cho incognito, data:, blob: đọc từ trang bằng evaluateJavascript theo nonce và origin rồi xoá trạng thái trong trang, tên tệp an toàn, rate limit) + DownloadTracker
- [x] SitePermissionBridge (chỉ request đang chờ) + quyết định incognito trong IncognitoSession
- [x] ThumbnailStore
- [x] Asset Readability 0.6.0 + 4 script Skim (bỏ phần tử ẩn, args JSON) + PageContentExtractor
- [x] WebViewHost
- [x] Test đơn vị + instrumented
- [x] Cổng thiết bị `skim_api36` + `skim_api28` (báo cáo MULTI_PROFILE/HTTP_CACHE_MANAGER, số test incognito đã chạy)
- [x] Commit cuối phase

## Success criteria

- Rule "Process death" (phần tabs, scroll position) và "Incognito" (profile riêng, xoá khi phiên kết thúc, không history) đạt: `TabManagerTest`, `TabStateStoreTest`, `IncognitoSessionTest` xanh; `IncognitoProfileTest` **chạy thật** (không skip) trên `skim_api36` và xanh; trên `skim_api28` nếu không có `MULTI_PROFILE` thì `IncognitoUnavailableTest` chạy và xanh.
- 05 "File input, fullscreen video" và "tel:, mailto:, intent: links", 09c "Storage" và "Last incognito tab closes", system UI "Downloads" chạy trên emulator (checklist ở Verification).
- Mở 10 tab: tối đa 3 WebView sống (log tag `SkimWebViewPool`); quay lại tab cũ khôi phục đúng URL và vị trí cuộn; file state bị cắt dở không gây crash khi mở lại app.
- Extractor trên `article.html` trả ≥ 5 block, không có block từ phần tử ẩn; highlight trả `true` với nonce đúng và `false` khi đã điều hướng; readerable `true`; find đếm đúng số match.

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.browser.*'
./gradlew :app:lintDebug   # webkit lint cảnh báo khi thiếu onRenderProcessGone
# Cổng thiết bị (quy trình đầy đủ trong plan.md): trên mỗi AVD skim_api36 (cổng 5580) và skim_api28 (cổng 5582)
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.dantech.skimbrowser.browser
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.DeviceCapabilitiesReportTest
grep -h -o 'skipped="[0-9]*"' app/build/outputs/androidTest-results/connected/debug/*.xml   # incognito: skipped phải là 0 trên skim_api36
adb -s emulator-5580 shell am send-trim-memory com.dantech.skimbrowser RUNNING_CRITICAL && adb -s emulator-5580 logcat -d -s SkimWebViewPool
```

Checklist thủ công trên emulator: tải file PDF qua link http(s) ở tab thường (thông báo hệ thống) và ở tab incognito (không có thông báo DownloadManager, file xuất hiện trong Downloads); trang tự gọi 3 download liên tiếp không có thao tác → chỉ 1 file; link `tel:` mở trình quay số chỉ khi bấm; `intent:` có `browser_fallback_url` mở fallback khi không có app; `<input type=file>` mở picker và huỷ không treo trang; video toàn màn hình rồi Back.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Thiết bị hỗ trợ `MULTI_PROFILE` | `isFeatureSupported` false | Tắt incognito, hiện lời nhắc cập nhật WebView (string D8); không dùng profile mặc định |
| `deleteProfile` không xoá được profile đã load | `IllegalStateException` | Tên mới mỗi phiên, xoá dữ liệu ở `end()`, purge lúc khởi động (đã thiết kế) |
| WebView offscreen gắn vào cây view tải và chạy JS bình thường `[UNVERIFIED]` | `awaitPageReady` timeout | Khi đó Reader/06 hiện trạng thái chưa sẵn sàng và chỉ thử lại khi người dùng mở lại tab; ghi lại kết quả cổng thiết bị |
| `SkimWebView` nested scroll đúng dấu dy/velocity | Kéo không refresh, hoặc refresh nhầm khi cuộn bản đồ | Chỉ bắt đầu sau `onOverScrolled(clampedY)`; vẫn lỗi ở cổng thiết bị thì chuyển sang `SwipeRefreshLayout` bọc WebView (phương án dự phòng đã chốt) |
| Renderer OOM lặp | `onRenderProcessGone` liên tục | Ngân sách 2 lần / 60 s rồi `Crashed`; hạ capacity 1 |
| Bundle cũ không đọc được sau cập nhật WebView | Header lệch / `restoreState` null | Xoá file, `loadUrl(tab.url)` |
| Vẽ software ra trắng với video/WebGL | Thumbnail trắng | Card dùng nền `surfaceContainerLowest` khi bitmap null hoặc gần như một màu |
| `addWebMessageListener` cài sau khi tải không tới được document hiện tại `[UNVERIFIED]` | Blob download luôn lỗi | Báo `snackbar_download_failed`; ghi ở "Known deviations"/roadmap, không quay lại cách cài `"*"` cho mọi frame |
| `ACCESS_LOCAL_NETWORK` của target 37 chặn trang LAN `[UNVERIFIED]` | Trang router báo Unreachable | Ghi nhận giới hạn (không có thiết kế xin quyền); câu hỏi mở |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 4. Dữ liệu `tabstate/`, `thumbs/`, `shared/` là cache, xoá an toàn.

## Security considerations

- Không `addJavascriptInterface`; cầu nối blob chỉ tồn tại trong thời gian một download, giới hạn đúng origin của blob, có nonce và kiểm `sourceOrigin`.
- `highlightBlock` không nội suy chuỗi; id được kiểm và lấy từ dữ liệu đã lưu; URL + nonce phải khớp, tránh chạy script trên origin khác sau khi trang tự điều hướng.
- Extractor bỏ text ẩn và ký tự điều khiển để giảm bề mặt prompt injection (phase 11, 12 có thêm biện pháp ở prompt).
- Intent ra ngoài: cần gesture, thêm BROWSABLE, xoá component/selector và cờ grant URI, fallback chỉ http/https (FAQ Google 9101196).
- `allowFileAccess`/`allowContentAccess` tắt; chặn `file:`/`content:`/`javascript:` trong điều hướng.
- SSL lỗi giữ `cancel()` mặc định; Safe Browsing mặc định bật.
- Incognito: Skim không ghi state/thumbnail/history ra đĩa; thư mục profile WebView trên đĩa được xoá dữ liệu ở `end()` và purge ở lần khởi động sau; cookie incognito không bao giờ rời process (download incognito chạy trong process); file share incognito ở `cacheDir/shared/incognito/` bị xoá ở `end()` và lúc khởi động; FLAG_SECURE do phase 5 áp.
- File tải xuống từ tab incognito là file công khai trong Downloads (đúng copy 09c "Bookmarks and downloads you make here are kept").

## Next steps

- Phase 5 cung cấp `LocalBrowserHost`, gọi `WindowSecureController` và hiển thị `WebViewHost` trong `BrowserRoute`.
- Phase 7 dùng `hitTargets`, `DownloadTracker`, `LocalBrowserHost.sitePermissions`/`downloads`; phase 10/11 dùng `awaitPageReady`/`extractPage`/`highlightBlock`; phase 11 dùng `pageEvents` (D4); phase 12 dùng `applyLayout(expectedVersion)` và `pendingCloseIds()`.

## Kết quả thực thi (2026-10-22)

Commit: `feat(browser): add tab manager, webview pool and incognito profiles`. Làm theo các lượt tuần tự, mỗi lượt do một agent làm rồi controller kiểm chứng độc lập (chạy lại toàn bộ test, lint, Roborazzi, biên dịch không incremental, đột biến riêng) trước khi sang lượt sau: 4A logic thuần và state (mô hình tab, `TabManager`, `TabStateStore`, `IncognitoSession`, `ThumbnailStore`, `NetworkMonitor`, liên kết ngoài, tên tệp tải); 4B lõi WebView (`SkimWebView`, hai client, `WebViewPool`, `BrowserHost`, fullscreen, FLAG_SECURE, script trang, `WebViewHost`); 4D-1 test thiết bị của engine; 4C tải xuống, quyền site, cầu nối blob; một reviewer độc lập đọc toàn bộ bề mặt bảo mật (12 phát hiện: 0 Critical, 0 High, 8 Medium, 4 Low; đều đã sửa hoặc chuyển phase sau); 4D-2a test thiết bị cho phần 4C; 4D-2b cổng `skim_api28`, dữ liệu trên SQLite cũ, gate theo năng lực. Báo cáo từng lượt: `plans/reports/*phase-04a*` … `*phase-04d2b*` và `code-reviewer-*phase-04-security-review.md`.

Kiểm chứng cuối phase: `testDebugUnitTest` 889 test (83 lớp), 0 lỗi, 0 bỏ qua; `assembleDebug` và `verifyRoborazziDebug` xanh; `lintDebug` 0 lỗi, đúng 123 cảnh báo cũ, 0 trong code phase 4; biên dịch lại không incremental cả ba source set 0 dòng `w:`. Thiết bị (84 test): `skim_api36` (Android 16, WebView 133, SQLite 3.44) 81 qua, 3 bỏ qua theo assumption đúng thiết kế (`IncognitoUnavailableTest`, hai test chỉ có nghĩa ở WebView khác); `skim_api28` (Android 9, WebView 66, SQLite 3.22) 60 qua, 24 bỏ qua kèm lý do đọc được, trong đó `IncognitoUnavailableTest` và test dữ liệu chạy thật; không lượt nào có crash. Khoảng 320 đột biến có chủ ý (agent 234, controller 84), mỗi cái làm đúng test liên quan đỏ, nguồn khôi phục trùng md5. Script trang được chạy thật trong Chrome headless.

Lỗi thật chỉ lộ khi chạy trên WebView thật hoặc qua review (đã sửa, mỗi lỗi có test):
- Trang dùng `height: 100%` bị dàn trang cao 0 px vì `AndroidView` gắn WebView với `wrap_content`: `showInHost` đặt `MATCH_PARENT`.
- Phiên incognito kết thúc để lại đăng ký service worker (`deleteAllData` không xoá): dùng `WebStorageCompat.deleteBrowsingData` khi có `DELETE_BROWSING_DATA`.
- Find Next gửi lúc WebView còn đếm thì không chuyển match: `FindSteps` giữ bước lại tới khi đếm xong.
- Văn bản nằm thẳng trong `div` mất id vì Readability bọc nó thành `<p>` mới: extractor đánh số cả `div` chữ.
- Cầu nối blob dựa vào `addWebMessageListener` không bao giờ chạy trên trang đang hiện (đối tượng chỉ được tiêm vào các lần điều hướng sau lời gọi): chuyển sang đọc bằng `evaluateJavascript` có thăm dò, giữ nonce, kiểm origin hai phía, tham số JSON, trần kích thước trước khi đọc, đọc từng lát, xoá trạng thái trong `finally`.
- Từ review bảo mật: hai `MainActivity` chồng nhau làm `TabManager` mất engine và dừng timer JS toàn process; intent ra ngoài giữ cờ launch, nhắm được vào chính Skim và mở app khác từ tab incognito; quyền đã nhớ áp cho iframe khác site; giới hạn tải tự động bị lách bằng reload hoặc cú cuộn; tải trong process không có trần kích thước, dung lượng, thông lượng; DownloadManager gửi lại `Cookie` qua redirect; bàn phím học chữ gõ ở incognito; hộp thoại JS từ tab nền; `.apk` lọt qua MIME giả; console incognito vào logcat; `evaluate` không timeout.
- 4D-2a: `TabManager.attach()` giữ `pageLoaded` do WebView của Activity cũ báo nên `awaitPageReady` trả `true` khi pool mới còn trống.
- 4D-2b: WebView 66 giao link `<a download>` cho listener trước khi request, nên URL chưa theo redirect: cookie cho DownloadManager chỉ gắn khi listener có type của phản hồi.
- Sau 4D-2b: trên WebView dưới Chromium 80 Readability không parse được nhưng `readerable` vẫn `true`: `PageScriptSource.isSupported` (mốc Chromium 80) làm `readerable` luôn `false` và `extractPage` trả null.
- Test kéo-làm-mới chập chờn (khoảng 15% lượt khi máy bận): Chromium gửi một chuỗi chạm không-hủy-được khi renderer trả lời chậm, và khi đó không trang nào giữ được cử chỉ kéo; test lặp lại lượt kéo cho tới khi trang nhận chuỗi hủy được.

Chữ ký thực tế khác spec (đã chấp nhận; chi tiết trong các báo cáo):
- `TabManager(…, scripts: PageScriptSource, …)`; sink là `TabManager.events` (`TabEventSink` có thêm `onStateSaved`, `onWebViewDestroyed`, không có `onSecurity`/`onReaderable`: `isSecure` tính từ URL https đã commit, readerable do `TabManager` tự chạy); `attach(engine)`, `detach(engine)`, `isAttached(engine)`; `IncognitoSession(profiles, sharedDir, appScope, …)`.
- `BrowserHost(pool, sitePermissions, downloads, blob, activityHost, fullscreen, secure)` với `destroy()`; `createBrowserHost(activity, container, activityHost)` trong `MainActivity.kt` là đường dựng duy nhất (cả test dùng); `ActivityHost` hiện thực bằng `ActivityResultHost` do `MainActivity` tạo.
- `WindowSecureController.setSecure(owner, secure)` và `setRecentsHidden(owner, hidden)`.
- `SitePermissionBridge`: `pending` là yêu cầu cũ nhất của tab đang hiện; `allow(request, remember)`, `block(request)`, `dismiss(request)` nhận đúng `PendingSitePermission` đang vẽ; `pending.host` là host của trang top-level; quyết định khoá theo host top-level, không theo origin của frame.
- `SkimWebView(context, incognito)` và `consumeGesture(withinMs)`; `externalNavigation(uri, hasGesture, incognito, ownPackage)`; `DownloadRouter.start(tabId, url, userAgent, contentDisposition, mimeType, webView, userInitiated)`; `DownloadTracker.events` không replay; `BlobBridge.fetch(webView, blobUrl, maxBytes): BlobData?`.
- `AppContainer` có thêm (lazy): `httpClient`, `browserProfiles`, `incognitoSession`, `tabStateStore`, `thumbnails`, `pageScripts`, `downloadTracker`, `networkMonitor`, `tabManager`.

Việc để các phase sau:
- Phase 5: `WebViewHost` chỉ cho tab có URL (tab New tab page có URL null sẽ tạo WebView trống); khi `generation` null (renderer bị bỏ sau ngân sách) phần lỗi do phase 5/7 vẽ theo `runtime.loadError`; FLAG_SECURE theo mọi entry đang hiện, chỉ gỡ cờ sau transition, và `setRecentsHidden` suốt lúc còn tab incognito; `MainActivity` chạy `singleTask`: xử lý `onNewIntent`; `BookmarkImporter` xử lý `importTree == null` thành lỗi "không có bookmark".
- Phase 7: dialog quyền gọi `host.sitePermissions.allow(pending, remember = true)`, `block(pending)`, `dismiss(pending)` với đúng `pending` đang vẽ và hiển thị `pending.host`; `DownloadSnackbarEffect` collect `tracker.events` suốt composition (không replay); "Download image/link" gọi `host.downloads.start(..., userInitiated = true)`: tải này không mang cookie (ảnh cần đăng nhập có thể thất bại; cân nhắc tải trong process); tải `data:`/`blob:` mất tên do trang đặt (lưu `download.<ext>`), blob thất bại khi trang thu hồi URL ngay sau cú bấm hoặc CSP chặn `fetch(blob:)` (chia sẻ ảnh `blob:` cùng giới hạn); hộp thoại "Open in another app?" cho tab incognito (hiện mọi scheme ngoài `tel:`/`mailto:`/`sms:`/`smsto:` bị chặn lặng lẽ); fullscreen chưa có gợi ý thoát (trang có thể vẽ thanh địa chỉ giả); tải trong process (incognito) chưa có tiến độ hay huỷ; thanh Find: gọi lại `find` hoặc `clearFind` khi tài liệu đổi (bước Next đang chờ đếm sẽ không bao giờ ra nếu lần đếm bị huỷ); chỉ đặt `SkimWebView` trong `PullToRefreshBox` (nó giành cử chỉ kéo ở đỉnh trang với mọi cha); thông báo/hộp thoại JS của tab đang hiện vẫn là hộp thoại mặc định của WebView (tối đa 3 mỗi tài liệu).
- Phase 9: thumbnail tab thường nằm trong RAM suốt process (khoảng 300 KB mỗi tab ở màn 1080 px): quyết LRU theo byte.
- Phase 10: `runtime.readerable` đã `false` và `extractPage` trả null trên WebView dưới Chromium 80, nên nút Reader không hiện ở đó; Reader phải xử lý `extractPage == null` ("không đọc được trang này").
- Phase 11: `ChatRepository.removals` không replay nên bộ nhớ chat đăng ký trước; `returnToNewTabPage` không gỡ chat; `PageContent.url` có thể dài tới 2 MB (prompt tự cắt; tham số highlight cũng mang URL đầy đủ); `extractPage`/`highlightBlock` chạy ở main world bằng `evaluateJavascript` có timeout 10 s; kiểm `kotlin-reflect` 1.9.25 với stdlib 2.4.21 bằng `ReleaseSmokeTest`.
- Phase 12: `applyLayout(expectedVersion)` và `pendingCloseIds()` đã có; Undo gom nhóm tắt sau process death nhờ `layoutVersion`.
- Phase 8: Clear browsing data phải xoá cả `tabstate/` (giữ URL hiện tại), `thumbs/`, snapshot gom nhóm, gọi `clearHistory()` của WebView đang sống và checkpoint; gọi `undo.cancelAll(...)` trước mọi xoá hàng loạt.
- Phase 13: đo lại dex và kích thước; gate `skim_api28` luôn cần đếm skip bằng `AssumptionViolatedException:` vì AGP 9.4.1 ghi skip theo assumption thành `<failure>` (task vẫn xanh).

Giới hạn đã biết:
- Extractor, Reader và Skim AI cần WebView từ Chromium 80 (Readability dùng `??`, `?.`); ảnh hệ thống `skim_api28` có WebView 66 nên ở đó chúng tự tắt. Máy API 26–28 thật thường có WebView cập nhật qua Play.
- Hai `MainActivity` chồng nhau (cờ `CLEAR_TASK` từ app khác): trạng thái trang xoá khi engine mới gắn, nhưng WebView của pool cũ còn báo sự kiện trong dưới 1 giây trước khi bị huỷ.
- Profile incognito vừa tạo mà process chết trước khi có WebView dùng nó (cửa sổ khoảng một khung hình) để lại thư mục mồ côi mà `purgeStale` không thấy; WebView có `MULTI_PROFILE` nhưng thiếu `DELETE_BROWSING_DATA` để service worker tới lần `purgeStale` sau.
- DownloadManager vẫn gửi `Cookie` qua redirect do server đặt cho tải do trang mở qua https (giới hạn của DownloadManager với thiết kế đã chốt).
- WebView chỉ giao tải sau khi nhận byte đầu của body; Chromium có thể gửi chuỗi chạm không-hủy-được khi máy chậm, khi đó trang bản đồ vẫn có thể bị kéo-làm-mới (Chrome cũng vậy).
- Khi `restore()` quá 5 giây hoặc lỗi, phiên đó không ghi tab vào Room để khỏi đè dữ liệu chưa đọc được.

Known deviations (đã chấp nhận): extract/highlight/readerable chạy main world bằng `evaluateJavascript` (androidx.webkit 1.17.1 không có thực thi theo world); ghim popup tối đa 2 cửa sổ mới nhất (lệch với "không bao giờ evict", vì `target=_blank` cũng đi qua `onCreateWindow`); điều hướng top-level `data:`/`blob:`/`about:` để WebView xử lý; ảnh `data:` lớn hơn 64 KB khi nhấn giữ bị bỏ; vận chuyển blob bằng `evaluateJavascript` thay cho message listener (lý do ở trên); cookie cho DownloadManager chỉ khi trang tự mở, qua https và listener có type; tab incognito loại khỏi autofill (`IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`) `[DEFAULT — chờ validate]`: mất điền mật khẩu đã lưu ở tab ẩn danh; `beforeunload` vượt hạn mức hoặc ở tab nền được `confirm()` (cho rời trang) thay vì `cancel()`; `robolectric.properties` gộp `application=android.app.Application` vào file gốc `sdk=36`; `.kotlin` thêm vào `.gitignore`.
