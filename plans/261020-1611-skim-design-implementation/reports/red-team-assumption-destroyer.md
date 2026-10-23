# Red team: Assumption Destroyer + Scope Auditor (vòng đời state)

Ngày 2026-10-21. Đối tượng: `plans/261020-1611-skim-design-implementation/` (plan.md, coverage-matrix.md, phase 01–13). Quy ước trích dẫn: `P/` = `plans/261020-1611-skim-design-implementation/`, `R-B` = `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md`, `R-A` = `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md`, `SC` = `plans/reports/scout-261020-2319-design-inventory.md`, `NM` = `design/spec/navigation-map.md`.

Kết luận chính: plan đặt nhiều state sống theo process (TabManager, SnackbarController, incomingLinks, ChatSessionStore, PageDigestCache) cạnh state sống theo Activity hoặc composition (WebViewPool, WindowSecureController, SitePermissionBridge, SkimNavigator, back stack), nhưng không định nghĩa điểm cắt giữa chúng. Hậu quả nặng nhất rơi vào incognito: phiên incognito có thể không bao giờ kết thúc, cookie của phiên lọt sang DownloadProvider của hệ thống, và quyền site của phiên trước sống sang phiên sau. Ba trong năm tiêu chí chấp nhận toàn plan (`plan.md:95-97`) có điều kiện không thể đạt như đang viết.

## Finding 1: Kết thúc phiên incognito bị buộc vào snackbar Undo, nên "đóng mọi tab incognito là kết thúc phiên" không đúng
- **Severity:** Critical
- **Location:** Phase 4, mục "Luồng dữ liệu" bước 5; Phase 9, mục "Key insights" và "Architecture"
- **Flaw:** `IncognitoSession.end()` chỉ chạy trong `finalizeClose`, tức khi snackbar "Tab closed" hết giờ, và chỉ khi lúc đó không còn tab incognito nào. Trong 10 giây Undo, phiên vẫn được coi là đang sống, và `ensureStarted()` trả lại đúng profile cũ cho mọi tab incognito mới.
- **Failure scenario:** Người dùng đóng tab incognito cuối cùng ở 09b (snackbar Undo hiện), rồi bấm FAB "New incognito tab" trong vòng 10 giây. Tab mới gọi `setProfile(ensureStarted())` và nhận profile `incognito-<ts>` cũ, nên cookie và trạng thái đăng nhập của "phiên đã đóng" vẫn còn. Khi snackbar hết giờ, `finalizeClose` thấy vẫn còn một tab incognito nên bỏ qua `end()`. Phiên cũ kéo dài vô thời hạn. Điều này trái copy 09c "Closing every incognito tab ends the session" và rule "deleted with the last tab". Test của phase 4 khẳng định `end()` "chỉ sau `finalizeClose`", nên test xanh đúng ở kịch bản gây lỗi.
- **Evidence:**
  - `P/phase-04-browser-engine.md:133`: "`finalizeClose` (snackbar hết hạn) xoá Room/state/thumbnail; nếu không còn tab incognito → `IncognitoSession.end()`."
  - `P/phase-04-browser-engine.md:130`: "(incognito: `setProfile(ensureStarted())` trước tiên)"
  - `P/phase-04-browser-engine.md:186`: "đóng tab incognito cuối cùng gọi `IncognitoSession.end()` chỉ sau `finalizeClose`."
  - `P/phase-09-tabs.md:60`: "`snackbar.show(text, Run(action_undo) { tabs.undoClose(token) }, onDismissed = { tabs.finalizeClose(token) })`"
  - `SC:405`: "Bookmarks and downloads you make here are kept. Closing every incognito tab ends the session."
  - `NM:247`: "Last incognito tab closes | Profile deleted: cookies, cache, history"
- **Suggested fix:** Tách "phiên logic" khỏi "Undo của tab". Khi tab incognito cuối đóng, đánh dấu phiên ở trạng thái `Ending`. Tab incognito mới trong trạng thái này phải mở phiên mới (tên profile mới) và finalize ngay token cũ, hoặc bỏ Undo cho tab incognito cuối giống "Close all incognito". Thêm test: đóng tab incognito cuối → mở tab incognito mới trước khi hết giờ → profile name phải khác.

