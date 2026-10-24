# Báo cáo: lượt 4D-2b của phase 4 (cổng thiết bị API 28, dữ liệu trên SQLite cũ, gate theo năng lực)

## Kết quả

- Status: DONE_WITH_CONCERNS. Đủ các mục E, F, G, H và checklist tay. Không `git add`/commit/push; không sửa manifest, Gradle, `design/`, `.idea/`, `plans/` (trừ report này).
- Bộ test thiết bị nay có 82 test (74 cũ, 4 test dữ liệu mới, 3 test thư mục Downloads mới, 1 test tách ra từ test Desktop site).
  - `skim_api28` (Android 9, WebView 66.0.3359.158, SQLite 3.22.0): 4/4 lượt cả bộ xanh. Mỗi lượt có 58 test qua và 24 test tự bỏ qua theo assumption có lý do đọc được; không test nào đỏ.
  - `skim_api36` (WebView 133.0.6943.137, SQLite 3.44.3): 4/4 lượt cả bộ xanh. Mỗi lượt có 80 test qua và 2 test bỏ qua đúng thiết kế.
- `IncognitoUnavailableTest`, 4 test dữ liệu và test "bị từ chối quyền lưu trữ" chạy thật và xanh trên API 28. `DeviceCapabilitiesReportTest` xanh ở cả hai máy; trên WebView 66 nó báo lỗi parse của Readability thay vì fail.
- Lượt này lộ **1 lỗi thật của mã sản phẩm**, liên quan bảo mật, và đã sửa tối thiểu trong `DownloadRouter` (+7/−3 dòng) kèm test JVM tái hiện.
  - Trên WebView 66, link có thuộc tính `download` được giao cho listener trước khi WebView gửi request nào. Listener nhận địa chỉ của chính link (chưa theo redirect), không có type, không có độ dài.
  - Skim vẫn gắn cookie của profile mặc định cho DownloadManager, và DownloadManager theo redirect mang cookie đó sang host khác.
  - Controller nên duyệt lại cách sửa (xem bảng lỗi và Câu hỏi 1).
- Không có ANR, crash hay lượt chập chờn nào do môi trường trong mọi lượt đo trên mã cuối.

## Kiểm chứng (chạy thật, trên mã cuối)

| Lệnh | Kết quả |
|---|---|
| `p4d2b-repeat.sh emulator-5582 3` (cài APK, `am instrument` cả bộ 3 lần) | 3/3 `OK (82 tests)`, mỗi lượt khoảng 180 s: 58 qua, 24 skip theo assumption, 0 lỗi; 0 `ANR in`, 0 `FATAL EXCEPTION` trong logcat |
| `ANDROID_SERIAL=emulator-5582 ./gradlew :app:connectedDebugAndroidTest --console=plain` (gỡ APK trước, nên quyền lưu trữ được cấp lại từ đầu) | BUILD SUCCESSFUL 3m7s; 82 test, 0 lỗi thật, 24 assumption skip; chỉ `emulator-5582` (`sumconnected.py`) |
| `p4d2b-repeat.sh emulator-5580 3` (API 36, sau khi khởi động lạnh và làm nóng) | 3/3 `OK (82 tests)`, mỗi lượt khoảng 250 s: 80 qua, 2 skip (`IncognitoUnavailableTest`, test bị từ chối quyền lưu trữ), 0 lỗi, 0 ANR |
| `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest --console=plain` | BUILD SUCCESSFUL 4m28s; 82 test, 0 lỗi thật, 2 assumption skip; chỉ `emulator-5580` |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; task chạy thật (không lấy từ cache); 886 test, 83 lớp, 0 lỗi, 0 skip (`DownloadRouterTest` 25) |
| Test JVM mới trước khi sửa `DownloadRouter` | Đỏ: `expected null, but was:<session=default>`; xanh sau khi sửa |
| `./gradlew :app:assembleDebug --console=plain` | BUILD SUCCESSFUL |
| `./gradlew :app:lintDebug --console=plain` | 0 lỗi; đúng 123 cảnh báo cũ (UnusedResources 118, NewerVersionAvailable 3, AGP 1, InsecureBaseConfiguration 1); 0 cảnh báo trong file đã sửa hoặc mới |
| `./gradlew :app:verifyRoborazziDebug --console=plain` | BUILD SUCCESSFUL (golden không đổi) |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL; 34/34 task chạy; 0 `w:`, 0 `e:` |
| `python3 -I p4a-hygiene.py` | 0 ID plan/phase/finding/"Red Team"; 0 ký tự bidi/zero-width thô; 0 secret; không `Log` mới (7 dòng bị gắn cờ đều có từ trước; lượt này bỏ bớt 1 dòng `Log.i` của test Desktop site). Không còn harness tạm hay log chẩn đoán nào trong repo |
| Trạng thái khi bàn giao | Không còn emulator nào chạy (`ps` không có `qemu-system`). Cả hai AVD không còn APK của Skim (giống lúc nhận), `/sdcard/Download` trống, 0 thông báo của DownloadManager |

