# Phase 2, phần B2: sheet, dialog, menu, card, tab card, chat

- Plan: `plans/261020-1611-skim-design-implementation/`, `phase-02-design-system-components.md`, bước 8 (phần còn lại), 9, 10, 11: sáu file component, test, golden, preview và phần bổ sung của `docs/design-guidelines.md`.
- Trạng thái: **DONE_WITH_CONCERNS**. Mọi lệnh kiểm chứng đều xanh, 16/16 đột biến làm test đỏ. Concern chính: chữ hẹp hơn bản vẽ 5–7% do trục `opsz` của font (ảnh hưởng mọi phase, cần quyết định), và vài chữ ký API khác spec (lý do ở dưới).
- Ngày 2026-10-21, giờ Asia/Singapore. Không `git add`, không commit, không đổi nhánh. `git diff --stat` rỗng. Không sửa file nào của phase 1, phần A, phần B1 (đã kiểm md5), trừ phần thêm vào `ContentScreenshotTest.kt` và `docs/design-guidelines.md`. Không đổi `strings.xml`, `app/build.gradle.kts`, `libs.versions.toml`.

## Kết quả kiểm chứng (output thật)

| Lệnh | Kết quả |
|---|---|
| `:app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.*'` | Xanh: 242 test, 0 fail, 0 skip (163 cũ + 79 mới) |
| `:app:recordRoborazziDebug --tests '…OverlaysScreenshotTest' --tests '…ContentScreenshotTest'` | Xanh: 31 golden mới (tổng 96). 20 golden B1 của `ContentScreenshotTest` được ghi lại nhưng trùng từng byte với bản B1 đã duyệt (md5 cả 65 golden cũ không đổi) |
| `:app:verifyRoborazziDebug` (×4, mỗi lần `cleanTestDebugUnitTest` trước) | Cả 4 lần xanh, 252 test, không golden nào lệch giữa các lần |
| `:app:lintDebug` | 0 error, không baseline, không suppress; 0 issue trong `ui/components`. Cảnh báo: `UnusedResources` 118 (B1: 160), `NewerVersionAvailable` 3, `AndroidGradlePluginVersion` 1, `InsecureBaseConfiguration` 1 |
| `:app:testDebugUnitTest` (toàn bộ) | Xanh: 252 test (173 + 79), 0 fail, 0 skip |
| Biên dịch lại không incremental (`--rerun-tasks -Pkotlin.incremental=false`) main + unit test | 0 cảnh báo Kotlin |

Đột biến (script sửa tạm, chạy test, khôi phục; md5 nguồn sau đó trùng trước): 16/16 đỏ.
- Sheet: `partialExpand` không chặn khi sheet chỉ có chiều cao đầy; bỏ `paneTitle`; hàng handle giữ chiều cao 48 của material3.
- Dialog: nút phá huỷ màu `primary`; bỏ `setDimAmount` (9 golden đỏ); hàng lựa chọn có role Button; ô nhập giữ khoảng 8 dp của material3 phía trên.
- Menu: không đóng sau khi chọn; đệm nhóm 2 dp.
- Tab card: badge hiện ở incognito; bỏ `selected`; chữ chạy dưới nút Close.
- Chat: Send gửi khi rỗng; ô nhận chữ khi tắt.
- Golden: đệm dưới AnswerCard 16 (2 golden đỏ); scrim 50% (12 golden đỏ).

## File

- Main (`app/src/main/java/com/dantech/skimbrowser/ui/components/`), mỗi composable public có `@PreviewLightDark`:
  - `SkimBottomSheet.kt`: `SkimSheetState`, `rememberSkimSheetState`, `SkimBottomSheet`, `SheetHandle`, `SheetShape`, `ScrimAlpha`.
  - `SkimDialogs.kt`: `DialogAction`, `SkimAlertDialog`, `ChoiceDialog`, `SkimTextField`.
  - `SkimMenu.kt`: `MenuEntry`, `SkimMenu`.
  - `SkimCards.kt`: `SkimCard`, `AiHeroCard`, `AnswerCard`.
  - `TabCard.kt`: `TabCard`.
  - `Chat.kt`: `UserBubble`, `AnswerText`, `ChatInputPill`.
