# Báo cáo: lượt 4B của phase 4 (lõi WebView + nối vào ứng dụng)

## Kết quả

- Status: DONE_WITH_CONCERNS. Đủ 14 mục phạm vi: `SkimWebView`, hai client, `WebViewPool` + `WebViewPoolPolicy`, `BrowserHost`/`LocalBrowserHost`, `ActivityHost` (+ `ActivityResultHost`), `FullscreenController`, `WindowSecureController`, `WebViewProfiles`, `applyDesktopMode`, chụp thumbnail, `PageScripts` + 6 asset JS, `WebViewHost`, nối `AppContainer`/`AppStorage`/`SkimApplication`/`MainActivity`. Không `git add`/commit; không sửa `design/`, manifest, Gradle.
- 15 file main mới/sửa trong `browser/` (1.925 dòng), 3 script Skim (265 dòng) + 3 file Readability (đúng hash), 4 file app sửa (+124/−7). 146 test mới; toàn dự án 709 test xanh (563 cũ + 146), 0 skip.
- 58 đột biến, cuối cùng 58/58 bị bắt (lần chạy đầu 54/56; 2 test yếu đã siết lại, xem dưới). Lint giữ đúng bộ cũ (123), 0 cảnh báo trong file mới. Biên dịch lại không incremental: 0 dòng `w:`.
- Script trang được chạy thật trong Chrome headless (harness trong scratchpad, không commit): 8 kiểu ẩn đều bị loại, ký tự zero-width/bidi bị xoá, id ổn định qua hai lần trích, highlight đúng/sai URL, không rò global, `module` của trang không bị ghi; 7/7 đột biến JS bị harness đó phát hiện.

## Kiểm chứng (chạy thật)

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.browser.*' --console=plain` ×3 liên tiếp | 3/3 BUILD SUCCESSFUL; mỗi lần 319 test, 0 lỗi, 0 skip (29 lớp) |
| 10 vòng các lớp mới/đổi (17 lớp, mỗi vòng `cleanTestDebugUnitTest` trước) | 10/10 xanh, mỗi vòng 181 test |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; 709 test, 0 lỗi, 0 skip (75 lớp) |
| `./gradlew :app:assembleDebug --console=plain` | BUILD SUCCESSFUL; APK có `assets/js/**` (6 file, đúng kích thước) |
| `./gradlew :app:lintDebug --console=plain` | 0 lỗi; 123 cảnh báo cũ (UnusedResources 118, NewerVersionAvailable 3, AndroidGradlePluginVersion 1, InsecureBaseConfiguration 1); 0 trong file mới; không còn `MissingOnRenderProcessGone` |
| `./gradlew :app:verifyRoborazziDebug --console=plain` | BUILD SUCCESSFUL (709 test, golden không đổi) |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL, 24/24 task chạy lại, 0 dòng `w:` |
| Quét vệ sinh (82 file mới/đổi) | 0 ID plan/phase/finding/"D8"/"#N"/"Red Team"; 0 ký tự bidi/zero-width/điều khiển thô; 0 secret; không `Log` nào in URL/title |
| Readability 0.6.0 | SHA-512 tarball khớp `dist.integrity` (`sha512-juG5…T9/QQ==`); SHA-256 ba file trùng giá trị controller đưa |

## Đột biến

Harness: `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/p4b-tools/mutate.py` (mẫu khớp đúng 1 lần, chạy lớp test liên quan, khôi phục, so md5 từng file và md5 cả cây `app/src/main`: khớp sau mọi lần).