Script trong scratchpad (không nằm trong repo): `p4d2b-repeat.sh <serial> <N> <prefix>` (bản sao `p4d1-repeat.sh`, nhận serial làm tham số) và `p4d2b-class.sh <serial> <classes|all> <N> <prefix>`.

## SkimGate (`DeviceCapabilitiesReportTest`)

- `skim_api28`:
  - Mọi feature đều `false`: `MULTI_PROFILE`, `HTTP_CACHE_MANAGER`, `SAVE_STATE`, `USER_AGENT_METADATA`, `GET_WEB_VIEW_RENDERER`, `WEB_VIEW_RENDERER_TERMINATE`, `WEB_MESSAGE_LISTENER`, `DOCUMENT_START_SCRIPT`, `DELETE_BROWSING_DATA`.
  - `WEBVIEW_PACKAGE=com.google.android.webview`, `WEBVIEW_VERSION=66.0.3359.158`, `SDK_INT=28`, `SQLITE_VERSION=3.22.0`.
  - `READABILITY_TYPEOF=null`, `READABILITY_PARSE_ERROR=SyntaxError: Unexpected token ?`, `READABILITY_READERABLE=true`.
- `skim_api36`:
  - `MULTI_PROFILE=true`, `HTTP_CACHE_MANAGER=false`, `SAVE_STATE=false`; `USER_AGENT_METADATA`, `GET_WEB_VIEW_RENDERER`, `WEB_VIEW_RENDERER_TERMINATE`, `WEB_MESSAGE_LISTENER`, `DOCUMENT_START_SCRIPT`, `DELETE_BROWSING_DATA` đều `true`.
  - `WEBVIEW_VERSION=133.0.6943.137`, `SDK_INT=36`, `SQLITE_VERSION=3.44.3`.
  - `READABILITY_TYPEOF=function`, `READABILITY_READERABLE=true`; không có `READABILITY_PARSE_ERROR`.

## Bảng test API 28 (lượt `am instrument` đầu của mã cuối; 3 lượt sau và lượt Gradle giống hệt)

Lý do bỏ qua, nguyên văn:
- (P) `This WebView (com.google.android.webview 66.0.3359.158) lacks MULTI_PROFILE, which every incognito tab needs`
- (S) `Skim's page scripts do not run on this WebView (com.google.android.webview 66.0.3359.158): SyntaxError: Unexpected token ?`
- (R) `... lacks GET_WEB_VIEW_RENDERER, which the test needs to reach the page's renderer`
- (H) `... lacks USER_AGENT_METADATA, which Skim needs to set the client hints sites see`

