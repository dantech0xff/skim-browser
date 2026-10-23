# Báo cáo: lượt 4D-2a của phase 4 (test thiết bị API 36: tải xuống, quyền site, liên kết ngoài, hồ bơi tab, dây nối WebView thật)

## Kết quả

- Status: DONE_WITH_CONCERNS. Đủ các mục A–D và G1–G5, chỉ trên `skim_api36` (`emulator-5580`, WebView 133.0.6943.137). Không `git add`/commit/push; không sửa manifest, Gradle, `design/`, `plans/` (trừ report này), `.idea/`.
- 40 test thiết bị mới: 8 lớp mới và 3 ca mới trong `MainActivityLifecycleTest`. Cả bộ nay có 74 test: 73 qua, 1 skip theo assumption (`IncognitoUnavailableTest`, đúng thiết kế).
- Lượt này lộ **1 lỗi thật của mã sản phẩm**, đã sửa tối thiểu trong `TabManager.attach()` và có test tái hiện (JVM + thiết bị). Khi một `MainActivity` mới thay `MainActivity` cũ còn sống, các tab vẫn báo `pageLoaded = true` theo WebView của Activity cũ. Vì vậy `awaitPageReady` trả `true` khi pool mới chưa có trang.
- Xác nhận tiền đề chính sách cookie của tải xuống: `DownloadListener` luôn nhận URL **sau** redirect, cả với link `download` lẫn với điều hướng.
- Có 3 giới hạn mới được đo trên thiết bị (xem Deviations): tên do trang đặt cho tải `data:`/`blob:` không bao giờ tới Skim; trang thu hồi `blob:` URL ngay sau cú bấm làm tải thất bại; CSP `connect-src` không cho `blob:` cũng làm tải thất bại.
- Đo độ chập chờn trên mã cuối: không có lượt đỏ nào. Mỗi lớp mới hoặc đã sửa chạy 5/5 lần xanh (45 lượt); cả bộ chạy 3/3 lần `OK (74 tests)`; hai lần `connectedDebugAndroidTest` qua Gradle đều BUILD SUCCESSFUL.

## Kiểm chứng (chạy thật)

| Lệnh | Kết quả |
|---|---|
| `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest --console=plain` (mã cuối) | BUILD SUCCESSFUL 4m23s; 74 test, 0 lỗi thật, 1 assumption skip (`IncognitoUnavailableTest`: "this WebView has profiles (MULTI_PROFILE)"); đếm bằng `sumconnected.py` |
| Như trên, lượt đầu (trước một sửa chỉ trong comment: thay `…` bằng `...`) | BUILD SUCCESSFUL 4m19s; 74 test, 0 lỗi thật, 1 assumption skip |
| `p4d1-pull-loop.sh <lớp> 5` cho 9 lớp mới/đổi, sau khi cài APK cuối | 45/45 lượt xanh: `DownloadDeviceTest` 5/5 (5 test), `DownloadLimitsDeviceTest` 5/5 (5), `BlobDownloadDeviceTest` 5/5 (7), `SitePermissionDeviceTest` 5/5 (7), `ExternalLinksTest` 5/5 (5), `TabPoolDeviceTest` 5/5 (2), `IncognitoPrivacyDeviceTest` 5/5 (3), `JsDialogDeviceTest` 5/5 (3), `MainActivityLifecycleTest` 5/5 (9) |
| `p4d1-repeat.sh 3` (cài APK cuối, `am instrument` cả bộ 3 lần) | 3/3 `OK (74 tests)`: 251 s, 246 s, 248 s; mỗi lượt đúng 1 skip là `IncognitoUnavailableTest` |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; 884 test (883 cũ + 1 mới), 83 lớp, 0 lỗi, 0 skip |
| Test JVM mới trước khi sửa `TabManager` | Đỏ tại `TabManagerPageTest.kt:529` (`pageLoaded` vẫn `true`); xanh sau khi sửa |
| `./gradlew :app:assembleDebug --console=plain` | BUILD SUCCESSFUL |
| `./gradlew :app:lintDebug --console=plain` | 0 lỗi; đúng 123 cảnh báo cũ (UnusedResources 118, NewerVersionAvailable 3, AGP 1, InsecureBaseConfiguration 1); 0 cảnh báo trong `TabManager.kt` và các file test |
| `./gradlew :app:verifyRoborazziDebug --console=plain` | BUILD SUCCESSFUL (golden không đổi) |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL; 34/34 task chạy; 0 `w:`, 0 `e:` |
| `python3 -I p4a-hygiene.py` + quét thêm mã phát hiện và log có URL/cookie | 0 ID plan/phase/finding/"Red Team"; 0 ký tự bidi/zero-width thô; 0 secret; không `Log` mới nào (các dòng bị gắn cờ đều có từ trước: Readability, `Log.i` của 4D-1, log số của pool); 2 dòng gắn cờ "url" là log có sẵn của 4A, chỉ có chữ "URL" chứ không in URL |
| Trạng thái emulator khi bàn giao | APK hiện tại đã cài lại (task connected tự gỡ APK sau khi chạy); `/sdcard/Download` trống; MediaStore Downloads không còn hàng nào; 0 thông báo của DownloadManager; quyền CAMERA/RECORD_AUDIO/LOCATION chưa cấp (như lúc nhận); không còn tiến trình `am instrument` nào |

