# Red team: phạm vi và độ phức tạp (Scope & Complexity Critic + Contract Verifier)

Plan: `plans/261020-1611-skim-design-implementation/` (plan.md, coverage-matrix.md, phase-01 … phase-13). Phạm vi được yêu cầu là toàn bộ thiết kế trong `design/` cộng README và `docs/`, nên các tính năng có trong thiết kế không bị coi là thừa. Những gì dưới đây vượt ra ngoài thiết kế hoặc ngoài yêu cầu, hoặc là chỗ hợp đồng giữa các phase không khớp nhau. Mọi trích dẫn đã được kiểm bằng grep trên repo ngày 2026-10-21.

## Finding 1: Hai đường ống parity ảnh cho cùng một mục đích (mỗi phase một lần, phase 13 thêm một lần)
- **Severity:** High
- **Location:** Phase 13, các mục "Architecture › Parity", "Implementation steps" 1–3; Phase 2/5–12, mục "Implementation steps" (ảnh Roborazzi "duyệt so với PNG")
- **Flaw:** Mỗi phase màn hình đã chụp golden Roborazzi bằng fixture "giống board" và có người duyệt so với PNG. Phase 13 lại dựng cả một hệ thống song song: `DesignParityTest` gọi lại mọi composable với một bộ fixture thứ hai (`ParityFixtures`), task Gradle `prepareDesignParity` sync PNG, rồi chạy `compareRoborazziDebug`. Chính plan thừa nhận diff không bao giờ bằng 0 và quyết định là của người duyệt, nên bước compare tự động không thêm được gì so với việc mở hai ảnh cạnh nhau. Kiểm tra vùng chạm 48 và ảnh reduced motion cũng bị làm hai lần: `TouchTargetTest` và `ContentScreenshotTest` ở phase 2, rồi `A11yAuditTest` và `ReducedMotionStillsTest` ở phase 13.
- **Failure scenario:** Khoảng 25 board/state và 15 overlay phải dựng fixture hai lần (fixture riêng của từng phase và `ParityFixtures`). Khi hai bộ fixture lệch nhau, golden vẫn xanh còn ảnh parity đỏ, hoặc ngược lại, và người duyệt phải xem lại toàn bộ lần thứ hai trong 24h của phase 13. Bước compare lại phụ thuộc hành vi `[UNVERIFIED]` của Roborazzi và đã có sẵn phương án dự phòng là ImageMagick, tức là có thêm một công cụ thứ ba.
- **Evidence:**
  - `plan.md:105-106`: "Golden component/màn hình | Roborazzi … | 2, 5–12" và "Parity với PNG … | Roborazzi compare + duyệt người … | 13"
  - Duyệt so với PNG ở từng phase: `phase-05:155`, `phase-06:120,140`, `phase-07:158`, `phase-08:108`, `phase-09:91`, `phase-10:87`, `phase-11:147`, `phase-12:112` ("fixture giống board … duyệt so với PNG")
  - Phase 13 làm lại: `phase-13:53` ("`DesignParityTest` … gọi các composable stateless của từng màn với `ParityFixtures`"), `:54` ("`[UNVERIFIED: tên file output của chế độ compare …]`"), `:28` ("Diff pixel không bao giờ bằng 0 … quyết định khớp là của người duyệt"), `:139` (dự phòng "ImageMagick `compare`")
  - Trùng nhau: `phase-02:178` ("reduced motion cho progress"), `:180`/`:199` (`TouchTargetTest` … `touchBoundsInRoot` ≥ 48dp) so với `phase-13:84` (`A11yAuditTest` … `touchBoundsInRoot` ≥ 48dp) và `:85` (`ReducedMotionStillsTest`)
- **Suggested fix:** Coi golden của từng phase là ảnh parity. Ghi kết quả duyệt và các lệch vào `docs/design-guidelines.md` ngay ở phase đó. Bỏ `DesignParityTest`, `ParityFixtures`, `prepareDesignParity` và `ReducedMotionStillsTest`. Phase 13 chỉ gom kết quả và chạy `verifyRoborazziDebug`. `A11yAuditTest` nên là một helper assert semantics được gọi bên trong các screenshot test đã có, không cần bộ fixture riêng.