| Lớp | Chạy và qua | Bỏ qua (lý do) |
|---|---|---|
| `DeviceCapabilitiesReportTest` | 1 | 0 |
| `MainActivityLifecycleTest` | 8 | `finishingMakesEveryClose…AndEndsTheIncognitoSession` (P) |
| `BlobDownloadDeviceTest` | 7 | 0 |
| `DownloadDeviceTest` | 2 (tab thường qua DownloadManager, `freeBytes`) | 3 ca incognito (P) |
| `DownloadLimitsDeviceTest` | 5 | 0 |
| `ExternalLinksTest` | 4 | `anIncognitoTabHandsOnlyCalls…` (P) |
| `IncognitoPrivacyDeviceTest` | 0 | cả 3 (P) |
| `IncognitoProfileTest` | 0 | cả 5 (P) |
| `IncognitoUnavailableTest` | 1 (chạy thật) | 0 |
| `JsDialogDeviceTest` | 3 | 0 |
| `LongPressAndThumbnailTest` | 1 (nhấn giữ) | `aShownPageGetsAThumbnail…` (P), sau khi nửa đầu (thumbnail tab thường, vẽ phần mềm từ WebView 66, lưu đĩa) đã chạy và qua |
| `PopupAndTrimTest` | 3 | 0 |
| `PullToRefreshNestedScrollTest` | 3 | 0 |
| `SharedDownloadsFolderDeviceTest` (mới) | 3 (gồm test bị từ chối quyền) | 0 |
| `SitePermissionDeviceTest` | 6 (`getUserMedia` thật trên `127.0.0.1`/`localhost`, iframe `allow`) | ca incognito (P) |
| `SkimWebViewEngineTest` | 5: find ×2, save/restore qua evict, Desktop site (UA, bề rộng layout), thứ tự callback | 9: extractor ×3, highlight ×2 (S); `anArticleIsReaderable…` (S) sau khi phần readerable chạy và qua; `aTabThatIsNotShown…` (S) sau khi phần tải offscreen chạy và qua; renderer (R); client hints (H) |
| `TabPoolDeviceTest` | 2 | 0 |
| `data.DeviceSqliteDataTest` (mới) | 4 | 0 |

## Lỗi tìm thấy trên thiết bị

| Triệu chứng | Nguyên nhân gốc | Sửa | Test tái hiện |
|---|---|---|---|
| Trên WebView 66, tap link `<a download href="/redirect?to=…">` thì listener nhận `url=…/redirect?to=…` (chưa theo redirect), `cd=""`, `mime=""`, `len=-1`, và server không thấy request nào của WebView. Ở tab thường qua https, Skim gắn cookie của profile mặc định vào request DownloadManager. DownloadManager tự theo redirect và gửi lại header `Cookie` cho mọi chặng, nên cookie (kể cả HttpOnly) đi tới host mà redirect trỏ tới. Đo bằng harness chẩn đoán tạm (đã xoá): link thường và điều hướng thì WebView 66 request trước rồi mới giao URL sau redirect kèm CD, type và độ dài, như WebView 133 | Quy tắc cookie của 4C dựa trên tiền đề "URL listener nhận là URL sau redirect của trang". Tiền đề chỉ đúng khi WebView đã tự request tải đó. Chromium 66 giao link có thuộc tính `download` ngay, chưa request gì | `DownloadRouter.startSystem`: chỉ gắn cookie khi listener có type của phản hồi (`declared != null`), tức là WebView đã request và địa chỉ là địa chỉ sau redirect. KDoc lớp và hàm ghi rõ. Tải trong process (incognito) không đổi, vì nó tự theo redirect và lấy cookie cho từng chặng | JVM: `DownloadRouterTest.aDownloadLinkHandedOverBeforeTheWebViewRequestedItCarriesNoCookies` (đỏ trước khi sửa, xanh sau). Thiết bị: `DownloadLimitsDeviceTest.aDownloadTheWebViewRequestedReachesTheListenerAtTheAddressTheServersRedirectsLeadTo` khẳng định cả hai hành vi theo năng lực. Cookie không quan sát được trên thiết bị: MockWebServer chạy cleartext, và cookie chỉ đi qua https |

Lỗi của hạ tầng test (không phải mã sản phẩm), đã sửa trong lúc làm:
- Rule chỉ cấp `WRITE_EXTERNAL_STORAGE`, nên MediaProvider từ chối truy vấn: `SecurityException … requires android.permission.READ_EXTERNAL_STORAGE`. Hộp thoại thật của Android 9 cấp cả READ (quyền tách ngầm của WRITE); đã xác nhận bằng tay: ALLOW cho cả hai `granted=true`. Rule nay cấp mọi quyền lưu trữ mà app có xin trên máy đó.
- Test Desktop site đọc `window.innerWidth`. Đó là bề rộng visual viewport, và WebView 66 làm tròn nó thành 981 khi thu nhỏ trang để vừa màn hình (scale 0,4204). Đã đo bằng log chẩn đoán tạm (đã hoàn nguyên): `document.documentElement.clientWidth` = 980 trên WebView 66. Test nay đo bề rộng layout.

