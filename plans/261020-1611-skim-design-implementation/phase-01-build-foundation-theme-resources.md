---
phase: 1
title: "Build foundation, theme & resources"
status: completed
priority: P1
effort: "17h"
dependencies: []
---

# Phase 1: Build foundation, theme & resources

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Build stack: `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §1–§3 (toml, plugin), §8 (edge-to-edge), §9 (lỗi font), §10 (AboutLibraries), §11 (test).
- SDK Claude + R8: `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §0.2, §C11, bảng rủi ro (AutoValue, packageScope, key lọt release).
- Red team: `plans/261020-1611-skim-design-implementation/reports/red-team-security-adversary.md` (Finding 1, 6, 7), `red-team-failure-mode-analyst.md` (Finding 6, 10).
- Strings/cd/plural: `plans/reports/scout-261020-2319-design-inventory.md` §3(a), §3(b), quy ước escape ở §0.
- Thiết kế: `design/README.md` (Build notes), `design/spec/tokens.md`, `design/compose/{Theme,Type,Color}.kt`, `design/compose/res/font/google_sans_flex.ttf`, `design/compose/licenses/OFL-GoogleSansFlex.txt`, `design/icons/*.xml`, `design/icons/LICENSE-MaterialSymbols.txt`.
- Scaffold hiện tại: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{themes,strings,colors}.xml`, `app/src/main/res/values-night/themes.xml`, `app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`, `app/src/main/keepRules/rules.keep`.

## Overview

<!-- Updated: Validation Session 1 - commit checkpoint + nhánh feat ở đầu phase 1 -->
Mở đầu bằng commit khởi tạo trên `master` và tạo nhánh `feat/skim-design-implementation`. Sau đó dựng nền build cho cả dự án: Kotlin 2.4.21 trên AGP 9.4.1 (built-in Kotlin), Compose + Material 3 Expressive 1.5.0-beta01, toàn bộ dependency của các phase sau, BuildConfig cho AI (debug gọi thẳng Anthropic, release gọi proxy), R8 cho release, kiểm tra không lọt key cho mọi variant không phải debug, manifest đầy đủ (kể cả chặn backup và device-to-device transfer), theme port từ `design/compose`, font và 68 icon, toàn bộ `strings.xml`, và hạ tầng test. Kết thúc phase, app build được ở debug và release, mở ra một màn hình trống theo theme Skim, edge-to-edge.

## Key insights

- AGP 9.4.1 kéo KGP 2.2.10; khai báo `org.jetbrains.kotlin.plugin.compose`/`plugin.serialization` 2.4.21 ở `plugins {}` gốc sẽ đẩy KGP lên 2.4.21 (researcher A §0.1). Không áp dụng `org.jetbrains.kotlin.android`.
- material3 1.4.0 không có API Expressive; pin `material3:1.5.0-beta01` đè BOM 2026.09.00 (researcher A §0.2).
- `design/compose/Type.kt` render mọi weight ở 400 vì overload `Font(resId, weight)` truyền `FontVariation.Settings()` rỗng; phải truyền `variationSettings` tường minh (researcher A §9). Có unit test chặn hồi quy.
- D1 (đã chốt — Validation Session 1): bỏ `packageScope` để R8 shrink cả SDK Anthropic (APK probe 9,65 MB / 4 dex khi giữ `packageScope`), cộng `-dontwarn com.google.auto.value.AutoValue` (researcher B §0.2).
- WebView tôn trọng chính sách cleartext với app target ≥ 26 (docs `<application>`, đã kiểm 2026-10-21). Trình duyệt phải mở được trang `http://`, nên dùng `network_security_config.xml` với `base-config cleartextTrafficPermitted="true"`; URL AI của release bị ép `https://` lúc build. Câu "`usesCleartextTraffic` bị bỏ qua từ target 38" lấy từ cùng trang docs `<application>` (đọc 2026-10-21), không có bản lưu trong repo.
<!-- Red Team: D2D transfer vẫn chạy khi allowBackup=false — 2026-10-21 -->
- Với target ≥ 31, `android:allowBackup="false"` chỉ tắt cloud backup, không tắt device-to-device transfer. Muốn đúng copy "Stored only on this phone"/"Never synced" phải giữ `dataExtractionRules` có `<cloud-backup>` và `<device-transfer>` loại trừ mọi domain, cộng `fullBackupContent` cho API ≤ 30.
<!-- Red Team: Proxy relay mở và cổng chặn key — 2026-10-21 -->
- URL proxy release là thông tin vận hành: không commit vào `gradle.properties`; đọc từ `local.properties` hoặc Gradle property của CI. Kiểm tra lọt key phải chạy cho mọi variant không phải debug, cả APK lẫn AAB (`bundleRelease`), và tìm đúng giá trị key debug chứ không chỉ prefix `sk-ant-`.
- Robolectric trên JDK 25 cần cờ `--add-opens`, thiếu thì crash (researcher A §11).

