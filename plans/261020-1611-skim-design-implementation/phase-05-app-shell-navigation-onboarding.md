---
phase: 5
title: "App shell, navigation & onboarding"
status: completed
priority: P1
effort: "22h"
dependencies: [2, 3, 4]
---

# Phase 5: App shell, navigation & onboarding

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Navigation 3: `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §5 (mô hình key, scene strategy, `rememberNavBackStack(SavedStateConfiguration, …)`), §8 (status bar khi app tối khác hệ thống).
- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §A3 (FLAG_SECURE), §B10 (ROLE_BROWSER, link từ app khác, import bookmarks + Jsoup).
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-failure-mode-analyst.md` Finding 1, 2; `red-team-assumption-destroyer.md` Finding 3, 4, 7; `red-team-security-adversary.md` Finding 9; `red-team-scope-complexity-critic.md` Finding 9, 10.
- `design/spec/navigation-map.md`: Main path, Rules for every screen, 01, 03, 05 "Back on the first page", "Android system UI, not drawn".
- Scout: `plans/reports/scout-261020-2319-design-inventory.md` §2 (01, 03), §3(d) (snackbar import).
- Board: `design/screens/01-welcome.png`, `design/screens/03-default-browser-import.png`, `design/html/01-welcome.html`, `design/html/03-default-browser-import.html`.

## Overview

Dựng khung ứng dụng: toàn bộ `NavKey` (tham số tường minh) cho mọi màn hình/sheet/dialog, scene strategy cho sheet và dialog, back stack được lưu qua process death và được đối soát với tab còn tồn tại sau `restore()`, `SkimNavigator`, trạng thái cấp app (theme, bề mặt incognito, FLAG_SECURE tính trên mọi entry đang hiển thị, icon status bar), host snackbar dịch action dữ liệu, link từ app khác nhận đúng một lần, `BrowserRoute`, và hai màn onboarding 01 Welcome và 03 Setup. Phase sở hữu end-to-end hai rule "Sheets and menus" và "Snackbars". Kết thúc bằng cổng thiết bị.

## Key insights

- Sheet là `NavKey` trong back stack đã lưu nên "restore open sheets" có sẵn; dùng overload `rememberNavBackStack(SavedStateConfiguration, …)` với `SerializersModule` polymorphic tường minh để an toàn với R8 (đã kiểm overload trong `navigation3-runtime-android` 1.2.0). `DialogSceneStrategy` có sẵn trong `navigation3-ui`; `BottomSheetSceneStrategy` là recipe (~60 dòng) phải copy.
<!-- Red Team: Back stack khôi phục trỏ tới tab không còn — 2026-10-21 -->
- Back stack khôi phục có thể trỏ tới tab đã mất (tab incognito theo D2, tab đóng mềm bị purge). Sau `restore()`, bỏ mọi key có `tabId` không còn tồn tại và đổi `TabsKey(incognito = true)` thành `TabsKey(false)` khi không còn tab incognito.
- Menu, find bar (05c), ô tìm kiếm mở rộng (04b), dialog quyền site và panel 08b để ở dạng UI state, không phải key.
- `SystemBarStyle.auto` đọc `uiMode` của hệ thống, không đọc theme app: gọi lại `enableEdgeToEdge(...)` mỗi khi `dark` của app đổi.
<!-- Red Team: FLAG_SECURE hở dưới sheet và lúc chuyển cảnh — 2026-10-21 -->
- FLAG_SECURE phải có trước frame incognito đầu tiên và chỉ được tắt khi không còn frame incognito nào: tính theo **mọi entry đang hiển thị** (màn hình + sheet/dialog phía trên), bật đồng bộ trong navigator trước khi đổi state, và chỉ tắt sau khi transition thoát kết thúc. Trên API 33+ còn `setRecentsScreenshotEnabled(false)` khi còn tab incognito.
<!-- Red Team: incomingLinks replay=1 mở lại link cũ — 2026-10-21 -->
- Link từ app khác phải được nhận đúng một lần: `Channel<String>(BUFFERED)` thuộc `MainActivity` (mất cùng Activity), không dùng flow process-scoped có replay.
- `singleTask` + VIEW intent: link từ app khác đi vào `onNewIntent`; Back ở trang đầu của tab đó đóng tab rồi `moveTaskToBack(true)` để về app gọi.
<!-- Red Team: Snackbar action dạng dữ liệu — 2026-10-21 -->
- Host snackbar của cửa sổ chính dịch `SnackbarAction` bằng navigator/Activity hiện tại; process-scoped controller không giữ lambda nào.
- Quy ước tích hợp (ghi trong `plan.md`): mọi đích có key ngay từ phase này; entry được đăng ký ở phase sở hữu đích.

## Requirements