## Điều đo được trên WebView 66 (danh sách "Cần kiểm" của 4D-2a)

- `getUserMedia` chạy được:
  - Trên `http://127.0.0.1` và `localhost`, kể cả trong iframe có `allow="camera; microphone"`.
  - Camera và mic của AVD hoạt động dù emulator chạy với `-no-audio`: trang nhận `granted audio,video`.
- Tải `data:`/`blob:` vẫn tới listener, nhưng giao ngay, không đọc trước, nên không có type:
  - Tải thành công vẫn đúng tên và đúng byte, vì Skim đọc type từ chính URL hoặc blob.
  - Tải blob thất bại mang tên `download` thay vì `download.txt`.
- `history.length` đúng (1 sau khi tệp state bị cắt; 2 sau khi khôi phục).
- CSP `connect-src 'self'` chặn `fetch(blob:)`, nên tải blob → `DownloadFailed`, như trên WebView 133.
- `beforeunload` (sau một tap) và hộp thoại JS chạy như trên 133. `ExternalLinksTest` (`hasGesture`) cũng vậy.
- Thứ tự callback, find (cả bước Next gửi lúc đang đếm), save/restore bằng `webView.saveState`, pull to refresh, popup, nhấn giữ và thumbnail vẽ phần mềm đều chạy đúng.
- Script readerable chạy được, vì `Readability-readerable.js` parse được: `readerable = true` trên `article.html`. Nhưng `extractPage` luôn trả null (xem Concerns).
- Chưa đo: "chỉ giao tải sau byte đầu" (`/huge.bin`), vì chỉ ca incognito dùng nó.
- Trợ năng của `UiAutomation` dưới API 31: không thấy chập chờn ở test cử chỉ trong 4 lượt cả bộ. Ca kéo bản đồ không phải thử lại lần nào (`could not cancel` = 0).

## Test và hạ tầng đã viết/sửa

- Mới:
  - `data/DeviceSqliteDataTest.kt` (4 test, 173 dòng), chạy `AppContainer` trên thư mục tạm:
    - `recordVisit` hai lần cùng URL, theo cả hai thứ tự: một dòng, `visitedAt` lớn nhất.
    - `clearAll`: WAL từ > 0 xuống 0 byte; `assumeTrue` khi máy RAM thấp và Room không dùng WAL.
    - `markClosed` 1.200 id.
    - `PRAGMA secure_delete` đọc trong transaction trên kết nối ghi: > 0.
  - `browser/SharedDownloadsFolderDeviceTest.kt` (3 test, 148 dòng):
    - Tệp Skim tự lưu nằm trong thư mục Downloads công khai, hàng MediaStore (Files trước Android 10, Downloads từ Android 10) có đúng URI mà Skim báo, và mở được qua URI đó.
    - Tên trùng thành `download (1).txt` ở cả hai máy.
    - Đường bị từ chối → `DownloadFailed`: router hỏi đúng `[WRITE_EXTERNAL_STORAGE]` qua `ActivityHost`, DownloadManager không nhận gì, không ghi gì.
- `browser/DeviceTesting.kt`: thêm `WebViewSupport`, helper năng lực dùng chung:
  - `has`, `assumeFeature`, `assumeProfiles`, `assumeNoProfiles`.
  - `assumePageScriptsRun`: chạy chính extract script của app bằng indirect eval trong một WebView riêng; lý do bỏ qua nêu lỗi parse.
  - `readsDownloadLinksFirst`: thăm dò bằng một link `download` tới `data:`.
  - Không dùng `SDK_INT` hay phiên bản Android để bỏ qua test nào.
