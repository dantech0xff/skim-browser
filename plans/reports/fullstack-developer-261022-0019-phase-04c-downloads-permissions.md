# Báo cáo: lượt 4C của phase 4 (tải xuống đã gia cố, cầu nối quyền site, cầu nối blob, sửa review bảo mật)

## Kết quả

- Status: DONE_WITH_CONCERNS. Đủ 8 mục phạm vi gốc: `DownloadRouter`, `DownloadTracker` (+ cổng `SystemDownloads`/`AndroidSystemDownloads`), `InProcessDownloader`, `MediaStoreDownloads`, `MimeSniffer`, `BlobBridge` + `assets/js/skim-blob-fetch.js`, `SitePermissionBridge`, override quyền trong `SkimChromeClient`, `BrowserHost` mới, `AppContainer` (`httpClient`, `browserProfiles`, `downloadTracker`, đều lazy), `MainActivity` (receiver `ACTION_DOWNLOAD_COMPLETE`, `refresh()` ở `onStart`, huỷ ở `onDestroy`). Thêm hai phần sau review của controller: phần 1 (blob qua `evaluateJavascript`) và phần 2 (13 mục của review bảo mật). Không `git add`/commit; không sửa manifest, Gradle, `design/`.
- Toàn dự án 883 test xanh (829 của lượt gốc, 10 thêm ở phần 1, 44 thêm ở phần 2), 0 skip.
- Đột biến trên mã cuối: 131/131 bị bắt, 0 TIMEOUT, md5 từng file và cả cây khôi phục đúng. Gồm 55 đột biến mới cho phần 2, 8 đột biến cũ viết lại theo mã mới và 68 đột biến cũ chạy lại.
- Concern còn lại: những điều chỉ kiểm được trên thiết bị (xem "Việc cho 4D-2"), loại trừ autofill ở incognito [DEFAULT — chờ validate], và giới hạn cookie của DownloadManager khi redirect.

## Thay đổi sau review của controller

### Phần 1: cầu nối blob qua `evaluateJavascript` có thăm dò

- Lý do: Javadoc của `WebViewCompat.addWebMessageListener` ghi object chỉ được tiêm cho các lần điều hướng sau lời gọi; nguồn Chromium khớp (`JsCommunication::SetJsObjects` chỉ lưu, `JsBinding::Install` chỉ chạy ở `DidClearWindowObject`). Object thêm ngay trước khi đọc không bao giờ có trong tài liệu đang hiện, nên tải `blob:` và chia sẻ ảnh `blob:` của phase 7 không bao giờ thành công. Controller chốt: bỏ hẳn `addWebMessageListener`; API công khai (`fetch`, `destroy`, `BlobData`) và mọi nơi gọi không đổi.
- `skim-blob-fetch.js`: một hàm nhận một đối số JSON `{op, nonce, url?, origin?, maxBytes?, chunkChars?}`, Kotlin gọi `($script)(${escapeForScript(json)})`.
  - `start` kiểm `location.origin === origin` (sai thì `unavailable`), giữ trạng thái trong MỘT thuộc tính không enumerable `__skimBlobRead_<nonce>` (`Object.defineProperty`), `fetch(blob:)` → `blob()`, `size > maxBytes` → `error size` trước khi đọc, trả `{status:'started', origin, nonce}` ngay.
  - `take` trả `waiting`, `start{size,mime}`, `chunk{index,data}`, `end{chunks}`, `error{reason}` hoặc `gone`, luôn kèm nonce; lát kế tiếp chỉ được đọc sau khi Skim lấy lát trước (trang không bao giờ giữ quá một lát base64).
  - `drop` xoá thuộc tính.
  - Script trả object thuần để WebView tự chuyển sang JSON, không dùng `JSON.stringify` mà trang có thể ghi đè.
- `BlobBridge`: sau `start` hợp lệ (status, nonce và origin trả về đúng) chạy vòng `take`: có dữ liệu thì hỏi tiếp ngay, `waiting` thì `delay(25 ms)`, tổng ≤ 30 s. `BlobAssembly` (giữ kiểm nonce, bỏ phần `sourceOrigin`/`isMainFrame`) kiểm mọi câu trả lời như dữ liệu không đáng tin: JSON object, nonce, trần kích thước lúc `start` và khi cộng dồn, thứ tự, độ dài và base64 của chunk, `end` khớp số chunk và size; số nguyên nhận cả dạng `4`, `4.0`, `4e0` (WebView có thể ghi số là double); `error`/`gone`/`unavailable`/khác → thất bại ngay. Mutex một lần đọc một lúc. `drop` chạy fire-and-forget (`evaluateJavascript(script, null)`) trong `finally` ở mọi đường; `destroy()` drop ngay rồi huỷ coroutine đọc (cả khi WebView không còn trả lời).
- Cổng `WebMessaging` thu thành `ScriptRunner` (một hàm có kết quả, một hàm không callback), bỏ phụ thuộc `WEB_MESSAGE_LISTENER`, nên blob chạy cả trên WebView cũ. Sửa KDoc `BlobBridge`, `BrowserHost.destroy()`.
- Test: `BlobBridgeTest` viết lại (29 test; giữ hai test `blobOriginOf`), `FakeBlobPage` thay `FakeWebMessaging`. Fake trả lời sau một dispatch như `evaluateJavascript`, mô hình trạng thái trong trang từ `start` tới `drop`, và biến quá 5.000 lần `take` thành `AssertionError` để một vòng lặp hỏng làm test đỏ chứ không treo. Hai test blob của `DownloadRouterTest` chuyển sang fake mới.
- JS chạy thật:
  - Harness Node: 22/22.
  - Chrome 155 headless thật: 21/21 (`p4c-chrome/run.py` trong scratchpad, không trong repo). Trang ở `http://127.0.0.1:<port>` tự tạo blob và đóng vai Skim (`start` rồi vòng `take`, gọi script bằng indirect `eval`). Các kiểm: SHA-256 khớp cho blob 1 KB/4 chunk và 1,2 MB/4 chunk 512 Ki ký tự; blob rỗng; vượt `maxBytes` không đọc lát nào; origin sai → `unavailable`, không tạo thuộc tính; nonce lạ → `gone`; blob đã thu hồi → `error fetch`; thuộc tính không liệt kê được bằng `Object.keys`/`for…in`; `drop` xoá và lát đang đọc dở không tạo lại; không còn thuộc tính nào trên `window`.
  - Thay cho `--virtual-time-budget`, server cục bộ giữ một ảnh để sự kiện `load` chờ trang POST kết quả, nên không phụ thuộc thời gian ảo.
