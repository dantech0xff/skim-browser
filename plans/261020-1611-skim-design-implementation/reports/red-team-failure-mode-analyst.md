# Red team: Failure Mode Analyst (Flow Tracer)

Phạm vi: `plan.md`, `coverage-matrix.md`, `phase-01` … `phase-13` trong thư mục plan, đối chiếu với các report research/scout, `design/` và scaffold. Mỗi kết luận đều có trích dẫn `file:line`.

Quy ước trích dẫn:
- `phase-NN-….md:L` và `plan.md:L` nằm trong `plans/261020-1611-skim-design-implementation/`.
- `R-web` = `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md`.
- `R-build` = `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md`.
- `NAV` = `design/spec/navigation-map.md`.

Kết luận chính: 10 lỗi, gồm 1 Critical, 8 High và 1 Medium. Plan làm đúng chốt chặn AI cho incognito và thứ tự FLAG_SECURE. Tuy vậy, các luồng khôi phục sau process death của Reader và 06 không thể chạy theo kiến trúc đang mô tả. Ngoài ra còn ba nhóm lỗi:
- Vòng lặp tự phục hồi không có giới hạn: renderer chết thì tự khôi phục, mạng trở lại thì tự reload.
- Undo và các lệnh xoá dữ liệu chạy đua với nhau.
- Đường kiểm chứng phụ thuộc thiết bị trong khi máy này đang không gắn thiết bị nào.

---

## Finding 1: Reader và sheet 06 được khôi phục sau process death nhưng không có WebView hay trang đã tải để đọc

- **Severity:** Critical
- **Location:** Phase 10, mục "Implementation steps" bước 1. Phase 11, mục "Requirements" (dòng process death) và mục "Luồng dữ liệu (06)". Phase 13, mục "Requirements" (Process death).
- **Flaw:** Cả Reader và 06 đều gọi `tabs.extractPage(tabId)` ngay khi ViewModel được tạo, với giả định WebView của tab đang sống và trang đã tải xong. Sau process death, `WebViewPool` rỗng. WebView chỉ được tạo khi `WebViewHost(tabId)` vào composition, mà `WebViewHost` chỉ nằm trong slot `page` của `BrowserKey`. `ReaderKey` là key màn hình, nên Nav3 dùng SinglePane fallback và chỉ vẽ entry trên cùng: `WebViewHost` không bao giờ được compose. Plan chỉ định nghĩa hành vi "chưa attach" cho lệnh `load`, không định nghĩa cho `extractPage`/`evaluate`. `TabRuntime.pageLoaded` được khai báo nhưng không có ai chờ nó.
- **Failure scenario:**
  1. Người dùng đang đọc ở 08 Reader thì process bị kill. Back stack khôi phục thành `[BrowserKey, ReaderKey]`.
  2. `ReaderViewModel` gọi `extractPage`. Lúc này không có WebView, nên kết quả là `null` → `Unavailable` → `navigator.back()`. Reader biến mất, vị trí cuộn của Reader không được khôi phục, trái với acceptance của plan.
  3. Biến thể nặng hơn: back stack là `[BrowserKey, ReaderKey, AskPageKey(fromReader = true)]`. Sheet là overlay nên Reader vẫn được compose bên dưới. Khi Reader chuyển sang Unavailable, `navigator.back()` pop phần tử trên cùng, tức là sheet 06, chứ không pop Reader.
  4. Với 06 mở trên 05: WebView được tạo, nhưng `restoreState` tải trang bất đồng bộ trong khi `AskPageViewModel` trích trang ngay lập tức. Kết quả là hiện sai thẻ "Not enough text", hoặc tóm tắt một DOM mới tải một nửa.
  5. Khi Keep chat history tắt, chat trong bộ nhớ đã mất, nên plan "tạo phiên mới" và tóm tắt tự chạy. Request AI được gửi mà người dùng không bấm gì, trái với "Chỉ gửi text trang khi người dùng bấm".
  6. Các key của tab incognito như `MoreMenuKey`, `SiteInfoKey`, `LinkOptionsKey` vẫn được khôi phục, trong khi D2 bỏ mọi tab incognito. Plan không định nghĩa hành vi khi `tabId` không còn tồn tại.
- **Evidence:**
  - `plan.md:97` "process death khôi phục tab, cuộn, sheet, chat"
  - `phase-13-verification-hardening-docs.md:39` "vị trí cuộn của trang và Reader, sheet đang mở (05b, 06, 06b, 05d)"
  - `phase-05-app-shell-navigation-onboarding.md:38`: Reader nằm trong nhóm "(màn hình)"
  - `R-build:209` "SinglePane được dùng làm fallback tự động"
  - `phase-04-browser-engine.md:130` "Hiển thị tab: `WebViewHost(tabId)` → `pool.obtain(tab)`"
  - `phase-04-browser-engine.md:132`: chỉ có "khi chưa attach, lệnh `load` chỉ cập nhật `tab.url`"
  - `phase-04-browser-engine.md:90` `pageLoaded`; grep cho thấy trường này không được dùng ở đâu khác
  - `phase-10-reader-view.md:81` "`extractPage` một lần khi tạo; … `null` → `Unavailable` (route gọi `navigator.back()`)"
  - `phase-10-reader-view.md:29` "WebView của tab vẫn sống trong pool khi Reader che lên" (chỉ đúng khi process còn sống)
  - `phase-11-skim-ai-core.md:51` "ViewModel đọc lại chat của tab (nếu đã lưu) hoặc tạo phiên mới"
  - `phase-11-skim-ai-core.md:112` `sessions.openForTab(tab, tabs.extractPage(tabId))`
  - `phase-11-skim-ai-core.md:136` "tóm tắt tự chạy khi phiên chưa có summary"
  - `phase-11-skim-ai-core.md:199` "Chỉ gửi text trang khi người dùng bấm"
  - Phase 13 không có test tự động cho luồng này: `phase-13-verification-hardening-docs.md:31` (không kill được process từ instrumentation) và `:63` (`RestoreAcrossContainersTest` chỉ kiểm dữ liệu của `TabManager`). Lỗi chỉ lộ ở checklist thủ công `:132`, sau khi đã xây xong 12 phase.