- `browser/DeviceDownloads.kt`:
  - Nhánh trước Android 10: hàng `MediaStore.Files` tạo trong lúc test cho tệp trong thư mục Downloads công khai, đường dẫn tương đối suy từ `_data`.
  - `SavedFileChanges` theo dõi bộ sưu tập Files; `removeAll` xoá tệp rồi xoá hàng.
  - Cấp `WRITE_EXTERNAL_STORAGE` + `READ_EXTERNAL_STORAGE` bằng `UiAutomation.grantRuntimePermission` khi app có xin trên máy đó.
- `browser/BrowserRig.kt`: `TestActivityHost.permissionRequests`.
- `DeviceCapabilitiesReportTest.kt`: báo chứ không phán; thêm `SQLITE_VERSION` và `READABILITY_PARSE_ERROR` (qua `new Function`); chỉ fail khi máy không có WebView.
- `browser/SkimWebViewEngineTest.kt`:
  - 5 test gate script trang ngay từ đầu.
  - 2 test gate giữa chừng, sau phần chạy được ở mọi WebView.
  - Renderer gate qua helper.
  - Desktop site tách thành UA + bề rộng layout (mọi WebView) và client hints (gate `USER_AGENT_METADATA`).
- `browser/DownloadLimitsDeviceTest.kt`: hai test quan sát listener được viết lại theo năng lực (và đổi tên); `browser/BlobDownloadDeviceTest.kt`: tên của tải thất bại theo năng lực.
- `assets/download.html` có `?plain` (link không có thuộc tính `download`). Các test về dây nối DownloadManager dùng nó, để WebView nào cũng request trước:
  - `DownloadDeviceTest` ca tab thường;
  - 2 ca của `MainActivityLifecycleTest`;
  - test bị từ chối quyền.
- Đổi sang helper chung: `IncognitoProfileTest`, `IncognitoUnavailableTest`, `IncognitoPrivacyDeviceTest`, `DownloadDeviceTest`, `SitePermissionDeviceTest`, `ExternalLinksTest`. Thêm gate profiles: `LongPressAndThumbnailTest` (giữa test), `MainActivityLifecycleTest.finishing…`.
- Mã sản phẩm: `browser/downloads/DownloadRouter.kt` (+7/−3). Test JVM: `DownloadRouterTest.kt` (+1 test).

## Deviations

- **Test bị từ chối quyền lưu trữ.** Android giết app (và process test) khi thu hồi một quyền runtime. Thứ tự lớp là theo bảng chữ cái, nên `MainActivityLifecycleTest` luôn được cấp quyền trước. Vì vậy trạng thái "chưa cấp" chỉ tạo được bằng một `ContextWrapper`: wrapper này trả `PERMISSION_DENIED` cho `checkPermission(WRITE_EXTERNAL_STORAGE)`, và `MediaStoreDownloads` của test dựa vào đó. Phần còn lại là mã thật: `DownloadRouter`, WebView và listener, `ActivityHost` của rig (từ chối). Hộp thoại thật của hệ thống được kiểm bằng tay (checklist 1).
- **Assumption giữa test** (readerable, offscreen, thumbnail). Trên API 28 ba test này hiện là "skip", dù nửa đầu chạy được ở mọi WebView đã chạy và qua trước khi gặp `assume…`. Làm vậy để không mất phần kiểm đó trên WebView cũ mà không phải tách test.
- Test Desktop site được tách làm hai (thêm 1 test); hai test của `DownloadLimitsDeviceTest` đổi tên theo điều chúng còn khẳng định.
- Hai nhánh `if (WebViewSupport.readsDownloadLinksFirst)` trong `DownloadLimitsDeviceTest` và tên tải thất bại trong `BlobDownloadDeviceTest` là kỳ vọng theo năng lực chứ không phải bỏ qua. Mỗi nhánh khẳng định một hành vi cụ thể đã đo trên WebView 133 hoặc 66.
- `DeviceDownloads` vẫn dùng `SDK_INT` để chọn API MediaStore (Downloads chỉ có từ Android 10, giống `MediaStoreDownloads`). Nó không dùng để bỏ qua test.
- Sửa mã sản phẩm là một thu hẹp chính sách cookie đã chốt ở 4C (H6). Cách sửa giữ đúng ý của quyết định đó: chỉ gửi cookie khi địa chỉ là địa chỉ sau redirect của trang. Controller nên duyệt lại.