Chức năng:
<!-- Red Team: Tham số key tường minh — 2026-10-21 -->
- Key màn hình: `WelcomeKey`, `SetupKey`, `BrowserKey`, `ReaderKey(tabId)`, `TabsKey(incognito, focusTabId?)`, `SettingsKey`, `AiPrivacyKey`, `HistoryKey`, `BookmarksKey(folderId?)`, `SkimHistoryKey`, `LicensesKey`. Key sheet: `MoreMenuKey(tabId)`, `LinkOptionsKey(tabId, target)`, `SiteInfoKey(tabId)`, `AskPageKey(tabId?, savedChatId?, fromReader)`, `AskTabsKey(prompt?, savedChatId?)`. Key dialog: `AiPermissionKey(tabId, fromReader)`, `ShortcutDialogKey(shortcutId?)`, `SearchEngineDialogKey`, `ThemeDialogKey`, `ClearBrowsingDataKey`, `DeleteAiDataKey`, `ClearSkimHistoryKey`, `RenameGroupKey(groupId: Long)`, `EditBookmarkKey(bookmarkId: Long)`, `MoveToFolderKey(bookmarkId: Long)`. 08b là panel trong Reader (phase 10).
- Khởi động: lần đầu → 01; các lần sau → Browser với tab cuối. Back stack khôi phục sau process death được ưu tiên, sau khi đã đối soát với tab còn tồn tại. `restore()` lỗi/quá hạn → mở Browser với tab New tab (không bao giờ màn trống).
- 01: Get started → 03; Terms of Service/Privacy Policy mở trong tab, Back quay về 01; Back hệ thống rời app.
- 03: Set as default → hộp thoại role (API 29+) hoặc Android default apps (26–28); khi Skim đã là default nút thành "Done" bị tắt (kiểm lại ở mỗi `onResume`); Choose file → picker `text/html` → snackbar "Imported N bookmarks" + View (`ViewImported(folderId)`) hoặc snackbar lỗi 4 s; Start browsing → 04, onboarding rời khỏi back stack; Back hệ thống → 01.
- Rule "Sheets and menus": sheet đóng khi vuốt xuống, chạm scrim, Back (scene strategy); menu đóng khi chạm ngoài hoặc Back.
- Rule "Snackbars": một host trong cửa sổ chính, đặt theo `placement` của màn trên cùng (8dp trên toolbar ở màn có toolbar, 16dp trên nav bar ở màn khác), 10 s/4 s, một tại một thời điểm, host rời composition = đóng.
- Theme toàn app theo cài đặt Theme; bề mặt incognito luôn tối + FLAG_SECURE; icon status bar đúng độ tương phản.
- Link VIEW http/https từ app khác mở tab thường mới ở foreground, đánh dấu `openedByExternalApp`; Back ở trang đầu đóng tab và trả về app gọi. Không bao giờ mở link ngoài vào incognito. Tạo lại Activity hoặc mở lại từ launcher không mở lại link cũ.
- `AiFabMode` dùng chung cho mọi toolbar: tắt trong incognito, tắt khi tab có lỗi tải (11), còn lại bật.
- Mở tab incognito khi WebView không hỗ trợ `MULTI_PROFILE` → snackbar `incognito_unavailable` `[DEFAULT — chờ validate]` D8, không mở tab.

Phi chức năng:
- Không reflection cho key (R8): `SerializersModule` liệt kê mọi subclass.
- Theme, FLAG_SECURE (khi bật), status bar đổi trong cùng frame với điều hướng.

## Architecture

```kotlin
@Serializable sealed interface SkimKey : NavKey { val placement: SnackbarPlacement get() = SnackbarPlacement.Bottom; val tabId: String? get() = null }
@Serializable data object BrowserKey : SkimKey { override val placement get() = SnackbarPlacement.AboveToolbar }
@Serializable data class TabsKey(val incognito: Boolean, val focusTabId: String? = null) : SkimKey { /* AboveToolbar */ }
@Serializable data class AskPageKey(override val tabId: String? = null, val savedChatId: Long? = null, val fromReader: Boolean = false) : SkimKey
@Serializable data class EditBookmarkKey(val bookmarkId: Long) : SkimKey
// ... đủ danh sách ở Requirements; key có tab khai báo `tabId` để bước đối soát lọc được
val skimNavConfig = SavedStateConfiguration { serializersModule = SerializersModule {
    polymorphic(NavKey::class) { subclass(WelcomeKey::class, WelcomeKey.serializer()) /* … mọi key … */ } } }
fun reconcileBackStack(keys: List<NavKey>, tabs: TabsState): List<NavKey>   // thuần; bỏ key có tabId mất, TabsKey(true) → TabsKey(false) khi không còn tab incognito
```

```kotlin
class SkimNavigator(private val backStack: NavBackStack<NavKey>, private val tabs: TabManager, private val incognito: IncognitoSession,
                    private val secure: WindowSecureController, private val snackbar: SnackbarController) {
    fun push(key: SkimKey); fun back(); fun replaceTop(key: SkimKey); fun remove(key: SkimKey); fun finishOnboarding()
    fun showBrowser(); fun showTab(tabId: String); fun openNewTab(incognito: Boolean, url: String? = null)
    fun openInBackground(url: String, incognito: Boolean): String?
    fun openTabs(incognito: Boolean, focusTabId: String? = null); fun openReader(tabId: String)
    fun openMoreMenu(tabId: String); fun openLinkOptions(tabId: String, target: HitTarget); fun openSiteInfo(tabId: String)
    fun openAskPage(tabId: String, fromReader: Boolean = false); fun openSavedChat(chatId: Long); fun openAskTabs(prompt: String? = null); fun openSavedTabsChat(chatId: Long)
    fun openSettings(); fun openAiPrivacy(); fun openHistory(); fun openBookmarks(folderId: Long? = null); fun openSkimHistory(); fun openLicenses()
    fun openDialog(key: SkimKey)
}
```