| Nhóm | ID | Đột biến | Bị bắt bởi (ví dụ) |
|---|---|---|---|
| Chính sách | P01–P08 | capacity 4; bỏ `keep`; bỏ ghim; `touch` không đổi thứ tự LRU; BACKGROUND evict; bỏ `lowMemory`; `onStart` không phục hồi; quan hệ popup còn sau khi opener mất | `WebViewPoolPolicyTest` (P08: `aNewWebViewOfEitherSideIsNoPartOfTheOldWindow`) |
| WebViewClient | C01–C07 | lỗi subframe thành lỗi trang; subframe được mở app; bỏ gesture; không fallback khi thiếu app; SecurityException cũng fallback; trả `false` khi renderer mất; chặn trang web | `SkimWebViewClientTest` |
| ChromeClient | H01–H04 | popup không cần gesture; callback cũ không nhận null; kết quả muộn đọc lại; detach không trả lời input | `SkimChromeClientTest` |
| FLAG_SECURE/Recents | W01–W03 | tắt cờ ngay trong khung; lệnh tắt muộn bỏ qua owner quay lại; recents ẩn sau khung | `WindowSecureControllerTest` |
| ActivityHost | A01–A02 | yêu cầu bị thay không được trả lời; destroy để yêu cầu treo | `ActivityResultHostTest` (A02 sau khi siết test) |
| Fullscreen | F01–F03 | không gọi `onCustomViewHidden`; tab khác thoát được; Back không tới | `FullscreenControllerTest`, `SkimChromeClientTest` |
| Script/asset | S01–S04, R01 | bỏ `var module;`; nối args vào chuỗi; đọc asset mỗi lần; sửa tay Readability.js; args mất `url` | `PageScriptsTest`, `HighlightRequestTest` |
| Pool | L01–L15 | load trước restore; không tải khi không có state; đóng tab không giao state; evict mất state; script chờ không được trả lời; evict tab hiện tại; SHOW_ERROR vẫn tạo lại; Try again không gỡ chặn; BACKGROUND không lưu; trang lỗi vẫn chụp; host cũ lấy view của host mới; incognito không `setProfile`; popup không ghim; host không dựng lại sau evict; chụp ngay không đợi 500 ms | `WebViewPoolTest` |
| Khác | U01–U02, T01–T02, V01–V03, X01–X02, K01–K02 | Desktop UA/viewport; thumbnail bỏ scroll/khung; nhấn giữ ảnh, mọi sự kiện là gesture, nhận mọi long-press; `WebViewHost` không theo thế hệ, không trả WebView; `close()` không đóng monitor; tabstate vào cache | `DesktopModeTest`, `ThumbnailCaptureTest`, `LongPressHitTest`, `SkimWebViewTest`, `WebViewHostTest`, `AppContainerBrowserTest` |

P08 và A02 sống ở lần đầu: P08 vì `isPinned` còn kiểm đối tác đang sống, nhưng mục cũ sẽ ghim lại cặp khi opener có WebView mới (đã thêm test đúng ca đó); A02 vì test gọi `chooseFiles` lần nữa và lần gọi đó che mất (đã khẳng định ngay sau `destroy()`). L05 chỉ bị bắt nhờ timeout 60 s của `runTest` (đúng bản chất: coroutine treo).

## Chữ ký thật (cho 4C/4D)