- **Suggested fix:**
  - Thêm `TabManager.awaitPageReady(tabId, timeout)`. Hàm này bảo đảm có WebView (pool tạo WebView headless nếu tab không hiển thị) và chờ `PageFinished` của URL hiện tại rồi mới trích. Reader ở trạng thái Loading cho tới lúc đó, và chỉ chuyển Unavailable sau khi tải xong.
  - Reader tự gỡ chính `ReaderKey` của nó (`removeKey`), không gọi `back()`.
  - Không bao giờ tự tóm tắt khi khôi phục: lưu cờ "đã yêu cầu tóm tắt" trong `SavedStateHandle`.
  - Thêm bước làm sạch back stack sau `restore()` để bỏ các key trỏ tới `tabId` không còn tồn tại.
  - Thêm test `ActivityScenario` có xoá pool trước khi khôi phục.

## Finding 2: `incomingLinks` với `replay = 1` mở lại link ngoài cũ mỗi khi Activity được tạo mới trong cùng process

- **Severity:** High
- **Location:** Phase 5, mục "Trạng thái app (`SkimApp`)" bước 7 và mục "Related code files → Sửa" (`AppContainer`).
- **Flaw:** `incomingLinks` sống theo process và có `replay = 1`. Mỗi composition mới của `SkimApp` lại collect từ đầu và nhận lại URL cuối cùng, sau đó gọi `tabs.newTab(url, openedByExternalApp = true)`. Plan không có bước "đã tiêu thụ" link.
- **Failure scenario:**
  1. Trên API 26–30 (nằm trong minSdk 26), Back ở Activity gốc gọi `finish()`. Người dùng mở link từ Gmail, đọc xong, rồi Back nhiều lần để rời app ("Back rời app"). Process vẫn còn trong cache.
  2. Lần sau người dùng bấm icon launcher. Một `MainActivity` mới được tạo, `LaunchedEffect` collect lại và nhận URL cũ, nên một tab mới mở lại link Gmail cũ.
  3. Tab đó mang cờ `openedByExternalApp`, nên Back đầu tiên đóng tab và gọi `moveTaskToBack(true)`. Người dùng vào từ launcher lại bị đẩy ra màn hình chính.
  4. Lỗi tương tự xảy ra khi bật "Don't keep activities", và ở mỗi lần `ActivityScenario.recreate()` sau khi đã nhận một link.
- **Evidence:**
  - `phase-05-app-shell-navigation-onboarding.md:142` "`incomingLinks: MutableSharedFlow<String>(replay = 1)`"
  - `:91` "LaunchedEffect: collect incomingLinks → tabs.newTab(url, openedByExternalApp = true)"
  - `:143`: chỉ đọc intent khi `savedInstanceState == null` và ở `onNewIntent`
  - `:103` "tab `openedByExternalApp` → đóng tab ngay … + `activity.moveTaskToBack(true)`; … còn lại không chặn (Back rời app)"
  - `phase-03-data-layer.md:138` "`AppContainer` … sống theo process"
  - `plan.md:18` minSdk 26
- **Suggested fix:**
  - Dùng `Channel<String>(BUFFERED)` để mỗi link chỉ được nhận một lần, hoặc giữ `MutableStateFlow<PendingLink?>` và gọi `consume()` ngay sau `newTab`.
  - Tốt hơn nữa: xử lý link ngay trong Activity bằng cách gọi thẳng `TabManager` (sống theo process), không đi qua collector của Compose.
  - Thêm test: finish Activity, mở lại trong cùng process, khẳng định không có tab mới nào.

## Finding 3: D4 bỏ chat của tab dựa trên `Navigated`, nhưng sự kiện này phát cả cho điều hướng cùng document; stream đang chạy và citation không được xử lý

- **Severity:** High
- **Location:** Phase 4, mục "Luồng dữ liệu" bước 3. Phase 11, mục "Architecture" (`ChatSessionStore` theo dõi `pageEvents`) và mục "Requirements" (D4, citation, Close).
- **Flaw:** `PageEvent.Navigated` được phát từ `doUpdateVisitedHistory`. Callback này chạy cho mọi navigation đã commit, kể cả `history.pushState`/`replaceState` của SPA (đây là lý do nó được dùng để bắt URL SPA). D4 coi mọi URL khác (đã bỏ fragment) là trang mới. Plan không định nghĩa:
  - Stream đang chạy sẽ ra sao khi phiên bị gỡ.
  - `record()` có gắn lại `tab.chatId` hay không.
  - Citation chip làm gì khi DOM bên dưới đã là trang khác.
  - Khi đóng sheet giữa stream thì phần text đã nhận có được lưu không.
- **Failure scenario:**
  1. Nhiều trang báo dùng `replaceState` để đổi URL khi người dùng cuộn sang bài kế tiếp. Người dùng bấm chip "From paragraph 6", `highlightBlock` gọi `scrollIntoView`, trang tự đổi URL, `Navigated` phát ra và chat bị gỡ giữa cuộc hội thoại. Câu hỏi tiếp theo tạo chat mới và tóm tắt lại (tốn thêm một request), còn chat cũ bị bỏ lại trong Skim history.
  2. Nếu `Navigated` đến khi stream đang chạy, `AskPageViewModel` vẫn collect và gọi `record()` cho một phiên đã bị gỡ.
  3. Trang tự redirect (meta refresh, JS) phía dưới lớp scrim. Citation trên sheet lúc đó gọi `highlightBlock` vào DOM mới: id `data-skim-i` không khớp, fallback theo `cited_text` thất bại, trang cuộn lên đầu. Highlight sai mà không báo gì.
  4. Plan ghi "Close hoặc vuốt xuống → chat ở lại với tab", nhưng việc huỷ `viewModelScope` giữa stream sẽ làm mất phần tóm tắt dở. Lần mở sau lại tóm tắt thêm lần nữa.