Độ chập chờn đo được trong lúc phát triển, ghi lại để không che giấu: mọi lượt đỏ đều do lỗi của test mới (đã sửa) hoặc do hành vi WebView mà fixture phải theo, không có lượt nào do môi trường.
- Hai ca quyền site đỏ tất định: `awaitRequest()` được gọi bên trong `onMain {}` nên chặn chính main thread mà bridge và WebView cần; sửa bằng cách chờ ngoài main thread.
- Ca CSP đỏ vì Chromium chuẩn hoá `'` trong query thành `%27`, nên `awaitLoaded(url)` không khớp URL.
- Assert cuối của ca vòng `alert()` kiểm focus khi cửa sổ hộp thoại thứ 3 còn đang đóng.
- Ca `huge.bin` đỏ vì WebView không giao tải cho listener khi máy chủ chỉ gửi header và 1 byte (xem Ghi nhận).

## Lỗi tìm thấy trên thiết bị

| Triệu chứng | Nguyên nhân gốc | Sửa | Test tái hiện |
|---|---|---|---|
| Hai `MainActivity` chồng nhau (Activity mới mở bằng `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_CLEAR_TASK` khi Activity cũ còn chạy). Sau khi Activity cũ bị huỷ, `awaitPageReady(tab)` trả `true` sau 67 ms, nhưng WebView của pool mới vẫn đang khôi phục: `evaluate` trả `null`, rồi `"undefined complete"` (`window.ticks` chưa có). Vì vậy `extractPage`/`highlightBlock` của Reader/AI thất bại ngay lần đầu sau khi app được mở lại kiểu này | `TabManager.attach(newPool)` thay engine đang gắn nhưng giữ nguyên `TabRuntime` (`pageLoaded`, `progress`, `find`) do WebView của pool cũ báo. Khi Activity cũ bị huỷ, pool cũ không phát `onWebViewDestroyed` (đúng chủ đích từ sửa M1 của 4C: pool đã bị thay không được reset runtime của pool mới), nên `pageLoaded = true` cũ không bao giờ bị xoá. Đã chứng minh bằng log chẩn đoán tạm (đã hoàn nguyên): ngay trước `awaitPageReady`, runtime là `pageLoaded=true, loadNonce=1` của WebView cũ | `attach(engine)`: khi đang có engine khác gắn (bị thay khi còn sống), gọi `forgetLivePages()` (`progress = null`, `pageLoaded = false`, `find = null`, xoá `pageStartedSinceVisit`) trước khi gắn engine mới. `detach` dùng chung hàm này. Thêm KDoc. Không đổi hành vi khi `attach` lần đầu hay sau `detach` | JVM: `TabManagerPageTest.anEngineThatTakesOverFromALiveOneHasNoLoadedPageUntilItLoadsOne` (đỏ trước khi sửa, xanh sau). Thiết bị: `MainActivityLifecycleTest.aNewerInstanceStartedOverAnOlderOneKeepsItsPagesRunningOnceTheOlderOneIsGone` (trước khi sửa: NPE vì `evaluate` trả `null`; sau khi sửa: xanh 5/5 và 3/3 trong cả bộ) |