## Finding 2: D5 dựng cả một đường tải xuống thứ hai (blob:/data:) mà thiết kế không có, trong lúc vẫn "chờ validate"
- **Severity:** High
- **Location:** Phase 4, Requirements (D5), Implementation step 10, Asset `skim-blob-fetch.js`; Phase 7, `ShareImage`
- **Flaw:** Thiết kế chỉ nói tới DownloadManager. Researcher B đã nêu cách rẻ hơn ("Báo lỗi") và để việc hỗ trợ blob/data là câu hỏi mở. Plan vẫn chọn hiện thực đầy đủ ngay ở phase 4: `MediaStoreDownloads` (tách nhánh API 26–28 và 29+), `DataUrls`, `BlobBridge` với nonce, kiểm `sourceOrigin` và giới hạn 25 MB, một script JS mới, và `addWebMessageListener` vốn còn `[UNVERIFIED]`. Sau đó phase 7 còn kéo `BlobBridge` vào `ShareImage`.
- **Failure scenario:** Nếu D5 không được duyệt, khoảng 5 file, 1 asset JS và các test liên quan thành công sức bỏ đi, và hợp đồng `ShareImage` của phase 7 phải viết lại. Nếu được duyệt, một blob 25 MB đi qua kênh message dưới dạng base64 (khoảng 33 MB chuỗi) cùng với mảng byte giải mã, gây đỉnh bộ nhớ ngay trên thiết bị đang giữ 3 WebView. D8 cũng phải thêm copy `snackbar_download_failed` chủ yếu cho nhánh này.
- **Evidence:**
  - `design/spec/navigation-map.md:103` ("Download starts | DownloadManager, then a snackbar"), `:140`, `:316` ("Downloads | DownloadManager with its notification")
  - `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md:97` ("Chỉ hỗ trợ http/https, không hỗ trợ `blob:` hay `data:`"), `:434` ("Báo lỗi; hoặc làm cầu nối JS"), `:451` (câu hỏi mở 6)
  - `plan.md:39` (D5), `phase-04:45`, `:140`, `:147` (`skim-blob-fetch.js`), `:180` ("`BlobBridge` … nonce … giới hạn 25 MB"), `:235` ("`addWebMessageListener` … `[UNVERIFIED]`")
  - `phase-07:50`, `:80` (`ShareImage(httpClient, blobBridge, cacheDir)`)
- **Suggested fix:** Ở phase 4 chỉ làm DownloadManager cho http/https. Với `blob:`/`data:` thì hiện `snackbar_download_failed`. Ghi D5 vào `docs/project-roadmap.md` và chỉ làm sau khi được duyệt. `ShareImage` chỉ cần OkHttp cùng cookie của profile, có thể thêm `data:` vì giải mã thuần không cần bridge.

## Finding 3: `PageDigestCache` là một pipeline nền tự đặt ra, trùng với lần quét readerable mỗi lần tải trang và đụng tới copy quyền riêng tư
- **Severity:** High
- **Location:** Phase 12, Key insights (digest), Architecture `ai/PageDigestCache.kt`, Risk assessment; Phase 4, Requirements (readerable check sau mỗi lần tải)
- **Flaw:** 06b chỉ yêu cầu "Reads: All open tabs". Planner tự đặt thêm cơ chế: sau mỗi `PageFinished` của mọi tab thường thì chạy `extractPage` (Readability đầy đủ cộng extractor, tối đa 400 block/100k ký tự) rồi giữ text trong bộ nhớ, dù người dùng chưa hề mở 06b. Mỗi lần tải trang như thế có tới hai lần inject JS, vì phase 4 đã chạy `Readability-readerable` sau mỗi lần tải. Ngay trong plan, câu hỏi mở 3 vẫn chưa trả lời liệu cách này có hợp với copy "Skim reads a page only when you ask" không, và plan cũng đã có sẵn phương án thay rẻ hơn.
- **Failure scenario:** Mọi trang đều phải trả chi phí CPU và pin để parse thêm một lần, 2 giây sau khi tải, cho một tính năng hiếm khi dùng. Chính phase 12 đã ghi rủi ro "giật khi cuộn ngay sau tải". Nếu câu hỏi 3 được trả lời là "chỉ title + host", thì `PageDigestCache`, `PageDigestCacheTest` và phần nối dây đều thành code chết.
- **Evidence:**
  - `phase-12:28` ("Quyết định nhỏ của planner: giữ 'digest' trong bộ nhớ … Cần xác nhận về mặt quyền riêng tư")
  - `phase-12:51` ("nghe pageEvents PageFinished (tab thường), debounce 2 s, extractPage"), `:145` ("Chạy Readability sau mỗi lần tải trang … Giật khi cuộn")
  - `phase-04:49` ("kiểm tra `isProbablyReaderable` sau mỗi lần tải xong")
  - `plan.md:124` (câu hỏi 3, có "Phương án thay: tab không sống chỉ gửi title + host")
  - `plans/reports/scout-261020-2319-design-inventory.md:94` (`setup_privacy_note`: "Skim reads a page only when you ask…")
  - `design/spec/navigation-map.md:182` ("Reads | All open tabs, never incognito ones")