- Đột biến lại: 76/76 bị bắt, md5 khôi phục đúng. Gồm 19 đột biến Kotlin mới của blob (bỏ kiểm origin/nonce ở `start`, nonce ở `take`, trần lúc báo và khi cộng dồn, tin `end`, thứ tự, độ dài, base64 lỏng, bỏ nghỉ 25 ms, bỏ timeout, bỏ `drop` trong `finally`, không `drop` khi đã huỷ, `destroy` không drop, `destroy` không huỷ, nối đối số không escape, hai lần đọc cùng lúc, lần đọc chờ lượt chạy sau `destroy`, lỗi bị coi như đang chờ), 8 đột biến JS bị harness Node và Chrome bắt (bỏ kiểm origin, bỏ trần, đọc trước khi được lấy, `drop` giữ trạng thái, thuộc tính enumerable, nonce lạ trả `waiting`, câu trả lời mất nonce, hai lần đọc một nonce), và chạy lại 49 đột biến còn hiệu lực của D/T/I/S/N/P/W.
- Sự cố khi chạy: lượt thử đầu của đột biến "bỏ nghỉ 25 ms" làm `runTest` treo vĩnh viễn (vòng lặp tự lập lịch ở cùng thời gian ảo khiến cơ chế timeout của `runTest` không thoát được). Harness cắt sau 600 s; hai JVM test do chính lượt đó sinh đã bị dừng (một cái bỏ qua SIGTERM, phải SIGKILL). Đã sửa bằng giới hạn `take` trong fake và cho harness tự dừng worker do nó sinh khi quá hạn. Lượt đầy đủ sau đó không có TIMEOUT.
- Kiểm chứng phần 1 (mã sau phần 1):
  - `browser.*` 3/3 xanh, mỗi lần 447 test; toàn dự án 839 test xanh; 10 vòng 11 lớp mới/đổi, mỗi vòng 180 test, đều xanh.
  - `assembleDebug` xanh; `lintDebug` vẫn đúng 123 cảnh báo cũ, 0 trong file mới/sửa; `verifyRoborazziDebug` xanh.
  - Biên dịch lại không incremental 34/34 task, 0 `w:`; vệ sinh sạch.

### Phần 2: sửa các phát hiện của review bảo mật

Nguồn: `plans/reports/code-reviewer-261022-0033-phase-04-security-review.md`. Quyết định H1–H13 do controller chốt; mỗi mục ghi phát hiện gốc của review, cách sửa, test và đột biến (ID của harness `p4c-mutate3.py`, mọi đột biến đều bị test liên quan bắt). Test, comment và tên test không nhắc mã phát hiện.

- **H1 (M1): hai `MainActivity` chồng nhau.**
  - Sửa: `TabManager.detach(engine)` chỉ tách khi engine đang gắn là đúng `engine` truyền vào (so `===`); thêm `isAttached(engine)`. `MainActivity.onDestroy` gọi `tabs.detach(browserHost.pool)`; `BrowserRig` cũng vậy. Lớp mới `WebViewTimers` (một instance cho cả process, `WebViewTimers.process`, inject được) đếm số pool đang started: timer JS chỉ pause khi pool started cuối cùng dừng (gọi qua một WebView của pool đó) và resume khi một pool start lại (qua WebView của nó, hoặc qua WebView đầu tiên nó tạo nếu lúc start chưa có). `WebViewPool.destroy` chỉ phát `onWebViewDestroyed` khi pool còn là engine đang gắn, nên pool đã bị thay không reset runtime của tab mà pool mới đang giữ.
  - Test: `WebViewPoolTest.anActivityThatStopsAfterANewerOneStartedLeavesItsPagesRunning` và `timersPausedWhileNoPoolRanResumeWithTheNextPoolsFirstWebView` (hai Activity Robolectric, hai pool, chung một `WebViewTimers`); `TabManagerPageTest.anEngineThatWasReplacedCannotDetachItsSuccessor`.
  - Đột biến: R01 (detach không so danh tính), R02 (pause khi pool khác còn started), R03 (pool đã bị thay vẫn báo WebView bị huỷ), R04 (WebView mới không resume timer).
- **H2 (M2): intent ra ngoài.**
  - Sửa: `externalNavigation(uri, hasGesture, incognito, ownPackage)`. Tab incognito chỉ giao `tel:`, `mailto:`, `sms:`, `smsto:` (vẫn cần cử chỉ); mọi thứ khác, kể cả `intent:` và `market:`, → `Blocked`. `intent.flags = 0` bỏ mọi cờ launch, kể cả NEW_TASK/CLEAR_TASK. Intent có `package` là gói của Skim → `Blocked`. `SkimWebViewClient(tabId, incognito, …)` truyền `view.context.packageName`; `WebViewPool` truyền `tab.incognito`.
  - Test: `ExternalIntentsTest` có 13 test. Test cũ khẳng định giữ NEW_TASK đổi thành `flags == 0`; thêm CLEAR_TASK, gói Skim và incognito. `SkimWebViewClientTest` thêm 2 test (incognito, gói Skim).
  - Đột biến: R05 (incognito mở mọi scheme), R06 (giữ cờ launch), R07 (không chặn gói Skim), R08–R09 (client không truyền incognito, không truyền gói).
- **H3 (M3): quyền site theo trang top-level.**
  - Sửa: host để tra, lưu và hiển thị quyết định là host của URL top-level của tab (`tabs.value.tab(tabId)?.url`), không còn là origin của frame. Tab không có URL web có host → deny. Location cần origin của frame là https có host VÀ URL của tab là https. Media cần frame có origin web: origin opaque (`null`) → deny. Đường incognito giữ nguyên. `allow(request, remember)`, `block(request)`, `dismiss(request)` chỉ có tác dụng khi `request == pending.value`, nên trang đổi yêu cầu ngay dưới ngón tay người dùng thì lần chạm đó không đổi gì.
  - Test: `SitePermissionBridgeTest` thêm 7 test (site nhúng trong trang khác không nhận quyết định của mình và dialog hiện host trang; câu trả lời ở trang top-level lưu cho trang, không cho site nhúng; tab không có trang web; trang http xin location; câu trả lời cho yêu cầu không còn hiện; frame xin location; frame sandbox). Mọi test cũ gọi `allow/block/dismiss` với đúng `pending`.
  - Đột biến: R10 (media theo site của frame), R11 (frame không có origin web), R12 (location theo site của frame), R13 (location cho trang http), R14 (frame `https:` không host), R15 (câu trả lời áp cho yêu cầu đang hiện bất kỳ), P07 và P10 viết lại theo mã mới.
