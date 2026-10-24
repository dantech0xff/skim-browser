---
phase: 2
title: "Design-system components"
status: completed
priority: P1
effort: "23h"
dependencies: [1]
---

# Phase 2: Design-system components

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- `design/spec/components.md` (mọi mục), `design/spec/tokens.md` (màu, type, shape, motion, spacing, elevation), `design/spec/navigation-map.md` mục "Rules for every screen".
- Scout: `plans/reports/scout-261020-2319-design-inventory.md` §1 (số đo chrome dùng chung), §3(e) (19 mâu thuẫn và bên thắng), §4 (icon).
- Researcher A: `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §4 (bảng API material3 1.5.0-beta01, opt-in, lệch default), §8 (toolbar 24dp, snackbar, IME).
- Red team: `plans/261020-1611-skim-design-implementation/reports/red-team-assumption-destroyer.md` Finding 7, `red-team-failure-mode-analyst.md` Finding 8, `red-team-scope-complexity-critic.md` Finding 1, 6.
- PNG tham chiếu: `design/screens/18-components-setup.png`, `design/screens/17-tokens.png`, `design/screens/15-dialogs-menus-snackbars.png`, `design/screens/05-browsing--loading.png`, `design/screens/09-tabs.png`.

## Overview

Xây toàn bộ component dùng chung trong `com.dantech.skimbrowser.ui.components` theo đúng số đo của bản vẽ (bản vẽ thắng default component), cộng `SnackbarController` với action dạng dữ liệu, `UndoRegistry` sống theo process, tiện ích chữ (`UiText`, thời gian tương đối, nhóm ngày), cờ reduced motion, helper kiểm a11y cho mọi screenshot test, và file `docs/design-guidelines.md` với bảng "Known deviations" để mỗi phase ghi lệch so với PNG. Mỗi component có `@Preview` sáng/tối và ảnh Roborazzi làm golden. Không component nào biết tới ViewModel, Room hay WebView.

## Key insights

- `FloatingToolbarDefaults.ScreenOffset` là 16dp, thiết kế cần 24dp trên nav bar: tự đặt `Modifier.navigationBarsPadding().padding(bottom = 24.dp)`; component không tự xử lý insets (researcher A §4, §8).
- `StandardFloatingActionButton` không có `enabled`: trạng thái tắt của AI FAB phải tự vẽ (nền `onSurface` 10%, tối 12%; nội dung 38%; không shadow; icon `ic_auto_awesome` outline) và bỏ click (scout §1, mâu thuẫn #1).
- `ListItemDefaults.segmentedShapes` cho góc ngoài 16; bản vẽ cần 20 → tự dựng `ListItemShapes` (constructor 6 shape, đã kiểm trong `material3.aar` 1.5.0-beta01). Màu container mặc định là `surface`, bản vẽ dùng `surfaceContainerLowest`.
- `DropdownMenuItem(text, onClick)` bind vào overload cũ không có shape theo nhóm; phải dùng overload có `shape` cùng `MenuDefaults.itemShape(i, n).shape` trong `DropdownMenuGroup(shapes = MenuDefaults.groupShape(i, n))`.
- `rememberModalBottomSheetState` và Slider stateless đã deprecated: dùng `rememberBottomSheetState(...)` và `Slider(state = rememberSliderState(...))`. `ModalBottomSheet` đã đệm IME, không thêm `imePadding()`.
- `LinearWavyProgressIndicator` gap mặc định 4dp, bản vẽ 8dp → truyền `gapSize = 8.dp`. Track 06c là `tertiaryContainer` (mâu thuẫn #2), 05 giữ `secondaryContainer`.
- Opt-in còn lại: `ExperimentalMaterial3ExpressiveApi` cho `LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes`, `toShape()`, `PullToRefreshDefaults.LoadingIndicator`; `ExperimentalMaterial3Api` cho `ModalBottomSheet`/`rememberBottomSheetState`. Đặt `@OptIn` cục bộ trong file component.
- Ba nút nhỏ hơn 48 (Close tab 36, Group options 40, copy/feedback 40) và action snackbar cao 36: giữ kích thước hình, vùng chạm 48 qua `minimumInteractiveComponentSize()` (mâu thuẫn #16).
<!-- Red Team: Snackbar/Undo giữ lambda của navigator đã chết — 2026-10-21 -->
- `SnackbarController` sống theo process nên không được giữ lambda bắt navigator/Activity. Action là dữ liệu (`SwitchToTab`, `Undo`, `EditBookmark`, `OpenDownload`, `ViewImported`); host đang hiện (thuộc Activity hiện tại) dịch dữ liệu thành lời gọi của chính nó. Undo chạy trên `appScope` qua `UndoRegistry`, nên vẫn chạy khi màn gốc đã rời back stack.
<!-- Red Team: Bỏ registry nhiều host snackbar — 2026-10-21 -->
- Chỉ một host dùng `SnackbarController` (cửa sổ chính, do phase 5 đặt). Snackbar duy nhất phải nằm trong cửa sổ sheet là "Copied to clipboard" của 06/06b (SDK ≤ 32): sheet 06/06b tự giữ `SnackbarHostState` cục bộ (phase 11/12), không đi qua controller.
<!-- Red Team: Cổng parity hai lần — 2026-10-21 -->
- Golden Roborazzi của từng phase, đã được người duyệt so với PNG thiết kế, chính là kiểm tra parity; lệch được ghi ngay trong phase đó vào `docs/design-guidelines.md`. Ảnh cấp board đặt tên trùng PNG thiết kế để task `designParity` (phase 13) ghép cặp tạo ảnh diff.

## Requirements

Chức năng (mỗi mục là một composable stateless, có `Modifier` và contentDescription truyền vào khi là icon button):
- Floating toolbar trình duyệt (Back, Forward, Tabs, More + AI FAB), nút Tabs đếm số, AI FAB có trạng thái tắt vẽ tay; toolbar tab switcher (09/09b) với FAB `primaryContainer`.
- Address bar (lock / không khoá / lỗi tĩnh, địa chỉ là nút, Reader view tuỳ chọn, wavy progress ở cạnh dưới).
- Ô tìm kiếm thu gọn (04, biến thể 09c tối).
- Large title scaffold (04c, 10, 12, 13, 14) có slot tìm kiếm thay tiêu đề; section label.
- Segmented list (item 1 dòng/2 dòng, leading tile, trailing slot) + biến thể tĩnh 48 của 09c.
- Connected toggle group 2 lựa chọn (09/09b, 08b).
- Nút M/S/XS, chip gợi ý XS outlined, chip trích dẫn XS tonal (có trạng thái tắt), nút icon nhỏ có vùng chạm 48, nút Send 40, toggle Text size.
- Bottom sheet wrapper (scrim 32%, half→full; handle và shape dùng lại cho panel 08b), dialog wrapper (344, icon tuỳ chọn, nút xác nhận phá huỷ màu `error`), dialog lựa chọn radio, text field outlined, menu nhóm.
- `SnackbarController` (một snackbar tại một thời điểm, 10 s nếu có action, 4 s nếu không) + `SkimSnackbarHost` (đặt 8dp trên toolbar hoặc 16dp trên nav bar) + `UndoRegistry`.
- Switch, slider cỡ chữ, loading indicator, wavy progress (trang/AI), badge, card (thường, hero AI, answer), tab card, chat (bubble, câu trả lời, ô nhập), các tile hình MaterialShapes.
- `UiText`, định dạng thời gian tương đối và nhóm ngày; `rememberReducedMotion()`.
- Helper test `assertA11y()` (mọi node bấm được có nhãn và vùng chạm ≥ 48dp) và `captureBoard(name)` (ảnh cấp board trùng tên PNG thiết kế), dùng trong mọi screenshot test của phase 2–12.
- `docs/design-guidelines.md` với mục "Known deviations" (bảng: board/component, lệch, lý do, phase).

Phi chức năng:
- Không component nào giữ state nghiệp vụ; mọi màu lấy từ `MaterialTheme.colorScheme` (không hex), mọi chữ từ `strings.xml`.
- Reduced motion (Remove animations, `ANIMATOR_DURATION_SCALE == 0`): loading indicator là ảnh tĩnh Cookie9Sided, sóng phẳng (tokens.md mục Motion).
- Mọi vùng bấm ≥ 48dp.

## Architecture

```
ui/theme/ReducedMotion.kt          rememberReducedMotion(): Boolean
ui/text/UiText.kt                  sealed UiText { Res(id, args), Plural(id, count, args), Raw(text) } + @Composable asString()
ui/text/RelativeTime.kt            relativeTimeText(epochMs, now, zone): UiText   // Just now, %d min ago, %d h ago, Yesterday, ngày (DateUtils)
ui/text/DateBuckets.kt             enum DateBucket { Today, Yesterday, EarlierThisWeek, Earlier }; bucketOf(epochMs, now, zone)
ui/snackbar/SnackbarController.kt  process-scoped, chỉ giữ dữ liệu
ui/snackbar/UndoRegistry.kt        process-scoped, chạy undo/finalize trên appScope
ui/components/*.kt                 composable stateless
```

<!-- Red Team: Snackbar action dạng dữ liệu + UndoRegistry — 2026-10-21 -->
`SnackbarController` và `UndoRegistry`:

```kotlin
sealed interface SnackbarAction {
    data class SwitchToTab(val tabId: String) : SnackbarAction          // "Switch"
    data class Undo(val token: UndoToken) : SnackbarAction              // "Undo"
    data class EditBookmark(val bookmarkId: Long) : SnackbarAction      // "Edit"
    data class OpenDownload(val uri: Uri, val mime: String) : SnackbarAction   // "Open"
    data class ViewImported(val folderId: Long) : SnackbarAction        // "View"
}
class SnackbarController(private val undo: UndoRegistry) {
    data class Message(val id: Long, val text: UiText, val action: SnackbarAction?, val isError: Boolean)
    val current: StateFlow<Message?>
    fun show(text: UiText, action: SnackbarAction? = null, isError: Boolean = false)   // thay message đang hiện
    fun onActionClicked(id: Long)                 // host gọi sau khi đã thực thi action
    fun onDismissed(id: Long)                     // hết giờ, vuốt, bị message mới thay, host rời composition
    fun dismissFor(kind: UndoKind)                // dùng khi xoá hàng loạt: đóng snackbar Undo cùng loại
}
enum class UndoKind { TAB, HISTORY, BOOKMARK, SHORTCUT, SKIM_CHAT }
@JvmInline value class UndoToken(val id: Long)
class UndoRegistry(private val appScope: CoroutineScope) {
    fun register(kind: UndoKind, undo: suspend () -> Unit, finalize: suspend () -> Unit = {}): UndoToken
    fun undo(token: UndoToken)       // chạy undo trên appScope, đúng một lần; token đã huỷ/finalize thì bỏ qua
    fun finalize(token: UndoToken)   // chạy finalize trên appScope, đúng một lần
    fun cancelAll(kind: UndoKind)    // finalize mọi token đang chờ của loại đó; Undo của chúng trở nên vô hiệu
}
```

- Hợp đồng: một message có `Undo(token)` mà bị đóng vì bất kỳ lý do nào ngoài bấm action (hết giờ, vuốt, bị thay, host rời composition, Activity bị huỷ) thì controller gọi `undo.finalize(token)`. Bấm Undo → `undo.undo(token)`. Lambda trong registry chỉ được bắt đối tượng sống theo process (repository, `TabManager`), không bắt navigator/Activity.
- "One at a time" = message mới thay message cũ (cũ coi như bị đóng), không xếp hàng.
- `SkimSnackbarHost(controller, placement, onAction: (SnackbarAction) -> Unit)`: gọi `SnackbarHostState.showSnackbar(duration = if (action != null) Long else Short)` (M3: Long 10 s, Short 4 s, đã có điều chỉnh theo cài đặt trợ năng); khi `DisposableEffect` của host bị huỷ trong lúc đang hiện → `controller.onDismissed(id)`. Nhãn action suy từ loại (`action_switch`, `action_undo`, `action_edit`, `action_open`, `action_view`). Phase 5 cung cấp `onAction` (dịch sang navigator/Intent).
- `SnackbarPlacement`: `AboveToolbar` = `navigationBars` + 24 + 64 + 8 = 96dp; `Bottom` = `navigationBars` + 16dp cho màn không có toolbar (quyết định nhỏ của planner).
- Snackbar lỗi (import thất bại): tối thiểu 68, padding 14×16, 2 dòng, `liveRegion = Assertive` (tương đương `role=alert`).

Toolbar:

```kotlin
object ToolbarMetrics { val Height = 64.dp; val BottomOffset = 24.dp; val FabGap = 8.dp }
@Composable fun BrowserToolbar(canGoBack: Boolean, canGoForward: Boolean, tabCount: Int, incognito: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onTabs: () -> Unit, onMore: () -> Unit,
    aiFab: @Composable () -> Unit, modifier: Modifier = Modifier)
@Composable fun AiFab(enabled: Boolean, contentDescription: String, onClick: () -> Unit)
@Composable fun TabSwitcherToolbar(incognito: Boolean, aiEnabled: Boolean, onAskSkim: () -> Unit,
    onNewIncognitoTab: (() -> Unit)?, onCloseAll: () -> Unit, onNewTab: () -> Unit)
```

- `BrowserToolbar` dùng `HorizontalFloatingToolbar(expanded = true, floatingActionButton = aiFab, colors = standardFloatingToolbarColors(toolbarContainerColor = if (dark) surfaceContainerHigh else surfaceContainer))`, padding nội dung 0×8, khoảng cách nút 4. Cả cụm toolbar + FAB căn giữa ngang, đáy cách nav bar 24 (`design/screens/05-browsing--loading.png`). Elevation giữ default Compose (mâu thuẫn #14 theo tokens).
- `TabsCountIcon(count)`: ô 22×22, viền 2 `LocalContentColor`, bo 7, số `labelMediumEmphasized` (mâu thuẫn #11: theo token 600). Số ≥ 100 hiện `99+` (string NT `tabs_count_overflow`, quyết định nhỏ). cd do caller truyền (`cd_tabs_open` hoặc `cd_incognito_tabs_open`, plural).
- `AiFab(enabled = false)`: vẽ `Surface` 56 bo 16 màu `onSurface.copy(alpha = if (dark) .12f else .10f)`, icon `ic_auto_awesome` màu `onSurface` 38%, `semantics { disabled() }`, không `clickable`, không shadow.

Address bar:

```kotlin
enum class SiteIndicator { Secure, NotSecure, Error }
@Composable fun AddressBar(address: String, indicator: SiteIndicator, readerAvailable: Boolean, loadProgress: Float?,
    onSiteInfo: () -> Unit, onEditAddress: () -> Unit, onReader: () -> Unit)
```

- Khung ngoài padding 8×16 trên `surface`, thanh 56 bo full `surfaceContainerHigh`, padding 0×4, gap 4. `Secure` → IconButton 48 `ic_lock` 20, cd `cd_site_info_secure`. `NotSecure` (trang `http://`, chưa thiết kế) → IconButton `ic_public` 20, cd `cd_site_info` `[DEFAULT — chờ validate]` D8. `Error` (11) → `Icon` tĩnh `ic_error` 20 cd `cd_page_didnt_load`, padding 0 16 0 4, không nút Reader (mâu thuẫn #7).
- Địa chỉ là `TextButton` `bodyLarge` `onSurface`, một dòng, ellipsis, cd `cd_edit_address` với tham số địa chỉ.
- `loadProgress != null` → `LinearWavyProgressIndicator(progress = { p }, color = primary, trackColor = secondaryContainer, gapSize = 8.dp, stroke 4, amplitude 3, wavelength 40)` đặt ở cạnh dưới thanh, cd `cd_loading_page`; reduced motion → amplitude 0.

Danh sách:

```kotlin
fun segmentedShapes(index: Int, count: Int): ListItemShapes      // ngoài 20, trong 4, một item 20 toàn bộ
@Composable fun SkimListItem(headline: String, supporting: String? = null, leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null, index: Int, count: Int, onClick: (() -> Unit)?, onLongClick: (() -> Unit)? = null,
    headlineColor: Color = Color.Unspecified, headlineWeight: FontWeight? = null)
@Composable fun StaticListItem(icon: Int, text: String, index: Int, count: Int)   // 09c: 48 cao, không tương tác
@Composable fun SectionLabel(text: String, first: Boolean)
@Composable fun SegmentedColumn(content: @Composable ColumnScope.() -> Unit)   // margin 0×16, spacedBy(ListItemDefaults.SegmentedGap)
```

Các component còn lại (tham số chính):
- `LargeTitleScaffold(title, onBack, actions, searchState: TextFieldState?, onCloseSearch, snackbarPlacement, content: LazyListScope.() -> Unit)` dùng `LargeFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior`, nền `surfaceContainerLow`, title `headlineLargeEmphasized`. Khi `searchState != null`: ô nhập thay tiêu đề trong hàng 64 (hành vi theo navigation-map 04c/13/14; giao diện chưa vẽ, `[DEFAULT — chờ validate]` D8).
- `ConnectedToggleGroup(options: List<String>, selected: Int, onSelect, contentDescription)` với `ToggleButton` + `ButtonGroupDefaults.connected{Leading,Trailing}ButtonShapes()`, gap `ConnectedSpaceBetween`, cao 40, nút chọn có `ic_check` và `labelLargeEmphasized`, nút còn lại `surfaceContainer`.
- `SkimButton(size: M|S|XS, style: Filled|Tonal|Text|Outlined, icon?)` theo `ButtonDefaults.{MediumContainerHeight, MinHeight, ExtraSmallContainerHeight}` và `contentPaddingFor/textStyleFor`; `AiSuggestionChip` (tránh trùng tên `SuggestionChip` của material3; XS outlined 32, bo 16, viền 1 `outlineVariant`); `CitationChip(paragraph: Int, enabled: Boolean)` (XS tonal + `ic_format_quote` 18, text `ai_citation_paragraph`; tắt khi trang đã đổi, phase 11); `CompactIconButton(iconSize, visualSize, cd)` (vùng chạm 48); `SendButton(enabled)` / `StopButton` (`FilledIconButton` 40 `primary`, `ic_arrow_upward`/`ic_stop_filled` 20); `TextSizeToggle(checked)` (`FilledTonalIconToggleButton` 40, bo 12, `ic_format_size` ↔ `ic_format_size_filled`).
- `SkimBottomSheet(onDismiss, skipPartiallyExpanded: Boolean = true, paneTitle: String, content)` dùng `ModalBottomSheet(sheetState = rememberBottomSheetState(...))`, bo trên 28, `surfaceContainerLow`, handle 32×4 bo 2 `onSurfaceVariant` 40% trong hàng 36, `scrimColor = Black.copy(.32f)`, `paneTitle` qua semantics; `SheetHandle()` và `sheetShape` public để panel không modal 08b (phase 10) dùng lại. Hàm `expand()/partialExpand()` lộ ra qua `SkimSheetState` để 06 hạ sheet khi bấm citation. Không có host snackbar bên trong.
- `SkimAlertDialog(title, text, icon?, iconTint, confirm, dismiss, destructiveConfirm: Boolean)`: `DialogProperties(usePlatformDefaultWidth = false)` + width 344, bo 28, `surfaceContainerHigh`, padding 24, title `headlineSmall` (căn giữa khi có icon), body `bodyMedium onSurfaceVariant`, nút text cao 40 cách nhau 8. Chỉ nút "Delete" của Delete all AI data dùng `error` (mâu thuẫn #6).
- `ChoiceDialog<T>(title, options, selected, label: (T) -> String, onSelect, onDismiss)` (Search engine, Theme, Move to folder: chọn là lưu + đóng; chỉ có Cancel); item radio 48.
- `SkimTextField(state, label, focusRequester)` dạng `OutlinedTextField`.
- `SkimMenu(expanded, onDismiss, groups: List<List<MenuEntry>>, anchorContentDescription)` với `DropdownMenuPopup` + `DropdownMenuGroup` + `DropdownMenuItem(shape = MenuDefaults.itemShape(i, n).shape, leadingIcon)`; rộng 220, nhóm `surfaceContainer` bo 16 padding 4 cách 2, item 48 bo 12 gap 12 `labelLarge`, icon 24 `onSurfaceVariant`; item phá huỷ ở nhóm riêng nhưng chữ `onSurface`.
- `SkimSwitch(checked, onCheckedChange)` (thumb `ic_check` khi bật) và `Modifier.toggleableRow(...)` để cả hàng 72 là một switch (role Switch, label = text hàng).
- `ReaderSizeSlider(percent, onPercent)` dùng `rememberSliderState(value, steps = 11, valueRange = 80f..200f)`, màu active `primary`, inactive `secondaryContainer`, stop dot `primary`; `stateDescription` = `reader_size_percent`.
- `AiLoadingIndicator()` (`ContainedLoadingIndicator` 48, `tertiaryContainer`/`onTertiaryContainer`, cd `cd_writing_summary` do caller truyền; reduced motion → `Box` 48 tròn + `MaterialShapes.Cookie9Sided` tĩnh 30); `AiWavyProgress()` (indeterminate, `tertiary` trên track `tertiaryContainer`).
- `CountBadge(count)` (24 cao, `secondaryContainer`/`onSecondaryContainer`, `labelMediumEmphasized`).
- `SkimCard(radius = 20|28, color)`, `AiHeroCard(title, body, onClick)` (min 88, bo 28, `tertiaryContainer`, tile 48 Cookie9Sided nền `onTertiaryContainer` icon `tertiaryContainer`, `ic_arrow_forward`), `AnswerCard` (trắng `surfaceContainerLowest`, bo 20, padding 16 16 8).
- `TabCard(title, letter, preview: ImageBitmap?, isCurrent, hasSkimChat, incognito, onClick, onClose, modifier)`: bo 20, `surfaceContainerLow` (tối: `surfaceContainer`, mâu thuẫn #4), hàng tiêu đề 44 (favicon 20 Cookie4Sided chữ 10/12 600, `labelMedium`, `CompactIconButton` 36 `ic_close` 18 cd `cd_close_tab`), preview 92 bo 12 `surfaceContainerLowest` (bitmap crop top), vòng hiện tại 3 inset `primary`, badge Sunny 24 `tertiaryContainer` góc phải dưới cách 8 khi có chat.
- `UserBubble(text)` (max 300, bo 20 20 4 20, `secondaryContainer`, padding 10×16), `AnswerText(text)` (`bodyMedium`, padding phải 24), `ChatInputPill(state, placeholder, enabled, generating, onSend, onStop)` (56, bo 28, `surfaceContainerHigh`, padding 0 8 0 20; tắt: nền `onSurface` 10%, chữ 38%, Send tắt).
- Tile: `SunnyMark(size, iconSize, brand: Boolean = false)` (AI: `tertiaryContainer`/`onTertiaryContainer`; `brand = true` cho logo 01: `primary`/`onPrimary`, mâu thuẫn #18), `FaviconLetterTile(letter, size, textStyle, container, content)` (Cookie4Sided), `ShortcutTile(letter, position)` (64 `surfaceContainerHighest`, chữ 22/28 500 `onSecondaryContainer`, shape theo `shortcutShapeFor(position)` = vòng Cookie9Sided, Clover4Leaf, SoftBurst, Cookie4Sided, Flower, Cookie12Sided, Pentagon, mâu thuẫn #13), `AddShortcutTile()` (vòng 64 viền đứt 2 `outlineVariant` + `ic_add`), `IconTile(icon, shape, container, content, size = 40)` (Circle mặc định; folder bo 12), `ShapeArt(shape, size, icon, iconSize)` (art lỗi 11, art 01, hero 09c). Hàm `siteLetter(host: String?, title: String?): String` lấy chữ đầu của host bỏ `www.` (hoặc tên shortcut), viết hoa.

Test helper (`app/src/test/java/com/dantech/skimbrowser/testing/`):
- `A11yAssertions.kt`: `fun SemanticsNodeInteractionsProvider.assertA11y()` duyệt mọi node có `OnClick`/`ToggleableState`: phải có text hoặc contentDescription, `touchBoundsInRoot` ≥ 48dp.
- `BoardCapture.kt`: `fun captureBoard(name: String)` ghi `app/src/test/screenshots/boards/<name>.png` với `name` trùng tên PNG trong `design/screens/` (ví dụ `05-browsing--loading`).

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/theme/ReducedMotion.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/text/UiText.kt`, `RelativeTime.kt`, `DateBuckets.kt` (cùng thư mục `ui/text/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/snackbar/SnackbarController.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/snackbar/UndoRegistry.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/components/FloatingToolbars.kt`, `AddressBar.kt`, `SearchBarField.kt`, `LargeTitleScaffold.kt`, `SegmentedList.kt` (gồm `SectionLabel`, `segmentedShapes`, `SkimListItem`, `StaticListItem`, `SegmentedColumn`), `ConnectedToggleGroup.kt`, `SkimButtons.kt`, `SkimBottomSheet.kt`, `SkimDialogs.kt`, `SkimMenu.kt`, `SkimSnackbarHost.kt`, `SkimSwitch.kt`, `ReaderSizeSlider.kt`, `Progress.kt`, `CountBadge.kt`, `SkimCards.kt`, `TabCard.kt`, `Chat.kt`, `ShapeTiles.kt` (cùng thư mục `ui/components/`)
- `app/src/test/java/com/dantech/skimbrowser/testing/A11yAssertions.kt`, `app/src/test/java/com/dantech/skimbrowser/testing/BoardCapture.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/components/ToolbarScreenshotTest.kt`, `BarsScreenshotTest.kt` (address bar 3 indicator + loading, search field sáng/09c), `ListsScreenshotTest.kt` (segmented, section label, toggle group, large title), `OverlaysScreenshotTest.kt` (sheet, dialog, choice dialog, menu, snackbar thường/lỗi), `ContentScreenshotTest.kt` (card, tab card, chat, chip, badge, switch, slider, progress, tile; sáng + tối + reduced motion cho progress) — mỗi test gọi `assertA11y()`
- `app/src/test/java/com/dantech/skimbrowser/ui/components/AiFabTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/snackbar/SnackbarControllerTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/snackbar/UndoRegistryTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/text/RelativeTimeTest.kt`, `app/src/test/java/com/dantech/skimbrowser/ui/text/DateBucketsTest.kt`
- `app/src/test/screenshots/components/*.png` (golden do `recordRoborazziDebug` sinh, commit)
- `docs/design-guidelines.md` (chỉ mục "Known deviations" ở phase này; phase 13 hoàn thiện phần còn lại)

Không sửa file nào khác (song song an toàn với phase 3).

## Implementation steps

1. `ReducedMotion.kt`: đọc `Settings.Global.ANIMATOR_DURATION_SCALE` qua `LocalContext`, trả `true` khi bằng 0; tính lại khi `LocalConfiguration` đổi.
2. `UiText`, `RelativeTime` (< 1 phút `time_just_now`; < 60 phút plural `time_minutes_ago`; cùng ngày plural `time_hours_ago`; hôm qua `date_yesterday`; xa hơn `DateUtils.formatDateTime(FORMAT_SHOW_DATE or FORMAT_ABBREV_MONTH)`), `DateBuckets` (tuần bắt đầu theo `WeekFields.of(Locale.getDefault())`). Test với clock và `ZoneId` cố định.
3. `UndoRegistry` + test (undo chạy đúng một lần trên scope test; `cancelAll(kind)` làm undo của token đó vô hiệu và chạy finalize; token lạ bị bỏ qua).
4. `SnackbarController` + `SkimSnackbarHost` + `SnackbarPlacement`; test: `show` hai lần liên tiếp chỉ giữ message thứ hai và finalize token Undo của message thứ nhất; bấm Undo không finalize; host bị huỷ khi đang hiện → finalize; action → `Long`, không action → `Short`; `dismissFor(kind)` đóng message Undo cùng loại.
5. `ShapeTiles.kt` trước (các component khác dùng): `SunnyMark`, `FaviconLetterTile`, `ShortcutTile`, `AddShortcutTile`, `IconTile`, `ShapeArt`, `siteLetter`, `shortcutShapeFor`.
6. `FloatingToolbars.kt` (+ `TabsCountIcon`, `AiFab`), `AddressBar.kt`, `SearchBarField.kt` (04: `ic_search` + placeholder `search_hint` + mic 48 cd `cd_voice_search` (ẩn khi `onVoice == null`); 09c: `surfaceContainerHighest`, leading `ic_visibility_off` 20 trong ô 48, placeholder `incognito_search_hint`, mâu thuẫn #3). Ô thu gọn chỉ là nút mở 04b, không nhận nhập.
7. `SegmentedList.kt`, `LargeTitleScaffold.kt`, `ConnectedToggleGroup.kt`, `SkimButtons.kt`, `CountBadge.kt`, `SkimSwitch.kt`, `ReaderSizeSlider.kt`, `Progress.kt`.
8. `SkimBottomSheet.kt`, `SkimDialogs.kt`, `SkimMenu.kt`, `SkimSnackbarHost.kt`.
9. `SkimCards.kt`, `TabCard.kt`, `Chat.kt`.
10. `@Preview` sáng/tối cho mọi composable public (`SkimTheme(dynamicColor = false)`).
11. Helper `A11yAssertions.kt`, `BoardCapture.kt`; test Roborazzi (Robolectric `@GraphicsMode(NATIVE)`, `v2.createComposeRule`, `dynamicColor = false`), mỗi test gọi `assertA11y()`; chạy `recordRoborazziDebug`, mở từng ảnh so với `design/screens/18-components-setup.png` và board liên quan, chỉnh tới khi khớp số đo; lệch còn lại ghi vào `docs/design-guidelines.md` "Known deviations"; commit golden.
12. `AiFabTest`: `enabled = false` → node không có `OnClick` và có `Disabled`.
13. Commit cuối phase: `feat(ui): add design-system components, snackbar and undo registry`.

## Todo list

- [x] ReducedMotion, UiText, RelativeTime, DateBuckets + test
- [x] UndoRegistry + test
- [x] SnackbarController (action dạng dữ liệu, finalize khi host rời) + host + placement + test
- [x] Tile MaterialShapes (Sunny, favicon, shortcut, add, icon, art)
- [x] Floating toolbar, Tabs count, AI FAB (vẽ trạng thái tắt), tab switcher toolbar
- [x] Address bar (3 indicator + wavy progress gap 8) và ô tìm kiếm thu gọn (04, 09c)
- [x] Segmented list (ListItemShapes 20/4), section label, large title scaffold
- [x] Connected toggle group, nút M/S/XS, chip (citation có trạng thái tắt), nút icon nhỏ, Send/Stop, toggle Text size
- [x] Bottom sheet (không host snackbar), alert dialog, choice dialog, text field, menu nhóm
- [x] Switch, slider, loading indicator, wavy AI, badge
- [x] Card, hero AI, answer card, tab card, chat bubble/answer/input pill
- [x] Preview sáng/tối cho mọi component
- [x] Helper `assertA11y`, `captureBoard`; golden Roborazzi đã duyệt so với board 15/17/18; lệch ghi vào `docs/design-guidelines.md`
- [x] AiFabTest
- [x] Commit cuối phase

## Success criteria

- `verifyRoborazziDebug` xanh với golden đã commit; mỗi ảnh đã được người duyệt so số đo với board 18 và scout §1 (toolbar 64, FAB 56 cách 8, address bar 56, sheet bo 28 + handle 32×4, dialog 344, menu 220, snackbar `inverseSurface` bo 4); lệch đã ghi ở "Known deviations".
- Component của toolbar nổi (64, FAB cách 8, đáy cách nav bar 24) và của "Groups and lists" (connected group, segmented list) đúng bản vẽ. Hành vi đóng sheet và đặt snackbar ở từng màn do phase 5 làm end-to-end.
- `SnackbarControllerTest` và `UndoRegistryTest` chứng minh: một snackbar tại một thời điểm, 10 s/4 s, Undo chạy sau khi màn gốc đã rời (scope process), host rời composition = đóng (finalize).
- Mọi mâu thuẫn #1, #2, #3, #4, #5, #6, #7, #11, #13, #14, #16, #18 của scout §3(e) được áp dụng đúng bên thắng trong component tương ứng.
- Reduced motion cho ảnh tĩnh của loading indicator và sóng phẳng (ảnh Roborazzi riêng).

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.*'
./gradlew :app:recordRoborazziDebug --tests 'com.dantech.skimbrowser.ui.components.*'
./gradlew :app:verifyRoborazziDebug
./gradlew :app:lintDebug
```

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| `ListItemShapes` tự dựng nhận đủ 6 state shape | Bo góc nhảy khi nhấn/kéo | Truyền cùng shape cho mọi state trừ pressed; ghi lại trong KDoc |
| `DropdownMenuPopup` vị trí neo đúng control | Menu lệch neo trong ảnh | Dùng `DropdownMenuPopupPositionProvider` mặc định với offset 0; kiểm trên emulator ở cổng thiết bị phase 5 |
| Robolectric render `MaterialShapes` và wavy giống máy thật | Ảnh golden khác máy | Golden là parity đã duyệt bằng người; lệch do raster ghi ở "Known deviations" |
| `SnackbarHostState.showSnackbar` bị huỷ khi host rời composition | Token Undo không bao giờ finalize | `DisposableEffect` gọi `onDismissed`; test bắt buộc |
| API beta01 đổi tên ở bản sau | Lỗi compile khi nâng | Không nâng material3 trong plan này |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 2 (component mới hoàn toàn).

## Security considerations

- Snackbar chỉ chứa dữ liệu; `OpenDownload` chỉ mở URI do Skim tạo (MediaStore/DownloadManager) với MIME do MediaStore trả, không nhận intent từ web.
- `UndoRegistry` không giữ Activity/navigator, nên không rò Activity.

## Next steps

- Phase 5 tạo `UndoRegistry` + `SnackbarController` trong `AppContainer`, đặt host ở shell và cung cấp `onAction`; các phase màn hình dùng component này, không tạo bản sao.

## Kết quả thực thi (2026-10-21)

Commit: `feat(ui): add design-system components, snackbar and undo registry`. Làm ba lượt tuần tự (A: nền, toolbar, address bar, hình, nút; B1: danh sách, nút, switch, slider, tiến trình; B2: sheet, dialog, menu, thẻ, chat), mỗi lượt có kiểm chứng độc lập. Kiểm chứng độc lập lượt cuối: `testDebugUnitTest` 252 test / 30 lớp (0 fail, 0 skip), `verifyRoborazziDebug` xanh 3 lần liên tiếp (mỗi lần `cleanTestDebugUnitTest` trước), `lintDebug` 0 error và 0 issue trong `ui/components`, 96 golden; lượt B2 có 16 đột biến đều làm test đỏ; quét 153 file: không ID plan, hex màu, TODO hay log.

Khác spec (đã chấp nhận):
- `SkimBottomSheet(onDismiss, paneTitle, modifier, state, content)`: `skipPartiallyExpanded` chuyển vào `rememberSkimSheetState(skipPartiallyExpanded = true)`; `SkimSheetState.partialExpand()` là no-op khi sheet chỉ có chiều cao đầy (material3 ném lỗi). `SheetShape` (PascalCase) thay `sheetShape`. Handle của sheet modal là drag handle của material3 (giữ hành động TalkBack); `SheetHandle()` công khai là bản trang trí cho panel 08b.
- `SkimAlertDialog(title, confirm: DialogAction, onDismissRequest, modifier, dismiss, destructiveConfirm, icon, iconTint, text)` với `icon`, `text` là slot; `ChoiceDialog(label: @Composable (T) -> String)`; `SkimTextField` thêm `keyboardOptions`, `onKeyboardAction`; `SkimCard` thêm `onClick`, `contentPadding`, `verticalArrangement`; `AnswerCard(verticalArrangement)`; menu bo 16/12 đủ bốn góc như bản vẽ; `ChatInputPill` thêm `ImeAction.Send`.
- `TwoRowsTopAppBar` thay `LargeFlexibleTopAppBar` (cùng bố cục, khớp bản vẽ hơn); slider cỡ chữ lệch 2–3 dp do `Slider` của material3 (không tự dựng).

Việc để các phase sau:
- Phase 5: `BottomSheetSceneStrategy` tạo `rememberSkimSheetState(meta.skipPartiallyExpanded)`, truyền cho `SkimBottomSheet` và đưa state tới nội dung entry (ví dụ CompositionLocal) để 06 gọi `partialExpand()` khi bấm citation. Xem menu, dialog, sheet trên thiết bị vì golden không có bóng của popup; kiểm cách TalkBack đọc snackbar lỗi lồng live region.
- Phase 6: nhóm Continue của 04 (bo 20/4) không vẽ được bằng `SkimCard(radius)`; dùng `Surface` riêng hoặc mở rộng API. Nút S/XS, citation và nút icon nhỏ có khung chạm 48 dp nên màn phải đặt cao hơn 4 hoặc 8 dp so với mép bản vẽ; `Column` truyền chiều cao còn lại cho con sau nên khung 48 của nút cuối trong vùng chiều cao cố định bị ép còn 40.
- Review thiết kế: copy trang `http` (`Site information`), quy tắc `99+`, placeholder ô tìm trong large title; chữ 14 sp hẹp hơn bản vẽ 5–7% chưa rõ nguyên nhân (không phải `opsz` tự động: HTML chỉ xin `wght`; thử `opsz` 14 ra 284 dp so với PNG 278 dp), muốn khớp hơn cần quyết định riêng vì đổi mọi golden.
- Theo dõi flake: golden `switch-rows-10` lệch 1 lần khi lượt B1 chạy đột biến, không tái hiện trong hơn 10 lần verify sau đó.
