# Phase 5 (khung app, điều hướng, onboarding): lượt kiểm trên thiết bị

Ngày 2026-10-22 (Asia/Singapore), nhánh `feat/skim-design-implementation`. Không `git add`, không commit, không push, không đổi nhánh. Không sửa mã sản phẩm. Điện thoại thật `21141FDF60011X` có lúc gắn USB nhưng không được dùng: mọi lệnh ghim `emulator-5580` hoặc `emulator-5582`.

## Kết quả

- 26 test thiết bị mới cho khung app, đều dùng `MainActivity` thật với container của process qua một rule chung (`SkimAppRule`): `SecureFlagTest` (9), `BackStackRestoreTest` (3), `ExternalLinkTest` (7), `AppStartTest` (5), `SetupImportTest` (2). Cả bộ nay có 110 test.
- `skim_api36` (Android 16, WebView 133.0.6943.137): lượt Gradle cuối trên mã cuối xanh, 110 test, 107 qua, 3 skip đúng thiết kế (như phase 4); `SecureFlagTest` chạy thật cả 9 test, không skip. Lặp cả bộ bằng `am instrument`: 3/3 xanh sau hai sửa đầu, thêm 1/1 xanh sau sửa `DeviceDownloads`; 5 lớp mới 5/5 lượt xanh; `ExternalLinkTest` 8/8 sau sửa cuối.
- `skim_api28` (Android 9, WebView 66.0.3359.158): lượt Gradle cuối xanh, 110 test, 76 qua, 34 tự bỏ qua có lý do (10 trong số đó là test mới cần incognito).
- 11 đột biến có chủ ý vào mã sản phẩm (chạy trong một bản sao của working tree ở scratchpad, không đụng repo) đều bị đúng test thiết bị liên quan bắt.
- Không tìm thấy lỗi nào của mã sản phẩm phase 5. Có 5 lỗi hoặc chập chờn của test, mỗi cái đã xem bằng log rồi sửa trong test (riêng cú kéo bị bỏ chỉ rõ hiện tượng, chưa rõ tầng): 2 của test mới (tap trong lúc snackbar còn đang hiện ra; đọc state giữa hai lần cập nhật của `TabManager.newTab`), 3 của test phase 4 (Chromium biến cú nhấn giữ link thành kéo thả; DownloadManager trên Android 9 từ chối cleartext tùy provider nào khởi động `android.process.media`; WebView 66 thỉnh thoảng bỏ qua cả một cú kéo).
- Có vài phát hiện về sản phẩm cần controller quyết (xem Concerns): snackbar của 03 che nút Start browsing; tab mới mở ngay khi Back đóng tab cuối của link ngoài; tải http qua DownloadManager trên Android 9; nhấn giữ link có thể thành kéo thả trên WebView 133.
- Trong lúc tôi làm (08:59–09:01), người khác đã sửa `SecureFlagEffect.kt`, `TabManager.kt`, `BookmarkImporter.kt`, `NetscapeBookmarkParser.kt` và các test JVM của chúng. Mọi lượt từ 09:13 trở đi chạy trên mã đã có các thay đổi đó (xem Deviations).

## Kiểm chứng (chạy thật)

`skim_api36`, `emulator-5580`:

| Lệnh | Kết quả |
|---|---|
| `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest --console=plain`, lượt 1 (mã đầu lượt) | BUILD SUCCESSFUL 5m55s; 110 test, 0 lỗi, 3 skip đúng thiết kế (`IncognitoUnavailableTest`, test từ chối quyền lưu trữ, test WebView cũ) |
| `p4d2b-repeat.sh emulator-5580 3` (mã đầu lượt) | 2/3 `OK (110 tests)`; lượt 3 đỏ `LongPressAndThumbnailTest` (Chromium kéo thả link, đã sửa) |
| `LongPressAndThumbnailTest` x10 sau khi sửa fixture | 10/10 |
| 5 lớp mới x5, lần 1 | 4/5; 1 lượt đỏ `SetupImportTest` (tap View lúc snackbar đang hiện ra, đã sửa) |
| `SetupImportTest` x12 sau khi sửa | 12/12 |
| 5 lớp mới x5, lần 2 | 5/5 `OK (26 tests)`, 0 skip |
| `p4d2b-repeat.sh emulator-5580 3`, lần 2 | 3/3 `OK (110 tests)` (352, 348, 353 s); 0 FATAL, 0 ANR |
| 11 đột biến (`p5d-mutate.py`) | 11/11 bị bắt (bảng Đột biến) |
| Gradle connected lượt 2 (sau sửa tap) | BUILD SUCCESSFUL 6m45s; 110 test, 0 lỗi, 3 skip đúng thiết kế |
| Gradle connected lượt 3 (sau sửa `DeviceDownloads`, `DownloadLimitsDeviceTest`) | BUILD FAILED: 1 lỗi `ExternalLinkTest.aLinkNeverOpensInIncognito…` (đọc state giữa hai lần cập nhật, đã sửa); 109 còn lại xanh |
| `p4d2b-repeat.sh emulator-5580 1` sau lượt 3 | `OK (110 tests)` |
| `ExternalLinkTest` x8 sau khi sửa | 8/8 `OK (7 tests)` |
| Gradle connected lượt 4 (mã cuối, cây nguồn md5 `9ef7cc83…`) | BUILD SUCCESSFUL 6m23s; 110 test, 0 lỗi, 3 skip đúng thiết kế; 0 FATAL, 0 ANR; rule `DeviceDownloads` không kích hoạt (0 `Force stopping com.android.providers.downloads`) |