- **H4 (M4): giới hạn tải tự động.**
  - Sửa: hạn mức của `DownloadRouter` tính theo thời gian, mỗi tab một tải tự động mỗi 30 s (`clock` inject được), không còn theo `loadNonce`. `SkimWebView` ghi mốc tap ở `ACTION_UP` khi ngón tay không vượt `scaledTouchSlop`; vượt slop khi MOVE, nhấc ngón xa điểm chạm, CANCEL, ngón thứ hai và pull-to-refresh đều không phải tap. `consumeGesture(withinMs)` trả true một lần rồi xoá mốc. Tải `userInitiated` không bị giới hạn và không tiêu hạn mức hay tap. Semaphore 2 cho `data:`/`blob:`: `tryAcquire` thất bại → `DownloadFailed`; `release` chạy trong `invokeOnCompletion`, nên chạy ở mọi đường, kể cả khi scope đã huỷ.
  - Test: `SkimWebViewTest` có 5 test mới hoặc viết lại (tap trong 5 s, mỗi tap một hành động, cuộn kể cả cuộn rồi quay lại và vuốt nhanh không có MOVE, CANCEL, pinch). `DownloadRouterTest` có 8 test mới hoặc viết lại (30 s mỗi tab dù tải lại trang, hạn mức riêng từng tab, tap, cuộn, `userInitiated`, tối đa 2 tải trong bộ nhớ, và 2 test cookie của H6).
  - Đột biến: D01–D04 viết lại, R16 (các tab chung hạn mức), R17 (3 tải trong bộ nhớ), R18 (blob giữ chỗ), R19 (tap không bị tiêu), R20–R24 (nhấc ngón xa vẫn là tap, cuộn rồi quay lại là tap, pinch là tap, chạm xuống là cử chỉ, vượt slop vẫn giữ tap).
- **H5 (M5) và câu hỏi 4 của reviewer: tải trong process.**
  - Trần kích thước: `MAX_IN_PROCESS_DOWNLOAD_BYTES` = 2 GiB. Từ chối theo `Content-Length` trước khi tạo hàng, và dừng khi số byte đếm được vượt trần.
  - Dung lượng trống: `freeSpace` mặc định là `MediaStoreDownloads.freeBytes()`, tức `StatFs` của volume chứa Downloads (`getExternalFilesDir(null)` từ API 29, thư mục Downloads công khai trước đó). Đo trên thư mục cha gần nhất đã tồn tại; không đọc được thì trả `Long.MAX_VALUE`. Kiểm trước khi ghi và sau mỗi 8 MiB; còn dưới 256 MiB → lỗi.
  - Server nhỏ giọt: cửa sổ 60 s nhận dưới 16 KiB → lỗi. Server im hẳn bị read timeout 15 s của client cắt.
  - Mọi lỗi trên đi qua đường `discard` có sẵn (xoá hàng hoặc tệp đang ghi) rồi thành `DownloadFailed`.
  - Client riêng có `ConnectionPool(0, 1, SECONDS)` (`internal val client`), nên kết nối của tải incognito không bao giờ được hoạt động thường dùng lại.
  - Test: `InProcessDownloaderTest` thêm 6 test (Content-Length, body chunked vượt trần rồi bị xoá, thiếu chỗ từ đầu, hết chỗ giữa chừng sau 8 MiB, server chậm, pool riêng không giữ kết nối). `MediaStoreDownloadsTest` thêm 3 test (volume API 29+, thư mục chưa tồn tại ở API 26, không có shared storage). `TestBrowserParts` inject `freeSpace`, vì `ShadowStatFs` mặc định báo 0 byte.
  - Đột biến: R25–R34 (bỏ kiểm Content-Length; bỏ đếm; bỏ kiểm chỗ đầu; bỏ kiểm giữa chừng; kiểm mỗi 64 MiB; bỏ ngưỡng thông lượng; dùng pool chung; giữ kết nối rảnh; đo nhầm volume; không đọc được chỗ thì chặn tải).
- **H6 (M6): cookie của DownloadManager.**
  - Sửa: header `Cookie` chỉ đi kèm tải do trang khởi tạo VÀ có URL https; không bao giờ đi với tải `userInitiated`, cũng không bao giờ đi qua `http:`. KDoc lớp ghi rõ giới hạn.
  - Test: 2 test mới (`userInitiated`, cleartext); test cũ "tab thường gửi cookie mặc định" vẫn xanh.
  - Đột biến: R35 (cookie cho tải người dùng chọn), R36 (cookie qua cleartext).
- **H7 (M7) và câu hỏi 3: bàn phím và autofill ở incognito.**
  - Sửa: `SkimWebView @JvmOverloads constructor(context, incognito = false)`. Ở incognito, `onCreateInputConnection` thêm `IME_FLAG_NO_PERSONALIZED_LEARNING` và WebView đặt `importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` [DEFAULT — chờ validate: mất autofill mật khẩu ở incognito là đánh đổi sản phẩm]. `WebViewPool.create` truyền `tab.incognito`. `@JvmOverloads` giữ constructor `(Context)` mà lint `ViewConstructor` đòi.
  - Test: 1 test (cả hai cờ có ở incognito, không có ở tab thường).
  - Đột biến: R37 (thiếu cờ IME), R38 (thiếu loại trừ autofill), R39 (cờ IME cho cả tab thường). Dây nối ở pool không kiểm được trên JVM, vì Robolectric không tạo được WebView incognito; xem 4D-2.
