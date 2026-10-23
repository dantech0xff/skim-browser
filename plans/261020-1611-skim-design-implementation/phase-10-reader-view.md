---
phase: 10
title: "Reader view"
status: done-with-concerns
priority: P2
effort: "11h"
dependencies: [4, 7]
---

# Phase 10: Reader view

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Scout `plans/reports/scout-261020-2319-design-inventory.md` §2 (08, 08b).
- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §A7 (Readability.js, render native, scale 80–200), §A8 (danh sách block dùng chung).
- `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §4 (Slider `rememberSliderState(steps = 11)`, `FilledTonalIconToggleButton`, connected group).
- `design/spec/navigation-map.md` mục 08, 08b, rule "Sheets and menus" (08b không scrim); `design/spec/tokens.md` (Reader body 17/28).
- Red team: `plans/261020-1611-skim-design-implementation/reports/red-team-failure-mode-analyst.md` Finding 1, `red-team-assumption-destroyer.md` Finding 5 (khôi phục Reader sau process death).
- Board: `design/screens/08-reader-view.png`, `design/screens/08b-reader-text-settings.png`; HTML: `design/html/08-reader-view.html`, `design/html/08b-reader-text-settings.html` (đã đo: domain 12/16 và tác giả 14/20 không đổi theo cỡ chữ; tiêu đề 28/36 → 31/40 và đoạn văn 17/28 → 19/31 ở 110%).

## Overview

Xây 08 Reader view: bài viết do Readability trích ra (`TabManager.extractPage`), render native bằng `LazyColumn` (domain, tiêu đề, tác giả, các block), toolbar nổi và AI FAB như 05; và 08b Reader text settings: panel không scrim với slider cỡ chữ 80–200% bước 10 và chọn font Sans serif/Serif, áp dụng ngay và lưu cho mọi trang reader.

## Key insights

- 08b "không scrim, chạm trang để đóng" và "Exit reader view đóng cả sheet": `ModalBottomSheet` với scrim trong suốt vẫn nuốt cú chạm đầu tiên (nút Exit phải bấm hai lần). Vì vậy 08b là panel không modal nằm trong màn Reader (cùng hình thức sheet: bo trên 28, `surfaceContainerLow`, handle, shadow mạnh hơn), trạng thái mở lưu bằng `rememberSaveable` (vẫn khôi phục sau process death). Hệ quả: bỏ key `ReaderTextKey` khỏi phase 5 (đã cập nhật).
- WebView của tab vẫn sống trong pool khi Reader che lên, nên thoát Reader quay về 05 đúng vị trí cuộn cũ.
<!-- Red Team: Khôi phục Reader sau process death — 2026-10-21 -->
- Sau process death, `ReaderKey` được khôi phục nhưng tab chưa có WebView. `extractPage` của phase 4 gọi `awaitPageReady` (WebView offscreen, chờ `PageFinished`), nên Reader ở trạng thái `Loading` tới khi trang tải xong; chỉ chuyển `Unavailable` khi trang đã tải mà không phải bài viết hoặc hết timeout. Khi `Unavailable`, Reader tự gỡ chính `ReaderKey` của nó (`navigator.remove(key)`), không gọi `back()` (sheet 06 có thể đang nằm trên Reader).
- `LazyListState` được `rememberSaveable` **bên trong** nhánh `Ready`, nên vị trí cuộn khôi phục chỉ áp dụng khi danh sách đã có dữ liệu.
- Danh sách block của Reader trùng với document gửi AI (phase 11), nên citation khi hỏi từ Reader có thể cuộn và nháy đúng block trong `LazyColumn`.

## Requirements

Chức năng:
- Mở từ nút Reader view (chỉ hiện khi `runtime.readerable`): `navigator.openReader(tabId)`; trích bài (chờ trang sẵn sàng, tối đa 15 s); không trích được hoặc không phải bài viết → Reader tự gỡ `ReaderKey` của nó, về 05.
- Toolbar nổi trên 08 (rule "Floating toolbar" cho màn này).
- 08: hàng 64 (Exit `ic_close` cd `cd_exit_reader_view`; Text size `TextSizeToggle` cd `cd_text_size`), nội dung padding 8 24 0 gap 16: domain `bodySmall`, tiêu đề 28/36 500, tác giả `bodyMedium`, block văn bản 17/28 `onSurface`; toolbar nổi (Back, Forward, Tabs như 05; More → 05b) + AI FAB (đích phase 11, mở với `fromReader = true`).
- Back hệ thống hoặc Exit → 05 cùng vị trí cuộn. Back/Forward trên toolbar thoát Reader rồi điều hướng tab (quyết định nhỏ của planner: nội dung reader cũ sẽ không còn khớp trang).
- 08b: toggle Text size bật (`ic_format_size_filled`, tonal bo 12) mở panel; panel: "Text" `titleMediumEmphasized`; khối Size ("Size" `labelLarge onSurfaceVariant` + "110%" `bodyMedium`; A 14 | slider | A 24/32); khối Font (nhãn "Font" + connected group "Sans serif"/"Serif", nút Serif vẽ bằng `FontFamily.Serif` 15). Slider 80–200 bước 10, áp dụng ngay; lựa chọn lưu DataStore (phase 3) cho mọi trang reader. Chạm trang hoặc bấm Text size lần nữa đóng panel; Exit đóng cả panel và Reader. Không có floating toolbar khi panel mở (bản vẽ 08b).
- Cỡ chữ mặc định 100% (quyết định nhỏ đã chốt); thang áp dụng cho tiêu đề và block, không áp dụng cho domain và tác giả.
- Block không có trong bản vẽ (quyết định nhỏ của planner): `h2`–`h6` dùng `titleLarge` 22/28 weight 500 (nhân thang), `li` có dấu "•" thụt 16, `blockquote` thụt 16 màu `onSurfaceVariant`, `pre` dùng `FontFamily.Monospace` 14/20 (nhân thang), `figcaption` `bodySmall onSurfaceVariant`; block đầu trùng tiêu đề bị bỏ.

Phi chức năng:
- Text theo font scale hệ thống (đơn vị sp) rồi mới nhân thang reader.
- Reduced motion: panel mở/đóng không animation trượt.

## Architecture

```kotlin
data class ReaderArticle(val host: String, val title: String, val byline: String?, val blocks: List<PageBlock>)
class ReaderViewModel(tabId: String, tabs: TabManager, settings: SettingsRepository) {
    val state: StateFlow<ReaderUiState>     // Loading (chờ awaitPageReady) | Ready(article, scalePercent, font) | Unavailable
    fun setScale(percent: Int); fun setFont(font: ReaderFont)
}
@Composable fun ReaderScreen(state: ReaderUiState.Ready, panelOpen: Boolean, actions: ReaderActions, listState: LazyListState, highlightIndex: Int?)
@Composable fun ReaderTextPanel(percent: Int, font: ReaderFont, onPercent: (Int) -> Unit, onFont: (ReaderFont) -> Unit)
fun readerBodyStyle(base: TextStyle, percent: Int): TextStyle    // fontSize và lineHeight × percent / 100
class ReaderHighlights { val requests: SharedFlow<Pair<String, Int>>; suspend fun request(tabId: String, blockIndex: Int) }   // app-scoped; route bỏ qua index ngoài phạm vi
```

- `ReaderRoute(key: ReaderKey)`: `rememberSaveable` cho `panelOpen`; `rememberSaveable(saver = LazyListState.Saver)` cho `LazyListState` trong nhánh `Ready`; collect `ReaderHighlights.requests` của tab → `listState.animateScrollToItem(index + headerCount)` và nháy nền block bằng `animateColorAsState` (`primaryContainer` → trong suốt, 2,4 s; reduced motion → đổi màu tức thì rồi tắt).
- Panel: `Box` căn đáy, chiếm toàn chiều rộng, có `Modifier.pointerInput` trên vùng trang để đóng; không dùng `ModalBottomSheet`.
- Luồng dữ liệu: `TabManager.extractPage(tabId)` (JS Readability trong WebView) → `PageContent` → `ReaderArticle` → UI; `SettingsRepository.settings` (scale, font) → style; slider → `setReaderTextScale` → DataStore → style mới.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderTextPanel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderBlocks.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderHighlights.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/ui/reader/ReaderViewModelTest.kt` (Loading tới khi `awaitPageReady` xong; `Unavailable` chỉ sau khi tải xong; với `FakeTabEngine`), `app/src/test/java/com/dantech/skimbrowser/ui/reader/ReaderStyleTest.kt` (100% → 17/28; 110% → 18.7/30.8 sp; 80% và 200% biên; slider 13 giá trị), `app/src/test/java/com/dantech/skimbrowser/ui/reader/ReaderScreenshotTest.kt` (08 ở 100% `captureBoard("08-reader-view")`, 08b ở 110% Sans serif `captureBoard("08b-reader-text-settings")`, 08b Serif, tối; mỗi test `assertA11y()`)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: entry `ReaderKey`.
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `readerHighlights`.

