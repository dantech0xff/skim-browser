# Báo cáo: lượt 4D-1 của phase 4 (test instrumented của engine trên WebView thật)

## Kết quả

- Status: DONE_WITH_CONCERNS. Đủ 8 mục phạm vi: 34 test instrumented (8 lớp) chạy trên `skim_api36` (WebView 133.0.6943.137), fixture ở `app/src/androidTest/assets/`, một móc test (getter `@VisibleForTesting internal` trong `MainActivity`). Không `git add`/commit, không sửa manifest, Gradle, `design/`, `plans/` (trừ report này).
- Lượt này tìm ra **3 lỗi thật của 4B** chỉ lộ trên WebView thật, đã sửa gốc (tối thiểu) và có test tái hiện: trang dùng `height: 100%` bị dàn trang cao 0 px trong tab đang hiện; phiên incognito kết thúc vẫn để lại đăng ký service worker; bước Find "next" bấm trong lúc WebView còn đếm kết quả bị mất.
- Code cuối: 5/5 lần chạy cả bộ instrumented xanh (mỗi lần 34 test: 33 qua, 1 skip đúng thiết kế là `IncognitoUnavailableTest`). Ba lệnh Gradle `connectedDebugAndroidTest` (tất cả, lọc package, lọc lớp) đều BUILD SUCCESSFUL. JVM 719 test xanh, lint giữ đúng 123 cảnh báo cũ, biên dịch lại không incremental 0 `w:`.
- Concerns chính: AGP 9.4.1 ghi skip theo assumption thành `<failure>` trong XML/HTML (task vẫn xanh); kiểm download qua DownloadManager chưa làm được vì 4C chưa có; máy host quá tải (load average 35–80) từng làm một lượt chạy treo, có bằng chứng ANR trong logcat.

## Kiểm chứng (chạy thật)

| Lệnh | Kết quả |
|---|---|
| `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest --console=plain` | BUILD SUCCESSFUL, chỉ `skim_api36(AVD)`; 34 test: 33 qua, 1 assumption skip (`IncognitoUnavailableTest`, lý do "this WebView has profiles (MULTI_PROFILE)"). AGP ghi skip này là `failures="1" skipped="0"` (xem Concerns); 0 lỗi khác |
| như trên + `-Pandroid.testInstrumentationRunnerArguments.package=com.dantech.skimbrowser.browser` | BUILD SUCCESSFUL; 27 test: 26 qua, 1 assumption skip (như trên), 0 lỗi khác |
| như trên + `-Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.DeviceCapabilitiesReportTest` | BUILD SUCCESSFUL; 1/1 qua; logcat `SkimGate` ở dưới |
| 5 lần cả bộ bằng `adb -s emulator-5580 shell am instrument -w -r …` (code cuối) | 5/5 `OK (34 tests)`, mỗi lần đúng 1 skip là `IncognitoUnavailableTest`; mỗi lớp 5/5 xanh |
| Vòng lặp riêng sau sửa lỗi | 2 test find: 15/15 lượt × 2 test xanh; trio từng chập chờn (callback order, find, pull): 10/10 xanh; pull sau khi đóng dấu thời gian sự kiện: 3/3 xanh |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; 719 test (76 lớp), 0 lỗi, 0 skip |
| `./gradlew :app:assembleDebug` / `:app:lintDebug` | BUILD SUCCESSFUL / 0 lỗi, 123 cảnh báo cũ (UnusedResources 118, NewerVersionAvailable 3, AGP 1, InsecureBaseConfiguration 1); 0 cảnh báo trong file mới/sửa |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false` | BUILD SUCCESSFUL, 34/34 task chạy, 0 dòng `w:` |
| Quét vệ sinh (31 file mới/sửa, script `p4d-hygiene.py`) | 0 ID plan/phase/finding/"D8"/"#N"/"Red Team"; 0 ký tự bidi/zero-width thô (fixture dùng `&#x200B;`…, Kotlin dùng `\uXXXX`); 0 secret; không `Log` nào in URL người dùng |

