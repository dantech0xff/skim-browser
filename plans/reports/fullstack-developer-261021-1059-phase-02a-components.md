# Phase 2, phần A: design-system components (toolbar, bars, tiles, snackbar, undo)

- Plan: `plans/261020-1611-skim-design-implementation/`, `phase-02-design-system-components.md`, các bước 1–6, cùng `SkimSnackbarHost`, hai helper test, test, golden, preview và `docs/design-guidelines.md`.
- Trạng thái: **DONE_WITH_CONCERNS**. Mọi lệnh kiểm chứng đều xanh. Concern nằm ở phần a11y lồng live region và hình MaterialShapes khác hình xấp xỉ của bản vẽ (chi tiết ở dưới).
- Ngày 2026-10-21, giờ Asia/Singapore. Không `git add`, không commit, không đổi nhánh. Không sửa file nào đã được track (`git diff --stat` rỗng), kể cả `app/build.gradle.kts`.

## Kết quả kiểm chứng (output thật)

| Lệnh | Kết quả |
|---|---|
| `:app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.*'` | Xanh: 91 test, 0 fail, 0 skip (trong đó 7 test theme của phase 1) |
| `:app:recordRoborazziDebug --tests 'com.dantech.skimbrowser.ui.components.*'` | Xanh: 39 test, ghi 29 golden ở `app/src/test/screenshots/components/` |
| `:app:verifyRoborazziDebug` | Xanh: 101 test, 0 fail. Thử âm tính: đổi padding toolbar 8 → 9 dp thì 5 test toolbar đỏ; đã khôi phục |
| `:app:lintDebug` | 0 error, không baseline, không suppress. Cảnh báo: `UnusedResources` 278 (phase 1 là 347), `NewerVersionAvailable` 3, `AndroidGradlePluginVersion` 1, `InsecureBaseConfiguration` 1; 0 cảnh báo trong file của lượt này |
| `:app:testDebugUnitTest` (toàn bộ) | Xanh: 101 test (11 test phase 1 + 90 test mới), 0 fail, 0 skip |
| Biên dịch lại không incremental main + unit test | 0 cảnh báo Kotlin |

Thử đột biến (sửa tạm rồi khôi phục bằng bản sao lưu) để chắc test không chạy cho có. Mỗi đột biến đều làm test tương ứng đỏ:
- bỏ `onDispose → onDismissed` của host làm đỏ `hostLeavingTheCompositionWhileShowingFinalizesTheUndo`;
- ép mọi snackbar thành `Short` làm đỏ cả test 10 s lẫn test ánh xạ;
- bỏ `Assertive` làm đỏ `anErrorIsAnAssertiveLiveRegion`;
- `show()` không đóng message cũ làm đỏ 3 test;
- `undo()` không `remove` token làm đỏ test exactly-once, gồm cả test đa luồng;
- đổi alpha FAB tắt khi tối 12% → 10% làm đỏ test pixel;
- bỏ observer của ReducedMotion làm đỏ test đổi cài đặt khi đang hiện.

## File đã tạo (không sửa file nào khác)

- Main:
  - `ui/theme/ReducedMotion.kt`
  - `ui/text/{UiText,RelativeTime,DateBuckets}.kt`
  - `ui/snackbar/{UndoRegistry,SnackbarController}.kt`
  - `ui/components/{FloatingToolbars,AddressBar,SearchBarField,ShapeTiles,SkimSnackbarHost}.kt`
- Test:
  - `testing/{A11yAssertions,BoardCapture}.kt` và `testing/{A11yAssertionsTest,BoardCaptureTest}.kt`
  - `ui/components/{AiFabTest,ToolbarScreenshotTest,BarsScreenshotTest,ShapeTilesScreenshotTest,SnackbarScreenshotTest,ShapeTilesTest}.kt`
  - `ui/snackbar/{SnackbarControllerTest,UndoRegistryTest}.kt`
  - `ui/text/{RelativeTimeTest,DateBucketsTest}.kt`
  - `ui/theme/ReducedMotionTest.kt`
- Golden: 29 ảnh ở `app/src/test/screenshots/components/`.
- Docs: `docs/design-guidelines.md` (47 dòng, gồm mục kiểm parity, bảng golden đã duyệt và bảng "Known deviations" 4 cột).

