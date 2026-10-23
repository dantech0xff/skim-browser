# Phase 6, lượt 6B (UI): Home, lớp tìm kiếm 04b, New tab incognito, dialog và menu shortcut

Ngày 2026-10-22 15:44 (Asia/Singapore), nhánh `feat/skim-design-implementation`, HEAD vẫn là commit phase 5. Chưa `git add`, chưa commit, không đổi nhánh. Không dùng emulator, không dùng điện thoại thật, không viết test instrumented.

## Kết quả

Phần UI của phase 6 đã xong và xanh trên JVM/Robolectric.
- Home (04) và New tab incognito (09c) đã thay slot `newTabPage` của `BrowserRoute` qua `NewTabPageRoute`. Toolbar có Back/Forward tắt, Tabs mở 09 hoặc 09b theo loại tab, More mở 05b của tab. AI FAB mở 06b ở 04 và tắt ở 09c. Mic chỉ hiện khi có app nhận giọng nói.
- Lớp tìm kiếm 04b là một cửa sổ dialog toàn màn dùng chung cho mọi chỗ mở. Ở tab incognito, cửa sổ luôn có FLAG_SECURE và bàn phím được yêu cầu không học chữ gõ. Trạng thái mở không được lưu, nên lớp không mở lại sau process death.
- Dialog Add/Edit shortcut là entry `ShortcutDialogKey` trong `DialogSceneStrategy`. Menu shortcut neo dưới ô và đổi màu ô sang `secondaryContainer` khi mở.
- Mục 0 đã làm trước: Save của dialog chỉ bật khi URL là địa chỉ web, `urlRejected` và `refusedUrl` đã bỏ.
- Có 78 test mới (cộng 1 test VM ở mục 0) và 7 golden mới, đã so với board. Có 55 đột biến, tất cả bị bắt.

Không thêm chuỗi hay drawable nào: mọi chữ và icon cần đã có từ phase 1.

## Kiểm chứng (chạy thật, theo thứ tự cuối)

- Lệnh lọc của prompt (`search.*`, `ui.search.*`, `ui.home.*`, `ui.incognito.*`, `system.*`, mỗi lần chạy sau `cleanTestDebugUnitTest`) lặp 10 lần liên tiếp sau lần sửa nguồn cuối. Cả 10 lần đều xanh, mỗi lần 204 test, 0 lỗi, 0 bỏ qua, 0 dòng `w:`. Trong đó 126 test có sẵn (6A cùng 2 test controller thêm, và `DefaultBrowserTest`), 78 test là mới.
- `:app:cleanTestDebugUnitTest :app:verifyRoborazziDebug` chạy 3 lần liên tiếp đều xanh, mỗi lần 1230 test, 114 lớp. Không golden cũ nào đổi; `git status` của `app/src/test/screenshots/` chỉ có 7 golden mới.
- Toàn dự án `:app:cleanTestDebugUnitTest :app:testDebugUnitTest`: 1230 test (1152 + 78), 0 lỗi, 0 bỏ qua.
- `:app:assembleDebug`: xanh.
- `:app:lintDebug`: 0 lỗi, 103 cảnh báo (baseline 112), 0 cảnh báo trong file `.kt` nào. `UnusedResources` giảm từ 104 xuống 95 vì UI đã dùng các chuỗi và drawable của 04, 04b, 09c. Các loại khác không đổi.
- Biên dịch lại không incremental (`--rerun-tasks -Pkotlin.incremental=false`) cả main, unit test và androidTest: 34 task chạy, 0 `w:`, 0 `e:`. Mọi test thiết bị, kể cả `MainActivityLifecycleTest`, vẫn biên dịch được.
- Quét vệ sinh (`p5-hygiene.py`; 35 file mới, 5 file sửa): 0 ID plan/phase/finding, 0 nhắc AI, 0 TODO, 0 log debug, 0 secret, 0 dòng log có nội suy, 0 ký tự bidi/zero-width thô. Escape `‮` trong `ShortcutDialogViewModelTest` vẫn là escape.

## Quyết định: Dialog toàn màn, không dùng `ExpandedFullScreenContainedSearchBar`