## Finding 2: `restore()` gọi `ProfileStore` trên `Dispatchers.Default`, và dữ liệu incognito trên đĩa sống lâu hơn phiên
- **Severity:** High
- **Location:** Phase 1, mục "Architecture" (Khởi động); Phase 4, mục "Luồng dữ liệu" bước 1, "Phi chức năng", Implementation step 3
- **Flaw:** (a) `appScope` dùng `Dispatchers.Default`, và `SkimApplication` chạy `appScope.launch { tabManager.restore() }`. `restore()` gọi `IncognitoSession.purgeStale()`, tức `ProfileStore.getInstance().allProfileNames`/`deleteProfile`. Trong `webkit-1.17.1.aar`, class `androidx.webkit.ProfileStore` mang `@UiThread` ở cấp class (đã kiểm bằng `javap`). Chính plan cũng ghi "Mọi thao tác WebView/ProfileStore trên main thread". (b) `end()` chỉ xoá cookie, web storage và geolocation. HTTP cache của profile chỉ bị chặn khi có `HTTP_CACHE_MANAGER`, mà feature này cần WebView M151+. Profile nằm trên đĩa và chỉ bị xoá ở lần khởi động lạnh sau.
- **Failure scenario:** Ở mọi lần khởi động lạnh, lời gọi ProfileStore off-main hoặc ném lỗi trong coroutine của `SupervisorJob` (không có `CoroutineExceptionHandler` nên process crash), hoặc chạy đua với UI thread trong Chromium. Nếu `restore()` không hoàn tất, `SkimApp` "chưa xong thì chỉ vẽ Surface" và treo ở màn trống. Kể cả khi thread đúng, trên WebView có `MULTI_PROFILE` nhưng dưới M151, cache ảnh/trang của phiên incognito vẫn nằm trong `app_webview/` sau khi đóng tab cuối. Sau process death (D2), toàn bộ cookie của phiên còn trên đĩa cho tới lần mở app sau, có thể là vài ngày. Copy "Profile deleted: cookies, cache" vì vậy không đúng trong khoảng đó.
- **Evidence:**
  - `P/phase-01-build-foundation-theme-resources.md:95`: "`appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`"
  - `P/phase-04-browser-engine.md:151`: "`appScope.launch { container.tabManager.restore() }`"
  - `P/phase-04-browser-engine.md:129`: "`tabManager.restore()` đọc Room … `IncognitoSession.purgeStale()`."
  - `P/phase-04-browser-engine.md:52`: "Mọi thao tác WebView/`ProfileStore` trên main thread"
  - `R-B:58`: "// Tên profile mới cho mỗi phiên incognito; chỉ chạy trên UI thread"
  - `P/phase-04-browser-engine.md:41`: "xoá cookie/web storage/geolocation của profile" (không có cache)
  - `P/phase-04-browser-engine.md:173`: "`httpCache.setQuotaBytes(0)` khi có `HTTP_CACHE_MANAGER`"
  - `R-B:53`: "`httpCache` (feature `HTTP_CACHE_MANAGER`, cần WebView M151 trở lên)"
  - `R-B:11`: "Profile "incognito" của androidx.webkit được lưu trên đĩa và không xoá được trong cùng process"
  - `P/phase-05-app-shell-navigation-onboarding.md:85`: "Chờ settings + `tabManager.restore()` xong (produceState); chưa xong thì chỉ vẽ Surface."
- **Suggested fix:** Tách `restore()` thành phần Room/IO (trên `Dispatchers.IO`) và phần profile (`withContext(Dispatchers.Main)`), có `CoroutineExceptionHandler` và trạng thái lỗi để `SkimApp` không treo. Khi không có `HTTP_CACHE_MANAGER`: hoặc tắt incognito, hoặc gọi `clearCache(true)` trên một WebView của profile trước khi destroy. Ghi rõ trong docs rằng dữ liệu phiên còn trên đĩa sau process death cho tới lần khởi động kế tiếp.

## Finding 3: `incomingLinks` sống theo process với `replay = 1` sẽ phát lại link cũ cho mỗi Activity mới
- **Severity:** High
- **Location:** Phase 5, mục "Trạng thái app" bước 7 và "Related code files" (Sửa `AppContainer`, `MainActivity`)
- **Flaw:** Flow nằm trong `AppContainer` (process) với `replay = 1`, nhưng người collect là `LaunchedEffect` trong `SkimApp` (composition của từng Activity). Mỗi composition mới đều nhận lại URL ngoài gần nhất. Plan không có bước "tiêu thụ" giá trị.
- **Failure scenario:** Trên API 26–30, Back ở tab không chặn Back sẽ finish Activity mà process vẫn sống. Người dùng mở lại từ launcher: `SkimApp` collect, nhận URL replay, rồi gọi `tabs.newTab(url, openedByExternalApp = true)`. Kết quả là một tab trùng của link từ app khác (có thể từ vài giờ trước) mở ra. Bấm Back trên tab đó gọi `moveTaskToBack(true)`, nên app tự biến mất. Cùng lỗi xảy ra với mọi lần tạo lại Activity: đổi "Bold text" (`fontWeightAdjustment` không có trong `configChanges`), hoặc `ActivityScenario.recreate()` ngay trong `BackStackRestoreTest`.
- **Evidence:**
  - `P/phase-05-app-shell-navigation-onboarding.md:142`: "`incomingLinks: MutableSharedFlow<String>(replay = 1)`"
  - `P/phase-05-app-shell-navigation-onboarding.md:91`: "LaunchedEffect: collect incomingLinks → tabs.newTab(url, openedByExternalApp = true) → navigator.showBrowser()"
  - `P/phase-05-app-shell-navigation-onboarding.md:103`: "tab `openedByExternalApp` → đóng tab ngay (không Undo) + `activity.moveTaskToBack(true)`; … còn lại không chặn (Back rời app)"
  - `P/phase-01-build-foundation-theme-resources.md:106`: danh sách `configChanges` không có `fontWeightAdjustment`, `colorMode`, `grammaticalGender`
  - `P/phase-05-app-shell-navigation-onboarding.md:156`: "`BackStackRestoreTest` (`ActivityScenario.recreate()` …)"