`skim_api28`, `emulator-5582`:

| Lệnh | Kết quả |
|---|---|
| Làm nóng `DeviceCapabilitiesReportTest` | OK; SkimGate bên dưới |
| 5 lớp mới, 1 lượt | `OK (26 tests)`: 16 qua, 10 skip có lý do |
| Gradle connected lượt 1 | BUILD FAILED: 4 test DownloadManager phase 4 đỏ, DownloadManager báo `HTTP_DATA_ERROR: Cleartext HTTP traffic to 127.0.0.1 not permitted` (cơ chế ở bảng lỗi; đã sửa `DeviceDownloads`) |
| Gradle connected lượt 2 | 4 test tải trong process đỏ vì một `download.txt` sót lại khi tôi dừng giữa chừng lượt lặp trước; đã dọn tệp và hàng MediaStore |
| Gradle connected lượt 3 | 109/110 xanh; 1 lỗi `DownloadLimitsDeviceTest.aScrollLetsThePageStartNoDownload` (WebView 66 bỏ qua cú kéo, đã sửa) |
| `p4d2b-repeat.sh emulator-5582 3` | 3/3 `OK (110 tests)` (208, 208, 211 s): mỗi lượt 76 qua, 34 skip; 0 FATAL, 0 ANR, 0 lỗi cleartext |
| 5 lớp mới x5 | 5/5 `OK (26 tests)`: 16 qua, 10 skip |
| Lặp chẩn đoán lớp `DownloadLimitsDeviceTest` | 2/15 đỏ với chẩn đoán nhẹ; 0/41 với chẩn đoán nặng; 0/25 với chẩn đoán sau-sự-việc; sau khi sửa 10/10 |
| Gradle connected lượt 4 (mã cuối của `browser/`) | BUILD SUCCESSFUL 4m23s; 110 test, 0 lỗi, 34 skip; 0 FATAL, 0 ANR, 0 lỗi cleartext |

Khác:

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL; 34/34 task chạy; 0 `w:`, 0 `e:` |
| `python3 -I p4a-hygiene.py` (48 file chưa track dưới `app/src`) và quét diff 3 file test đã track | 0 ID plan/phase/finding, 0 nhắc AI, 0 TODO, 0 log debug, 0 secret, 0 ký tự bidi/zero-width, 0 ký tự ngoài ASCII; `grep SkimDiag` trong repo = 0 |
| JVM, assemble, lint, Roborazzi | Không chạy: tôi không sửa mã sản phẩm (đúng quy định của prompt). |
| Trạng thái emulator khi bàn giao | Cả hai đã tắt (`emu kill`), `ps` không còn `qemu-system`, không còn tiến trình `am instrument` hay script nào. `skim_api36`: không còn APK Skim, `/sdcard/Download` trống, trình duyệt mặc định Chrome, `night no`, xoay tự động, không dịch vụ trợ năng. `skim_api28`: không còn APK Skim, `/sdcard/Download` trống, không hàng MediaStore nào, component setup wizard đã bật lại, adbd đã `unroot` |

## SkimGate

- `skim_api36`: `MULTI_PROFILE=true`, `HTTP_CACHE_MANAGER=false`, `SAVE_STATE=false`; `USER_AGENT_METADATA`, `GET_WEB_VIEW_RENDERER`, `WEB_VIEW_RENDERER_TERMINATE`, `WEB_MESSAGE_LISTENER`, `DOCUMENT_START_SCRIPT`, `DELETE_BROWSING_DATA` đều `true`; `WEBVIEW_VERSION=133.0.6943.137`, `SDK_INT=36`, `SQLITE_VERSION=3.44.3`, `READABILITY_TYPEOF=function`, `READABILITY_READERABLE=true`.
- `skim_api28`: mọi feature `false`; `WEBVIEW_PACKAGE=com.google.android.webview`, `WEBVIEW_VERSION=66.0.3359.158`, `SDK_INT=28`, `SQLITE_VERSION=3.22.0`, `READABILITY_TYPEOF=null`, `READABILITY_PARSE_ERROR=SyntaxError: Unexpected token ?`, `READABILITY_READERABLE=true`.

