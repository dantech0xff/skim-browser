# Phase 6, lượt 6A (logic, không UI): tìm kiếm, gợi ý, Home, dialog shortcut, giọng nói

Ngày 2026-10-22 13:56 (Asia/Singapore), nhánh `feat/skim-design-implementation`, HEAD vẫn là commit phase 5. Chưa `git add`, chưa commit. Không viết composable, không ảnh Roborazzi, không test instrumented, không emulator, không dùng điện thoại thật.

## Kết quả

Phần logic của phase 6 đã xong và xanh trên JVM/Robolectric:
- `resolveOmnibox` (cùng `omniboxAddress` nội bộ) quyết định Enter mở địa chỉ hay tìm kiếm. Chỉ http/https là địa chỉ; mọi scheme khác, khoảng trắng và ký tự điều khiển/bidi/vô hình đều thành văn bản tìm kiếm đã mã hoá.
- `parseOpenSearchSuggestions` và `SuggestionService` lấy gợi ý từ engine. Service huỷ call thật khi coroutine bị huỷ, có call timeout 3 s, đọc theo charset của header, đọc tối đa 64 KiB. Mọi lỗi đều cho danh sách rỗng. Request không mang cookie, credential hay referrer, không đi qua cache và không theo redirect.
- `SearchViewModel`: nghỉ 150 ms rồi tra cứu theo kiểu `collectLatest`; gợi ý engine và Room chạy song song; incognito không đọc history/bookmark và không có Ask Skim.
- `HomeViewModel`: đếm tab, shortcut, thẻ Continue, Remove có Undo chạy qua `UndoRegistry` nên vẫn chạy sau khi ViewModel bị huỷ.
- `ShortcutDialogViewModel`: chỉ lưu địa chỉ web; Name rỗng thì lấy host.
- `VoiceInput`.
- `AppContainer.suggestionService`.

Có 115 test mới và 89 đột biến có chủ ý, tất cả đều bị test liên quan bắt. Không thêm chuỗi, không sửa manifest hay Gradle.

## Kiểm chứng (chạy thật, theo thứ tự cuối)