- **Suggested fix:** Dùng `Channel<String>(BUFFERED)` hoặc `MutableStateFlow<String?>` có `consume()` để reset về null sau khi mở tab. Thêm test: recreate Activity sau một VIEW intent thì số tab không đổi.

## Finding 4: Back stack khôi phục sau process death trỏ tới tab đã bị D2 hoặc purge soft-close xoá
- **Severity:** High
- **Location:** Phase 5, mục "Requirements" (Khởi động, key có `tabId`) và "Architecture"; Phase 4, D2; Phase 3, soft close
- **Flaw:** Sheet và màn hình mang `tabId` trong key (`MoreMenuKey`, `SiteInfoKey`, `LinkOptionsKey(tabId, HitTarget)`, `ReaderKey`, `AiPermissionKey`, `TabsKey(incognito = true)`), và back stack khôi phục được "ưu tiên hơn back stack khởi tạo". Trong khi đó D2 bỏ mọi tab incognito và `loadOpen()` xoá tab có `closedAt != null`. Không phase nào đối soát key với tập tab đã khôi phục.
- **Failure scenario:** Người dùng đang ở tab incognito, mở 05d trên một link, rồi chuyển app; hệ thống kill process. Khi mở lại, back stack có `LinkOptionsKey(tabId = <incognito đã mất>, HitTarget(url = <link incognito>))`. Entry tìm tab không có: crash nếu dùng `first {}`, hoặc hiện sheet rỗng không đóng được. URL incognito cũng đã nằm trong saved-state bundle qua process death, trái tinh thần D2. `TabsKey(incognito = true)` khôi phục sẽ cho 09b tối, trống, có FLAG_SECURE. Checklist process death của phase 13 không có kịch bản sheet mở trên tab incognito, nên lỗi lọt qua.
- **Evidence:**
  - `P/phase-05-app-shell-navigation-onboarding.md:39`: "Back stack khôi phục sau process death được ưu tiên hơn back stack khởi tạo."
  - `P/phase-05-app-shell-navigation-onboarding.md:147`: "encode/decode mọi key (kể cả `LinkOptionsKey` có `HitTarget`)"
  - `P/phase-04-browser-engine.md:40`: "D2: sau khi process chết, tab incognito không được khôi phục"
  - `P/phase-03-data-layer.md:28`: "xoá hẳn các dòng `closedAt != null` khi khởi động"
  - `P/phase-13-verification-hardening-docs.md:132`: checklist chỉ mở sheet trên tab thường
- **Suggested fix:** Sau `restore()`, lọc back stack: bỏ mọi key có `tabId` không còn tồn tại, và đổi `TabsKey(true)` thành `TabsKey(false)` khi không còn tab incognito. Không đưa dữ liệu của tab incognito (URL trong `HitTarget`) vào key được lưu: dùng UI state không saveable cho sheet của tab incognito. Thêm test round-trip "kill khi 05b/05d mở trên tab incognito".