## Bảng test API 28 (3 lượt lặp `am instrument` sau sửa `DeviceDownloads` và lượt Gradle cuối cho cùng con số)

Lý do bỏ qua, nguyên văn:
- (P) `This WebView (com.google.android.webview 66.0.3359.158) lacks MULTI_PROFILE, which every incognito tab needs`
- (S) `Skim's page scripts do not run on this WebView (com.google.android.webview 66.0.3359.158): SyntaxError: Unexpected token ?`
- (R) `... lacks GET_WEB_VIEW_RENDERER, which the test needs to reach the page's renderer`
- (H) `... lacks USER_AGENT_METADATA, which Skim needs to set the client hints sites see`

| Lớp | Chạy và qua | Bỏ qua (lý do) |
|---|---|---|
| `ui.shell.SecureFlagTest` (mới) | 0 | cả 9 (P) |
| `ui.shell.BackStackRestoreTest` (mới) | 3 | 0 |
| `ui.shell.ExternalLinkTest` (mới) | 6 | `aLinkNeverOpensInIncognitoEvenOverIncognitoScreens` (P) |
| `ui.shell.AppStartTest` (mới) | 5 (Back ở Welcome: Activity kết thúc) | 0 |
| `ui.onboarding.SetupImportTest` (mới) | 2 | 0 |
| `DeviceCapabilitiesReportTest` | 1 | 0 |
| `MainActivityLifecycleTest` | 8 | `finishingMakesEveryClose…AndEndsTheIncognitoSession` (P) |
| `BlobDownloadDeviceTest` | 7 | 0 |
| `DownloadDeviceTest` | 2 | 3 ca incognito (P) |
| `DownloadLimitsDeviceTest` | 5 | 0 |
| `ExternalLinksTest` | 4 | `anIncognitoTabHandsOnlyCalls…` (P) |
| `IncognitoPrivacyDeviceTest` | 0 | cả 3 (P) |
| `IncognitoProfileTest` | 0 | cả 5 (P) |
| `IncognitoUnavailableTest` | 1 (chạy thật) | 0 |
| `JsDialogDeviceTest` | 3 | 0 |
| `LongPressAndThumbnailTest` | 1 | `aShownPageGetsAThumbnail…` (P), sau nửa đầu đã qua |
| `PopupAndTrimTest` | 3 | 0 |
| `PullToRefreshNestedScrollTest` | 3 | 0 |
| `SharedDownloadsFolderDeviceTest` | 3 | 0 |
| `SitePermissionDeviceTest` | 6 | ca incognito (P) |
| `SkimWebViewEngineTest` | 7 | 9: script trang x7 (S), renderer (R), client hints (H) |
| `TabPoolDeviceTest` | 2 | 0 |
| `data.DeviceSqliteDataTest` | 4 | 0 |
| Tổng | 76 | 34 |

## Lỗi tìm thấy trên thiết bị

Không có lỗi nào của mã sản phẩm. Lỗi và chập chờn của test, mỗi cái đã được xem bằng log trước khi sửa (chẩn đoán tạm đã hoàn nguyên, `grep SkimDiag` trong repo = 0). Riêng cú kéo bị WebView 66 bỏ qua chỉ chứng minh được hiện tượng, chưa xác định được tầng nào bỏ chuỗi chạm:

| Triệu chứng | Nguyên nhân gốc | Sửa | Kiểm lại |
|---|---|---|---|
| `LongPressAndThumbnailTest` (phase 4) đỏ 1/3 lượt cả bộ API 36: `IllegalArgumentException: Targeted input event injection … was not directed at a window owned by uid` khi nhấc ngón | WindowManager ghi `perform drag … ClipData { text/plain {T(48)} } touch(540.0,360.0)`: Chromium đã biến cú nhấn giữ link thành kéo thả (48 ký tự = chữ của link + xuống dòng + URL). Cửa sổ `drag` của hệ thống giữ cú chạm, nên ngón nhấc lên bị từ chối và không có long-click nào. 20 lượt thăm dò riêng không tái hiện, kể cả khi kéo ngón sau khi nhấn giữ | `assets/longpress.html`: link và ảnh `draggable="false"`, kèm comment lý do. Test vẫn đo đúng điều nó đo (đích của cú nhấn giữ) | 10/10 lớp đó, rồi 3/3 + Gradle cả bộ API 36 |
| `SetupImportTest` (mới) đỏ 1/5: tap View không mở thư mục | Log chẩn đoán: lúc tap, bounds của View là 842–948 px (snackbar đang phóng to vào chỗ), một frame sau là 893–1016 px, nên cú tap trượt nút | `AppScreen.tap` chờ node đứng yên 3 frame liền rồi mới tap | 12/12, rồi 5/5 các lớp mới |
| 4 test DownloadManager (phase 4) đỏ trên API 28: chờ DownloadManager quá 30 s | DownloadManager báo `HTTP_DATA_ERROR: Cleartext HTTP traffic to 127.0.0.1 not permitted` dù cấu hình mạng của Skim cho phép cleartext. Đó là lỗi của tầng HTTP trong process `android.process.media` (Android 9 chạy chung MediaProvider và DownloadProvider), không phải từ chối của DownloadProvider. Process đó lấy chính sách mạng của provider khởi động nó: log `Start proc 5175:android.process.media … for content provider com.android.providers.media/.MediaProvider`, do một lệnh shell `content` của bộ chạy test lúc 10:30:17, trước test đầu tiên. Ở lượt 4D-2b process do DownloadProvider khởi động. Tái hiện tất định: `am force-stop com.android.providers.downloads` + `content query … media` làm đỏ; để DownloadProvider khởi động thì xanh | `DeviceDownloads.before()`: ở thiết bị mà DownloadProvider và MediaProvider chung process (kiểm bằng `resolveContentProvider(...).processName`, không theo `SDK_INT`), dừng process đó rồi khởi động lại từ DownloadProvider | Từ trạng thái xấu dựng chủ ý: 4/4 xanh; 3/3 lặp + Gradle cuối API 28 xanh, 0 lỗi cleartext; trên API 36 rule không kích hoạt (0 `Force stopping com.android.providers.downloads`) |
| `DownloadLimitsDeviceTest.aScrollLetsThePageStartNoDownload` (phase 4) đỏ 1/6 lượt cả bộ API 28: trang không cuộn sau cú kéo | Chẩn đoán nhẹ (listener `touch*`/`scroll` passive trong trang) tái hiện 2/15: trang không nhận sự kiện chạm hay cuộn nào, `scrollY` vẫn 0, dù tiêm sự kiện không lỗi. Lượt qua thì trang nhận chạm không-hủy-được (renderer trả lời chậm). Chẩn đoán nặng hơn (bọc `Window.Callback`, `OnTouchListener`) cho thấy ở lượt qua cửa sổ và WebView nhận đủ 26 sự kiện, nhưng với nó lỗi không tái hiện (0/41), nên chưa biết lúc lỗi sự kiện dừng ở tầng nào. Điều chắc chắn: lúc lỗi WebView 66 không biến chuỗi chạm thành cú cuộn nào, cùng họ với hiện tượng 4D đã ghi cho kéo-làm-mới (Chromium xử lý chuỗi chạm tổng hợp khác đi khi renderer bận) | `scrollThePage`: kéo lại tối đa 3 lần cho tới khi trang cuộn (mỗi lần theo dõi 2 s). Điều test khẳng định không đổi: không cú kéo nào là tap | 10/10 lớp đó; Gradle cuối API 28 xanh |
| `ExternalLinkTest.aLinkNeverOpensInIncognito…` (mới) đỏ 1 lần trên Gradle API 36: tab hiện tại vẫn là tab incognito | `TabManager.newTab` thêm tab rồi `select` bằng hai lần cập nhật `StateFlow` trong cùng một task main thread; test đọc từ thread của nó vào giữa hai lần đó | `awaitTabShowing` đọc state trong `onMain` (sau khi task đó xong) | 8/8 lớp đó; Gradle cuối API 36 xanh |

Ngoài ra, lượt Gradle thứ 2 trên API 28 đỏ vì tôi dừng một lượt lặp giữa chừng, để sót `download.txt` trong `/sdcard/Download`; đã xoá tệp và hàng MediaStore của nó, không phải lỗi của test.

## Đột biến trên thiết bị (bản sao working tree ở scratchpad, repo không bị đụng; md5 sau khôi phục trùng repo)

| # | Đột biến | Test bắt (thông điệp) |
|---|---|---|
| M01 | `MainActivity` nhận link ở mọi `onCreate` | `aLinkIsNeverOpenedAgain…`: "after recreate expected 1 but was 2" |
| M02 | `IncomingIntents` bỏ kiểm `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` | cùng test: "after a start from Recents expected 1 but was 2" |
| M03 | decorator theo entry không giữ cờ | `anIncognitoPageIsSecureInEveryFrameItFadesOutIn` (frame 2, 3, 4… bị vẽ không có FLAG_SECURE), `incognitoTabsKeepTheWindowSecureUntilTheyHaveFadedOut`, `backFromAnIncognitoPage…` |
| M04 | navigator không bật cờ trước khi đổi | `anIncognitoTabOpenedThroughTheNavigatorIsSecureBeforeItsFirstFrame` |
| M05 | không ẩn Recents | `recentsKeepsNoPictureOfSkimWhileAnIncognitoTabIsOpen` |
| M06 | không đối soát back stack | `keysOfATabClosedMeanwhile…`, `incognitoTabsWithNoIncognitoTabLeft…` |
| M07 | không `moveTaskToBack` | `backOnTheLinksFirstPage…` |
| M08 | tab link không mang `openedByExternalApp` | `aLinkFromAnotherApp…`, `backOnTheLinksFirstPage…` |
| M09 | `secureTarget` chỉ xét màn trên cùng | `aSheetOfAnIncognitoTabKeepsTheWindowSecureOverAnyScreen` |
| M10 | Start browsing không lưu cờ | `startBrowsingLeavesOnboardingForGood…` |
| M11 | back stack không lưu theo state Activity | `openSheetsAndDialogsComeBackWithTheRecreatedActivity` |