Có 4 file test ngoài danh sách tối thiểu của task, nhưng đều là "test tương ứng":
- `SnackbarScreenshotTest` và `ShapeTilesScreenshotTest` cho golden của host và tile thuộc phần A. Hai file này đặt tên riêng để không đụng `OverlaysScreenshotTest`/`ContentScreenshotTest` của phần B.
- `ShapeTilesTest` cho logic `siteLetter`/`shortcutShapeFor`.
- `ReducedMotionTest`.
- `A11yAssertionsTest`/`BoardCaptureTest` chứng minh helper bắt được lỗi.

## Parity: golden đã so với PNG (đo bằng ImageMagick, cắt cùng vùng)

- `browser-toolbar-05/-04/-11/-09c/-05-dark/-tab-count-overflow` so với dải đáy 120 dp của 05--loading, 04, 11, 09c:
  - Pill 220×64 dp ở x 64, đáy cách mép 24; FAB 56 ở x 292, cách 8.
  - Màu trùng từng pixel: FAB tắt sáng (226,224,237) = onSurface 10%, tối (34,35,72) = 12%; icon `onSurfaceVariant`; icon tắt 38%.
- `tab-switcher-toolbar-09/-09b` so với 09, 09b: pill 168 và 116 dp, FAB `primaryContainer` cùng vị trí (x 266 và 240 dp).
- `address-bar-05`, `-05-loading`, `-11` so với 05, 05--loading, 11 (dải trên 120 dp):
  - Thanh 380×56 ở (16, 32); icon và chữ bắt đầu cùng x.
  - Sóng cao 10 dp, tâm ở y 96 dp (mép dưới khung), vẽ đè lên trang; track bắt đầu cùng x 246 dp.
  - `-reduced-motion` là đường phẳng; `-dark`, `-not-secure` được xem bằng mắt.
- `search-bar-04/-04-dark/-09c` so với 04 (từ y 112 dp) và 09c: thanh 380×56, icon và chữ cùng vị trí (chữ ở 52 dp và 56 dp tính từ mép thanh).
- `shortcut-tiles-04/-dark` so với 04 (từ y 352 dp): lưới 4 cột, ô 64, thứ tự hình đúng.
- `shape-art-11/-01/-09c`, `marks-and-tiles/-dark` so với 11 (3 state), 01, 09c, 04c, 04 (Sunny 64): kích thước, role màu và icon trùng.
- `snackbars-15/-dark`, `snackbar-above-toolbar/-dark` so với panel snackbar của 15 (phóng 2 lần):
  - Cao 48, lỗi 2 dòng 68, bo 4.
  - Action cao 36, mép phải chữ action cách mép 16.
  - Đáy snackbar cách đỉnh toolbar đúng 8 dp (đáy cách cạnh 96).

Lệch tìm thấy (đã ghi vào "Known deviations"):
- bóng toolbar và FAB nhạt hơn (giữ elevation mặc định Compose theo tokens);
- số đếm tab weight 600 thay vì 700 (token thắng);
- khe sóng–track 8 dp so với 6 dp của HTML (theo spec "gap of 8"), chấm dừng lệch 1 dp;
- chữ hẹp hơn khoảng 3% nên chỗ xuống dòng khác (raster Chrome so với Skia);
- hình MaterialShapes khác hình xấp xỉ của công cụ vẽ: Flower 6 cánh trên bản vẽ, 8 cánh trong thư viện; Clover4Leaf; độ lõm của Sunny, Cookie, Clover8Leaf; Pentagon bo tròn hơn;
- ripple của icon button 40 dp so với 48 dp (chỉ thấy khi nhấn);
- nhãn nhóm toolbar là một điểm dừng TalkBack;
- live region lồng nhau ở snackbar lỗi.

Trạng thái chưa vẽ đã chọn: `99+`, trang `http`, thanh địa chỉ không có Reader view, snackbar trên màn điện thoại, ellipsis cho mọi snackbar có action.

## Lệch so với spec và lý do

