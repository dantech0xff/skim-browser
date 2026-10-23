---
phase: 9
title: "Tabs"
status: pending
priority: P1
effort: "21h"
dependencies: [2, 4, 5]
---

# Phase 9: Tabs

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Scout `plans/reports/scout-261020-2319-design-inventory.md` §2 (09, 09b, 15 Tab group menu, Rename group), §3(c) (`Tab`, `TabGroup`), §3(d) (snackbar Tab closed, N tabs closed), §3(e) #4, #16.
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-assumption-destroyer.md` Finding 1, 7, 9; `red-team-failure-mode-analyst.md` Finding 9, 10; `red-team-scope-complexity-critic.md` Finding 9.
- `design/spec/navigation-map.md` mục 09, 09b, rule "Tab switcher", "Groups and lists"; `design/spec/components.md` (FAB and tab switcher toolbar, Tab card, Connected button group, Badge); `design/spec/dialogs.md` (Rename group, Tab group menu).
- Board: `design/screens/09-tabs.png`, `design/screens/09b-incognito-tabs.png`, `design/screens/15-dialogs-menus-snackbars.png`.

## Overview

Xây màn chuyển tab 09 (tab thường, theo nhóm) và 09b (incognito, tối): đếm tab, tìm kiếm lọc tại chỗ, connected group Open/Incognito, nhóm tab với menu (Rename/Ungroup/Close N tabs), tab card có thumbnail, đóng bằng nút hoặc vuốt + Undo, kéo thả sắp xếp trong nhóm, đóng tất cả + Undo, FAB tab mới và toolbar tab actions. Banner gom nhóm AI của 09 được vẽ ở đây theo state, còn việc gom nhóm do phase 12 nối. Kết thúc bằng cổng thiết bị.

## Key insights

<!-- Red Team: Undo đóng tab chạy qua UndoRegistry — 2026-10-21 -->
- Undo đóng tab cần WebView được khôi phục đúng history: `TabManager.close` lưu state (đĩa với tab thường, bộ nhớ với incognito), `undoClose` dựng lại; `finalizeClose` chạy khi snackbar kết thúc mà không bấm Undo, kể cả khi host snackbar rời composition. Cả hai được đăng ký vào `UndoRegistry` (process) và chỉ bắt `TabManager`, không bắt ViewModel hay navigator.
<!-- Red Team: Phiên incognito không được nối lại trong lúc Undo — 2026-10-21 -->
- Đóng tab incognito cuối cùng đưa phiên vào `Ending` (phase 4). Nếu người dùng mở tab incognito mới trước khi snackbar hết giờ, đó là một phiên mới (profile mới) và token cũ bị finalize ngay: bấm Undo lúc đó không làm gì. "Close all incognito tabs" kết thúc phiên ngay, không Undo (quyết định nhỏ của planner: navigation-map 09b không ghi Undo cho hành động này và phiên riêng tư nên kết thúc tức thì).
<!-- Red Team: Một đường ghi layout — 2026-10-21 -->
- Mọi thay đổi nhóm/thứ tự đi qua một đường duy nhất `TabManager.applyLayout`; ViewModel không ghi `TabRepository` trực tiếp.
- Close tab 36 và Group options 40 nhỏ hơn 48: vùng chạm 48 bằng `minimumInteractiveComponentSize` (mâu thuẫn #16). Tab card tối dùng `surfaceContainer` (mâu thuẫn #4).
- Compose không có lưới kéo thả sẵn: viết helper nhỏ dựa trên `detectDragGesturesAfterLongPress` + `LazyGridState.layoutInfo`, giới hạn trong cùng nhóm.

## Requirements

Chức năng:
- 09: tiêu đề plural `tabs_title`; Search tabs → ô thay hàng tiêu đề, lọc theo title hoặc URL tại chỗ, ẩn section rỗng (giao diện tìm kiếm chưa vẽ, `[DEFAULT — chờ validate]` D8); connected group "Tab list" Open/Incognito → 09/09b (bật FLAG_SECURE trước khi sang 09b); banner AI khi có state (phase 12); section theo nhóm (tên `titleMediumEmphasized` + `CountBadge` + Group options) rồi section "Other" cho tab không nhóm (không có Group options; khi chưa có nhóm nào thì chỉ một lưới không header); tab card mở tab (05/04); Close tab hoặc vuốt card → đóng + snackbar "Tab closed" + Undo; nhấn giữ và kéo → sắp xếp trong nhóm; toolbar tab actions (rule "Tab switcher"): Ask Skim about these tabs (`tertiary`, đích phase 12), New incognito tab (→ 09c trong tab incognito mới), Close all tabs (→ đóng mọi tab thường + snackbar "N tabs closed" + Undo); FAB New tab (→ 04 trong tab mới).
- Tab group menu (neo ở Group options, nút có nền pressed `onSurfaceVariant` 12% khi mở): [Rename group `ic_drive_file_rename_outline`, Ungroup `ic_layers_clear`], [Close N tabs `ic_close`] → Rename mở dialog `RenameGroupKey(groupId)` (1 field focus, Save tắt khi rỗng); Ungroup đưa tab về Other; Close N tabs đóng cả nhóm + snackbar "N tabs closed" + Undo (khôi phục cả nhóm).
- 09b: tiêu đề `incognito_tabs_title`; Search incognito tabs; Open/Incognito; banner thông tin `incognito_tabs_banner`; lưới tab incognito; Close tab + Undo; nút AI tắt (`ic_auto_awesome` 38%, cd `cd_ask_skim_off_incognito`); Close all incognito tabs → đóng hết (kết thúc phiên) rồi về 09; FAB New incognito tab → 09c.
- `TabsKey(focusTabId)` → cuộn tới header của section đang chứa tab đó (nhóm hoặc Other; dùng bởi link nhóm ở 06b).
- Sau Close all tabs, lưới rỗng hiện `tabs_empty` (D8); Back từ 09 về Browser, `BrowserRoute` tự tạo tab New tab nếu không còn tab.

Phi chức năng:
- Thumbnail đọc từ `ThumbnailStore` (bitmap null → nền `surfaceContainerLowest`).
- Danh sách 50+ tab cuộn mượt (dùng `key = tab.id`, `animateItem()`).

## Architecture

```kotlin
data class TabCardState(val id: String, val title: String, val letter: String, val thumbnail: ImageBitmap?,
    val isCurrent: Boolean, val hasSkimChat: Boolean)                       // hasSkimChat = tab.chatId != null (phase 3 đã đối soát id)