## Giả định 4C đã xác nhận / bị bác

Xác nhận trên WebView 133:
- **Cầu nối blob qua `evaluateJavascript`** chạy thật, và trang không còn thuộc tính `__skimBlobRead_*` nào sau mọi kết cục (kiểm bằng `Object.getOwnPropertyNames(window)`).
  - Blob 1.000 B: 1 lát, đúng byte.
  - Blob 3 MiB: 8 lát 384 KiB, SHA-256 khớp.
  - Blob rỗng: lưu thành tệp 0 B.
  - Blob 26 MiB: `DownloadFailed`, 0 lát được đọc (đếm bằng `FileReader.prototype.readAsDataURL` bọc trong trang).
  - Trang điều hướng sau lát thứ 2: `DownloadFailed`, không lưu gì.
  - `BlobBridge.destroy()` giữa chừng: `DownloadFailed`, trang dừng đọc thêm lát.
  - CSP `connect-src 'self'`: `DownloadFailed` (như 4C dự báo).
- **DownloadManager và receiver.**
  - Request của DownloadManager chỉ có `Accept-Encoding: identity`, `Connection`, `Host`, `User-Agent`. User-Agent là UA của WebView, và không có `Cookie` qua http dù request của chính trang có `Cookie: skim=normal`.
  - Tên tệp đã sanitize đúng: `reports/Q3: report.pdf` → `Q3_ report.pdf` (cả `COLUMN_TITLE` lẫn tệp `Download/Q3_ report.pdf`).
  - `refresh()` của tracker cho `DownloadCompleted("Q3_ report.pdf", content://downloads/all_downloads/<id>, "application/pdf")`; URI mở được và đúng byte.
  - Receiver `ACTION_DOWNLOAD_COMPLETE` của `MainActivity` báo được tải do trang tự mở.
  - Tải xong khi Activity đã ở `CREATED` (máy chủ giữ phản hồi tới lúc dừng) không có sự kiện nào trong lúc dừng, và có `DownloadCompleted` ngay khi `RESUMED`.
  - DownloadManager cũng ghi tệp của nó vào `MediaStore.Downloads` dưới tên Skim. `DownloadManager.remove` xoá cả tệp lẫn thông báo.
- **Tiền đề cookie (H6).** `DownloadListener` nhận URL sau redirect, cả với `<a download>` lẫn `location.href`, kèm Content-Disposition của phản hồi cuối. Máy chủ chỉ thấy đúng 2 request `/redirect`, đều của WebView; DownloadManager chỉ xin URL cuối.
- **Tải trong process (incognito).**
  - Request chỉ mang cookie của phiên (`skim=incognito`, không có `skim=normal`) và UA của WebView.
  - DownloadManager không có hàng nào.
  - `MediaStore.Downloads`: `IS_PENDING = 0`, `DISPLAY_NAME = Q3_ report.pdf`, `MIME_TYPE = application/pdf`, `RELATIVE_PATH = Download/`.
  - Redirect sang `localhost` (site khác) chỉ mang cookie của chính `localhost` (`skim=local`).
  - `Content-Length` 3 GiB bị từ chối trước khi có hàng MediaStore nào (`ContentObserver` đếm 0) và sớm hơn read timeout 15 s.
  - `freeBytes()` khớp `StatFs(/storage/emulated/0)` (sai số cho phép 64 MiB).