- **H8 (M8): hộp thoại JS.**
  - Sửa: `SkimChromeClient` override `onJsAlert`/`onJsConfirm`/`onJsPrompt`/`onJsBeforeUnload` qua `pageDialog(result, leaving)`. Tab không hiển thị, hoặc client đã detach → `cancel()` và trả true; riêng beforeunload thì `confirm()`, để tab nền vẫn điều hướng được. Tab đang hiện dùng hộp thoại mặc định, tối đa 3 lần mỗi tài liệu (`documentId` = `loadNonce`); từ lần thứ 4 → `cancel()` (beforeunload → `confirm()`). Hàm `isShown` của pool cần đủ bốn điều kiện: entry còn sống, `hosts > 0`, `isAttachedToWindow`, và là tab hiện tại.
  - Test: `SkimChromeClientTest` thêm 4 test (3 lần mỗi tài liệu và đếm lại từ đầu ở tài liệu mới, tab không hiện, beforeunload vượt hạn mức, client đã detach). `WebViewPoolTest` thêm 2 test: mỗi điều kiện của `isShown` hỏng riêng một lần (offscreen không host, có host nhưng ngoài window, tab khác thành hiện tại), và dây nối `documentId` qua `onPageStarted`.
  - `RecordingJsResults` (test) tạo `JsResult`/`JsPromptResult` thật qua constructor ẩn (reflection + `Proxy` cho `ResultReceiver`). Lần `cancel` đọc qua `ShadowJsResult.wasCancelled()`, vì shadow của Robolectric cho `cancel()` không báo receiver.
  - Đột biến: R40–R48 (hiện hộp thoại của tab nền, bỏ hạn mức, không đếm lại theo tài liệu, beforeunload vượt hạn mức giữ người dùng lại, bỏ từng điều kiện của `isShown`, pool coi mọi trang là đang hiện, pool chỉ một tài liệu).
- **H9 (L1): `.apk` đội lốt định dạng ZIP.**
  - Sửa: `refines(ZIP, …)` cần thêm `MimeTypeMap.getExtensionFromMimeType(specific) != null`. `DownloadNames.extensionFor`: với type không có đuôi, chỉ giữ đuôi do trang đặt khi đuôi đó không ánh xạ sang type khác.
  - Test: `MimeSnifferTest` 1 test (byte ZIP + ODF/OOXML giả + `update.apk` → `application/zip` và `update.zip`). `DownloadNamesTest` 1 test.
  - Đột biến: R52, R53.
- **H10 (L2): console ở incognito.** `onConsoleMessage` trả true ở incognito. Test 1, đột biến R49.
- **H11 (L4): `evaluate` không timeout.** `withTimeoutOrNull(EVALUATE_TIMEOUT_MS = 10 s)` cộng `invokeOnCancellation` gỡ continuation. Test 1 dùng thời gian ảo (chưa xong ở 9.999 ms, xong ở 10.001 ms, câu trả lời muộn không tới ai). Đột biến R50. Bỏ `invokeOnCancellation` là đột biến tương đương (chỉ giữ thêm bộ nhớ tới khi pool huỷ), nên không đưa vào.
- **H12 (câu hỏi 7): tiêu đề trang.** `onTitle` dùng `title.toVisibleText().cutTo(Tab.MAX_TITLE_LENGTH)` trước khi vào state và Room. Test 1 (RLO, PDF, zero-width, xuống dòng, isolate; cả state lẫn history). Đột biến R51.
- **H13 (câu hỏi 1): `data:` URL lớn.**
  - Sửa: `parseDataUrl` đo độ dài body trước khi copy hoặc giải mã. Base64 tối đa ⌈max/3⌉ × 4 + 8 ký tự; dạng khác tối đa 3 × max + 64.
  - Test: 1 test ở biên (thêm 8 khoảng trắng thì nhận, 9 thì từ chối).
  - Đột biến: R54, R55.
- Giữ lại cho phase sau, không viết mã: xem "Việc cho phase 7/9" và "Việc cho 4D-2".
- File sửa ở phần 2:
  - Mã: `TabManager.kt`, `WebViewPool.kt` (+ `WebViewTimers`), `SkimWebView.kt`, `SkimWebViewClient.kt`, `SkimChromeClient.kt`, `ExternalIntents.kt`, `MainActivity.kt`, `downloads/{DataUrls,DownloadNames,MimeSniffer,InProcessDownloader,MediaStoreDownloads,DownloadRouter}.kt`, `permissions/SitePermissionBridge.kt`.
  - androidTest: `BrowserRig.kt`, chỉ dòng `tabs.detach(pool)`.
  - Test mới: `RecordingJsResults.kt`.
  - Test sửa: `SkimWebViewTest`, `ExternalIntentsTest`, `SkimWebViewClientTest`, `SkimChromeClientTest`, `WebViewPoolTest`, `ui/WebViewHostTest` (timer riêng), `TabManagerPageTest`, `TestBrowserParts`, `permissions/SitePermissionBridgeTest`, `downloads/{DownloadRouterTest,InProcessDownloaderTest,MediaStoreDownloadsTest,DataUrlsTest,DownloadNamesTest,MimeSnifferTest}`.

## Kiểm chứng (chạy thật, trên mã cuối)

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.browser.*' --console=plain` ×3 liên tiếp | 3/3 BUILD SUCCESSFUL; mỗi lần 491 test, 37 lớp, 0 lỗi, 0 skip |
| 10 vòng 17 lớp mới/đổi (mỗi vòng `cleanTestDebugUnitTest` trước; `p4c-loop2.sh`) | 10/10 xanh, mỗi vòng 308 test; chạy hai lần, trước và sau `@JvmOverloads`, đều 10/10 |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; 883 test, 83 lớp, 0 lỗi, 0 skip |
| `./gradlew :app:assembleDebug --console=plain` | BUILD SUCCESSFUL; APK có `assets/js/skim-blob-fetch.js` (4.368 B) |
| `./gradlew :app:lintDebug --console=plain` | 0 lỗi; đúng 123 cảnh báo cũ (UnusedResources 118, NewerVersionAvailable 3, AGP 1, InsecureBaseConfiguration 1); 0 trong file mới/sửa. Lần chạy đầu lộ 1 `ViewConstructor` mới ở `SkimWebView` (constructor có thêm `incognito`); đã sửa bằng `@JvmOverloads` và chạy lại toàn bộ bảng này |
| `./gradlew :app:verifyRoborazziDebug --console=plain` | BUILD SUCCESSFUL (883 test, golden không đổi) |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL, 34/34 task chạy, 0 dòng `w:`, 0 `e:`; androidTest (`BrowserRig`) biên dịch |
| Quét vệ sinh 46 file (`p4c-hygiene2.py`, thêm mẫu mã phát hiện dạng `H1`/`M3`/`L4`) | 0 ID plan/phase/finding; 0 ký tự bidi/zero-width thô (ký tự ngoài ASCII chỉ là gạch nối và dấu ba chấm trong comment, emoji và tiếng Việt trong test có sẵn); 0 secret; log mới chỉ in tên lớp exception (4 log số của pool có sẵn từ 4B, không có URL hay host) |
| JS (`skim-blob-fetch.js` không đổi ở phần 2) | J01–J08 chạy lại qua harness Node và Chrome thật vẫn bị bắt |

## Đột biến