## Requirements

Chức năng:
<!-- Updated: Validation Session 1 - commit checkpoint cuối mỗi phase -->
- Commit khởi tạo trên `master` (`chore: initial project scaffold and design handoff`), không stage `.claude/`, `.idea/workspace.xml`, `local.properties`; sau đó tạo và chuyển sang nhánh `feat/skim-design-implementation`. Cuối phase một commit conventional.
- `./gradlew :app:assembleDebug`, `:app:assembleRelease` và `:app:bundleRelease` (khi các property release đã đặt) chạy thành công.
- BuildConfig có `AI_BASE_URL`, `ANTHROPIC_API_KEY` (chỉ debug có giá trị, release là `null`), `TERMS_URL`, `PRIVACY_URL`, `AI_DATA_POLICY_URL`.
- Manifest: một `MainActivity` exported, `launchMode="singleTask"`, filter MAIN/LAUNCHER, VIEW http/https (DEFAULT + BROWSABLE, không giới hạn host), MAIN + `CATEGORY_APP_BROWSER`; `configChanges` để xoay/đổi uiMode/đổi độ đậm chữ không tạo lại Activity; `windowSoftInputMode="adjustResize"`; quyền INTERNET, ACCESS_NETWORK_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, WRITE_EXTERNAL_STORAGE `maxSdkVersion=28`; `<queries>` cho `android.speech.action.RECOGNIZE_SPEECH`; `FileProvider`.
- Backup: `allowBackup="false"`, `dataExtractionRules` (cloud-backup + device-transfer loại trừ `root`, `file`, `database`, `sharedpref`, `external`), `fullBackupContent` loại trừ cùng domain cho API ≤ 30.
- Theme Compose `SkimTheme(theme, incognito, dynamicColor)` dùng `MaterialExpressiveTheme` + `MotionScheme.expressive()`; dynamic color từ API 31 khi `dynamicColor = true`.
- `strings.xml` chứa mọi string, plural và content description của scout §3(a)(b), các string `[DEFAULT — chờ validate]` D8 (bảng trong `plan.md`), và mã lỗi NT.

Phi chức năng:
- Configuration cache vẫn bật và lưu được entry.
- Không commit key hay URL proxy: cả hai đọc từ `local.properties` (đã nằm trong `.gitignore`) hoặc Gradle property của CI.
- Key debug là key riêng của workspace dev, có spend limit, xoay định kỳ; không phân phối APK debug (ghi trong README ở phase 13).
- `appScope` có `CoroutineExceptionHandler`: lỗi không làm crash process im lặng hay treo màn hình; lỗi được log và lưu ở `AppContainer.lastError` để shell (phase 5) mở trạng thái dùng được.

## Architecture

Cấu hình build:

```kotlin
// app/build.gradle.kts (trích phần quyết định)
val localProps = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
fun prop(name: String): String = providers.gradleProperty(name).orNull ?: localProps.getProperty(name, "")
val debugApiKey = localProps.getProperty("skim.anthropicApiKey", "")
android {
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        buildConfigField("String", "TERMS_URL", "\"${prop("skim.termsUrl")}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${prop("skim.privacyUrl")}\"")
        buildConfigField("String", "AI_DATA_POLICY_URL", "\"${prop("skim.aiDataPolicyUrl")}\"")
    }
    buildTypes {
        debug {
            buildConfigField("String", "AI_BASE_URL", "\"https://api.anthropic.com\"")
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"$debugApiKey\"")
        }
        release {
            buildConfigField("String", "AI_BASE_URL", "\"${prop("skim.aiProxyUrl")}\"")
            buildConfigField("String", "ANTHROPIC_API_KEY", "null")
            optimization { enable = true }   // D1 (đã chốt): không còn packageScope
        }
    }
}
```

- Task `checkSkimReleaseConfig` (chạy trước `preReleaseBuild`): fail nếu `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, `skim.aiDataPolicyUrl` rỗng hoặc không bắt đầu bằng `https://`. URL pháp lý có thể nằm trong `gradle.properties` (không bí mật); `skim.aiProxyUrl` chỉ được lấy từ `local.properties` hoặc `-P`/biến môi trường của CI.
<!-- Red Team: Cổng chặn key bỏ sót AAB — 2026-10-21 -->
- Kiểm tra lọt key qua `androidComponents.onVariants(selector().all())`: với mọi variant có `buildType != "debug"`, đăng ký `verifyNoApiKey<Variant>` có input là `variant.artifacts.get(SingleArtifact.APK)` và `SingleArtifact.BUNDLE`; task mở từng entry của APK/AAB và fail nếu gặp `sk-ant-` hoặc đúng chuỗi `debugApiKey` (khi khác rỗng). `assemble<Variant>`, `bundle<Variant>` finalizedBy task này; `check` phụ thuộc mọi task này. Chỉ dùng provider/`layout` để tương thích configuration cache.
- Ở debug, URL pháp lý rỗng thì link hiển thị dạng chữ thường, không bấm được, và log cảnh báo (không dùng URL giả).

Theme (`com.dantech.skimbrowser.ui.theme`):

```kotlin
enum class ThemeSetting { System, Light, Dark }
fun ThemeSetting.isDark(systemDark: Boolean, incognito: Boolean): Boolean
@Composable fun SkimTheme(theme: ThemeSetting, incognito: Boolean = false, dynamicColor: Boolean = true, content: @Composable () -> Unit)
val GoogleSansFlex = FontFamily(listOf(400, 500, 600).map {
    Font(R.font.google_sans_flex, FontWeight(it), variationSettings = FontVariation.Settings(FontWeight(it), FontStyle.Normal))
})
```

Khởi động: `SkimApplication.onCreate` tạo `AppContainer(this)` (DI thủ công). Ở phase này container có `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "uncaught", e); lastError.value = e })` và `lastError: MutableStateFlow<Throwable?>`; các phase sau thêm thành phần. `MainActivity : ComponentActivity` gọi `enableEdgeToEdge()` trước `super.onCreate`, rồi `setContent { SkimTheme(ThemeSetting.System) { Surface(Modifier.fillMaxSize()) {} } }`; phase 5 thay nội dung bằng `SkimApp`.

Luồng dữ liệu: `local.properties` / Gradle property CI / `gradle.properties` → Gradle → `BuildConfig` → (phase 11) `ClaudeClientProvider`. Không có dữ liệu runtime nào khác trong phase này.

## Related code files