- **Evidence:**
  - `phase-04-browser-engine.md:42` "ghi ở `doUpdateVisitedHistory`"
  - `phase-04-browser-engine.md:131` "`doUpdateVisitedHistory` → … + `PageEvent.Navigated`"
  - `phase-11-skim-ai-core.md:96` "`Navigated` với URL bỏ fragment khác `pageUrl` → `tabs.bindChat(tabId, null)` và gỡ phiên"
  - `phase-11-skim-ai-core.md:45` (citation → `highlightBlock`, fallback cuộn đầu trang)
  - `phase-11-skim-ai-core.md:48` "Close hoặc vuốt xuống → đóng; chat ở lại với tab"
  - `phase-11-skim-ai-core.md:136` "Stop = huỷ `Job` collect"
  - `phase-11-skim-ai-core.md:145`: `ChatSessionStoreTest` chỉ kiểm "URL khác bỏ chat", không có kịch bản đang stream hay SPA
  - `R-web:433` "Highlight trỏ sai đoạn (SPA render lại DOM)"
  - `phase-12-skim-ai-across-tabs-history.md:28`: digest "xoá khi … đổi trang" cũng dùng chung trigger này
- **Suggested fix:**
  - Thêm `isSameDocument` vào `PageEvent.Navigated` (same-document khi không có `onPageStarted` giữa hai lần `doUpdateVisitedHistory`). D4 chỉ áp dụng cho navigation cross-document của main frame.
  - Khi có navigation thật trong lúc đang stream: huỷ stream, lưu message với trạng thái `STOPPED`, và vô hiệu citation chip khi `session.pageUrl != tab.url`.
  - `record()` chỉ ghi vào `session.chatId` và không bao giờ đổi `tab.chatId`.
  - Khi đóng sheet giữa stream: lưu phần đã nhận với trạng thái `STOPPED`.

## Finding 4: Tự khôi phục renderer và tự reload khi có mạng đều không có giới hạn, nên có thể lặp vô hạn

- **Severity:** High
- **Location:** Phase 4, mục "Requirements" (renderer gone, tự reload) và mục "Risk assessment" (Renderer OOM). Phase 12, mục "Architecture" (`PageDigestCache`).
- **Flaw:** Sau mỗi `PageFinished` của tab thường, `PageDigestCache` tự chạy `extractPage`. Hàm này chạy Readability đầy đủ trên `document.cloneNode(true)`, tức là nhân đôi DOM trong renderer. Giới hạn 400 block / 100k ký tự chỉ áp cho output, sau khi đã clone. `onRenderProcessGone` thì "khôi phục" ngay mà không có ngân sách thử lại. Hạ capacity xuống 1 cũng không cắt được vòng lặp, vì tab gây lỗi là tab hiện tại, không bao giờ bị evict. Một renderer phục vụ nhiều WebView, nên mọi tab sống cùng chết và cùng được khôi phục.
- **Failure scenario:**
  1. Người dùng mở một trang HTML rất lớn (bảng 200k dòng, log export, danh sách Wikipedia dài). `PageFinished` → chờ 2 s → digest → `cloneNode` → renderer bị OOM-kill.
  2. `onRenderProcessGone` phát cho cả 3 WebView. Pool khôi phục từ file và tải lại ngay. Trang lớn lại `PageFinished`, digest chạy lại, OOM lại. Vòng lặp chạy mãi, tốn pin và dữ liệu, và người dùng không thoát ra được vì tab hiện tại luôn được khôi phục.
  3. Mạng: trên mạng chưa được validate (captive portal, mạng chặn connectivity check), mọi lỗi đều thành NoInternet, kể cả gõ sai tên miền. "Connection comes back" không bao giờ kích hoạt.
  4. Khi mạng chập chờn (VALIDATED bật tắt liên tục), mỗi lần bật lên lại reload mọi tab NoInternet. Hành vi `reload` cho tab không có WebView sống thì không được định nghĩa.
- **Evidence:**
  - `phase-04-browser-engine.md:48` "`onRenderProcessGone` (trả `true`, khôi phục)"
  - `:175` `pool.onRendererGone(tabId, detail.didCrash())`
  - `:231` "Renderer OOM | … | khôi phục từ file state, hạ capacity 1" (không có giới hạn số lần)
  - `:49` readerable check "sau mỗi lần tải xong"
  - `:147` giới hạn 400 block / 100k ký tự
  - `:183` extract inject Readability
  - `phase-12-skim-ai-across-tabs-history.md:51` "nghe `pageEvents` PageFinished …, debounce 2 s, `extractPage`"
  - `phase-12-skim-ai-across-tabs-history.md:145` (rủi ro chỉ ghi "giật")
  - `R-web:213` `new Readability(document.cloneNode(true), …)`
  - `R-web:24` "Một renderer có thể phục vụ nhiều WebView"
  - Mạng: `phase-04-browser-engine.md:43`, `:178`; `R-web:112` "`hasCapability(NET_CAPABILITY_VALIDATED) != true -> LoadError.NoInternet`"
  - `phase-04-browser-engine.md:132`: không định nghĩa lệnh `reload` khi chưa có WebView
- **Suggested fix:**
  - Đặt ngân sách crash cho mỗi tab, ví dụ tối đa 1 lần tự khôi phục mỗi 60 s. Vượt quá thì hiện trạng thái lỗi kèm Try again (copy D8 mới), không tự tải lại. Chỉ khôi phục tab nền khi tab được hiển thị lại.
  - Không chạy digest/Readability tự động sau mỗi lần tải. Chỉ chạy khi có hành động AI, hoặc kiểm số node DOM trước khi clone. Gắn cờ "heavy" cho tab đã từng gây crash.
  - Mạng: chỉ gán NoInternet khi gặp `ERR_INTERNET_DISCONNECTED` hoặc không có active network. Tự reload chỉ áp cho tab đang hiển thị (debounce theo cạnh lên của VALIDATED); tab khác reload khi được mở lại.

## Finding 5: Trim memory ở mức BACKGROUND evict mọi tab không hiện tại sau mỗi lần chuyển app, làm hỏng popup đăng nhập/thanh toán