- **Suggested fix:** Câu hỏi đặt ra là có cần giữ text của các tab không còn sống hay không. Trong lúc chờ câu trả lời, làm 06b theo cách rẻ: trích mới các tab còn sống lúc người dùng hỏi, các tab khác chỉ gửi title + host. Nếu sau này vẫn cần digest, hãy gộp vào một lần trích duy nhất mỗi lần tải trang (`extractPage` trả luôn `readerable`) thay vì hai lần inject.

## Finding 4: D6 `X-Skim-Install` là hạ tầng cho một proxy nằm ngoài phạm vi, và tạo ra một định danh bền vững không có trong thiết kế
- **Severity:** Medium
- **Location:** plan.md D6; Phase 3, `SettingsRepository.installId()`; Phase 11, `ClaudeClientProvider`, `RequestShapeTest`; Phase 13, docs hợp đồng proxy
- **Flaw:** Plan.md liệt kê "server proxy release" là non-goal. Researcher còn để mở việc proxy xác thực app bằng install token hay Play Integrity, mà Play Integrity cũng là non-goal. Plan vẫn hiện thực một UUID bền vững theo lần cài, gửi kèm mọi request AI ở release, có test khẳng định header và có tài liệu hợp đồng proxy. Thiết kế không có chỗ nào nói tới định danh như vậy.
- **Failure scenario:** Khi proxy thật được thiết kế và chọn Play Integrity hoặc một token ký số, phần lưu `installId` trong DataStore, header, `RequestShapeTest` và docs đều phải gỡ ra. Trong lúc đó, bản release gửi một định danh ổn định cùng text trang, một hành vi riêng tư không có trong copy 03 ("Page text is sent encrypted and deleted after each answer").
- **Evidence:**
  - `plan.md:23` (Non-goals: "server proxy release; Play Integrity"), `plan.md:40` (D6)
  - `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md:452` (câu hỏi 7: "install token trong `X-Skim-Install`, hay Play Integrity?")
  - `phase-03:132` (`installId()` "gửi kèm request proxy"), `phase-11:40`, `:65`, `:130`, `:141` ("chế độ proxy … có `X-Skim-Install`"), `phase-13:92` ("header `X-Skim-Install` D6")
  - grep `install|identifier|proxy` trong `design/README.md`, `design/spec/*.md`: không có kết quả
- **Suggested fix:** Bỏ D6 khỏi plan này. Client release chỉ đổi `baseUrl` và không gửi key. Khi proxy được thiết kế, cơ chế xác thực sẽ thêm vào `ClaudeClientProvider`, vì nó vốn đã có một điểm duy nhất để chèn header.

## Finding 5: Hỗ trợ nhiều cửa sổ (`onCreateWindow`, popup thành tab) là hành vi tự đặt ra, không có board và không được gắn nhãn default
- **Severity:** Medium
- **Location:** Phase 4, Overview ("nhiều cửa sổ"), Requirements, Implementation steps 6–7
- **Flaw:** Thiết kế không có hàng nào cho `target=_blank`/`window.open`. README liệt kê "Dialogs opened by web pages" là chưa thiết kế. Phase 4 vẫn bật `setSupportMultipleWindows(true)` và tự đặt luồng: popup thành tab mới, `obtainForPopup` bỏ qua `obtain`, gán `WebViewTransport`, tự chọn tab mới, và `onCloseWindow` thì đóng tab. Hành vi này không được gắn `[DEFAULT — chờ validate]` như D1–D8, cũng không có hàng trong coverage-matrix.
- **Failure scenario:** WebView popup chưa từng `loadUrl` sẽ có `url = null`. Nếu nó bị đẩy khỏi LRU trước khi trang tải xong, `restoreState ?: loadUrl(url)` sẽ không có gì để tải. Tab mở bằng `onCreateWindow` còn đi một nhánh riêng về `setProfile` cho incognito, phải kiểm thêm. Người dùng bị chuyển sang tab mới mà không có snackbar (thiết kế chỉ vẽ "Opened in a new tab › Switch" cho 05d). Đổi lại, tắt nhiều cửa sổ có thể làm hỏng luồng đăng nhập OAuth dạng popup trên một số site. Đây là đánh đổi cần được nêu ra, không nên âm thầm đưa vào phạm vi.
- **Evidence:**
  - `phase-04:23` ("… nhiều cửa sổ, thumbnail …"), `:48` ("nhiều cửa sổ (`onCreateWindow` chỉ khi có gesture)"), `:176` (`obtainForPopup`, `WebViewTransport`, `onCloseWindow` → đóng tab), `:177` (`setSupportMultipleWindows(true)`)
  - `design/spec/navigation-map.md:89-105` (bảng 05 không có hàng popup/new window), `design/README.md:71` ("Dialogs opened by web pages … not designed")
  - `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md:103` (chỉ là gợi ý kỹ thuật)