Tôi đo trên Robolectric bằng một test thăm dò tạm. Test này đã xoá, ảnh để ở `scratchpad/p6b/probe/`.
- Khi không có `SearchBar` thu gọn của material3, `state.collapsedBounds` bằng 0. Lúc đó thanh mở rộng bay vào từ ngoài mép trái: ở 100 ms nó nằm ở x −80 dp. Khi đóng, nó bay ra theo hướng đó.
  - Trường hợp này gặp ở 05/11, và cả ở 04/09c, vì thanh thu gọn của hai màn này là `SearchBarField` của phase 2 (golden đã khớp board), không phải `SearchBar` của material3.
  - Khi có `SearchBar` của material3 thì hiệu ứng mượt. Nhưng dùng nó nghĩa là thay component phase 2, không có chỗ gọi `search.open(...)` trong sự kiện mở (thanh mở theo focus), và thanh thu gọn sẽ hiện chữ còn trong `query`.
- Bố cục mở rộng của material3 cách mép 8 dp và cách status bar 4 dp. 04b vẽ 16 dp và 8 dp.
- `rememberSearchBarState` là saveable, nên lớp sẽ mở lại sau process death với một view model mới, đang trống.

Đây đúng là trường hợp "animation nhảy" trong Risk assessment, nên tôi chọn `Dialog(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` cho mọi chỗ mở.
- Cửa sổ vẽ tràn viền, icon thanh hệ thống hợp nền, không dim và không có animation cửa sổ. Nội dung hiện bằng fade `defaultEffectsSpec`.
- Bố cục khớp board: thanh ở y 32 và x 16–396, các nhóm ở y 104, 188, 372.

FLAG_SECURE: `securePolicy = if (incognito) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit`. Cửa sổ của tab incognito luôn có cờ, kể cả khi cửa sổ Activity bên dưới chưa có. Ở tab thường cửa sổ đi theo Activity. Có hai test kiểm cờ thật trên `WindowManager.LayoutParams` của cửa sổ dialog: `inIncognitoTheLayersWindowIsSecureEvenOverAWindowThatIsNot` và `theSearchBarOfTheIncognitoNewTabPageOpensTheLayerForAnIncognitoTab`.

Process death: trạng thái mở là `remember(tab.id)`, không saveable. Test `theLayerDoesNotComeBackOpenWhenTheScreenIsRestored` dùng `StateRestorationTester` để kiểm điều này; đột biến đổi sang `rememberSaveable` làm test này đỏ. Đóng lớp, mở một trang hay Ask Skim đều gọi `search.clear()`: lần tra cứu đang chạy (cả request tới engine) bị huỷ và không giữ lại chữ đã gõ.

## Chữ ký thật