Sửa:
- `gradle/libs.versions.toml`: thay bằng khối của researcher A §2, cộng `anthropic = "2.73.0"` (`com.anthropic:anthropic-java`), `jsoup = "1.23.2"` (`org.jsoup:jsoup`), `okhttp = "4.12.0"` (`com.squareup.okhttp3:okhttp`, khai báo tường minh vì Skim dùng trực tiếp cho gợi ý tìm kiếm, share ảnh và tải xuống từ tab incognito, cùng version SDK đang kéo), `com.squareup.okhttp3:mockwebserver:4.12.0` (`testImplementation` và `androidTestImplementation`). Bỏ `appcompat`, `material`. Bỏ `room-testing` (schema v1, chưa có migration).
- `build.gradle.kts`: khối plugin gốc theo researcher A §3.
- `gradle.properties`: thêm `skim.termsUrl=`, `skim.privacyUrl=`, `skim.aiDataPolicyUrl=` kèm comment giải thích (giá trị do chủ sản phẩm cung cấp). Không thêm `skim.aiProxyUrl` (comment ghi rõ: đặt trong `local.properties` hoặc CI).
- `app/build.gradle.kts`: plugin (android.application, kotlin.compose, kotlin.serialization, ksp, room, aboutlibraries, roborazzi), `buildFeatures`, BuildConfig, `optimization` (D1), `testOptions.unitTests` (includeAndroidResources + jvmArgs `--add-opens` như researcher A §3), `room { schemaDirectory("$projectDir/schemas") }`, `aboutLibraries { collect { configPath = file("config") } }` `[UNVERIFIED: tên property DSL 15.3.0]`, `roborazzi { outputDir.set(layout.projectDirectory.dir("src/test/screenshots")) }` `[UNVERIFIED: tên DSL]`, `checkSkimReleaseConfig`, các task `verifyNoApiKey<Variant>`, dependencies đầy đủ.
- `app/src/main/AndroidManifest.xml`: như Requirements; `android:name=".SkimApplication"`, `android:allowBackup="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/backup_rules"`, `android:networkSecurityConfig="@xml/network_security_config"`, `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode|keyboard|keyboardHidden|navigation|density|fontScale|layoutDirection|locale|fontWeightAdjustment"`, `<uses-feature>` camera/camera.autofocus/location.gps/microphone với `required="false"`.
- `app/src/main/res/xml/data_extraction_rules.xml`: `<cloud-backup>` và `<device-transfer>` mỗi khối `exclude` đủ domain `root`, `file`, `database`, `sharedpref`, `external`.
- `app/src/main/res/xml/backup_rules.xml`: `<full-backup-content>` loại trừ cùng các domain (API ≤ 30).
- `app/src/main/keepRules/rules.keep`: thêm `-dontwarn com.google.auto.value.AutoValue` kèm comment lý do (opentelemetry-api tham chiếu annotation chỉ có lúc compile).
- `app/src/main/res/values/themes.xml`: parent `android:Theme.Material.Light.NoActionBar`, `android:windowBackground=@color/skim_window_light`, bỏ `statusBarColor` và các màu MaterialComponents.
- `app/src/main/res/values-night/themes.xml`: parent `android:Theme.Material.NoActionBar`, `windowBackground=@color/skim_window_dark`.
- `app/src/main/res/values/colors.xml`: chỉ còn `skim_window_light` `#F8F5FF` và `skim_window_dark` `#07092F` (surface của Color.kt).
- `app/src/main/res/values/strings.xml`: toàn bộ strings + plurals + cd (xem bước 7).

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/SkimApplication.kt`
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/theme/Color.kt` (port, đổi package)
- `app/src/main/java/com/dantech/skimbrowser/ui/theme/Type.kt` (port + sửa font)
- `app/src/main/java/com/dantech/skimbrowser/ui/theme/Theme.kt` (port + `dynamicColor`, `isDark`)
- `app/src/main/res/font/google_sans_flex.ttf` (copy)
- `app/src/main/res/drawable/ic_*.xml` (68 file copy từ `design/icons/`)
- `app/src/main/res/xml/network_security_config.xml`, `app/src/main/res/xml/file_paths.xml` (`<cache-path name="shared" path="shared/"/>`; thư mục con `shared/incognito/` do phase 4/7 dùng)
- `app/src/main/res/raw/keep.xml` (`tools:keep="@raw/aboutlibraries"`)
- `app/config/libraries/google_sans_flex.json`, `app/config/libraries/material_symbols_rounded.json`, `app/config/libraries/readability_js.json` (định nghĩa thư viện tuỳ chỉnh cho AboutLibraries; license SPDX `OFL-1.1`, `Apache-2.0`) `[UNVERIFIED: schema JSON của AboutLibraries 15.3.0]`
- `app/licenses/OFL-GoogleSansFlex.txt`, `app/licenses/LICENSE-MaterialSymbols.txt` (giữ bản gốc trong repo)
- `app/src/test/resources/robolectric.properties` (`sdk=36`; chuyển 37 khi đã kiểm Robolectric 4.17 tải được android-all 37)
- `app/src/test/java/com/dantech/skimbrowser/testing/ScreenshotDefaults.kt` (hằng `PHONE_QUALIFIERS = "w412dp-h915dp-xhdpi"`, helper `setSkimContent(dark, incognito, statusBarInset = 24.dp)` gọi `SkimTheme(dynamicColor = false)`, helper chèn insets bằng `ViewCompat.dispatchApplyWindowInsets` `[UNVERIFIED trên Robolectric; dự phòng: bù 24dp khi so]`)
- `app/src/test/java/com/dantech/skimbrowser/testing/MainDispatcherRule.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/theme/SkimTypographyTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/theme/SkimThemeTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/BackupRulesTest.kt` (Robolectric: `ApplicationInfo.FLAG_ALLOW_BACKUP` tắt trong manifest đã merge; `R.xml.data_extraction_rules` có `cloud-backup` và `device-transfer` với đủ 5 `exclude`; `R.xml.backup_rules` có đủ 5 `exclude`)

