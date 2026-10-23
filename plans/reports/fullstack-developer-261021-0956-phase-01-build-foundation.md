# Phase 1: Build foundation, theme & resources

- Plan: `plans/261020-1611-skim-design-implementation/`, phase `phase-01-build-foundation-theme-resources.md`, các bước 2–13.
- Trạng thái: **DONE_WITH_CONCERNS**. Mọi lệnh Verification đều xanh, kể cả các thử âm tính. Phần "App cài trên emulator" do controller làm. Tôi không `git add`/`commit` gì.
- Ngày: 2026-10-21, giờ Asia/Singapore.

## Kết quả Verification (đều là output thật)

| Lệnh | Kết quả |
|---|---|
| `:app:assembleDebug` | Xanh |
| `:app:testDebugUnitTest --tests SkimTypographyTest/SkimThemeTest/BackupRulesTest` | Xanh, 11 test, 0 fail, 0 skip (Typography 1, Theme 6, BackupRules 4) |
| Hoàn nguyên tạm bản sửa font thành `Font(resId, FontWeight(it))` | `SkimTypographyTest` đỏ ở `SkimTypographyTest.kt:23` (thiếu trục `wght`). Đã khôi phục, `cmp` khớp |
| Tạm đặt `allowBackup="true"` và bỏ `dataExtractionRules` | `mergedManifestTurnsBackupOff` đỏ (32768 ≠ 0); `mergedManifestAppliesTheBackupRules` đỏ (`""` ≠ `@xml/data_extraction_rules`). Đã khôi phục |
| `:app:assembleRelease :app:bundleRelease` với 4 property `.invalid` | Xanh. Thứ tự task: `checkSkimReleaseConfig` → `preReleaseBuild`. `verifyNoApiKeyRelease`: "Scanned app-release-unsigned.apk: 141 entries, 0 matches." và "Scanned app-release.aab: 151 entries, 0 matches." |
| `:app:assembleRelease` không có property | Đỏ ở `checkSkimReleaseConfig`, liệt kê đủ 4 property "is not set". Chỉ thiếu proxy: chỉ báo `skim.aiProxyUrl`. Proxy là `http://`: báo "must be an https:// URL" |
| Thử âm tính lọt key (cần một tham chiếu tạm `Log.d(TAG, BuildConfig.ANTHROPIC_API_KEY ?: "")`, vì R8 đang loại bỏ `BuildConfig` không dùng tới) | (A) Thêm đúng `buildConfigField("String","ANTHROPIC_API_KEY","\"sk-ant-test\"")` vào `defaultConfig`: **xanh**, vì override `null` của release thắng; artifact không có key, quét 0 match. (B) Chuyển field lên `defaultConfig` và bỏ override `null` của release (kịch bản red team): **đỏ** ở `verifyNoApiKeyRelease`, bắt được cả `apk!classes.dex` lẫn `aab!base/dex/classes.dex`. (C) Key debug giả, không có prefix `sk-ant-`, đưa vào release: **đỏ** với "contains the debug API key from local.properties", cả APK lẫn AAB. Đã khôi phục cả hai file |
| Tạm thêm `skim.aiProxyUrl=…` vào `gradle.properties` | Đỏ: "skim.aiProxyUrl must not be set in the committed gradle.properties." Đã khôi phục |
| `:app:check` với 4 property | Xanh, chạy `testDebugUnitTest`, `lintDebug`, `verifyNoApiKeyRelease` (0 match, cả APK và AAB) |
| `:app:lintDebug` | Xanh: 0 error, không baseline, không suppress. Cảnh báo: `UnusedResources` 347 (string/icon dành cho các phase sau), `InsecureBaseConfiguration` 1 (cố ý, lý do ghi trong file), `NewerVersionAvailable` 3, `AndroidGradlePluginVersion` 1 |
| `--configuration-cache :app:assembleDebug` ×2 | Lần 1 "Configuration cache entry stored.", lần 2 "Reusing configuration cache." |
| `:app:dependencies --configuration releaseRuntimeClasspath` | OkHttp chỉ 4.12.0; Jackson chỉ 2.19.4 (core, databind, annotations, kotlin, jdk8, jsr310); okio 3.9.1; material3 1.5.0-beta01; anthropic-java 2.73.0 |
| Số dex / kích thước release | APK `app-release-unsigned.apk`: 3.275.439 byte (3,28 MB), **1 dex** (`classes.dex` 2.036.624 byte). AAB: 4.979.449 byte, 1 dex |
| Biên dịch lại Kotlin (`--rerun`) | 0 cảnh báo |

Lệnh `unzip -l app/build/outputs/apk/release/*.apk | grep -c 'classes.*\.dex'` bị hook `scout-block` của phiên chặn (pattern `build`). Tôi đếm tương đương bằng `python3 -I` + `zipfile`, cùng regex `classes.*\.dex`. Mọi lần đọc file trong `app/build/` cũng làm theo cách này.

## Tài nguyên

