# Phase 5 (khung app, điều hướng, onboarding): phần code

Ngày 2026-10-22 07:59 (Asia/Singapore), nhánh `feat/skim-design-implementation`, HEAD vẫn là commit phase 4. Chưa `git add`, chưa commit. Không khởi động emulator, không dùng điện thoại thật, không viết test instrumented.

## Kết quả

Phần code của phase 5 đã xong và xanh trên JVM/Robolectric. Có đủ 26 key điều hướng với tham số tường minh, lưu qua process death bằng `SerializersModule` liệt kê từng key. `SkimNavigator` có đủ hàm (kể cả `remove`) và bật FLAG_SECURE trước mọi thay đổi đưa nội dung incognito lên màn hình. `SkimNavDisplay` dùng `DialogSceneStrategy`, `BottomSheetSceneStrategy` (recipe Nav3) và một decorator giữ FLAG_SECURE tới frame incognito cuối. `SkimApp` khởi động có dự phòng, đối soát back stack một lần, đổi theme, icon status bar, FLAG_SECURE và Recents theo những gì đang hiện, đặt một host snackbar dịch action dạng dữ liệu, và nhận link từ app khác đúng một lần. Ngoài ra có `BrowserRoute`, 01 Welcome, 03 Setup (role hoặc default apps, import bookmark bằng Jsoup, snackbar), `AiFabMode` + `TabAiFab`, `IncomingIntents`, `DefaultBrowser`. Golden 01 và 03 khớp board, trừ các lệch đã ghi vào `docs/design-guidelines.md`. 66 đột biến có chủ ý đều bị test bắt.

## Kiểm chứng (chạy thật, theo thứ tự cuối)