- Test (`app/src/test/java/com/dantech/skimbrowser/ui/components/`):
  - Hành vi: `SkimBottomSheetTest` (9), `SkimDialogsTest` (14, gồm choice dialog và text field), `SkimMenuTest` (5), `TabCardTest` (10), `ChatInputPillTest` (10).
  - Ảnh: `OverlaysScreenshotTest` (14), 17 test thêm vào `ContentScreenshotTest` (20 → 37; chỉ thêm: nhóm test, fixture, import, một câu KDoc).
  - Helper: `AskPageFixtures.kt` (nội dung mẫu của 06 dùng chung cho golden sheet và golden dải), `PixelColors.kt` (`ImageBitmap.hasPixel`).
- Golden: 31 ảnh mới ở `app/src/test/screenshots/components/`.
- Docs: `docs/design-guidelines.md` thêm 12 dòng "Golden đã duyệt" và 13 dòng "Known deviations" (95 dòng).

## Cách chụp overlay

Đã thử trước trong Robolectric NATIVE: `captureScreenRoboImage` của Roborazzi 1.76.0 vẽ mọi cửa sổ theo đúng vị trí: activity, dialog (kể cả lớp dim theo `dimAmount` của cửa sổ), sheet (scrim do Compose vẽ trong cửa sổ sheet) và popup của menu. Vì vậy golden overlay chụp cả màn với wrapper thật, không tách composable nội dung `internal`. Golden 06 và 06d so trực tiếp với PNG; dialog và menu của 15 so với panel đã phóng 2 lần.

Thăm dò cũng cho thấy cửa sổ dialog của Compose có `FLAG_DIM_BEHIND` với dim 0,6 của theme nền tảng. Vì vậy `SkimAlertDialog`/`ChoiceDialog` đặt `setDimAmount(0.32f)` qua `DialogWindowProvider`. Scrim trên trang trắng là (174,174,174), bản vẽ (173,173,173).

## Parity (đo bằng ImageMagick, cắt cùng vùng; chi tiết ở bảng "Golden đã duyệt")

- `sheet-06`: mép trên, bo 28, handle 32×4, màu handle, nền, scrim trên ba nền và Send trùng từng pixel. Thẻ Summary thấp hơn 20 dp (ít hơn một dòng), phần dưới dịch đúng 20 dp.
- `dialog-06d`: 344×364 dp tại x 34, mọi phần cùng vị trí (lệch 0,5 dp), không có bóng.
- Dialog của 15: cấu trúc và nút trùng, mép phải nút cuối trùng. `add-shortcut` và `choice-dialog` cao đúng như vẽ, radio và viền ô trùng từng pixel. Ba dialog thân chữ thấp hơn 20 dp (ít hơn một dòng).
- `menu-tab-group-15`: kích thước, khe, bo, vị trí icon và chữ trùng; menu cách mép 8 dp thay vì 16.
- `cards-03`: mép thẻ trùng từng pixel.
- `ai-hero-card-04`: cao 98 so với 89 (bản vẽ bị flex co lại).
- `answer-card-06`, `chat-06`, `chat-input-*`: lệch tối đa 0,5 dp ngoài chỗ xuống dòng; ô tắt trùng màu nền và Send tắt.
- `tab-cards-09`, `-09b`: thẻ, vòng, preview, badge trùng từng pixel; Close tab lệch 2 dp.
- Ảnh không có board (xem bằng mắt): `-dark`, `sheet-06-half`, `chat-input-typed`, `tab-cards-no-preview`.

## Lệch so với spec và lý do