Logcat `SkimGate` trên `skim_api36`: `MULTI_PROFILE=true`, `HTTP_CACHE_MANAGER=false`, `SAVE_STATE=false`, `USER_AGENT_METADATA=true`, `GET_WEB_VIEW_RENDERER=true`, `WEB_VIEW_RENDERER_TERMINATE=true`, `WEB_MESSAGE_LISTENER=true`, `DOCUMENT_START_SCRIPT=true`, `DELETE_BROWSING_DATA=true`, `WEBVIEW_PACKAGE=com.google.android.webview`, `WEBVIEW_VERSION=133.0.6943.137`, `SDK_INT=36`, `READABILITY_TYPEOF=function`, `READABILITY_READERABLE=true`.

Lịch sử chạy lặp được ghi đủ để không che độ chập chờn: bộ 5 lần đầu tiên (trước khi sửa) xanh 1/5 (lỗi ở test: đua bộ thu sự kiện ×3, bắt tay chạm ×1; và lỗi find thật ×1); bộ thứ hai xanh 3/5 (vận tốc thả bằng 0 khi máy chậm ×1; một lượt máy treo: ANR "sandboxed_process… failed to complete startup", activity pause/stop timeout, "Displayed +58s"); bộ thứ ba trên code cuối xanh 5/5.

## Lỗi tìm thấy trên thiết bị

| Triệu chứng | Nguyên nhân gốc | Sửa | Test tái hiện |
|---|---|---|---|
| Trang dùng `html, body { height: 100% }` (bản đồ, web app toàn màn hình) dàn trang cao 0 px trong tab đang hiện: `#map` và `body` cao 0, chạm rơi vào `<html>`, listener kéo của trang không bao giờ chạy, và pull to refresh cướp cử chỉ | `AndroidView` gắn WebView với LayoutParams mặc định `WRAP_CONTENT`; Android WebView bật zero-layout-height khi chiều cao layout là wrap_content (`AwLayoutSizer.updateLayoutSettings` → `setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())`, Chromium 133). Host offscreen dùng `MATCH_PARENT` nên trích nội dung không bị | `WebViewPool.showInHost` đặt `ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)` cho WebView trước khi trao cho host | Thiết bị: `PullToRefreshNestedScrollTest.aPageThatKeepsTheDragForItselfIsNeverPulledThoughTheSamePageCanBe` (đỏ trước sửa). JVM: `WebViewHostTest.theTabsWebViewFillsItsHostSoPagesSizedToTheScreenKeepTheirHeight` (đỏ khi gỡ dòng sửa, xanh khi có) |
| Sau `end()` của phiên incognito, đọc lại bằng WebView của profile cũ: cookie, local storage, IndexedDB đã sạch nhưng `workers: 1` (đăng ký service worker còn trên đĩa) | `WebStorage.deleteAllData()` chỉ xoá file systems, IndexedDB, local storage, WebSQL (Chromium ghi chú "Legacy"); không có service worker, Cache Storage | `WebViewProfiles.clearWebStorage()` dùng `WebStorageCompat.deleteBrowsingData` khi có `DELETE_BROWSING_DATA`, ngược lại giữ `deleteAllData()`; KDoc của `BrowserProfile.clearWebStorage` cập nhật | Thiết bị: `IncognitoProfileTest.endingTheSessionLeavesNoCookiesStorageOrCachedFilesInItsProfile` (đỏ trước sửa). JVM: không tái hiện được (Robolectric không có WebView provider) |
| Find in page: bấm Next ngay khi kết quả đầu tiên về thì không chuyển sang match thứ hai (đứng ở `0/7`); chập chờn 2/30 lượt ở test gốc | WebView 133 trả lời một `findNext` gửi trong lúc `findAllAsync` còn đếm (`isDoneCounting = false`) bằng cách chọn lại match đầu (thăm dò: 20/20 kẹt khi gửi lúc đang đếm, 0/20 khi gửi sau `done = true`). Pool bỏ qua `isDoneCounting` và chuyển `findNext` ngay | Lớp mới `FindSteps` (internal): bước Next/Previous hỏi trong lúc đếm phải chờ, gửi theo thứ tự khi listener báo `isDoneCounting`; tìm mới hoặc `clearFind` bỏ bước chờ. `WebViewPool.find/findNext/clearFind` và find listener dùng nó | Thiết bị: `SkimWebViewEngineTest.aStepAskedForWhileTheMatchesAreStillCountedStillMovesToTheNextMatch` (3/3 đỏ trước sửa, 15/15 xanh sau). JVM: `FindStepsTest` (4 test) |