## Giả định phase 5 đã xác nhận / bị bác (trên `skim_api36`)

Xác nhận:
- **FLAG_SECURE trước frame incognito đầu tiên.** `openNewTab(incognito = true)` bật cờ ngay trong task main thread mở tab (kiểm trong cùng `onMain`). Một `OnDrawListener` kiểm từng frame được vẽ khi WebView của tab incognito ở trên màn: không frame nào thiếu cờ khi mở tab, khi Back về trang incognito (phím Back hệ thống, predictive back), khi trang mờ đi, khi đóng tab incognito cuối.
- **Cờ giữ suốt transition, không cần trễ 600 ms.** Rủi ro "[UNVERIFIED] quan sát lúc transition của NavDisplay kết thúc" được giải bằng cách giữ cờ theo entry: 09b → 09 giữ cờ suốt ít nhất 560 ms (kiểm mỗi 20 ms) rồi tắt; trang incognito mờ đi trong crossfade 700 ms (09b) và thu nhỏ trong predictive back (về Welcome) đều được vẽ với cờ ở mọi frame. M03 (bỏ giữ theo entry) làm lộ ngay các frame 2, 3, 4… không có cờ, nên decorator là cần thiết.
- **Sheet/dialog.** 05b, 05d, Site info của tab incognito giữ cờ, kể cả khi nằm trên tab thường; sau Back thì cờ tắt.
- **Recents (API 33+).** Ảnh Recents của task (snapshot của WindowManager, `mIsRealSnapshot`) là ảnh thật khi không có tab incognito, và không phải ảnh thật khi có tab incognito mở phía sau tab thường (cửa sổ khi đó không secure). Nghĩa là `setRecentsScreenshotEnabled(false)` có hiệu lực đầu-cuối. M05 bị bắt.
- **Ảnh chụp màn hình.** `UiAutomation.takeScreenshot()` vùng nội dung giữa các thanh hệ thống: 0 pixel sáng khi tab incognito hiện, hơn 50% sáng khi trang thường hiện. `adb exec-out screencap` tay: cửa sổ app đen 99,8%; phần còn lại là icon status bar và thanh cử chỉ của SystemUI, vì FLAG_SECURE chỉ phủ cửa sổ của app. Ghi chú "ảnh đen hoàn toàn" của 4D-1 vì vậy hơi rộng.
- **Khôi phục back stack.** `rememberNavBackStack` được gọi sau khi start state đã đọc vẫn khôi phục đúng sau `recreate()` (rủi ro dòng 1 của phase, phần recreate; process death thật chưa kiểm). `LinkOptionsKey` với `HitTarget.Image` và `AiPermissionKey` đi qua Bundle thật. Đối soát chạy trên thiết bị: key của tab đã đóng bị bỏ, `TabsKey(true)` thành `TabsKey(false)`, Browser hiện tab còn lại.
- **Link ngoài.** Mở đúng một lần ở cả `onCreate` lẫn `onNewIntent`. Không mở lại khi `recreate()`, khi mở lại từ launcher, và khi Recents khởi động lại task với `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`. `file:`, `javascript:`, `intent:` không mở gì. Link không bao giờ vào incognito. Back ở trang đầu đóng hẳn tab (không Undo) và đưa task xuống nền: Activity dừng (CREATED), không kết thúc. Với dữ liệu mới, link mở trên Welcome, và `BackHandler` của `BrowserRoute` thắng back handler của `NavDisplay`. Tay: `am start -a VIEW -d https://developer.android.com com.dantech.skimbrowser` mở tab, trang hiện (screenshot, `dumpsys activity top` có `SkimWebView`); Back đưa về launcher.
- **Onboarding.** Lần đầu là Welcome. Get started dẫn tới Setup, Back về Welcome. Start browsing để lại đúng `[BrowserKey]` và lưu cờ. Lần mở sau vào thẳng Browser với tab cuối. Back ở Welcome rời app: API 36 đưa task xuống nền, API 28 kết thúc Activity.