1. **`SkimBottomSheet(onDismiss, paneTitle, modifier, state = rememberSkimSheetState(), content)`.** `skipPartiallyExpanded` chuyển vào `rememberSkimSheetState(skipPartiallyExpanded = true)`. Lý do: 06 cần chính state đó để hạ sheet khi bấm citation; một nguồn sự thật, giống `ModalBottomSheet(sheetState = …)`. Phase 5 viết `SkimBottomSheet(…, state = rememberSkimSheetState(meta.skip))`. `SkimSheetState` chỉ lộ `expand()` và `partialExpand()`. Trên sheet không có nửa chiều cao, `partialExpand()` là no-op có ghi KDoc (material3 ném `IllegalStateException`).
2. **`SheetShape`** (PascalCase như hằng của repo) thay vì `sheetShape`.
3. **Handle của sheet modal là drag handle của material3, vẽ theo kích thước bản vẽ.** Lý do: giữ nhãn "Drag handle" bản địa hoá và các hành động expand/collapse/dismiss cho TalkBack. Chuỗi của material3 là resource private, nên không dùng lại được; thêm copy thì bị cấm. `SheetHandle()` public là bản trang trí (clearAndSetSemantics) cho panel 08b. Hai bản dùng chung hằng số.
4. **Menu không dùng `MenuDefaults.groupShape(i, n)`/`itemShape(i, n)`.** Hai hàm này cho bo kiểu segmented (nhóm 16/8, item 12/4). Board 15 vẽ nhóm bo 16 cả bốn góc, item bo 12 (đã phóng ảnh để kiểm). Bản vẽ thắng, nên dùng `MenuGroupShapes(RoundedCornerShape(16), …)` và overload `DropdownMenuItem(onClick, text, shape, …)`. Màu nhóm đặt `surfaceContainer` (mặc định là `surfaceContainerLow`), item ép cao 48 (mặc định 44), icon 24, khe 12, đệm 0×12.
5. **`ChoiceDialog(label: @Composable (T) -> String)`** thay vì `(T) -> String`, để caller trả `stringResource(…)` trực tiếp (Theme, Search engine).
6. **`SkimAlertDialog(title, confirm: DialogAction, onDismissRequest, modifier, dismiss: DialogAction?, destructiveConfirm, icon: @Composable?, iconTint, text: @Composable?)`.**
   - `DialogAction(label, onClick, enabled)`: Save và Clear có lúc tắt.
   - `icon` là slot, vì 06d cần SunnyMark 56.
   - `text` là slot, cho hai đoạn của 06d, ô nhập và checkbox.
   - Phần thân cuộn được khi màn hình thấp.
   - Dùng `Dialog` trực tiếp với `paneTitle = title`, thay `BasicAlertDialog` (vốn đặt pane title "Dialog").
   - Nút 40 dp có khung 48, nên khoảng cách trên/dưới hàng nút đặt 20 + 4 để vẫn ra 24 như vẽ.
7. **`SkimTextField(state, label, modifier, focusRequester, keyboardOptions, onKeyboardAction)`.** Hai tham số cuối là cần thiết cho ô URL (bàn phím Uri) và nút Save trên bàn phím. Ô được bố cục bằng đúng hộp 56 dp: khoảng 8 dp mà material3 chừa trên viền cho nhãn được trả lại, nhãn vươn lên khoảng trống phía trên như HTML.
8. **`SkimCard(modifier, radius = 20.dp, color, onClick?, contentPadding = 16, verticalArrangement, content)`, `AnswerCard(modifier, verticalArrangement = spacedBy(12), content)`.** Thẻ cần nút bấm (04) và đệm khác nhau (03: 20; 06: 16/16/8). Nhóm Continue của 04 (bo 20/4 kiểu segmented) không vẽ được chỉ bằng `radius`; phase 6 cần `Surface` riêng hoặc mở rộng API.
9. **`TabCard(… incognito …)`:**
   - Màu thẻ lấy theo dark scheme (09b và Theme Dark) hoặc `incognito`.
   - `incognito` còn ẩn badge Skim chat, vì AI luôn tắt ở incognito (giống `TabSwitcherToolbar` của phần A).
   - Tab hiện tại đọc là `selected` (không có chuỗi riêng).
   - Close tab dùng `CompactIconButton` tại góc trên phải: khung 48 nằm trọn trong thẻ, hàng tiêu đề giữ 44. Đổi lại, hình lệch 2 dp (ghi Known deviations).