## Finding 5: Reader và AI sau process death cần một WebView mà chỉ `WebViewHost` dưới `BrowserKey` tạo ra
- **Severity:** High
- **Location:** Phase 10, "Key insights" và Implementation step 1; Phase 11, "Requirements" (process death) và "Luồng dữ liệu"; Phase 4, "Luồng dữ liệu" bước 2 và 4
- **Flaw:** WebView chỉ được tạo khi `WebViewHost(tabId)` vào composition. `ReaderKey` là màn hình riêng nên khi nó ở đỉnh, `BrowserKey` không compose và WebView không tồn tại. `ReaderViewModel` gọi `extractPage` đúng một lần, và kết quả null đưa tới `navigator.back()`. Với 06 khôi phục (overlay trên `BrowserKey`), WebView vừa được tạo và đang `restoreState ?: loadUrl`; `extractPage` không chờ `PageFinished`.
- **Failure scenario:** Kịch bản trong checklist phase 13 ("một tab đang ở Reader cuộn giữa bài" → `am kill` → mở lại) cho kết quả Reader tự đóng ngay, và vị trí cuộn Reader mất. Tiêu chí "process death khôi phục … cuộn, sheet" vì vậy đỏ. Với 06 đang mở trên một bài dài, extractor chạy trên DOM chưa tải nên trả < 300 ký tự. Kết quả là UI hiện "Not enough text", hoặc chat đã lưu được replay với document rỗng, khiến câu trả lời tiếp theo không có căn cứ.
- **Evidence:**
  - `P/phase-04-browser-engine.md:130`: "Hiển thị tab: `WebViewHost(tabId)` → `pool.obtain(tab)` → … `restoreState` ?: `loadUrl(url)`"
  - `P/phase-04-browser-engine.md:132`: "khi chưa attach, lệnh `load` chỉ cập nhật `tab.url`"
  - `P/phase-10-reader-view.md:29`: "WebView của tab vẫn sống trong pool khi Reader che lên"
  - `P/phase-10-reader-view.md:81`: "`extractPage` một lần khi tạo; `readerable == false` hoặc `null` → `Unavailable` (route gọi `navigator.back()`)"
  - `P/phase-11-skim-ai-core.md:112`: "`sessions.openForTab(tab, tabs.extractPage(tabId))`"
  - `P/phase-11-skim-ai-core.md:33`: "lượt assistant cũ được replay dạng text … với document trích lại từ trang"
  - `P/phase-13-verification-hardening-docs.md:39` và `:132`: yêu cầu khôi phục "vị trí cuộn của trang và Reader"
  - `plan.md:97`: "process death khôi phục tab, cuộn, sheet, chat"
- **Suggested fix:** Thêm `TabManager.ensureLoaded(tabId)`: tạo WebView headless trong pool nếu cần, rồi suspend tới `PageFinished` có timeout. `extractPage` dùng hàm này. Ngoài ra, lưu `ReaderArticle` của phiên (tab thường) vào cache `filesDir` để Reader khôi phục không phụ thuộc WebView.

## Finding 6: Download và share ảnh từ tab incognito đẩy cookie và nội dung của phiên ra ngoài ranh giới incognito
- **Severity:** High
- **Location:** Phase 4, D5 và "Security considerations"; Phase 7, "Requirements" (Share ảnh) và "Phi chức năng"
- **Flaw:** (a) D5 gửi download http/https qua `DownloadManager` kèm header `Cookie` lấy từ profile incognito. DownloadProvider của hệ thống lưu request header vào DB riêng của nó (bảng request headers), ngoài tầm `IncognitoSession.end()`. (b) Share ảnh từ tab incognito ghi file vào `cacheDir/shared/`, và file chỉ bị dọn "cũ hơn 1 ngày … lúc khởi động". Cả hai mâu thuẫn với "Tab incognito không bao giờ ghi đĩa" và "Profile deleted: cookies". Copy 09c chỉ chấp nhận giữ *file đã tải*, không chấp nhận giữ cookie của phiên.
- **Failure scenario:** Người dùng tải một hoá đơn PDF trong phiên incognito đã đăng nhập ngân hàng. Cookie phiên nằm trong `downloads.db` của DownloadProvider cho tới khi bản ghi download bị xoá thủ công; xoá profile không chạm tới nó. Ảnh chia sẻ từ incognito nằm trong cache của app tới lần khởi động lạnh sau 24 giờ, hoặc lâu hơn nếu process cứ sống.
- **Evidence:**
  - `P/phase-04-browser-engine.md:45`: "D5: tải xuống http/https qua `DownloadManager` (cookie của profile tab + User-Agent, thông báo hệ thống)"
  - `P/phase-04-browser-engine.md:246`: "cookie tải xuống lấy từ cookie manager của đúng profile"
  - `R-B:97`: "`DownloadManager.Request` kèm header `Cookie` (lấy từ cookie manager của profile)"
  - `P/phase-04-browser-engine.md:39`: "Tab incognito không bao giờ ghi đĩa (state + thumbnail chỉ trong bộ nhớ)."
  - `P/phase-07-browsing-surfaces.md:50`: share ảnh "ghi `cacheDir/shared/`"; `:54`: "file trong `cacheDir/shared/` cũ hơn 1 ngày bị xoá lúc khởi động"
  - `NM:247`: "Profile deleted: cookies, cache, history"