```kotlin
// ui/search/SearchOverlay.kt
@Composable fun searchViewModel(container: AppContainer): SearchViewModel   // viewModel(key = "search") của entry đang gọi
@Composable fun SearchOverlay(search: SearchViewModel, incognito: Boolean,
    onOpenUrl: (String) -> Unit, onAskSkim: (String) -> Unit, onClose: () -> Unit)   // hiện khi còn trong composition

// ui/home/HomeScreen.kt
@Immutable data class HomeActions(onSearch: () -> Unit, onVoice: () -> Unit, onSummarizeTabs: (prompt: String) -> Unit,
    onOpenShortcut: (ShortcutEntity) -> Unit, onAddShortcut: () -> Unit, onEditShortcut: (ShortcutEntity) -> Unit,
    onRemoveShortcut: (ShortcutEntity) -> Unit, onOpenChat: (chatId: Long) -> Unit, onSeeAllChats: () -> Unit)
@Composable fun HomeScreen(state: HomeUiState, actions: HomeActions, toolbar: @Composable () -> Unit, modifier: Modifier = Modifier)
@Composable internal fun ShortcutGrid(shortcuts, onOpen, onEdit, onRemove, onAdd, modifier)

// ui/home/ShortcutMenu.kt
@Composable fun BoxScope.ShortcutMenu(name: String, expanded: Boolean, onEdit: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit)

// ui/home/ShortcutDialog.kt
val ShortcutDialogProperties = DialogProperties(usePlatformDefaultWidth = false)
@Composable fun ShortcutDialogRoute(key: ShortcutDialogKey, container: AppContainer, navigator: SkimNavigator)
@Composable fun ShortcutDialog(dialog: ShortcutDialogViewModel, onClose: () -> Unit)

// ui/incognito/IncognitoNewTabScreen.kt
@Composable fun IncognitoNewTabScreen(voiceAvailable: Boolean, onSearch: () -> Unit, onVoice: () -> Unit,
    toolbar: @Composable () -> Unit, modifier: Modifier = Modifier)

// ui/home/NewTabPageRoute.kt
@Composable fun NewTabPageRoute(tab: Tab, container: AppContainer, navigator: SkimNavigator)

// ui/home/ShortcutDialogViewModel.kt (mục 0)
data class ShortcutDialogUiState(val editing: Boolean, val loaded: Boolean, val canSave: Boolean, val done: Boolean)
//  canSave = loaded && !saving && !done && omniboxAddress(url) != null; save() không làm gì khi URL không phải địa chỉ

// ui/components (phase 2, thêm)
@Composable fun SkimDialogContent(title, confirm, modifier, dismiss, destructiveConfirm, icon, iconTint, text)
fun ShortcutTile(letter: String, position: Int, modifier: Modifier = Modifier, container: Color = surfaceContainerHighest)
```

Cách dựng view model từ container:
- `searchViewModel(container)` dựng `SearchViewModel(container.settings.settings.map { it.searchEngine }, container.suggestionService::suggestions, container.historyRepository, container.bookmarkRepository)` với key `"search"` trong store của entry `BrowserKey`. Vì cùng store và cùng key, 04, 09c và sau này 05/11 dùng chung một instance.
- `NewTabPageRoute` dựng `HomeViewModel` (key `"home"`, chỉ cho tab thường) với `voiceAvailable = VoiceInput.isAvailable(context.applicationContext)`.
- `ShortcutDialogRoute` dựng `ShortcutDialogViewModel(key.shortcutId, container.shortcutRepository, container.appScope)` trong store riêng của entry dialog. Lưu xong hoặc Cancel thì gọi `navigator.remove(key)`.

Cách phase 7 mở lớp tìm kiếm từ địa chỉ của 05/11, trong slot `page` của cùng entry:

```kotlin
val search = searchViewModel(container)
var searching by remember(tab.id) { mutableStateOf(false) }
// sự kiện chạm địa chỉ (không đặt trong LaunchedEffect):
search.open(tab.url.orEmpty(), selectAll = true, incognito = tab.incognito); searching = true
if (searching) SearchOverlay(search, tab.incognito,
    onOpenUrl = { url -> tabs.load(tab.id, url); searching = false; search.clear() },
    onAskSkim = { text -> searching = false; search.clear(); navigator.openAskTabs(text) },
    onClose = { searching = false; search.clear() })
```

## Parity (golden so với PNG; đo bằng ImageMagick theo cột/hàng pixel, 2 px mỗi dp)

