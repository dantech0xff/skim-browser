# Phase 2, phần B1: danh sách, điều khiển nhỏ, progress

- Plan: `plans/261020-1611-skim-design-implementation/`, `phase-02-design-system-components.md`, bước 7 (danh sách, large title, connected group, nút, badge, switch, slider, progress), test, golden, preview và phần bổ sung của `docs/design-guidelines.md`.
- Trạng thái: **DONE_WITH_CONCERNS**. Mọi lệnh kiểm chứng đều xanh. Concern chính: một lần duy nhất golden `switch-rows-10` lệch 2 dp khi đang chạy thử đột biến, không tái hiện được trong 8 lần verify sau đó; giao diện tìm kiếm của large title chưa có bản vẽ.
- Ngày 2026-10-21, giờ Asia/Singapore. Không `git add`, không commit, không đổi nhánh. `git diff --stat` rỗng. Không sửa file nào của phần A (đã kiểm md5 381 file trước và sau; chỉ `docs/design-guidelines.md` đổi, và chỉ thêm dòng). Không đổi `strings.xml`, `app/build.gradle.kts`, `libs.versions.toml`.

## Kết quả kiểm chứng (output thật)

| Lệnh | Kết quả |
|---|---|
| `:app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.*'` | Xanh: 163 test, 0 fail, 0 skip |
| `:app:recordRoborazziDebug --tests '…ListsScreenshotTest' --tests '…ContentScreenshotTest'` | Xanh: 36 test, 36 golden mới. Lần record cuối trùng từng byte với bản đã mở và đo |
| `:app:verifyRoborazziDebug` | Xanh: 173 test, 0 fail. 29 golden phần A không đổi (md5) |
| `:app:lintDebug` | 0 error, không baseline, không suppress. Cảnh báo: `UnusedResources` 160 (phần A là 278), `NewerVersionAvailable` 3, `AndroidGradlePluginVersion` 1, `InsecureBaseConfiguration` 1. 0 cảnh báo trong file của lượt này |
| `:app:testDebugUnitTest` (toàn bộ) | Xanh: 173 test (101 cũ + 72 mới), 0 fail, 0 skip |
| Biên dịch lại main và unit test (`--rerun`) | 0 cảnh báo Kotlin |

Thử đột biến bằng script (sửa tạm, chạy test, khôi phục). Cả 14 đột biến đều làm test đỏ:
- `pressedShape` giữ hình thường; bỏ vùng chạm 48 của nút trailing; bỏ bù khe 12 dp; để trailing làm item cao lên (ba đột biến cuối cùng làm đỏ `aOneLineItemWithAnIconButtonStays56High`);
- bấm lại lựa chọn đang chọn gọi `onSelect`; bỏ role RadioButton;
- slider không theo state của caller; bỏ `stateDescription`;
- citation tắt vẫn bấm được; `toggleableRow` thành Checkbox;
- bỏ `BackHandler` và bỏ focus của ô tìm kiếm;
- padding item 1 dòng 8 → 9 dp (5 golden đỏ); bỏ nhánh reduced motion của sóng (2 golden đỏ).

## File đã tạo

- Main (`app/src/main/java/com/dantech/skimbrowser/ui/components/`): `SegmentedList.kt`, `LargeTitleScaffold.kt`, `ConnectedToggleGroup.kt`, `SkimButtons.kt`, `CountBadge.kt`, `SkimSwitch.kt`, `ReaderSizeSlider.kt`, `Progress.kt`. Mỗi file có `@PreviewLightDark` cho mọi composable public.
- Test (`app/src/test/java/com/dantech/skimbrowser/ui/components/`):
  - Hành vi: `SegmentedListTest` (11), `ConnectedToggleGroupTest` (6), `ReaderSizeSliderTest` (7), `CitationChipTest` (2), `SkimSwitchTest` (5), `LargeTitleScaffoldTest` (5).
  - Ảnh: `ListsScreenshotTest` (16), `ContentScreenshotTest` (20). Helper dùng chung của `ContentScreenshotTest` (`setStrip`, `capture`, `FRAME`) đặt private ở đầu file để phần B2 thêm nhóm của mình.
- Golden: 36 ảnh mới ở `app/src/test/screenshots/components/` (tổng 65).
- Docs: `docs/design-guidelines.md` thêm 13 dòng "Golden đã duyệt" và 10 dòng "Known deviations" (70 dòng).