- `showTab`/`openNewTab(incognito = true)`/`openTabs(incognito = true)` và mọi push key của tab incognito gọi `secure.setSecure(true)` trước khi đổi tab hoặc back stack. `openNewTab(incognito = true)` khi `!incognito.isSupported` chỉ hiện snackbar D8. `remove(key)` gỡ đúng key đó (Reader dùng để tự gỡ, phase 10).
- `SkimNavDisplay(backStack, navigator, container)`: `NavDisplay(backStack, onBack = navigator::back, sceneStrategies = listOf(DialogSceneStrategy(), BottomSheetSceneStrategy()), entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), entryProvider = entryProvider { … })`. Phase này đăng ký `WelcomeKey`, `SetupKey`, `BrowserKey`; mỗi phase sau thêm entry của mình vào cùng khối (thực thi tuần tự).
- `BottomSheetSceneStrategy`: copy recipe Nav3 (giữ header Apache-2.0), dùng `SkimBottomSheet` của phase 2; metadata `bottomSheet(skipPartiallyExpanded: Boolean = true)`.

Trạng thái app (`ui/shell/SkimApp.kt`):

```kotlin
@Composable fun SkimApp(container: AppContainer, host: BrowserHost, pendingLinks: ReceiveChannel<String>) {
    // 1. Chờ settings + tabManager.restore() (produceState); kết quả Fallback hoặc lỗi trong container.lastError → vẫn vào Browser với tab New tab.
    // 2. backStack = rememberNavBackStack(skimNavConfig, *(if (onboardingCompleted) arrayOf(BrowserKey) else arrayOf(WelcomeKey)))
    //    rồi một lần: backStack.replaceAll(reconcileBackStack(backStack, tabs.state.value)).
    // 3. secureTarget = mọi entry đang hiển thị: màn hình trên cùng là BrowserKey/ReaderKey của tab incognito hoặc TabsKey(true),
    //    hoặc bất kỳ sheet/dialog nào phía trên có tabId của tab incognito.
    // 4. SecureFlagEffect(secureTarget): bật ngay (SideEffect); tắt sau khi transition thoát xong (dò transition của NavDisplay
    //    [UNVERIFIED: API quan sát transition của Nav3]; dự phòng: trễ 600 ms, huỷ nếu secureTarget bật lại).
    //    API 33+: setRecentsScreenshotEnabled(false) khi tabs có ít nhất một tab incognito.
    // 5. dark = settings.theme.isDark(isSystemInDarkTheme(), incognitoSurface); SystemBarsEffect(dark)
    // 6. SkimTheme(settings.theme, incognito = incognitoSurface) { SkimNavDisplay(...); SkimSnackbarHost(controller, topScreenKey.placement, onAction = ::interpret) }
    // 7. LaunchedEffect: for (url in pendingLinks) { tabs.newTab(incognito = false, url = url, openedByExternalApp = true); navigator.showBrowser() }
}
```

- `interpret(action)`: `SwitchToTab(id)` → `navigator.showTab(id)`; `Undo(token)` → `container.undoRegistry.undo(token)`; `EditBookmark(id)` → `navigator.openDialog(EditBookmarkKey(id))`; `OpenDownload(uri, mime)` → `startActivity(Intent(ACTION_VIEW).setDataAndType(uri, mime).addFlags(FLAG_GRANT_READ_URI_PERMISSION))` (bắt `ActivityNotFoundException`); `ViewImported(folderId)` → `navigator.openBookmarks(folderId)`.
- `MainActivity`: `private val pendingLinks = Channel<String>(Channel.BUFFERED)`; `onCreate` (chỉ khi `savedInstanceState == null`) và `onNewIntent` gửi `IncomingIntents.urlFrom(intent)` vào channel; `setContent { CompositionLocalProvider(LocalBrowserHost provides host) { SkimApp(container, host, pendingLinks) } }`.

`BrowserRoute` (khung tab hiện tại, dạng slot):

```kotlin
@Composable fun BrowserRoute(container: AppContainer, navigator: SkimNavigator,
    newTabPage: @Composable (Tab) -> Unit, page: @Composable (Tab, TabRuntime) -> Unit)
```

- Không có tab hiện tại → `tabs.newTab(incognito = false)`.
- `BackHandler`: `runtime.canGoBack` → `goBack`; tab `openedByExternalApp` → đóng tab ngay (không Undo) + `activity.moveTaskToBack(true)`; tab `closeOnBack` → đóng tab + `navigator.back()` (về 01); tab `fromNewTabPage` → `tabs.returnToNewTabPage(tab.id)` (quyết định nhỏ của planner); còn lại không chặn (Back rời app). Find bar, ô tìm kiếm, fullscreen tự đăng ký `BackHandler` bên trong nên được ưu tiên.
- Phase này đăng ký `page = { tab, _ -> WebViewHost(tab.id) }` và `newTabPage = {}`; phase 6 thay `newTabPage`, phase 7 thay `page` (chỉ sửa `SkimNavDisplay.kt`).

`AiFabMode` (`ui/shell/AiFabMode.kt`): `enum { Enabled, OffInIncognito, UnavailableUntilLoaded }`, `fun aiFabMode(tab: Tab, runtime: TabRuntime)` và `@Composable fun aiFabContentDescription(mode, isNewTabPage)` trả `ask_skim` (04), `cd_ask_skim_about_page`, `cd_ask_skim_off_incognito`, `cd_ask_skim_unavailable`. Wrapper dùng chung `@Composable fun TabAiFab(tab: Tab, runtime: TabRuntime, onClick: () -> Unit)` tính mode rồi gọi `AiFab(enabled = mode == Enabled, contentDescription = aiFabContentDescription(mode, tab.url == null), onClick)`; mọi toolbar (04, 05, 05c, 08, 09c, 11) dùng wrapper này.