| Golden | So với | Kết quả |
|---|---|---|
| `boards/04-home-new-tab` | `04-home-new-tab.png` | Sunny ở y 48, thanh tìm kiếm ở y 132–188 và thẻ hero từ y 212 trùng vị trí. Tile, "See all" (mép phải ở x 395) và thẻ Continue (cao 135, khe 2, favicon ở x 32) lệch ngang tối đa 0,5 dp. Lệch dọc đã biết: thẻ hero cao 98 thay vì 89 (+9,5 dp), "Stack Overflow" vừa một dòng (−16 dp), nên thẻ đầu ở y 634 thay vì 641. |
| `home-04-first` | không có board | Không hero, không Continue, chỉ ô Add. Toolbar 1 tab. |
| `boards/04b-search-suggestions` | `04b-search-suggestions.png` | Mép các khối (thanh y 32–88, Ask Skim 104–176, gợi ý 188–360 với khe 2, history 372–444) trùng từng pixel. Icon cùng x, chữ bắt đầu lệch tối đa 0,5 dp, màu nền, hàng, `tertiary` trùng. Chỉ khác hình chữ: dấu ba chấm cắt muộn hơn. |
| `search-04b-incognito` | không có board | Theme tối, chỉ gợi ý engine; hàng `surfaceContainerLowest` trên `surfaceContainerLow`. |
| `boards/09c-incognito-new-tab` | `09c-incognito-new-tab.png` | Thanh y 32–88, hero ở y 128 (lệch tối đa 1 dp do hình), item 48 ở y 388–536 và 584–732, chú thích ở y 754, toolbar tối với FAB tắt: trùng vị trí. Thân và chú thích xuống dòng ở chữ khác. |
| `home-shortcut-dialog-15` | panel Add shortcut của 15 | Trùng từng pixel với `dialog-add-shortcut-15` đã duyệt ở phase 2 (ImageMagick AE = 0). |
| `home-shortcut-menu-15` | panel Shortcut menu của 15 | Menu ở x 24, y 128 như HTML, hai nhóm 56 cách 2, rộng 220. Ô W có màu `#CCCEFF` như vẽ. Golden không có bóng. |

Đã ghi vào `docs/design-guidelines.md`: 7 dòng trong "Golden đã duyệt" và 5 dòng cuối "Known deviations" (04b là dialog và fade; 04b chưa vẽ: placeholder, incognito, bookmark, bàn phím; Home lần đầu; tên shortcut 2 dòng, ripple, menu phía trên; tiêu đề thẻ Continue 2 dòng). Các lệch hình MaterialShapes, chữ hẹp, hero 98 dp và bóng menu đã có dòng từ phase 2.

## Đột biến

Có 55 đột biến. Cả 55 đều bị bắt; nguồn khôi phục trùng md5 sau mỗi lượt và md5 của cả cây trùng trước và sau. Harness nằm ngoài repo: `scratchpad/p6b-mutate.py`, chép từ `p6c-mutate.py`. Mỗi đột biến chỉ chạy lớp test liên quan.
- Lượt 1 có 50 đột biến: 49 bị bắt, 1 sống (S12: ô rỗng ở incognito hiện placeholder của tab thường). Tôi đã thêm test `inIncognitoTheEmptyFieldAsksForASearchOrAnAddress`.
- Lượt 2 có S12 và 2 đột biến mới cho entry dialog (D08, D09). Hai đột biến này có 3 test route mới. Cả 3 bị bắt.
- Lượt 3 có 3 đột biến cho chốt Save ở mục 0. D12 sống (Save bật khi chưa nạp xong shortcut): test cũ không kiểm `canSave` khi URL đã là địa chỉ mà shortcut chưa đọc xong. Tôi đã thêm test tất định `saveStaysOffUntilTheShortcutBeingEditedIsRead`, dùng DAO giả giữ lần đọc lại. Lượt 4 chạy D12, D06, D07: cả 3 bị bắt.

Mọi test bắt đột biến đều là test của đúng hành vi bị đổi.

