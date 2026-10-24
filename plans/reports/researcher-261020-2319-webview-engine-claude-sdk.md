# Nghiên cứu: WebView engine, tích hợp hệ thống Android và gọi Claude từ Android (Skim)

- Thời điểm nghiên cứu: 2026-10-20 (Asia/Singapore). Phạm vi gồm các câu A1–A8, B9–B10 và C11–C14 trong brief. Không sửa file nào của dự án.
- Phương pháp: kiểm tra `maven-metadata.xml` và POM; đọc source jar (anthropic-java 2.73.0, Compose UI 1.13.0-beta01, material3 1.5.0-beta01, AGP 9.4.1); đọc class của androidx.webkit 1.17.1; đọc docs chính thức (developer.android.com, bản `.md` của platform.claude.com) và source Chromium/AOSP.
- Đã chạy hai thí nghiệm, đều nằm trong scratchpad: (1) một probe app dùng AGP 9.4.1 với R8, build 3 biến thể; (2) chạy APK release đã qua R8 trên emulator API 30 arm64, nối với một fake SSE server dựng trên máy.

## 0. Kết luận chính

1. **`com.anthropic:anthropic-java` dùng được trên Android minSdk 26.** Bằng chứng là chạy thật chứ không chỉ đọc docs. APK release đã R8, chạy trên ART (API 30), làm đúng các việc sau: stream `text_delta`, `citations_delta` loại `content_block_location`, block `fallback`, `stop_reason: refusal` kèm `category`, và khi Stop thì kết nối bị đóng thật (server nhận `BrokenPipe` sau 5/42 event). Bản mới nhất là **2.73.0** (2026-10-15), không phải 2.34.0 như brief ghi.
2. **Hai thay đổi build bắt buộc khi thêm SDK.** (a) Thêm `-dontwarn com.google.auto.value.AutoValue`, vì nếu thiếu thì R8 dừng build với lỗi "Missing class" từ `io.opentelemetry.api`. (b) `optimization.packageScope = androidx/kotlin/kotlinx` trong `app/build.gradle.kts` khiến SDK không được shrink: APK probe nặng **9,65 MB (4 file dex)**, so với **1,34 MB** khi để scope mặc định `"**"`. Mã nguồn AGP xác nhận packageScope chỉ tối ưu đúng các package được liệt kê.
3. **Profile "incognito" của androidx.webkit được lưu trên đĩa và không xoá được trong cùng process sau khi đã load.** `deleteProfile` ném `IllegalStateException` cho profile đã load bằng `getOrCreateProfile`/`getProfile`. Trong Chromium, `AwBrowserContextStore::Delete` trả `kInUse` khi `entry->instance` tồn tại. Vì vậy câu "Profile deleted with the last tab" phải hiện thực bằng cách khác: xoá dữ liệu ngay, đổi sang tên profile mới cho mỗi phiên, và xoá các profile cũ ở lần khởi động sau.
4. **Cả `fallbacks` và `effort` đều có typed builder trong SDK.** Đó là `MessageCreateParams.fallbacksDefault()` và `BetaOutputConfig.Effort.MEDIUM`; body gửi đi đúng là `"fallbacks":"default"` và `"output_config":{"effort":"medium"}`. Không cần `putAdditionalBodyProperty`.
5. **Citations và structured output không dùng chung được** (API trả 400). Vì vậy hỏi đáp trên trang (06) dùng citations, còn gom nhóm tab và "Summarize my 7 open tabs" (06b, 09) dùng `output_config.format` và không bật citations.
6. **`android.webkit.WebView` không implement `NestedScrollingChild`**, nên `PullToRefreshBox` không nhận được sự kiện cuộn của nó. Hướng xử lý là viết subclass WebView implement `NestedScrollingChild3`; Firefox Android đã làm tương tự với `NestedGeckoView`. Phía Compose, `AndroidViewHolder` đã map `TYPE_TOUCH` thành `UserInput` và chuyển `onNestedPreFling` thành `dispatchPreFling`.
7. **Chromium map `ERR_INTERNET_DISCONNECTED` sang `ERROR_HOST_LOOKUP`**, tức cùng mã với lỗi DNS. Để hiện đúng trạng thái "No internet" phải kết hợp chuỗi `description` với kết quả kiểm tra `ConnectivityManager`.
8. **Reader view dùng Readability.js 0.6.0** (Mozilla, Apache-2.0), inject bằng `evaluateJavascript`. Không chọn Readability4J vì bản cuối là năm 2021. Cùng một danh sách block được dùng cho ba việc: render Reader, làm document gửi AI (chỉ số block = số đoạn), và highlight khi bấm citation.

## A. WebView engine

### A1. Một WebView cho mỗi tab, giữ sống và khôi phục