- **Hạn mức tải.**
  - 3 tải tự động liên tiếp: 1 `DownloadCompleted` và 2 `DownloadFailed`.
  - Mỗi tap (ở bất kỳ đâu trên trang, hoặc trên chính link) mở đúng 1 tải.
  - Drag cuộn trang không mở tải nào, và một tap ngay sau đó thì mở được.
- **Quyền site** đúng mọi mục B:
  - `PendingSitePermission(tab, "127.0.0.1", {CAMERA, MICROPHONE}, false)`.
  - `allow(remember = true)` cho `granted audio,video` và lưu `ALLOWED` vào `SitePermissionRepository`; lần sau không hỏi.
  - `block`: `NotAllowedError`, lưu `BLOCKED`, lần sau không hỏi.
  - `dismiss`: không lưu gì, lần sau vẫn hỏi.
  - iframe `localhost` trong trang `127.0.0.1` không nhận quyết định đã lưu của `localhost`, và yêu cầu mang host của trang.
  - Tab incognito: `incognito = true`, quyết định chỉ ở `IncognitoSession`, không có hàng trong repository, mất khi phiên kết thúc.
  - Location trên http bị từ chối ngay (`code 1`), không hiện yêu cầu.
  - Tab nền chờ tới khi được chọn.
- **Liên kết ngoài.**
  - `tel:`/`mailto:`/`intent:` chỉ mở app sau tap.
  - Intent đã làm sạch: BROWSABLE, `flags == 0`, `component`/`selector` null; `package=com.dantech.skimbrowser` bị chặn.
  - Tab incognito chỉ giao `tel:`, `mailto:`, `sms:`, `smsto:`; chặn `intent:`, `market:`, `geo:`.
  - `ActivityNotFoundException` thật → tải `browser_fallback_url` http; fallback `file:` bị bỏ.
  - `file:`/`content:` không bao giờ tải. `javascript:` qua `TabManager.load`/`newTab` không chạy.
- **Dây nối incognito.**
  - `onCreateInputConnection` đặt `IME_FLAG_NO_PERSONALIZED_LEARNING` chỉ ở tab incognito.
  - `importantForAutofill = NO_EXCLUDE_DESCENDANTS` chỉ ở tab incognito.
  - `console.log` của trang incognito không vào logcat; của tab thường thì có.
- **Hộp thoại JS.**
  - Tab nền: `alert`/`confirm`/`prompt` trả `undefined`/`false`/`null` ngay, cửa sổ app giữ focus, trang chạy tiếp.
  - `beforeunload` ở tab nền vẫn rời trang; cùng trang đó khi đang hiện thì WebView hỏi trước, và Back giữ trang lại.
  - Tab đang hiện chạy vòng 6 `alert()`: đúng 3 hộp thoại (mỗi hộp thoại giữ ≥ 500 ms tới khi Back), 3 lần sau trả về ngay.
- **Hồ bơi 10 tab.**
  - Tối đa 3 tab có trang sống sau mỗi tab mới; mọi dòng `SkimWebViewPool` "… N live" của test đều có N ≤ 3.
  - Quay lại tab 1 thì đúng URL và vị trí cuộn 2400.
  - Tệp state bị cắt một nửa: không crash; tab tải lại trang (`history.length == 1`, ở đầu trang), rồi một state mới được lưu.