10. **`ChatInputPill`:**
    - Send vẽ bật khi ô rỗng như 06, nhưng chỉ gọi `onSend` khi có chữ không trắng.
    - Thêm `ImeAction.Send` cùng quy tắc; khi đang generating, bàn phím không gửi. Test bắt được lỗi này ở lượt đầu, đã sửa.
    - Placeholder là nhãn a11y nhờ node text field gộp con.
11. **Fixture**, không phải component: hàng nút 48 dp trong fixture 06 và 03 dùng `drawnHeight` để khoảng cách của bản vẽ áp lên hình 40/32. Phát hiện kèm theo: `Column` truyền chiều cao còn lại cho con sau, nên khung 48 của nút cuối trong vùng có chiều cao cố định bị ép còn 40 (citation lệch 4 dp ở lần chụp đầu, đã sửa trong fixture). Màn hình phase 6–12 cần lưu ý điều này.

## API material3 1.5.0-beta01 đã xác nhận trong nguồn

- `ModalBottomSheet(onDismissRequest, modifier, sheetState, sheetMaxWidth, sheetGesturesEnabled, shape, containerColor, contentColor, tonalElevation, scrimColor, dragHandle, contentWindowInsets, properties, content)` gồm:
  - `ModalBottomSheetDialog` (`ComponentDialog`, theme EdgeToEdgeFloatingDialog, không dim);
  - `Scrim(contentDescription = "Close sheet")` bấm được;
  - `BottomSheet(…)` public.
  - `BottomSheetImpl` đặt `paneTitle = "Bottom Sheet"` trên Surface; `paneTitle` đặt trong `modifier` (phía ngoài) thắng (đã có test).
- `rememberBottomSheetState(initialValue, enabledValues = {Hidden, PartiallyExpanded, Expanded}, confirmValueChange)`; `rememberModalBottomSheetState` deprecated. `partialExpand()` gọi `check(!skipPartiallyExpanded)`.
- Cờ `isBottomSheetPartiallyExpandedDeterministicEnabled = true`: neo nửa chiều cao ở `fullHeight - min(fullHeight/2, sheetHeight)`.
- `BottomSheetDefaults.DragHandle(modifier, width, height, shape, color)`: đệm dọc 22 dp, contentDescription "Drag handle". `DragHandleWithTooltip` bọc handle trong `TooltipBox` clickable có dismiss/expand/collapse.
- `public.txt` của AAR material3 rỗng: mọi resource của nó là private.
- `DropdownMenuPopup(expanded, onDismissRequest, modifier, popupPositionProvider = rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below), properties = PopupProperties(focusable = true), content)`; lề tới mép cửa sổ 8 dp.
- `DropdownMenuGroup(shapes: MenuGroupShapes, modifier, containerColor = groupStandardContainerColor (surfaceContainerLow), tonalElevation, shadowElevation (Level 2), border, contentPadding = vertical 2, interactionSource, content)`. `MenuGroupShapes(shape, inactiveShape)` có constructor public; `MenuDefaults.GroupSpacing` = 2 dp.
- `DropdownMenuItem(onClick, text, shape, modifier, leadingIcon, trailingContent, supportingText, enabled, colors, horizontalArrangement, contentPadding, interactionSource)` không experimental. Surface của item lùi 4 dp mỗi bên; hàng tối thiểu 44 dp (`SegmentedMenuTokens.Item`); icon 20.
- `OutlinedTextField(state: TextFieldState, …, labelPosition = Cutout, label: @Composable TextFieldLabelScope.() -> Unit, …, lineLimits, …)` thêm `padding(top = bodySmall.lineHeight / 2)` và gộp semantics.
- Khác:
  - `RadioButton(onClick = null)`: 20 dp + đệm 2, không khung chạm, chấm vẽ 10 dp.
  - `TextButton` có overload `shapes`.
  - `BasicAlertDialog` đặt paneTitle "Dialog" và `sizeIn(280, 560)`.
  - `Dialog` của Compose căn giữa nội dung trong cửa sổ `MATCH_PARENT` khi `usePlatformDefaultWidth = false`.