## Implementation steps

1. `ReaderViewModel`: `extractPage` (đã chờ `awaitPageReady`) một lần khi tạo; `readerable == false` hoặc `null` → `Unavailable` (route gọi `navigator.remove(key)`); map `PageContent` → `ReaderArticle` (host từ `tab.url`, byline từ Readability, bỏ block đầu nếu text trùng title).
2. `ReaderBlocks.kt`: style theo tag như Requirements; font `GoogleSansFlex` hoặc `FontFamily.Serif`; `readerBodyStyle` + test.
3. `ReaderScreen`: hàng 64 (padding 0×4, space-between), `LazyColumn` padding 8 24 0, khoảng đáy đủ cho toolbar; toolbar `BrowserToolbar` + `TabAiFab` (phase 5) khi panel đóng.
4. `ReaderTextPanel` với `ReaderSizeSlider` và `ConnectedToggleGroup` (phase 2), stateDescription của slider = `reader_size_percent`, `paneTitle` = `cd_text_settings`.
5. `ReaderRoute`: Back/Exit → `navigator.remove(key)`; toolbar Back/Forward → `navigator.remove(key)` rồi `tabs.goBack/goForward`; Tabs → `navigator.openTabs(tab.incognito)`; More → `navigator.openMoreMenu(tab.id)`; FAB → `navigator.openAskPage(tab.id, fromReader = true)`.
6. `ReaderHighlights` trong `AppContainer`; đăng ký entry.
7. Ảnh Roborazzi với bài viết mẫu (fixture test có đoạn văn giống board) ở 100% và 110%; duyệt so với PNG, ghi lệch vào `docs/design-guidelines.md`.
8. Commit cuối phase: `feat(reader): add reader view and text settings panel`.