- Lệnh lọc của prompt (`:app:cleanTestDebugUnitTest :app:testDebugUnitTest` với `search.*`, `ui.search.*`, `ui.home.*`, `system.*`): lặp 10 lần liên tiếp, rồi thêm 3 lần sau lần sửa cuối (chỉ gói lại comment trong `AppContainer.kt`). Cả 13 lần đều xanh, mỗi lần 124 test (115 mới cộng 9 của `DefaultBrowserTest` cùng package), 0 lỗi, 0 bỏ qua, 0 dòng `w:`. Số test theo lớp: `OmniboxResolverTest` 23, `SuggestionParserTest` 13, `SuggestionServiceTest` 21, `SearchViewModelTest` 23, `HomeViewModelTest` 13, `ShortcutDialogViewModelTest` 12, `VoiceInputTest` 10.
- Toàn dự án `:app:cleanTestDebugUnitTest :app:testDebugUnitTest`: 1150 test, 105 lớp, 0 lỗi, 0 bỏ qua (phase 5 là 1035 test, 98 lớp).
- `:app:assembleDebug`: xanh.
- `:app:lintDebug`: 0 lỗi, 112 cảnh báo, 0 cảnh báo trong file mới hoặc file đã sửa. So với baseline 110: `UnusedResources` còn 104 (giảm 1 vì `snackbar_shortcut_removed` nay đã được dùng). Có thêm 3 `GradleDependency`, do lint tự dò thấy bản thượng nguồn mới phát hành (material3 1.5.0-beta02, webkit 1.18.0, datastore-preferences 1.3.0), không do thay đổi nào của lượt này (không file Gradle nào bị đụng). Còn lại: `NewerVersionAvailable` 3, `AndroidGradlePluginVersion` 1, `InsecureBaseConfiguration` 1.
- `:app:verifyRoborazziDebug`: xanh (1150 test), không ảnh nào đổi (`git status` thư mục screenshots trống).
- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks -Pkotlin.incremental=false`: xanh, 0 dòng `w:`, 0 dòng `e:`.
- Quét vệ sinh (`p5-hygiene.py`, 18 file mới, các dòng thêm của `AppContainer.kt`): 0 ID plan/phase/finding, 0 nhắc AI, 0 TODO, 0 log debug, 0 secret, 0 dòng log có nội suy ngoài tên lớp exception, 0 ký tự bidi/zero-width thô.
- Đã gọi thử cả 4 endpoint gợi ý ngày 2026-10-22: đều trả đúng định dạng OpenSearch `["q",[...]]`; Google có thêm `[]` và một object phía sau; Bing và Ecosia nay gửi `application/json; charset=utf-8`. Fixture `app/src/test/resources/suggest/*.json` giữ đúng cấu trúc thật nhưng dùng nội dung trung tính (bản thật của Google có gợi ý theo vị trí người gọi).

## Đột biến

Có 89 đột biến, tất cả đều bị bắt. Harness nằm ngoài repo (`scratchpad/p6a-mutate.py`, chép từ mẫu của phase 5); mỗi đột biến chỉ chạy lớp test liên quan, sau đó khôi phục nguồn và so md5. File được khôi phục trùng md5 ở mọi lượt, md5 của cả cây nguồn trùng trước/sau.
- **Lượt 1** (88 đột biến): 85 bị bắt, 2 sống, 1 lỗi biên dịch.
  - R07 lỗi biên dịch vì mẫu thay thế của tôi sai (`!host == …`); đã sửa mẫu.
  - V04 sống vì test incognito khẳng định "không đọc history" quá sớm: truy vấn Room chạy trên luồng của Room. Test nay gõ thêm chữ rồi chờ kết quả của chữ mới; `collectLatest` phải join lần tra cứu cũ (cả truy vấn Room) trước khi bắt đầu lần mới, nên đến lúc đó mọi lần đọc đã vào log SQL.
  - H07 sống vì thiếu test cho trường hợp ViewModel bị huỷ ngay khi vừa bấm Remove; đã thêm `aRemoveTappedAsHomeGoesAwayStillHappensWithItsUndo`.
- **Lượt 2:** R07, V04, H07 cùng V03, V14 (dùng chung test vừa sửa) đều bị bắt.
- **Lượt 3:** thêm S16 nhắm thẳng trần 64 KiB, vì S09 (bỏ dòng `request`) bị bắt chỉ do nó làm hỏng luôn việc đọc body. S16 bị bắt.

Mọi đột biến đều bị test của đúng lớp liên quan bắt, nên không cần chạy lặp để loại trừ flake.

| Nhóm | Số | Đột biến | Test bắt |
|---|---|---|---|
| `resolveOmnibox` | 14 | scheme khác (`javascript:`, `file:`, `intent:`) mở nguyên văn; URL http không parse vẫn mở; host mở không có `https://`; không nhận IPv4; nhận octet trên 255; bỏ `localhost`; `localhost` phân biệt hoa thường; cho khoảng trắng; cho ký tự điều khiển/bidi/vô hình; nhãn cuối toàn số (`1.5`) thành domain; nhãn có gạch đầu/cuối; host dài hơn 253; không cắt khoảng trắng (chuỗi trắng thành tìm kiếm); không nhận IPv6 trong ngoặc | `OmniboxResolverTest` |
| Parser | 8 | lấy phần tử 2; không giới hạn số mục; không giới hạn độ dài; giữ bidi/điều khiển; giữ mục trùng; lấy cả mảng phụ của Google; BOM làm hỏng body; JSON hỏng ném lỗi | `SuggestionParserTest` |
| Service | 16 | không huỷ call; không call timeout; timeout mặc định 30 s; ném khi lỗi mạng; parse cả phản hồi HTTP lỗi; gửi query trắng; gửi địa chỉ đang gõ; bỏ charset của header; bỏ bước đọc có trần; lệch 1 ở trần; gửi cookie của client chung; trả lời 401 bằng credential; theo redirect; ghi vào cache của client chung; gửi chuỗi dài hơn 100; đọc hết body dù lớn | `SuggestionServiceTest` |
| `SearchViewModel` | 19 | không debounce; `collect` thay `collectLatest`; Ask Skim ở incognito; đọc history/bookmark ở incognito; không bỏ URL trùng; bookmark trước history; 4 dòng thay 3; giữ gợi ý trùng chữ đang gõ; tra cứu chữ trắng; bỏ chốt phiên mở; bỏ chốt chữ hiện tại; không chọn sẵn URL; Clear text không xoá dòng ngay; trước lần mở đầu coi như tab thường; lần mở mới giữ dòng cũ; Enter tìm kiếm bằng engine đoán khi chưa đọc setting; mũi tên chọn hết thay vì đặt con trỏ cuối; gợi ý giống địa chỉ mở như địa chỉ; lỗi đọc history không được bắt | `SearchViewModelTest` |
| `HomeViewModel` | 16 | đếm tab incognito; đếm trang New tab; đếm `about:blank`; Undo không restore; Undo thêm lại ở cuối với id mới; Undo chạy trên `viewModelScope`; lệnh xoá chạy trên `viewModelScope`; snackbar thiếu Undo; Undo đăng ký sai loại; hero hiện ở Home lần đầu; Continue hiện khi không có chat; 3 thẻ Continue; shortcut mở đè lên trang; shortcut mở trong tab incognito; thời gian thẻ tính từ chính nó; chữ cái lấy từ title thay vì host | `HomeViewModelTest` |
| `ShortcutDialogViewModel` | 8 | Save nhận cả URL tìm kiếm (`javascript:` thành URL Google); Name rỗng giữ rỗng; Save bật khi URL trắng; chạm Save lần hai lưu thêm; Save chạy trước khi nạp xong shortcut; shortcut đã mất không đóng dialog; cờ từ chối không mất khi sửa URL; lệnh lưu chết theo dialog | `ShortcutDialogViewModelTest` |
| `VoiceInput` | 6 | `LANGUAGE_MODEL_FREE_FORM`; lấy kết quả cuối; huỷ vẫn trả chữ; không làm sạch chữ; bỏ `MATCH_DEFAULT_ONLY`; gọi API 33 ở mọi phiên bản | `VoiceInputTest` |
| `AppContainer` | 2 | service dùng client riêng; service tạo ngay cùng container | `SuggestionServiceTest` |

## Chữ ký thật

`search/OmniboxResolver.kt`
- `fun resolveOmnibox(input: String, engine: SearchEngine): String?`: cắt khoảng trắng hai đầu. Trả null cho chuỗi trắng; trả địa chỉ web nếu chuỗi là địa chỉ; còn lại trả `engine.searchUrl(text)`.
- `internal fun omniboxAddress(input: String): String?`: phần "địa chỉ" của hàm trên, null khi là văn bản tìm kiếm. Dialog shortcut và service gợi ý dùng hàm này.
  - URL `http:`/`https:` (mọi kiểu hoa thường) mà OkHttp parse được thì giữ nguyên văn.
  - Host không có scheme thì thêm `"https://" +` ở trước. Host hợp lệ là domain (có dấu chấm, nhãn LDH hoặc chữ Unicode, nhãn cuối có chữ, mỗi nhãn tối đa 63, cả host tối đa 253), `localhost`, IPv4, hoặc IPv6 trong ngoặc. Port từ 1 đến 65535, sau đó tuỳ chọn path, query hoặc fragment. Kết quả cuối vẫn phải được OkHttp parse.

`search/SuggestionParser.kt`
- `fun parseOpenSearchSuggestions(body: String): List<String>`: lấy phần tử [1]. Mỗi gợi ý được `toVisibleText()`; bỏ gợi ý trắng, trùng hoặc dài hơn 256; tối đa 10 mục (`internal const val MAX_SUGGESTIONS`, `MAX_SUGGESTION_LENGTH`).

`search/SuggestionService.kt`
- `class SuggestionService(client: OkHttpClient, callTimeoutMs: Long = CALL_TIMEOUT_MS)` với `suspend fun suggestions(engine: SearchEngine, query: String): List<String>`.
- `internal val client`: client dẫn xuất từ client chung, chung dispatcher và connection pool. Cấu hình: `callTimeout` 3 s, `followRedirects(false)`, `CookieJar.NO_COOKIES`, `Authenticator.NONE`, `cache(null)`.
- Companion nội bộ: `CALL_TIMEOUT_MS = 3_000`, `MAX_BODY_BYTES = 64 KiB`, `MAX_QUERY_LENGTH = 100`, `suggestionAddress(engine, query): HttpUrl?` (chỉ https, đúng host của engine).

`ui/search/SearchViewModel.kt`
- `data class PageMatch(val title: String, val host: String, val url: String, val isBookmark: Boolean)`. `title` là chữ thuần; trang không có title thì dùng URL.
- `data class SearchUiState(val incognito: Boolean = true, val askSkim: Boolean = false, val engineSuggestions: List<String> = emptyList(), val pageMatches: List<PageMatch> = emptyList())`.
- `class SearchViewModel(engine: Flow<SearchEngine>, suggestions: suspend (SearchEngine, String) -> List<String>, history: HistoryRepository, bookmarks: BookmarkRepository) : ViewModel()`.
  - Thuộc tính: `val query: TextFieldState`, `val state: StateFlow<SearchUiState>`.
  - Hành động (main thread): `open(initialText: String = "", selectAll: Boolean = false, incognito: Boolean)`, `clear()`, `editSuggestion(suggestion: String)`, `submitTarget(text: String): String?`, `suggestionTarget(suggestion: String): String?`.

`ui/home/HomeViewModel.kt`
- `data class ContinueCard(val chatId: Long, val host: String, val letter: String, val timeText: UiText, val title: String, val snippet: String)`.
- `data class HomeUiState(val eligibleTabCount: Int = 0, val shortcuts: List<ShortcutEntity> = emptyList(), val continueChats: List<ContinueCard> = emptyList(), val voiceAvailable: Boolean = false)`, có hai thuộc tính tính toán `showsHero` (`eligibleTabCount > 0`) và `showsContinue` (`continueChats.isNotEmpty()`). Trạng thái Home lần đầu suy ra từ đây mà không cần cờ riêng; Shortcuts chỉ còn ô Add khi `shortcuts` rỗng.
- `class HomeViewModel(tabs: TabManager, shortcuts: ShortcutRepository, chats: ChatRepository, snackbar: SnackbarController, undo: UndoRegistry, appScope: CoroutineScope, voiceAvailable: Boolean, clock: () -> Long = System::currentTimeMillis, zone: () -> ZoneId = ZoneId::systemDefault) : ViewModel()`.
  - `val state: StateFlow<HomeUiState>` (`WhileSubscribed(5_000)`).
  - `fun openShortcut(url: String)`: `tabs.load` vào tab hiện tại, chỉ khi đó là tab thường đang ở trang New tab.
  - `fun removeShortcut(id: Long)`.
  - `internal companion`: `CONTINUE_CARDS = 2`, `eligibleTabCount(state: TabsState): Int`.

`ui/home/ShortcutDialogViewModel.kt`
- `data class ShortcutDialogUiState(val editing: Boolean, val loaded: Boolean, val canSave: Boolean, val urlRejected: Boolean, val done: Boolean)`.
- `class ShortcutDialogViewModel(shortcutId: Long?, shortcuts: ShortcutRepository, appScope: CoroutineScope) : ViewModel()` với `val name: TextFieldState`, `val url: TextFieldState`, `val state: StateFlow<ShortcutDialogUiState>`, `fun save()` (main thread).

`system/VoiceInput.kt`
- `object VoiceInput` với `fun isAvailable(context: Context): Boolean`. Hàm dùng `queryIntentActivities(ACTION_RECOGNIZE_SPEECH, MATCH_DEFAULT_ONLY)`, bản `ResolveInfoFlags` từ API 33 nên không có cảnh báo deprecated, và dựa vào `<queries>` đã có trong manifest.
- `class VoiceInput.RecognizeSpeech : ActivityResultContract<Unit, String?>()` với `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_WEB_SEARCH`. Trả `EXTRA_RESULTS[0]` dạng chữ thuần, tối đa 1.000 ký tự (`internal const val MAX_TEXT_LENGTH`); null khi huỷ hoặc không có kết quả.

`AppContainer.kt`
- `val suggestionService: SuggestionService by lazy { SuggestionService(httpClient) }`. Không tạo gì trước lần dùng đầu (test kiểm cả `suggestionService$delegate` lẫn `httpClientHolder`).

Cách dựng từ `AppContainer` (gợi ý cho 6B):

```kotlin
// Trong entry BrowserKey (một store cho mọi tab), dùng chung cho 04, 09c và sau này địa chỉ của 05/11:
val search: SearchViewModel = viewModel(key = "search") {
    SearchViewModel(
        engine = container.settings.settings.map { it.searchEngine },
        suggestions = container.suggestionService::suggestions,
        history = container.historyRepository,
        bookmarks = container.bookmarkRepository,
    )
}
val home: HomeViewModel = viewModel(key = "home") {
    HomeViewModel(
        tabs = container.tabManager, shortcuts = container.shortcutRepository, chats = container.chatRepository,
        snackbar = container.snackbarController, undo = container.undoRegistry, appScope = container.appScope,
        voiceAvailable = VoiceInput.isAvailable(context.applicationContext),
    )
}
// Trong entry ShortcutDialogKey (store riêng của dialog, huỷ khi dialog đóng):
val dialog = viewModel { ShortcutDialogViewModel(key.shortcutId, container.shortcutRepository, container.appScope) }
```

Chuỗi đã dùng: `R.string.snackbar_shortcut_removed` (qua `UiText.Res`) và, qua `relativeTimeText`, các chuỗi `time_just_now`, `time_minutes_ago`, `time_hours_ago`, `date_yesterday`. Không thêm chuỗi nào.

## Lệch spec và lý do

1. **`SearchUiState` không có `query: TextFieldValue`.** Ô nhập là `SearchViewModel.query: TextFieldState`; `onQueryChanged(text)` được thay bằng chính state này: input field ghi thẳng vào `query`, ViewModel quan sát bằng `snapshotFlow`. Clear text, mũi tên và chọn sẵn URL là `clear()`, `editSuggestion(s)`, `open(initialText, selectAll, incognito)`. Lý do, đã kiểm bằng nguồn:
   - Trong material3 1.5.0-beta01, overload `SearchBarDefaults.InputField` duy nhất không bị deprecated nhận `textFieldState` cùng `searchBarState` (`SearchBar.kt:2377`); các bản nhận `String` hoặc `TextFieldState`+`expanded` đều deprecated (dòng 2620, 2780). Không overload nào nhận `TextFieldValue`.
   - Component của dự án cũng dùng `TextFieldState`: `SkimTextField(state: TextFieldState)` (phase 2) và `SearchTitleBar(state: TextFieldState)`.
   - Một `TextFieldValue` nằm trong `StateFlow` tới ô nhập chậm một lượt dispatch, làm IME lệch nhịp khi gõ nhanh.
   - State nằm trong ViewModel nên không bị lưu vào saved state của Activity: chữ gõ ở incognito không bao giờ vào Bundle.

   Tôi đã hỏi `kongming`. `kongming` khuyên để composable giữ `rememberTextFieldState` và giữ ViewModel không phụ thuộc Compose. Tôi chọn để ViewModel giữ state vì hai lý do: giữ được phạm vi prompt (Clear, mũi tên, selectAll có test JVM), và tránh việc `rememberTextFieldState` (saveable) lưu chữ incognito.
2. **Incognito truyền theo từng lần mở, không qua constructor.** `open(..., incognito)`, và trước lần mở đầu tiên ViewModel coi như incognito. Store của entry `BrowserKey` dùng chung cho mọi tab; nếu lấy cờ từ constructor, một ViewModel bị tạo sai key có thể đọc history khi đang ở tab incognito mà không ai thấy.
3. **`SearchViewModel` nhận hàm `suggestions` thay vì `SuggestionService`.** 6B truyền `container.suggestionService::suggestions`; nhờ vậy test điều khiển được thời điểm trả lời. Có một test nối service thật qua MockWebServer.
4. **Debounce viết là `collectLatest { delay(150); lookUp() }`.** Hành vi như `debounce` rồi `mapLatest`, nhưng lần tra cứu cũ (cả HTTP call) bị huỷ ngay khi chữ đổi, không phải đợi thêm 150 ms. Ask Skim và việc xoá dòng khi chữ trắng chạy ngay, không chờ hết thời gian nghỉ.
5. **Kết quả engine và Room hiện riêng, ai xong trước hiện trước.** Engine chậm (tới 3 s) không giữ chân dòng history. Một kết quả chỉ được hiện khi còn đúng lần mở và đúng chữ trong ô; chốt này chặn khoảnh khắc giữa lúc ô đổi và lúc `snapshotFlow` báo tin.
6. **Bỏ gợi ý engine trùng với chữ đang gõ (không phân biệt hoa thường).** Board 04b cho "heat pump" có ba gợi ý và không có chính "heat pump"; ô nhập và hàng Ask Skim đã hiện chữ đó. Thực tế Bing, DuckDuckGo và Ecosia đều trả chính query ở vị trí đầu.
7. **Service không gửi gì khi chữ là địa chỉ (`omniboxAddress`) hoặc dài hơn 100 ký tự.** Khi sửa địa chỉ của 05/11 tại chỗ, nếu không có chốt này, engine sẽ nhận nguyên path và query (có thể chứa token). Firefox, nơi lấy các endpoint gợi ý này, cũng không gửi chuỗi trông như origin hay URL. Chữ quá dài thường là dán vào, không phải gõ.
8. **`submitTarget` chỉ mở địa chỉ cho tới khi đọc xong setting engine**, không bao giờ tìm kiếm bằng một engine đoán. Khoảng này chỉ vài mili giây sau khi tạo ViewModel.
9. **Thêm `suggestionTarget(s)`.** Bấm một gợi ý mở trang kết quả của engine, kể cả khi gợi ý trông như địa chỉ, đúng như 04b ghi.
10. **`HomeViewModel` có thêm `appScope`, `voiceAvailable`, `clock`, `zone`.** Lệnh xoá chạy trên `appScope` giống `SetupViewModel`; khối Undo chỉ giữ repository và dữ liệu, không giữ ViewModel. `eligibleTabCount` chỉ đếm URL http(s), nên `about:blank` không tính. `openShortcut(url)` bỏ qua tab incognito và tab đã hiện trang (cú chạm thứ hai trong cùng frame).
11. **`ShortcutDialogViewModel` giữ hai `TextFieldState`** vì `SkimTextField` cần chúng.
    - Save bật khi đã nạp xong, không đang lưu và URL không trắng.
    - URL bị từ chối thì bật `urlRejected` và không lưu; cờ tự mất khi người dùng sửa URL.
    - Khi sửa một shortcut không còn tồn tại (key khôi phục sau process death), dialog báo `done`.
    - Lệnh lưu chạy trên `appScope`.
12. **Parser bỏ thêm mục trùng và BOM đầu body.** Mục trùng sẽ làm crash `LazyColumn` nếu 6B dùng chuỗi gợi ý làm key.
13. **File của phase 1–5 đã sửa:** chỉ `AppContainer.kt` (+8/−3: import, KDoc gói lại, `suggestionService`). `SearchEngine` đã có sẵn URL gợi ý của cả 4 engine nên không cần sửa. Không sửa `strings.xml`, manifest, Gradle, `.idea/`.
14. **Sự cố công cụ:** công cụ ghi file đã biến một số escape `\uXXXX` (bidi, zero-width, BOM, soft hyphen, cặp surrogate) trong literal thành ký tự thô. Tôi đã đưa chúng về dạng escape bằng script (dựng dấu gạch chéo bằng `chr(92)`); quét vệ sinh cho 0. Agent sau nên kiểm byte thật khi viết test có ký tự vô hình.

## Việc cho 6B

- **Quan sát state:** `collectAsStateWithLifecycle()` trên `state` của từng ViewModel. Ô nhập 04b: `SearchBarDefaults.InputField(textFieldState = search.query, searchBarState = barState, onSearch = { text -> search.submitTarget(text)?.let(onOpenUrl) }, ...)`. Hàng Ask Skim lấy chữ từ `search.query.text` (đọc snapshot, đồng bộ), còn việc hiện hay ẩn theo `state.askSkim` (chậm tối đa một frame). Nút Clear text chỉ hiện khi `search.query.text.isNotEmpty()` và gọi `search.clear()`.
- **Mở overlay:** gọi `search.open(initialText, selectAll, incognito = tab.incognito)` ngay trong sự kiện mở overlay: chạm thanh ở 04/09c, callback giọng nói, chạm địa chỉ ở 05/11 của phase 7. Không gọi trong `LaunchedEffect`, vì effect chạy lại sẽ xoá chữ đang gõ. Nên tạo `SearchViewModel` ở mức slot của `BrowserRoute`, để thanh thu gọn và overlay dùng chung một instance; key cố định là đủ.
- **Process death:** `rememberSearchBarState` là saveable. Sau process death overlay có thể hiện lại ở trạng thái mở trong khi ViewModel là bản mới: ô trống và coi như incognito tới lần `open` kế tiếp. Có hai cách: đừng khôi phục trạng thái mở, hoặc gọi lại `open` khi overlay được khôi phục.
- **Hành động trên các dòng:**
  - Gợi ý: `search.suggestionTarget(s)?.let(onOpenUrl)`.
  - Mũi tên north_west: `search.editSuggestion(s)`, content description `cd_edit_search` kèm `s`.
  - Dòng trang: `onOpenUrl(match.url)`.
  - Ask Skim: `navigator.openAskTabs(search.query.text.toString().trim())`.
  - `onOpenUrl(url)` = `tabs.load(tab.id, url)` rồi đóng overlay; `BrowserRoute` tự chuyển sang slot `page`.
- **Dữ liệu các dòng:** `engineSuggestions` và `pageMatches` đều không trùng, tối đa 3 dòng mỗi nhóm, dùng làm key `LazyColumn` được (`it`, `it.url`). `PageMatch.isBookmark` cho biết dùng `ic_bookmarks` hay `ic_history`. Khi `state.incognito` thì không có Ask Skim và `pageMatches` luôn rỗng, overlay chỉ cần vẽ theme tối.
- **Home:**
  - Hero hiện khi `state.showsHero`; prompt lấy từ `pluralStringResource(R.plurals.home_summarize_tabs, n, n)` rồi gọi `navigator.openAskTabs(prompt)`.
  - Ô shortcut dùng `siteLetter(host = null, title = s.name)` và `shortcutShapeFor(index)`, trong đó `index` là vị trí trong danh sách chứ không phải `ShortcutEntity.position` (có thể có khoảng trống sau khi xoá).
  - Bấm ô: `home.openShortcut(s.url)`. Nhấn giữ: menu với Edit (`navigator.openDialog(ShortcutDialogKey(s.id))`) và Remove (`home.removeShortcut(s.id)`). Ô Add: `navigator.openDialog(ShortcutDialogKey())`.
  - Continue hiện khi `state.showsContinue`: chạm thẻ gọi `navigator.openSavedChat(card.chatId)`, See all gọi `navigator.openSkimHistory()`, thời gian hiện bằng `card.timeText.asString()`.
  - Mic hiện khi `state.voiceAvailable` (ở 09c dùng `VoiceInput.isAvailable(context)`).
  - Snackbar Undo không cần làm gì thêm: host của shell (phase 5) đã chạy Undo qua `onActionClicked`.
- **Giọng nói:** `rememberLauncherForActivityResult(VoiceInput.RecognizeSpeech()) { spoken -> spoken?.let { search.open(it, incognito = tab.incognito); /* mở overlay */ } }`. Gọi `launch(Unit)` trong `try`/`catch (ActivityNotFoundException)` và log tĩnh.
- **Dialog shortcut:**
  - Hai trường: `SkimTextField(dialog.name, stringResource(R.string.field_name))` và `SkimTextField(dialog.url, stringResource(R.string.field_url), focusRequester = urlFocus)`; focus vào URL khi dialog mở.
  - Tiêu đề: `shortcut_menu_edit` khi `state.editing`, ngược lại `dialog_add_shortcut_title`.
  - Save: bật theo `state.canSave`, gọi `dialog.save()`.
  - Đóng: `LaunchedEffect(state.done) { if (state.done) navigator.back() }`; trường trống cho tới khi `state.loaded`.
  - Khi `state.urlRejected` cần một phản hồi trên trường URL; xem câu hỏi 3.
- **Thời gian thẻ Continue** được tính khi dữ liệu chat đổi, không tự nhảy khi Home cứ mở; giá trị cũ còn tới lần đăng ký lại sau 5 s.

## Câu hỏi chưa giải quyết

1. Có chấp nhận lệch 1 (`SearchViewModel.query: TextFieldState` thay cho `SearchUiState.query: TextFieldValue`, kèm `open`/`clear`/`editSuggestion`) không? `kongming` khuyên phương án composable giữ state; nếu controller chọn phương án đó, cần đổi chữ ký và test của `SearchViewModel`.
2. Có giữ quy tắc riêng tư không gửi tới engine chữ là địa chỉ hoặc dài hơn 100 ký tự (lệch 7) không? Hệ quả là gõ "example.com" chỉ thấy dòng history/bookmark, không có gợi ý engine.
3. Dialog shortcut từ chối URL không phải địa chỉ web nhưng chưa có copy báo lỗi. Có ba lựa chọn: viền lỗi không chữ (cần thêm `isError` cho `SkimTextField` của phase 2), thêm copy D8 (ví dụ "Enter a web address"), hoặc tắt Save khi URL không phải địa chỉ.
4. N của "Summarize my N open tabs" hiện tính cả tab thuộc site đã tắt Skim AI, trong khi 06b (phase 12) bỏ các site đó (D3). Có cần loại chúng khỏi N không?
5. Có chấp nhận bỏ gợi ý trùng với chữ đang gõ (lệch 6) không?
6. `LIKE` chỉ không phân biệt hoa thường với ASCII (giới hạn đã biết của phase 3), nên gõ "đà nẵng" không khớp "Đà Nẵng" trong history và bookmark của 04b.