## Giả định 4A/4B đã xác nhận / bị bác trên WebView thật

Xác nhận:
- Thứ tự callback (bản ghi thô từ `WebViewClient`/`WebChromeClient`): tài liệu mới `onPageStarted` → `doUpdateVisitedHistory` → `onProgressChanged(100)` ×2 → `onPageFinished`; `pushState` chỉ có `doUpdateVisitedHistory` + `onProgressChanged(100)` ×2 (không started/finished); tải lỗi `onPageStarted` → `onReceivedError` → `onPageFinished` → `doUpdateVisitedHistory` → `onProgressChanged(100)` ×2. Ở tầng `TabManager`: cùng nonce từ PageStarted tới PageFinished, `isSameDocument` đúng cho pushState và fragment, lỗi được giữ, URL lỗi không vào history. Không cần sửa `TabManager`/client.
- `onOverScrolled(clampedY)` vẫn tới khi `overScrollMode = NEVER` (kéo ở đỉnh trang refresh được); dấu `dy` và velocity đúng chiều qua interop của Compose; trang `preventDefault` trên `touchmove` không bị kéo, cùng trang không `preventDefault` thì bị kéo.
- Extractor trên Chromium 133 cho đúng danh sách block như Chrome headless cho `article.html`, `divs.html` (div chữ thành `p`, `<h2><div>` vẫn `h2`, `<li><div>` vẫn `li`, post ẩn và `div.comment` bị loại), `mixed.html`, `plain.html`; id giữ nguyên qua hai lần trích; mọi kiểu ẩn bị loại; zero-width/bidi bị xoá.
- Highlight: `true` đúng URL+nonce; `false` sau reload, sau khi sang trang khác, và khi `pushState` commit sau lúc `TabManager` đã kiểm (script tự kiểm `location`).
- `readerable` của `TabManager` chạy trên thiết bị (article `true`, settings `false`).
- `restoreState` sau evict (trim RUNNING_CRITICAL) giữ back history, vị trí cuộn trang hiện tại (2400 → 2400) và trang trước (Back: 1600 → 1596,57, giống hệt khi không qua save/restore). `SAVE_STATE=false` nên đường `webView.saveState` cũ được dùng và chạy đúng.
- Desktop site: UA, viewport 980 px (trang không có meta viewport), `navigator.userAgentData` và header `Sec-CH-UA-Mobile`/`Sec-CH-UA-Platform` đổi đúng; tắt trả đúng UA và `UserAgentMetadata` mặc định.
- `terminate()` renderer: WebView bị huỷ, `WebViewHost` dựng WebView mới theo `generation` hai lần, lần ba → `LoadError.Crashed("RENDERER_KILLED")` và host trống, Try again dựng lại và tải trang.
- WebView offscreen (host ẩn, `translationX` ngoài màn hình) tải trang và chạy JS; `extractPage` cho tab nền trả đúng block.
- `setProfile` trước mọi thứ cho WebView incognito, WebView popup incognito và WebView tạm của `withTemporaryWebView` (`getProfile` trả đúng tên, WebView tạm bị huỷ sau khối); cookie tách hai chiều (header `Cookie` ở server và hai `CookieManager`); tab incognito mới lúc `Ending` → profile khác, Undo cũ vô hiệu, cookie phiên cũ bị xoá; `end()` xoá entry HTTP cache trong `cache/WebView/Profile N/HTTP Cache/Cache_Data` qua `clearCache(true)` của WebView tạm.
- Popup: `window.open` sau chạm → tab foreground có `openerTabId`, `window.opener` còn; `window.close()` đóng hẳn (không Undo) và về opener (đúng WebView cũ); BACKGROUND/UI_HIDDEN giữ cả hai (generation không đổi, state đã lưu); không có cử chỉ → `window.open` trả `null`, không tab.
- Nhấn giữ qua `setOnLongClickListener` + `hitTestResult` + `requestFocusNodeHref`: link (`url`, text từ khoá `title`), ảnh, ảnh trong link (`linkUrl` từ khoá `url`); không mở link.
- Thumbnail vẽ phần mềm từ WebView thật: đúng kích thước `thumbnailSize`, có chữ (>1% điểm tối); tab thường có `thumbs/<id>.webp`, incognito chỉ trong bộ nhớ.
- `MainActivity`: attach khi sống, detach khi huỷ; `pauseTimers` ở `onStop` (không tick trong 1 s), `resumeTimers` ở `onStart`; `evaluateJavascript` vẫn chạy khi timers dừng; xoá `cacheDir/shared/incognito` khi khởi động; finishing → finalize mọi close chờ Undo và kết thúc phiên incognito (dòng Room bị xoá); recreate → close vẫn Undo được.