Harness: `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/p4c-mutate3.py`. Mỗi mẫu phải khớp đúng 1 lần; harness chạy các lớp test liên quan, khôi phục file và so md5. Timeout 300 s, khi quá hạn tự dừng worker do chính nó sinh. Kết quả lưu ở `p4c-mutation-results-3.json`, cùng thư mục: 131/131 KILLED, 0 SURVIVED/TIMEOUT/COMPILE_ERROR, cả cây khôi phục đúng. Lượt chạy mất 1.113 s, lâu nhất 38 s một đột biến. Lượt đầy đủ chạy trước khi thêm `@JvmOverloads` vào `SkimWebView`, thay đổi chỉ thêm một constructor overload. 9 đột biến của `SkimWebView` (R19–R24, R37–R39) được chạy lại trên mã cuối và 9/9 bị bắt (`p4c-mutation-results-3-skimwebview.json`).

| Nhóm | ID | Đột biến | Bị bắt bởi (ví dụ) |
|---|---|---|---|
| Rate limit (viết lại) | D01–D05 | mọi tải tự động đều qua; một tải mỗi tab mãi mãi; tap không tính; bỏ `userInitiated`; cửa sổ tap 50 s | `aPageGetsOneDownloadOfItsOwnEvery30SecondsWhateverItLoads`, `aTapAllowsOneDownloadForFiveSeconds`, `downloadsTheUserAskedForAreNeverLimited` |
| Phân nhánh | D06–D08, D13, D14 | incognito → DownloadManager (viết lại); incognito gửi cookie mặc định; không phiên rơi về profile mặc định; scheme lạ thành web; không xin quyền lưu trữ API 26–28 | `anIncognitoTabsWebDownload…NeverReachesDownloadManager`, `anIncognitoTabWithoutASessionDownloadsNothing`, `otherSchemesAreRefused` |
| Tên/trần | D09–D12 | tên DownloadManager không sanitize (viết lại); tên tệp lưu không sanitize; `data:`/`blob:` bỏ trần 25 MB | `aNormalTabsWebDownloadGoesToDownloadManagerWithTheDefaultProfilesCookies`, `aDataUrlOverTheLimitIsRefused` |
| Tracker, in-process, MediaStore, sniff | T01–T04, I01–I04, S01–S04, N01–N03 | như lượt gốc | `DownloadTrackerTest`, `InProcessDownloaderTest`, `MediaStoreDownloadsTest`, `MimeSnifferTest` |
| Blob Kotlin / JS | K01–K19, J01–J08 | như phần 1 | `BlobBridgeTest`, harness Node + Chrome |
| Quyền site | P01–P14 | như lượt gốc; P07 (dismiss lưu BLOCKED) và P10 (location cho frame http) viết lại | `SitePermissionBridgeTest` |
| Nối dây | W01–W06 | như lượt gốc | `SkimChromeClientTest`, `WebViewPoolTest`, `AppContainerBrowserTest` |
| Hai Activity | R01–R04 | xem H1 | `anActivityThatStopsAfterANewerOneStartedLeavesItsPagesRunning`, `anEngineThatWasReplacedCannotDetachItsSuccessor` |
| Intent | R05–R09 | xem H2 | `ExternalIntentsTest`, `SkimWebViewClientTest` |
| Quyền theo trang | R10–R15 | xem H3 | `aSiteEmbeddedInAnotherPageGetsNoneOfTheDecisionsMadeForIt`, `anAnswerForARequestNoLongerShownChangesNothing` |
| Tap và hạn mức | R16–R24 | xem H4 | `eachTabHasItsOwnAllowance`, `atMostTwoDownloadsHeldInMemoryRunAtOnce`, `aScrollIsNoTap`, `aPinchIsNoTap` |
| Tải trong process | R25–R34 | xem H5 | `aFileLargerThanTheLimitIsRefusedFromItsLength`, `aDownloadStopsWhenTheSpaceRunsOutOnTheWay`, `downloadsUseConnectionsOfTheirOwnThatAreNeverKept` |
| Cookie | R35–R36 | xem H6 | `aDownloadTheUserAskedForCarriesNoCookies`, `aCleartextDownloadCarriesNoCookies` |
| IME/autofill | R37–R39 | xem H7 | `anIncognitoPageKeepsTheKeyboardFromLearningAndTheAutofillServiceAway` |
| Hộp thoại JS | R40–R48 | xem H8 | `aPageThatIsNotOnScreenGetsNoDialog`, `onlyTheTabOnScreenShowsThePagesDialogs`, `aPageOnScreenShowsThreeDialogsPerDocument` |
| Console, evaluate, tiêu đề | R49–R51 | xem H10–H12 | `anIncognitoPagesConsoleStaysOutOfTheSystemLog`, `aScriptThatNeverAnswersEndsWithNothingAfterTenSeconds`, `aTitleLosesTheCharactersThatMakeItReadAsSomethingElse` |
| MIME/tên | R52–R53 | xem H9 | `aMadeUpTypeOfADocumentFamilyOnAZipIsAZip`, `aTypeWithoutAnExtensionDropsOneThatNamesAnotherType` |
| `data:` | R54–R55 | xem H13 | `aBase64BodyIsMeasuredBeforeItIsCopied` |

Trước khi chạy, hai test được siết vì soát thấy đột biến tương ứng sẽ sống:
- Test timeout của `evaluate` nay khẳng định kết quả đã xong đúng ở 10 s. Nếu không, timeout 100 s vẫn qua nhờ `runTest` tự tua thời gian ảo.
- Test `isShown` nay làm hỏng riêng từng điều kiện. Bản đầu có ba điều kiện thừa lẫn nhau, nên bỏ một điều kiện bất kỳ vẫn qua.

Thêm vào đó: test vuốt nhanh không có MOVE, test pinch, frame xin location và frame sandbox được viết để R20, R22, R12 và R11 có test bắt. Mọi killer đều là test liên quan.

## Chữ ký thật (cho phase 7 và 4D-2)