## Parity: golden đã so với PNG (ImageMagick, cắt cùng vùng)

- `large-title-12`, `-04c`, `-13`, `-14` so với toàn màn 12, 04c, 13, 14:
  - Cạnh mọi item trùng từng pixel theo cột x 390 dp: 1 dòng 56, 2 dòng 72, khe 2, bo 20/4.
  - Tile 40 ở x 32, chữ ở x 88; nhãn mục, Back, hai action cách 4, tiêu đề 32/40 lệch tối đa 1 px.
  - Nút trailing 48 cách mép 4; chữ chạy sát nút (dấu ba chấm cắt muộn hơn một ký tự vì chữ hẹp hơn).
- `segmented-list-04b` so với 04b từ y 96: item 1 dòng có nút 48 vẫn 56; icon hai đầu trùng từng pixel.
- `static-list-09c` so với 09c từ y 328: item 48, hai nhóm cách 48, trùng từng pixel.
- `connected-toggle-09`, `-09b`, `-08b` so với 09, 09b, 08b: nút 40 cùng y, khe 2, bo đúng; chữ lệch 1–3 px.
- `buttons`: M 56, S 40, XS 32 đúng; đệm chữ S 16/16 như "Set as default" của 03.
- `chips-06` so với 06 từ y 531: icon copy/feedback trùng từng pixel; chip và citation cùng y.
- `icon-buttons`: Send 40 và mũi tên trùng vị trí với 06; Send tắt (226,224,237), tối (34,35,72) đúng 10% và 12%.
- `text-size-toggle-08`, `-08b` trùng từng pixel với dải trên của 08, 08b.
- `count-badge-09`: badge 24×24 cùng y, số căn giữa.
- `switch-rows-10` so với toàn màn 10: item và switch 52×32 trùng từng pixel.
- `reader-size-slider-08b`: track 16, tay cầm 4×44, khe 6, chấm dừng cùng y.
- `ai-progress-*` so với thẻ Generating của 06c: vòng 48 cùng vị trí; Cookie9Sided tĩnh rộng 28 dp (vẽ 29); sóng cao 10, bước 40, cùng y.
- Ảnh chỉ xem bằng mắt (không có board): `-dark`, thu gọn, tìm kiếm, nhấn, `switches`, `reader-size-slider-range`.

Lệch tìm thấy (đã ghi vào "Known deviations" hoặc đã có dòng của phần A):
- Slider 08b: track thụt 2 dp mỗi đầu, tay cầm ở 110% lệch trái 3 dp (bố cục của `Slider` material3).
- "Serif" bằng `FontFamily.Serif` 14 sp: cùng bề rộng, thấp hơn khoảng 1 dp so với Georgia 15 px.
- Sóng AI indeterminate không có chấm dừng; khung loading indicator khác khung vẽ khi chuyển động.
- Khung bố cục 48 dp quanh nút 40 và 32 dp.
- Chữ hẹp hơn 3–5% (dòng có sẵn của phần A), nên chỗ xuống dòng và dấu ba chấm khác.
- Hình Cookie4Sided và Cookie9Sided (dòng có sẵn của phần A).

Trạng thái chưa vẽ đã chọn: large title thu gọn, ô tìm kiếm thay tiêu đề, item đang nhấn, citation tắt, Send tắt ở theme tối.

## Lệch so với spec và lý do

1. **Large title dùng `TwoRowsTopAppBar` thay `LargeFlexibleTopAppBar`.** Hai API có cùng bố cục bên trong. `LargeFlexibleTopAppBar` dùng một lambda tiêu đề cho cả hai hàng, với `displaySmall`/`titleLarge` và padding 28 dp tính từ baseline. Bản vẽ cần `headlineLargeEmphasized` cùng padding 12 dp dưới hộp chữ, chỉ ở hàng mở rộng. `TwoRowsTopAppBar` (public, không opt-in) truyền `expanded` cho tiêu đề. Vì vậy hàng mở rộng lấy đúng style của bản vẽ, hàng thu gọn giữ `titleLarge`, và không phải đoán hàng từ `LocalTextStyle`. Vẫn dùng `exitUntilCollapsedScrollBehavior`.
2. **Chế độ tìm kiếm dùng `TopAppBar` một hàng.** Ở hàng trên của thanh hai hàng, tiêu đề có alpha 0 khi thanh mở rộng, nên ô nhập đặt ở đó sẽ không thấy.
   - Ô nhập là `BasicTextField(TextFieldState)`; Close search (`ic_arrow_back`, `cd_close_search`) và Clear text (`ic_close`, `cd_clear_text`) theo 04b.
   - Bàn phím mở ngay; Back hệ thống đóng ô tìm (`BackHandler`).
   - Thêm tham số `searchPlaceholder` (bắt buộc khi có `searchState`): placeholder và nhãn a11y lấy nhãn của nút đã mở ô, ví dụ `cd_search_history`.