Onboarding:
- `WelcomeScreen(onGetStarted, onOpenLegal: (url) -> Unit, termsUrl, privacyUrl)`: bố cục theo scout §2 01 (logo `SunnyMark(brand = true)` 36 + wordmark `headlineSmall` 600; art 300×240 ba `ShapeArt`, vị trí đọc từ `design/html/01-welcome.html`; headline `displaySmallEmphasized`; body `bodyLarge onSurfaceVariant` max 340; nút M 56; dòng pháp lý `bodySmall` với hai `LinkAnnotation.Clickable` màu `primary` 500). URL rỗng → chữ thường.
- `SetupScreen(state: SetupUiState, onSetDefault, onChooseFile, onStartBrowsing)`: bố cục scout §2 03.
- `SetupViewModel(defaultBrowser, importer, settings, snackbar)`: `isDefault` (cập nhật ở `ON_RESUME`), `import(uri)` → `ImportResult` → snackbar.
- `system/DefaultBrowser.kt`: `isDefault(context)` (29+: `RoleManager.isRoleHeld(ROLE_BROWSER)`; 26–28: `resolveActivity(ACTION_VIEW https://example.com, MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName`); `requestIntent(context): Intent` (29+ và role khả dụng, chưa giữ: `createRequestRoleIntent`; còn lại `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`, có kiểm `resolveActivity`, dự phòng `Settings.ACTION_SETTINGS`).
- `data/importer/NetscapeBookmarkParser.kt` (Jsoup, theo researcher B §B10; chỉ lấy `href` http/https; trả `null` nếu không phải bookmark export) và `data/importer/BookmarkImporter.kt` (đọc `OpenableColumns.DISPLAY_NAME`, giới hạn 10 MB, parse trên `Dispatchers.Default`, gọi `BookmarkRepository.importTree`).

Luồng dữ liệu onboarding: picker → `Uri` → `BookmarkImporter` → `ImportedNode` → Room → `ImportOutcome(folderId, count)` → `snackbar.show(plural snackbar_imported_bookmarks, ViewImported(folderId))`.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimKeys.kt`, `SkimNavSerialization.kt`, `BackStackReconcile.kt`, `BottomSheetSceneStrategy.kt`, `SkimNavigator.kt`, `SkimNavDisplay.kt` (cùng thư mục `ui/navigation/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt`, `SystemBarsEffect.kt`, `SecureFlagEffect.kt`, `SnackbarActions.kt` (hàm `interpret`), `AiFabMode.kt`, `IncomingIntents.kt` (cùng thư mục `ui/shell/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/browser/BrowserRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/WelcomeScreen.kt`, `SetupScreen.kt`, `SetupViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/system/DefaultBrowser.kt`
- `app/src/main/java/com/dantech/skimbrowser/data/importer/NetscapeBookmarkParser.kt`, `BookmarkImporter.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/ui/navigation/SkimKeySerializationTest.kt`, `SkimNavigatorTest.kt`, `BackStackReconcileTest.kt`; `app/src/test/java/com/dantech/skimbrowser/ui/shell/AiFabModeTest.kt`, `IncomingIntentsTest.kt`, `SecureTargetTest.kt` (tính secureTarget từ back stack: sheet 05b/05d/Site info trên tab incognito, Reader incognito, 09b); `app/src/test/java/com/dantech/skimbrowser/data/importer/NetscapeBookmarkParserTest.kt`; `app/src/test/java/com/dantech/skimbrowser/ui/onboarding/SetupViewModelTest.kt`, `OnboardingScreenshotTest.kt` (gọi `captureBoard("01-welcome")`, `captureBoard("03-default-browser-import")`, `assertA11y()`)
- Fixture: `app/src/test/resources/bookmarks/chrome-export.html` (export Netscape có thư mục con, entity, link `javascript:` phải bị bỏ), `app/src/test/resources/bookmarks/not-bookmarks.html`
- Instrumented: `app/src/androidTest/java/com/dantech/skimbrowser/ui/shell/SecureFlagTest.kt`, `BackStackRestoreTest.kt`, `ExternalLinkTest.kt` (cùng thư mục `androidTest/.../ui/shell/`)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `undoRegistry`, `snackbarController`, `bookmarkImporter`.
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt`: `pendingLinks` channel, `setContent` với `LocalBrowserHost` + `SkimApp`.

## Implementation steps