```kotlin
data class BrowserHost(val pool: WebViewPool, val activityHost: ActivityHost, val fullscreen: FullscreenController,
                       val secure: WindowSecureController)
val LocalBrowserHost = staticCompositionLocalOf<BrowserHost> { error("BrowserHost not provided") }

@MainThread interface ActivityHost {
    fun chooseFiles(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit)            // đúng 1 lần; huỷ = RESULT_CANCELED, null
    fun requestPermissions(permissions: Array<String>, onResult: (granted: Map<String, Boolean>) -> Unit) // đủ mọi quyền đã xin; huỷ = false hết
    fun startExternal(intent: Intent)   // ném ActivityNotFoundException / SecurityException
}
@MainThread class ActivityResultHost(activity: ComponentActivity, registry: ActivityResultRegistry = activity.activityResultRegistry)
    : ActivityHost { fun destroy() }    // tạo trong onCreate (trước STARTED)

@MainThread class WebViewPool(activity: Activity, tabs: TabManager, incognito: IncognitoSession, stateStore: TabStateStore,
    thumbnails: ThumbnailStore, activityHost: ActivityHost, fullscreen: FullscreenController,
    isLowRamDevice: Boolean = <ActivityManager>, mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    drawThumbnail: (View) -> Bitmap? = ::captureThumbnail) : TabEngine {
    fun generation(tabId: String): StateFlow<Long?>   // giá trị mới khi WebView của tab bị thay/huỷ; null = SHOW_ERROR tới reload/load
    fun obtain(tab: Tab): WebView                      // @throws IllegalStateException: incognito không có phiên, pool đã huỷ
    fun obtainForPopup(popupTabId: String): WebView?
    fun saveAll(); fun onStart(); fun onStop(); fun onTrimMemory(level: Int); fun destroyAll()
    internal fun showInHost(tabId: String): WebView?; internal fun releaseFromHost(tabId: String, view: View)
}
class SkimWebViewClient(tabId: String, events: TabEventSink, activityHost: ActivityHost,
    loadInTab: (tabId: String, url: String) -> Unit,
    onRendererGone: (view: WebView, action: RendererGoneAction, didCrash: Boolean) -> Unit) : WebViewClient() { fun detach() }
class SkimChromeClient(tabId: String, events: TabEventSink, activityHost: ActivityHost, fullscreen: FullscreenController,
    obtainForPopup: (popupTabId: String) -> WebView?,
    parseFileChooserResult: (resultCode: Int, data: Intent?) -> Array<Uri>? = WebChromeClient.FileChooserParams::parseResult)
    : WebChromeClient() { fun detach() }
class SkimWebView(context: Context) : WebView(context), NestedScrollingChild3 {
    var onHitTarget: ((HitTarget) -> Unit)?; val lastUserGestureAt: Long; fun hadRecentGesture(withinMs: Long): Boolean
    companion object { const val NO_GESTURE = Long.MIN_VALUE }
}
@MainThread class FullscreenController(activity: ComponentActivity) {
    val isFullscreen: StateFlow<Boolean>; fun enter(ownerTabId: String, view: View, callback: CustomViewCallback)
    fun exit(); fun exit(ownerTabId: String); fun destroy()
}
@MainThread class WindowSecureController(activity: Activity) {   // + internal (window, setRecentsScreenshotEnabled?) cho test
    val isSecure: Boolean; fun setSecure(owner: String, secure: Boolean); fun setRecentsHidden(owner: String, hidden: Boolean)
}
@MainThread class WebViewProfiles : BrowserProfiles
class PageScripts(assets: AssetManager) : PageScriptSource     // + internal (open: (path) -> InputStream)
@MainThread fun applyDesktopMode(webView: WebView, on: Boolean)
@Composable fun WebViewHost(tabId: String, modifier: Modifier = Modifier)
internal data class HighlightArgs(val id: String, val cited: String?, val url: String)
// AppContainer (lazy): networkMonitor, incognitoSession, tabStateStore, thumbnails, pageScripts, tabManager
// AppStorage: tabStateDir, thumbnailDir, incognitoSharedDir (temp(dir) đặt cả ba trong dir)
```

## Deviations (lệch spec và lý do)