- **Suggested fix:** Gắn nhãn D9 `[DEFAULT — chờ validate]`, nêu đánh đổi OAuth với độ phức tạp, và thêm một hàng "Popup/target=_blank" vào coverage-matrix. Nếu không có ai duyệt, giữ mặc định của WebView (`setSupportMultipleWindows(false)`, link mở ngay trong tab).

## Finding 6: Registry nhiều host snackbar chỉ phục vụ một trường hợp (Copy trong 06/06b trên Android ≤ 12)
- **Severity:** Medium
- **Location:** Phase 2, Key insights, Architecture `SnackbarController` (`registerHost/unregisterHost/topHost`, `SnackbarPlacement.InSheet`), `SkimBottomSheet`
- **Flaw:** Mọi `SkimBottomSheet` đều gắn một `SkimSnackbarHost(InSheet)` đăng ký vào một stack host toàn cục. Theo chính plan, hành động trong 05b và 05d đóng sheet trước rồi mới hiện snackbar trên host chính. Snackbar duy nhất thực sự phải nằm trong sheet là "Copied to clipboard" khi bấm Copy summary hoặc Copy answer, và nó chỉ hiện trên SDK ≤ 32.
- **Failure scenario:** Năm sheet (05b, 05d, Site info, 06, 06b) đều mang thêm host và phải đăng ký, gỡ đăng ký. Khi sheet chạy animation đóng, `onDispose` gỡ host trễ hơn lúc `topHost` đã chuyển, nên snackbar có thể hiện trong cửa sổ sheet đang biến mất rồi mất theo nó. `SnackbarControllerTest` phải phủ cả logic stack này, tất cả cho một xác nhận copy trên API 26–32.
- **Evidence:**
  - `phase-02:35` ("Snackbar từ sheet (Copy summary ở 06) … host snackbar phải có ở cả cửa sổ chính lẫn trong sheet"), `:79` (`registerHost(): Int; unregisterHost(id: Int); val topHost`), `:86` (`InSheet`), `:133` ("bên trong có `SkimSnackbarHost(InSheet)`")
  - `phase-07:34` ("hành động trong 05b/05d đóng sheet trước rồi mới hiện snackbar (host của cửa sổ chính)")
  - `phase-11:43` ("Copy summary (clipboard; snackbar chỉ SDK ≤ 32)"), `design/spec/navigation-map.md:136`, `design/spec/dialogs.md:97`
- **Suggested fix:** Giữ `SnackbarController` với một host duy nhất. Để `AskPageSheet` và `AskTabsSheet` tự giữ một `SnackbarHostState` cục bộ cho riêng xác nhận copy khi SDK ≤ 32. Bỏ `registerHost/topHost/InSheet` khỏi component dùng chung.