data class TabSection(val group: TabGroup?, val tabs: List<TabCardState>)       // group == null → "Other"
data class TabsUiState(val incognito: Boolean, val count: Int, val sections: List<TabSection>, val query: String?,
    val grouping: GroupingBannerState?, val aiEnabled: Boolean)
class TabsViewModel(incognito: Boolean, tabs: TabManager, thumbnails: ThumbnailStore, snackbar: SnackbarController, undo: UndoRegistry) {
    fun close(tabId: String); fun closeAll(); fun closeGroup(groupId: Long); fun ungroup(groupId: Long)
    fun rename(groupId: Long, title: String); fun move(tabId: String, toIndexInGroup: Int); fun setQuery(q: String?)
}
@Composable fun TabsScreen(state: TabsUiState, actions: TabsActions, gridState: LazyGridState)
@Composable fun TabsRoute(key: TabsKey, container: AppContainer, navigator: SkimNavigator)
```

- Đóng có Undo: `val token = tabs.close(ids); val t = undo.register(TAB, undo = { tabs.undoClose(token) }, finalize = { tabs.finalizeClose(token) }); snackbar.show(text, SnackbarAction.Undo(t))`. Token đã finalize (ví dụ vì phiên incognito mới) thì `undoClose` không làm gì.
- Close all incognito: `tabs.finalizeClose(tabs.close(allIncognitoIds))` rồi `navigator.replaceTop(TabsKey(false))`.
- Rename/Ungroup/sắp xếp: dựng `GroupLayout` mới từ state hiện tại → `tabs.applyLayout(layout)` (phase 4 ghi Room trong một transaction).
- Lưới: một `LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = spacedBy(12.dp), verticalArrangement = spacedBy(12.dp))`, header nhóm dùng `GridItemSpan(maxLineSpan)`; card bọc `SwipeToDismissBox` (cả hai chiều) và `Modifier.dragReorder(...)`.
- `GroupingBannerState(visible: Boolean, undoEnabled: Boolean, onUndo: () -> Unit)`: phase này vẽ banner (bo 20 `tertiaryContainer`, `ic_auto_awesome_filled` 20, `tabs_grouped_banner`, TextButton Undo `onTertiaryContainer` 600) khi state khác null; phase 12 cung cấp state.
- Toolbar: `TabSwitcherToolbar` (phase 2), đáy cách nav bar 24; snackbar `AboveToolbar`.

Luồng dữ liệu: `TabManager.state` (tab + nhóm) + `ThumbnailStore` → `TabsViewModel` (lọc theo mode/query, chia section) → `TabsScreen`. Hành động → `TabManager` (state bộ nhớ + Room) → `Flow` cập nhật lưới. Undo → `UndoRegistry` (appScope) → `TabManager`.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/TabsRoute.kt`, `TabsScreen.kt`, `TabsViewModel.kt`, `TabGroupMenu.kt`, `RenameGroupDialog.kt`, `DragReorder.kt` (cùng thư mục `ui/tabs/`)
- Test: `app/src/test/java/com/dantech/skimbrowser/ui/tabs/TabsViewModelTest.kt`, `DragReorderTest.kt` (tính chỉ số đích thuần), `TabsScreenshotTest.kt` (09 có banner `captureBoard("09-tabs")`, 09 không nhóm, 09b `captureBoard("09b-incognito-tabs")`, menu nhóm, dialog Rename, chế độ tìm kiếm, rỗng; mỗi test `assertA11y()`)
- Instrumented: `app/src/androidTest/java/com/dantech/skimbrowser/ui/tabs/TabsGesturesTest.kt` (vuốt đóng + Undo, nhấn giữ kéo đổi chỗ trong nhóm, không kéo được sang nhóm khác; đóng tab incognito cuối rồi mở tab incognito mới trong lúc snackbar còn hiện → cookie phiên trước không còn, Undo không làm gì)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: entry `TabsKey` (màn hình), `RenameGroupKey` (dialog).