```kotlin
data class BrowserHost(val pool: WebViewPool, val sitePermissions: SitePermissionBridge, val downloads: DownloadRouter,
    val blob: BlobBridge, val activityHost: ActivityHost, val fullscreen: FullscreenController, val secure: WindowSecureController) {
    @MainThread fun destroy()   // sitePermissions.destroy(); blob.destroy(); downloads.destroy(); pool.destroyAll()
}
internal fun createBrowserHost(activity: ComponentActivity, container: AppContainer, activityHost: ActivityHost): BrowserHost  // MainActivity.kt; BrowserRig dùng chung

@MainThread class DownloadRouter(tabs: TabManager, incognito: IncognitoSession, profiles: BrowserProfiles, tracker: DownloadTracker,
    inProcess: InProcessDownloader, store: MediaStoreDownloads, blob: BlobBridge, activityHost: ActivityHost, scope: CoroutineScope,
    defaultCookies: (url: String) -> String? = { CookieManager.getInstance().getCookie(it) },
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: () -> Long = SystemClock::elapsedRealtime) {
    fun start(tabId: String, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?,
              webView: WebView, userInitiated: Boolean = false)
    fun destroy()
    // 1 tải tự động / 30 s / tab; 1 tap = 1 tải trong 5 s; tối đa 2 tải data:/blob: đồng thời
}
sealed interface DownloadEvent {
    data class DownloadCompleted(val fileName: String, val uri: Uri, val mime: String) : DownloadEvent
    data class DownloadFailed(val fileName: String?) : DownloadEvent
}
class DownloadTracker(system: SystemDownloads, scope: CoroutineScope, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val events: SharedFlow<DownloadEvent>     // không replay, buffer 16
    @AnyThread fun enqueue(request: SystemDownloadRequest): Boolean; @AnyThread fun onDownloadComplete(id: Long)
    @AnyThread fun refresh(); @AnyThread fun report(event: DownloadEvent)
}
interface SystemDownloads { fun enqueue(request: SystemDownloadRequest): Long; fun status(id: Long): Int?; fun uriFor(id: Long): Uri?; fun mimeTypeFor(id: Long): String? }
class AndroidSystemDownloads(context: Context) : SystemDownloads
class SystemDownloadRequest(val url: String, val fileName: String, val mimeType: String?, val userAgent: String?, val cookie: String?)
const val MAX_IN_PROCESS_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024
class InProcessDownloader(httpClient: () -> OkHttpClient, store: MediaStoreDownloads, ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxBytes: Long = MAX_IN_PROCESS_DOWNLOAD_BYTES, freeSpace: () -> Long = store::freeBytes,
    clock: () -> Long = SystemClock::elapsedRealtime) {
    internal val client: OkHttpClient   // ConnectionPool(0, 1, SECONDS) riêng, tự theo redirect
    suspend fun download(url: String, userAgent: String?, cookies: suspend (url: String) -> String?, rawName: String?, declaredMime: String?): DownloadEvent.DownloadCompleted
}
class DownloadException(message: String) : IOException(message)
class MediaStoreDownloads(context: Context, sdkInt: Int = Build.VERSION.SDK_INT, legacyFolder: () -> File = <public Downloads>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val needsStoragePermission: Boolean
    fun freeBytes(): Long   // StatFs của volume chứa Downloads; Long.MAX_VALUE khi không đọc được
    suspend fun save(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): DownloadEvent.DownloadCompleted
}
const val SNIFF_BYTES = 64; fun sniffMime(head: ByteArray): String?; fun downloadMimeOf(head: ByteArray, declared: String?, rawName: String?): String
class BlobData(val mimeType: String?, val bytes: ByteArray)
@MainThread class BlobBridge(assets: AssetManager) {   // + internal (ScriptRunner, readScript, newNonce, timeoutMs, mainDispatcher) cho test
    suspend fun fetch(webView: WebView, blobUrl: String, maxBytes: Int = MAX_INLINE_DOWNLOAD_BYTES): BlobData?; fun destroy()
}
internal interface ScriptRunner { suspend fun evaluate(webView: WebView, script: String): String?; fun run(webView: WebView, script: String) }
data class PendingSitePermission(val tabId: String, val host: String, val kinds: Set<SitePermissionKind>, val incognito: Boolean)
@MainThread class SitePermissionBridge(context: Context, tabs: StateFlow<TabsState>, repository: SitePermissionRepository,
    incognitoSession: IncognitoSession, activityHost: ActivityHost, scope: CoroutineScope, mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) {
    val pending: StateFlow<PendingSitePermission?>   // host = host của trang top-level của tab
    fun allow(request: PendingSitePermission, remember: Boolean); fun block(request: PendingSitePermission)
    fun dismiss(request: PendingSitePermission); fun destroy()   // ba hàm đầu bỏ qua khi request != pending.value
    internal fun requestLocation(tabId: String, incognito: Boolean, origin: String, callback: GeolocationPermissions.Callback)
    internal fun locationRequestWithdrawn(tabId: String); internal fun requestMedia(tabId: String, incognito: Boolean, request: PermissionRequest)
    internal fun mediaRequestWithdrawn(request: PermissionRequest); internal fun cancelTab(tabId: String)
}
interface BrowserProfile { …; fun cookieHeader(url: String): String? }

class SkimWebView @JvmOverloads constructor(context: Context, incognito: Boolean = false) : WebView(context), NestedScrollingChild3 {
    fun consumeGesture(withinMs: Long): Boolean   // thay hadRecentGesture/lastUserGestureAt: true một lần sau một tap
}
fun externalNavigation(uri: Uri, hasGesture: Boolean, incognito: Boolean, ownPackage: String): ExternalNav
class SkimWebViewClient(tabId: String, incognito: Boolean, events: TabEventSink, activityHost: ActivityHost,
    loadInTab: (tabId: String, url: String) -> Unit, onRendererGone: (view: WebView, action: RendererGoneAction, didCrash: Boolean) -> Unit)
class SkimChromeClient(tabId: String, incognito: Boolean, events: TabEventSink, activityHost: ActivityHost, fullscreen: FullscreenController,
    sitePermissions: SitePermissionBridge, obtainForPopup: (popupTabId: String) -> WebView?, isShown: () -> Boolean, documentId: () -> Long,
    parseFileChooserResult: (resultCode: Int, data: Intent?) -> Array<Uri>? = WebChromeClient.FileChooserParams::parseResult)
// TabManager: @MainThread fun detach(engine: TabEngine)   // chỉ tách khi engine đang gắn === engine
//             fun isAttached(engine: TabEngine): Boolean
// WebViewPool(…, sitePermissions, downloads, isLowRamDevice = …, mainDispatcher = …, timers: WebViewTimers = WebViewTimers.process, drawThumbnail = …)
//   evaluate(...) trả null khi trang không trả lời trong 10 s (EVALUATE_TIMEOUT_MS)
@MainThread class WebViewTimers { val paused: Boolean; fun poolStarted(anyView: WebView?); fun poolStopped(anyView: WebView?)
    fun webViewCreated(view: WebView); companion object { val process: WebViewTimers } }
// AppContainer (lazy): httpClient: OkHttpClient (connect/read 15 s), browserProfiles: BrowserProfiles, downloadTracker: DownloadTracker
```