Bị bác hoặc điều mới:
- Code-pass nói "khi không còn tab nào Browser mở một New tab lúc hiện lại". Thực tế New tab mở ngay khi Back đóng tab cuối của link, trước khi Activity dừng. Log harness: `done: [WelcomeKey, BrowserKey], tabs [normal new-tab-page]`.
- Snackbar ở 03 (đặt Bottom, 16 dp trên nav bar) che nút Start browsing khi đang hiện; snackbar lỗi hai dòng che gần hết. Một cú chạm vào chỗ snackbar vừa tắt rơi xuống Start browsing (tôi đã vô tình làm vậy trong lúc kiểm tay).
- Trên WebView 133, nhấn giữ một link đôi khi thành kéo thả của Chromium, khi đó Skim không nhận long-click (05d của phase 7 sẽ không mở).
- Trên Android 9, DownloadManager từ chối tải http (cleartext) của mọi app khi `android.process.media` do MediaProvider khởi động. Tải http trong tab thường của Skim đi qua DownloadManager, nên trên máy Android 9 thật có thể hỏng tùy thứ tự khởi động.
- `moveTaskToBack`: khi link đến từ `am start` của shell, launcher hiện ra sau Back (đúng như rủi ro đã chấp nhận: về "app gọi", ở đây là launcher).
- `WindowSecureController` ghi "earlier versions have no such switch". Thực ra API 28–32 có `Activity.setDisablePreviewScreenshots` (deprecated ở 33). Chỉ ghi nhận, không sửa.

## Test đã viết / sửa

- Mới (androidTest, 1.305 dòng):
  - `ui/shell/SkimAppRule.kt`: rule dùng chung. Đọc tab đã lưu, đóng mọi tab còn sót, ghi nhớ settings; launch theo intent của launcher hoặc của link; `navigatorOf`, `keysOf`, `activityOf`; Back hệ thống. Dọn: kết thúc mọi `MainActivity`, đóng hẳn mọi tab, kết thúc phiên incognito, tắt snackbar, trả settings về như trước, xoá history, cookie, WebStorage, dừng server.
  - `ui/shell/AppScreen.kt`: đọc semantics hợp nhất của cửa sổ, tap qua input pipeline khi node đã đứng yên.
  - `SecureFlagTest` (9), `BackStackRestoreTest` (3), `ExternalLinkTest` (7), `AppStartTest` (5), `ui/onboarding/SetupImportTest` (2); fixture `assets/bookmarks-export.html`.
- Sửa test phase 4: `assets/longpress.html` (+7/−4), `browser/DeviceDownloads.kt` (+24/−2), `browser/DownloadLimitsDeviceTest.kt` (+20/−2).

## Deviations

- Không dùng compose test rule. Rule đó cài frame clock và recomposer riêng cho mọi cửa sổ trong process, làm sai thời gian thật mà `SecureFlagTest` đo và các `LaunchedEffect` của app. Thay vào đó `AppScreen` đọc semantics qua `ViewRootForTest` và tap bằng `Finger`.
- (6) Recents kiểm bằng snapshot task trong `dumpsys window` (`mIsRealSnapshot`, chờ `mId` mới sau HOME), có lượt đối chứng không incognito. Gate theo API 33 vì `Activity.setRecentsScreenshotEnabled` là API của nền tảng, lý do đọc được; thiết bị không giữ snapshot (`mSnapshotEnabled=false`) thì skip.
- (4) "Back từ incognito về màn thường" với nội dung nhìn thấy được dùng `[WelcomeKey, BrowserKey(incognito)]`, vì các màn thường khác chưa có entry. Phần thời gian 09b → 09 đo bằng `openTabs`.
- Sheet hiện là entry rỗng toàn màn, nên "cờ còn bật khi sheet hiện" kiểm bằng `holdsFor` (thời gian chuyển màn + 500 ms), không chờ trang bên dưới.
- Import kiểm qua `SetupViewModel` + `BookmarkImporter` + Room thật, với tài liệu do `FileProvider` của app phục vụ (`cacheDir/shared`), không qua picker. Picker thật kiểm tay trên cả hai máy.
- Sửa 3 test/fixture của phase 4 (bảng lỗi); các file nằm trong `app/src/androidTest/`, đúng phạm vi được sửa.
- Mã sản phẩm của người khác đổi giữa lượt (08:59–09:01): `SecureFlagEffect.showsIncognito` (key của tab đã mất tính là incognito), `TabManager.onRendererGone` (`canGoBack`), `BookmarkImporter` và `NetscapeBookmarkParser` (giới hạn số thẻ, bắt `RuntimeException`) cùng test JVM của chúng. Gradle lượt 1 và 3 lượt lặp đầu trên API 36 chạy trước thay đổi đó; mọi lượt từ 09:13 chạy sau. Tôi không chạy JVM vì không sửa mã sản phẩm.
- Kiểm tay link pháp lý dùng một bản cài tạm với `-Pskim.termsUrl=https://example.com/terms -Pskim.privacyUrl=https://example.com/privacy` (không sửa file nào), sau đó cài lại bản debug thường.
- Đột biến chạy trong bản sao working tree ở scratchpad (để reviewer không đọc phải mã bị đột biến); APK của bản sao được cài lên `skim_api36`.
- Lượt Gradle cuối trên API 28 chạy trước khi sửa `ExternalLinkTest.awaitTabShowing` (chỉ thêm rào `onMain` khi đọc state); sửa đó chỉ được chạy lại trên API 36.
- Sau lượt Gradle cuối trên API 36 chỉ sửa một câu KDoc của `DownloadLimitsDeviceTest.scrollThePage` (bỏ khẳng định "while its renderer is busy" chưa chứng minh); đã biên dịch lại androidTest (BUILD SUCCESSFUL, 0 `w:`), không chạy lại thiết bị.

