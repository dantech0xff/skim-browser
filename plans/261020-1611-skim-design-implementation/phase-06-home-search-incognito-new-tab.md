---
phase: 6
title: "Home, search & incognito new tab"
status: completed
priority: P1
effort: "16h"
dependencies: [5]
---

# Phase 6: Home, search & incognito new tab

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Scout `plans/reports/scout-261020-2319-design-inventory.md` §2 (04, 04b, 09c), §3(d) (Add shortcut, Shortcut menu, snackbar Shortcut removed), §3(e) #3, #5, #12, #13.
- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §B9 (endpoint gợi ý, debounce, huỷ), §B10 (RecognizerIntent).
- `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §4 (`SearchBar`, `ExpandedFullScreenContainedSearchBar`, `rememberSearchBarState`).
- `design/spec/navigation-map.md` mục 04, 04b, 09c; `design/spec/dialogs.md` (Add shortcut, Shortcut menu).
- Red team: `plans/261020-1611-skim-design-implementation/reports/red-team-failure-mode-analyst.md` Finding 8 (Undo chạy trong scope bị huỷ).
- Board: `design/screens/04-home-new-tab.png`, `design/screens/04b-search-suggestions.png`, `design/screens/09c-incognito-new-tab.png`, `design/screens/15-dialogs-menus-snackbars.png`; HTML cùng tên trong `design/html/`.

## Overview

Xây trang New tab cho tab thường (04 Home: logo Sunny, ô tìm kiếm, thẻ hero AI, Shortcuts, Continue with Skim) và cho tab incognito (09c), lớp tìm kiếm 04b (gợi ý engine, Ask Skim, history, bookmark; giọng nói; Enter mở URL hoặc tìm kiếm), dialog Add/Edit shortcut, menu shortcut với Remove + Undo. Thay slot `newTabPage` của `BrowserRoute`.

## Key insights

- 04b mở từ nhiều nơi (04, 09c, địa chỉ của 05 và 11) nên là một composable `SearchOverlay` dùng chung, mở dạng `ExpandedFullScreenContainedSearchBar`; 04/09c có `SearchBar` thu gọn cho hiệu ứng mở rộng. Mở từ 05/11 dùng `rememberSearchBarState(initialValue = Expanded)` `[UNVERIFIED: hành vi khi không có thanh thu gọn]`; nếu hiệu ứng sai trên thiết bị thì vẽ cùng nội dung trong `Dialog(usePlatformDefaultWidth = false)` toàn màn hình.
- Incognito (09c → 04b): tối, không hàng history, không hàng Ask Skim (navigation-map 09c). Gợi ý từ engine vẫn có (quyết định nhỏ của planner, giống trình duyệt phổ biến).
- Endpoint gợi ý không có tài liệu công khai: lỗi hoặc rỗng thì ẩn nhóm gợi ý engine, vẫn giữ history/bookmark (researcher B rủi ro).
- Ô nhập không được biến scheme nguy hiểm thành điều hướng: `javascript:`, `file:`, `content:`, `intent:`, `data:` gõ vào sẽ được tìm kiếm như văn bản.
- Thẻ hero và "Continue with Skim" hiển thị dữ liệu thật ngay ở phase này; hành vi bấm đi tới đích của phase 11/12 qua `SkimNavigator` (quy ước tích hợp trong `plan.md`).
<!-- Red Team: Undo của shortcut chạy trên appScope — 2026-10-21 -->
- Remove shortcut đăng ký khôi phục vào `UndoRegistry` (process) và hiện snackbar với `SnackbarAction.Undo(token)`, nên Undo vẫn chạy khi người dùng bấm một shortcut khác và Home rời composition.
<!-- Updated: Validation Session 1 - D3: hàng Ask Skim gửi tới 06b, loại site đã tắt Skim AI -->
- Hàng Ask Skim của 04b mở 06b với text làm câu hỏi; 06b chỉ đọc tab thường không thuộc site đã tắt Skim AI (D3, phase 12).

## Requirements

Chức năng:
- 04: Sunny 64 (cd `app_name`, role Image); ô tìm kiếm → 04b với bàn phím; mic → giọng nói rồi đưa text vào 04b (ẩn mic khi không có app nhận giọng nói); thẻ hero "Summarize my N open tabs" (N = tab thường đang mở trang web, không tính trang New tab) → `navigator.openAskTabs(prompt)`; Shortcuts (lưới 4 cột, shape theo vị trí, ô Add viền đứt) — bấm mở site trong tab này, nhấn giữ mở menu, Add mở dialog; Continue with Skim (2 chat PAGE gần nhất) + See all → `navigator.openSkimHistory()`, bấm thẻ → `navigator.openSavedChat(chatId)`; toolbar Back/Forward tắt, Tabs → 09, More → 05b không có page actions; AI FAB → `navigator.openAskTabs()`.
- `[DEFAULT — chờ validate]` D8: lần đầu (chưa có dữ liệu) ẩn thẻ hero khi N = 0, ẩn mục Continue khi chưa có chat, Shortcuts chỉ còn ô Add.
- 04b: nền `surfaceContainerLow`; Close search/Back quay về 04 hoặc 05; Clear text xoá chữ, bàn phím giữ nguyên; gõ → gợi ý engine (tối đa 3), history + bookmark khớp (tối đa 3, history trước, bỏ trùng URL; dòng bookmark dùng layout dòng history với `ic_bookmarks`, chưa vẽ, `[DEFAULT — chờ validate]` D8); hàng Ask Skim → `navigator.openAskTabs(text)`; gợi ý → mở kết quả tìm kiếm của engine mặc định; mũi tên north_west chép gợi ý vào ô; mục history → mở trang; Enter → URL thì mở, còn lại tìm kiếm; mở từ 05/11 thì ô bắt đầu bằng URL được chọn sẵn.
- 09c: thanh tìm kiếm tối (`surfaceContainerHighest`, `ic_visibility_off`, "Search or type URL") → 04b tối; mic → 04b tối; nội dung tĩnh như bản vẽ; toolbar tối Back/Forward tắt, "Incognito tabs, N open" → 09b, More → 05b tối không có page actions; AI FAB tắt.
- Add shortcut dialog (Name, URL; URL focus) Save/Cancel; Edit shortcut mở cùng dialog đã điền, tiêu đề "Edit shortcut" (quyết định nhỏ đã chốt); Save tắt khi URL rỗng; Name rỗng → dùng host.
- Shortcut menu (neo dưới ô, ô chuyển `secondaryContainer` khi mở): nhóm 1 "Edit shortcut" (`ic_edit`), nhóm 2 "Remove" (`ic_delete`); Remove xoá ngay + snackbar "Shortcut removed" + Undo (qua `UndoRegistry`, loại `SHORTCUT`).
- Toolbar nổi trên 04 và 09c (rule "Floating toolbar" cho hai màn này): Back/Forward tắt, Tabs, More, AI FAB.

Phi chức năng:
- Gợi ý: debounce 150 ms, huỷ request cũ khi gõ tiếp, timeout 3 s, parse theo charset của header.
- Nội dung Home cuộn được, chừa đáy bằng chiều cao toolbar + 24 + nav bar để mục cuối không bị che vĩnh viễn.

## Architecture

```kotlin
// search/OmniboxResolver.kt
fun resolveOmnibox(input: String, engine: SearchEngine): String?   // null khi rỗng
// http(s)://… → giữ nguyên; scheme khác → tìm kiếm; không khoảng trắng và giống host (có dấu chấm + nhãn hợp lệ,
// localhost, IPv4, host:port) → "https://" + input; còn lại → engine.searchUrl(input)