Xoá:
- `app/src/test/java/com/dantech/skimbrowser/ExampleUnitTest.kt`, `app/src/androidTest/java/com/dantech/skimbrowser/ExampleInstrumentedTest.kt` (template, thay bằng test thật)

## Implementation steps

<!-- Updated: Validation Session 1 - commit khởi tạo + nhánh -->
1. Git: trên `master`, `git add` scaffold hiện có (`.gitignore`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew*`, `app/`, `.idea/` trừ `workspace.xml`) và `design/` (không stage `.claude/`, `.idea/workspace.xml`, `local.properties`; kiểm `git status` trước khi commit), commit `chore: initial project scaffold and design handoff`, rồi `git switch -c feat/skim-design-implementation`. Không push.
2. Thay `gradle/libs.versions.toml` theo researcher A §2 cộng các mục ở trên. Chạy `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` để chắc OkHttp/Jackson chỉ có một version.
3. Cập nhật `build.gradle.kts` gốc và `app/build.gradle.kts`. Giữ `namespace`, `applicationId`, `compileSdk 37`, `minSdk 26`, `targetSdk 37`, `versionCode 1`, `versionName "1.0"`. D1 (đã chốt): xoá dòng `packageScope`, giữ `optimization { enable = true }`.
4. Viết `checkSkimReleaseConfig` và các task `verifyNoApiKey<Variant>` như Architecture; nối `tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkSkimReleaseConfig) }`.
5. Viết manifest như Requirements. Filter VIEW phải có cả `<data android:scheme="http"/>` và `https`, không có `host`, để `BrowserRoleBehavior` chấp nhận (researcher B §B10). Viết lại `data_extraction_rules.xml` và `backup_rules.xml`.
6. Viết `network_security_config.xml`: `<base-config cleartextTrafficPermitted="true">` với trust anchors `system` (không thêm `user`).
7. Viết `strings.xml`:
   - Copy nguyên văn mọi dòng của scout §3(a) (string + plural) và §3(b) (cd). Escape theo scout §0: `\'`, `&amp;`, `%%`, giữ `…` và `›`. Đánh `translatable="false"` cho `search_engine_*`, `reader_size_glyph`, `settings_version_value`, `error_code_*`.
   - Đổi `app_name` thành `Skim`.
   - Plural: `other` đúng bản vẽ; `one` suy ra từ `other` (quyết định nhỏ đã chốt), riêng `skim_history_questions_about` và `skim_history_answers_across_tabs` dùng dạng `one` đã vẽ.
   - Thêm các string `[DEFAULT — chờ validate]` D8 đúng như bảng "Copy cần design review" trong `plan.md` (khối comment `<!-- D8: copy needs design review -->`).
   - Thêm string NT `tabs_count_overflow` = `99+` (`translatable="false"`, dùng cho nút Tabs khi ≥ 100 tab).