- **Suggested fix:** Với tab incognito, tải bằng OkHttp trong process (cookie chỉ trong bộ nhớ) rồi ghi MediaStore Downloads. Đường MediaStore cho `data:`/`blob:` đã có sẵn, nên chỉ cần dùng chung. Ảnh share từ incognito: xoá file ngay khi `IncognitoSession.end()` chạy, hoặc dùng thư mục `shared/incognito/` bị dọn trong `end()` và `purgeStale()`.

## Finding 7: `SnackbarController` sống theo process giữ lambda bắt `SkimNavigator` (composition) và `WindowSecureController` (Activity)
- **Severity:** High
- **Location:** Phase 2, mục "Architecture" (`SnackbarController`); Phase 5, "Luồng dữ liệu onboarding"; Phase 7, "Luồng dữ liệu bookmark" và step 6; Phase 11, "Sửa `SkimApp.kt`"
- **Flaw:** Controller nằm trong `AppContainer`, và `current: StateFlow<Message?>` giữ `Action.Run(block)` cùng `onDismissed`. Các block này bắt `navigator`. `navigator` lại giữ `NavBackStack` (lấy từ `rememberNavBackStack`), `rememberCoroutineScope()`, và `WindowSecureController` do `MainActivity` tạo. Hợp đồng `onDismissed` liệt kê "hết giờ, bị vuốt, bị thay", nhưng không nói gì khi host rời composition và `showSnackbar` bị huỷ.
- **Failure scenario:** Từ 05d, người dùng chọn "Open in new tab" và snackbar "Switch" hiện. Activity bị tạo lại, chẳng hạn vì đổi Bold text hoặc vì finish rồi mở lại trên API ≤ 30. Activity cũ và composition cũ bị giữ qua lambda (leak). Host mới hiện lại message cũ; bấm Switch gọi `showTab` trên back stack đã chết và `setSecure` trên window đã huỷ. Chỉ phần `tabs.select` chạy được, nên UI không chuyển. Nếu host bị huỷ mà controller coi như đã tiêu thụ message, `finalizeClose` không bao giờ chạy cho token đó. Rủi ro này nhân với Finding 1 khi token là tab incognito cuối.
- **Evidence:**
  - `P/phase-02-design-system-components.md:64`: "`class SnackbarController (app-scoped, tạo ở phase 5)`"
  - `P/phase-02-design-system-components.md:72-73`: "`onDismissed` … gọi khi hết giờ, bị vuốt hoặc bị message mới thay"
  - `P/phase-05-app-shell-navigation-onboarding.md:64-65`: `SkimNavigator(private val backStack: NavBackStack<NavKey>, …, private val secure: WindowSecureController, private val snackbar: SnackbarController)`
  - `P/phase-05-app-shell-navigation-onboarding.md:116`: "`Action.Run(action_view) { navigator.openBookmarks(folderId) }`"
  - `P/phase-07-browsing-surfaces.md:92`: "`Run(action_edit) { navigator.openDialog(EditBookmarkKey(id)) }`"
  - `P/phase-11-skim-ai-core.md:125`: "truyền `aiGate` và `rememberCoroutineScope()` khi tạo navigator"
  - `P/phase-04-browser-engine.md:152`: `MainActivity` "tạo `WebViewPool`, `WindowSecureController`, …"
  - `P/phase-09-tabs.md:124`: rủi ro "`onDismissed` luôn được gọi" chỉ được xử lý cho `isFinishing`
- **Suggested fix:** Đưa action về dạng dữ liệu (`sealed interface SnackbarIntent { SwitchToTab(id), OpenBookmarks(folderId), Undo(token) … }`) để host hiện tại dịch sang lời gọi navigator của chính nó. Hoặc đặt `SnackbarController` trong `ViewModelStore` của Activity và xoá `current` khi Activity bị huỷ, đồng thời gọi `onDismissed` cho mọi token đang chờ. Bổ sung "host rời composition" vào hợp đồng `onDismissed` và viết test cho nó.