Bị bác:
- `deleteAllData()` không xoá service worker (plan ghi `[UNVERIFIED]`) → đã sửa (bảng trên).
- WebView trong `AndroidView` không tự lấp host như giả định → đã sửa.
- `findNext` không an toàn khi gửi ngay sau `findAllAsync` → đã sửa.

Quan sát (không phải lỗi của mã, ghi lại cho các phase sau):
- Chromium đưa vị trí cuộn vào navigation state khoảng 1 s sau khi cuộn; state lưu ngay sau khi cuộn (stop/evict tức thì) sẽ mất vị trí mới. Test chờ state chứa vị trí mới rồi mới evict.
- `pushState` gây `onProgressChanged(100)` ×2 nên pool chụp thumbnail và lưu state sau mỗi điều hướng SPA (vô hại).
- Chromium chuyển chạm tới trang chỉ sau khi renderer báo vùng có listener (ngay sau khi tải); cử chỉ quá sớm không tới listener của trang (cả Chrome cũng vậy).
- `pauseTimers` là toàn cục nhưng cờ `timersPaused` theo từng pool: không gây lỗi trên thiết bị vì renderer kết thúc khi WebView cuối bị huỷ (test `javaScriptTimersRunInTheNextActivityAfterTheLastOneStoppedAndWentAway` giữ hành vi này).
- Profile incognito tạo mà chưa có WebView nào dùng, rồi process chết trong ~0,1 s: WebView không ghi profile vào `app_webview/pref_store`, thư mục `Profile N` thành mồ côi, `purgeStale` không thấy, và profile kế tiếp dùng lại đúng đường dẫn đó (quan sát `Profile 147`). Khi WebView đã dùng profile, danh sách được ghi trong ~0,5 s (3/3 lần) và không có dữ liệu nào qua phiên mới (2/2 lần).

## Test đã viết

- `DeviceCapabilitiesReportTest`: feature, WebView, SDK, Readability trên trang thật; `SkimGate key=value` + `Instrumentation.sendStatus(2, …)`.
- `SkimWebViewEngineTest` (13): extractor (article/divs/mixed/plain), readerable, highlight (2, gồm điều hướng sau lúc kiểm), find (2), save/restore qua evict, desktop UA + client hints, renderer gone + ngân sách + Try again, tab nền offscreen, thứ tự callback thô + tầng TabManager + history.
- `IncognitoProfileTest` (5), `IncognitoUnavailableTest` (1, chỉ chạy khi không có `MULTI_PROFILE`), `PopupAndTrimTest` (3), `PullToRefreshNestedScrollTest` (3, `PullToRefreshBox` thật, chạm thật qua input pipeline), `LongPressAndThumbnailTest` (2), `MainActivityLifecycleTest` (6).
- Hạ tầng: `BrowserRig` (rule JUnit: container riêng trên thư mục tạm qua `AppStorage.temp`, `ComponentActivity`, pool và `BrowserHost` dựng như `MainActivity`; dọn tab, phiên incognito, cookie/storage/cache của profile mặc định, thư mục tạm), `TestPages` (MockWebServer 127.0.0.1 phục vụ asset + `/set-cookie`, `/cookie-check`, `/image.png`, tài nguyên cache được, `/sw.js`), `DeviceTesting` (`onMain`, `waitUntil`/`holdsFor` có timeout, `awaitValue`, `awaitNextFrame`, `Finger` chạm/nhấn giữ/kéo qua `sendPointerSync`), `Fixtures`.

## Deviations