1. **Cookie4Sided và Clover8Leaf được xoay theo hướng vẽ** (45° và 22,5°), rồi co giãn theo bound thật. Mọi board, kể cả bảng Shape library của 17, vẽ favicon là "kim cương" (thùy trên/dưới/trái/phải) và Clover8Leaf có thùy ở đỉnh; MaterialShapes 1.5.0-beta01 đặt thùy Cookie4Sided ở góc. Theo quy tắc "bản vẽ thắng", tôi xoay bằng `RoundedPolygon.transformed(Matrix)` trong `toDrawnShape()`. `toShape(startAngle)` không dùng được: nó chỉ đổi điểm bắt đầu outline, đã thử và thấy ảnh không đổi.
2. **`relativeTimeText` định dạng ngày bằng `android.icu.text.DateFormat.getInstanceForSkeleton("MMMd"/"yMMMd")`**, không gọi trực tiếp `DateUtils.formatDateTime`. Lý do: `DateUtils` cần Context, còn `UiText` không mang Context. Skeleton này chính là skeleton `DateUtils` dùng cho `FORMAT_SHOW_DATE | FORMAT_ABBREV_MONTH` (thêm năm khi khác năm). Hàm nhận thêm `locale` có default để test được. `bucketOf` cũng thêm `locale`.
3. **`UndoKind`, `UndoToken`, `SnackbarAction`, `SnackbarPlacement` nằm trong `SnackbarController.kt`**, đúng như task. Vì vậy `ui.snackbar` tham chiếu `ToolbarMetrics` của `ui.components` (96 = 24 + 64 + 8).
4. **API thêm, đều nhỏ:**
   - `UndoRegistry.kindOf(token)` (internal) để `dismissFor(kind)` vẫn đóng được snackbar khi registry đã `cancelAll` trước. Controller ghi lại kind lúc `show()`.
   - `snackbarDuration(hasAction)` (internal) để test ánh xạ.
   - `captureComponent(name)` và `boardGoldenPath(name)` trong `BoardCapture.kt`. `captureBoard` từ chối tên không có PNG tương ứng trong `design/screens/`.
   - `ShapeArt` nhận `container`/`content` (01, 09c, 11 dùng màu khác nhau); `SunnyMark` và `ShapeArt` có `contentDescription` tuỳ chọn ("Skim" ở 04).
5. **Vị trí sóng tải trang:** tâm sóng nằm trên mép dưới khung 8×16, tức 8 dp dưới thanh, đè 5 dp lên trang, đúng như HTML/PNG (`bottom: -6px`). Spec viết "ở cạnh dưới thanh". `AddressBar` đặt `zIndex(1f)` để sóng vẽ trên sibling phía sau (WebView ở phase 7).
6. **`HorizontalFloatingToolbar` được cố định cao 64.** Layout của material3 cao tối thiểu 80 dp (cỡ FAB khi thu gọn), nên nếu không cố định thì thanh nằm cách đáy bounds 8 dp, và `padding(bottom = 24.dp)` sẽ ra 32. Với chiều cao 64, bounds trùng thanh vẽ. Caller đặt `navigationBarsPadding().padding(bottom = ToolbarMetrics.BottomOffset)`.
7. **`TabSwitcherToolbar` luôn tắt Ask Skim khi `incognito`**, bất kể `aiEnabled`, vì AI không bao giờ chạy ở incognito.
8. **Build:** không đổi gì. Test JVM chạy đủ với heap mặc định; không cần `maxHeapSize`.

## API material3 1.5.0-beta01 đã xác nhận (sources jar tải từ Google Maven; graphics-shapes 1.0.1, compose ui 1.12.1 từ cache)

- `HorizontalFloatingToolbar(expanded, floatingActionButton, modifier, colors, contentPadding, scrollBehavior, shape, floatingActionButtonPosition, animationSpec, expandedShadowElevation, collapsedShadowElevation, content)`.
  - Đã stable, không cần opt-in.
  - Layout có `defaultMinSize(minHeight = 80.dp)` (lệch so với giả định 64 của spec, xử lý ở mục 6).
  - Không có khoảng cách giữa các nút (tự thêm `spacedBy(4.dp)`).
  - Màu nội dung mặc định là `contentColorFor(surfaceContainer)` = `onSurface`, nên tôi truyền `onSurfaceVariant`.