// search/SuggestionService.kt
class SuggestionService(client: OkHttpClient) { suspend fun suggestions(engine: SearchEngine, query: String): List<String> }
// search/SuggestionParser.kt
fun parseOpenSearchSuggestions(body: String): List<String>   // phần tử [1] của mảng OpenSearch, bỏ mảng phụ của Google

// ui/search/SearchViewModel.kt
data class SearchUiState(val query: TextFieldValue, val incognito: Boolean, val askSkim: Boolean,
    val engineSuggestions: List<String>, val pageMatches: List<PageMatch>)   // PageMatch(title, host, url, isBookmark)
class SearchViewModel(engineFlow, suggestions: SuggestionService, history: HistoryRepository, bookmarks: BookmarkRepository) {
    fun onQueryChanged(text: String)   // debounce 150 → mapLatest
    fun submitTarget(text: String): String?   // resolveOmnibox
}

// ui/search/SearchOverlay.kt
@Composable fun SearchOverlay(state: SearchBarState, initialText: String, selectAll: Boolean, incognito: Boolean,
    onOpenUrl: (String) -> Unit, onAskSkim: (String) -> Unit, onClose: () -> Unit)

// ui/home
@Composable fun HomeScreen(state: HomeUiState, actions: HomeActions, toolbar: @Composable () -> Unit)
data class HomeUiState(val eligibleTabCount: Int, val shortcuts: List<ShortcutEntity>, val continueChats: List<ContinueCard>, val voiceAvailable: Boolean)
class HomeViewModel(tabs: TabManager, shortcuts: ShortcutRepository, chats: ChatRepository, snackbar: SnackbarController, undo: UndoRegistry)
@Composable fun NewTabPageRoute(tab: Tab, container: AppContainer, navigator: SkimNavigator)   // incognito → IncognitoNewTabScreen, còn lại HomeScreen
```

- `ContinueCard(chatId, host, letter, timeText: UiText, title, snippet)`; thời gian từ `relativeTimeText(updatedAt)` (phase 2).
- Toolbar dùng `BrowserToolbar` + `TabAiFab(tab, runtime, onClick)` (phase 2, 5); FAB ở 04 gọi `navigator.openAskTabs()`, ở 09c tự tắt.
- Mở site từ shortcut/history/gợi ý: `tabs.load(tab.id, url)` trên tab New tab hiện tại; `BrowserRoute` tự đổi sang slot `page` khi `tab.url != null`.
- Voice: `system/VoiceInput.kt` có `isAvailable(context)` (`queryIntentActivities(ACTION_RECOGNIZE_SPEECH)`, dựa vào `<queries>` của phase 1) và `ActivityResultContract<Unit, String?>` với `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_WEB_SEARCH`, trả `EXTRA_RESULTS[0]`.

Luồng dữ liệu tìm kiếm: phím gõ → `SearchViewModel.onQueryChanged` → (debounce) song song `SuggestionService` (mạng) và `HistoryRepository.matches`/`BookmarkRepository.matches` (Room) → `SearchUiState` → overlay. Enter → `resolveOmnibox` → `TabManager.load(tabId, url)` → đóng overlay.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/search/OmniboxResolver.kt`
- `app/src/main/java/com/dantech/skimbrowser/search/SuggestionService.kt`
- `app/src/main/java/com/dantech/skimbrowser/search/SuggestionParser.kt`
- `app/src/main/java/com/dantech/skimbrowser/system/VoiceInput.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/search/SearchOverlay.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/search/SearchViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/HomeScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/NewTabPageRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/ShortcutDialog.kt` (dialog + ViewModel nhỏ)
- `app/src/main/java/com/dantech/skimbrowser/ui/home/ShortcutMenu.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/incognito/IncognitoNewTabScreen.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/search/OmniboxResolverTest.kt`, `app/src/test/java/com/dantech/skimbrowser/search/SuggestionParserTest.kt`, `app/src/test/java/com/dantech/skimbrowser/search/SuggestionServiceTest.kt` (MockWebServer: charset, huỷ, lỗi → rỗng), `app/src/test/java/com/dantech/skimbrowser/ui/search/SearchViewModelTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/home/HomeViewModelTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/home/HomeScreenshotTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/search/SearchScreenshotTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/incognito/IncognitoNewTabScreenshotTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/home/ShortcutOverlaysScreenshotTest.kt`
- Fixture: `app/src/test/resources/suggest/google.json`, `bing.json`, `duckduckgo.json`, `ecosia.json` (định dạng đã ghi nhận ở researcher B §B9)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: slot `newTabPage = { tab -> NewTabPageRoute(tab, container, navigator) }`; entry dialog `ShortcutDialogKey`.
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `suggestionService` (dùng `httpClient` đã tạo ở phase 4).