**Những điểm đã xác minh trong docs:**
- Phần lớn bộ nhớ của WebView là native memory nằm ở renderer process, nên heap dump Java không thấy. Khi bỏ một WebView phải remove khỏi parent rồi gọi `destroy()`; nếu không thì Activity bị leak ([manage-webview-memory](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory)).
- Một renderer có thể phục vụ nhiều WebView. Policy mặc định là `RENDERER_PRIORITY_IMPORTANT` bất kể WebView có hiển thị hay không; nếu đổi policy thì bắt buộc phải xử lý `onRenderProcessGone` ([WebView.setRendererPriorityPolicy](https://developer.android.com/reference/android/webkit/WebView#setRendererPriorityPolicy(int,%20boolean))). Sau khi renderer chết, WebView đó không dùng lại được, phải remove và destroy. Từ webkit 1.17.0 có lint cảnh báo khi thiếu callback này.
- `savedInstanceState` chỉ có 1 MB dùng chung cho cả process. Vượt mức sẽ gây `TransactionTooLargeException`. Docs khuyên dùng `WebViewCompat.saveState(webView, bundle, maxSizeBytes, includeForwardState)` (feature `SAVE_STATE`): API này cắt bớt các entry cũ trong Bundle nhưng không đụng tới history đang sống. Khoảng 70% dung lượng state là PageState blob (form, vị trí cuộn) ([manage-state](https://developer.android.com/develop/ui/views/layout/webapps/manage-state)).
- Chỉ nên gọi `restoreState` trên một WebView chưa load gì; nếu gọi muộn hơn có thể gây tác dụng phụ ([WebView.restoreState](https://developer.android.com/reference/android/webkit/WebView#restoreState(android.os.Bundle))).

**Khuyến nghị (xếp theo thứ tự ưu tiên):**
1. **TabManager giữ một LRU gồm N WebView sống**, đề xuất N = 3 (tab đang mở + 2 tab gần nhất). Khi nhận `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` trở lên, hoặc khi `isLowRamDevice`, hạ N xuống 1. Con số 3 là heuristic; không có con số chính thức nào. Tab bị đẩy ra khỏi LRU thì: `saveState` → ghi ra file → `destroy()`.
2. **Ghi state ra đĩa theo từng tab** (`filesDir/tabstate/<id>.bin`, dùng `Parcel.marshall()` cho Bundle), không gom N tab vào `savedInstanceState`. Đặt `maxSizeBytes` khoảng 256 KB và `includeForwardState = true` vì Skim có nút Forward. Parcel không ổn định qua các phiên bản OS/WebView, nên nếu `restoreState` trả `null` thì `loadUrl(tab.url)`. URL, title và trạng thái sheet để trong DB.
3. **Tab incognito không bao giờ ghi state hay thumbnail ra đĩa.**
4. **Context:** WebView phải được tạo bằng context của Activity, vì dialog JS và popup `<select>` cần theme. TabManager vì thế sống theo Activity. Có hai cách đi: khai báo `android:configChanges` (orientation|screenSize|screenLayout|smallestScreenSize|uiMode) để xoay màn hình không tạo lại Activity, hoặc tạo WebView qua `MutableContextWrapper` rồi đổi base context sau khi Activity được tạo lại. Nên chọn cách 1 vì đơn giản hơn.

**Gắn WebView vào Compose mà không bị tạo lại.** `AndroidView` chỉ gọi `factory` đúng một lần cho mỗi node, và chỉ tái sử dụng View khi có `onReset` ([AndroidView KDoc, ui 1.13.0-beta01]). Vì vậy instance được hoist trong TabManager và gắn với `key(tab.id)`:

```kotlin
key(tab.id) {
    AndroidView(
        factory = { tabs.webViewFor(tab).also { (it.parent as? ViewGroup)?.removeView(it) } },
        onRelease = { /* chỉ tách khỏi cây view; TabManager quyết định khi nào destroy */ },
        modifier = Modifier.fillMaxSize(),
    )
}
// TabManager: LinkedHashMap(accessOrder = true) làm LRU; webViewFor() tạo SkimWebView(activity),
// gọi setProfile NGAY nếu là tab incognito (xem A2), sau đó configure(); restoreState(file) ?: loadUrl(url)
```

### A2. androidx.webkit và Profile cho incognito

- **Phiên bản:** bản stable là **1.17.1** (2026-09-23); có thêm **1.18.0-rc01** (2026-10-07). minSdk của thư viện là 24 ([release notes](https://developer.android.com/jetpack/androidx/releases/webkit)). Nên dùng 1.17.1 và chuyển lên 1.18.0 khi bản stable ra.
- **Quy tắc của `WebViewCompat.setProfile`** ([reference](https://developer.android.com/reference/androidx/webkit/WebViewCompat#setProfile(android.webkit.WebView,java.lang.String))): phải gọi trước mọi thao tác khác trên WebView, ngoại trừ việc gắn WebView vào cây view. Hàm sẽ ném lỗi nếu WebView đã navigate, đã chạy `evaluateJavascript`, đã gọi `getProfile`, hoặc đã set profile trước đó. Nếu profile chưa tồn tại thì hàm tự tạo.
- **`ProfileStore.deleteProfile`** ([reference](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String))): ném lỗi khi còn WebView sống đang dùng profile, và cả khi profile đã được load vào bộ nhớ. Dữ liệu có thể bị xoá bất đồng bộ. Mã Chromium khẳng định điều này: [`aw_browser_context_store.cc`](https://github.com/chromium/chromium/blob/main/android_webview/browser/aw_browser_context_store.cc) có `if (entry->instance) return DeletionResult::kInUse;`, và không có đường nào để unload một profile.
- **API không có cờ off-the-record.** Profile chỉ cung cấp `cookieManager`, `webStorage`, `geolocationPermissions`, `serviceWorkerController`, và `httpCache` (feature `HTTP_CACHE_MANAGER`, cần WebView M151 trở lên). Lưu ý: guide manage-state ghi `WebViewFeature.HTTP_CACHE`, nhưng hằng số thật trong AAR là `HTTP_CACHE_MANAGER`.

**Chiến lược đề xuất (đáp ứng "deleted with the last tab" về mặt người dùng):**

```kotlin
// Tên profile mới cho mỗi phiên incognito; chỉ chạy trên UI thread
fun startIncognitoSession(): String = "incognito-${System.currentTimeMillis()}".also { name ->
    if (WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER))
        ProfileStore.getInstance().getOrCreateProfile(name).httpCache.setQuotaBytes(0)
}
fun onLastIncognitoTabClosed(name: String) {            // destroy() mọi WebView incognito TRƯỚC khi gọi
    ProfileStore.getInstance().getProfile(name)?.run {
        cookieManager.removeAllCookies(null); webStorage.deleteAllData(); geolocationPermissions.clearAll()
    }                                                   // phiên incognito tiếp theo dùng tên mới
}
fun purgeStaleIncognito(current: String?) = ProfileStore.getInstance().run {   // lần khởi động sau
    allProfileNames.filter { it.startsWith("incognito-") && it != current }
        .forEach { runCatching { deleteProfile(it) } }   // profile chưa load thì xoá được
}
```

- **Khi `MULTI_PROFILE` không được hỗ trợ:** tắt "New incognito tab" và hiện lời nhắc cập nhật Android System WebView. Không được rơi về profile mặc định, vì như vậy cookie incognito sẽ lẫn sang tab thường. Release notes không nêu phiên bản WebView tối thiểu cho tính năng này, nên chỉ dựa vào kiểm tra lúc chạy `WebViewFeature.isFeatureSupported(MULTI_PROFILE)`.
- **Download trong tab incognito** phải lấy cookie từ `profile.cookieManager`, không lấy từ `CookieManager.getInstance()`.

### A3. FLAG_SECURE và ảnh trong Recents

- Bật `window.addFlags(FLAG_SECURE)` khi một bề mặt incognito đang hiển thị (tab incognito, 09b, 09c), và `clearFlags` khi rời đi. Cách làm: một `LaunchedEffect(isIncognitoSurface)` ở cấp Activity. FLAG_SECURE chặn chụp màn hình và khiến ảnh trong Recents trống ([FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)).
- Không nên chỉ dùng `setRecentsScreenshotEnabled(false)` (API 33). API này chỉ ẩn ảnh trong Recents, người dùng vẫn chụp màn hình được ([Activity](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean))).
- Ảnh Recents được chụp lúc app chuyển xuống nền. Vì vậy cờ phải được bật đồng bộ trước khi frame incognito đầu tiên được vẽ; cần kiểm thử trên thiết bị thật.

### A4. WebViewClient và WebChromeClient

| Nhu cầu | Cách làm (nguồn: reference của `WebViewClient` / `WebChromeClient`) |
|---|---|
| Thanh tiến trình dạng sóng | `onProgressChanged(view, p)` đặt `progress = p / 100f`; ẩn thanh khi p = 100 (`onPageFinished` có thể gọi nhiều lần nên không dựa vào nó). |
| Lỗi tải trang (11) | Dùng `onReceivedError(view, request, error)` (API 23+) và chỉ xử lý khi `request.isForMainFrame`. Hiện UI lỗi của Compose đè lên WebView để không mất history. |
| Tự tải lại khi có mạng | `registerDefaultNetworkCallback`; trong `onCapabilitiesChanged`, khi có `NET_CAPABILITY_VALIDATED` thì `reload()` các tab đang ở trạng thái NoInternet. Cần quyền `ACCESS_NETWORK_STATE`. |
| Lỗi SSL | Không override, để mặc định là `cancel()`. Docs ghi rõ không được `proceed()` và không được hỏi người dùng. |
| Màn hình bị renderer crash | `onRenderProcessGone` trả `true`; destroy WebView của tab đó rồi khôi phục từ state đã lưu. |
| Chọn file | `onShowFileChooser` trả `true`; mở chooser bằng `FileChooserParams.createIntent()` qua ActivityResult, lấy kết quả bằng `parseResult()`. Luôn gọi `onReceiveValue(null)` khi người dùng huỷ. Có các mode `MODE_OPEN/OPEN_MULTIPLE/OPEN_FOLDER/SAVE`. |
| Video toàn màn hình | Dùng `onShowCustomView(view, callback)` (bản có 2 tham số; bản 3 tham số đã deprecated). Đặt view lên một overlay full-screen, `WindowInsetsControllerCompat.hide(systemBars())` với `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Nút Back và `onHideCustomView` gọi `callback.onCustomViewHidden()`. |
| Vị trí | `onGeolocationPermissionsShowPrompt(origin, cb)` chỉ được gọi với origin HTTPS. Hiện dialog "Site permission" của Skim, rồi xin runtime permission `ACCESS_FINE/COARSE_LOCATION`, rồi `cb.invoke(origin, allow, false)`. Quyết định theo từng site lưu trong DB của Skim để sheet Site information hiển thị và xoá được. |
| Camera/mic | `onPermissionRequest(req)`: chỉ `grant()` những resource (`RESOURCE_VIDEO_CAPTURE` / `RESOURCE_AUDIO_CAPTURE`) mà cả người dùng lẫn runtime permission đã cho phép; còn lại `deny()`. Không override thì mặc định là deny. Manifest cần `CAMERA`, `RECORD_AUDIO` và `MODIFY_AUDIO_SETTINGS` (quyền normal) ([MS Learn, WebView WebRTC](https://learn.microsoft.com/en-us/azure/communication-services/quickstarts/voice-video-calling/get-started-android-webview)). |
| alert/confirm | Giữ mặc định (`onJsAlert` trả `false` thì WebView tự hiện dialog). Thiết kế cho phần này chưa có. |
| Download | `setDownloadListener` lấy `URLUtil.guessFileName`, rồi `DownloadManager.Request` kèm header `Cookie` (lấy từ cookie manager của profile) và `User-Agent`, `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`, lưu vào `DIRECTORY_DOWNLOADS`. Trên API 26–28 cần `WRITE_EXTERNAL_STORAGE` với `maxSdkVersion=28`. Chỉ hỗ trợ http/https, không hỗ trợ `blob:` hay `data:`. |
| Snackbar khi tải xong | Đăng ký `ACTION_DOWNLOAD_COMPLETE` bằng `ContextCompat.registerReceiver(..., broadcastPermission = "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS", ..., RECEIVER_EXPORTED)`. Quyền này có protection level signature của DownloadProvider ([AOSP manifest](https://android.googlesource.com/platform/packages/providers/DownloadProvider/+/refs/heads/main/AndroidManifest.xml)). Kiểm tra id có phải do Skim enqueue, rồi query `STATUS_SUCCESSFUL`. |
| Find in page | `setFindListener { active, total, done -> }`; gọi `findAllAsync(q)`; `findNext(forward)` tự quay vòng; `clearMatches()` khi đóng. |
| Nhấn giữ | `setOnLongClickListener`: lấy `hitTestResult.type`. Với `SRC_ANCHOR_TYPE` thì `extra` là link. Với `IMAGE_TYPE` thì `extra` là src. Với `SRC_IMAGE_ANCHOR_TYPE` thì `extra` là src ảnh, còn link lấy qua `requestFocusNodeHref(msg)` (các key `url`, `title`, `src`). Chỉ trả `true` cho ba loại này. |
| Desktop site | Cờ riêng từng tab: `settings.userAgentString` lấy từ `WebSettings.getDefaultUserAgent()`, bỏ `; wv`, `Mobile ` và `Version/4.0 `, thay đoạn `(Linux; Android …)` bằng `(X11; Linux x86_64)`. Thêm `useWideViewPort` và `loadWithOverviewMode`. Nếu có `USER_AGENT_METADATA` thì `setUserAgentMetadata(...setMobile(false).setPlatform("Linux"))`. Cuối cùng `reload()`. |
| Safe Browsing | Mặc định đã bật (`EnableSafeBrowsing` = true) và hiện interstitial mặc định. Giữ nguyên cho đến khi có thiết kế ([managing-webview](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview)). |
| Mở link ở tab nền | Chỉ tạo bản ghi tab với URL; WebView được tạo khi mở tab hoặc khi LRU còn chỗ. Với `target=_blank` / `window.open`: `setSupportMultipleWindows(true)` + `onCreateWindow`, trong đó tạo WebView mới, gọi `setProfile` trước nếu tab mẹ là incognito, rồi gửi `WebViewTransport`. |

**Map mã lỗi sang 3 trạng thái** (mapping lấy từ [AwWebResourceError.java](https://github.com/chromium/chromium/blob/main/android_webview/java/src/org/chromium/android_webview/AwWebResourceError.java): `ERR_TIMED_OUT`/`ERR_CONNECTION_TIMED_OUT` → `ERROR_TIMEOUT`; `ERR_INTERNET_DISCONNECTED`/`ERR_NAME_NOT_RESOLVED` → `ERROR_HOST_LOOKUP`; nhóm refused/reset/closed → `ERROR_CONNECT`):

```kotlin
fun loadErrorOf(error: WebResourceError, net: ConnectivityManager): LoadError = when {
    error.errorCode == WebViewClient.ERROR_TIMEOUT -> LoadError.TimedOut
    error.description?.contains("ERR_INTERNET_DISCONNECTED") == true ||
        net.getNetworkCapabilities(net.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true -> LoadError.NoInternet
    else -> LoadError.Unreachable           // HOST_LOOKUP, CONNECT, UNKNOWN, ...
}
```

**`shouldOverrideUrlLoading`** (theo [Google FAQ 9101196](https://support.google.com/faqs/answer/9101196) và [Chrome intents](https://developer.chrome.com/docs/android/intents)). Hàm này không được gọi cho `loadUrl` hay POST:

```kotlin
override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
    val uri = req.url
    when (uri.scheme?.lowercase()) {
        "http", "https" -> return false
        "file", "content", "javascript" -> return true
    }
    if (!req.hasGesture()) return true                   // như Chrome: không mở app khi không có thao tác người dùng
    val intent = if (uri.scheme == "intent")
        runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return true
    else Intent(Intent.ACTION_VIEW, uri)                 // tel:, mailto:, sms:, geo:, market:...
    intent.addCategory(Intent.CATEGORY_BROWSABLE); intent.component = null; intent.selector = null
    intent.flags = intent.flags and (FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION or
        FLAG_GRANT_PERSISTABLE_URI_PERMISSION or FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
    val fallback = intent.getStringExtra("browser_fallback_url")
    try { view.context.startActivity(intent) } catch (_: ActivityNotFoundException) {
        fallback?.toUri()?.takeIf { it.scheme == "https" || it.scheme == "http" }?.let { view.loadUrl(it.toString()) }
    }
    return true
}
```

### A5. Pull to refresh quanh WebView trong Compose

Đã xác minh: `WebView` kế thừa `AbsoluteLayout` và không implement `NestedScrollingChild` ([reference](https://developer.android.com/reference/android/webkit/WebView)). `Modifier.pullToRefresh` (material3 1.5.0-beta01) chỉ phản ứng với `onPostScroll`/`onPreScroll` có `source == UserInput`, và kích hoạt refresh trong `onPreFling` (đọc `PullToRefresh.kt`). Phía Compose, `AndroidViewHolder` chỉ cầu nối khi `view.isNestedScrollingEnabled`; nó chuyển `onNestedScroll` thành `dispatchPostScroll` (TYPE_TOUCH → UserInput) và `onNestedPreFling` thành `dispatchPreFling` ([nested scroll interop](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/nested-scroll)).

| Thứ hạng | Cách làm | Đúng thiết kế | Độ phức tạp | Rủi ro |
|---|---|---|---|---|
| **1** | `SkimWebView : WebView, NestedScrollingChild3` + `PullToRefreshBox` có sẵn | Có (dùng LoadingIndicator M3) | Trung bình (~60 dòng) | Trang tự bắt cử chỉ chạm, ví dụ bản đồ |
| 2 | `SwipeRefreshLayout` bọc WebView trong `AndroidView` | Không (spinner cũ) | Thấp | Thấp |
| 3 | `pointerInput` tự viết + `state.snapTo()` | Có | Cao | Ngữ nghĩa consume của interop dễ vỡ |

Bản phác cho cách 1 (chưa chạy trên thiết bị; tiền lệ là [NestedGeckoView của Firefox](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/android-components/components/browser/engine-gecko/src/main/java/mozilla/components/browser/engine/gecko/NestedGeckoView.kt)):

```kotlin
class SkimWebView(ctx: Context) : WebView(ctx), NestedScrollingChild3 {
    private val helper = NestedScrollingChildHelper(this)
    private var lastY = 0f; private var pulling = false; private var vt: VelocityTracker? = null
    init { isNestedScrollingEnabled = true; overScrollMode = OVER_SCROLL_NEVER }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            ACTION_DOWN -> { lastY = e.y; pulling = false; vt = VelocityTracker.obtain()
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH) }
            ACTION_MOVE -> { val dy = (lastY - e.y).toInt(); lastY = e.y
                if (!pulling && dy < 0 && !canScrollVertically(-1)) { pulling = true
                    super.onTouchEvent(MotionEvent.obtain(e).apply { action = ACTION_CANCEL }) }
                if (pulling) { vt?.addMovement(e); val c = IntArray(2)
                    dispatchNestedPreScroll(0, dy, c, null, ViewCompat.TYPE_TOUCH)
                    dispatchNestedScroll(0, c[1], 0, dy - c[1], null, ViewCompat.TYPE_TOUCH, IntArray(2))
                    return true } }
            ACTION_UP, ACTION_CANCEL -> { if (pulling) { vt?.computeCurrentVelocity(1000)
                    dispatchNestedPreFling(0f, -(vt?.yVelocity ?: 0f)) }   // luôn gọi, vì refresh chạy trong onPreFling
                stopNestedScroll(ViewCompat.TYPE_TOUCH); vt?.recycle(); vt = null
                if (pulling) { pulling = false; return true } }
        }
        return super.onTouchEvent(e)
    }
    // phần còn lại của NestedScrollingChild2/3: ủy quyền hết cho helper
}
```

Khi chạy trên thiết bị cần kiểm tra lại dấu của dy và velocity, và xem trang bản đồ ở vị trí đầu trang có kích hoạt refresh nhầm không. Nếu có, chỉ bắt đầu kéo sau khi đã nhận `onOverScrolled(clampedY = true)`.

### A6. Thumbnail cho thẻ tab

1. **Chọn:** vẽ WebView ra bitmap đã thu nhỏ bằng phần mềm. Cách này không dính floating toolbar hay các overlay, và không cần `Window`:
   `createBitmap((w*s).toInt(), (w*s/2).toInt()).applyCanvas { scale(s, s); draw(this) }` (core-ktx, s ≈ 0.35, cắt lấy phần đầu theo tỉ lệ 2:1 cho khung preview 92 dp).
2. **Không chọn:** `PixelCopy.request(window, rect, bmp, …)` (API 26) chụp cả overlay Compose đè trên trang, nên ảnh bị dính toolbar.
- **Thời điểm chụp:** khi `progress == 100`, debounce khoảng 500 ms; khi rời tab; và trước khi mở màn 09. Ghi JPEG/WebP vào `cacheDir/thumbs/<tabId>`. Thumbnail incognito chỉ giữ trong bộ nhớ.
- **Rủi ro chưa kiểm chứng:** video, WebGL hoặc canvas có thể ra màu trắng khi vẽ bằng canvas phần mềm. Nếu gặp thì dùng favicon và màu nền làm placeholder.

### A7. Reader view

| Tiêu chí | **Readability.js (chọn)** | Readability4J |
|---|---|---|
| Phiên bản / hoạt động | npm `@mozilla/readability` 0.6.0 (2025-03-03); repo push gần nhất 2026-08-04, 11,5k sao | 1.0.8, 2021-10-26; repo đứng từ 2021 |
| License | Apache-2.0 | Apache-2.0 |
| Đầu vào | DOM thật sau khi JS của trang đã render | Phải chuyển `outerHTML` từ WebView sang JVM (thêm IPC + parse jsoup) |
| Kiểm tra có bài viết không | Có `isProbablyReaderable(doc)` (minScore 20, minContentLength 140) | Không có |

Cách dùng: inject `Readability.js` + `Readability-readerable.js` + extractor qua `evaluateJavascript`. Chạy `new Readability(document.cloneNode(true), {serializer: el => el}).parse()`; phải clone vì Readability sửa DOM, và serializer dạng identity trả về element thay vì chuỗi HTML (README). Có thể ẩn nút Reader khi `isProbablyReaderable` trả false. Phần render native:
- Kết quả là một `LazyColumn` gồm các block `h`/`p`/`li`/`quote`/`pre` ở dạng plain text, không render HTML nên không cần DOMPurify.
- Cỡ chữ là `baseStyle.fontSize * (pct / 100f)`, slider từ 80 đến 200 với `steps = 11`. Font chọn giữa `FontFamily.Serif` và Google Sans Flex.
- Board 08 không có ảnh nên bản đầu chỉ cần text. Giữ `LazyListState` theo tab; WebView vẫn sống bên dưới nên khi thoát Reader thì vị trí cuộn của trang còn nguyên.

### A8. Lấy text trang cho AI và highlight đoạn N

Readability giữ lại thuộc tính `data-*`: nó chỉ xoá `class` và các thuộc tính trình bày, và `_setNodeTag` copy toàn bộ attributes. Vì vậy có thể đánh số các phần tử trên DOM sống trước khi clone:

```js
(() => {                                  // trả về object; evaluateJavascript nhận lại dạng JSON
  const SEL = 'p,li,h1,h2,h3,h4,h5,h6,blockquote,pre,figcaption'; let n = 0;
  document.body.querySelectorAll(SEL).forEach(e => e.setAttribute('data-skim-i', n++));
  const art = isProbablyReaderable(document)
      ? new Readability(document.cloneNode(true), { serializer: el => el }).parse() : null;
  const root = art ? art.content : document.body, blocks = [];
  root.querySelectorAll('[data-skim-i]').forEach(e => {
    if (e.querySelector('[data-skim-i]')) return;                // chỉ lấy block lá
    const text = (e.textContent || '').replace(/\s+/g, ' ').trim();
    if (text.length > 1) blocks.push({ id: e.getAttribute('data-skim-i'), tag: e.tagName.toLowerCase(), text });
  });
  return { title: art?.title || document.title, readerable: !!art, lang: document.documentElement.lang, blocks };
})()
```

```js
((id, cited) => {                          // chip "From paragraph k+1" → id = blocks[k].id
  let el = document.querySelector(`[data-skim-i="${id}"]`);
  if (!el && cited) el = [...document.querySelectorAll('[data-skim-i]')].find(e => e.textContent.includes(cited));
  if (!el) return false;
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  el.animate([{ backgroundColor: 'rgba(255,214,0,.45)' }, { backgroundColor: 'transparent' }], { duration: 2400 });
  return true;
})(ID, CITED)
```

- **Quy ước chỉ số:** `blocks[k]` là phần tử thứ k trong `source.content` của document; citation trả về `start_block_index = k`, và chip hiển thị số k + 1.
- **Giới hạn kích thước:** khoảng 400 block hoặc 100k ký tự.
- **Trạng thái "Not enough text" (06c):** đề xuất ngưỡng dưới 300 ký tự (heuristic).
- **Hardening (để sau):** androidx.webkit có `JavaScriptExecutionWorld` (feature `JS_INJECTION_IN_FRAME_AND_WORLD`) để tách script khỏi JS của trang. Bản đầu chạy ở main world cho đơn giản.

## B. Tích hợp hệ thống

### B9. Công cụ tìm kiếm

Lấy từ [Firefox search-config-v2](https://github.com/mozilla-firefox/firefox/blob/main/services/settings/dumps/main/search-config-v2.json), đã bỏ partner code. Ngày 2026-10-20 đã gọi thử cả 4 endpoint gợi ý: tất cả trả về định dạng OpenSearch Suggestions `["query", ["s1","s2",…]]`; riêng Google có thêm các mảng phụ phía sau, bỏ qua được.

| Engine | URL tìm kiếm | URL gợi ý | Content-Type |
|---|---|---|---|
| Google | `https://www.google.com/search?q={q}` | `https://www.google.com/complete/search?client=firefox&q={q}` | `text/javascript; charset=UTF-8` |
| Bing | `https://www.bing.com/search?q={q}` | `https://www.bing.com/osjson.aspx?query={q}&form=OSDJAS` | `application/json` |
| DuckDuckGo | `https://duckduckgo.com/?q={q}` | `https://ac.duckduckgo.com/ac/?q={q}&type=list` | `application/javascript` |
| Ecosia | `https://www.ecosia.org/search?q={q}` | `https://ac.ecosia.org/autocomplete?q={q}&type=list` | `application/json` |

Parse bằng charset ghi trong header, debounce khoảng 150 ms, và huỷ request cũ khi người dùng gõ tiếp. Các endpoint gợi ý không có tài liệu công khai cho bên thứ ba (xem bảng rủi ro).

### B10. Default browser, voice, import bookmarks, clipboard, share

- **Điều kiện cho ROLE_BROWSER:** `BrowserRoleBehavior` (AOSP) chỉ chấp nhận activity có `exported` và `handleAllWebDataURI`, resolve được intent `VIEW` + `BROWSABLE` + `http:` ([source](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/role-controller/java/com/android/role/controller/behavior/BrowserRoleBehavior.java)). Manifest cần:
  `<intent-filter><action VIEW/><category DEFAULT/><category BROWSABLE/><data scheme="http"/><data scheme="https"/></intent-filter>` (không giới hạn host). Thêm một filter `MAIN` + `CATEGORY_APP_BROWSER` để các app khác mở được "trình duyệt".
- **Luồng đặt default trên API 29+:** `registerForActivityResult(StartActivityForResult)` với `roleManager.createRequestRoleIntent(ROLE_BROWSER)`; kết quả `RESULT_OK` nghĩa là đã được cấp ([RoleManager](https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String))). Kiểm tra lại `isRoleHeld(ROLE_BROWSER)` trong `onResume`, vì người dùng có thể đổi trong Settings, rồi chuyển nút thành "Done" ở trạng thái disabled.
- **API 26–28:** mở `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (API 24, nhớ bắt `ActivityNotFoundException`). Để biết Skim đã là default chưa, kiểm tra `packageManager.resolveActivity(Intent(ACTION_VIEW, "https://example.com".toUri()), MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName`. Việc so với package của chính mình không cần khai báo `<queries>`.
- **Link từ app khác:** chỉ nhận `intent.data` khi scheme là http/https, bỏ qua mọi extra. Xử lý cả trong `onNewIntent`. Đánh dấu tab là "do app khác mở" để Back ở trang đầu thì đóng tab.
- **Panel Internet:** `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` có từ API 29. Trên API 26–28 dùng `Settings.ACTION_WIRELESS_SETTINGS`.
- **Nhập bằng giọng nói:** `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` + `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_WEB_SEARCH`; lấy `EXTRA_RESULTS[0]` rồi đưa sang 04b. Khai báo `<queries><intent><action android:name="android.speech.action.RECOGNIZE_SPEECH"/></intent></queries>` để `resolveActivity` biết có ứng dụng nhận giọng nói hay không; nếu không có thì ẩn nút mic.
- **Import bookmarks:** `OpenDocument().launch(arrayOf("text/html"))`, đọc stream qua `contentResolver` (đặt giới hạn khoảng 10 MB), parse trên `Dispatchers.Default`.
  - **Chọn Jsoup 1.23.2** (MIT, jar 538 KB; R8 sẽ shrink phần lớn) thay vì tự viết parser. Đã chạy thử trên một file export mẫu kiểu Chrome: Jsoup đặt `<DL>` con nằm trong `<DT>` của folder, decode được entity, và nhận ra doctype `netscape-bookmark-file-1`.
  - Nếu tự parse sẽ phải tự xử lý tag soup, entity và chữ hoa/thường. Tiết kiệm được khoảng 500 KB không đáng với rủi ro đó.

```kotlin
data class Bm(val title: String, val url: String? = null, val children: List<Bm> = emptyList())
fun parseNetscape(html: String): List<Bm>? {
    val doc = Jsoup.parse(html); val root = doc.selectFirst("dl") ?: return null
    fun walk(dl: Element): List<Bm> = dl.children().filter { it.tagName() == "dt" }.mapNotNull { dt ->
        dt.selectFirst("> a[href]")?.takeIf { it.attr("href").startsWith("http") }?.let { Bm(it.text(), it.attr("href")) }
            ?: dt.selectFirst("> h3")?.let { Bm(it.text(), children = dt.selectFirst("> dl")?.let(::walk).orEmpty()) }
    }
    return walk(root).takeIf { it.isNotEmpty() }        // null → "The file isn't a bookmarks export"
}
```

- **Clipboard:** từ Android 13 hệ thống tự hiện xác nhận khi copy, và docs khuyên bỏ toast/snackbar của app trên 13+ để tránh trùng ([copy-paste](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)). Vì vậy chỉ hiện snackbar khi `SDK_INT <= 32`.
- **Share:** `Intent(ACTION_SEND).setType("text/plain").putExtra(EXTRA_TEXT, url).putExtra(EXTRA_TITLE, title)` rồi `Intent.createChooser(it, null)`.
- **Share ảnh:** lấy bytes bằng OkHttp (đã có sẵn qua SDK) kèm cookie của profile của tab; ảnh `data:` thì decode base64. Ghi vào `cacheDir/shared/`, chia sẻ qua `FileProvider.getUriForFile` với `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION` và `ClipData`.

## C. Gọi Claude từ Android

### C11. SDK Java có dùng được trên Android không? Có.

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| Bản mới nhất | 2.73.0 (2026-10-15); repo 383 sao, release dày (2.71 → 2.73 trong một tuần) | `repo1.maven.org/.../anthropic-java/maven-metadata.xml`; GitHub releases |
| Bytecode / API Java | Class major 52 (Java 8). Không tham chiếu API Java 9+ nào, ngoại trừ `java.lang.ref.Cleaner` gọi qua reflection, có fallback khi không có. `java.nio.file`, `java.time`, `Stream`, `CompletableFuture` đều có từ API 24–26, nên không cần desugaring | Đã scan toàn bộ class trong core và okhttp jar |
| Dependency chạy lúc runtime | okhttp 4.12.0, jackson 2.19.4 (core/databind/kotlin/jsr310/jdk8), kotlin-reflect 1.9.0, opentelemetry-api 1.62.0, victools jsonschema 4.38.0, swagger-annotations, standardwebhooks | POM 2.73.0 |
| R8 | SDK có consumer rules trong `META-INF/proguard/anthropic-java-core.pro`, chủ yếu là `-if` để các model không dùng vẫn bị loại. Bản 2.69.0 đã sửa lỗi "requests failing under ProGuard and R8". **Thiếu** `-dontwarn com.google.auto.value.AutoValue` | Build probe thất bại cho đến khi thêm rule này |
| Kích thước | APK +1,32 MB (dex 2,9 MB) khi R8 tối ưu toàn bộ. Với `packageScope` hiện tại của dự án: 9,65 MB, 4 dex | So probe: APK baseline 16,8 KB |
| Chạy thực tế | Stream, citations, fallback, refusal và huỷ đều đúng sau R8 trên ART API 30 | Logcat + log của fake server |
| Issue liên quan Android | Không có issue Android nào trong repo. Repo anh em openai-java có issue #602 về kích thước SDK | GitHub search |

Lưu ý: không gọi `fromEnv()` trên Android vì nó đọc biến môi trường và file cấu hình. Client là singleton, và nên khởi tạo lazy ngoài main thread vì Jackson và kotlin-reflect tốn thời gian khởi tạo; thời gian cụ thể chưa đo. Header `X-Stainless-OS: Android` được gửi tự động.

### C12. Dùng từ Kotlin coroutines (đã biên dịch và chạy trong probe)

Hợp đồng HTTP mà proxy release cần biết (đo được trong probe):
- SDK gọi `{baseUrl}/v1/messages?beta=true` với header `anthropic-version: 2023-06-01`, `anthropic-beta: server-side-fallback-2026-07-01` và các header `X-Stainless-*`.
- Khi không set `apiKey` thì request **không có `x-api-key`** (`AnthropicBackend.applyCredentials` không gắn header nào).
- Một header tuỳ chỉnh như `X-Skim-Install` đi qua được. Proxy phải giữ nguyên query, chèn `x-api-key`, và không được buffer SSE.

```kotlin
fun skimClient(baseUrl: String, apiKey: String?, installToken: String?): AnthropicClient =
    AnthropicOkHttpClient.builder().baseUrl(baseUrl)
        .apply { if (apiKey != null) apiKey(apiKey) }                    // chỉ debug: BuildConfig từ local.properties
        .apply { if (installToken != null) putHeader("X-Skim-Install", installToken) }
        .timeout(Timeout.builder().connect(Duration.ofSeconds(15)).read(Duration.ofSeconds(60))
            .request(Duration.ofMinutes(5)).build())
        .build()

fun askParams(doc: BetaRequestDocumentBlock, history: List<BetaMessageParam>, q: String) =
    MessageCreateParams.builder()
        .model(Model.CLAUDE_OPUS_5_5).maxTokens(8192L)                    // thinking tính vào max_tokens
        .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacksDefault()
        .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
        .system(SKIM_SYSTEM_PROMPT)                                      // KHÔNG set thinking (disabled → 400)
        .addUserMessageOfBetaContentBlockParams(listOf(BetaContentBlockParam.ofDocument(doc),
            BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(FIRST_PROMPT).build())))
        .apply { history.forEach(::addMessage) }.addUserMessage(q).build()

fun AnthropicClient.streamAnswer(p: MessageCreateParams): Flow<AiEvent> = callbackFlow {
    val acc = BetaMessageAccumulator.create()                            // tin nhắn cuối → lịch sử chat
    val res = async().beta().messages().createStreaming(p)
    res.subscribe(object : AsyncStreamResponse.Handler<BetaRawMessageStreamEvent> {
        override fun onNext(e: BetaRawMessageStreamEvent) { acc.accumulate(e); e.toAiEvent()?.let { trySend(it) } }
        override fun onComplete(error: Optional<Throwable>) {
            error.orElse(null)?.let { close(it) } ?: run { trySend(AiEvent.Final(acc.message())); close() } }
    })
    awaitClose { res.close() }    // Stop → huỷ collector → OkHttp call.cancel() (đã thấy BrokenPipe phía server)
}

private fun BetaRawMessageStreamEvent.toAiEvent(): AiEvent? {
    contentBlockDelta().orElse(null)?.delta()?.let { d ->
        d.text().orElse(null)?.let { return AiEvent.Text(it.text()) }
        d.citations().orElse(null)?.citation()?.contentBlockLocation()?.orElse(null)
            ?.let { return AiEvent.Cite(it.startBlockIndex(), it.endBlockIndex(), it.citedText()) }
    }
    contentBlockStart().orElse(null)?.contentBlock()?.fallback()?.orElse(null)
        ?.let { return AiEvent.FellBack(it.to().model().asString()) }
    messageDelta().orElse(null)?.delta()?.stopReason()?.orElse(null)?.let { r ->
        val cat = messageDelta().get().delta().stopDetails().orElse(null)?.category()?.orElse(null)?.asString()
        return AiEvent.Done(r.asString(), if (r == BetaStopReason.REFUSAL) cat else null)
    }
    return null
}
```

- Không dùng `Optional.stream()` như các ví dụ trong docs Java vì đó là API Java 9, chỉ có từ Android API 33; dùng `orElse(null)`.
- **Phân loại kết quả và lỗi:**
  - `AnthropicIoException` → trạng thái "No connection" (06c).
  - `RateLimitException`, `InternalServerException`, `SseException` → lỗi server (chưa có thiết kế).
  - `stop_reason == refusal`: bỏ phần text đang hiển thị dở và hiện trạng thái từ chối, vì docs yêu cầu coi output một phần là chưa hoàn chỉnh. Phải dựa vào `stop_reason`, không dựa vào `stop_details.category` vì trường này có thể null.
  - Sự kiện `FellBack` giữa chừng: giữ text đã nhận, vì docs nói phần đã nhận không bị vô hiệu ([refusals-and-fallback](https://platform.claude.com/docs/en/build-with-claude/refusals-and-fallback)).
- **Opus 5.5 (đã xác minh):** adaptive thinking luôn bật; `thinking: disabled` trả 400. Effort mặc định là `medium`, nên gửi `medium` cho kết quả giống như bỏ trống; vẫn nên gửi tường minh để mỗi request tự mang cấu hình. Thinking mặc định `display: "omitted"` nên không có thinking text nào được stream, và time-to-first-text nhanh hơn ([effort](https://platform.claude.com/docs/en/build-with-claude/effort), [thinking](https://platform.claude.com/docs/en/build-with-claude/thinking)).
- **Lịch sử chat:** dùng `addMessage(BetaMessage)` từ accumulator. Cách này giữ nguyên block thinking, citation và vị trí block `fallback`, đúng như docs yêu cầu khi gửi lại hội thoại.
- **Chốt chặn incognito:** đặt một chỗ duy nhất trong code: `require(!tab.incognito)`, đi kèm kiểm tra quyền theo site (06d), trước khi build params.

### C13. Citations trên custom content và prompt caching

- **Định dạng document:** `source: {type:"content", content:[text block cho từng đoạn]}`. API không tách nhỏ thêm, và citation trả `content_block_location` với `start_block_index` đánh từ 0, `end_block_index` loại trừ ([citations](https://platform.claude.com/docs/en/build-with-claude/citations)).
- **Khi stream:** mỗi `citations_delta` mang đúng một citation. Kiểu Java tương ứng là `BetaCitationsDelta.citation().contentBlockLocation()`; probe đã nhận đúng `start=1, end=2`. `cited_text` không tính vào output token.
- **Dựng document** (đã xác minh trên wire):
  `BetaRequestDocumentBlock.builder().source(BetaContentBlockSource.builder().contentOfBetaContentBlockSource(blocks.map { BetaContentBlockSourceContent.ofText(it.text) }).build()).title(title).context(url).citations(BetaCitationsConfigParam.builder().enabled(true).build()).cacheControl(BetaCacheControlEphemeral.builder().build())`.
- **Prompt caching:**
  - Đặt `cache_control` trên document block. Mức tối thiểu để cache trên Opus 5.5 là **512 token**; dưới mức đó API không báo lỗi mà chỉ không cache.
  - TTL mặc định 5 phút. Giá Opus 5.5: ghi cache $5/MTok, đọc cache $0,20/MTok (0,05×), input thường $4/MTok ([prompt-caching](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)).
  - Câu hỏi tiếp theo giữ document ở user message đầu tiên và chỉ nối thêm message mới, nên prefix (system → document) trùng byte và được đọc từ cache. Lookback 20 block chỉ có ý nghĩa khi hội thoại rất dài.
  - Cách kiểm tra: xem `usage.cache_read_input_tokens` của các lượt sau.

### C14. Structured output: gom nhóm tab và tóm tắt nhiều tab

- **Cách gửi:** `output_config.format = {type:"json_schema", schema}`. Mọi object phải có `additionalProperties: false`; không hỗ trợ schema đệ quy. Giới hạn: tối đa 24 tham số optional và 16 tham số dạng union cho mỗi request. Nếu bị refusal hoặc `max_tokens` thì JSON có thể không đúng schema, nên phải kiểm tra `stop_reason` trước khi parse. Enum có thể bị sai hoa/thường ([structured-outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)).
- **Không bật citations trong các request này.**
- **Gom nhóm tab (banner trên 09):** chỉ gửi `{id, title, host}` của các tab không phải incognito, effort `low`, schema `{groups:[{name, tabIds[]}]}`.
- **Tóm tắt nhiều tab (06b):** schema `{intro, groups:[{name, summary, tabIds[]}], followUps[]}`. Đầu vào là các đoạn text trích ra (cắt khoảng 4k ký tự mỗi tab), dạng text thường, không bật citations. Vẫn stream, nhưng render khi đã nhận đủ JSON.

```kotlin
val schema = BetaJsonOutputFormat.Schema.builder()
    .putAdditionalProperty("type", JsonValue.from("object"))
    .putAdditionalProperty("properties", JsonValue.from(mapOf("groups" to mapOf("type" to "array",
        "items" to mapOf("type" to "object", "additionalProperties" to false, "required" to listOf("name", "tabIds"),
            "properties" to mapOf("name" to mapOf("type" to "string"),
                "tabIds" to mapOf("type" to "array", "items" to mapOf("type" to "string"))))))))
    .putAdditionalProperty("required", JsonValue.from(listOf("groups")))
    .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build()
// .outputConfig(BetaOutputConfig.builder().effort(LOW).format(BetaJsonOutputFormat.builder().schema(schema).build()).build())
```

Không dùng helper `outputConfig(MyClass::class.java)`: helper này sinh schema bằng reflection qua victools, và R8 sẽ đổi tên field. Viết schema bằng tay như trên, sau đó parse bằng Jackson (đã có sẵn) hoặc `org.json`.

## Danh sách dependency đã xác minh (2026-10-20)

| Artifact | Phiên bản | Nguồn |
|---|---|---|
| `com.anthropic:anthropic-java` | **2.73.0** | repo1.maven.org/maven2/com/anthropic/anthropic-java/maven-metadata.xml |
| `androidx.webkit:webkit` | **1.17.1** (rc: 1.18.0-rc01) | dl.google.com/dl/android/maven2/androidx/webkit/webkit/maven-metadata.xml |
| `androidx.compose.material3:material3` | 1.5.0-beta01 (theo design README) | dl.google.com …/material3/maven-metadata.xml |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | repo1 maven-metadata |
| `org.jsoup:jsoup` | 1.23.2 (MIT) | repo1 maven-metadata, POM |
| `androidx.activity:activity-compose` | 1.13.0 (stable) | dl.google.com maven-metadata |
| `androidx.core:core-ktx` | 1.19.1 (dự án đã có) | dl.google.com maven-metadata |
| Readability.js + Readability-readerable.js | 0.6.0 (Apache-2.0), đóng gói làm asset | registry.npmjs.org/@mozilla/readability |
| Transitive qua SDK | okhttp 4.12.0, jackson 2.19.4, kotlin-reflect 1.9.0, opentelemetry-api 1.62.0 | POM anthropic-java-core/client-okhttp 2.73.0 |

Màn hình license (AboutLibraries) cần liệt kê cả các thư viện transitive ở trên.

## Bảng rủi ro

| Rủi ro | Dấu hiệu hỏng | Phương án dự phòng |
|---|---|---|
| WebView trên máy không hỗ trợ `MULTI_PROFILE` | `isFeatureSupported` trả false | Tắt incognito và nhắc cập nhật WebView; không dùng chung profile mặc định |
| Không xoá được profile incognito trong process | `IllegalStateException` từ `deleteProfile` | Mỗi phiên dùng tên mới; xoá cookie/storage ngay; purge profile cũ khi khởi động lại |
| Renderer bị OOM hoặc crash | `onRenderProcessGone` | Destroy các WebView bị ảnh hưởng, khôi phục từ file state, giảm N |
| State tab quá lớn | Crash `TransactionTooLargeException` | Lưu state ra file, đặt `maxSizeBytes`, không dùng `savedInstanceState` cho WebView |
| State cũ không tương thích sau khi cập nhật WebView/OS | `restoreState` trả null | `loadUrl(tab.url)` |
| Pull to refresh kích hoạt nhầm trên trang tự bắt cử chỉ | Trang reload khi người dùng kéo bản đồ ở đầu trang | Chỉ bắt đầu kéo sau `onOverScrolled(clampedY)`; nếu vẫn lỗi thì chuyển sang SwipeRefreshLayout |
| R8 thiếu class do dependency transitive | Build fail với "Missing class" | Copy rule từ `missing_rules.txt` |
| SDK không được shrink vì `packageScope` | APK > 9 MB, có 4 file dex | Bỏ `packageScope` hoặc thêm `com.anthropic.**`, `com.fasterxml.**`, `okhttp3.**`, `okio.**`, `io.opentelemetry.**` |
| Lỗi reflection chỉ xuất hiện ở bản release | `AnthropicInvalidDataException` hoặc lỗi JSON chỉ trong release | Instrumented test chạy bản release với fake SSE server (giống probe) |
| Xung đột phiên bản Jackson/OkHttp do thư viện khác kéo vào | SDK ném lỗi version check của Jackson; `NoSuchMethodError` | Pin phiên bản; kiểm tra bằng `./gradlew :app:dependencies` |
| Khởi tạo client gây giật UI | Rớt frame ở lần dùng AI đầu tiên | Tạo singleton trên `Dispatchers.Default` lúc app rảnh |
| Refusal hoặc fallback | `stop_reason: refusal`; sự kiện `FellBack` | Có UI cho trạng thái từ chối; đo riêng số refusal và số lần fallback |
| Gửi citations kèm structured output | HTTP 400 | Tách thành hai loại request |
| Cache không trúng | `cache_read_input_tokens == 0` ở lượt sau | Giữ document y hệt từng byte; chấp nhận khi trang ngắn dưới 512 token |
| Key debug lọt vào bản release | CI tìm thấy `sk-ant-` trong APK release | Chỉ khai báo `buildConfigField` cho debug (cần `buildFeatures.buildConfig = true`) |
| Endpoint gợi ý tìm kiếm thay đổi hoặc bị chặn | HTTP 4xx hoặc mảng rỗng | Ẩn gợi ý từ engine, vẫn giữ gợi ý từ history và bookmarks |
| Lạm dụng link `intent:` | Mở app mà không có thao tác người dùng | Kiểm tra `hasGesture`, thêm BROWSABLE, xoá component/selector và các grant flag |
| Highlight trỏ sai đoạn (SPA render lại DOM) | `querySelector` trả null | Tìm theo `cited_text`; nếu không thấy thì chỉ cuộn tới đầu trang |
| Download `blob:`/`data:` | `DownloadManager.Request` ném `IllegalArgumentException` | Báo lỗi; hoặc làm cầu nối JS fetch → base64 (xem câu hỏi mở) |

## Giới hạn của nghiên cứu này

- Probe runtime chạy trên **API 30**, không phải API 26 (máy không có image API 26). Kết luận cho API 26 dựa trên việc scan bytecode.
- `SkimWebView` cho pull to refresh, thumbnail bằng software draw, và việc bật FLAG_SECURE đúng lúc đều **chưa chạy trên thiết bị**. Chúng là bản phác dựa trên source và docs.
- Chưa đo thời gian khởi tạo client SDK, và chưa đo bộ nhớ khi có N WebView sống.
- Không xác định được phiên bản WebView tối thiểu cho `MULTI_PROFILE`.
- Không gọi API thật của Anthropic (không dùng key); hành vi server được giả lập theo docs. Hình dạng request và response được kiểm chứng bằng fake server, không phải bằng server thật.

## Câu hỏi chưa giải quyết

1. Có sửa `optimization.packageScope` trong `app/build.gradle.kts` không (bỏ đi, hay thêm các package của SDK)? Đây là quyết định cấu hình của dự án, chưa được chốt.
2. Khi process bị kill, có khôi phục tab incognito không? Chrome thì không; nếu Skim cũng không thì bỏ qua lưu state cho incognito.
3. Quyền "Ask before sending" theo từng site (06d) có áp dụng cho 06b (đọc tất cả tab) không, hay chỉ gửi những tab thuộc site đã được cho phép?
4. Chat của tab gắn theo tab hay theo từng trang (khi người dùng điều hướng sang trang khác trong cùng tab)?
5. Download trong tab incognito sẽ thành file công khai trong Downloads (giống Chrome). Có chấp nhận, hay cần cảnh báo?
6. Có hỗ trợ download `blob:`/`data:` ở bản đầu không?
7. Proxy release xác thực app thế nào: một install token trong `X-Skim-Install`, hay Play Integrity? Việc này ảnh hưởng tới header client cần gửi.