## Checklist tay cho controller (lệnh adb đã thử)

Harness tạm `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/p4d2b-manual/ManualProbe3.kt` không nằm trong repo; đã xoá khỏi repo sau phiên.
- Cách dùng: chép vào `app/src/androidTest/java/com/dantech/skimbrowser/`, chạy `ANDROID_SERIAL=emulator-5582 ./gradlew :app:installDebug :app:installDebugAndroidTest`, rồi chạy từng method bằng `adb -s emulator-5582 shell am instrument -w -e class com.dantech.skimbrowser.ManualProbe3#<method> -e holdMs <ms> com.dantech.skimbrowser.test/androidx.test.runner.AndroidJUnitRunner` (chạy nền). Chờ `adb -s emulator-5582 logcat -d -s SkimManual` có `ready`.
- Mỗi method đặt tab thật lên `MainActivity` (`ActivityResultHost` và receiver thật) và không giữ UiAutomation. Mọi mục dưới đây đã thử trên `skim_api28` trong lượt này. Bản API 36 đã thử ở 4D-1 và 4D-2a.

1. Hộp thoại quyền lưu trữ của hệ thống, thông báo DownloadManager, mở tệp bằng Files (đạt):
   - Cài mới (chưa cấp quyền), rồi chạy `#downloadNotification -e holdMs 90000`. Trang tự tải `/download.html?plain&auto=1`.
   - `adb -s emulator-5582 shell uiautomator dump /sdcard/skim-ui.xml && adb -s emulator-5582 shell "grep -o 'text=\"[^\"]*\"[^>]*bounds=\"[^\"]*\"' /sdcard/skim-ui.xml"` cho thấy `com.google.android.packageinstaller` "Allow Skim to access photos, media, and files on your device?" với DENY/ALLOW.
   - `adb -s emulator-5582 shell input tap 783 1289` (ALLOW). Sau đó `dumpsys package com.dantech.skimbrowser | grep EXTERNAL_STORAGE` cho READ và WRITE `granted=true`.
   - `adb -s emulator-5582 shell ls -la /sdcard/Download` thấy `Q3_ report.pdf` 193 B. Logcat `SkimManual` có `DownloadCompleted(fileName=Q3_ report.pdf, uri=content://downloads/all_downloads/36, mime=application/pdf)`.
   - `adb -s emulator-5582 shell dumpsys notification --noredact | sed -n '/Notification List:/,/mArchive/p' | grep -A40 providers.downloads | grep -E "android.title=|android.text="` cho `Q3_ report.pdf` / `Download complete.`. Chỉ grep mục "Notification List": mục `mArchive` liệt kê cả thông báo đã gỡ.
   - `adb -s emulator-5582 shell am start -a android.intent.action.VIEW_DOWNLOADS` mở DocumentsUI, liệt kê "Q3_ report.pdf 193 B". `input tap 308 941` gửi `START act=VIEW dat=content://com.android.providers.downloads.documents/document/36 typ=application/pdf`; AVD API 28 không có app xem PDF nên không mở được (trên API 36, Drive mở được, theo 4D-2a). `input keyevent 4` để quay lại.
   - Khi harness kết thúc: thư mục trống, 0 thông báo đang hiện.
2. Tệp Skim tự lưu (nhánh `MediaStore.Files`) mở được bằng app khác (đạt):
   - `#savedFile -e holdMs 60000` cho `DownloadCompleted(fileName=download.txt, uri=content://media/external/file/154, mime=text/plain)`.
   - `adb -s emulator-5582 shell content read --uri content://media/external/file/154` (uid shell) in ra "Skim saved this file.". `content query … --projection _display_name:mime_type:_size:_data` cho `download.txt, text/plain, 21, /storage/emulated/0/Download/download.txt`.
   - Files (`VIEW_DOWNLOADS`) liệt kê "download.txt 21 B"; chạm vào thì mở `com.android.htmlviewer/.HTMLViewerActivity`.
   - Dọn: `adb -s emulator-5582 shell rm /sdcard/Download/download.txt` và `adb -s emulator-5582 shell content delete --uri content://media/external/file/154`.