| Nhóm | Số | Đột biến | Test bắt |
|---|---|---|---|
| `NewTabPageRoute` | 20 | cờ incognito sai vào `search.open`; Enter không nạp vào tab; lớp không đóng sau khi mở trang; đóng lớp không xoá chữ; FAB 04 mở 06 thay vì 06b; FAB 09c bật; Tabs của 09c mở 09; More mở menu sai tab; Add mở dialog có id; Edit mở dialog không id; Remove không xoá; hero mất prompt; thẻ Continue mở 04c; bỏ kết quả giọng nói; không bắt `ActivityNotFoundException`; mic hiện khi không có app giọng nói; trạng thái mở được lưu (`rememberSaveable`); Ask Skim không đóng lớp; entry dialog không rời back stack; entry dialog bỏ id của key | `NewTabPageRouteTest` |
| `HomeScreen`, `ShortcutMenu` | 10 | mic hiện khi không có giọng nói; hero hiện khi N = 0; Continue hiện khi rỗng; nhấn giữ không mở menu; ô không đổi `secondaryContainer`; hero gửi prompt rỗng; Edit/Remove đảo; chạm shortcut không mở; thẻ Continue mở sai chat; menu Edit gọi Remove | `HomeScreenTest` |
| Dialog shortcut | 10 | không đóng sau khi lưu; Save luôn bật; Edit mang tiêu đề Add; URL không focus; Cancel lưu; `canSave` nhận chữ không phải địa chỉ; `save()` nhận chữ không phải địa chỉ; Save lần hai khi đang lưu; Save bật khi đang lưu; Save bật trước khi nạp | `ShortcutDialogTest`, `ShortcutDialogViewModelTest` |
| `SearchOverlay`, `selectAll` | 13 | cửa sổ incognito chỉ `Inherit`; bàn phím học chữ ở incognito; Back không đóng; Clear đóng lớp; mũi tên không chép; gợi ý giống địa chỉ mở như địa chỉ; Ask Skim không cắt khoảng trắng; Enter mở chữ thô; ô không focus khi mở; hàng Ask Skim luôn hiện; dòng trang mở host; placeholder incognito sai; `open(selectAll = true)` không chọn hết | `SearchOverlayTest` |
| `IncognitoNewTabScreen` | 2 | mic hiện khi không có giọng nói; thanh tìm kiếm là của 04 | `IncognitoNewTabScreenTest` |

## Lệch spec và lý do

1. **Chữ ký `SearchOverlay`** khác Architecture. Không có `SearchBarState`, `initialText`, `selectAll`, vì 6A đã đưa chúng vào `SearchViewModel.open(...)`, gọi trong sự kiện mở. Overlay nhận view model và `incognito`; `incognito` chỉ dùng cho FLAG_SECURE, bàn phím và placeholder.
2. **Dialog thay `ExpandedFullScreenContainedSearchBar`** (lý do ở mục Quyết định). Lớp hiện bằng fade, không có hiệu ứng thanh trượt lên từ 04, và đóng ngay không có animation.
3. **File của phase 1–6A đã sửa:**
   - `ui/components/SkimDialogs.kt` (+100/−56, phần lớn là dời code): tách `DialogSurface` khỏi `DialogFrame` và thêm `SkimDialogContent`. Lý do: `DialogSceneStrategy` tự mở một cửa sổ `Dialog`. Nếu gọi `SkimAlertDialog` bên trong, sẽ có dialog lồng dialog và hai lớp dim (60% của cửa sổ ngoài cộng 32%). `SkimAlertDialog` và `ChoiceDialog` vẫn như cũ; mọi golden phase 2 không đổi (verify 3 lần).
   - `ui/components/ShapeTiles.kt` (+9/−2): thêm `container` cho `ShortcutTile`, để ô chuyển `secondaryContainer` khi menu mở. Mặc định giữ màu cũ.
   - `ui/navigation/SkimNavDisplay.kt` (+7/−1): slot `newTabPage` và entry dialog.
   - 6A: `ShortcutDialogViewModel.kt` (+12/−22) và test của nó (+53/−10), theo mục 0.
   - `SkimMenu.kt` không đổi. Tôi đã thử thêm tham số `offset`, nhưng offset của material3 cũng cộng vào vị trí "phía trên", làm menu đè 8 dp lên ô. Tôi bỏ thay đổi đó bằng `git checkout -- SkimMenu.kt`; file này không có thay đổi chưa commit nào trước đó, và md5 trùng ảnh chụp đầu phiên. Thay vào đó, `ShortcutMenu` dùng một khung neo riêng: 8 dp vào trong ô và 8 dp dưới tên.