## Finding 7: Smoke test release dựng thêm một fake server bằng Python, trùng với `FakeClaudeServer` của phase 11
- **Severity:** Medium
- **Location:** Phase 13, Architecture "Smoke test release", Related code files, Verification
- **Flaw:** Phase 11 đã có `FakeClaudeServer` (MockWebServer) cùng các fixture `.sse`, và phase 1 đã thêm `mockwebserver` cho `androidTestImplementation`. Phase 13 vẫn viết thêm `scripts/fake-claude-sse.py` làm cùng việc là phát lại SSE, thêm build type `releaseSmoke` và quy trình tay gồm `adb reverse`, chạy server nền, rồi grep logcat. Tiêu chí đạt được người quan sát bằng mắt ("log server thấy client ngắt"), không phải assertion.
- **Failure scenario:** Hai bộ phát lại SSE ở hai ngôn ngữ dễ lệch định dạng event với nhau. Smoke test không chạy được trong CI. Thêm một tiến trình nền phải tự theo dõi và dừng (đúng loại rủi ro quy tắc quản lý tiến trình muốn tránh). Hồi quy R8 chỉ bị phát hiện khi có người chạy tay đủ các bước.
- **Evidence:**
  - `phase-13:57-60` (`releaseSmoke`, `scripts/fake-claude-sse.py`, `adb reverse`), `:73`, `:126-129`, `:111` (tiêu chí bằng log)
  - `phase-11:119` (`FakeClaudeServer.kt` (MockWebServer phát SSE từ file fixture …)), `:120` (fixture `.sse`)
  - `phase-01:102` (`mockwebserver:4.12.0` (`testImplementation` và `androidTestImplementation`))
  - `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md:424` ("Instrumented test chạy bản release với fake SSE server")
- **Suggested fix:** Chạy một instrumented test trên biến thể đã qua R8 (`testBuildType = "releaseSmoke"`), dùng MockWebServer ngay trên thiết bị với chính các fixture `.sse` của phase 11 và assert event bằng code. Bỏ script Python và các bước `adb reverse` làm tay.

## Finding 8: Các đối tượng sống theo Activity (bridge quyền, download, blob, cleaner) không có đường nào tới consumer ở phase 7–8
- **Severity:** High
- **Location:** Phase 4, mục "Sửa › MainActivity", "Next steps"; Phase 7, Architecture (`ShareImage`, `BrowsingDataCleaner`, `SitePermissionDialog`), mục "Sửa › AppContainer"; Phase 8, `BrowsingDataCleaner.clearAll`
- **Flaw:** Phase 4 tạo `SitePermissionBridge` trong `MainActivity` và chỉ cung cấp duy nhất `LocalWebViewPool`. `DownloadRouter` cần `ActivityHost`, còn `BlobBridge` được cài vào từng WebView. Phase 7 thì đọc `bridge.pending`, gọi `DownloadRouter.start(...)` từ composable, và đưa `ShareImage(httpClient, blobBridge, cacheDir)` vào `AppContainer` (sống theo process), trong khi chưa phase nào bind `blobBridge` hay `downloadRouter` vào container. Phase 7 khai báo `class BrowsingDataCleaner` không có dependency nào, nhưng phase 8 cho `clearAll` gọi `HistoryRepository` và `WebView.clearCache(true)` "trên một WebView sống của pool, hoặc một WebView tạm bằng context Activity", tức là một object sống theo process phải với tới pool sống theo Activity.
- **Failure scenario:** Người làm phase 7 buộc phải tự đặt ra CompositionLocal hoặc nhét object sống theo Activity vào `AppContainer`. Cách sau làm rò Activity: bridge giữ launcher của Activity cũ, mà `configChanges` không chặn được trường hợp Activity bị huỷ khi người dùng thoát app. Quyết định quyền của incognito "nhớ phiên" nằm trong bridge sống theo Activity, trong khi tab incognito sống trong `TabManager` sống theo process. Activity bị huỷ rồi tạo lại thì quyền mất nhưng profile và tab vẫn còn.
- **Evidence:**
  - `phase-04:152` ("tạo `WebViewPool`, `WindowSecureController`, `FullscreenController`, `SitePermissionBridge` … cung cấp `LocalWebViewPool`"), `:150` (AppContainer phase 4: `networkMonitor, incognitoSession, tabStateStore, thumbnails, downloadTracker, tabManager`, không có router/bridge), `:177` (`BlobBridge.install(webView)`), `:180` (`DownloadRouter` … "xin `WRITE_EXTERNAL_STORAGE` qua `ActivityHost`"), `:181` ("nhớ phiên (incognito)"), `:253`
  - `phase-07:80` (`ShareImage(httpClient: OkHttpClient, blobBridge: BlobBridge, …)`), `:82` (`class BrowsingDataCleaner { fun clearSite(…) }`), `:85` ("khi `bridge.pending != null`"), `:119` (AppContainer thêm `shareImage`, `browsingDataCleaner`), `:128` (`DownloadRouter.start(...)`)
  - `phase-08:71-74` (`clearAll` → `HistoryRepository.clearAll()` … "`WebView.clearCache(true)` trên một WebView sống của pool, hoặc một WebView tạm bằng context Activity")
  - grep `Local[A-Z]` trên plan: chỉ có `LocalWebViewPool`, `LocalContext`, `LocalConfiguration`, `LocalContentColor`