## Finding 8: `SitePermissionBridge` sống theo Activity nhưng giữ quyết định của phiên incognito, vốn sống theo process
- **Severity:** Medium
- **Location:** Phase 4, Requirements (Quyền site), step 11, "Sửa MainActivity"; Phase 7, step 7
- **Flaw:** Quyết định của tab incognito được "nhớ phiên" trong bridge, nhưng bridge được tạo trong `MainActivity`. Không có hook nào xoá các quyết định này khi `IncognitoSession.end()` chạy (grep cả plan không có). `SiteInfoSheet` (một NavEntry ViewModel) cần đọc "state phiên incognito", nhưng `AppContainer` không có đường nào tới object của Activity.
- **Failure scenario:** Trong phiên incognito A, người dùng cho `meet.example` dùng camera và mic. Đóng hết tab incognito sẽ kết thúc phiên (cookie bị xoá). Mở phiên incognito B trong cùng Activity: bridge vẫn nhớ "ALLOWED" nên site được grant camera không hỏi. Ngược lại, khi Activity bị tạo lại giữa phiên (tab incognito vẫn sống trong `TabManager`), mọi quyết định bị quên và người dùng bị hỏi lại. Sheet Site information của tab incognito không có nguồn dữ liệu xác định.
- **Evidence:**
  - `P/phase-04-browser-engine.md:47`: "Quyết định tab thường lưu `SitePermissionRepository`; tab incognito chỉ trong bộ nhớ phiên."
  - `P/phase-04-browser-engine.md:181`: "`allow()` → lưu (thường) hoặc nhớ phiên (incognito) … Activity bị huỷ → deny mọi request đang chờ."
  - `P/phase-04-browser-engine.md:152`: `MainActivity` "tạo … `SitePermissionBridge`"
  - `P/phase-07-browsing-surfaces.md:129`: "đọc `SitePermissionRepository.observe(host)` hoặc state phiên incognito"
  - `NM:247`: "Profile deleted: cookies, cache, history"
- **Suggested fix:** Chuyển phần state (quyết định incognito theo host) vào `IncognitoSession` (process), xoá cùng lúc với `end()`, và để bridge của Activity chỉ giữ request đang chờ cùng callback WebView. `SiteInfoSheet` đọc từ `IncognitoSession`.

## Finding 9: State AI trong bộ nhớ có vòng đời sai: digest bỏ qua quyền "Skim AI" theo site, chat của tab đã đóng không bị gỡ, `chatId` âm sống sót qua process death
- **Severity:** Medium
- **Location:** Phase 12, "Key insights" (digest, D3) và "Architecture"; Phase 8, `AiDataEraser`; Phase 11, `ChatSessionStore`; Phase 3, `TabEntity.chatId`; Phase 9, step 1
- **Flaw:** (a) `PageDigestCache` (process) trích ≤ 4.000 ký tự của *mọi* tab thường sau mỗi lần tải trang. D3 coi một cú bấm 06b là đồng ý cho mọi tab, kể cả site mà người dùng đã tắt switch Skim AI ("Can read this site when you ask") về `ASK`. `AiDataEraser.deleteAllAiData()` không xoá digest. (b) `ChatSessionStore` chỉ gỡ phiên khi `Navigated` hoặc `cleared`, không gỡ khi tab bị `finalizeClose`. `document` (tới 100k ký tự) của tab đã đóng vì vậy nằm trong bộ nhớ suốt đời process, kể cả khi Keep chat history tắt. (c) Chat chỉ trong bộ nhớ dùng id âm, được ghi vào cột `tabs.chatId` trong Room. Sau process death, `hasSkimChat = tab.chatId != null` hiện badge Sunny cho một chat không còn tồn tại.
- **Failure scenario:** Người dùng tắt Skim AI cho `mybank.example` trong Site information, rồi bấm "Summarize my 7 open tabs" ở Home. Text tài khoản ngân hàng (digest) vẫn được gửi tới Claude. Sau "Delete all AI data", digest vẫn còn và sẽ được gửi ở lần 06b kế tiếp. Với Keep chat history tắt, sau process death tab card vẫn hiện badge chat, nhưng mở 06 lại không có chat nào.
- **Evidence:**
  - `P/phase-12-skim-ai-across-tabs-history.md:28`: digest "chụp sau khi trang tải xong, không ghi đĩa, xoá khi tab đóng hoặc đổi trang"
  - `P/phase-12-skim-ai-across-tabs-history.md:29`: "D3: … coi là đồng ý cho mọi tab thường đang mở (không hỏi 06d theo từng site)"
  - `P/phase-03-data-layer.md:62`: "Switch Skim AI trong Site information: tắt → `ASK`"
  - `design/spec/dialogs.md:87`: "Skim AI; Can read this site when you ask"
  - `P/phase-08-settings-privacy-history-bookmarks.md:66-68`: `deleteAllAiData()` = "chats.deleteAll() + sites.resetAllSkimAi() + bỏ chatId của mọi tab" (không có digest)
  - `P/phase-11-skim-ai-core.md:96`: `ChatSessionStore` chỉ theo dõi `Navigated` và `ChatRepository.cleared`
  - `P/phase-03-data-layer.md:51`: "`chatId: Long?` (… chat chỉ trong bộ nhớ khi tắt Keep chat history dùng id âm …)"
  - `P/phase-09-tabs.md:86`: "`hasSkimChat = tab.chatId != null`"