4. **Lưới shortcut không lazy** (Column gồm các Row 4 cột) thay vì `LazyVerticalGrid`. Lưới lazy không đặt được trong cột cuộn của Home, và số shortcut ít.
5. **Tên shortcut tối đa 2 dòng**, như "Stack Overflow" trên board, rồi dấu ba chấm. Risk assessment ghi 1 dòng, nhưng bản vẽ cho tên xuống dòng.
6. **Dialog đóng bằng `navigator.remove(key)`** thay vì `navigator.back()` như gợi ý của 6A. Cách này không bao giờ gỡ nhầm màn dưới khi key đã rời back stack.
7. **Bàn phím incognito không học chữ gõ**: `IME_FLAG_NO_PERSONALIZED_LEARNING`, đặt qua `InterceptPlatformTextInput`. Prompt không ghi điều này. Tôi thêm vì đây là ranh giới dữ liệu incognito, giống chốt phase 4 đã làm cho WebView.
8. **Thêm public**: `searchViewModel(container)`, `ShortcutDialogProperties`, `ShortcutDialogRoute`, `SkimDialogContent`. Mục đích là để phase 7 và các dialog sau dùng chung, không phải chép lại factory.
9. **Ask Skim đóng lớp rồi mới mở 06b**. Thẻ hero gửi chính tiêu đề "Summarize my N open tab(s)" làm prompt.

## Việc cho controller (chỉ kiểm được trên thiết bị)

- Mic mở hộp thoại giọng nói; chữ nói vào 04b với con trỏ ở cuối (04 tối nếu từ 09c). Không có app giọng nói thì không có mic.
- Gợi ý thật từ 4 engine (đổi engine qua phase 8 hoặc tạm sửa DataStore).
- Nhấn giữ shortcut: menu neo dưới ô và ô đổi màu. Shortcut ở hàng sát toolbar thì menu mở phía trên.
- Remove: snackbar "Shortcut removed" với Undo nằm trên toolbar. Undo vẫn chạy sau khi đã mở shortcut khác.
- Khi mở 04b:
  - Bàn phím lên ngay, con trỏ ở ô.
  - Không có chớp dim và không có animation phóng của cửa sổ dialog: cờ dim và animation tắt sau khi cửa sổ được tạo.
  - Danh sách cuộn được trên bàn phím (IME inset của cửa sổ dialog).
  - Gboard hiện biểu tượng incognito khi đang ở tab incognito.
- Chọn sẵn URL: chỉ lộ ra khi phase 7 nối địa chỉ của 05/11.
- Back:
  - Back đóng 04b trước, không rời app.
  - Back ở Home rời app như phase 5.
  - Back trong dialog shortcut đóng dialog, và chỉ có một lớp dim 32%.
- Xoay màn hình khi 04b đang mở: Activity không bị tạo lại nhờ `configChanges`, nên lớp được kỳ vọng vẫn mở và bố cục lại.
- Theme tối của 09c và 04b incognito. FLAG_SECURE: ảnh chụp màn hình khi 04b mở ở tab incognito phải đen, kể cả vùng của cửa sổ dialog.

## Câu hỏi chưa giải quyết

1. Có chấp nhận 04b là dialog với fade, không có hiệu ứng thanh trượt lên từ 04, không? Nếu muốn hiệu ứng đó, cần thay thanh thu gọn của 04/09c bằng `SearchBar` của material3 (đổi golden phase 2) hoặc tự dựng animation trượt.
2. Thanh của 04b incognito nên là `surfaceContainerHigh` (như 04b, đang dùng) hay `surfaceContainerHighest` (như 09c thu gọn)?
3. Tên shortcut nên là 2 dòng (theo board, đang dùng) hay 1 dòng (theo Risk assessment)?
4. Ask Skim từ 04b có nên giữ 04b mở phía sau 06b không? Hiện tại 04b đóng trước khi 06b mở.
5. Các phase sau (7, 8, 11) định dùng `SkimAlertDialog`/`ChoiceDialog` trong entry dialog. Với `DialogSceneStrategy`, chúng nên dùng `SkimDialogContent` cùng `DialogProperties(usePlatformDefaultWidth = false)`. Hiện chưa có bản không-cửa-sổ của `ChoiceDialog`. Cần thống nhất cách này trước phase 7.
6. Entry `ShortcutDialogKey` trong `SkimNavDisplay` chưa có test JVM: dựng `SkimNavDisplay` cần `BrowserHost`. Nội dung entry (`ShortcutDialogRoute`) đã có test với navigator thật. Phần metadata cần kiểm trên thiết bị.