Bị bác hoặc điều mới:
- Runtime cũ sống sót sau khi Activity mới thay Activity cũ còn sống: lỗi thật, đã sửa (bảng trên).
- KDoc của `DownloadRouter` ghi "a `data:` or `blob:` URL only has one [name] through Content-Disposition". Trên WebView 133 nó **không bao giờ** có: listener nhận `contentDisposition = ""` cho cả `data:` (mime = media type của URL, `contentLength = 0`) lẫn `blob:` (mime = type của blob, `contentLength` = kích thước), dù link có thuộc tính `download`. Tệp được đặt tên theo type: `download.txt`.
- WebView chỉ giao tải cho listener khi đã có các byte đầu của body. Máy chủ gửi header + 1 byte rồi im thì không có lời gọi listener nào trong 8 s; gửi 64 KiB thì có ngay, với `contentLength = 3221225472`. Fixture `/huge.bin` vì thế gửi 64 KiB rồi im.
- Trang thu hồi `blob:` URL ngay sau cú bấm (`a.click(); URL.revokeObjectURL(url)`, hoặc `setTimeout(…, 0)` trong handler click) làm tải blob thất bại (`DownloadFailed`), vì Skim đọc blob từ trang sau khi WebView báo tải. Đo bằng probe tạm, không thành test.
- Với ca tab nền xin camera, test thiết bị chỉ thấy kết quả đầu-cuối (không hỏi khi ở nền, hỏi ngay khi tab được chọn). Nó không phân biệt được Chromium hay bridge là bên giữ yêu cầu.

## Test đã viết

- `browser/DownloadDeviceTest` (5): DownloadManager cho tab thường; tải incognito trong process; redirect sang site khác; `Content-Length` vượt 2 GiB; `freeBytes()` khớp `StatFs`.
- `browser/DownloadLimitsDeviceTest` (5): 3 tải tự động; tap; cuộn; `data:` đúng byte + những gì listener nhận; URL listener nhận khi có redirect.
- `browser/BlobDownloadDeviceTest` (7): các ca ở mục A5.
- `browser/SitePermissionDeviceTest` (7): các ca ở mục B.
- `browser/ExternalLinksTest` (5): các ca ở mục C.
- `browser/TabPoolDeviceTest` (2): 10 tab và khôi phục; tệp state bị cắt.
- `browser/IncognitoPrivacyDeviceTest` (3): G1.
- `browser/JsDialogDeviceTest` (3): G2.
- `MainActivityLifecycleTest` (+3): G3, receiver, `refresh()`.
- Hạ tầng:
  - `browser/DeviceDownloads`: rule dọn mọi tải của DownloadManager và mọi hàng MediaStore của Skim trước và sau mỗi test. Sau test, nó chờ tới khi `/sdcard/Download` (nhìn bằng shell) giống hệt lúc trước test. Có thêm truy vấn hàng và đếm thay đổi MediaStore.
  - `TestPages` có các endpoint `/report.pdf`, `/redirect`, `/moving.pdf`, `/huge.bin`, cách giữ phản hồi `holdAfterFirst`/`release`, header CSP `?csp=`, và host `localhost`.
  - `DeviceTesting`: `shell()`/`shellAutomation()`, từ API 31 dùng `UiAutomation.FLAG_DONT_USE_ACCESSIBILITY` để không bật cây trợ năng của WebView cho các test sau.
  - `BrowserRig` có thêm `downloadEvents`, `awaitDownloadEvents`, `loadIn`, `shownWebView`, `scrollAndKeep`, `isScrolledTo`, và `TestActivityHost.startExternalWith`.
  - 7 fixture HTML mới: `download`, `blob`, `media`, `frame`, `link`, `dialogs`, `form`.
- Số dòng:
  - File test mới: 1.452 dòng Kotlin (kể cả `DeviceDownloads`) và 171 dòng HTML.
  - File test đã sửa: `BrowserRig` +85/−2, `DeviceTesting` +21, `TestPages` +91/−13, `MainActivityLifecycleTest` +140/−4, `IncognitoProfileTest` +4/−18, `PopupAndTrimTest` +2/−8, `SkimWebViewEngineTest` +6/−62 (ba file cuối chỉ đổi sang helper chung, không đổi hành vi).
  - Mã sản phẩm: `TabManager.kt` +15/−3. Test JVM: `TabManagerPageTest.kt` +1 test.

## Deviations