## Checklist tay cho controller (đã thử, lệnh đã chạy)

Công cụ ngoài repo (scratchpad): `p5d-ui.py <dump.xml> [text]` (tìm node trong `uiautomator dump`), `p5d-tap.sh <serial> <text>` (dump rồi `input tap` vào giữa node), `p5d-pngsample.py <png>` (lấy mẫu màu status bar, icon, nội dung, tỉ lệ đen), harness `p5d-manual/ManualProbe5.kt` (chép tạm vào `androidTest/.../ui/shell/`, cài, chạy `am instrument -w -e class com.dantech.skimbrowser.ui.shell.ManualProbe5 -e onboarded … -e theme … -e tab … -e holdMs …`, log `SkimManual: ready/done`; đã xoá khỏi repo trước các lượt cổng). Mọi lệnh `adb` đều có `-s emulator-5580` hoặc `-s emulator-5582`.

1. **Link từ app khác (API 36, đạt).** `pm clear com.dantech.skimbrowser`; `am start -W -a android.intent.action.VIEW -d https://developer.android.com com.dantech.skimbrowser`. `dumpsys activity activities` cho `topResumedActivity … com.dantech.skimbrowser/.MainActivity` với `Intent { act=android.intent.action.VIEW dat=https://developer.android.com … }`; `dumpsys activity top` có `com.dantech.skimbrowser.browser.SkimWebView{… 0,0-1080,2201}`; screenshot là trang Android Developers. `input keyevent KEYCODE_BACK` thì `topResumedActivity` là launcher, Skim `PAUSED`/dừng, không `finishing`.
2. **Set as default, API 36 (đạt).** `pm clear`, mở app, tap Get started, tap Set as default: hộp thoại `com.google.android.permissioncontroller` "Set Skim as your default browser app?" (Chrome "Current default", Skim). Chọn Skim, Set as default: `cmd role get-role-holders android.app.role.BROWSER` cho `com.dantech.skimbrowser`; nút thành "Done", node clickable cha `enabled=false`, tap không làm gì. Kiểm lại ở `onResume`: HOME, `cmd role add-role-holder android.app.role.BROWSER com.android.chrome`, mở lại Skim thì nút lại là "Set as default". Chrome đã được trả lại làm mặc định.
3. **Set as default, API 28 (đạt một phần, xem ghi chú).** Set as default mở Settings "Default apps" (`com.android.settings`); "Browser app" đang "None", chọn được Skim. Quay lại Skim, nút vẫn là "Set as default". Lý do không phải lỗi của Skim: trên image này `cmd package resolve-activity -a android.intent.action.VIEW -d https://example.com` trả `com.google.android.setupwizard/.util.WebDialogActivity` (filter http/https priority 5, hiện là "Android Setup" trong danh sách Browser app), nên link web thật sự không tới Skim và `isDefault` đúng khi báo `false`. Tắt tạm component đó (`adb root`, `pm disable …WebDialogActivity`) thì `resolve-activity` trả Skim, nhưng setup wizard tự bật lại; đã `pm enable` và `adb unroot` để trả nguyên trạng. Đường kiểm lại ở `onResume` đã đạt trên API 36; nhánh API 26–28 của `DefaultBrowser` có test JVM. Cần một máy Android 8–9 thật để kiểm trọn.
4. **Choose file với picker thật (đạt, cả hai máy).** `adb push bookmarks.html /sdcard/Download/` (export kiểu Chrome, 6 bookmark http/https, 1 `javascript:` bị bỏ) và `page.html` (trang thường). Setup, Choose file, DocumentsUI, Show roots, Downloads, `bookmarks.html`: snackbar "Imported 6 bookmarks" + "View"; tap View ngay thì sang màn Bookmarks (trống, chưa có entry), Back về Setup. `page.html`: snackbar "Couldn't read that file. Choose an HTML bookmarks file exported from a browser." hai dòng, không action, còn ở +3 s, mất trước +5 s. Đã xoá các tệp đã push.
5. **Link pháp lý.** Bản debug (`skim.termsUrl`/`skim.privacyUrl` rỗng): `uiautomator dump` chỉ có một TextView "By continuing you agree to the Terms of Service and Privacy Policy." `clickable=false`, không node link riêng, chữ thường. Bản cài tạm với `-Pskim.termsUrl=https://example.com/terms -Pskim.privacyUrl=https://example.com/privacy`: hai đoạn màu primary trên dòng pháp lý (x 500–739 và 807–1002 px); tap "Terms of Service" mở tab example.com/terms (`SkimWebView` trong cây view); Back đóng tab (0 WebView) và về Welcome. Đã cài lại bản debug thường.
6. **Icon status bar (API 36, đạt).** Harness giữ Browser với trang thường. `cmd uimode night yes` + Theme Light: `dumpsys window windows` cho cửa sổ MainActivity `apr=LIGHT_STATUS_BARS LIGHT_NAVIGATION_BARS`, nền status bar (250,248,255), icon tối nhất (100,99,102). `cmd uimode night no` + Theme Dark: không `LIGHT_*`, nền (18,19,24), icon (255,255,255). Tab incognito: không `LIGHT_*`, `FLAG_SECURE`, screencap đen 99,8% còn icon trắng. Xoay ngang (`settings put system accelerometer_rotation 0; settings put system user_rotation 1`) rồi xoay lại: vẫn `LIGHT_STATUS_BARS`, ảnh ngang nền sáng icon tối. Đã trả `night no`, `accelerometer_rotation 1`, `user_rotation 0`.
7. **TalkBack (không kiểm được qua adb).** Bật được (`settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`, `accessibility_enabled 1`; `dumpsys accessibility` cho TalkBack bound, `touchExplorationEnabled=true`) và TalkBack có đọc (log TTS). Nhưng không lấy được nội dung đọc: TalkBack không log câu đọc ở mức log mặc định, `uiautomator events` dừng sau vài sự kiện, `input swipe` và `input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT` không sinh sự kiện focus nào. Đã tắt lại (`settings delete secure enabled_accessibility_services`, `accessibility_enabled 0`). Bằng chứng gián tiếp: snackbar lỗi có `LiveRegionMode.Assertive` (test tự động); với URL rỗng thì không có link nào để đọc. Cần người kiểm bằng TalkBack trên bản có URL pháp lý.
8. **Kéo thả khi nhấn giữ link (API 36, quan sát).** Xem bảng lỗi; liên quan 05d của phase 7.