- extract/highlight/readerable chạy ở main world bằng `WebView.evaluateJavascript`: androidx.webkit 1.17.1 có `JavaScriptExecutionWorld` (`getExecutionWorld`, `addJavaScriptOnEvent`, `addWebMessageListener` theo world) nhưng không có API chạy một script theo yêu cầu trong world và nhận kết quả. Trang có thể sửa DOM/prototype để làm sai kết quả trích: Kotlin vẫn lọc lại (`parsePageContent`), highlight kiểm URL+nonce ở Kotlin và tự kiểm `location.href` trong script.
- `ActivityHost` được hiện thực bằng lớp `ActivityResultHost` (trong `ActivityHost.kt`) mà `MainActivity` tạo trong `onCreate`, thay vì `MainActivity` tự implement: test được bằng `ActivityResultRegistry` giả.
- `WebViewPool` có thêm tham số `drawThumbnail`: trong Robolectric `WebView.setFrame` uỷ hết cho provider nên WebView không bao giờ có kích thước; thiếu tham số này thì không kiểm được lịch chụp/điều kiện chụp. Vẽ thật được kiểm bằng `View` thường trong `ThumbnailCaptureTest` và để 4D trên WebView thật.
- Hàng đợi khi WebView đang khôi phục: ngoài `load`/`reload` còn giữ `goBack` (trả `true`), `goForward`, `clearHistory` (lệnh điều hướng cuối cùng thắng; reload sau load bỏ). Tab chưa có WebView vẫn chỉ giữ load/reload như KDoc của `TabEngine`. Lý do: Back đúng lúc vừa mở app không được biến thành "trang đầu" (đóng tab hoặc về New tab page).
- Host đếm số `WebViewHost` đang hiện một WebView: hai màn cùng compose một tab lúc chuyển cảnh thì host cũ nhả ra không lấy mất view của host mới.
- WebView offscreen không gọi `onPause()` (để trang tải và chạy JS như đang hiện); host offscreen dùng `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS` (ẩn cả con khỏi trợ năng), đặt ở index 0 của `android.R.id.content`, `translationX` = 2 lần bề rộng màn hình.
- `applyDesktopMode` ghi lại client hints mặc định của WebView trước lần bật đầu tiên (WeakHashMap theo `WebSettings`, main thread) và trả đúng nó khi tắt, thay vì đặt `UserAgentMetadata.Builder().build()` (builder đó cố định `mobile = true`); WebView chưa từng bật Desktop site không bị ghi metadata.
- `skim-extract.js`: id `data-skim-i` giữ nguyên qua các lần trích (chỉ đánh số mới cho phần tử mới hoặc id bị trang nhân bản), text lấy từ phần tử sống bằng bộ duyệt bỏ con bị ẩn (nhờ vậy span `display:none`/sr-only trong đoạn văn hiện cũng bị bỏ), kích thước < 2 px tính là ẩn, thêm `visibility:collapse`, `closest('[hidden],[aria-hidden="true"]')`. `checkVisibility` truyền cả tên option cũ và mới (`checkOpacity`/`opacityProperty`…).
- `SkimChromeClient.onCreateWindow` kiểm `WebViewTransport` trước khi xin tab popup; không tạo được WebView thì đóng lại tab vừa tạo (`onPopupClosed`).
- `SkimWebView`: khi đang kéo, mọi sự kiện khác (ngón thứ hai) bị nuốt vì trang đã nhận `ACTION_CANCEL`.
- `@SuppressLint` cục bộ có lý do: `SetJavaScriptEnabled` (`configure`), `ClickableViewAccessibility` (`onTouchEvent`), `RequiresFeature` trên lớp bọc `Profile` (chỉ tồn tại khi `ProfileStore` đã tạo, các lời gọi `ProfileStore` vẫn có `if (isFeatureSupported)` từ vựng), `MissingOnRenderProcessGone` trên `SkimWebViewClient`: đã kiểm bằng `javap` rằng `RenderProcessGoneDetector.visitConstructor` báo vô điều kiện mọi lời gọi constructor `WebViewClient()`, mà lớp con Kotlin bắt buộc gọi nó ở header; phần `visitClass` (kiểm có override) không báo — lần lint trước khi suppress chỉ có đúng một báo cáo, tại `) : WebViewClient() {`.
- Hạ tầng test: thêm `app/src/test/resources/com/dantech/skimbrowser/robolectric.properties` (`application=android.app.Application`). Lý do: `SkimApplication.onCreate` giờ chạy `restore()` nền (mở DB mặc định, DataStore, đăng ký network callback) trong mọi test Robolectric; `ConnectivityNetworkMonitorTest` (đòi đúng 1 callback) và `AppContainerStorageTest` (đòi DB mặc định không tồn tại) sẽ đỏ ngẫu nhiên. Không test nào dùng `SkimApplication`; test cần nó tự đặt `@Config(application = SkimApplication::class)`. File cấp package, không sửa `robolectric.properties` gốc của phase 1.
- File 4A đã sửa: `DesktopUserAgent.kt` (thêm `applyDesktopMode`, phần cũ giữ nguyên), `page/HighlightRequest.kt` (+`url`), `page/HighlightRequestTest.kt` (cập nhật + 1 test), `TabManagerPageTest.kt` (một assert so chuỗi JSON đúng từng ký tự, nay có `"url"`). Không file 4A nào khác đổi (so với bản sao baseline của controller).
- Test double trong package `android.webkit` (`app/src/test/java/android/webkit/WebViewTestDoubles.kt`): constructor của `WebResourceError` bị ẩn khỏi SDK (package-private trong android.jar), constructor `RenderProcessGoneDetail` bị deprecated.

## Việc cho 4C