- **Severity:** High
- **Location:** Phase 4, mục "Implementation steps" bước 6 (`onCreateWindow`) và bước 7 (`onTrimMemory`).
- **Flaw:** `TRIM_MEMORY_BACKGROUND` dẫn tới "capacity 1 và evict". Chính plan cũng ghi `RUNNING_LOW` có thể không còn được gửi trên API 34+, nên BACKGROUND trở thành tín hiệu chính, và nó đến sau gần như mọi lần app xuống nền. Popup từ `onCreateWindow` thành tab hiện tại, tab mở nó (opener) không còn là tab hiện tại nên bị evict. `restoreState` không khôi phục được quan hệ `window.opener`. Plan cũng không gọi `onPause()`/`pauseTimers()` cho WebView bị tách khỏi cây view hoặc khi app xuống nền.
- **Failure scenario:**
  1. Người dùng bấm "Sign in with Google" (hoặc PayPal, 3DS). Trang mở popup bằng `window.open`, popup thành tab hiện tại.
  2. Người dùng chuyển sang app authenticator để lấy mã 2FA. Skim nhận BACKGROUND → capacity 1 → tab opener được lưu rồi `destroy()`.
  3. Quay lại, popup gửi `window.opener.postMessage` rồi `window.close()`. Opener đã chết, nên đăng nhập hoặc thanh toán thất bại mà không có lỗi nào. `onCloseWindow` đóng popup và người dùng về một opener vừa được tải lại, không có phiên đăng nhập.
  4. Ngoài popup, mỗi lần chuyển app đều huỷ 2 tab sống còn lại, làm mất form chưa gửi và state SPA. Mục đích của LRU 3 không đạt được.
  5. Video hoặc audio trong tab đã tách khỏi cây view vẫn tiếp tục phát sau khi chuyển tab hoặc app xuống nền.
- **Evidence:**
  - `phase-04-browser-engine.md:177` "`TRIM_MEMORY_RUNNING_LOW` trở lên hoặc `TRIM_MEMORY_BACKGROUND` → capacity 1 và evict; `onStart` … trả về 3"
  - `:32` "`onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` có thể không còn được gửi … API 34+; nên phản ứng thêm với `TRIM_MEMORY_BACKGROUND`/`UI_HIDDEN`"
  - `:176` "`onCreateWindow` … `pool.obtainForPopup(tab)` …, chọn tab mới; `onCloseWindow` → đóng tab đó"
  - `:130` "không bao giờ evict tab hiện tại"
  - `:134`: `onStop` chỉ `saveAll()`, không pause
  - `:184`: `onRelease` chỉ tách view
  - `:159`: `SkimWebViewEngineTest` không có kịch bản popup hay trim
  - `R-web:103` (`window.open` → `onCreateWindow`)
  - grep `onPause|pauseTimers` trong plan: không có kết quả nào
- **Suggested fix:**
  - UI_HIDDEN/BACKGROUND chỉ lưu state. Chỉ evict khi gặp `RUNNING_CRITICAL`/`COMPLETE`, khi `isLowRamDevice`, hoặc khi `ActivityManager.MemoryInfo.lowMemory`.
  - Ghim cặp opener ↔ popup: không evict tab đang có popup con mở.
  - Gọi `webView.onPause()` cho WebView không hiển thị. Gọi `pauseTimers()` ở `onStop` và `resumeTimers()` ở `onStart`.
  - Thêm test instrumented: mở popup, chạy `am send-trim-memory … BACKGROUND`, khẳng định opener vẫn sống.

## Finding 6: File state ghi không nguyên tử và lỗi đọc không được bắt, có thể gây crash loop lúc khởi động; `restore()` chạy sai thread

- **Severity:** High
- **Location:** Phase 4, mục "Implementation steps" bước 2 (`TabStateStore`), bước 14 (`WebViewHost`) và mục "Luồng dữ liệu" bước 1. Phase 1, mục "Khởi động" (`appScope`).
- **Flaw:**
  1. `<id>.bin` được ghi đè trực tiếp: không dùng `AtomicFile`, không ghi ra file tạm rồi đổi tên. Plan chỉ xử lý trường hợp `restoreState` trả `null`. `Parcel.unmarshall`/`readBundle` trên byte bị cắt dở hoặc sai định dạng thì ném exception, và không có try/catch.
  2. `load` chạy trong factory đồng bộ của `AndroidView`, tức là đọc đĩa trên main thread, trái với "I/O file trên `Dispatchers.IO`".
  3. `restore()` được launch trên `appScope` (`Dispatchers.Default`) nhưng gọi `IncognitoSession.purgeStale()`, trong khi plan và research đều yêu cầu `ProfileStore` chạy trên UI thread.
- **Failure scenario:**
  1. App ở nền, debounce lưu 1 s hoặc `saveAll()` ở `onStop` đang ghi file thì LMK kill process. File `<lastTabId>.bin` bị cắt dở.
  2. Lần mở sau, `restore()` chọn `lastTabId` và hiển thị tab đó. `stateStore.load` ném exception trên main thread → crash ở frame đầu.
  3. Mỗi lần mở lại đều crash, vì vẫn cùng `lastTabId`, cho tới khi người dùng xoá dữ liệu app và mất luôn tab, bookmark, chat. Lỗi tương tự có thể xảy ra sau cập nhật OS, vì định dạng Parcel không được bảo đảm cho lưu trữ lâu dài.
  4. Riêng chuyện thread: `ProfileStore.getInstance()` gọi từ thread Default. Nếu nó ném lỗi, coroutine với `SupervisorJob` đẩy lỗi lên default handler (crash). Nếu lỗi bị nuốt, `SkimApp` chờ `restore()` mãi và chỉ vẽ `Surface` trống.
- **Evidence:**
  - `phase-04-browser-engine.md:172` "`Parcel.obtain().apply { writeBundle(b) }.marshall()` ghi `filesDir/tabstate/<id>.bin` trên IO"
  - `:29` và `:233`: chỉ có `restoreState(...) ?: loadUrl(url)`
  - `:131` lưu state debounce 1 s; `:134` `onStop` → `saveAll()`
  - `:178` "`restore()` chọn `lastTabId`"
  - `:184` `AndroidView(factory = { pool.obtain(tab) … })`
  - `:54` "đọc lazy khi tab được hiển thị" mâu thuẫn với `:52` "I/O file trên `Dispatchers.IO`"
  - Thread: `:151` `appScope.launch { container.tabManager.restore() }`, `:129` `restore` → `IncognitoSession.purgeStale()`, `:52` "Mọi thao tác WebView/`ProfileStore` trên main thread"
  - `phase-01-build-foundation-theme-resources.md:95` `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`
  - `R-web:58` "chỉ chạy trên UI thread"
  - `R-web:30` "Parcel không ổn định qua các phiên bản OS/WebView"
  - `phase-05-app-shell-navigation-onboarding.md:85` "Chờ settings + `tabManager.restore()` xong … chưa xong thì chỉ vẽ Surface"
  - grep `AtomicFile` trong plan: không có kết quả nào