- Không có `GrantPermissionRule`: `androidx.test:rules` không có trong classpath androidTest (đã kiểm `debugAndroidTestRuntimeClasspath`), và thêm nó cần sửa `app/build.gradle.kts`, file không được đụng. Quyền CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION được cấp bằng `UiAutomation.grantRuntimePermission`, đúng hàm `GrantPermissionRule` gọi bên trong.
- Test hạn mức tải dùng link `data:` (lưu qua MediaStore) thay cho http, để không phụ thuộc lịch job của DownloadManager. Đường DownloadManager được phủ ở A1 và G5. Giới hạn "tối đa 2 tải `data:`/`blob:` đồng thời" và "thêm 1 tải tự động sau 30 s" không có test thiết bị, vì đó là logic Kotlin thuần đã có test JVM (`DownloadRouterTest`).
- Ca fallback của `intent:` dùng `TestActivityHost.startExternalWith = { activity.startActivity(it) }`, giống hệt `ActivityResultHost.startExternal`. Không dùng được `ActivityResultHost` thật trên rig vì nó phải được tạo trước STARTED.
- `MainActivity` thứ hai được mở bằng `Application.startActivity(NEW_TASK | CLEAR_TASK)`, như khi app khác mở link, và theo dõi bằng `ActivityLifecycleCallbacks`. Hộp thoại JS được đóng bằng `input keyevent 4` qua shell. Test chỉ bấm Back khi tiêu đề trang vẫn là "Alert k" và cửa sổ app mất focus liên tục 500 ms, nên Back không bao giờ rơi vào Activity.
- Ca blob "đúng tên" khẳng định tên `download.txt` (đặt theo type), vì WebView 133 không đưa tên của trang (xem trên).
- Ba test của 4D-1 được đổi sang helper chung của `BrowserRig` (`shownWebView`, `scrollAndKeep`, `isScrolledTo`, `loadIn`). Đây là chuyển mã y nguyên, không đổi hành vi.
- Các phần API 28 (MediaStore.Files, thư mục Downloads công khai, quyền lưu trữ, gate theo năng lực) để lại cho 4D-2b như prompt yêu cầu. Chỉ có `DeviceDownloads.removeAll()` bỏ qua MediaStore dưới API 30 để không crash.
- Known deviations (đề xuất ghi vào phase file; lượt này không được sửa `plans/`):
  1. Tải `data:`/`blob:` mất tên do trang đặt, nên được lưu thành `download.<ext>`.
  2. Tải `blob:` thất bại khi trang thu hồi URL ngay sau cú bấm.
  3. Trang có CSP `connect-src` không cho `blob:` thì không giao được blob (`DownloadFailed`).
  4. WebView chỉ giao tải sau các byte đầu của body (vô hại).

## Checklist tay cho controller (lệnh adb đã thử)

Harness tạm: `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/p4d2a-manual/ManualProbe2.kt`, không có trong repo.
- Cách dùng: chép vào `app/src/androidTest/java/com/dantech/skimbrowser/`, chạy `ANDROID_SERIAL=emulator-5580 ./gradlew :app:installDebug :app:installDebugAndroidTest`, rồi xoá file sau phiên.
- Mỗi method đặt tab thật lên `MainActivity` (với `ActivityResultHost` và receiver thật) và giữ trong `holdMs`.
- Harness cố ý không dùng UiAutomation. Nếu một instrumentation đang giữ UiAutomation (ví dụ rule `DeviceDownloads`) mà ta chạy `adb shell uiautomator dump`, instrumentation sẽ crash lúc kết thúc ("UiAutomationService … not registered!"). Đã gặp một lần; lỗi của phiên tay, không phải của app.