## Implementation steps

1. `OmniboxResolver` + bảng test: `example.com`, `example.com/path?q=1`, `localhost:8080`, `192.168.1.1`, `http://a.b`, `heat pump vs furnace`, `javascript:alert(1)`, `file:///sdcard`, `intent://x#Intent;end`, `about:blank`, chuỗi rỗng, có khoảng trắng đầu/cuối.
2. `SuggestionParser` + `SuggestionService` (OkHttp `newCall(...).await` bằng `suspendCancellableCoroutine` có `invokeOnCancellation { call.cancel() }`, `callTimeout(3 s)`); test với 4 fixture và MockWebServer.
3. `SearchViewModel` (debounce 150 ms bằng `snapshotFlow`/`Flow.debounce`, `mapLatest`); incognito → `askSkim = false`, không truy vấn history/bookmark.
4. `SearchOverlay`: khung padding 8×16, thanh 56 bo 28 `surfaceContainerHigh` padding 0×4 (Close search `ic_arrow_back` `onSurface`, input `bodyLarge` label ẩn `search_hint`, Clear text `ic_close`), vùng gợi ý `semantics { contentDescription = cd_suggestions }` padding 8 16 0, các nhóm segmented cách 12: nhóm Ask Skim (72, `SunnyMark` 40 + query + nhãn "Ask Skim" 14/20 500 `tertiary`, mâu thuẫn #12), nhóm engine (56, `ic_search` trong ô 40, trailing `ic_north_west` cd `cd_edit_search`), nhóm trang (72, `ic_history`/`ic_bookmarks`, title + host). Nền `surfaceContainerLow`. `selectAll` → `TextFieldValue(selection = TextRange(0, len))`.
5. `HomeViewModel` + `HomeScreen` theo scout §2 04 (padding 24 16 0, gap 24; hero `AiHeroCard`; lưới `LazyVerticalGrid(GridCells.Fixed(4))` gap 16×8 trong cột; Continue: header + `SegmentedColumn` thẻ `surfaceContainerLow` padding 16 gap 8). Long-press shortcut → `ShortcutMenu`; Remove → `val s = shortcuts.remove(id)`; `val t = undo.register(SHORTCUT, undo = { shortcuts.restore(s) })`; `snackbar.show(snackbar_shortcut_removed, SnackbarAction.Undo(t))`.
6. `ShortcutDialog` (dialog key `ShortcutDialogKey(shortcutId?)`): hai `SkimTextField`, focus URL khi mở; Save chuẩn hoá URL bằng `resolveOmnibox` (chỉ nhận kết quả http/https), Name rỗng → `hostOf(url)`.
7. `IncognitoNewTabScreen` theo scout §2 09c (hero `ShapeArt(Cookie12Sided, 72)` `secondaryContainer` + `ic_visibility_off` 36; tiêu đề `headlineMediumEmphasized`; `StaticListItem` 48 trên `surfaceContainer`, mâu thuẫn #5; footnote `bodySmall`). Toàn bộ dưới `SkimTheme(incognito = true)` do shell cấp.
8. `NewTabPageRoute` chọn 04/09c, nối toolbar (`onTabs = navigator.openTabs(tab.incognito)`, `onMore = navigator.openMoreMenu(tab.id)`), FAB (`TabAiFab`), voice launcher, search overlay.
9. Sửa `SkimNavDisplay.kt` và `AppContainer.kt`.
10. Ảnh Roborazzi: 04 với fixture giống board (7 shortcut, 2 thẻ Continue, N = 7; `captureBoard("04-home-new-tab")`), 04 lần đầu (D8), 04b (query "heat pump", 3 gợi ý, 1 history; `captureBoard("04b-search-suggestions")`), 04b incognito, 09c (N = 2; `captureBoard("09c-incognito-new-tab")`), Add shortcut dialog, Shortcut menu; mỗi test gọi `assertA11y()`. Fixture chỉ nằm trong test. Duyệt so với PNG, ghi lệch vào `docs/design-guidelines.md` ("Known deviations").
11. Test Undo: xoá shortcut, huỷ `HomeViewModel` (rời composition), bấm Undo → shortcut trở lại.
12. Commit cuối phase: `feat(home): add home, search overlay and incognito new tab`.

## Todo list

- [x] OmniboxResolver + test bảng đầu vào
- [x] SuggestionParser/Service (4 engine, debounce, huỷ, lỗi → ẩn) + test
- [x] SearchViewModel (incognito không history/Ask Skim) + test
- [x] SearchOverlay 04b (mở từ 04, 09c, 05, 11; chọn sẵn URL)
- [x] VoiceInput (ẩn mic khi không có)
- [x] HomeScreen/ViewModel: Sunny, search bar, hero (ẩn khi N = 0), shortcuts, Continue (ẩn khi rỗng), toolbar, FAB
- [x] Add/Edit shortcut dialog
- [x] Shortcut menu + Remove + snackbar Undo
- [x] IncognitoNewTabScreen 09c
- [x] NewTabPageRoute + thay slot trong SkimNavDisplay
- [x] Ảnh Roborazzi 04, 04 lần đầu, 04b, 04b incognito, 09c, dialog, menu (+ `assertA11y`, lệch ghi vào docs)
- [x] Test Undo shortcut sau khi Home rời composition
- [x] Commit cuối phase

## Success criteria

- Các row của 04 (trừ Summarize, See all, Continue card, Ask Skim: phase 11/12 chịu trách nhiệm end-to-end), toàn bộ row 04b (trừ hàng Ask Skim), các row 09c (trừ Storage và Last incognito tab closes thuộc phase 4) chạy đúng; rule "Floating toolbar" trên 04 và 09c đạt; overlay Add shortcut, Shortcut menu, snackbar Shortcut removed (Undo sau khi rời Home vẫn chạy) đúng dialogs.md.
- Gõ "example.com" + Enter mở `https://example.com` trong tab hiện tại; gõ "heat pump" + Enter mở trang kết quả của engine đang chọn.
- Ảnh Roborazzi đã duyệt so với `04-home-new-tab.png`, `04b-search-suggestions.png`, `09c-incognito-new-tab.png` (lệch đã ghi chú).

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.search.*' --tests 'com.dantech.skimbrowser.ui.search.*' --tests 'com.dantech.skimbrowser.ui.home.*' --tests 'com.dantech.skimbrowser.ui.incognito.*'
./gradlew :app:recordRoborazziDebug --tests '*HomeScreenshotTest*' --tests '*SearchScreenshotTest*' --tests '*IncognitoNewTabScreenshotTest*' --tests '*ShortcutOverlaysScreenshotTest*'
./gradlew :app:verifyRoborazziDebug
./gradlew :app:installDebug   # trên emulator skim_api36 đang chạy (quy trình ở plan.md)
```

Thủ công trên emulator: mic mở hộp thoại giọng nói và text vào 04b; gợi ý xuất hiện khi gõ với cả 4 engine (đổi engine ở phase 8, hoặc tạm qua test ViewModel); nhấn giữ shortcut → menu neo dưới ô.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| `ExpandedFullScreenContainedSearchBar` mở được khi không có thanh thu gọn (05/11) | Không mở hoặc animation nhảy | Dùng `Dialog` toàn màn hình với cùng nội dung `SearchOverlay` cho đường vào từ 05/11 |
| Endpoint gợi ý ổn định | 4xx, mảng rỗng, đổi format | Ẩn nhóm engine; vẫn hiện history/bookmark |
| Kích thước lưới 4 cột vừa 380dp | Nhãn shortcut bị cắt | Nhãn 1 dòng ellipsis như bản vẽ |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 6 (slot `newTabPage` trở về rỗng; package `search`, `ui/home`, `ui/search`, `ui/incognito` bị gỡ).

## Security considerations

- Không điều hướng tới scheme ngoài http/https từ ô nhập hay dialog shortcut.
- Gợi ý engine gửi chuỗi đang gõ tới engine đã chọn (hành vi chuẩn của trình duyệt); không gửi gì khi ô rỗng.
- Incognito không đọc history/bookmark cho gợi ý và không hiện Ask Skim.

## Next steps

- Phase 7 mở `SearchOverlay` từ address bar của 05/11; phase 12 làm hàng Ask Skim, thẻ hero, See all, FAB 04 chạy end-to-end; phase 11 làm thẻ Continue chạy end-to-end.

## Kết quả thực thi (2026-10-22)

Commit: `feat(home): add home, search overlay and incognito new tab`. Làm theo hai lượt tuần tự, mỗi lượt do một agent làm rồi controller kiểm chứng độc lập (chạy lại toàn bộ test, lint, Roborazzi, biên dịch không incremental, đột biến riêng) trước khi sang lượt sau: 6A logic không có giao diện (`resolveOmnibox`, gợi ý của engine, `SearchViewModel`, `HomeViewModel`, `ShortcutDialogViewModel`, giọng nói) và 6B giao diện (lớp tìm kiếm 04b, Home 04, New tab incognito 09c, dialog và menu shortcut, `NewTabPageRoute`, nối vào `SkimNavDisplay`, golden). Controller còn kiểm tay trên `skim_api36`. Báo cáo từng lượt: `plans/reports/fullstack-developer-261022-1356-phase-06a-logic.md`, `plans/reports/fullstack-developer-261022-1544-phase-06b-ui.md`.

Kiểm chứng cuối phase: `testDebugUnitTest` 1232 test (114 lớp), 0 lỗi, 0 bỏ qua; `assembleDebug` và `verifyRoborazziDebug` xanh; `lintDebug` 0 lỗi, 103 cảnh báo (baseline phase trước 110: `UnusedResources` giảm nhờ chuỗi và drawable nay được dùng, thêm 3 `GradleDependency` vì lint thấy bản thư viện mới phát hành), 0 cảnh báo trong file của phase; biên dịch lại không incremental cả ba source set 0 dòng `w:`; ba lượt lặp 204 test của các gói phase đều xanh. Đột biến có chủ ý: agent 6A 89, agent 6B 55, controller 110 (63 cho logic 6A, 47 cho phần nối giao diện 6B), mỗi cái làm đúng test liên quan đỏ; chín đột biến của controller lọt qua lần đầu (hai tương đương, vì OkHttp còn chặn cổng 0 và nhãn host dài hơn 63 ký tự) và bốn trong số đó nay có test riêng, một bị golden bắt, hai còn lại không kiểm được ở JVM (kiểu bàn phím địa chỉ) hoặc tương đương (đóng dialog bằng `remove(key)` hay `back()` khi khóa ở trên cùng). Golden mới: `boards/04-home-new-tab`, `boards/04b-search-suggestions`, `boards/09c-incognito-new-tab`, `components/home-04-first`, `search-04b-incognito`, `home-shortcut-dialog-15`, `home-shortcut-menu-15`, đã so với `design/screens/` và ghi lệch vào `docs/design-guidelines.md`.

Kiểm tay trên `skim_api36` (Android 16, WebView 133, mạng thật), bằng một probe tạm đã xoá: Home hiển thị đúng bố cục (màu theo dynamic color); nhấn thanh tìm kiếm mở lớp 04b với con trỏ; gõ "heat pump" hiện hàng Ask Skim và ba gợi ý thật của Google; mũi tên chép gợi ý vào ô; Enter mở trang kết quả trong tab và Back từ trang đầu về lại Home; xoay màn hình giữ chữ đã gõ và bàn phím đúng kiểu địa chỉ. Nhấn giữ shortcut mở menu neo dưới ô, Remove cho snackbar "Shortcut removed" trên toolbar và Undo trả shortcut về đúng vị trí; dialog Add chỉ bật Save với địa chỉ web (`javascript:`, chữ thường và địa chỉ có khoảng trắng thì Save tắt), tên rỗng lấy host, Edit mở sẵn dữ liệu. Ở tab incognito: New tab page 09c đúng bản vẽ, cả cửa sổ Activity và cửa sổ lớp tìm kiếm có cờ `SECURE`, ảnh chụp màn hình đen, bàn phím được yêu cầu không học chữ, chỉ có gợi ý của engine và không có Ask Skim. Không tìm thấy lỗi nào trên thiết bị; mic và hộp thoại giọng nói chưa kiểm.

Lỗi và lỗ hổng chỉ lộ khi đo hoặc qua đột biến (đã sửa, mỗi cái có test):
- `SuggestionService.suggestions` nuốt `CancellationException` thành danh sách rỗng (không test nào kiểm kết quả của một lần gọi đã huỷ): nay ném lại.
- Cờ mic của Home không đi qua `combine` (không test nào đợi trạng thái ghép) và số tab trên toolbar đếm cả tab khác loại, thẻ Continue thứ hai mở nhầm chat: mỗi cái có test riêng.
- Khoảng hở riêng tư: client HTTP dùng chung mà phase sau thêm interceptor (header cho proxy AI) sẽ đưa header đó tới engine tìm kiếm cùng chữ người dùng gõ. `AppContainer.httpClient` ghi rõ không có interceptor và `SuggestionServiceTest` giữ điều đó (phase cần interceptor phải dẫn xuất client bằng `newBuilder()`).
- Từ lượt 6B: Material3 `ExpandedFullScreenContainedSearchBar` không có `SearchBar` thu gọn thì thanh bay vào từ ngoài mép trái, lệch 8 và 4 dp so với 04b và có trạng thái saveable mở lại sau process death với view model trống; `DialogSceneStrategy` tự mở một cửa sổ nên `SkimAlertDialog` bên trong sẽ thành dialog lồng dialog với hai lớp dim; `LazyVerticalGrid` không đặt được trong cột cuộn của Home. Cách xử lý ở phần deviations.

Chữ ký thực tế khác spec (đã chấp nhận; chi tiết trong hai báo cáo):
- `resolveOmnibox(input, engine): String?` và `omniboxAddress(input): String?` (phần chỉ xét địa chỉ, dùng chung với dialog shortcut và service gợi ý); `parseOpenSearchSuggestions(body)`; `SuggestionService(client, callTimeoutMs)` với `suggestions(engine, query)`.
- `SearchViewModel(engine: Flow<SearchEngine>, suggestions: suspend (SearchEngine, String) -> List<String>, history, bookmarks)` với `query: TextFieldState` (state của ô do view model giữ), `state: StateFlow<SearchUiState(incognito, askSkim, engineSuggestions, pageMatches)>`, `open(initialText, selectAll, incognito)`, `clear()`, `editSuggestion(s)`, `submitTarget(text)` và `suggestionTarget(s)`; `searchViewModel(container)` dùng khoá `"search"` trong store của entry `BrowserKey`, nên 04, 09c và địa chỉ của 05 và 11 dùng chung một instance.
- `HomeViewModel(tabs, shortcuts, chats, snackbar, undo, appScope, voiceAvailable, clock, zone)` với `state: StateFlow<HomeUiState(eligibleTabCount, shortcuts, continueChats, voiceAvailable)>` (có `showsHero`, `showsContinue`), `openShortcut(url)` và `removeShortcut(id)`; `ShortcutDialogViewModel(shortcutId, shortcuts, appScope)` với `name`, `url`, `state(editing, loaded, canSave, done)` và `save()`; `VoiceInput.isAvailable(context)` và `VoiceInput.RecognizeSpeech`.
- Composable: `SearchOverlay(search, incognito, onOpenUrl, onAskSkim, onClose)`, `HomeScreen(state, actions: HomeActions, toolbar)`, `ShortcutMenu`, `ShortcutDialogRoute(key, container, navigator)` và `ShortcutDialog(dialog, onClose)`, `IncognitoNewTabScreen(voiceAvailable, onSearch, onVoice, toolbar)`, `NewTabPageRoute(tab, container, navigator)`. `AppContainer` thêm `suggestionService`.
- Sửa component phase 2: `SkimDialogs.kt` tách `DialogSurface` và thêm `SkimDialogContent` (nội dung dialog không tự mở cửa sổ; API cũ giữ nguyên, golden cũ không đổi), `ShapeTiles.kt` thêm tham số `container` (mặc định giữ màu cũ).

Việc để các phase sau:
- Phase 7: mở lớp tìm kiếm từ địa chỉ của 05 và 11 trong sự kiện chạm, không trong `LaunchedEffect`: `search.open(url, selectAll = true, incognito = tab.incognito)`, bật cờ hiện, vẽ `SearchOverlay(...)` với `onOpenUrl` gọi `tabs.load(tab.id, url)`, đóng lớp và `search.clear()`; entry dialog dùng `SkimDialogContent` thay cho `SkimAlertDialog` (nếu không sẽ thành dialog lồng dialog); `ChoiceDialog` chưa có bản không cửa sổ, cần thêm trước phase 7 hoặc 8; test cho metadata của entry `ShortcutDialogKey` (cần dựng `BrowserHost`); nhấn giữ link thành kéo thả của Chromium (đã ghi ở phase 5).
- Phase 11 và 12: N của "Summarize my N open tabs" hiện đếm mọi tab thường có trang web, nên phải loại tab thuộc site đã tắt Skim AI để khớp 06b (D3); hàng Ask Skim, thẻ hero, thẻ Continue và See all mới chỉ gọi `SkimNavigator`; thời gian trên thẻ Continue tính khi dữ liệu chat đổi và không tự cập nhật khi Home cứ mở.
- Phase 13: design review các điểm lệch ở dưới (nhất là 04b dạng dialog với fade thay cho thanh trượt lên, hàng 04b incognito `surfaceContainerLowest` gần như đen, thẻ hero cao hơn 9,5 dp, tên shortcut tối đa 2 dòng); kiểm bằng tay mic và hộp thoại giọng nói, và IME với con trỏ trên máy thật.

Giới hạn đã biết:
- Chữ là địa chỉ web hoặc dài hơn 100 ký tự không được gửi tới engine (gõ "example.com" chỉ thấy dòng history và bookmark, không có gợi ý engine); chữ gợi ý trùng với chữ đang gõ bị bỏ `[DEFAULT — chờ validate]`.
- `LIKE` chỉ không phân biệt hoa thường với ASCII (giới hạn của phase 3), nên gõ "đà nẵng" không khớp "Đà Nẵng" ở history và bookmark của 04b.
- Lớp tìm kiếm không bao giờ mở lại sau process death (trạng thái mở không saveable) và đóng lớp luôn xoá chữ đã gõ.
- Bàn phím ở incognito được yêu cầu không học chữ gõ bằng cờ IME; chỉ các bàn phím tôn trọng cờ này mới thực hiện.
- Trên emulator có bàn phím cứng, bàn phím mềm chỉ hiện sau khi xoay màn hình (không phải lỗi của app).

Known deviations (đã chấp nhận): ô tìm kiếm là `TextFieldState` do `SearchViewModel` giữ thay cho `TextFieldValue` trong `SearchUiState` (material3 1.5.0-beta01 chỉ nhận `TextFieldState`, và chữ gõ ở incognito không vào saved state); 04b là cửa sổ dialog toàn màn vẽ tràn viền với fade `defaultEffectsSpec` (không có hiệu ứng thanh trượt lên từ 04) và cửa sổ đó `SecureOn` ở tab incognito; dialog shortcut tắt Save khi URL không phải địa chỉ web thay vì thêm copy báo lỗi; lưới shortcut không lazy và tên shortcut tối đa 2 dòng (bản vẽ "Stack Overflow" 2 dòng); gợi ý của engine vẫn có ở incognito nhưng history, bookmark và Ask Skim thì không; `HomeViewModel` có thêm `appScope`, `voiceAvailable`, `clock`, `zone`.