- **Suggested fix:**
  - Ghi bằng `AtomicFile` (`startWrite`/`finishWrite`).
  - Đặt header gồm version WebView, `SDK_INT` và checksum; lệch thì bỏ file.
  - Bọc unmarshall + `restoreState` trong `try/catch (Throwable)`; lỗi thì xoá file rồi `loadUrl`.
  - Đọc byte trên IO trước khi compose: tạo WebView trống, đọc file xong mới `restoreState`.
  - `purgeStale()` chạy bằng `withContext(Dispatchers.Main)` sau frame đầu tiên. `restore()` có timeout; quá hạn thì mở tab New tab, không để màn hình trống mãi.

## Finding 7: Xoá dữ liệu không trọn: incognito không xoá cache, Clear browsing data có thể xoá nhầm profile và không flush cookie

- **Severity:** High
- **Location:** Phase 4, mục "Requirements" (Incognito) và bước 3 (`IncognitoSession`). Phase 8, mục "Architecture" (`BrowsingDataCleaner.clearAll`) và mục "Risk assessment".
- **Flaw:**
  1. Thiết kế hứa "Profile deleted: cookies, cache, history". `end()` chỉ xoá cookie, web storage và geolocation. Việc tắt cache (`setQuotaBytes(0)`) chỉ có hiệu lực khi có `HTTP_CACHE_MANAGER` (WebView M151+), và thiếu nó thì không có phương án dự phòng. Profile đã load không xoá và không unload được, nên mỗi phiên incognito để lại một browser context trong bộ nhớ và một thư mục trên đĩa, tồn tại tới lần khởi động lạnh sau. Lúc đó `purgeStale` dùng `runCatching` và không kiểm lại.
  2. `clearAll` xoá cache "trên một WebView sống của pool": nếu WebView sống duy nhất là incognito, lệnh tác động vào cache của profile incognito (suy luận: `clearCache` gắn với browser context của chính WebView đó). Phương án "WebView tạm bằng context Activity" không làm được, vì `BrowsingDataCleaner` nằm trong `AppContainer` sống theo process và không có Activity.
  3. `removeAllCookies` chạy bất đồng bộ và plan không gọi `flush()`. Các WebView đang sống vẫn giữ phiên trong bộ nhớ và ghi lại cookie hay localStorage.
- **Failure scenario:**
  1. Thiết bị có `MULTI_PROFILE` nhưng WebView cũ hơn M151, ví dụ máy không cập nhật được qua Play. Ảnh và trang trong incognito được cache vào thư mục profile. Đóng tab incognito cuối cùng xong, cache vẫn nằm trên đĩa nhiều ngày, cho tới khi process khởi động lạnh lại.
  2. Người dùng đang ở tab incognito, mở 05b → Settings → Clear browsing data. Cache của profile mặc định vẫn còn, nhưng UI báo đã xoá.
  3. Người dùng xoá Cookies rồi tắt app ngay. Nếu process bị kill trước khi cookie store ghi xuống đĩa, phiên đăng nhập quay lại ở lần mở sau. Tab Gmail đang mở cũng ghi lại cookie ngay sau khi xoá.
  4. Checklist thủ công ("site đang đăng nhập bị đăng xuất") kiểm tra ngay sau khi xoá, nên không bắt được cả hai lỗi này.
- **Evidence:**
  - `NAV:247` "Last incognito tab closes | Profile deleted: cookies, cache, history"; `NAV:19`
  - `phase-04-browser-engine.md:41` "xoá cookie/web storage/geolocation của profile"
  - `:173` "`httpCache.setQuotaBytes(0)` khi có `HTTP_CACHE_MANAGER`"
  - `:229` (risk `deleteProfile`)
  - `R-web:11` (không xoá được profile đã load trong process)
  - `R-web:52` "không có đường nào để unload một profile"
  - `R-web:53` "`httpCache` (feature `HTTP_CACHE_MANAGER`, cần WebView M151 trở lên)"
  - `R-web:70` `runCatching { deleteProfile(it) }`
  - `phase-08-settings-privacy-history-bookmarks.md:72-74` "cache → `WebView.clearCache(true)` trên một WebView sống của pool, hoặc một WebView tạm bằng context Activity"
  - `:145` (rủi ro chưa kiểm); `:139` (checklist thủ công)
  - `phase-07-browsing-surfaces.md:119` (`browsingDataCleaner` trong `AppContainer`)
  - grep `flush()` trong plan: không có kết quả nào
- **Suggested fix:**
  - Khi không có `HTTP_CACHE_MANAGER`: coi incognito là không hỗ trợ (giống trường hợp thiếu `MULTI_PROFILE`), hoặc lúc `end()` gọi `clearCache(true)` trên một WebView của profile incognito trước khi destroy.
  - Dùng một tên profile incognito cố định cho cả vòng đời process và xoá sạch nó ở mỗi `end()` (gồm cả service worker), thay vì tạo profile mới mỗi phiên rồi để lại.
  - Chạy `clearAll` qua pool hoặc `ActivityHost`, trên WebView của profile mặc định. Chờ callback của `removeAllCookies`, gọi `flush()`, rồi reload các WebView đang sống của profile mặc định.
  - Thêm test: kill process ngay sau khi xoá, mở lại và kiểm cookie không còn.

## Finding 8: Undo của History, Bookmark, Shortcut và Skim history chạy trong scope có thể đã bị huỷ, đụng ràng buộc UNIQUE, và hồi sinh dữ liệu sau "Delete all"