1. Thông báo DownloadManager hiện/biến mất, mở tệp bằng Files (đã thử, đạt):
   - `adb -s emulator-5580 shell am instrument -w -e class com.dantech.skimbrowser.ManualProbe2#downloadNotification -e holdMs 45000 com.dantech.skimbrowser.test/androidx.test.runner.AndroidJUnitRunner &`. Chờ `adb -s emulator-5580 logcat -d -s SkimManual` có `ready`.
   - `adb -s emulator-5580 shell dumpsys notification --noredact | grep -A40 "pkg=com.android.providers.downloads" | grep -E "android.title|android.text"` → `Q3_ report.pdf` / `Download complete.` (channel `complete`). `adb -s emulator-5580 shell ls -la /sdcard/Download/` → `Q3_ report.pdf`, 193 B.
   - `adb -s emulator-5580 shell am start -a android.intent.action.VIEW_DOWNLOADS`. Sau đó `adb -s emulator-5580 shell uiautomator dump /sdcard/skim-ui.xml && adb -s emulator-5580 shell "grep -o 'text=\"Q3_ report.pdf\"[^>]*bounds=\"[^\"]*\"' /sdcard/skim-ui.xml"` → DocumentsUI liệt kê tệp.
   - `adb -s emulator-5580 shell input tap 315 1194` → `dumpsys activity activities | grep topResumedActivity` cho `com.google.android.apps.docs/com.google.android.apps.viewer.PdfViewerActivity`, mở đúng tệp.
   - `input keyevent 4` hai lần để quay lại. Khi harness kết thúc (xoá tải): số thông báo `providers.downloads` = 0, `/sdcard/Download` trống. Dọn: `adb -s emulator-5580 shell rm /sdcard/skim-ui.xml`.
2. Trim với tab thật (đã thử):
   - `…ManualProbe2#trimWithRealTabs -e holdMs 25000` (3 tab, không có eviction nào trước đó).
   - `adb -s emulator-5580 shell am send-trim-memory com.dantech.skimbrowser RUNNING_CRITICAL && adb -s emulator-5580 logcat -d -s SkimWebViewPool` → `Evicted 2, 1 live, capacity 1` và `Memory warning 15: EVICT, capacity 1 (was 3), 1 live`.
   - `am send-trim-memory … BACKGROUND` → hệ thống từ chối: "Unable to set a background trim level on a foreground process" (như 4D-1).
3. Hộp thoại quyền runtime của hệ thống (đã thử, đạt):
   - `adb -s emulator-5580 shell pm revoke com.dantech.skimbrowser android.permission.CAMERA` (và `RECORD_AUDIO`), rồi `…ManualProbe2#runtimePermissionPrompt -e holdMs 40000`. Harness trả lời yêu cầu của Skim bằng `allow(pending, remember = false)`.
   - `uiautomator dump` → `com.google.android.permissioncontroller` "Allow Skim to take pictures and record video?". `input tap 540 1394` ("Only this time") → "Allow Skim to record audio?". `input tap 540 1363` → log `SkimManual: page answer: "granted audio,video"`.
   - `dumpsys package com.dantech.skimbrowser` → CAMERA/RECORD_AUDIO `granted=true`, cờ `ONE_TIME`.
4. FLAG_SECURE + Recents: đã thử ở 4D-1 (mục 3 của checklist 4D-1, harness `p4d-manual/ManualProbe.kt#secureWindow`), không chạy lại.

## Việc cho 4D-2b (gate theo năng lực khi chạy trên WebView 66 của API 28)

- `DeviceDownloads.savedFiles()` và `SavedFileChanges` dùng `MediaStore.Downloads` (API 29+/30+). Ở API 28 cần nhánh `MediaStore.Files` + thư mục Downloads công khai, hoặc gate:
  - `DownloadLimitsDeviceTest` (3 test dùng `savedFiles`), `BlobDownloadDeviceTest` (5 test dùng `savedFiles`; 2 test còn lại chỉ đọc URI), `DownloadDeviceTest.anIncognitoTabs…`/`aServer…` (vốn đã `assumeTrue(MULTI_PROFILE)`).
  - `removeAll()` đã bỏ qua MediaStore dưới API 30.