- **Suggested fix:** Phase 4 định nghĩa một `LocalBrowserHost` (bridge, download router, activity host) đi cùng `LocalWebViewPool`, và chuyển quyền incognito theo phiên vào `IncognitoSession` (sống theo process). Bỏ tham số `BlobBridge` khỏi `ShareImage` (Finding 2). Cho `BrowsingDataCleaner` constructor tường minh (`HistoryRepository`, `TabManager`/`TabEngine` để clear cache) và liệt kê nó trong mục sửa `AppContainer` của phase 7.

## Finding 9: Chữ ký bị trôi giữa producer và consumer, và chốt incognito không có dữ liệu để chạy
- **Severity:** High
- **Location:** Phase 11, Architecture (`SkimAiService`, `ChatSession`); Phase 5, `SkimApp`; Phase 11/12, mục "Sửa"; Phase 13, `RestoreAcrossContainersTest`; Phase 9, `TabsUiState`
- **Flaw:**
  1. Chốt incognito duy nhất là `send(params, sources: List<Tab>)`. Nhưng `summarize(session)` và `ask(session, q)` chỉ nhận `ChatSession`, vốn chỉ có `tabId` và không có cờ incognito, còn `SkimAiService(clients)` không có `TabManager` để tra `Tab`. Vì vậy chốt chỉ chạy được nếu caller tự đưa vào đúng danh sách tab.
  2. `tabs.newTab(url, openedByExternalApp = true)` (phase 5) không khớp với `newTab(incognito: Boolean, url: String? = null, …)` (phase 4): `url` rơi vào vị trí của `incognito`.
  3. Phase 11 thêm `aiGate` và `scope` vào constructor `SkimNavigator`, phase 12 thêm `TabGrouping` vào `TabsViewModel`, nhưng không phase nào liệt kê việc sửa `SkimNavigatorTest` hay `TabsViewModelTest` theo.
  4. `RestoreAcrossContainersTest` cần dựng `AppContainer` trên DB và DataStore ở thư mục tạm rồi "đóng container", trong khi phase 3 hardcode `"skim.db"` và `preferencesDataStoreFile("skim_settings")`, và `AppContainer` không có `close()`.
  5. `TabManager.captureThumbnail` được tạo ra mà không có consumer nào.
  6. `hasSkimChat = tab.chatId != null` bỏ qua quy tắc của phase 3 "id không còn trong `skim_chats` coi như không có chat".
- **Failure scenario:** Điểm (1): `IncognitoGuardTest` chỉ thử `send` trực tiếp, nên một đường `ask` với tab incognito có thể vẫn đi qua được, đúng loại hồi quy mà plan cam kết chặn. Điểm (2) và (3): compile đỏ ở phase 5, 11 và 12. Điểm (4): DataStore ném `IllegalStateException` khi có hai instance cùng file trong một process, nên test không viết được nếu không sửa `AppContainer`. Điểm (6): khi Keep chat history tắt, chat dùng id âm và chỉ sống trong bộ nhớ. Sau process death, `TabEntity.chatId` âm vẫn được khôi phục từ Room, nên badge Sunny vẫn hiện dù chat không còn.
- **Evidence:**
  - (1) `phase-11:81-84` (`class SkimAiService(clients: ClaudeClientProvider) { fun summarize(session: ChatSession) … internal fun send(params, sources: List<Tab>)`), `:95` (`ChatSession`: `chatId`, `tabId`, `pageUrl`, … không có Tab/incognito), `:143` (`IncognitoGuardTest`: "`send` với tab incognito ném …"), `plan.md:27` ("một chốt incognito")
  - (2) `phase-04:111` vs `phase-05:91`
  - (3) `phase-05:64-65`, `:137`, `:149` vs `phase-11:122-126` (không có `SkimNavigatorTest`); `phase-09:52`, `:78` vs `phase-12:82`, `:92`, `:98` (không có `TabsViewModelTest`)
  - (4) `phase-13:63` vs `phase-03:153`, `phase-01:95` (`AppContainer(this)`)
  - (5) `phase-04:123`, `:170`, `:182` ("khi `captureThumbnail` được gọi trước khi mở 09"); không có trong `phase-05:69` (`openTabs`) và `phase-09:90` (`TabsRoute`)
  - (6) `phase-09:86` vs `phase-03:51`, `phase-11:49` ("id âm, không sống qua process death")