8. Copy `design/compose/res/font/google_sans_flex.ttf` → `app/src/main/res/font/`; copy 68 file `design/icons/ic_*.xml` → `app/src/main/res/drawable/`; copy hai file license vào `app/licenses/`.
9. Port `design/compose/Color.kt`, `Type.kt`, `Theme.kt` sang `com.dantech.skimbrowser.ui.theme`, đổi import `com.skim.browser.R` → `com.dantech.skimbrowser.R`. Sửa `GoogleSansFlex` như Architecture. Thêm tham số `dynamicColor: Boolean = true` (`val dynamic = dynamicColor && SDK_INT >= 31`) và hàm `ThemeSetting.isDark(...)` dùng chung cho theme và thanh hệ thống.
10. Viết `SkimApplication`, `AppContainer` (kèm `CoroutineExceptionHandler`, `lastError`), `MainActivity` như Architecture.
11. Tạo file định nghĩa thư viện tuỳ chỉnh cho AboutLibraries (Google Sans Flex OFL-1.1, Material Symbols Rounded Apache-2.0, Readability.js 0.6.0 Apache-2.0) và `res/raw/keep.xml`.
12. Hạ tầng test: `robolectric.properties`, `ScreenshotDefaults.kt`, `MainDispatcherRule.kt`. Viết `SkimTypographyTest` (mỗi `Font` trong `GoogleSansFlex.fonts` ép kiểu `ResourceFont`, `variationSettings` có trục `wght` bằng weight 400/500/600), `SkimThemeTest` (`SkimTheme(ThemeSetting.Light, dynamicColor = false)` cho `primary == Color(0xFF004FD9)`; `incognito = true` cho `surface == Color(0xFF07092F)`), `BackupRulesTest`.
13. Xoá template test; chạy toàn bộ Verification.
14. Commit cuối phase: `build: set up compose build, theme, resources and release checks` (conventional, không nhắc AI, không có secret hay `local.properties`).

## Todo list

- [x] Commit khởi tạo trên master + tạo nhánh `feat/skim-design-implementation`
- [x] Thay version catalog, thêm anthropic/jsoup/okhttp/mockwebserver, bỏ appcompat/material
- [x] Khối plugin gốc và app (built-in Kotlin, compose, serialization, ksp, room, aboutlibraries, roborazzi)
- [x] BuildConfig AI/URL pháp lý, key debug và URL proxy từ local.properties/CI, release key = null
- [x] D1: bỏ `packageScope`, thêm `-dontwarn com.google.auto.value.AutoValue`
- [x] `checkSkimReleaseConfig` + `verifyNoApiKey<Variant>` cho mọi variant không phải debug (APK + AAB, đúng giá trị key)
- [x] Manifest đầy đủ (activity, filter, quyền, queries, FileProvider, network security config)
- [x] Chặn cloud backup + device-to-device transfer (dataExtractionRules + fullBackupContent) + BackupRulesTest
- [x] Theme XML NoActionBar + màu window
- [x] strings.xml đầy đủ (scout §3(a)(b) + D8 + mã lỗi)
- [x] Copy font, 68 icon, license
- [x] Port theme + sửa font + `dynamicColor`
- [x] SkimApplication, AppContainer (exception handler), MainActivity edge-to-edge
- [x] AboutLibraries custom definitions + keep resource
- [x] Hạ tầng test + SkimTypographyTest + SkimThemeTest; xoá template test
- [x] Commit cuối phase