- **Severity:** High
- **Location:** Phase 3, mục "Key insights" (hàm `restore` chèn lại cùng id). Phase 6 bước 5. Phase 8, mục "Architecture" (`HistoryViewModel`, `BookmarksViewModel`). Phase 12 bước 7.
- **Flaw:**
  1. Phase 9 có ghi quy tắc "callback phải bám `TabManager` (process), không bám ViewModel", nhưng chỉ áp cho tab. Với History, Bookmark, Shortcut và Skim history, `restore` là hàm `suspend` được gọi từ lambda `Run: () -> Unit`, và plan không nói lambda đó launch coroutine trên scope nào. Lựa chọn tự nhiên là `viewModelScope`, mà scope này bị huỷ ngay khi entry rời back stack, trong khi snackbar (sống theo app) vẫn còn hiện tới 10 s.
  2. `history.url` có unique index, `recordVisit` upsert theo URL, còn `restore` chèn lại với id cũ. Plan không quy định cách xử lý xung đột.
  3. Không có cơ chế huỷ token Undo đang chờ khi chạy `deleteAll`/`clearAll`.
- **Failure scenario:**
  1. Ở 14, người dùng xoá một bookmark rồi bấm Back về 05 và bấm Undo trên snackbar. `viewModelScope` đã bị huỷ, Undo không làm gì và bookmark mất hẳn, không có báo lỗi.
  2. Ở 04, người dùng xoá một shortcut rồi bấm một shortcut khác. Tab chuyển từ New tab sang trang web, `HomeViewModel` bị huỷ, và Undo không còn tác dụng.
  3. Ở 13, người dùng xoá URL U trong khi một tab nền đang mở U và tự reload (mạng trở lại, liveblog, SPA `replaceState`). `recordVisit` tạo dòng U mới với id mới. Bấm Undo thì `restore` chèn id cũ với cùng URL → `SQLiteConstraintException` (crash), hoặc nếu dùng REPLACE thì xoá mất lượt truy cập mới.
  4. Ở 04c, người dùng xoá một mục (Undo còn hiện), rồi Delete all → Clear. Bấm Undo trên snackbar vẫn còn hiện thì chat hồi sinh sau khi đã "xoá hết". Tương tự với 13: Remove, rồi Clear browsing data, rồi Undo.
- **Evidence:**
  - `phase-09-tabs.md:26` "Callback hoàn tất phải bám vào `TabManager` (process) chứ không bám ViewModel"
  - `phase-02-design-system-components.md:75` `data class Run(…, val block: () -> Unit)`
  - `phase-03-data-layer.md:79`, `:89`, `:94`, `:100`: `suspend fun restore(...)`
  - `phase-03-data-layer.md:54` "`url` (unique index)"
  - `phase-03-data-layer.md:160` (upsert theo `url`)
  - `phase-03-data-layer.md:27` "hàm `restore` chèn lại với cùng id"
  - `phase-04-browser-engine.md:131` (`recordVisit` mỗi `doUpdateVisitedHistory`)
  - `phase-06-home-search-incognito-new-tab.md:115` "Run(action_undo) { restore }"
  - `phase-08-settings-privacy-history-bookmarks.md:61-63` (`HistoryViewModel`, `BookmarksViewModel` nhận `snackbar`)
  - `phase-12-skim-ai-across-tabs-history.md:40`, `:110` (Remove + Undo, Delete all)
  - `phase-03-data-layer.md:167`: test chỉ kiểm "Undo trả lại đúng dữ liệu"
  - grep `onConflict|REPLACE|ABORT` trong plan: không có kết quả nào
- **Suggested fix:**
  - Thêm một `UndoRegistry` sống theo process. Mỗi lệnh xoá trả về một token và Undo chạy trên `appScope`.
  - `deleteAll`/`clearAll` huỷ mọi token đang chờ và đóng snackbar.
  - `HistoryDao.restore` upsert theo `url` và giữ `max(visitedAt)`.
  - Thêm test cho ba trường hợp: Undo sau khi rời màn, Undo sau khi URL được truy cập lại, Undo sau Delete all.

## Finding 9: Gom nhóm tự động trên 09 chạy đua với thao tác của người dùng và với Undo đóng tab

- **Severity:** Medium
- **Location:** Phase 12, mục "Requirements" (gom nhóm 09) và mục "Architecture" (`TabGrouping`, `TabsViewModel`). Phase 9, mục "Architecture" (Undo đóng tab).
- **Flaw:** `maybeGroup()` gọi model (mất vài giây) rồi `applyLayout` lên state tại thời điểm có kết quả. Plan không kiểm version layout, không loại các tab đang chờ Undo đóng, và không nói rõ có huỷ request khi người dùng rời 09 hay không (`TabGrouping` sống theo process). Undo của banner khôi phục toàn bộ snapshot cũ. `saveLayout` và `putSnapshot` là hai lệnh ghi riêng.
- **Failure scenario:**
  1. Người dùng mở 09 với 6 tab chưa nhóm, request được gửi. Trong lúc chờ, người dùng kéo đổi chỗ 2 tab và đóng 2 tab (Undo còn chờ). Kết quả về, layout mới ghi đè thứ tự người dùng vừa kéo, và 2 tab đang chờ Undo bị đưa vào nhóm.
  2. Undo đóng tab khôi phục tab "kể cả vị trí và nhóm" cũ, xung đột với layout mới. Nếu tab bị đóng hẳn, nhóm có thể còn dưới 2 tab, vì quy tắc "bỏ nhóm < 2" chỉ chạy lúc nhận kết quả.
  3. Bấm Undo trên banner sau khi đã Close N tabs xoá mất các nhóm `createdBySkim`. Lúc này token đóng tab vẫn mang `groupId` của nhóm đã bị xoá, và `undoClose` khôi phục tab với `groupId` treo trong state bộ nhớ (FK `SET NULL` chỉ sửa trong DB).
  4. Process chết giữa `saveLayout` và `putSnapshot`: layout đã được áp nhưng không còn Undo.
  5. Nếu request vẫn chạy sau khi người dùng rời 09, layout đổi trong khi banner đã ẩn, nên người dùng không có cách Undo.