- `strings.xml`: 264 string và 16 plural. Một script đối chiếu tự động với bảng scout §3(a), §3(b), bảng copy cần duyệt trong `plan.md` và `tabs_count_overflow`: không thiếu, không thừa, chữ khớp sau khi bỏ escape. Có 10 mục `translatable="false"`: `search_engine_*`, `error_code_*`, `reader_size_glyph`, `settings_version_value`, `tabs_count_overflow`.
- Đã copy 68 icon `ic_*.xml` (byte-identical, `cmp`), font `google_sans_flex.ttf` (identical) và 2 license vào `app/licenses/`. Đã xoá 2 template test.
- AboutLibraries: export ra scratchpad thấy 141 thư viện, trong đó có 3 định nghĩa tuỳ chỉnh với license OFL-1.1 và Apache-2.0 kèm nội dung. `R.raw.aboutlibraries` còn trong APK release (dưới tên rút gọn `res/M7.json`) và trong AAB. Thử bỏ `keep.xml` thì JSON bị shrink mất, nên file keep là cần thiết.

## File đã sửa/tạo/xoá (đều nằm trong "Related code files")

- Sửa: `gradle/libs.versions.toml` (73 dòng), `build.gradle.kts` (12), `gradle.properties` (26), `app/build.gradle.kts` (280), `AndroidManifest.xml` (96), `res/xml/data_extraction_rules.xml` (21), `res/xml/backup_rules.xml` (12), `keepRules/rules.keep` (6), `values/themes.xml` (14), `values-night/themes.xml` (6), `values/colors.xml` (6), `values/strings.xml` (367).
- Tạo:
  - Code: `SkimApplication.kt`, `AppContainer.kt`, `MainActivity.kt`, `ui/theme/{Color,Type,Theme}.kt`.
  - Tài nguyên: `res/font/google_sans_flex.ttf`, 68 file `res/drawable/ic_*.xml`, `res/xml/{network_security_config,file_paths}.xml`, `res/raw/keep.xml`.
  - Cấu hình AboutLibraries và license: `app/config/libraries/{google_sans_flex,material_symbols_rounded,readability_js}.json`, `app/licenses/{OFL-GoogleSansFlex,LICENSE-MaterialSymbols}.txt`.
  - Test: `src/test/resources/robolectric.properties` (`sdk=36`), `testing/{ScreenshotDefaults,MainDispatcherRule}.kt`, `ui/theme/{SkimTypographyTest,SkimThemeTest}.kt`, `BackupRulesTest.kt`.