- Thêm `sitePermissions`, `downloads`, `blob` vào `BrowserHost`; pool cần nhận bridge/router để `configure()` gắn `setDownloadListener` và để `SkimChromeClient` override `onGeolocationPermissionsShowPrompt`/`onPermissionRequest` (thêm tham số constructor). Nhớ gỡ `setDownloadListener(null)` trong `WebViewPool.destroy`.
- Giới hạn download tự động: `SkimWebView.hadRecentGesture(5_000)`; quyền runtime: `ActivityHost.requestPermissions`; receiver `ACTION_DOWNLOAD_COMPLETE` ở `onStart`/`onStop` của `MainActivity`.

## Việc cho 4D (chỉ kiểm được trên WebView thật)

- `setProfile` chạy trước mọi thứ cho WebView incognito, popup incognito và WebView tạm của `end()`; cookie tách profile.
- `restoreState` thật: back history, vị trí cuộn, lệnh load/reload/back đến trong lúc khôi phục; state bị cắt/khác WebView → tải lại URL.
- `skim-extract.js`/`skim-highlight.js`/`skim-readerable.js` trên `article.html` trong WebView (đã chạy được trong Chrome desktop, chưa trong WebView); `awaitPageReady` với WebView offscreen (tải và chạy JS khi nằm ngoài màn hình).
- Pull to refresh: dấu `dy`/velocity, cổng `onOverScrolled(clampedY)`, trang bản đồ ở đỉnh không refresh nhầm.
- Long-press link/ảnh-trong-link qua `requestFocusNodeHref` (message `url`/`title`).
- Thumbnail vẽ phần mềm của WebView thật (cả trang trắng với video/WebGL), đúng store thường/incognito.
- `onRenderProcessGone` qua `WebViewCompat.getWebViewRenderProcess(webView)?.terminate()`: destroy trong callback, `WebViewHost` dựng lại theo `generation`, hết ngân sách → `Crashed`, Try again; `didCrash = false` hạ capacity 1.
- Popup `window.open` có gesture, `window.close()`, trim `BACKGROUND` giữ opener; destroy popup ngay trong `onCloseWindow`.
- File chooser thật (huỷ không treo), video toàn màn hình (ẩn system bars, Back), FLAG_SECURE và `setRecentsScreenshotEnabled` (ảnh chụp màn hình, Recents).
- Desktop site: UA + client hints thật (`USER_AGENT_METADATA`), tắt trả đúng hints mặc định.
- `pauseTimers`/`resumeTimers` ở stop/start; `MainActivity` cả vòng đời (attach, `purgeStale` sau khung đầu, `finalizePendingCloses` khi finishing).

## Concerns

- Popup ghim mãi tới khi đóng: link `target=_blank` có gesture cũng đi qua `onCreateWindow`, nên nhiều tab mở kiểu đó là nhiều cặp không bao giờ bị evict (capacity mềm), kể cả sau trim critical. Gợi ý cho controller: chỉ ghim khi popup hoặc opener là tab hiện tại, hoặc giới hạn số cặp.
- Readability đổi `div` chỉ chứa text thành `p` không có `data-skim-i`, nên các đoạn đó không vào `blocks` (Reader/AI thiếu text ở trang dựng bằng div). Cần quyết định có đánh số thêm "div lá" không.
- Asset script đọc trên main thread ở lần dùng đầu (~95 KB, một lần).
- `WebViewHost` cho tab ở New tab page (url null) sẽ tạo một WebView trống; phase 5 nên chỉ hiện `WebViewHost` cho tab có URL. Khi `generation` là null, phần lỗi 11 phải do phase 5/7 vẽ theo `runtime.loadError`.
- Phase 5 (plan) gọi `secure.setSecure(true)` và dự định "WindowSecureController giả ghi thứ tự"; API lượt này là `setSecure(owner, secure)` trên lớp final (test cùng module dùng constructor internal với window Robolectric).
- `git status` cho thấy các file `.idea/*` ở trạng thái `A` (đã vào index) — không phải do tôi (không chạy `git add` nào); có lẽ Android Studio tự thêm. Controller cẩn thận khi commit.

## Câu hỏi chưa giải quyết

1. Popup: giữ ghim tới khi đóng như hợp đồng, hay thu hẹp (chỉ khi một bên là tab hiện tại / giới hạn số cặp)?
2. Có đánh số `div` lá để không mất text ở trang không dùng `<p>` không?
3. Giữ file `robolectric.properties` cấp package (Application thường cho test JVM), hay chuyển dòng đó vào file gốc của phase 1?