## Todo list

- [x] ReaderViewModel (trích bài, không phải bài viết → quay về)
- [x] ReaderBlocks + style thang 80–200 + test
- [x] ReaderScreen 08 (hàng Exit/Text size, nội dung, toolbar, FAB)
- [x] ReaderTextPanel 08b không scrim (slider, font, lưu, chạm trang đóng)
- [x] Back/Exit giữ vị trí cuộn 05; toolbar Back/Forward/Tabs/More
- [x] ReaderHighlights (cho citation từ Reader)
- [x] Đăng ký entry ReaderKey
- [x] Ảnh Roborazzi 08, 08b (+ `assertA11y`, lệch ghi docs)
- [x] Loading tới khi trang sẵn sàng; tự gỡ ReaderKey khi Unavailable
- [ ] Thủ công: thoát reader, 05 vẫn đúng chỗ cuộn — chưa chạy: máy này không có system image
- [x] Commit cuối phase

## Success criteria

- Mọi row của 08 (trừ Ask Skim: phase 11) và 08b, cùng row 05 "Reader view" (ẩn khi không có bài viết) và rule "Floating toolbar" trên 08 chạy đúng.
- Reader khôi phục sau khi tạo lại container/Activity với pool rỗng hiển thị bài và giữ vị trí cuộn (kiểm ở `ProcessDeathRestoreTest` phase 13).
- Kéo slider từ 80 tới 200 đi đúng 13 nấc; đóng app rồi mở lại reader vẫn giữ cỡ và font đã chọn.
- Thoát reader thấy 05 ở đúng vị trí cuộn trước đó (thủ công trên thiết bị).

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.reader.*'
./gradlew :app:recordRoborazziDebug --tests '*ReaderScreenshotTest*' && ./gradlew :app:verifyRoborazziDebug
```

Thủ công: mở một bài báo dài → Reader → Text size 150% → chạm trang (panel đóng) → Exit → 05 đúng chỗ cuộn; trang không phải bài viết (trang chủ tìm kiếm) không có nút Reader.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| `isProbablyReaderable` khớp `parse()` | Nút Reader hiện nhưng trích rỗng | `Unavailable` → quay về 05; ẩn nút cho trang đó |
| Readability chạy đủ nhanh trên trang lớn | Màn Reader trống > 1 s | Giới hạn 400 block/100k ký tự (phase 4); hiện `Loading` là nền trống như bản vẽ, không có spinner (không có thiết kế) |
| Panel tự vẽ khớp hình sheet của bản vẽ | Ảnh lệch bo góc/shadow | Dùng chung `SheetHandle`/hình nền với `SkimBottomSheet` (phase 2) |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 10 (gỡ entry `ReaderKey`; nút Reader view ở 05 vẫn gọi navigator).

## Security considerations

- Render dạng text thuần (không HTML), nên không cần sanitize; script Readability đầy đủ chỉ chạy khi người dùng mở Reader hoặc hỏi AI (không có trích nền; xem D3 ở phase 12), kiểm readerable chỉ chạy script nhỏ.

## Next steps

- Phase 11: AI FAB trên 08 mở 06 đọc chính văn bản bài viết; citation gọi `ReaderHighlights.request(tabId, blockIndex)`.