- **Suggested fix:** Cho `ChatSession` mang `sources: List<Tab>` (hoặc `incognito: Boolean`) và để `summarize`/`ask` gọi `send(params, session.sources)`. `IncognitoGuardTest` phải thử cả `summarize`, `ask`, `overviewTabs` và `groupTabs`. Sửa lời gọi `newTab(incognito = false, url = …)`. Thêm `SkimNavigatorTest` và `TabsViewModelTest` vào mục sửa của phase 11 và 12. Cho `AppContainer` nhận thư mục, có `close()`, hoặc viết test ở mức repository thay vì cả container. Bỏ `captureThumbnail` hoặc gọi nó trong `openTabs`. Không lưu `chatId` âm vào Room.

## Finding 10: Coverage matrix giao một số dòng cho phase không thể hiện thực hay kiểm chứng chúng
- **Severity:** Medium
- **Location:** coverage-matrix.md mục B và E
- **Flaw:** Matrix định nghĩa Owner là "phase làm cho mục đó chạy end-to-end và kiểm chứng nó". Vậy mà các rule "Sheets and menus close on swipe down, scrim tap, Back", "Snackbars … 8 above toolbar" và "Floating toolbar (04, 05, 05c, 06d, 08, 09c, 11)" lại giao cho phase 2. Phase 2 chỉ là component cô lập, không đụng NavDisplay. Việc Back đóng sheet nằm ở `BottomSheetSceneStrategy` của phase 5, còn toolbar trên 06d đã có dòng riêng giao cho phase 11. "Removed from history (13 và 04c)" giao cho phase 8, nhưng 04c tới phase 12 mới xây. "Copied to clipboard" giao cho phase 7, nhưng nguồn copy summary và copy answer thuộc phase 11 và 12, và còn phụ thuộc vào host trong sheet (Finding 6).
- **Failure scenario:** Phase 2 đánh dấu xong các rule này chỉ dựa vào golden component. Còn hành vi thật, như Back đóng sheet key, snackbar nằm đúng 8 dp trên toolbar ở từng màn, hay snackbar Undo ở 04c, thì không có phase nào chịu trách nhiệm kiểm chứng cho tới khi phase 13 làm vét. Toolbar trên 06d vì thế có hai owner.
- **Evidence:**
  - `coverage-matrix.md:3` (định nghĩa Owner, "Mỗi mục có đúng một owner"), `:49`, `:52`, `:54` (owner 2), `:177` ("06d | Floating toolbar | 11"), `:274` ("Removed from history (Undo; 13 và 04c) | 8"), `:281` ("Copied to clipboard … | 7")
  - `phase-02:185` ("Không sửa file nào ngoài các file trên"); `phase-05:79`, `:148` ("`OverlayScene` gọi `onBack` khi sheet đóng"); `phase-12:40` (04c Remove + snackbar Undo); `design/spec/dialogs.md:99` (Copy … "copy summary or answer (06, 06b)")
- **Suggested fix:** Chuyển rule sheet và snackbar sang phase 5 (scene strategy và host đặt theo `placement`). Tách các dòng nhiều bề mặt theo màn: "Removed from history" thành 13 → 8 và 04c → 12, "Copied to clipboard" thành 05d → 7 và 06/06b → 11/12. Sửa dòng "Floating toolbar" thành "component 2; 06d 11; các màn khác theo owner của màn".

## Contract Verifier results