- Trước scoped storage, router xin `WRITE_EXTERNAL_STORAGE`, nên mọi test tải (rig dùng `TestActivityHost` luôn từ chối) phải cấp quyền này trước bằng `shellAutomation().grantRuntimePermission`. Với `MainActivityLifecycleTest.whileStarted…`/`aDownloadThatEnds…`, prompt thật của `ActivityResultHost` sẽ hiện nếu chưa cấp.
- Mọi ca incognito đã `assumeTrue(MULTI_PROFILE)`: `DownloadDeviceTest` (3), `SitePermissionDeviceTest.anIncognitoTabs…`, `ExternalLinksTest.anIncognitoTab…`, cả lớp `IncognitoPrivacyDeviceTest`. Trên API 28 không có profiles, nên các ca này sẽ hiện là skip theo assumption (AGP ghi thành `<failure>`; đếm bằng `AssumptionViolatedException:`).
- Cần kiểm trên WebView 66, gate nếu khác:
  - `getUserMedia` trên `http://127.0.0.1`/`localhost` (secure context), iframe với `allow="camera; microphone"` (Feature Policy), và camera/mic của AVD `skim_api28`.
  - `listener` có nhận tải `data:`/`blob:` không, và có Content-Disposition không (ca `aDataLink…` khẳng định CD rỗng như WebView 133).
  - `history.length`, CSP header `connect-src`.
  - Hành vi "chỉ giao tải sau byte đầu" (`/huge.bin`).
  - `beforeunload` cần user activation.
- `shellAutomation()` dưới API 31 dùng UiAutomation mặc định, có kết nối trợ năng. Trên API 28, WebView của các test sau một test dùng `shell()` có thể dựng cây trợ năng; nếu thấy chập chờn ở test cử chỉ thì tách lượt chạy.
- `JsDialogDeviceTest` dựa vào tiêu đề trang (`onReceivedTitle`) và focus cửa sổ; `ExternalLinksTest` dựa vào `request.hasGesture()` (API 24+). Cả hai nên chạy được, nhưng chưa thử.

## Concerns / Blockers

- Sửa trong `TabManager.attach()` chỉ xoá trạng thái khi Activity mới gắn vào. Từ lúc đó tới lúc Activity cũ bị huỷ (thường dưới 1 s), WebView của pool cũ vẫn báo sự kiện vào `TabManager`. Ví dụ, một trang của Activity cũ đang tải dở mà `onPageFinished` lúc đó sẽ đặt lại `pageLoaded = true` cho tab mà pool mới chưa có. Test G3 không chạm tới khe này (trang của Activity cũ đã đứng yên). Muốn đóng hẳn thì pool không còn gắn phải bỏ mọi sự kiện, trừ `onStateSaved` (state lúc `onStop` của Activity cũ vẫn là state mới nhất của tab). Đây là quyết định thiết kế, chưa làm.
- Hai giới hạn tải blob/data (mất tên, thu hồi URL ngay sau cú bấm) ảnh hưởng tới nút "Export"/"Download" phổ biến trên web thật. Cách khắc phục đều cần thêm script chạy từ đầu trang (`DOCUMENT_START_SCRIPT` có trên WebView 133), nên cần quyết định sản phẩm (xem câu hỏi).
- Task `connectedDebugAndroidTest` của AGP gỡ cả hai APK sau khi chạy, và các quyền test đã cấp mất theo. Lượt chạy `am instrument` sau đó cần `installDebug` + `installDebugAndroidTest` trước (đã cài lại khi bàn giao).
- Máy host vẫn tải nặng nhưng không có ANR hay timeout môi trường nào trong mọi lượt đo của lượt này.

## Câu hỏi chưa giải quyết

1. Tải `data:`/`blob:`: chấp nhận tên `download.<ext>`, hay ghi lại tên của thuộc tính `download` lúc trang bấm (script chạy từ đầu trang, chỉ trên WebView có `DOCUMENT_START_SCRIPT`)?
2. Blob bị trang thu hồi ngay sau cú bấm: chấp nhận `DownloadFailed`, hay hoãn việc thu hồi (bọc `URL.revokeObjectURL` từ đầu trang) cho blob vừa được bấm tải?
3. Pool đã bị Activity mới thay thế: có bỏ mọi sự kiện của nó (trừ `onStateSaved`) cho tới khi nó bị huỷ không?