## Implementation steps

1. `TabsViewModel`: chia section (nhóm theo `position`, rồi Other), lọc query (title/URL, không phân biệt hoa thường), đếm, `isCurrent`, `hasSkimChat`; các hành động như Architecture. Test: close → Undo → cùng vị trí; Undo sau khi `TabsViewModel` bị huỷ vẫn chạy (registry); close group → Undo khôi phục nhóm; Ungroup; rename; Close all incognito gọi `finalizeClose` ngay và phiên kết thúc (qua `FakeTabEngine`/fake session).
2. `TabsScreen`: hàng 64 (padding 0 4 0 16, `headlineMediumEmphasized`, IconButton search), `ConnectedToggleGroup` (margin 0 16 16, cd `cd_tab_list`), banner, lưới, `TabSwitcherToolbar` + FAB (`primaryContainer` `ic_add`, cd `new_tab`/`new_incognito_tab`). 09b: banner `incognito_tabs_banner` (bo 20 `surfaceContainer` padding 16, `ic_visibility_off` 20).
3. `TabGroupMenu` (`SkimMenu`, cd neo `cd_tab_group_menu` với tên nhóm), `RenameGroupDialog` (entry `RenameGroupKey(groupId)`, `SkimTextField` focus, Save tắt khi rỗng).
4. `DragReorder.kt`: `Modifier.dragReorder(state: ReorderState, itemKey: String, groupId: Long?)`; khi thả, tính chỉ số đích trong cùng nhóm từ `layoutInfo.visibleItemsInfo` (hàm thuần có test) và gọi `onMove`.
5. `TabsRoute`: nối navigator (`showTab`, `openNewTab(false/true)`, `openAskTabs()`, `replaceTop(TabsKey(!incognito))`), cuộn tới section chứa `focusTabId` bằng `gridState.scrollToItem(indexOfHeader)`.
6. Đăng ký entry; ảnh Roborazzi với fixture giống board (7 tab: nhóm "Heat pump research" 4, "Osaka trip" 2, Other 1; tab hiện tại có vòng; 1 tab có badge chat); duyệt so với `09-tabs.png`, `09b-incognito-tabs.png`, ghi lệch vào `docs/design-guidelines.md`.
7. Cổng thiết bị (quy trình chung ở `plan.md`): `TabsGesturesTest` trên `skim_api36` (phần incognito phải chạy thật) và `skim_api28`.
8. Commit cuối phase: `feat(tabs): add tab switcher with groups, search and undo`.