- **Suggested fix:** `collectTabsContext` loại tab có `skimAi == ASK`, hoặc chỉ gửi title + host cho tab đó. `AiDataEraser` gọi `PageDigestCache.clear()`. `ChatSessionStore` gỡ phiên khi tab finalize-close. Không ghi `chatId` âm xuống Room, và lúc `restore()` xoá mọi `chatId` không có dòng trong `skim_chats`.

## Finding 10: Hai cổng chấp nhận không thể vừa xanh vừa có ý nghĩa
- **Severity:** Medium
- **Location:** `plan.md` "Acceptance criteria"; Phase 13, "Architecture" (Parity) và "Success criteria"; Phase 4/5, instrumented test
- **Flaw:** (a) `DesignParityTest` nằm trong `app/src/test` nên chạy dưới `verifyRoborazziDebug`. Nó ghi ảnh vào `app/build/design-parity/`, chính là thư mục mà `prepareDesignParity` đổ PNG thiết kế vào làm reference. Plan tự thừa nhận diff "không bao giờ bằng 0". Sau một lần `compareRoborazziDebug`, mọi lần `verifyRoborazziDebug` sau đó sẽ đỏ, vì `build/` không bị dọn. Kết quả xanh hay đỏ phụ thuộc vào trạng thái thư mục build. (b) `IncognitoProfileTest` và `SecureFlagTest` dùng `assumeTrue(MULTI_PROFILE)`. Thiết bị không có feature này làm test bị skip, và skip được tính là xanh. Tiêu chí "`IncognitoProfileTest` xanh" vì vậy có thể đạt mà incognito chưa từng được kiểm. Plan không bắt buộc thiết bị kiểm phải có `MULTI_PROFILE`.
- **Failure scenario:** CI hoặc người chạy làm theo phase 13: chạy `compareRoborazziDebug` để duyệt, rồi `verifyRoborazziDebug` để chốt. Lần thứ hai đỏ ở 33 ảnh parity, và cách nhanh nhất để "xanh" là nới ngưỡng, điều chính plan cấm. Trên một máy có WebView cũ, cả hai test incognito skip, và tiêu chí "FLAG_SECURE và profile incognito đã kiểm trên máy thật" được tick dù chưa kiểm.
- **Evidence:**
  - `plan.md:95`: "`:app:verifyRoborazziDebug` … xanh; `:app:connectedDebugAndroidTest` xanh trên ≥ 1 thiết bị API ≥ 29"
  - `P/phase-13-verification-hardening-docs.md:28`: "Diff pixel không bao giờ bằng 0"
  - `P/phase-13-verification-hardening-docs.md:53-54`: `DesignParityTest` "ghi `app/build/design-parity/<tên-png>.png`"; `prepareDesignParity` "`Sync` từ `design/screens/` … vào `app/build/design-parity/`"
  - `P/phase-13-verification-hardening-docs.md:68`: `app/src/test/java/com/dantech/skimbrowser/parity/DesignParityTest.kt`
  - `P/phase-04-browser-engine.md:160`: "`IncognitoProfileTest.kt` (bỏ qua bằng `assumeTrue` khi không có `MULTI_PROFILE`)"
  - `P/phase-05-app-shell-navigation-onboarding.md:156`: `SecureFlagTest` "bỏ qua khi không có `MULTI_PROFILE`"
  - `P/phase-04-browser-engine.md:208`: tiêu chí "`IncognitoProfileTest` xanh"
- **Suggested fix:** Đặt parity test trong một source set hoặc task riêng (`designParity`), loại nó khỏi `verifyRoborazziDebug`, và chỉ chạy compare. Trong phase 13, yêu cầu kết quả instrumented ghi rõ `MULTI_PROFILE = true` cùng số test incognito đã chạy (không skip). Nếu skip thì tiêu chí incognito chưa đạt.

---

## Scope Auditor results