Cho phase 7:
- `SitePermissionDialog` gọi `allow(pending, remember = true)` / `block(pending)` với đúng `pending` nó đang vẽ; Back/scrim gọi `dismiss(pending)`.
- `pending.host` là host của trang top-level, không phải của frame. `pending.kinds` chỉ gồm loại mà site chưa có quyết định (camera đã ALLOWED + mic chưa → chỉ hỏi mic).
- "Download image" gọi `host.downloads.start(tab.id, src, null, null, null, webView, userInitiated = true)`. Tải này không mang cookie; xem "Việc cho phase 7/9".
- `DownloadSnackbarEffect` nên collect `tracker.events` suốt composition (không replay).

## Deviations (lệch spec và lý do)

- `SitePermissionBridge` nhận `tabs: StateFlow<TabsState>` và `pending` chỉ là yêu cầu cũ nhất **của tab đang hiện** (controller đã đồng ý); yêu cầu tab khác chờ tới khi tab đó hiện, prompt Android chỉ bật cho tab đang hiện, một prompt mỗi lúc. Quyết định đã lưu (remember) trả lời luôn các yêu cầu cùng site đang chờ.
- Yêu cầu có resource ngoài camera/mic bị deny cả yêu cầu: `PermissionRequestAdapter.grant` của Chromium biến grant một phần thành deny (đã đọc nguồn). Yêu cầu WebView tự rút (`onGeolocationPermissionsHidePrompt`, `onPermissionRequestCanceled`) bị bỏ, không trả lời: `AwContents::InvokeGeolocationCallback` sẽ trả nhầm cho prompt kế tiếp cùng origin. Mọi yêu cầu khác được trả đúng một lần.
- Quyền site (diễn giải thêm khi sửa H3): origin của frame xin location phải là https **có host** (origin `https:` không host bị deny, như trước); frame không có origin web (opaque `null`) xin camera/mic bị deny dù trang đã có quyết định.
- Quyền `WRITE_EXTERNAL_STORAGE` (API 26–28) xin cả cho nhánh DownloadManager: DownloadProvider trước Q đòi quyền này để ghi vào thư mục công khai.
- Tải chạy trong scope của process (`appScope`), không huỷ theo Activity; thêm `DownloadRouter.destroy()` (bỏ `ActivityHost`, sau đó không xin quyền được nữa) để coroutine tải dài không giữ Activity đã huỷ. Thêm `BrowserHost.destroy()` và `createBrowserHost(...)` (MainActivity.kt) làm đường dựng/huỷ duy nhất cho `MainActivity` và `BrowserRig`.
- `InProcessDownloader` tự theo redirect (tối đa 20) và lấy cookie cho từng chặng: OkHttp giữ header `Cookie` cố định khi redirect sang host khác (rò cookie incognito sang site khác).
- Tải trong process (diễn giải thêm khi sửa H5): ngưỡng thông lượng được kiểm mỗi khi byte về; server không gửi gì bị read timeout 15 s của client cắt trước. `freeBytes()` mở khi không đọc được dung lượng (`Long.MAX_VALUE`): khi đĩa đầy, lệnh ghi tự thất bại và hàng dở bị xoá.
- Hạn mức tải (diễn giải thêm khi sửa H4): tải `userInitiated` không tiêu hạn mức tự động hay tap; một tap đã bị tiêu vẫn mất khi tải sau đó bị từ chối vì đủ 2 tải trong bộ nhớ (đóng an toàn).
- Hộp thoại JS (diễn giải H8): beforeunload ở tab không hiện, client đã detach, hoặc vượt 3 lần mỗi tài liệu đều `confirm()` (cho rời trang) chứ không `cancel()`. Lý do: `cancel()` giữ người dùng lại ở trang đang spam hộp thoại, đúng điều cần chặn.
- MIME: byte thắng MIME khai, trừ khi byte là container (ZIP/MP4/Ogg) và MIME khai hoặc đuôi tên là định dạng xây trên nó (docx/xlsx/pptx, ODF, EPUB; m4a; ogv) (controller giữ quy tắc này). Từ phần 2, định dạng con trên ZIP còn phải là type mà `MimeTypeMap` biết. APK không bao giờ thuộc danh sách này.
- `AppContainer`: tách `browserProfiles` (dùng chung cho `IncognitoSession` và cookie tải ẩn danh); `close()` tắt dispatcher và connection pool của `httpClient`.
- `DownloadTracker` qua cổng `SystemDownloads`; `report(event)` cho tải Skim tự lưu; `BlobData.mimeType` nullable (blob không có type).
- `WebViewTimers` là lớp công khai có instance `process` cho cả process, inject vào `WebViewPool` để test hai Activity dùng một bộ đếm riêng.
- File 4A/4B đã sửa:
  - Lượt gốc và phần 1: `page/HighlightRequest.kt` (`escapeForScript` từ `private` thành `internal`), `BrowserProfiles.kt`, `WebViewProfiles.kt`, `WebViewPool.kt`, `SkimChromeClient.kt`, `BrowserHost.kt`.
  - Phần 2 (controller cho phép): `TabManager.kt`, `WebViewPool.kt`, `SkimWebView.kt`, `SkimWebViewClient.kt`, `SkimChromeClient.kt`, `ExternalIntents.kt`, `MainActivity.kt`, `downloads/DataUrls.kt`, `downloads/DownloadNames.kt`.
  - Test 4A/4B sửa: `FakeBrowserProfiles.kt`, `SkimChromeClientTest.kt`, `WebViewPoolTest.kt`, `ui/WebViewHostTest.kt`, `AppContainerBrowserTest.kt`, `SkimWebViewTest.kt`, `ExternalIntentsTest.kt`, `SkimWebViewClientTest.kt`, `TabManagerPageTest.kt`, `downloads/DataUrlsTest.kt`, `downloads/DownloadNamesTest.kt`.
  - androidTest: `BrowserRig.kt` (dựng bằng `createBrowserHost`, huỷ bằng `host.destroy()`, `tabs.detach(pool)`).
  - Không đổi build/Gradle.

## Việc cho 4D-2 (chỉ kiểm được trên thiết bị)