3. Trim với tab thật (đạt):
   - `#trimWithRealTabs -e holdMs 30000` (3 tab). Sau đó `adb -s emulator-5582 shell am send-trim-memory com.dantech.skimbrowser RUNNING_CRITICAL && adb -s emulator-5582 logcat -d -s SkimWebViewPool` cho `Evicted 2, 1 live, capacity 1` và `Memory warning 15: EVICT, capacity 1 (was 3), 1 live`.
   - `send-trim-memory … BACKGROUND` bị hệ thống từ chối: "Unable to set a background trim level on a foreground process".
4. Hộp thoại camera/mic của hệ thống (đạt):
   - `#runtimePermissionPrompt -e holdMs 45000`: harness trả lời yêu cầu của Skim bằng `allow(pending, remember = false)`.
   - Dump cho "Allow Skim to take pictures and record video?" (1 of 2). `input tap 783 1289`, rồi "Allow Skim to record audio?" (2 of 2), lại `input tap 783 1289`.
   - Logcat có `page answer: "granted audio,video"`; CAMERA và RECORD_AUDIO `granted=true`.
5. FLAG_SECURE và Recents: đã thử ở 4D-1 (harness `p4d-manual/ManualProbe.kt#secureWindow`), không chạy lại. Trên API 28 không có `setRecentsScreenshotEnabled` (API 33+), nên chỉ FLAG_SECURE áp dụng.
- Dọn sau phiên: `adb -s emulator-5582 shell rm -f /sdcard/skim-ui.xml`, gỡ APK, rồi tắt emulator.

## Concerns / Blockers

- **Sửa cookie trong `DownloadRouter`.** Tín hiệu để nhận ra tải chưa được WebView request là "không có type". Vì vậy một server thật không gửi `Content-Type` cũng mất cookie: đóng an toàn, nhưng tải cần đăng nhập từ server đó sẽ thất bại.
  - Trên WebView cũ, mọi link có thuộc tính `download` qua https nay không có cookie.
  - Tên tệp của chúng lấy theo địa chỉ, vì WebView không có Content-Disposition: `report.pdf` thay vì `Q3_ report.pdf`.
- **Reader trên WebView dưới Chromium 80.** Readability.js không parse được, nên `extractPage` luôn null. Nhưng `readerable` vẫn `true`, vì script readerable chỉ dùng `Readability-readerable.js`. Vì vậy nút Reader (phase 10) có thể hiện trong khi Reader không đọc được gì. Chưa sửa: đây là quyết định sản phẩm (xem câu hỏi 2).
- Hành vi "chỉ giao tải sau byte đầu" (`/huge.bin`) chưa đo trên WebView 66, vì test dùng nó cần incognito.
- Máy host vẫn thiếu RAM (swap khoảng 13/14 GB lúc bắt đầu), nhưng không có ANR hay timeout môi trường nào trong mọi lượt của lượt này. Mọi lượt chạy tuần tự, mỗi lúc chỉ một emulator và một lệnh Gradle.
- Task `connectedDebugAndroidTest` của AGP gỡ cả hai APK sau khi chạy. Lượt `am instrument` sau đó cần cài lại.

## Câu hỏi chưa giải quyết

1. Giữ cách sửa cookie theo "listener có type", hay chặt hơn: không bao giờ gửi cookie cho DownloadManager khi WebView giao link `download` chưa request? Hoặc thay bằng cách tự theo redirect trước khi giao cho DownloadManager.
2. Trên WebView không chạy được script trang, `readerable` có nên là `false` không? Ví dụ: script readerable parse luôn Readability.js (tốn thêm parse 90 KB mỗi lần tải), hoặc gate theo kết quả thăm dò một lần mỗi process. Hay đóng gói một bản Readability không dùng `??`/`matchAll` cho WebView cũ?
3. Kiểu assumption giữa test (nửa đầu chạy ở mọi WebView, rồi bỏ qua) có chấp nhận được không, hay nên tách thành hai test để bảng kết quả rõ hơn?