## Todo list

- [ ] TabsViewModel (section, lọc, đóng/Undo qua registry, nhóm, sắp xếp qua applyLayout) + test
- [ ] TabsScreen 09 + 09b (tiêu đề plural, search, connected group, banner slot, lưới, toolbar, FAB)
- [ ] Đóng bằng nút và vuốt + snackbar Undo; Close all + Undo; Close all incognito (không Undo, kết thúc phiên, về 09)
- [ ] Tab group menu + Rename dialog + Ungroup + Close N tabs + Undo
- [ ] Kéo thả trong nhóm (DragReorder) + test
- [ ] Cuộn tới section chứa `focusTabId`
- [ ] Đăng ký entry TabsKey, RenameGroupKey
- [ ] Ảnh Roborazzi (+ `assertA11y`, lệch ghi docs) + instrumented gesture test
- [ ] Cổng thiết bị `skim_api36` + `skim_api28`
- [ ] Commit cuối phase

## Success criteria

- Mọi row của 09 (trừ Undo trong banner và Ask Skim about these tabs: phase 12) và toàn bộ row 09b chạy đúng (coverage-matrix.md); rule "Tab switcher" đạt.
- Overlay Tab group menu, Rename group, snackbar Tab closed và N tabs closed đúng dialogs.md (Undo khôi phục tab với history: Back trong tab khôi phục vẫn về trang trước; Undo vẫn chạy sau khi rời 09).
- Đóng tab incognito cuối cùng rồi mở tab incognito mới ngay (snackbar còn hiện) → tab mới không thấy cookie của phiên trước (`TabsGesturesTest` chạy thật trên `skim_api36`).

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.tabs.*'
./gradlew :app:recordRoborazziDebug --tests '*TabsScreenshotTest*' && ./gradlew :app:verifyRoborazziDebug
# Cổng thiết bị (plan.md): mỗi AVD
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.ui.tabs.TabsGesturesTest
```

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Vuốt đóng và nhấn giữ kéo không xung đột | Kéo dọc bị nhận thành vuốt ngang | Vuốt chỉ nhận chiều ngang với ngưỡng mặc định; kéo chỉ bắt đầu sau long-press |
| Thumbnail chụp kịp trước khi rời tab | Card hiện nền trơn | Chụp trong `onRelease` của `WebViewHost` và khi progress 100 (phase 4); chấp nhận nền trơn nếu trang chưa tải xong |
| Token Undo luôn được finalize | Tab đóng mềm không bao giờ hoàn tất trong phiên | Host rời composition = đóng (phase 2); `finalizePendingCloses` khi Activity `isFinishing`; dòng `closedAt` bị purge ở lần khởi động sau (phase 3) |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 9 (gỡ entry `TabsKey`/`RenameGroupKey`; package `ui/tabs` mới).

## Security considerations

- 09b là bề mặt incognito: FLAG_SECURE bật trước khi chuyển segment (navigator) và chỉ tắt sau khi transition rời 09b kết thúc (phase 5).
- Thumbnail incognito chỉ trong bộ nhớ; tìm kiếm tab incognito không ghi gì ra đĩa.
- Phiên incognito mới không bao giờ dùng lại profile của phiên đang `Ending`.

## Next steps

- Phase 12 cung cấp `GroupingBannerState`, gọi gom nhóm khi mở 09, làm Ask Skim about these tabs chạy end-to-end, và cập nhật `TabsViewModelTest` khi constructor có thêm `TabGrouping`.