- **Evidence:**
  - `phase-12-skim-ai-across-tabs-history.md:39` (lưu snapshot, áp layout, Undo khôi phục snapshot, "Rời 09 → ẩn banner")
  - `:82` "gọi `maybeGroup()` một lần khi mở … `onCleared` gọi `hideBanner()`"
  - `:63` `class TabGrouping(...)` và `:100` `tabGrouping` trong `AppContainer`
  - `phase-04-browser-engine.md:133` "`undoClose` khôi phục (kể cả vị trí và nhóm)"
  - `phase-09-tabs.md:60` (`close`/`undoClose`/`finalizeClose`), `:62` (`applyLayout`)
  - `phase-03-data-layer.md:71-72` (`saveLayout` là transaction riêng, `putSnapshot` là hàm riêng)
- **Suggested fix:**
  - Kiểm tương tranh lạc quan: chỉ áp kết quả khi `tabSetKey` và `layoutVersion` chưa đổi kể từ lúc gửi request.
  - Loại các tab đang chờ đóng khỏi request.
  - Huỷ request khi rời 09.
  - Ghi layout và snapshot trong cùng một transaction.
  - Undo của banner chỉ khả dụng khi `layoutVersion` chưa đổi, và chỉ gỡ các nhóm `createdBySkim`.
  - `undoClose` kiểm lại `groupId` với các nhóm hiện có trước khi khôi phục.

## Finding 10: Đường kiểm chứng và rollback có lỗ: không có thiết bị nào được gắn, plan bỏ qua emulator có sẵn, không có image API 26–28, và rollback dựa vào commit không tồn tại

- **Severity:** High
- **Location:** `plan.md`, mục "Dependencies", "Quy ước tích hợp và thực thi" và dòng Rollback. Mục "Verification" và "Rollback" của phase 4, 5, 7, 9 và 13.
- **Flaw:**
  1. Success criteria của phase 4, 5, 7, 9 dựa vào test instrumented (`IncognitoProfileTest`, `SecureFlagTest`, `BackStackRestoreTest`, `BrowsingFlowsTest`, `TabsGesturesTest`). Trên máy này `adb devices` không có thiết bị nào. Plan cho rằng "chưa có emulator" và chọn "máy thật qua adb". Thực tế `~/Library/Android/sdk/emulator/` có tồn tại và đã có AVD API 36 (`duoly_baseline_profile_api36`, `localmsg_stress_api36`), nhưng plan không dùng.
  2. Không có system image nào cho API 26–28 (chỉ có android-30 trở lên). Các nhánh chỉ dành cho 26–28 (`WRITE_EXTERNAL_STORAGE` khi tải xuống, Android default apps, `Settings.ACTION_WIRELESS_SETTINGS`) bị để "nếu có", nên sẽ phát hành mà không được kiểm.
  3. Các phase chạy tuần tự từ 1 đến 13 mà không có cổng thiết bị nào. Những phần nền chưa từng chạy trên máy (NestedScrollingChild3, cách ly profile, thời điểm bật FLAG_SECURE) sẽ chỉ lộ lỗi ở phase 13, sau khi phase 7–12 đã xây dựa trên chúng.
  4. Rollback được mô tả là "revert commit của phase", nhưng plan cũng ghi "plan không có bước commit/push", nên không có commit nào để revert. Kể cả khi có commit, các file đăng ký dùng chung (`SkimNavDisplay.kt`, `SkimNavigator.kt`, `AppContainer.kt`) bị mọi phase từ 5 đến 12 sửa. Phase 11 còn đổi constructor của `SkimNavigator`. Vì vậy revert phase N sẽ xung đột với các phase N+1..12.
- **Failure scenario:**
  1. Người thực thi tới cuối phase 4 thì không chạy được `connectedDebugAndroidTest`. Hoặc phase bị đánh dấu xong mà không có bằng chứng, hoặc công việc bị chặn.
  2. Tới phase 13 mới thấy pull to refresh không chạy trên máy thật, phải chuyển sang `SwipeRefreshLayout`, và phải sửa lại phase 7 (`PullToRefreshBox`) cùng các ảnh golden liên quan.
  3. Lỗi tải xuống trên API 28 lọt ra bản phát hành.
  4. Muốn rollback phase 11 thì không có commit nào để revert, và nếu sửa tay thì làm vỡ `SkimNavDisplay` của phase 12.
- **Evidence:**
  - `plan.md:90` "máy dev chưa có emulator trên PATH"
  - `plan.md:95` (acceptance cần `connectedDebugAndroidTest`)
  - `plan.md:66` (file đăng ký dùng chung)
  - `plan.md:68` "plan không có bước commit/push" mâu thuẫn với `plan.md:111` "rollback = revert commit của phase"
  - `phase-04-browser-engine.md:217`, `:238`
  - `phase-04-browser-engine.md:31` và `R-web:439` (nested scroll và FLAG_SECURE "chưa chạy trên thiết bị")
  - `phase-07-browsing-surfaces.md:175` (phương án `SwipeRefreshLayout`)
  - `phase-11-skim-ai-core.md:124` ("constructor nhận `aiGate` và `scope`")
  - `phase-13-verification-hardening-docs.md:91` "thêm một máy 26–28 nếu có"
  - `phase-04-browser-engine.md:180`, `phase-05-app-shell-navigation-onboarding.md:113`, `phase-07-browsing-surfaces.md:45` (các nhánh 26–28)
  - Kiểm trên máy (2026-10-21): `adb devices` trả danh sách rỗng; `which emulator` không tìm thấy nhưng `~/Library/Android/sdk/emulator/` tồn tại; `~/.android/avd/` có hai AVD API 36; `~/Library/Android/sdk/system-images/` chỉ có android-30, 33, 34, 36.
- **Suggested fix:**
  - Thêm cổng thiết bị ở cuối phase 4: chạy bộ instrumented trên AVD có sẵn (`~/Library/Android/sdk/emulator/emulator -avd localmsg_stress_api36`, ghi lại PID và dừng khi xong) trước khi sang phase 5.
  - Cài image API 28 cho các nhánh 26–28.
  - Phase nào chưa chạy được test instrumented thì ghi trạng thái `DONE_WITH_CONCERNS`, không ghi Completed.
  - Hoặc thêm checkpoint commit/tag cho từng phase (cần người dùng đồng ý), hoặc đổi định nghĩa rollback thành "nhánh riêng cho mỗi phase".
  - Tách đăng ký entry thành hàm mở rộng `EntryProviderScope` trong file riêng của từng tính năng để giảm sửa chung một file.