3. **Không có tham số `snackbarPlacement`** trong `LargeTitleScaffold`, theo chỉ dẫn: host snackbar duy nhất do shell đặt.
4. **Thứ tự tham số:** tham số bắt buộc đứng trước `modifier` (lint `ModifierParameter`). Ví dụ `SkimListItem(headline, index, count, onClick, modifier, …)`. Tên tham số giữ như spec.
5. **API thêm, đều cần cho bản vẽ:**
   - `SkimListItem`:
     - `singleLine`: tiêu đề trang 1 dòng có dấu ba chấm (04b, 04c, 13, 14), nhãn cài đặt thì xuống dòng (10);
     - `trailingIsIconButton`: nút 48 cách mép 4 và đặt sát chữ;
     - `supportingColor`, `supportingWeight`: hàng Ask Skim của 04b là `tertiary` 500;
     - nhấn giữ cần có `onClick` (`require`).
   - `ConnectedToggleGroup(fontFamilies)`: "Serif" của 08b.
   - `SkimSwitch(onCheckedChange` nullable để hàng `toggleableRow` sở hữu, `enabled)`; `toggleableRow(…, enabled)`.
   - `CompactIconButton(enabled)`.
   - `AiLoadingIndicator(contentDescription)` bắt buộc.
   - `internal fun disabledContainerColor()` (10% sáng, 12% tối) và `internal fun readerSizePercent()` (có test).
6. **`ReaderSizeSlider` gồm cả hai chữ "A"** (ẩn với a11y) quanh slider. Slider có contentDescription "Size" (`reader_size`) và `stateDescription` "110%".
7. **Chuỗi morph của `AiLoadingIndicator`** là chuỗi mặc định của material3 nhưng bắt đầu từ Cookie9Sided, để khung đầu trùng bản vẽ.
8. **`AiWavyProgress`** ẩn với accessibility (sóng 06c là `aria-hidden`; indicator là progress bar). Với Remove animations, nó vẽ khung tĩnh phẳng ở 53%, vị trí sóng trên bản vẽ. Indeterminate gốc với scale 0 chỉ còn track, không thấy "sóng phẳng".
9. **`CitationChip` tắt được vẽ tay, không có click.** Foundation 1.12 vẫn giữ `OnClick` (kèm `disabled`) cho clickable bị tắt. Chip tắt giữ khung 48 dp để bố cục không nhảy.
10. **Item trailing:** nút 48 không làm item cao thêm (tràn vào padding). Khe 12 dp của list item được bù để chữ chạy sát nút; leading và trailing khác giữ khe 16 dp như bản vẽ.

## API material3 1.5.0-beta01 đã xác nhận (nguồn trong scratchpad)

- `ListItemShapes(shape, selectedShape, pressedShape, focusedShape, hoveredShape, draggedShape)` có constructor public; hình morph khi mọi state là `RoundedCornerShape`.
- `SegmentedListItem`:
  - Padding mặc định 16/16/10/10. Chiều cao tối thiểu 56/72/88 theo số dòng (cờ `isExpressiveListItemHeightBasedOnTextLinesFixEnabled = true`), trừ đi padding.
  - Leading và trailing được bọc với khe 12 dp và hạ `LocalMinimumInteractiveComponentSize` đi phần padding: nút trailing chỉ còn 40 dp nếu không trả lại 48.
  - Căn dọc mặc định chuyển sang Top khi nội dung từ 60 dp, nên truyền `CenterVertically` cho hàng 4 dòng của 10.