| Hợp đồng | Producer | Consumer (file:line) | Kết quả |
|---|---|---|---|
| Bộ `SkimKey` (27 key) | `phase-05:38`, `:54-60` | `phase-06:106`; `phase-07:117`; `phase-08:78`; `phase-09:82`; `phase-10:76`; `phase-11:126`; `phase-12:99` | Tên đủ 27/27. Lệch: tham số của `EditBookmarkKey(id)` (`phase-07:92`), `RenameGroupKey`, `MoveToFolderKey` không được định nghĩa ở `phase-05:38`. Ô Folder trong Edit bookmark dùng `FolderChoiceDialog` dạng UI state (`phase-07:75`, `:131`), song song với `MoveToFolderKey`, tức là có hai đường cho cùng một dialog. |
| API `SkimNavigator` | `phase-05:64-74` | `phase-06:37,118`; `phase-07:88,92,128`; `phase-08:77`; `phase-09:61,90`; `phase-10:85`; `phase-11:124`; `phase-12:40,80` | Mọi hàm được gọi đều có. Lệch: constructor đổi ở `phase-11:124-125` mà không sửa `SkimNavigatorTest` (`phase-05:137`). |
| Binding trong `AppContainer` | `phase-01:95`, `phase-03:153`, `phase-04:150`, `phase-05:142`, `phase-06:107`, `phase-07:119`, `phase-08:94`, `phase-10:77`, `phase-11:123`, `phase-12:100` | `phase-07:80,118`; `phase-13:63` | Lệch: `shareImage` cần `blobBridge` mà không phase nào bind; `downloadRouter` và `sitePermissionBridge` chỉ sống theo Activity; `browsingDataCleaner` thiếu dependency; container không nhận thư mục và không có `close()` cho `RestoreAcrossContainersTest` (Finding 8, 9). |
| Repository (`Tab`, `History`, `Bookmark`, `Shortcut`, `Chat`, `SitePermission`, `Settings`) | `phase-03:66-133` | `phase-04:104`; `phase-05:114`; `phase-06:64,76`; `phase-07:65,129,131`; `phase-08:56,61,63,66`; `phase-09:52`; `phase-11:86,92`; `phase-12:62,73` | Mọi hàm được gọi đều có. Lệch: `TabsViewModel` nhận `tabRepo` nhưng mọi thay đổi đi qua `tabs.applyLayout` (`phase-09:52` vs `:62`), nên có hai đường ghi layout. Có hai cơ chế cùng xoá chat trong bộ nhớ: `ChatRepository.cleared` (`phase-03:101`, `phase-11:96`) và `AiDataEraser` gọi `bindChat(null)` (`phase-08:67-68`). `hasSkimChat` lệch quy tắc `chatId` (`phase-09:86` vs `phase-03:51`). |
| API `TabManager`/`TabEngine` | `phase-04:104-125`, `:168-170` | `phase-05:91,102,103`; `phase-06:82`; `phase-07:87`; `phase-08:68,77`; `phase-09:60-62`; `phase-10:62,85`; `phase-11:96,112`; `phase-12:52,84` | Lệch: `newTab` truyền tham số sai vị trí (`phase-05:91`); `captureThumbnail` không có consumer. |
| API AI (`SkimAiService`, `ChatSessionStore`, `AiEvent`) | `phase-11:61-92`, mở rộng `phase-12:65-68` | `phase-12:70,78,84`; `phase-08:66` (gián tiếp qua `ChatRepository`) | Lệch nghiêm trọng: `summarize`/`ask` không có `List<Tab>` để chạy chốt `send` (Finding 9.1). `AiEvent.Final` và `parseTabsOverview` khớp nhau. |
| `AiFabMode`/`TabAiFab` | `phase-05:106-107` | `phase-06:81`; `phase-07:124`; `phase-10:83` | Khớp. |
| Tên trong `strings.xml` | scout §3(a)(b), D8 (`plan.md:46-57`), `phase-01:149` (`tabs_count_overflow`) | Mọi tên string đặt trong backtick ở phase 1–13 | Đã grep tự động: không có tên string nào được dùng mà thiếu định nghĩa. Các tên còn lại không có trong scout là tên bảng/khoá API (`skim_chats`, `stop_reason` …), không phải string. |

## Câu hỏi chưa giải quyết

1. Có cần text của các tab không còn sống cho 06b không (Finding 3), hay chỉ title + host là đủ cho "Reads: All open tabs"?
2. Hỗ trợ popup `window.open` (OAuth) có thuộc phạm vi bản đầu không (Finding 5)?
3. Proxy release sẽ xác thực bằng gì? Câu trả lời quyết định có cần `X-Skim-Install` hay không (Finding 4).
4. Gom nhóm tự động khi mở 09 (D3) gửi title + host mà người dùng không hề bấm gì. Hành vi này có hợp với copy "Skim reads a page only when you ask" không? Đây là tính năng có trong thiết kế (banner 09), nên chỉ nêu thành câu hỏi, không đề nghị cắt.

Status: DONE
Summary: 10 finding. Phần phạm vi thừa gồm parity ảnh làm hai lần, D5 blob/data, PageDigestCache, D6 install id, popup nhiều cửa sổ, registry host snackbar và fake server Python. Phần hợp đồng gồm đối tượng sống theo Activity không có đường tới consumer, chốt incognito thiếu dữ liệu Tab, chữ ký bị trôi và owner sai trong coverage matrix.