- Test engine dùng `BrowserRig` (container cô lập + `ComponentActivity` + pool dựng như `MainActivity`), không dùng `MainActivity`, để mỗi test có DB/thư mục riêng; `MainActivity` chỉ dùng ở `MainActivityLifecycleTest` (container của process, dọn tab/history/cookie sau mỗi test). Rig dùng `TestActivityHost` vì `ActivityResultHost` không đăng ký được sau STARTED. Khi 4C thêm tham số vào `BrowserHost`, `BrowserRig.before()` phải cập nhật theo.
- Báo cáo năng lực thêm `GET_WEB_VIEW_RENDERER` (cần cho test terminate) và `DELETE_BROWSING_DATA` (cần cho sửa lỗi incognito).
- Fixture lưu trữ kiểm thêm service worker (plan ghi kiểm việc này trong `IncognitoProfileTest`).
- `purgeStale` "sau khung đầu": test tự động kiểm hiệu ứng (xoá file chia sẻ của phiên trước); thứ tự sau khung đầu là cấu trúc (`decorView.post`). Xoá profile mồ côi của process trước không tự động hoá được trong một process instrumentation (profile đã load không xoá được) → kiểm tay hai process (checklist).
- Sửa ba file 4B và một file 4A: `WebViewPool.kt` (`showInHost` + find), `WebViewProfiles.kt`, `BrowserProfiles.kt` (chỉ KDoc), thêm `FindSteps.kt`; `MainActivity.kt` thêm getter. Test JVM: thêm `FindStepsTest.kt`, thêm 1 test vào `WebViewHostTest.kt`.
- Không chạy cổng `skim_api28` (không trong phạm vi lượt này: chỉ một emulator `skim_api36`).

## Checklist tay cho controller (lệnh adb đã thử)

App chưa có giao diện duyệt web (phase 5), nên các bước dưới đặt tab lên màn hình bằng harness tạm `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/p4d-manual/ManualProbe.kt` (không nằm trong repo). Cách dùng: chép vào `app/src/androidTest/java/com/dantech/skimbrowser/`, `ANDROID_SERIAL=emulator-5580 ./gradlew :app:installDebug :app:installDebugAndroidTest`, chạy từng mục, xong thì xoá file. Mỗi mục giữ trang trên `MainActivity` thật (ActivityResultHost, FullscreenController, WindowSecureController thật) trong `holdMs`.

1. File chooser (đã thử, đạt):
   - `adb -s emulator-5580 shell am instrument -w -e class com.dantech.skimbrowser.ManualProbe#fileChooser -e holdMs 90000 com.dantech.skimbrowser.test/androidx.test.runner.AndroidJUnitRunner &`; chờ `adb -s emulator-5580 logcat -d -s SkimManual` có `ready`.
   - `adb -s emulator-5580 shell input tap 540 1200` rồi `adb -s emulator-5580 shell uiautomator dump /sdcard/skim-ui.xml && adb -s emulator-5580 shell "grep -o 'package=\"[^\"]*\"' /sdcard/skim-ui.xml | sort -u"` → `com.google.android.documentsui`.
   - `adb -s emulator-5580 shell input keyevent KEYCODE_BACK` → dump lại ra `com.dantech.skimbrowser`; `adb -s emulator-5580 logcat -d -s chromium | grep skim-file` → `"skim-file cancel"` (callback được trả `null`).
   - Chạm lại → picker mở lại (không treo) → Back → thêm một dòng `skim-file cancel`. Dọn: `adb -s emulator-5580 shell rm /sdcard/skim-ui.xml`.