## Success criteria

- `git log --oneline master` có commit khởi tạo; nhánh hiện tại là `feat/skim-design-implementation`.
- `assembleDebug` xanh; `assembleRelease` và `bundleRelease` (có property) xanh và `verifyNoApiKeyRelease` báo 0 match cả APK lẫn AAB; thêm thử `buildConfigField("String","ANTHROPIC_API_KEY", …)` vào `defaultConfig` thì release build đỏ (kiểm thủ công rồi hoàn nguyên); thiếu `skim.aiProxyUrl` thì release fail với thông báo rõ.
- `SkimTypographyTest`, `SkimThemeTest`, `BackupRulesTest` xanh.
- App cài trên emulator chạy, vẽ edge-to-edge, không crash khi xoay hoặc đổi dark mode hệ thống (Activity không bị tạo lại: log `onCreate` một lần).
- Release APK chỉ có 1 file dex sau khi bỏ `packageScope` (đối chiếu 1,34 MB so với 9,65 MB của probe; số đo thật ghi ở phase 13).
- Rule "Color" và phần "Edge to edge" (enableEdgeToEdge, adjustResize) của navigation-map.md được đáp ứng ở mức nền.

## Verification

```bash
git status --short && git log --oneline -3 && git branch --show-current   # phải là feat/skim-design-implementation
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests '*SkimTypographyTest*' --tests '*SkimThemeTest*' --tests '*BackupRulesTest*'
./gradlew :app:assembleRelease :app:bundleRelease -Pskim.aiProxyUrl=https://proxy.example.invalid -Pskim.termsUrl=https://example.invalid/terms -Pskim.privacyUrl=https://example.invalid/privacy -Pskim.aiDataPolicyUrl=https://example.invalid/ai-data
./gradlew :app:assembleRelease            # phải fail ở checkSkimReleaseConfig khi property rỗng
./gradlew :app:check -Pskim.aiProxyUrl=https://proxy.example.invalid -Pskim.termsUrl=https://example.invalid/terms -Pskim.privacyUrl=https://example.invalid/privacy -Pskim.aiDataPolicyUrl=https://example.invalid/ai-data
./gradlew :app:lintDebug
./gradlew --configuration-cache :app:assembleDebug   # lần 2 phải "Reusing configuration cache"
unzip -l app/build/outputs/apk/release/*.apk | grep -c 'classes.*\.dex'
```

Giá trị `.invalid` (RFC 2606) chỉ dùng trên dòng lệnh để kiểm tra build, không vào mã nguồn. Tên file APK release phụ thuộc cấu hình ký (scaffold chưa có `signingConfig`), nên lệnh dùng wildcard.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| KGP 2.4.21 chạy ổn với AGP 9.4.1 (ngoài bảng "fully supported") | Lỗi compile/plugin lạ | Lùi `kotlin` về 2.4.20 (spike đã build sạch) |
| material3 1.5.0-beta01 giữ API như spike | Lỗi unresolved khi build | Pin đúng beta01; không nâng beta khác trong plan này |
| Bỏ `packageScope` không làm vỡ reflection | Crash chỉ ở release | Smoke test release (instrumented, phase 13); thêm keep rule đúng chỗ (không khôi phục packageScope) |
| R8 thiếu class transitive khác | "Missing class" khi `assembleRelease` | Copy rule từ `build/outputs/mapping/release/missing_rules.txt` vào `rules.keep` |
| Jackson/OkHttp bị kéo hai version | `NoSuchMethodError`, lỗi version check Jackson | Pin version trong toml, kiểm tra bằng `:app:dependencies` |
| Variant API của AGP 9 nối được task kiểm vào APK/AAB `[UNVERIFIED: cách finalizedBy với SingleArtifact]` | Task không chạy sau `bundleRelease` | Cho `check` và bước release trong deployment guide gọi tường minh `verifyNoApiKeyRelease` |
| Robolectric SDK 37 chưa ổn | Crash khi tải android-all | Giữ `sdk=36` |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 1 (commit khởi tạo trên `master` giữ nguyên). Chưa có dữ liệu người dùng.