1. `SkimKeys.kt` + `SkimNavSerialization.kt`; `SkimKeySerializationTest` encode/decode mọi key (kể cả `LinkOptionsKey` có `HitTarget`) bằng `Json { serializersModule = … }` với `PolymorphicSerializer(NavKey::class)`.
2. `BackStackReconcile.kt` + test (tab incognito mất, tab thường bị purge, `TabsKey(true)` không còn tab incognito, `ReaderKey` của tab còn sống được giữ).
3. `BottomSheetSceneStrategy.kt` từ recipe Nav3, dùng `SkimBottomSheet`; `OverlayScene` gọi `onBack` khi sheet đóng.
4. `SkimNavigator` + `SkimNavigatorTest` (back stack giả `NavBackStack`, `FakeTabEngine`, `WindowSecureController` giả ghi thứ tự): `finishOnboarding` để lại đúng `[BrowserKey]`; `showTab(incognitoTab)` gọi `setSecure(true)` trước khi `select`; `openNewTab(incognito = true)` khi không hỗ trợ chỉ hiện snackbar; `remove(key)` chỉ gỡ đúng key.
5. `SkimNavDisplay` với ba entry của phase này; `BrowserRoute` như Architecture.
6. `SkimApp` + `SystemBarsEffect` (gọi `ComponentActivity.enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }, SystemBarStyle.auto(lightScrim, darkScrim) { dark })` trong `DisposableEffect(dark)`), `SecureFlagEffect`, `SnackbarActions.interpret`, host snackbar theo `placement`.
7. `IncomingIntents.urlFrom(intent)`: chỉ `ACTION_VIEW` với `data.scheme` http/https; bỏ qua extras; test Robolectric với `http`, `https`, `file`, `javascript`, `intent`.
8. `AiFabMode` + `TabAiFab` + test.
9. Onboarding: `WelcomeScreen`, `SetupScreen`, `SetupViewModel`, `DefaultBrowser`, `NetscapeBookmarkParser`, `BookmarkImporter`. Kết quả role dùng `rememberLauncherForActivityResult(StartActivityForResult())`; picker dùng `OpenDocument()` với `arrayOf("text/html")`; mở link pháp lý: `tabs.newTab(incognito = false, url = url, closeOnBack = true)` rồi `navigator.push(BrowserKey)`.
10. Ảnh Roborazzi 01, 03 (chưa default), 03 (Done tắt) với `PHONE_QUALIFIERS`; duyệt so với PNG, ghi lệch vào `docs/design-guidelines.md`.
11. Instrumented:
    - `SecureFlagTest`: mở tab incognito qua navigator → ngay sau lời gọi `window.attributes.flags and FLAG_SECURE != 0`; mở 05b, 05d, Site info trên tab incognito → cờ vẫn bật; đổi segment 09b → 09 → cờ còn bật trong lúc transition và tắt sau khi xong; API 33+: Recents screenshot tắt khi còn tab incognito (`assumeTrue(MULTI_PROFILE)`, phải chạy thật trên `skim_api36`).
    - `BackStackRestoreTest`: `ActivityScenario.recreate()` khi đang mở một sheet key → sheet còn; dựng lại với tab của sheet đã bị xoá → key bị lọc, không crash.
    - `ExternalLinkTest`: gửi `ACTION_VIEW` tới trang MockWebServer → Back → tab bị đóng, Activity xuống nền; sau đó `recreate()` và `finish()` + mở lại từ launcher trong cùng process → số tab không đổi.
12. Cổng thiết bị (quy trình chung ở `plan.md`): `connectedDebugAndroidTest` cho package `ui.shell` trên `skim_api36` và `skim_api28`; kiểm thủ công Set as default (API 29+ trên `skim_api36`, Android default apps trên `skim_api28`).
13. Commit cuối phase: `feat(shell): add navigation, app shell and onboarding`.

## Todo list

- [x] Toàn bộ key (tham số tường minh) + SerializersModule + test round-trip
- [x] Đối soát back stack sau restore + test
- [x] BottomSheetSceneStrategy (recipe) + DialogSceneStrategy trong NavDisplay (rule Sheets and menus)
- [x] SkimNavigator đủ hàm điều hướng (kể cả `remove`) + test
- [x] SkimApp: khởi động có fallback, back stack lưu được, theme/incognito/status bar
- [x] FLAG_SECURE theo mọi entry hiển thị, tắt sau transition, Recents API 33+
- [x] Host snackbar + dịch `SnackbarAction` (rule Snackbars)
- [x] BrowserRoute (slot NTP/page, Back cho tab app khác mở, tab closeOnBack, tab fromNewTabPage)
- [x] AiFabMode + TabAiFab + test
- [x] Link VIEW từ app khác qua Channel của Activity + test không mở lại
- [x] 01 Welcome (link pháp lý mở tab, Back quay về)
- [x] 03 Setup: default browser (role/settings, recheck onResume), import bookmarks + snackbar, Start browsing
- [x] Ảnh Roborazzi 01/03 + test ViewModel + instrumented
- [x] Cổng thiết bị `skim_api36` + `skim_api28`
- [x] Commit cuối phase

## Success criteria