- Lệnh lọc của prompt (`:app:cleanTestDebugUnitTest :app:testDebugUnitTest` với 4 package `ui.navigation`, `ui.shell`, `ui.onboarding`, `data.importer`): 3 lần liên tiếp, mỗi lần 117 test, 0 lỗi, 0 bỏ qua, 0 dòng `w:`.
- Lặp 10 lần mọi lớp test mới và lớp test bị sửa (thêm `system.*`, `ui.browser.*`, `testing.*`), mỗi lần `cleanTestDebugUnitTest` trước: 10/10 xanh, mỗi lần 139 test.
- Toàn dự án `:app:cleanTestDebugUnitTest :app:testDebugUnitTest`: 1022 test, 97 lớp, 0 lỗi, 0 bỏ qua (phase 4 là 889).
- `:app:assembleDebug`: xanh.
- `:app:lintDebug`: 0 lỗi, 110 cảnh báo (baseline 123; giảm vì một số chuỗi trước chưa dùng nay đã dùng), 0 cảnh báo trong file mới hoặc file đã sửa. Cảnh báo `UseKtx` duy nhất ở lượt đầu (`Uri.parse` trong `DefaultBrowser`) đã sửa bằng `toUri()`.
- `:app:recordRoborazziDebug --tests '*OnboardingScreenshotTest*'` ghi lại 3 golden trùng md5 với bản đã duyệt; `:app:verifyRoborazziDebug` xanh (1022 test, 96 golden component cũ không đổi).
- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false`: xanh, 0 dòng `w:`, 0 dòng `e:`; androidTest (gồm `MainActivityLifecycleTest`) vẫn biên dịch.
- Quét vệ sinh (36 file mới, dòng thêm của 5 file sửa): 0 ID plan/phase/finding, 0 nhắc AI, 0 TODO, 0 log debug, 0 secret, 0 ký tự bidi/zero-width thô, 0 ký tự ngoài ASCII trong code; mọi `Log.w` tĩnh hoặc chỉ in tên lớp exception.

## Đột biến

66 đột biến, harness ngoài repo (`scratchpad/p5-mutate.py`, `p5-mutate-i.py`), khôi phục và so md5 cả cây nguồn sau mỗi lượt (đều trùng). Lượt đầu: 62 bị bắt, 4 sống. I01–I03 sống vì kiểm `data.scheme` trùng với `hostOf` (vốn chỉ nhận http/https có host), nên đã bỏ kiểm thừa và viết lại ba đột biến cho mã mới. E01 sống vì test "giữ cờ khi màn incognito mờ đi" khẳng định trước khi thay đổi back stack kịp compose; một probe tạm cho thấy cần một frame, test đã sửa và nay kiểm cả hai màn còn hiện giữa lúc mờ. Lượt hai chạy lại I01–I06 và E01–E05: đều bị bắt. Kết quả cuối 66/66, mỗi đột biến bị test của đúng lớp liên quan bắt (harness chỉ chạy các lớp đó).

| Nhóm | Số | Đột biến | Test bắt |
|---|---|---|---|
| Đối soát back stack | 6 | giữ key của tab đã mất; bỏ nhầm key của tab còn mở; `TabsKey(true)` không về `false`; về `false` cả khi còn tab incognito; giữ focus tab đã mất; để back stack rỗng | `BackStackReconcileTest` |
| `secureTarget` | 6 | chỉ xét màn trên cùng (bỏ sheet và dialog); 09b không secure; Browser của tab incognito không secure; Reader, sheet, dialog của tab incognito không secure; tính cả key dưới màn trên cùng; dialog không phải overlay | `SecureTargetTest` |
| Navigator | 12 | `setSecure` sau `select`; sau `newTab`; sau khi đổi back stack; `finishOnboarding` để lại `WelcomeKey`; `remove` gỡ key đầu thay vì trên cùng; gỡ cả phần phía trên; tab incognito vẫn mở khi không hỗ trợ; không có snackbar; `back` gỡ key cuối; push trùng; `showBrowser` giữ overlay; Tabs và 09b chồng nhau | `SkimNavigatorTest` |
| Link từ app khác | 6 | nhận `file:`, `javascript:`, `intent:`; đọc link từ extras; nhận mọi action; mở lại link Recents giao lại | `IncomingIntentsTest` |
| AI FAB | 4 | bật trong incognito; đảo điều kiện lỗi tải; nhãn New tab page sai; FAB luôn bật | `AiFabModeTest` |
| Parser | 6 | nhận `javascript:`; không giải entity; giữ ký tự bidi; trang thường thành tệp bookmark; mất `<DL>` đứng sau `<DT>` đã đóng; không giới hạn độ dài URL | `NetscapeBookmarkParserTest`, `BookmarkImporterTest` |
| Importer | 5 | không giới hạn 10 MB; từ chối tệp đúng 10 MB; báo thành công khi `importTree` null; bỏ tên hiển thị; không làm sạch tên | `BookmarkImporterTest`, `SetupViewModelTest` |
| Setup | 4 | không kiểm lại ở `ON_RESUME`; snackbar thành công thiếu View; lỗi không phải alert; Start browsing không lưu onboarding | `SetupViewModelTest` |
| Default browser | 3 | đảo nhánh API; không dự phòng `ACTION_SETTINGS`; xin role cả khi đã giữ | `DefaultBrowserTest` |
| Snackbar action | 6 | Switch không đổi tab; Edit sai bookmark; View mở gốc Bookmarks; Open thiếu quyền đọc; Open crash khi không có app; Undo thành finalize | `SnackbarActionsTest` |
| Cờ theo entry, effect | 5 | entry rời back stack buông cờ ngay; entry không giữ cờ; không buông khi rời composition; bỏ qua lần navigator bật trước; không ẩn Recents | `SecureFlagEffectTest` |
| Back của Browser | 2 | đóng tab app khác trước khi lùi lịch sử; bỏ nhánh New tab page | `BrowserBackTest` |
| Serializer | 1 | thiếu `MoveToFolderKey` trong `SerializersModule` | `SkimKeySerializationTest` |

## Chữ ký thật

- Key (`ui/navigation/SkimKeys.kt`): `@Serializable sealed interface SkimKey : NavKey { val placement: SnackbarPlacement; val tabId: String? }`, sealed con `SheetKey`, `DialogKey`.
  - Màn: `WelcomeKey`, `SetupKey`, `BrowserKey`, `ReaderKey(tabId)`, `TabsKey(incognito, focusTabId = null)`, `SettingsKey`, `AiPrivacyKey`, `HistoryKey`, `BookmarksKey(folderId: Long? = null)`, `SkimHistoryKey`, `LicensesKey`. `placement` là `AboveToolbar` cho `BrowserKey`, `ReaderKey`, `TabsKey`.
  - Sheet: `MoreMenuKey(tabId)`, `LinkOptionsKey(tabId, target: HitTarget)`, `SiteInfoKey(tabId)`, `AskPageKey(tabId = null, savedChatId = null, fromReader = false)`, `AskTabsKey(prompt = null, savedChatId = null)`.
  - Dialog: `AiPermissionKey(tabId, fromReader = false)`, `ShortcutDialogKey(shortcutId: Long? = null)`, `SearchEngineDialogKey`, `ThemeDialogKey`, `ClearBrowsingDataKey`, `DeleteAiDataKey`, `ClearSkimHistoryKey`, `RenameGroupKey(groupId: Long)`, `EditBookmarkKey(bookmarkId: Long)`, `MoveToFolderKey(bookmarkId: Long)`.
  - Helper: `SkimKey.isOverlay`, `visibleKeys(keys)`, `topScreen(keys)`, `skimNavConfig: SavedStateConfiguration`, `reconcileBackStack(keys: List<NavKey>, tabs: TabsState): List<NavKey>`. Key mới phải thêm vào `skimNavConfig` (test bắt nếu thiếu).
- `class SkimNavigator(backStack: NavBackStack<NavKey>, tabs: TabManager, incognito: IncognitoSession, secure: WindowSecureController, snackbar: SnackbarController)`, gọi trên main thread: `push(key: SkimKey)`, `back()`, `replaceTop(key)`, `remove(key)`, `finishOnboarding()`, `showBrowser()`, `showTab(tabId)`, `openNewTab(incognito, url = null)`, `openInBackground(url, incognito): String?`, `openTabs(incognito, focusTabId = null)`, `openReader(tabId)`, `openMoreMenu(tabId)`, `openLinkOptions(tabId, target)`, `openSiteInfo(tabId)`, `openAskPage(tabId, fromReader = false)`, `openSavedChat(chatId)`, `openAskTabs(prompt = null)`, `openSavedTabsChat(chatId)`, `openSettings()`, `openAiPrivacy()`, `openHistory()`, `openBookmarks(folderId = null)`, `openSkimHistory()`, `openLicenses()`, `openDialog(key: DialogKey)`; `val secureArming: Int`; `@VisibleForTesting internal val keysForTests: List<NavKey>`.
- `@Composable fun SkimNavDisplay(backStack, navigator, container, modifier = Modifier)`. Entry đăng ký trong `skimEntries(navigator, container)` cùng file: `entry<K> { ... }`; sheet `entry<K>(metadata = BottomSheetSceneStrategy.bottomSheet(R.string.x)) { ... }` (hoặc `metadata = { key -> bottomSheet(...) }` khi tiêu đề theo key, như 05d Link/Image); dialog `entry<K>(metadata = DialogSceneStrategy.dialog()) { ... }`. Key chưa có entry hiện entry rỗng. Slot của Browser: `newTabPage = {}` (phase 6 thay), `page = { tab, runtime -> ... }` hiện `WebViewHost` hoặc `PageStoppedWorking` khi `LoadError.Crashed` (phase 7 thay cả lambda).
- `class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T>`, `BottomSheetSceneStrategy.bottomSheet(@StringRes paneTitle: Int, skipPartiallyExpanded: Boolean = true): Map<String, Any>`; `val LocalSkimSheetState: ProvidableCompositionLocal<SkimSheetState?>` (null ngoài sheet; 06 gọi `LocalSkimSheetState.current?.partialExpand()` khi bấm citation).
- `@Composable fun SkimApp(container: AppContainer, host: BrowserHost, pendingLinks: ReceiveChannel<String>, onNavigator: (SkimNavigator) -> Unit = {})`.
- FLAG_SECURE: `fun showsIncognito(key: SkimKey, tabs: TabsState): Boolean`, `fun secureTarget(keys: List<NavKey>, tabs: TabsState): Boolean`, `@Composable fun SecureFlagEffect(target: Boolean, hasIncognitoTabs: Boolean, armed: Int, secure: WindowSecureController)`, `@Composable fun rememberSecureEntryDecorator(keys, contentKeyOf: (NavKey) -> Any, tabs, secure): NavEntryDecorator<NavKey>`, owner `INCOGNITO_SURFACE_OWNER = "incognito-surface"` (internal), Recents owner `incognito-tabs`.
- `fun interpret(action: SnackbarAction, navigator: SkimNavigator, undo: UndoRegistry, activityHost: ActivityHost)`.
- `enum class AiFabMode { Enabled, OffInIncognito, UnavailableUntilLoaded }`, `fun aiFabMode(tab: Tab, runtime: TabRuntime)`, `@Composable fun aiFabContentDescription(mode, isNewTabPage)`, `@Composable fun TabAiFab(tab: Tab, runtime: TabRuntime, onClick: () -> Unit, modifier: Modifier = Modifier)`.
- `object IncomingIntents { fun urlFrom(intent: Intent?): String? }`; `@Composable fun SystemBarsEffect(dark: Boolean)`.
- `@Composable fun BrowserRoute(container, navigator, newTabPage: @Composable (Tab) -> Unit, page: @Composable (Tab, TabRuntime) -> Unit)`, `enum class BrowserBack`, `fun browserBack(tab, runtime): BrowserBack`, `@Composable fun PageStoppedWorking(tab, error: LoadError.Crashed, onTryAgain, modifier)`.
- Onboarding: `WelcomeScreen(onGetStarted, onOpenLegal: (url: String) -> Unit, termsUrl, privacyUrl, modifier)`, `SetupScreen(state: SetupUiState, onSetDefault, onChooseFile, onStartBrowsing, modifier)`, `SetupRoute(container, navigator)`, `data class SetupUiState(val isDefault: Boolean)`, `class SetupViewModel(isDefaultBrowser: () -> Boolean, importer, settings, snackbar, appScope) : ViewModel(), DefaultLifecycleObserver` với `state`, `refreshDefault()`, `import(uri)`, `finishOnboarding()`.
- `object DefaultBrowser { fun isDefault(context): Boolean; fun requestIntent(context): Intent }`; `object NetscapeBookmarkParser { fun parse(bytes: ByteArray): List<ImportedNode>?; MAX_URL_LENGTH = 8192; MAX_TITLE_LENGTH = 1024 }`; `class BookmarkImporter(resolver: ContentResolver, bookmarks: BookmarkRepository, ioDispatcher = IO, parseDispatcher = Default) { suspend fun import(uri): ImportResult; MAX_FILE_BYTES = 10 * 1024 * 1024 }`; `sealed interface ImportResult { Imported(folderId, bookmarkCount); Failed(reason: Unreadable | TooLarge | NotBookmarks | NoBookmarks) }`.
- `AppContainer`: thêm `undoRegistry`, `snackbarController`, `bookmarkImporter` (lazy). `MainActivity`: `pendingLinks = Channel<String>(Channel.BUFFERED)` (gửi từ `onCreate` khi `savedInstanceState == null` và từ `onNewIntent`), `setContent { CompositionLocalProvider(LocalBrowserHost provides browserHost) { SkimApp(...) } }`, `@VisibleForTesting internal val navigatorForTests: SkimNavigator?`; `browserHostForTests`, receiver tải xuống, `createBrowserHost`, vòng đời và `tabs.detach(pool)` giữ nguyên.

## Parity

So bằng ImageMagick trên ảnh 824×1830. `03-default-browser-import`: tiêu đề, hai thẻ (y 184–348 và 360–524 dp), nút tonal S (cùng y, cùng mép phải), chú thích (y 548 dp), Start browsing (y 819 dp) trùng từng pixel; lệch là chỗ xuống dòng của thân thẻ thứ hai và chú thích (chữ hẹp hơn), nút hẹp hơn 3,5 dp, hình tile MaterialShapes. `01-welcome`: kích thước, màu và khoảng cách bên trong khối giữa trùng (logo đến art 72 dp, art đến tiêu đề 234 dp), link `primary` 500 gạch chân; lệch là dòng pháp lý một dòng thay vì hai nên Get started thấp hơn 16 dp và khối giữa thấp hơn 7–8 dp. `setup-done-03` (chưa vẽ) xem bằng mắt. Đã thêm 3 dòng "Golden đã duyệt" và 4 dòng "Known deviations" vào `docs/design-guidelines.md`: dòng pháp lý của 01, chữ lớn (01 và 03 cuộn được), Done của 03, chuyển màn hình (crossfade mặc định và theme đổi cùng frame).

## Lệch spec và lý do

1. `bottomSheet(@StringRes paneTitle: Int, skipPartiallyExpanded = true)`: thêm `paneTitle` bắt buộc vì `SkimBottomSheet` của phase 2 bắt buộc pane title (TalkBack đọc khi sheet mở). Phase 11 gọi `bottomSheet(R.string.ai_ask_about_page, skipPartiallyExpanded = false)`.
2. `openDialog(key: DialogKey)` thay cho `SkimKey`: chặt hơn, mọi lời gọi dự kiến vẫn biên dịch.
3. `SetupViewModel` thêm `appScope`, vì Start browsing gỡ `SetupKey` và view model bị huỷ cùng entry, kéo theo lệnh ghi `onboardingCompleted` hoặc import đang chạy. `isDefaultBrowser` là một hàm để test không cần RoleManager thật. View model là `DefaultLifecycleObserver` và `SetupRoute` gắn nó vào lifecycle của entry, nên default được kiểm lại ở mỗi `ON_RESUME`.
4. `SkimApp` thêm `onNavigator` làm móc cho `MainActivity.navigatorForTests`.
5. FLAG_SECURE không tắt bằng trễ 600 ms. Nav3 1.2.0 không có API công khai báo transition xong ở cấp `NavDisplay` (`LocalNavAnimatedContentScope` chỉ có trong entry), và crossfade mặc định dài 700 ms nên trễ 600 ms để lộ 100 ms cuối. `rememberSecureEntryDecorator` cho nội dung của entry incognito giữ cờ suốt thời gian còn trong composition: AnimatedContent giữ màn đang thoát tới khi transition xong, NavDisplay giữ overlay tới khi `onRemove` xong. Entry rời back stack giữ trạng thái cuối; entry còn trong back stack đi theo tab của nó. `SecureFlagEffect` vẫn tính theo `secureTarget` và gỡ phần navigator bật trước (bộ đếm `secureArming`).
6. Test "SerializersModule đủ mọi subclass" dùng descriptor sealed do plugin serialization sinh lúc biên dịch (`serializer<SkimKey>()`) thay cho `KClass.sealedSubclasses`: `kotlin-reflect` không có trên classpath biên dịch của test (gây 2 dòng `w:`), lúc chạy chỉ có bản 1.9.25 kéo theo SDK Anthropic. Ý nghĩa giữ nguyên.
7. `IncomingIntents` dựa vào `hostOf` (http hoặc https có host) thay cho so `data.scheme` riêng (kiểm thừa, xem đột biến). Intent có `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (Recents giao lại intent cũ) bị bỏ qua vì đó là link đã mở.
8. `reconcileBackStack` còn xoá `focusTabId` của `TabsKey` khi tab đó không còn; kết quả rỗng thành `[BrowserKey]`.
9. Key chưa có màn hiện entry rỗng thay vì crash, tới khi phase sở hữu đăng ký.
10. Import: tiêu đề và tên tệp thành chữ hiển thị thuần (bỏ bidi, zero-width), cắt 1.024 và 255 ký tự; URL dài hơn 8.192 bị bỏ (một hàng như vậy làm hỏng việc đọc danh sách bookmark); tệp có cấu trúc DL/DT bookmark nhưng thiếu doctype vẫn được nhận. Mọi lỗi dùng cùng snackbar `snackbar_import_failed` (4 s, alert).
11. `push` bỏ qua key đã ở trên cùng (chạm hai lần); `openTabs` thay màn Tabs đang mở (đổi segment 09 ↔ 09b); `showTab` của tab không còn mở thì không làm gì; `BrowserRoute` chỉ mở New tab khi màn còn trong back stack (lifecycle từ STARTED).
12. Trang crash vượt ngân sách hiện `PageStoppedWorking` tối thiểu (copy D8: `error_crashed_title`, `error_crashed_body` với host, Try again) trong slot `page`, chờ phase 7.
13. Test ngoài danh sách: `SecureFlagEffectTest`, `BrowserBackTest`, và `testing/TestDocuments.kt` (provider tài liệu thật cho import). Sửa `testing/A11yAssertions.kt` cùng 2 test mới trong `A11yAssertionsTest`: link nội tuyến (`LinkAnnotation`) không có nhãn riêng trong cây semantics, nay nhãn là chữ nó phủ.
14. File phase 1–4 đã sửa: `AppContainer.kt`, `MainActivity.kt`, `testing/A11yAssertions.kt`, `testing/A11yAssertionsTest.kt`, `docs/design-guidelines.md`. Không sửa `strings.xml` (phase 1 đã có đủ chuỗi), manifest, Gradle, `.idea/`; không thêm drawable.