- Blob thật: trang https tạo blob rồi bấm `<a download href="blob:…">`, kỳ vọng tệp đúng byte trong `MediaStore.Downloads`. Trang có CSP `connect-src` không cho `blob:` sẽ chặn `fetch(blob:)` của script → `DownloadFailed`; nếu xảy ra thì ghi vào Known deviations.
- DownloadManager thật, tab thường tải PDF qua MockWebServer:
  - `User-Agent` tới server.
  - Header `Cookie` (từ `/set-cookie`) chỉ có khi trang tự mở tải qua https. Không có với "Download link/image" (`userInitiated`), cũng không có với `http:`.
  - Thông báo hệ thống; receiver → `DownloadCompleted` với URI/MIME của DownloadManager; `refresh()` sau stop/start bắt được download xong lúc dừng.
  - Xác nhận URL đưa cho `DownloadListener` là URL SAU redirect: trang → 302 → tệp. Tiền đề của H6 dựa vào điều này.
- Incognito:
  - Cookie của profile incognito tới server; DownloadManager không có hàng nào cho URL đó; tệp trong `MediaStore.Downloads` với `IS_PENDING = 0`.
  - Chrome client của tab incognito báo `incognito = true`, và client của nó chặn `intent:`/`market:` nhưng mở `tel:`/`mailto:`.
  - `EditorInfo` của ô nhập có `IME_FLAG_NO_PERSONALIZED_LEARNING`; `importantForAutofill` của WebView là `NO_EXCLUDE_DESCENDANTS`.
  - Robolectric không tạo được WebView incognito, nên dây nối ở pool chỉ kiểm được tại đây.
- Tải trong process trên máy thật: tệp lớn hơn chỗ trống trừ 256 MiB bị dừng và xoá; server nhỏ giọt bị huỷ sau 60 s; `freeBytes()` khớp `StatFs` của `/storage/emulated/0`.
- MediaStore API 36 (scoped) và API 28 (`skim_api28`: prompt `WRITE_EXTERNAL_STORAGE` qua `ActivityResultHost`, tên trùng " (1)", hàng `MediaStore.Files` mở được bằng app khác).
- `data:` từ `<a href="data:…" download>`: listener nhận gì (MIME, Content-Disposition). Rate limit:
  - Trang tự gọi 3 download không chạm → 1 tệp, và thêm 1 sau 30 s.
  - Mỗi tap → một tệp; cuộn không tính.
- Hộp thoại JS:
  - Tab nền gọi `alert()`/`prompt()` → không có gì hiện, tab nền vẫn chạy.
  - Tab đang hiện chạy vòng `alert()` → 3 hộp thoại rồi thôi.
  - beforeunload vẫn cho rời trang.
- Hai `MainActivity` chồng nhau (nếu launch mode cho phép, ví dụ VIEW intent từ app khác khi Skim đang mở): trang của Activity mới không bị dừng timer khi Activity cũ dừng, và tab vẫn tải.
- Quyền runtime thật (UiAutomator cho prompt):
  - Camera/mic qua `getUserMedia` trên `http://127.0.0.1` (secure context) → `onPermissionRequest`.
  - Location cần frame https VÀ trang https: MockWebServer http sẽ bị từ chối ngay, nên cần https thật hoặc chấp nhận kiểm tay.
  - iframe khác site: dialog hiện host của trang top-level.

## Việc cho phase 7/9

- Phase 7: hộp thoại "Open in another app?" cho tab incognito. Hiện mọi scheme ngoài `tel:`/`mailto:`/`sms:`/`smsto:` bị `Blocked` lặng lẽ.
- Phase 7: fullscreen (`onShowCustomView`) chưa có gợi ý cách thoát, nên trang có thể vẽ thanh địa chỉ giả.
- Phase 7: dialog quyền dùng API mới (`allow/block/dismiss(pending…)`) với đúng `pending` đang vẽ, và hiển thị `pending.host` (trang top-level).
- Phase 7: "Download image/link" (`userInitiated`) không mang cookie để tránh DownloadManager gửi cookie qua redirect. Ảnh hoặc tệp cần đăng nhập có thể thất bại; phase 7 cân nhắc (ví dụ tải trong process cho các tải này).
- Phase 7/8: huỷ và thông báo tiến độ cho tải trong process (incognito). Hiện chỉ có snackbar khi xong hoặc lỗi.
- Phase 7: tab đang hiện vẫn dùng hộp thoại JS mặc định của WebView (tối đa 3 mỗi tài liệu); có thể thay bằng dialog theo design sau.
- Phase 9: thumbnail tab thường nằm trong RAM suốt process và tăng theo số tab (phát hiện L3); quyết LRU theo byte.

## Concerns

- DownloadManager tự theo redirect và gửi lại header `Cookie` của URL đầu cho mọi chặng. Phần 2 thu hẹp rủi ro: cookie chỉ đi với tải do trang mở qua https, với tiền đề URL của `DownloadListener` đã là URL sau redirect của trang (4D-2 đo). Một redirect do server tải tự đặt vẫn mang cookie đó theo; đây là giới hạn còn lại của DownloadManager với thiết kế đã chốt.
- Khi Activity dừng, event không có người nhận bị mất (SharedFlow không replay); `refresh()` ở `onStart` chỉ bù cho download DownloadManager kết thúc lúc receiver gỡ.
- Prompt quyền runtime dùng chung `ActivityHost.requestPermissions` với prompt lưu trữ của tải xuống: hai prompt chồng nhau thì prompt trước bị trả "từ chối" (hành vi của `ActivityResultHost`).
- Không kiểm được trên JVM:
  - Dây nối incognito trong `WebViewPool` (`SkimWebView(…, incognito)`, `SkimWebViewClient(…, incognito, …)`), vì Robolectric không có WebView profiles.
  - Phần "đo thư mục cha gần nhất đã tồn tại" của `freeBytes()`, vì `ShadowStatFs` khớp theo tiền tố đường dẫn và không cần thư mục tồn tại.
  - Cả hai được kiểm bằng đọc mã và đưa vào 4D-2.
- Loại trừ autofill ở incognito là đánh đổi sản phẩm [DEFAULT — chờ validate].

## Câu hỏi chưa giải quyết

1. Autofill ở incognito: giữ loại trừ (`IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`, mặc định hiện tại) hay bỏ để người dùng vẫn điền mật khẩu đã lưu? [DEFAULT — chờ validate]
2. beforeunload vượt 3 lần mỗi tài liệu hoặc ở tab không hiện: giữ `confirm()` (cho rời trang, như hiện tại) hay `cancel()` đúng như câu chữ của quyết định H8?