## Concerns / Blockers

- Không có blocker. Cả hai cổng thiết bị xanh trên mã cuối của từng máy (API 28: trước sửa rào `onMain` của một test, xem Deviations).
- 3 sửa vào test/fixture của phase 4 nên được controller duyệt: `longpress.html` không còn draggable; `DeviceDownloads` dừng và khởi động lại `android.process.media` trước mỗi test ở thiết bị mà DownloadProvider chung process với MediaProvider; `DownloadLimitsDeviceTest` kéo lại tối đa 3 lần khi WebView bỏ qua cú kéo. Riêng sửa thứ ba chưa chứng minh được tầng nào bỏ chuỗi chạm (chẩn đoán nặng làm mất lỗi), chỉ chứng minh được trang không nhận sự kiện nào.
- Tải http qua DownloadManager trên Android 9 có thể hỏng ngoài thực tế (xem trên); tải https không bị ảnh hưởng.
- Process death thật (không chỉ `recreate`) vẫn chưa kiểm trên thiết bị (rủi ro dòng 1 của phase, đã hẹn ở phase 13).
- Người khác sửa mã sản phẩm trong lúc lượt này chạy; controller nên chạy lại JVM trên trạng thái cuối trước khi commit.

## Câu hỏi chưa giải quyết

1. Snackbar của 03 che Start browsing trong 4–10 s: chấp nhận, hay 03 cần vị trí snackbar khác (ví dụ trên nút)?
2. Back ở tab cuối của link ngoài: giữ việc mở New tab ngay, hay chỉ mở khi Browser hiện lại? Với dữ liệu mới, lần mở sau có nên quay về Welcome (`[WelcomeKey]`) thay vì Browser với New tab?
3. Tải http trên Android 9: chấp nhận là giới hạn nền tảng, hay tải http (không https) trong process ở API 28 trở xuống?
4. Nhấn giữ link thành kéo thả trên WebView 133: phase 7 có cần coi lúc bắt đầu kéo như một long-press (mở 05d) không?
5. API 28–32: có dùng `setDisablePreviewScreenshots(true)` để ẩn ảnh Recents khi còn tab incognito (như `setRecentsScreenshotEnabled(false)` ở API 33+) không?
6. TalkBack: ai kiểm bằng tay trên bản có URL pháp lý (hai link và snackbar lỗi)?