## Việc cho lượt test thiết bị

Móc đã để sẵn: `MainActivity.navigatorForTests` (null tới khi settings và tab khôi phục xong; lấy trong `scenario.onActivity` và chờ khác null), `SkimNavigator.keysForTests`, `MainActivity.browserHostForTests.secure.isSecure` hoặc `window.attributes.flags`. Mọi lệnh navigator chạy trên main thread.

- `SecureFlagTest` (`skim_api36`, `assumeTrue(MULTI_PROFILE)`): `openNewTab(incognito = true)` rồi đọc cờ ngay trong cùng lần chạy main thread; `openMoreMenu`, `openLinkOptions`, `openSiteInfo` trên tab incognito giữ cờ (các key sheet chưa có entry tới phase 7 nên hiện entry rỗng toàn màn; logic cờ không phụ thuộc). `openTabs(true)` rồi `openTabs(false)`: cờ còn bật giữa crossfade 700 ms và tắt sau đó. API 33+: Recents tắt chụp khi còn tab incognito; không có getter công khai, cần cách kiểm riêng.
- `BackStackRestoreTest`: `ActivityScenario.recreate()` khi có key sheet thì `keysForTests` còn key đó; đóng tab của sheet rồi `recreate()` thì key bị lọc, không crash. Activity khai báo `configChanges` nên chỉ `recreate()` đi đường lưu và khôi phục state.
- `ExternalLinkTest`: `ACTION_VIEW` tới trang MockWebServer mở tab thường `openedByExternalApp`; Back ở trang đầu đóng hẳn tab và đưa task xuống nền; sau `recreate()` hoặc `finish()` rồi mở từ launcher, link không mở lại. Khi không còn tab nào mà màn trên cùng là Browser, `BrowserRoute` mở một New tab lúc màn hiện lại, nên khi đếm tab hãy mở sẵn một tab thường hoặc chỉ đếm tab có URL. Dữ liệu app mới thì link mở trên Welcome.
- `MainActivityLifecycleTest` đã đọc: vẫn hợp lệ; với dữ liệu mới, màn đầu là Welcome nên không tự mở tab.
- Kiểm tay: Set as default trên `skim_api36` (hộp thoại role, quay lại thì Done tắt) và `skim_api28` (Default apps); Choose file với export của Chrome và với một HTML thường; link pháp lý mở tab rồi Back về 01 (cần `skim.termsUrl`, `skim.privacyUrl`); icon status bar với Theme Light trên hệ thống tối, Dark trên hệ thống sáng, tab incognito, và sau khi xoay; TalkBack đọc hai link pháp lý và snackbar lỗi.

## Câu hỏi chưa giải quyết

1. Chuyển màn hình đang là crossfade 700 ms mặc định của Nav3; tokens.md nói `slowSpatialSpec` cho "whole screen changes" nhưng không vẽ chuyển động. Có cần `transitionSpec` riêng, và ai quyết?
2. Theme đổi cùng frame với điều hướng nên màn đang mờ đi mang scheme mới (rời 09b, 09b sáng lên trong 700 ms). Chấp nhận, hay cần theme theo từng entry?
3. Sheet đóng bằng code (nút Close gọi `navigator.back()`) biến mất ngay vì recipe không cài `OverlayScene.onRemove`; vuốt, chạm scrim và Back vẫn có hoạt ảnh. Phase 7 và 11 có cần hoạt ảnh này không?
4. Có nên thêm ở phase 13 một test "mọi `SkimKey` có entry"?
5. Sau khi tab cuối bị đóng, quay lại Browser sẽ mở một New tab; đúng mong muốn không?