- `TwoRowsTopAppBar(title: @Composable (expanded: Boolean) -> Unit, …, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)` không có padding đáy cho tiêu đề. `LargeFlexibleTopAppBar` mở rộng mặc định cao 120.
- `rememberSliderState(value, steps, trackRange)`: tham số là **`trackRange`**, không phải `valueRange`.
  - Overload không deprecated là `Slider(state, onValueChange, modifier, enabled, onValueChangeFinished, colors, interactionSource, thumb, track)`; `onValueChange` bắt buộc và thay cho việc tự ghi `state.value`.
  - Có `steps` thì vẽ tick, trừ khi truyền `drawTick = { _, _ -> }`.
  - Chấm dừng mang màu của track active.
- `ToggleButton` tự đặt role Checkbox (semantics bên ngoài thắng). Connected shapes: góc trong 8, khi nhấn 4, khi chọn tròn; `ConnectedSpaceBetween` = 2.
- Nút: `Button`, `FilledTonalButton`, `OutlinedButton` và `TextButton` đều có overload `shapes`.
  - Hàng nội dung có `defaultMinSize(58×40)`, nên XS dùng `heightIn(min = 32)`.
  - Đệm: XS 12/6 (icon 20, khe 4), S 16/10, M 24/16 (icon 24, khe 8).
- `Switch(onCheckedChange = null)` không có semantics và không có vùng chạm tối thiểu. Màu icon khi bật mặc định là `onPrimaryContainer`; bản vẽ là `primary`.
- `ContainedLoadingIndicator(modifier, containerColor, indicatorColor, containerShape, polygons)` cần ExpressiveApi; container 48, hình 38; góc xoay ban đầu 90°.
- `LinearWavyProgressIndicator` indeterminate: bước sóng mặc định 20 (truyền 40), không có chấm dừng. Khi duration scale bằng 0, `InfiniteTransition` nhảy về cuối và chỉ còn track.
- Compose UI test: khi `mainClock.autoAdvance = true`, animation vô hạn bị huỷ, nên golden chuyển động đặt `autoAdvance = false` rồi tiến tới 900 ms.
- Robolectric:
  - Đo chữ cần `@GraphicsMode(NATIVE)`; chế độ legacy cho kích thước chữ sai, nên mọi test đo bố cục ở lượt này đều dùng NATIVE và `PHONE_QUALIFIERS`.
  - `touchBoundsInRoot` bị cắt theo bound của cha, nên nội dung tràn khỏi khung sẽ trượt `assertA11y`. Lỗi này đã gặp ở fixture và đã sửa bằng khung cao hơn.

## Concerns

1. **Golden `switch-rows-10` lệch 1 lần.** Khi thử đột biến "bỏ nhánh reduced motion của sóng", hàng Delete cao 58 thay vì 56 dp. Ngoài hàng đó, mọi pixel trùng; bề rộng chữ và tile không đổi. Đột biến đó để lại một animation vô hạn chờ scale > 0. Chạy lại đúng đột biến đó thì không lặp lại; 8 lần verify bình thường liên tiếp đều xanh. Nếu CI gặp lại, cần điều tra trạng thái dùng chung giữa các test Robolectric.
2. **Khung bố cục 48 dp.** Nút S/XS, connected group, citation và nút icon nhỏ có khung 48 dp quanh hình 40/32 dp, nên màn hình (phase 6–12) đặt chúng cao hơn 4 hoặc 8 dp so với mép trên hình vẽ. Nút copy/feedback 40 cách nhau 4 cần `Arrangement.spacedBy((-4).dp)` (fixture `chips-06` đã làm vậy).
3. **Previews chỉ được biên dịch**, chưa mở trong Android Studio.
4. **Fixture màn hình chỉ là fixture.** Fixture 10, 12, 13, 14, 04c trong test dùng dữ liệu mẫu và không có footer; thẻ Summary và Generating chỉ là `Box` nền. Phần B2 có thể thay bằng `AnswerCard` nếu muốn.

## Câu hỏi chưa giải quyết

1. Giao diện tìm kiếm trong large title (placeholder lấy nhãn của nút, Close search và Clear text như 04b, không có màn không kết quả) cần design review và chấp nhận.
2. Chấp nhận `TwoRowsTopAppBar` thay `LargeFlexibleTopAppBar` (cùng bố cục, khớp bản vẽ hơn)?
3. Khung tĩnh 53% của sóng AI khi Remove animations có được chấp nhận, hay muốn chỉ còn track như mặc định của material3?
4. Lệch 2–3 dp của slider 08b (bố cục material3) có cần tự dựng slider không? Đề xuất là không.