| Đối tượng | Vòng đời theo plan | Kết quả |
|---|---|---|
| `AppContainer` | Process (`P/phase-01…:95`) | PASS cho bản thân container; các thành phần FAILED ở dưới. |
| `TabManager` | Process (`P/phase-04…:27`, `:61`); giữ `TabEngine` qua `attach/detach` | PASS về mặt leak: `detach()` ở `onDestroy`. FAILED về phạm vi lệnh: `extractPage` coi pool là nguồn duy nhất của WebView (Finding 5). |
| `WebViewPool` | Activity (`P/phase-04…:62`, `:134`) | PASS. |
| `WindowSecureController` | Activity (`P/phase-04…:152`) | FAILED: bị `SkimNavigator` bắt, rồi `SnackbarController` (process) bắt qua lambda, nên giữ window đã chết (Finding 7). |
| `SkimNavigator` | Composition (`P/phase-11…:125`, `rememberCoroutineScope`) | FAILED: bị lambda của snackbar process-scoped giữ lại (Finding 7). |
| `SnackbarController` | Process (`P/phase-02…:64`, `P/phase-05…:142`) | FAILED: giữ lambda của composition/Activity; hợp đồng `onDismissed` thiếu trường hợp host bị huỷ (Finding 7). |
| `incomingLinks` | Process, `replay = 1` (`P/phase-05…:142`) | FAILED: phát lại cho mỗi composition mới, mở tab trùng (Finding 3). |
| `IncognitoSession` / tên profile `incognito-<ts>` | Process (bộ nhớ) + profile trên đĩa (`R-B:11`) | FAILED: kết thúc phụ thuộc snackbar UI (Finding 1); purge off-main và cache sống trên đĩa (Finding 2). |
| `SitePermissionBridge` (state incognito) | Activity (`P/phase-04…:152`, `:181`) | FAILED: state theo phiên đặt sai tầng, không xoá khi phiên kết thúc (Finding 8). |
| `TabStateStore` / `ThumbnailStore` (nhánh incognito) | Process, bộ nhớ (`P/phase-04…:172`, `:182`) | PASS có điều kiện: xoá ở `finalizeClose`, nên kế thừa lỗi gating của Finding 1. |
| `DownloadTracker` | Process (`P/phase-04…:150`); receiver theo Activity `onStart/onStop` (`:152`); collector theo composition (`P/phase-07…:89`) | FAILED (nhẹ): download xong khi Activity đã stop thì mất event "downloaded"; nhánh incognito lọt cookie (Finding 6). |
| `BrowsingDataCleaner` | Process (`P/phase-07…:119`), nhưng `clearAll(cache)` cần "WebView sống của pool, hoặc WebView tạm bằng context Activity" (`P/phase-08…:73-74`) | FAILED (thiết kế): singleton process cần Activity/pool mà không có đường truyền; nếu giữ reference thì leak Activity. Phải truyền pool theo từng lời gọi. |
| `ClaudeClientProvider` (AI client singleton) | Process, lazy, Mutex (`P/phase-11…:65-66`) | PASS. Không giữ Context; key chỉ ở debug; `X-Skim-Install` lấy một lần. |
| `ChatSessionStore` | Process (`P/phase-11…:86`) | FAILED: phiên của tab đã đóng không bị gỡ (Finding 9b). |
| `PageDigestCache` (digest 06b) | Process, bộ nhớ (`P/phase-12…:51`) | FAILED: không bị `AiDataEraser` xoá, bỏ qua `skimAi == ASK` (Finding 9a). |
| `ReaderHighlights` | Process `SharedFlow` (`P/phase-10…:57`) | PASS: request mất khi không có collector là vô hại. |
| `tabs.chatId` (cột Room) cùng `skim_chats` | Đĩa, hai nguồn cho cùng một sự thật | FAILED: trùng state, id âm sống sót qua process death (Finding 9c). |
| Settings keys `lastTabId`, `installId` | DataStore (`P/phase-03…:126`, `:132`) | PASS: `lastTabId` chỉ cho tab thường (`P/phase-04…:178`). Lưu ý: `installId` không xoay vòng khi "Delete all AI data". |
| BuildConfig / Gradle property (`ANTHROPIC_API_KEY`, `AI_BASE_URL`, URL pháp lý) | Build-time (`P/phase-01…:59-76`) | PASS: release `null`, có `verifyNoApiKeyInRelease`; `releaseSmoke` không gắn `checkSkimReleaseConfig` là chủ ý. |
| Back stack Nav3 (saved state) | System saved state, sống qua process death | FAILED: không đối soát với D2 và purge soft-close; mang URL incognito (Finding 4). |

## Câu hỏi chưa giải quyết

1. Khi người dùng mở tab incognito mới trong lúc Undo của tab incognito cuối còn hiện, phiên mới phải là phiên tươi (đề xuất) hay được phép nối phiên cũ? Quyết định này đổi hợp đồng của `IncognitoSession`.
2. D3 có loại site đang ở `skimAi == ASK` khỏi 06b không? Đây là quyết định sản phẩm đang chờ validate, nhưng hiện plan mặc định là gửi.
3. Chấp nhận cookie của phiên incognito nằm trong DownloadProvider của hệ thống (giống cách tối giản), hay chuyển download incognito sang OkHttp + MediaStore?

Status: DONE_WITH_CONCERNS
Summary: 10 finding (1 Critical, 6 High, 3 Medium). Lỗi tập trung ở ranh giới vòng đời process ↔ Activity ↔ composition: incognito không kết thúc đúng, state UI process-scoped giữ navigator đã chết, và back stack khôi phục lệch với D2.