2. Video toàn màn hình (đã thử, đạt): `…ManualProbe#fullscreen …`; `input tap 540 1200` → logcat `chromium` có `"skim-fullscreen on"`; `adb -s emulator-5580 shell dumpsys window | grep -E "InsetsSource id=.* type=(statusBars|navigationBars)"` → cả hai `visible=false`; `adb -s emulator-5580 exec-out screencap -p > fullscreen.png` thấy gợi ý "Viewing full screen" của hệ thống, không thanh hệ thống. Bấm "Got it" (`input tap 925 635`), `input keyevent KEYCODE_BACK` → `"skim-fullscreen off"`, hai thanh `visible=true`, trang không bị Back điều hướng.
3. FLAG_SECURE + Recents (đã thử, đạt): `…ManualProbe#secureWindow …`; `adb -s emulator-5580 shell dumpsys window windows | grep -A12 "Window{.* com.dantech.skimbrowser/com.dantech.skimbrowser.MainActivity}:" | grep -o "fl=.*SECURE[A-Z_ ]*"` → `fl=… SECURE …`; `screencap` → ảnh đen hoàn toàn; `adb -s emulator-5580 shell input keyevent KEYCODE_APP_SWITCH` rồi `screencap` → thẻ Skim trong Recents đen; `input keyevent KEYCODE_BACK`. Sau khi probe nhả, `grep -c SECURE` → 0.
4. Profile incognito mồ côi qua hai process (đã thử): `…ManualProbe#leaveAnIncognitoProfile -e waitMs 15000 …` rồi `…ManualProbe#staleProfilesAreDeleted …`; đọc `adb -s emulator-5580 shell run-as com.dantech.skimbrowser cat app_webview/pref_store | grep -o '"profile":{[^}]*}[^}]*}'` và `adb -s emulator-5580 logcat -d -s SkimManual`. Với `waitMs 15000` profile có trong danh sách và bị `purgeStale` xoá ở process sau; với `waitMs 0` (không WebView) thư mục mồ côi xuất hiện (xem Concerns). Thí nghiệm rò dữ liệu: `…ManualProbe#incognitoWriteThenDie -e settleMs 15000 …` rồi `…ManualProbe#incognitoReadInANewSession -e settleMs 15000 …` → tiêu đề `skim-leak read null` (không rò).
5. Trim qua hệ thống: `adb -s emulator-5580 shell am send-trim-memory com.dantech.skimbrowser RUNNING_CRITICAL && adb -s emulator-5580 logcat -d -s SkimWebViewPool` (lệnh của phase file; BACKGROUND không gửi được cho process foreground, nên test gọi `pool.onTrimMemory` trực tiếp). Mục này chưa chạy trong lượt (cần tab có WebView trên `MainActivity`).
6. Download qua DownloadManager: chưa thử được, chức năng thuộc 4C (chưa có `DownloadRouter`); để 4D-2.

## Concerns / Blockers

- AGP 9.4.1 ghi assumption skip thành `<failure>org.junit.AssumptionViolatedException…` (`failures="1" skipped="0"`, HTML "97% successful") dù task BUILD SUCCESSFUL. Lệnh `grep -h -o 'skipped="[0-9]*"'` của phase file sẽ luôn ra 0; đếm skip bằng `grep -c "AssumptionViolatedException:"` trên XML. Trên `skim_api28` (không `MULTI_PROFILE`) cả 5 test `IncognitoProfileTest` sẽ hiện như vậy.
- Máy host quá tải trong lúc chạy (Screen Studio, một VM khác, load average 35–80): một lượt cả bộ treo với ANR của renderer và timeout activity. Kết quả trên là các lượt sau đó; nếu cổng chạy lại khi host bận có thể gặp timeout môi trường.
- Profile incognito mồ côi khi process chết ngay sau `getOrCreateProfile` (trước khi có WebView): `purgeStale` không xoá được và profile sau dùng lại thư mục. Cửa sổ rất hẹp (thường chỉ một khung hình giữa `newTab(incognito)` và WebView đầu tiên); tab incognito mở nền (`background = true`) kéo dài cửa sổ này.
- 4C sẽ sửa `WebViewPool.configure()`: thay đổi của tôi ở đó chỉ là find listener (thêm tham số `isDoneCounting` và dòng chuyển `FindSteps`).
- Emulator chỉ còn ~370 MB RAM trống và swap 684 MB sau nhiều lượt; đã cài lại `installDebug` như trạng thái lúc nhận việc.

## Câu hỏi chưa giải quyết

1. Có cần chặn hẳn đường profile incognito mồ côi (ví dụ chỉ tạo profile khi WebView đầu tiên của phiên được tạo, để `setProfile` tạo nó) không, hay chấp nhận cửa sổ một khung hình?
2. Có muốn chuyển `IncognitoUnavailableTest`/assumption sang cơ chế khác để báo cáo AGP không đếm thành failure, hay giữ assumption (đúng nghĩa) và đếm skip bằng `AssumptionViolatedException`?
3. WebView không có `DELETE_BROWSING_DATA` nhưng có `MULTI_PROFILE` vẫn để lại service worker tới lần khởi động sau (`purgeStale`). Chấp nhận như giới hạn đã biết?
4. `SkimWebView` giành cử chỉ kéo ở đỉnh trang (gửi `ACTION_CANCEL` cho trang) cả khi cha không phải pull to refresh. Hiện mọi host đều là `PullToRefreshBox` nên không ảnh hưởng; phase 5 có host nào khác không?