- Foundation 1.12: `TextFieldDecoratorModifierNode.shouldMergeDescendantSemantics = true`.
- Roborazzi 1.76.0: `captureRoboImage`/`captureScreenRoboImage` chỉ ghi file khi chạy record/verify, không ghi khi chạy `testDebugUnitTest` thường.

## Concerns

1. **Chữ hẹp hơn bản vẽ 5–7% ở cỡ 14 (chiếm phần lớn lệch "chữ hẹp 3–5%" mà phần A/B1 quy cho raster).**
   - Google Sans Flex có trục `opsz` mặc định 18. Chrome đặt `opsz` theo cỡ chữ, còn `Type.kt` chỉ đặt `wght`.
   - Đo cùng câu: app 264 dp, cùng font với `opsz` 14 là 284 dp, PNG 278 dp.
   - Hệ quả: nhiều chỗ xuống dòng khác; thẻ và dialog thấp hơn một dòng.
   - Không sửa, vì đây là typography chung (phase 1) và đổi mọi golden. Đề xuất cho controller: đặt `FontVariation.Setting("opsz", cỡ chữ)` theo từng style (cần FontFamily theo cỡ), rồi ghi lại toàn bộ golden.
2. **Bóng:**
   - Menu có bóng Level 2 trong code, nhưng Robolectric không vẽ bóng trong cửa sổ popup, nên golden không chứng minh được; cần xem trên thiết bị ở cổng phase 5.
   - Dialog và sheet không có bóng theo default Compose. Bản vẽ có bóng; đã ghi Known deviations, theo tokens.
3. **Vùng chạm thực của drag handle khoảng 42 dp.** Phần 6 dp phía trên mép sheet bị sheet clip. `assertA11y` vẫn đạt vì `touchBoundsInRoot` nới qua cha có clip. Ảnh hưởng nhỏ vì cả sheet kéo được.
4. **Previews chỉ được biên dịch.** Sheet, dialog và menu trong preview cần interactive mode.
5. Golden `sheet-06` và `dialog-06d` có trang phía sau để trống (fixture). Board có nội dung trang dưới scrim, nên chỉ so vùng thanh địa chỉ, sheet và dialog.

## Câu hỏi chưa giải quyết

1. Có sửa typography (`opsz` theo cỡ chữ) để chữ khớp bản vẽ không? Việc này đổi mọi golden đã duyệt.
2. Chấp nhận các chữ ký API ở mục "Lệch so với spec" 1, 2, 5–8? Đặc biệt `SkimBottomSheet(state = …)` thay `skipPartiallyExpanded` mà phase 5 đang ghi.
3. Drag handle theo material3 (TalkBack đọc, chạm để mở rộng hoặc đóng) hay handle chỉ trang trí như HTML (`aria-hidden`)? Nếu chỉ trang trí thì người dùng TalkBack không mở rộng được sheet 06 từ nửa chiều cao.
4. Menu sát mép cách 8 dp (default material3) hay 16 dp như bản vẽ nhóm tab? Muốn 16 dp thì cần `popupPositionProvider` riêng.