---

## Flow Tracer results

1. **Process death → khôi phục Reader và vị trí cuộn Reader: FAILED.** `ReaderKey` là màn SinglePane, nên `WebViewHost` không được compose (`R-build:209`, `phase-04-browser-engine.md:130`). Khi đó `extractPage` → `null` → `Unavailable` → `navigator.back()` (`phase-10-reader-view.md:81`).
2. **Process death → khôi phục sheet 06 và chat: FAILED (một phần).** Chat lưu trong Room được đọc lại, nhưng tài liệu được trích lại trong lúc trang chưa tải xong (`phase-11-skim-ai-core.md:33`, `:112`, `phase-04-browser-engine.md:90`). Khi Keep chat history tắt, tóm tắt tự chạy mà người dùng không bấm gì (`phase-11-skim-ai-core.md:51`, `:136`).
3. **Đóng tab → snackbar Undo → hết giờ → `finalizeClose` → `IncognitoSession.end()`: VERIFIED.** Chuỗi gọi được xây đúng nhân quả: `phase-09-tabs.md:60` → `phase-02-design-system-components.md:73` (`onDismissed` khi hết giờ, khi bị vuốt hoặc bị thay) → `phase-04-browser-engine.md:133` → `:186` (test thứ tự). Có cả đường dự phòng `finalizePendingCloses` khi Activity `isFinishing` (`phase-04-browser-engine.md:118`, `:184`).
4. **"Last incognito tab closes → profile deleted: cookies, cache, history": FAILED.** Cache chỉ được xử lý khi có `HTTP_CACHE_MANAGER`, và profile không unload được (`phase-04-browser-engine.md:41`, `:173`; `R-web:52-53`; `NAV:247`).
5. **FLAG_SECURE bật trước frame incognito đầu tiên: VERIFIED.** `SkimNavigator` bật cờ trước khi `select` hoặc đổi back stack (`phase-05-app-shell-navigation-onboarding.md:31`, `:77`), và `SkimNavigatorTest` kiểm thứ tự gọi (`:149`). Popup từ tab incognito đi thẳng qua `TabManager` (`phase-04-browser-engine.md:176`), nhưng lúc đó cờ đã bật sẵn vì tab mẹ là incognito.
6. **Không có request AI nào từ tab incognito: VERIFIED.** Một chốt duy nhất trong `SkimAiService.send` (`phase-11-skim-ai-core.md:34`, `:83`, `:143`). Phase 12 đưa mọi tab nguồn qua cùng chốt này (`phase-12-skim-ai-across-tabs-history.md:78`).
7. **Link VIEW từ app khác → tab mới → Back đóng tab và trả về app gọi: VERIFIED cho lần nhận đầu, FAILED khi link bị phát lại** (`replay = 1`, `phase-05-app-shell-navigation-onboarding.md:142`, `:91`).
8. **`Navigated` → D4 bỏ chat: VERIFIED có liên kết, nhưng kích hoạt cả khi không nên.** Sự kiện phát cho cả điều hướng cùng document, và không có xử lý cho stream đang chạy (`phase-04-browser-engine.md:131`, `phase-11-skim-ai-core.md:96`).
9. **Mạng trở lại → reload các tab NoInternet: PARTIAL.** Có liên kết (`phase-04-browser-engine.md:178`), nhưng phân loại lệ thuộc VALIDATED (`R-web:112`) và không định nghĩa cho tab không có WebView sống (`phase-04-browser-engine.md:132`).
10. **Renderer gone → khôi phục: VERIFIED có liên kết, nhưng không có ngân sách thử lại** (`phase-04-browser-engine.md:175`, `:231`). Kết hợp với digest tự động (`phase-12-skim-ai-across-tabs-history.md:51`), luồng này có thể lặp vô hạn.
11. **Delete all AI data → xoá chat + quyền AI về `UNSET` + gỡ `chatId` của tab + xoá phiên trong bộ nhớ: VERIFIED** (`phase-08-settings-privacy-history-bookmarks.md:66-68`, `phase-03-data-layer.md:101`, `phase-11-skim-ai-core.md:96`). **Thứ tự với Undo đang chờ: FAILED** (Finding 8).
12. **AI FAB → `AiGate` → 06d → 06: VERIFIED** (`phase-11-skim-ai-core.md:41`, `:112`, `:124`). Riêng `AskPageKey` khôi phục sau process death bỏ qua `AiGate`; điều này chấp nhận được vì quyền đã được lưu.
13. **Gom nhóm → banner → Undo khôi phục snapshot: VERIFIED có đường đi, nhưng FAILED về xử lý tương tranh** (`phase-12-skim-ai-across-tabs-history.md:39`, `:82`; Finding 9).
14. **Rollback của từng phase = revert commit: FAILED.** Plan không tạo commit nào (`plan.md:68` mâu thuẫn với `plan.md:111`).

## Câu hỏi chưa giải quyết

1. Khi renderer crash nhiều lần, có cần một trạng thái "trang bị lỗi" (copy D8 mới) thay cho việc tự tải lại không?
2. Có chấp nhận coi incognito là không hỗ trợ trên WebView cũ hơn M151 (không có `HTTP_CACHE_MANAGER`) để giữ đúng lời hứa "cache deleted" không?
3. Có được thêm checkpoint commit cho từng phase không, hay rollback nên được định nghĩa theo cách khác?
4. Có được dùng AVD API 36 sẵn có và cài thêm image API 28 làm cổng kiểm chứng cho phase 4 không?

Status: DONE
Summary: Tìm thấy 10 lỗi (1 Critical, 8 High, 1 Medium), mỗi lỗi có trích dẫn file:line. Lỗi nặng nhất: Reader và 06 khôi phục sau process death khi không có WebView, link ngoài bị phát lại do `replay = 1`, renderer crash lặp vô hạn, evict theo trim làm hỏng popup đăng nhập, và Undo chạy đua với Delete all.