## Security considerations

- Key Anthropic chỉ ở `local.properties` (đã ignore), chỉ vào BuildConfig của debug; release là `null`; mọi variant không phải debug bị quét đúng giá trị key và prefix `sk-ant-`, cả APK lẫn AAB. Key debug riêng, có spend limit; không phân phối APK debug.
- URL proxy không nằm trong file đã commit; hợp đồng bảo mật tối thiểu của proxy ở `docs/deployment-guide.md` (phase 13) và chặn phát hành tới khi proxy đạt.
- Network security config cho phép cleartext vì trình duyệt phải mở trang `http://`; URL AI và URL pháp lý của release bị ép `https://` lúc build.
- Backup tắt và device-to-device transfer loại trừ mọi domain, nên lịch sử, bookmark, chat, cookie WebView và `installId` không rời máy.
- `FileProvider` không exported, chỉ lộ `cacheDir/shared/`.

## Next steps

- Phase 2 (component) và phase 3 (data) chạy song song được: không chung file.
- Câu hỏi mở cho chủ sản phẩm: URL Terms/Privacy/AI data policy, URL proxy release, icon launcher (thiết kế chưa có, giữ icon template).

## Kết quả thực thi (2026-10-21)

Commit: `build: set up compose build, theme, resources and release checks`. Kiểm chứng độc lập trên máy dev: `assembleDebug`, `testDebugUnitTest` (11 test), `assembleRelease` + `bundleRelease` (quét key: APK 141 entry, AAB 151 entry, 0 match), `check`, `lintDebug`, configuration cache tái sử dụng; smoke test trên `skim_api36`: edge-to-edge đúng màu `#F8F5FF`, `onCreate` đúng 1 lần sau khi xoay và đổi dark mode, không crash.

Khác spec (đã chấp nhận):
- Icon thiết kế tint bằng `?attr/colorControlNormal`; `values/themes.xml` khai báo attr này và trỏ về `?android:attr/colorControlNormal`.
- `checkSkimReleaseConfig` nối bằng `variant.lifecycleTasks.registerPreBuild`; release fail thêm khi `skim.aiProxyUrl` nằm trong `gradle.properties` đã commit.
- Thử `buildConfigField("ANTHROPIC_API_KEY", …)` trong `defaultConfig` không làm release đỏ vì field của buildType `release` (`null`) thắng; kịch bản rò thật (field ở `defaultConfig` mà release không đè, hoặc key debug lọt vào artifact) đã được thử và làm `verifyNoApiKey*` đỏ ở cả APK lẫn AAB.
- `ScreenshotDefaults.setSkimContent` chèn insets 24 dp bằng `ViewCompat.dispatchApplyWindowInsets` vào `LocalView` sau idle; không dùng `DeviceConfigurationOverride.WindowInsets` (tạo root thứ hai làm `onRoot()` hỏng).

Việc để các phase sau (không thuộc phase 1):
- Phase 11 và 13: `kotlin-reflect` resolve ra 1.9.25 trong khi stdlib là 2.4.21; kiểm bằng `ReleaseSmokeTest` với R8 đầy đủ trước khi quyết định pin.
- Phase 8: AboutLibraries lấy nội dung license SPDX qua mạng lúc build; kiểm màn license khi build offline.
- Phase 13: số dex/kích thước release hiện (1 dex, 3,28 MB) chưa phản ánh app thật vì R8 loại cả SDK; đo lại khi có code AI.