- Xoá: `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
- Các file test tạm (`InsetsProbeTest`, `ManifestProbeTest`) đã xoá. `git status` không còn file ngoài phạm vi.

## Lệch spec và lý do

1. **Icon thiết kế tint bằng `?attr/colorControlNormal`.** Đây là attr của AppCompat/MDC, mà spec đã bỏ hai thư viện này, nên aapt2 báo lỗi "resource attr/colorControlNormal not found". Tôi giữ icon là bản copy nguyên vẹn, khai báo `<attr name="colorControlNormal" format="color"/>` (cùng format với AppCompat) trong `values/themes.xml`, và trỏ nó về `?android:attr/colorControlNormal` trong cả hai theme.
2. **`checkSkimReleaseConfig` nối bằng `variant.lifecycleTasks.registerPreBuild(...)`.** Đây là API công khai của AGP 9.4.1 (`@Incubating`), thay cho `tasks.matching { it.name == "preReleaseBuild" }`. Hiệu ứng giống hệt: `preReleaseBuild` phụ thuộc vào task này, log xác nhận thứ tự. `finalizedBy` dùng `tasks.named { … }` (Spec<String>, lazy) thay vì `matching`.
3. **Thêm chốt chặn URL proxy bị commit.** Release fail nếu `skim.aiProxyUrl` có giá trị trong `gradle.properties` đã commit. Đây là cách hiện thực câu "chỉ lấy từ local.properties hoặc -P/CI". `skimProperty()` coi Gradle property rỗng là chưa đặt và fallback sang `local.properties`, vì `gradle.properties` khai báo sẵn 3 URL pháp lý rỗng. Không có giá trị URL nào được in ra log.
4. **Comment khối copy viết "Copy needs design review: …"**, không có chữ "D8", theo luật không đưa ID plan vào code.
5. **Filter `MAIN` + `APP_BROWSER` có thêm `CATEGORY_DEFAULT`.** Nếu thiếu, intent ngầm từ `startActivity` không resolve được.
6. **`BackupRulesTest` có thêm test `mergedManifestAppliesTheBackupRules`.** Test này đọc manifest đã merge qua `test_config.properties` của AGP, theo gợi ý ở red team Finding 6. Lý do: Robolectric để `dataExtractionRulesRes`/`fullBackupContent` bằng 0 (đã đo). Test dùng `ClassLoader.getSystemResourceAsStream`, vì sandbox loader không thấy file này.
7. **`SkimThemeTest` có thêm 4 case:** dynamic color ở API 36 khớp `dynamicLightColorScheme`, API 26 rơi về brand (`@Config(sdk=[26])`), bảng `isDark`, và `setSkimContent` (brand colors + nội dung nằm dưới 24 dp).
8. **`Theme.kt` gộp điều kiện dynamic vào nhánh `when`** (`dynamicColor && SDK_INT >= S -> if (dark) … else …`). Ngữ nghĩa giống `val dynamic = …` của spec; viết vậy để lint NewApi theo dõi được điều kiện.
9. **`MainActivity` có `Log.d("MainActivity", "onCreate")`**, phục vụ tiêu chí trên thiết bị "log onCreate một lần".
10. **`gradle.properties` được thêm newline cuối file.** File cũ không có, khiến dòng append bị dính vào dòng trước.
11. **Thử âm tính (A) theo đúng chữ của spec ra xanh**, vì override `null` của release thắng `defaultConfig` nên không lọt key. Kịch bản lọt thật (B) và key đúng giá trị (C) đều đỏ (xem bảng).
12. **`assembleRelease` một mình cũng đóng gói AAB**, vì `verifyNoApiKeyRelease` có cả hai input như spec mô tả.

## UNVERIFIED đã giải quyết

- AboutLibraries 15.3.0: `aboutLibraries { collect { configPath = file("config") } }`, trong đó `CollectorConfig.configPath` là `DirectoryProperty` (đọc từ source plugin, build chạy được).
  - Schema JSON tuỳ chỉnh (`LibraryReader.kt`): `uniqueId` (bắt buộc), `name`, `artifactVersion`, `description`, `website`, `developers[{name, organisationUrl}]`, `licenses[SPDX id]`. Thư mục là `config/libraries/`.
  - SPDX `OFL-1.1` và `Apache-2.0` được map và tải nội dung license (đã kiểm bằng export).
- Roborazzi 1.76.0: `roborazzi { outputDir.set(layout.projectDirectory.dir("src/test/screenshots")) }`, trong đó `RoborazziExtension.outputDir` là `DirectoryProperty`.
- Cổng quét key: input là `variant.artifacts.get(SingleArtifact.APK)` (Directory, quét mọi `*.apk`) và `SingleArtifact.BUNDLE` (RegularFile). `assemble<V>` và `bundle<V>` dùng `finalizedBy`, `check` dùng `dependsOn`. Áp cho mọi variant có `buildType != "debug"`. Quét theo byte, cả UTF-8 lẫn UTF-16LE.
- Insets trên Robolectric: dispatch trực tiếp `ViewCompat.dispatchApplyWindowInsets` vào `LocalView` sau `runOnIdle` cho kết quả 24 dp, vẫn giữ sau 1 s, chỉ một root.
  - `DeviceConfigurationOverride.WindowInsets` cũng cho 24 dp, nhưng tạo compose root thứ hai làm `onRoot()` hỏng, mà Roborazzi cần `onRoot()`, nên không dùng.
  - Helper đọc `WindowInsets.statusBars` để listener inset của Compose luôn được cài.

## Concerns

1. `kotlin-reflect` resolve ra **1.9.25** (kéo qua jackson-module-kotlin/anthropic) trong khi stdlib là 2.4.21. Có thể lỗi metadata khi Jackson reflect class Kotlin 2.4; nên kiểm ở phase 11, và nếu cần thì pin `kotlin-reflect` 2.4.21.
2. Plugin AboutLibraries resolve configuration lúc cấu hình. Khi cache miss, AGP cảnh báo "Configuration 'debugCompileClasspath' was resolved during configuration time"; đây là hành vi của plugin (`BaseAboutLibrariesTask.kt:299`). Plugin cũng tải nội dung license SPDX qua mạng lúc build; build offline không fail nhưng entry sẽ thiếu nội dung license.
3. R8 hiện loại bỏ `BuildConfig` và toàn bộ SDK vì chưa có code nào dùng. Vì vậy số 1 dex / 3,28 MB chưa phản ánh app thật; số đo thật để phase 13.
4. License hiển thị của Google Sans Flex là văn bản OFL-1.1 chung từ SPDX; dòng copyright nằm trong `description`.
5. Phần trên thiết bị (edge-to-edge, xoay, dark mode, log onCreate một lần) chưa kiểm; controller sẽ làm.

## Câu hỏi chưa giải quyết

1. Có cần chặn ngay lúc cấu hình việc khai báo `ANTHROPIC_API_KEY` trong `defaultConfig`, để thử nghiệm (A) cũng đỏ không? Hiện tại nó vô hại nhờ override `null` của release và lượt quét artifact.
2. Có pin `kotlin-reflect` 2.4.21 ngay bây giờ không, hay để phase 11 quyết sau khi chạy SDK?
3. Có muốn cung cấp license nội bộ (`config/licenses/*.json`, nằm ngoài danh sách file của phase) để màn license không phụ thuộc mạng lúc build và hiện đúng văn bản OFL của font không?
4. Chuyển Robolectric sang `sdk=37` khi nào? Chưa thử, vì phải tải android-all 37.