- Mọi row của 01 và 03 trong navigation-map.md chạy đúng (coverage-matrix.md); row 05 "Back on the first page" và system UI "Default browser", "Bookmarks file", "Links from other apps" chạy đúng.
- Rule "Sheets and menus", "Snackbars", "Incognito" (dark scheme + FLAG_SECURE), "AI FAB disabled", "Process death: open sheets" đạt: `SecureFlagTest` (chạy thật trên `skim_api36`), `AiFabModeTest`, `BackStackRestoreTest`, `SnackbarControllerTest` xanh.
- Snackbar 15 "Imported N bookmarks" (View) và "Couldn't read that file…" (4 s, không action, alert) xuất hiện đúng điều kiện.
- Lần mở app thứ hai đi thẳng vào tab cuối; 01/03 không còn trong back stack sau Start browsing; link ngoài không bị mở lại khi tạo lại Activity.

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.navigation.*' --tests 'com.dantech.skimbrowser.ui.shell.*' --tests 'com.dantech.skimbrowser.ui.onboarding.*' --tests 'com.dantech.skimbrowser.data.importer.*'
./gradlew :app:recordRoborazziDebug --tests '*OnboardingScreenshotTest*' && ./gradlew :app:verifyRoborazziDebug
# Cổng thiết bị (plan.md): mỗi AVD
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.dantech.skimbrowser.ui.shell
adb -s emulator-5580 shell am start -a android.intent.action.VIEW -d https://developer.android.com com.dantech.skimbrowser
```

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| `rememberNavBackStack` khôi phục được khi gọi sau khi start state đã load | Sau process death quay về 01/Browser gốc thay vì sheet | Đọc start state một lần từ DataStore trước `setContent` (DataStore `first()` trên IO, Activity chờ bằng `produceState`); kiểm ở phase 13 |
| Recipe BottomSheet tương thích Nav3 1.2.0 | Lỗi compile/hành vi đóng sheet | Đối chiếu recipe ở skill `navigation-3` (`bottomsheet.md`) với API 1.2.0 |
| Quan sát được lúc transition của NavDisplay kết thúc `[UNVERIFIED]` | Cờ tắt sớm/muộn | Dự phòng trễ 600 ms (đã thiết kế); test kiểm cờ còn bật ngay sau khi đổi segment |
| `moveTaskToBack` đưa người dùng về app gọi | Về launcher thay vì app gọi | Chấp nhận (hành vi giống Chrome với `singleTask`); ghi nhận |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 5 (thay `setContent` về màn trống của phase 1; key/navigator là code mới).

## Security considerations

- Link từ app khác: chỉ nhận http/https từ `intent.data`, bỏ mọi extra; luôn mở tab thường; nhận đúng một lần.
- FLAG_SECURE bật trước khi bất kỳ nội dung incognito nào được vẽ (kể cả dưới sheet/dialog), chỉ tắt sau khi frame incognito cuối biến mất; Recents bị tắt chụp khi còn tab incognito (API 33+).
- Key của tab incognito có thể nằm trong saved state tới lần khởi động sau; bước đối soát bỏ chúng ngay khi khôi phục.
- Import: giới hạn 10 MB, chỉ nhận link http/https, parse ngoài main thread; file lỗi chỉ báo snackbar.

## Next steps

- Phase 6 thay slot `newTabPage` và đăng ký entry dialog shortcut; phase 7 thay slot `page`; phase 10 dùng `navigator.remove(ReaderKey)`; phase 11 sửa `openAskPage` và `SkimNavigatorTest` khi constructor đổi.

## Kết quả thực thi (2026-10-22)

Commit: `feat(shell): add navigation, app shell and onboarding`. Làm theo hai lượt tuần tự, mỗi lượt do một agent làm rồi controller kiểm chứng độc lập trước khi sang lượt sau: lượt code (khóa điều hướng, `SkimNavigator`, `SkimApp`, FLAG_SECURE, link từ app khác, onboarding 01/03, import bookmark, golden 01/03) và lượt thiết bị (năm lớp test thiết bị mới dùng `MainActivity` thật qua một rule chung, cổng `skim_api36` rồi `skim_api28`). Một reviewer độc lập đọc toàn bộ bề mặt bảo mật của phase: 5 phát hiện (0 Critical, 0 High, 2 Medium, 3 Low), 4 đã sửa kèm test, 1 chuyển vào giới hạn đã biết. Báo cáo từng lượt: `plans/reports/fullstack-developer-261022-0759-phase-05-shell-main.md`, `plans/reports/code-reviewer-261022-0814-phase-05-review.md`, `plans/reports/fullstack-developer-261022-1157-phase-05-shell-device.md`.

Kiểm chứng cuối phase: `testDebugUnitTest` 1035 test (98 lớp), 0 lỗi, 0 bỏ qua; `assembleDebug` và `verifyRoborazziDebug` xanh; `lintDebug` 0 lỗi, đúng 110 cảnh báo cũ, 0 trong file của phase; biên dịch lại không incremental cả ba source set 0 dòng `w:`; ba lượt lặp 180 test của các lớp phase đều xanh. Thiết bị (110 test, 26 test mới): `skim_api36` (Android 16, WebView 133) 107 qua, 3 bỏ qua theo assumption đúng thiết kế, `SecureFlagTest` chạy thật cả 9 test; `skim_api28` (Android 9, WebView 66) 76 qua, 34 bỏ qua kèm lý do đọc được (10 trong số đó là test mới cần incognito); không lượt nào có crash hay ANR; năm lớp mới lặp ba lần trên `skim_api36` đều xanh. 105 đột biến có chủ ý (agent code 66, controller 28, agent thiết bị 11) đều làm đúng test liên quan đỏ; một đột biến của controller (`topScreen` lấy màn đầu thay vì màn trên cùng) lọt qua lần đầu và nay có `SkimKeyHelpersTest`. Golden mới: `boards/01-welcome.png`, `boards/03-default-browser-import.png`, `components/setup-done-03.png`, đã so với `design/screens/` và ghi lệch vào `docs/design-guidelines.md`.

Lỗi thật chỉ lộ khi review hoặc đo (đã sửa, mỗi lỗi có test):
- Một trang incognito có thể tự `window.close()` popup của nó trong lúc Reader hoặc sheet của popup đang hiện; khóa của tab đã đóng bị coi là không incognito nên FLAG_SECURE bị gỡ giữa lúc màn còn hiện, rồi crossfade 700 ms chạy tiếp không có cờ: `showsIncognito` coi tab không còn mở là incognito cho tới khi khóa rời back stack.
- Sau khi renderer chết quá ngân sách, `canGoBack` vẫn `true` dù WebView đã bị huỷ, nên Back không làm gì (tab của app khác không về app gọi, tab pháp lý không về 01): `TabManager.onRendererGone` đặt `canGoBack` và `canGoForward` về `false` khi hiện lỗi.
- Một ngoại lệ không phải IO từ provider tài liệu của app khác (`IllegalArgumentException`, `IllegalStateException`, `UnsupportedOperationException`) thoát khỏi `BookmarkImporter.import`: không có snackbar lỗi, và message (có thể chứa URI) bị handler của `appScope` ghi log. `import` bắt mọi `RuntimeException` và để `CancellationException` đi qua.
- Tệp bookmark 10 MB toàn thẻ (khoảng 440.000 mục) làm Jsoup giữ 119 đến 148 MB heap (đo bằng JVM thật) trước khi mã duyệt cây chạy: `NetscapeBookmarkParser.hasTooManyTags` đếm thẻ trước khi parse, trần `MAX_TAGS = 250_000` (khoảng 80.000 bookmark).
- Lượt thiết bị không tìm thấy lỗi nào của mã sản phẩm phase 5; ba chập chờn của test phase 4 lộ ra và đã sửa trong test: Chromium (WebView 133) đôi khi biến nhấn giữ link thành kéo thả nên không có long-click (`longpress.html` đặt `draggable="false"`); trên Android 9 `DownloadManager` từ chối cleartext tùy provider nào khởi động `android.process.media` (`DeviceDownloads` khởi động lại process từ DownloadProvider); WebView 66 thỉnh thoảng bỏ qua cả một cú kéo (`DownloadLimitsDeviceTest` kéo lại tối đa 3 lần).

Giả định đã xác nhận trên thiết bị (`skim_api36`): FLAG_SECURE bật ngay trong task main thread mở tab incognito, và một `OnDrawListener` kiểm từng frame không thấy frame nào có trang incognito mà thiếu cờ (mở tab, Back, predictive back, crossfade 700 ms, đóng tab incognito cuối); giữ cờ theo entry là đủ, không cần trễ 600 ms (đột biến bỏ phần giữ làm lộ ngay các frame không có cờ); sheet của tab incognito giữ cờ kể cả trên tab thường; Recents (API 33+) không còn ảnh thật của task khi còn tab incognito (`dumpsys window`, `mIsRealSnapshot`); ảnh chụp màn hình vùng nội dung đen khi tab incognito hiện; back stack và sheet khôi phục đúng sau `recreate()` và đã đối soát với tab còn mở; link từ app khác mở đúng một lần qua `recreate`, launcher và Recents; Back ở trang đầu của tab đó đóng hẳn tab (không Undo) và đưa task xuống nền.

Chữ ký thực tế khác spec (đã chấp nhận; chi tiết trong báo cáo lượt code):
- `SkimKey` là `@Serializable sealed interface` có `placement` và `tabId`, hai nhánh `SheetKey` và `DialogKey`, 26 khóa đều có trong `skimNavConfig` (test bắt khóa thiếu); `reconcileBackStack` còn xoá `focusTabId` của `TabsKey` khi tab đó không còn và không bao giờ trả danh sách rỗng.
- `SkimNavigator(backStack, tabs, incognito, secure, snackbar)` gọi trên main thread: `push`, `back`, `replaceTop`, `remove`, `finishOnboarding`, `showBrowser`, `showTab`, `openNewTab`, `openInBackground`, `openTabs`, các `openXxx` cho từng đích và `openDialog(key: DialogKey)`; `secureArming` cho shell và `keysForTests` cho test thiết bị. `push` bỏ qua khóa đã ở trên cùng, `openTabs` thay màn Tabs đang mở, `showTab` của tab không còn mở thì không làm gì.
- `bottomSheet(@StringRes paneTitle, skipPartiallyExpanded = true)` thêm `paneTitle` bắt buộc (TalkBack đọc khi sheet mở) và `LocalSkimSheetState`; khóa chưa có màn hiện entry rỗng thay vì crash.
- `SkimApp(container, host, pendingLinks, onNavigator)`; `MainActivity` có `pendingLinks`, `onNewIntent` và `navigatorForTests`; `AppContainer` thêm `undoRegistry`, `snackbarController`, `bookmarkImporter`.
- FLAG_SECURE: `showsIncognito`, `secureTarget`, `SecureFlagEffect` và `rememberSecureEntryDecorator` (mỗi entry hiện nội dung incognito giữ cờ tới khi nội dung rời composition); chủ cờ `incognito-surface` (navigator bật trước khi đổi), `incognito-tabs` (Recents) và một chủ cho mỗi entry.
- `SetupViewModel(isDefaultBrowser, importer, settings, snackbar, appScope)` thêm `appScope`, vì Start browsing gỡ khóa Setup và view model bị huỷ cùng entry; trang crash vượt ngân sách hiện `PageStoppedWorking` tối thiểu trong slot `page`, chờ phase 7.
- Import: tiêu đề và tên tệp thành chữ hiển thị thuần (bỏ bidi, zero-width), URL dài hơn 8.192 ký tự bị bỏ, trần 10 MB và 250.000 thẻ; tệp có cấu trúc bookmark nhưng thiếu doctype vẫn được nhận. Mọi lỗi dùng chung một snackbar.
- Test thiết bị không dùng compose test rule (nó thay frame clock của mọi cửa sổ và làm sai các phép đo thời gian thật); Recents kiểm bằng `dumpsys window`; import kiểm qua `SetupViewModel` với tài liệu do `FileProvider` phục vụ, picker thật kiểm tay.

Việc để các phase sau:
- Phase 6: thay slot `newTabPage` của `SkimNavDisplay` (hiện `{}`) và đăng ký entry `ShortcutDialogKey`; `TabAiFab(tab, runtime, onClick)` là AI FAB dùng chung cho 04, 05, 05c, 08, 09c, 11; `openAskTabs`, `openSavedChat`, `openSkimHistory` đã có.
- Phase 7: thay slot `page`; sheet đóng bằng code phải đi qua `LocalSkimSheetState` (`hide()`) để có hoạt ảnh, vì `navigator.back()` làm sheet biến mất ngay; sheet và dialog của tab incognito dùng `SecureFlagPolicy.Inherit` (cửa sổ riêng của sheet lấy cờ từ cửa sổ Activity), cần kiểm bằng ảnh chụp màn hình thật khi sheet có nội dung và cân nhắc `SecureOn`; snackbar mang chữ từ ngữ cảnh incognito (ví dụ "%s downloaded") hiện trên màn thường 10 giây thì ảnh chụp màn hình thấy chữ đó, nên dùng chữ chung hoặc giữ cờ trong lúc nó hiện; URL của link, ảnh và bookmark giữ nguyên chuỗi gốc (chỉ kiểm scheme và host) nên path có thể chứa bidi hoặc ký tự điều khiển: hiển thị host hoặc URL đã làm sạch; nhấn giữ link có thể thành kéo thả của Chromium (WebView 133) và khi đó không có long-click, quyết định có coi lúc bắt đầu kéo như long-press (mở 05d) không; entry của khóa có `tabId` phải tự thoát khi tab đóng (trang có thể tự đóng popup của nó), FLAG_SECURE được giữ tới khi khóa rời back stack.
- Phase 8 đến 11: khóa trỏ tới nhóm, bookmark, thư mục, chat hoặc shortcut không được đối soát (chỉ lọc theo tab), nên entry phải tự xử lý đối tượng không còn (đóng hoặc Back).
- Phase 10: dùng `navigator.remove(ReaderKey)`. Phase 11: gọi `bottomSheet(R.string.ai_ask_about_page, skipPartiallyExpanded = false)`; sửa `openAskPage` và `SkimNavigatorTest` khi constructor đổi.
- Phase 13: test "mọi `SkimKey` có entry"; kiểm process death thật (`am kill`) cho back stack; design review ba điểm lệch của phần chuyển màn hình (bên dưới); TalkBack bằng tay trên bản có URL pháp lý (hai link và snackbar lỗi); đếm skip bằng `AssumptionViolatedException:`.

Giới hạn đã biết:
- Khi Skim là trình duyệt mặc định, một trang ở tab thường có thể dùng link `intent://…#Intent;scheme=https;end` để Skim tự mở chính nó thành tab nền trước mang cờ `openedByExternalApp` (Back ở trang đầu đóng tab không Undo và đẩy Skim xuống nền); không thể xảy ra ở tab incognito. Chưa sửa vì cách xử lý (nạp URL ngay trong tab hiện tại, như Chrome) là quyết định sản phẩm.
- Cờ `openedByExternalApp` và `closeOnBack` được lưu trong Room, nên Back ở trang đầu của tab đó vẫn đóng hẳn tab nhiều ngày sau.
- Link nhận trong lúc chờ khởi động mất nếu Activity bị tạo lại trước khi `LaunchedEffect` lấy nó ra (hiếm vì `configChanges` đã khai báo gần hết).
- Snackbar ở 03 (vị trí Bottom, 16 dp trên thanh điều hướng) che nút Start browsing trong 4 đến 10 giây; thiết kế không vẽ vị trí snackbar cho màn không có toolbar `[DEFAULT — chờ validate]`.
- Back đóng tab cuối của link ngoài mở New tab ngay (trước khi Activity dừng); với dữ liệu mới, link đến mở trên Welcome và lần mở sau vào Browser trên New tab, không quay về Welcome.
- API 28 đến 32 không có API công khai ẩn ảnh Recents (`setDisablePreviewScreenshots` không có trong SDK công khai nào), nên ở đó chỉ FLAG_SECURE khi nội dung incognito đang hiện bảo vệ; từ API 33 `setRecentsScreenshotEnabled(false)` ẩn ảnh suốt lúc còn tab incognito.
- Ảnh chụp màn hình khi tab incognito hiện: vùng nội dung của app đen, icon status bar và thanh cử chỉ vẫn thấy vì thuộc cửa sổ SystemUI.
- Trên Android 9, tải http qua `DownloadManager` có thể bị từ chối (`Cleartext HTTP traffic … not permitted`) tùy provider nào khởi động `android.process.media`; tải https không bị ảnh hưởng.
- Trên ảnh hệ thống `skim_api28`, link web đi tới `setupwizard/.util.WebDialogActivity` (priority 5) nên Skim không thành trình duyệt mặc định ở đó dù đã chọn trong Default apps; cần máy Android 8 hoặc 9 thật để kiểm trọn nhánh này.
- Process death thật chưa kiểm (chỉ `recreate()`); TalkBack chưa kiểm được qua adb.
- Settings đọc lỗi IO được coi như lần chạy đầu; chọn cùng một tệp bookmark hai lần tạo hai thư mục Imported.

Known deviations (đã chấp nhận): chuyển màn hình là crossfade 700 ms mặc định của Navigation 3 chứ không phải `slowSpatialSpec` của tokens.md (không có `transitionSpec` riêng); theme đổi cùng frame điều hướng nên màn đang mờ đi mang scheme mới (rời 09b sang 09, 09b sáng lên trong lúc mờ); sheet đóng bằng code biến mất ngay không có hoạt ảnh (recipe không cài `OverlayScene.onRemove`; vuốt, chạm scrim và Back vẫn có); FLAG_SECURE giữ bằng decorator theo entry thay cho trễ 600 ms vì Navigation 3 1.2.0 không có API công khai báo transition xong ở cấp `NavDisplay`; khóa của tab đã đóng tính là incognito tới khi rời back stack (bảo thủ); test serializer dùng descriptor sealed do plugin sinh thay cho `kotlin-reflect`; sửa ba test và fixture của phase 4 (danh sách lỗi ở trên).