- `FloatingToolbarDefaults.StandardFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, interactionSource, content)`: không có `enabled`, tự `fillMaxSize()`, elevation Level 2. `ScreenOffset` = 16 dp, `ToolbarToFabGap` = 8 dp (internal).
- `LinearWavyProgressIndicator(progress: () -> Float, modifier, color, trackColor, stroke, trackStroke, gapSize, stopSize, amplitude: (Float) -> Float, wavelength, waveSpeed)`.
  - Mặc định: container cao 10 dp, stroke 4, wavelength 40, gap 4, stop 4.
  - `amplitude` là tỉ lệ 0..1 của chiều cao (1 tương ứng sóng 3 dp).
  - `gapSize` đo giữa hai nắp tròn.
- `RoundedPolygon.toShape(startAngle: Int = 0): Shape` (@Composable, ExpressiveApi). `startAngle` chỉ xoay để đỉnh đầu tiên nằm ở góc đó, không xoay hình một góc cố định. Dùng `androidx.graphics.shapes.transformed(android.graphics.Matrix)` và `calculateBounds(approximate = false)` thay thế.
- `SnackbarHostState.showSnackbar(visuals)`: hết giờ `Long` = 10 000 ms, `Short` = 4 000 ms, qua `calculateRecommendedTimeoutMillis`. `SnackbarHost` bọc mỗi snackbar trong `liveRegion = Polite` và pane title.
- `ComposeMaterial3Flags.isSnackbarStylingFixEnabled = false`, nên layout là bản Legacy: 1 dòng cao 48, 2 dòng cao 68, lề 16/8.
- `IconButton`: container 40 dp trong `minimumInteractiveComponentSize`; `iconButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)`.
- Compose ui 1.12.1: `SemanticsNode.touchBoundsInRoot` nới tới 48 dp cho mọi node có `OnClick`, kể cả khi bị cha clip. Vì vậy với nút nhỏ có click, `assertA11y` gần như luôn đạt phần kích thước; phần này chỉ bắt node toggle không có click, hoặc node thiếu nhãn.
- `pluralStringResource` không cần opt-in; `@PreviewLightDark` có sẵn trong ui-tooling-preview 1.12.1. Roborazzi 1.76.0: `captureScreenRoboImage` cần `@ExperimentalRoborazziApi`; đường dẫn tương đối tính từ thư mục module.

## Concerns

1. **Snackbar lỗi có hai live region lồng nhau:** `Assertive` trên snackbar, `Polite` ở lớp bọc của `SnackbarHost`. Semantics đúng spec và đã có test, nhưng cách TalkBack đọc thật cần kiểm ở cổng thiết bị phase 5.
2. **Hình học MaterialShapes khác hình xấp xỉ của bản vẽ.** Rõ nhất: Flower 6 cánh so với 8 cánh, Clover4Leaf "vuông thắt eo". Nếu designer muốn đúng hình vẽ thì phải tự dựng polygon, trái với tokens.md ("MaterialShapes").
3. **Previews `@PreviewLightDark` chỉ được biên dịch.** Chưa mở trong Android Studio. Snackbar trong preview của `SkimSnackbarHost` hiện qua `LaunchedEffect`, nên có thể chỉ thấy ở interactive mode (chưa kiểm). Preview `SkimSnackbar` vẽ tĩnh.
4. **Nhãn nhóm `Browser controls`/`Tab actions` thêm một điểm dừng TalkBack.** Nếu thấy thừa, có thể bỏ contentDescription của hàng nút.
5. **Lưu ý cho phần B:** `assertA11y` báo lỗi node có `ToggleableState` mà không có `OnClick` và nhỏ hơn 48. Cần gộp `Switch(onCheckedChange = null)` vào hàng `toggleable(mergeDescendants)` để nó không thành node riêng. `OverlaysScreenshotTest`/`ContentScreenshotTest` không cần chụp lại snackbar và tile (đã có ở `SnackbarScreenshotTest`, `ShapeTilesScreenshotTest`).

## Câu hỏi chưa giải quyết

1. Có chấp nhận việc xoay Cookie4Sided/Clover8Leaf theo bản vẽ (khác default thư viện), hay muốn giữ hướng của MaterialShapes?
2. Có cần dựng Flower 6 cánh và Clover4Leaf "vuông" riêng cho khớp bản vẽ, hay chấp nhận hình của MaterialShapes?
3. Copy cho trang `http` (`Site information`, `ic_public`) và quy tắc "99+" vẫn chờ design review.
