# Nghiên cứu: build stack Android + Compose Material 3 Expressive cho Skim

Ngày 2026-10-20. Phạm vi: câu hỏi 1–10 trong brief. Đầu vào là `design/README.md`, `design/spec/{components,tokens,navigation-map}.md`, `design/compose/*.kt`, các skill `agp-9-upgrade`, `navigation-3`, `edge-to-edge`, `testing-setup`. Mọi version lấy từ `maven-metadata.xml`, không lấy theo trí nhớ. Signature API đọc thẳng từ sources jar. Ngoài ra có một spike biên dịch thật trong scratchpad, không đụng vào project.

**Spike đã chạy:** AGP 9.4.1, Gradle 9.6.0, compose/serialization plugin 2.4.20, KSP 2.3.12, Room 2.8.5, Hilt 2.60.1, BOM 2026.09.00 với material3 1.5.0-beta01, Nav3 1.2.0, Robolectric 4.17, Roborazzi 1.76.0, chạy trên JDK 25. Các bước đã chạy được gồm `compileDebugKotlin`, `assembleDebug`, `testDebugUnitTest` và một lượt record Roborazzi.

## 0. Kết luận chính

1. **Phải nâng Kotlin lên 2.4.21.** AGP 9.4.1 kéo theo KGP 2.2.10. Trình biên dịch 2.2 không đọc được metadata Kotlin 2.4, mà `kotlinx-serialization-json` 1.12.0 (stdlib 2.4.10) và `aboutlibraries-compose-m3` 15.3.0 (stdlib 2.4.21) đều cần nó. Cách nâng: khai báo plugin `org.jetbrains.kotlin.plugin.compose` (hoặc `plugin.serialization`) ở version 2.4.21 trong `plugins {}` gốc. POM của plugin này phụ thuộc `kotlin-gradle-plugin:2.4.21`, nên Gradle tự đẩy KGP lên. Spike xác nhận qua `buildEnvironment`: `kotlin-gradle-plugin:2.2.10 -> 2.4.20`, `symbol-processing-gradle-plugin:2.2.10-2.0.2 -> 2.3.12`.
2. **material3 1.5.0 vẫn là beta01 (2026-10-07), chưa có rc hay stable** tính đến 2026-10-20. Bản stable 1.4.0 đã gỡ hết API Expressive, nên không dùng được. Cách nhẹ nhất: BOM stable 2026.09.00 (Compose 1.12.1) rồi pin riêng `material3:1.5.0-beta01`. POM của nó chỉ cần Compose 1.12.0, nên ui/foundation không phải lên beta.
3. **Phần lớn API Expressive đã ra khỏi trạng thái experimental trong 1.5.0-beta01.** Chỉ còn cần opt-in ở: `LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes`, `toShape()`, `PullToRefreshDefaults.LoadingIndicator` (cần `ExperimentalMaterial3ExpressiveApi`), và `ModalBottomSheet`/`rememberBottomSheetState` (cần `ExperimentalMaterial3Api`). Có 2 snippet trong `components.md` dùng API đã deprecated (Slider stateless, `rememberModalBottomSheetState`).
4. **Lỗi trong `design/compose/Type.kt`:** `Font(R.font.google_sans_flex, FontWeight(it))` resolve vào overload truyền `FontVariation.Settings()` **rỗng**, nên trục `wght` không được set. Kết quả là mọi style 500/600 hiển thị ở weight 400. Đã chứng minh bằng unit test (`twoArg=[]`) và bằng ảnh render thật qua Robolectric. Sửa xem mục 7.
5. Đề xuất: **Navigation 3 1.2.0**, **DI thủ công bằng AppContainer** (Hilt là phương án 2, đã build OK trên AGP 9.4.1), **Room 2.8.5**, **Roborazzi** để so màn hình với PNG thiết kế. Robolectric trên JDK 25 bắt buộc có cờ `--add-opens`; thiếu thì crash, đã gặp trong spike.

## 1. AGP 9.4.1 + built-in Kotlin

- AGP 9 tự bật built-in Kotlin. **Không** áp dụng `org.jetbrains.kotlin.android` hay `kotlin-kapt`. Thay `android.kotlinOptions {}` bằng `kotlin { compilerOptions { … } }`. `jvmTarget` mặc định lấy theo `compileOptions.targetCompatibility` (skill `agp-9-upgrade`: `migrate-to-built-in-kotlin.md`). Thư mục source Kotlin bổ sung chỉ khai báo qua `android.sourceSets.kotlin`.
- POM AGP 9.4.1 phụ thuộc runtime `kotlin-gradle-plugin:2.2.10` và `symbol-processing-gradle-plugin:2.2.10-2.0.2` (https://dl.google.com/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1.pom). Ghi chú phát hành AGP 9 giải thích cơ chế "upgrade KGP/KSP qua classpath".
- AGP 9.4 yêu cầu tối thiểu Gradle 9.6.0, JDK 17, build-tools 36.0.0, hỗ trợ tối đa API 37 (https://developer.android.com/build/releases/agp-9-4-0-release-notes). Project hiện khớp các yêu cầu này.
- **Compose:** plugin `org.jetbrains.kotlin.plugin.compose` phải cùng version với Kotlin, cộng `buildFeatures { compose = true }` (https://developer.android.com/develop/ui/compose/compiler). Trang này cũng ghi "latest Compose BOM: 2026.09.00". Ví dụ trên trang vẫn dùng `kotlin-android`, nên bỏ dòng đó khi dùng AGP 9.
- **Serialization:** `org.jetbrains.kotlin.plugin.serialization` cùng version Kotlin.
- **KSP2:** từ 2.3.0, KSP tách khỏi version Kotlin và "không còn là compiler plugin" (https://github.com/google/ksp/releases/tag/2.3.0). Bản 2.3.10 sửa lỗi R-class khi dùng built-in Kotlin của AGP 9 và lỗi tên module của Kotlin 2.4 (https://github.com/google/ksp/releases/tag/2.3.10). Skill AGP 9 yêu cầu KSP ≥ 2.3.6 và Hilt ≥ 2.59.2. Spike: Room 2.8.5 và Hilt 2.60.1 sinh code qua KSP và `assembleDebug` thành công.
- **Configuration cache** (đang bật trong `gradle.properties`): spike lưu được cache entry với đủ bộ plugin trên, problems report chỉ có cảnh báo deprecation của Kotlin.
- **Bảng tương thích KGP 2.4.21:** "fully supported" tới AGP 9.3.1 / Gradle 9.7.0 (https://kotlinlang.org/docs/gradle-configure-project.html, biến lấy từ `docs/v.list`). AGP 9.4.1 nằm ngoài khoảng đó. Tuy nhiên bản thân AGP 9.4.1 mặc định dùng KGP 2.2.10, mà bảng ghi 2.2.10 chỉ hỗ trợ tới AGP 8.10. Nghĩa là bảng này bảo thủ. Spike 2.4.20 build sạch. Mức rủi ro: thấp, nhưng nên ghi nhận.
- **Theme XML hiện tại** (`Theme.MaterialComponents.DayNight.DarkActionBar` và `android:statusBarColor`) không hợp với app Compose edge-to-edge. Đổi parent sang `android:Theme.Material.Light.NoActionBar` rồi bỏ dependency `appcompat` và `material` (Views). Điều này phù hợp KISS và là quyết định của plan.

## 2. Đề xuất `gradle/libs.versions.toml` (dán thẳng được)

```toml
# Nguồn version (đọc <base>/<group path>/<artifact>/maven-metadata.xml ngày 2026-10-20):
#   GM  = https://dl.google.com/android/maven2   MC = https://repo1.maven.org/maven2
#   GPP = https://plugins.gradle.org/m2
[versions]
agp = "9.4.1"                  # GM com/android/tools/build/gradle (9.5.0 mới alpha10)
kotlin = "2.4.21"              # MC org/jetbrains/kotlin/kotlin-gradle-plugin (2026-10-08; 2.5.0 mới Beta1)
ksp = "2.3.12"                 # MC com/google/devtools/ksp/symbol-processing-gradle-plugin (2026-09-09)
composeBom = "2026.09.00"      # GM androidx/compose/compose-bom -> Compose 1.12.1, material3 1.4.0
material3 = "1.5.0-beta01"     # GM androidx/compose/material3/material3 (2026-10-07; chưa có rc/stable)
coreKtx = "1.19.1"             # GM androidx/core/core-ktx
activityCompose = "1.13.0"     # GM androidx/activity/activity-compose
lifecycle = "2.11.0"           # GM androidx/lifecycle/lifecycle-viewmodel-compose (+ -navigation3)
navigation3 = "1.2.0"          # GM androidx/navigation3/navigation3-runtime (stable 2026-09-23)
webkit = "1.17.1"              # GM androidx/webkit/webkit (design: ProfileStore/WebViewCompat.setProfile)
room = "2.8.5"                 # GM androidx/room/room-runtime (2026-09-09)
datastore = "1.2.1"            # GM androidx/datastore/datastore-preferences (1.3.0 mới rc01)
coroutines = "1.11.0"          # MC org/jetbrains/kotlinx/kotlinx-coroutines-core (2026-05-08)
serializationJson = "1.12.0"   # MC org/jetbrains/kotlinx/kotlinx-serialization-json (2026-10-19, build trên Kotlin 2.4.10)
aboutLibraries = "15.3.0"      # GPP com/mikepenz/aboutlibraries/plugin/... + MC com/mikepenz/aboutlibraries-compose-m3
junit = "4.13.2"               # MC junit/junit
junitVersion = "1.3.0"         # GM androidx/test/ext/junit
androidxTestRunner = "1.7.0"   # GM androidx/test/runner
espressoCore = "3.7.0"         # GM androidx/test/espresso/espresso-core
robolectric = "4.17"           # MC org/robolectric/robolectric (hỗ trợ SDK 37)
roborazzi = "1.76.0"           # MC io/github/takahirom/roborazzi/roborazzi (AGP 9 từ 1.56.0)
turbine = "1.2.1"              # MC app/cash/turbine/turbine

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" } # override BOM
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
aboutlibraries-compose-m3 = { group = "com.mikepenz", name = "aboutlibraries-compose-m3", version.ref = "aboutLibraries" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
roborazzi = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { group = "io.github.takahirom.roborazzi", name = "roborazzi-compose", version.ref = "roborazzi" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
aboutlibraries = { id = "com.mikepenz.aboutlibraries.plugin.android", version.ref = "aboutLibraries" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

Đã gỡ `appcompat` và `material` (Views). Nếu chọn Hilt thì thêm: `hilt = "2.60.1"` (MC com/google/dagger/hilt-android), `hiltLifecycleViewmodelCompose = "1.4.0"` (GM androidx/hilt/hilt-lifecycle-viewmodel-compose), cùng plugin `com.google.dagger.hilt.android`.

## 3. Khối plugin

Root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false        // kéo KGP 2.2.10 -> 2.4.21 trên classpath
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false                   // 2.2.10-2.0.2 -> 2.3.12
    alias(libs.plugins.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
}
```

`app/build.gradle.kts` (chỉ ghi phần thêm vào; giữ nguyên namespace, SDK, `optimization`):

```kotlin
plugins {
    alias(libs.plugins.android.application)   // built-in Kotlin, KHÔNG thêm kotlin-android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.roborazzi)
}
android {
    buildFeatures { compose = true }
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all { it.jvmArgs( // Robolectric trên JDK 17+ (daemon JVM của project là 25), nguồn: robolectric.org/getting-started
            "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED") }
    }
}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview); debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose); implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime); implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit); implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime); ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.compose.m3)
    testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines.test); testImplementation(libs.turbine)
    testImplementation(libs.robolectric); testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4); testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom)); androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core); androidTestImplementation(libs.androidx.room.testing)
}
```

`room-runtime` 2.8.5 đã gồm hỗ trợ DAO `suspend` (spike build được mà không cần `room-ktx`).

## 4. Bảng API (material3 1.5.0-beta01; đọc từ sources jar, compile qua spike)

| API (package `androidx.compose.material3` trừ khi ghi khác) | Có? | Opt-in | Tham số chính / lệch so với `components.md` |
|---|---|---|---|
| `MaterialExpressiveTheme(colorScheme?, motionScheme?, shapes?, typography?, content)` | Có | Không | Tham số nullable. Bản 1.4.0 không có. |
| `MotionScheme.expressive()` | Có | Không | Đã ra khỏi experimental ("Graduate motion scheme"). |
| `Typography(... displaySmallEmphasized … labelSmallEmphasized)` và `MaterialTheme.typography.*Emphasized` | Có | Không | Public từ 1.5. Ở 1.4.0 là `internal`. `@OptIn` trong Type.kt/Theme.kt thừa nhưng vô hại. |
| `HorizontalFloatingToolbar(expanded, floatingActionButton, modifier, colors, contentPadding, scrollBehavior, shape, floatingActionButtonPosition, animationSpec, …, content)` | Có | Không | Chiều cao 64 và khoảng cách FAB 8 khớp thiết kế. `FloatingToolbarDefaults.ScreenOffset` = **16dp**, thiết kế cần 24, nên tự đặt padding. Component **không** tự xử lý insets. |
| `FloatingToolbarDefaults.StandardFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, interactionSource, content)` / `standardFloatingToolbarColors(toolbarContainerColor, toolbarContentColor, fabContainerColor, fabContentColor)` | Có | Không | Không có tham số `enabled` (trạng thái disabled phải tự vẽ, đúng như ghi chú thiết kế). FAB 56, shape mặc định FAB 16. |
| `SegmentedListItem` (4 overload: tĩnh / `onClick` / `selected` / `checked`) với `shapes: ListItemShapes` (bắt buộc), slot `content` = headline | Có | Không | Bản 1.4.0 không có. |
| `ListItemDefaults.segmentedShapes(index, count, defaultShapes)` / `segmentedColors(containerColor=…)` / `SegmentedGap` | Có | Không | Gap 2dp khớp. Góc ngoài = `shapes.large` (**16dp**), góc trong 4dp; thiết kế cần ngoài **20**, nên phải tự dựng `ListItemShapes`. Màu container mặc định là `surface`, thiết kế dùng `surfaceContainerLowest`. |
| `DropdownMenuPopup(expanded, onDismissRequest, modifier, popupPositionProvider, properties, content)` + `DropdownMenuGroup(shapes: MenuGroupShapes, modifier, containerColor, …, contentPadding, content)` | Có | Không | Dùng `MenuDefaults.groupShape(i, n)`, `MenuDefaults.itemShape(i, n).shape`, `GroupSpacing` = 2dp. Snippet `DropdownMenuItem(text, onClick)` sẽ bind vào overload **cũ**, không có shape theo nhóm. Phải dùng overload có `shape:`. Bản 1.4.0 không có. |
| `SearchBar(state, inputField, modifier, shape, colors, …)` + `rememberSearchBarState()` + `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch, …)` + `ExpandedFullScreenContainedSearchBar(state, inputField, …, windowInsets, properties, content)` | Có | Không | Các bản cũ `SearchBar(inputField, expanded, onExpandedChange)` và `InputField(…expanded…)` đã deprecated. `fullScreenWindowInsets` = `safeDrawing`. |
| `LargeFlexibleTopAppBar(title, modifier, subtitle, navigationIcon, actions, titleHorizontalAlignment, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)` + `TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state, canScroll, …)` | Có | Không | Tự xử lý insets trên. Style title `headlineLargeEmphasized` truyền qua `Text(style=)`. |
| `ToggleButton(checked, onCheckedChange, modifier, buttonSize, enabled, icon, shapes, colors, …, content)` + `ButtonGroupDefaults.connected{Leading,Middle,Trailing}ButtonShapes(shape, pressedShape, checkedShape)` + `ConnectedSpaceBetween` | Có | Không | Gap 2dp, cao 40, khớp. Có slot `icon` mới, dùng cho dấu check. |
| `FilledTonalIconToggleButton(checked, onCheckedChange, modifier, enabled, shape, colors, interactionSource, content)` (+ overload `shapes: IconToggleButtonShapes`) | Có | Không | Overload `shapes` cho hiệu ứng morph tròn sang vuông bo góc như ở 08b. |
| `ButtonDefaults.ExtraSmallContainerHeight` (32), `MinHeight` (40), `MediumContainerHeight` (56), `contentPaddingFor(h)`, `textStyleFor(h)`, `iconSizeFor(h)`, `shapesFor(h)` | Có | Không | Khớp XS/S/M của thiết kế. |
| `ContainedLoadingIndicator(modifier, containerColor, indicatorColor, containerShape, polygons)` (+ bản có `progress`) | Có | **ExpressiveApi** | Việc đưa lên stable đã bị revert ("Revert MaterialShapes and LoadingIndicator promotions"). |
| `LinearWavyProgressIndicator(progress, …, gapSize, stopSize, amplitude: (Float)->Float, wavelength, waveSpeed)` và bản indeterminate (`amplitude: Float` 0..1) | Có | Không | Mặc định: stroke 4, biên độ 3, bước sóng 40 khớp. **Gap mặc định 4dp**, thiết kế cần 8, nên truyền `gapSize = 8.dp`. |
| `MaterialShapes.{Sunny, Cookie4Sided, Cookie9Sided, Cookie12Sided, Clover4Leaf, Clover8Leaf, SoftBurst, Flower, Pentagon}` + `RoundedPolygon.toShape(startAngle)` (@Composable) | Đủ 9 | **ExpressiveApi** | Kéo theo `androidx.graphics:graphics-shapes` 1.0.1. |
| `pulltorefresh.PullToRefreshBox(isRefreshing, onRefresh, modifier, state, contentAlignment, indicator, enabled, threshold, content)` / `PullToRefreshDefaults.LoadingIndicator(state, isRefreshing, modifier, …)` | Có | Box: không. LoadingIndicator: **ExpressiveApi** | |
| `ModalBottomSheet(onDismissRequest, modifier, sheetState, sheetMaxWidth, sheetGesturesEnabled, shape, containerColor, contentColor, tonalElevation, scrimColor, dragHandle, contentWindowInsets, properties, content)` | Có | **M3Api** | `rememberModalBottomSheetState` đã **deprecated**; dùng `rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues, confirmValueChange)` (cũng cần M3Api). Inset mặc định là `safeDrawing` dọc, đã gồm IME. |
| `Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, …)` | Có | Không | |
| `Slider` | Có | Không | `Slider(value, onValueChange, valueRange, steps)` đã **deprecated**; dùng `Slider(state = rememberSliderState(value, steps = 11, trackRange = 80f..200f), onValueChange)`. `steps = 11` đúng (13 giá trị). |
| `Badge(modifier, containerColor, contentColor, content)` | Có | Không | |

Nguồn: release notes https://developer.android.com/jetpack/androidx/releases/compose-material3 (1.5.0-beta01, alpha29 "Deprecated stateless Slider", các mục "Graduate … FloatingToolbar / ButtonGroup / ToggleButtons / LargeFlexibleTopAppBar / ExpandedFullScreenContainedSearchBar", "Promoted SearchBarState … stable"; ghi chú 1.4.0 "All public APIs tagged with ExperimentalMaterial3ExpressiveApi … have been removed"). Đã đối chiếu thêm với sources của 1.4.0: không có HorizontalFloatingToolbar, SegmentedListItem, DropdownMenuGroup, ToggleButton, LoadingIndicator, MaterialShapes. Test Compose: `androidx.compose.ui.test.junit4.createComposeRule` đã deprecated trong 1.12, dùng `…junit4.v2.createComposeRule` (StandardTestDispatcher).

## 5. Navigation: chọn Navigation 3 1.2.0

| Tiêu chí | Navigation 3 1.2.0 (stable 2026-09-23) | Navigation Compose 2.10.2 |
|---|---|---|
| Sheet M3 làm destination | `OverlayScene`: copy recipe `BottomSheetSceneStrategy` (~60 dòng) | Không có artifact M3. `material-navigation` chỉ cho M2 |
| Dialog | `DialogSceneStrategy` có sẵn | `dialog<Route>` có sẵn |
| "Restore open sheets" sau process death (navigation-map.md) | Có ngay: sheet là `NavKey` nằm trong back stack được lưu | Sheet là state cục bộ, phải tự lưu |
| ViewModel theo từng entry, SavedStateHandle | `rememberViewModelStoreNavEntryDecorator` (lifecycle 2.11.0) cộng decorator saveable, có cung cấp `SavedStateRegistryOwner` | Có sẵn |
| Back stack | App sở hữu `SnapshotStateList`, dễ test | Controller nội bộ |
| Mức trưởng thành | 1.0 từ 2025. Có results API, deep link, predictive back | Rất trưởng thành |

**Mô hình đề xuất:** `sealed interface SkimKey : NavKey`, mỗi key là `@Serializable`. Có ba loại:
- Màn hình đầy đủ: Welcome, Setup, Home, Browsing(tabId), Reader(tabId), Tabs(incognito), Settings, AiPrivacy, History, Bookmarks, SkimHistory.
- Sheet (`metadata = BottomSheetSceneStrategy.bottomSheet()`): MoreMenu, LinkOptions, AskPage, AskHome, ReaderText.
- Dialog (`DialogSceneStrategy.dialog()`): 06d và các dialog ở board 15.

Menu, snackbar, Find-in-page (05c) và SearchBar mở rộng (04b) để dạng UI state. Cấu hình `NavDisplay(backStack, sceneStrategies = listOf(DialogSceneStrategy(), BottomSheetSceneStrategy()), entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()))`. Thứ tự decorator như trên, và spike đã compile cấu hình này. SinglePane được dùng làm fallback tự động (`calculateSceneWithSinglePaneFallback`).

Về lưu trạng thái: overload Android `rememberNavBackStack(vararg)` dùng reflection. Overload `rememberNavBackStack(SavedStateConfiguration, …)` yêu cầu `SerializersModule` có `polymorphic(NavKey::class) { subclass(…) }`, và an toàn với R8. Nên dùng overload này, vì release đang bật `optimization`. Nguồn: https://developer.android.com/jetpack/androidx/releases/navigation3, recipe trong skill `navigation-3` (`bottomsheet.md`), sources của navigation3-runtime-android 1.2.0.

## 6. DI: đề xuất AppContainer thủ công

| | AppContainer thủ công | Hilt 2.60.1 | Koin 4.2.2 |
|---|---|---|---|
| Rủi ro build trên AGP 9.4.1 | Không có | Đã build OK trong spike (plugin, KSP, `hiltJavaCompile`, `assembleDebug`). Hỗ trợ AGP 9 từ 2.59, các bản 2.59.1/2.59.2/2.60.1 đều là bản vá regression (https://github.com/google/dagger/releases) | Không có plugin. Lỗi wiring chỉ lộ lúc runtime |
| Boilerplate | `viewModel { VM(container.x, createSavedStateHandle()) }` cho mỗi VM | Thấp. `hiltViewModel()` (androidx.hilt 1.4.0) chạy được với decorator của Nav3 | Thấp |
| Thay fake khi test | Dựng UI không trạng thái với fake, hoặc thay container | `@TestInstallIn` | Override module |
| Thời gian build | Không đổi | Thêm KSP và bước aggregate | Không đổi |

Skim chỉ có một module, khoảng 10–15 ViewModel, cộng các singleton sống ngoài composition (TabManager giữ WebView, DB, DataStore, AI client). Container thủ công là cách đơn giản và chắc chắn nhất. Nên chuyển sang Hilt khi tách nhiều module hoặc khi số binding cần thay trong test tăng mạnh; lúc đó đã có bằng chứng Hilt build được. Koin xếp thứ 3 vì mất an toàn compile-time.

## 7. Persistence, lifecycle, coroutines, serialization

- **Room 2.8.5** dùng KSP, export schema qua plugin `androidx.room` (đã spike). Room 3.0.3 (`androidx.room3`, stable 2026-07-01, https://developer.android.com/jetpack/androidx/releases/room3) hướng tới KMP, chỉ có API coroutine, có FTS5. App chỉ chạy Android nên chưa cần. Cân nhắc lại nếu tìm kiếm History/Bookmarks cần FTS5.
- **DataStore Preferences 1.2.1** cho Settings (Theme, search engine, các công tắc AI).
- **lifecycle 2.11.0**: `lifecycle-viewmodel-compose` (`viewModel { }`, `createSavedStateHandle()`), `lifecycle-runtime-compose` (`collectAsStateWithLifecycle`), `lifecycle-viewmodel-navigation3`.
- **activity-compose 1.13.0**, **coroutines 1.11.0** (stdlib 2.2.20). **serialization-json 1.12.0** ra ngày 2026-10-19, build trên Kotlin 2.4.10 (https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0), nên chỉ dùng được khi Kotlin ≥ 2.3; spike đã compile được. Nếu muốn bản đã chạy lâu ngày hơn thì dùng 1.11.0.

## 8. Edge-to-edge (targetSdk 37)

- Gọi `enableEdgeToEdge()` trước `setContent`, đặt `android:windowSoftInputMode="adjustResize"`. Theo skill `edge-to-edge`, edge-to-edge đã bắt buộc từ targetSdk 35. Thay đổi riêng của targetSdk 37 không gồm edge-to-edge. Có hai thay đổi đáng chú ý: không thể opt-out việc bỏ qua khóa orientation/resizability khi sw ≥ 600dp, và quyền `ACCESS_LOCAL_NETWORK` (https://developer.android.com/about/versions/17/behavior-changes-17).
- **Floating toolbar:** đặt trong `Box(Modifier.fillMaxSize())`, căn `BottomCenter`, dùng `Modifier.navigationBarsPadding().padding(bottom = 24.dp)` (không dùng `ScreenOffset` vì nó là 16). Trang web vẽ chạy xuống dưới toolbar. Snackbar cần `bottom = navBars + 24 + 64 + 8`.
- **IME:** `ModalBottomSheet` đã cộng IME qua `modalWindowInsets` (`safeDrawing` dọc), nên ô chat ở 06/06b **không** thêm `imePadding()`, nếu thêm sẽ đệm gấp đôi. Ô nhập đáy nằm ngoài sheet thì dùng `Modifier.fitInside(WindowInsetsRulers.Ime.current)` (đã kiểm tra có trong Compose 1.12.1) hoặc `imePadding()`.
- **Biểu tượng status bar khi incognito ép tối:** `SystemBarStyle.auto` mặc định đọc `uiMode` của **hệ thống**, không đọc theme của app (mã nguồn activity 1.13.0 `EdgeToEdge.kt`). Khi `dark` của app khác hệ thống (incognito, hoặc Theme Dark/Light ở 12), phải gọi lại `enableEdgeToEdge(SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark }, SystemBarStyle.auto(lightScrim, darkScrim) { dark })` mỗi khi `dark` đổi. Đây đúng là cách Now in Android làm (https://github.com/android/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/MainActivity.kt).

## 9. Font biến thiên (Google Sans Flex)

`Font(resId, weight)` gọi tới overload `Font(resId, weight, style, loadingStrategy)`. Overload này truyền `FontVariation.Settings()` **rỗng**. Overload có `variationSettings = FontVariation.Settings(weight, style)` chỉ được chọn khi truyền tham số đó tường minh. Kotlin ưu tiên ứng viên dùng ít giá trị mặc định hơn. Khi settings rỗng, `TypefaceCompatApi26` trả về typeface nguyên trạng (ui-text 1.12.1, `Font.kt` và `PlatformTypefaces.android.kt`). Kết quả là typeface ở instance mặc định (400), và Compose không tổng hợp đậm vì weight khai báo đã khớp. Bằng chứng: unit test cho `twoArg=[]`, `explicit=[wght=500, ital=0]`; ảnh Roborazzi cho thấy 400/500/600 giống hệt nhau, còn bản đã sửa đậm dần đúng. Cách sửa trong Type.kt (API 26+, không cần opt-in):

```kotlin
Font(R.font.google_sans_flex, FontWeight(it),
     variationSettings = FontVariation.Settings(FontWeight(it), FontStyle.Normal))
```

## 10. Giấy phép mã nguồn mở: AboutLibraries 15.3.0

- Plugin `com.mikepenz.aboutlibraries.plugin.android` 15.3.0 (Gradle Plugin Portal) tự gắn vào build Android và sinh ra `R.raw.aboutlibraries`. UI dùng `aboutlibraries-compose-m3` 15.3.0 (Maven Central): `val libraries by produceLibraries(R.raw.aboutlibraries)` rồi `LibrariesContainer(libraries)`. README ghi "Compose 1.12.x | AGP 9 | Kotlin 2.4 | Compile 37" ứng với 15.3.0. Hỗ trợ AGP 9 có từ 14.0.0 (https://github.com/mikepenz/AboutLibraries).
- **Bắt buộc**, vì release đang bật `optimization` (resource shrinking tối ưu): thêm file keep `tools:keep="@raw/aboutlibraries"`, nếu không file JSON sẽ bị xoá (README, mục "Optimized Resource Shrinking / AGP 9.0").
- Google Sans Flex (OFL 1.1) và bộ icon Material Symbols (Apache 2.0) không phải dependency Maven, nên phải thêm định nghĩa thư viện tuỳ chỉnh qua `aboutLibraries { collect { configPath = … } }`. Phần này chưa spike.

## 11. Testing

| Lớp test | Công cụ | Trạng thái kiểm chứng |
|---|---|---|
| ViewModel, repo | JUnit 4.13.2, coroutines-test 1.11.0, Turbine 1.2.1 (2025-06, ít thay đổi) | Theo metadata |
| UI behavior local | Robolectric 4.17 (hỗ trợ SDK 37), `v2.createComposeRule`, `isIncludeAndroidResources`, cờ `--add-opens` | Spike: thiếu cờ thì crash `IllegalAccessException jdk.internal.access` trên JDK 25; có cờ thì chạy |
| Screenshot so với thiết kế | **Roborazzi 1.76.0** với `@GraphicsMode(NATIVE)` và `@Config(qualifiers = "w412dp-h915dp-xhdpi")` | Spike: ảnh ra **824×1830**, đúng bằng kích thước PNG trong `design/screens` |
| Instrumented | androidx.test runner 1.7.0, ext-junit 1.3.0, espresso 3.7.0, compose ui-test-junit4; DAO test với Room in-memory | Theo metadata |

Xếp hạng công cụ screenshot:
1. **Roborazzi.** Ổn định (1.x), hỗ trợ AGP 9 từ 1.56.0. Chụp được toàn màn hình kèm fake. Chế độ compare so ảnh hiện tại với file có sẵn ở cùng đường dẫn và sinh `_compare.png`, nên có thể đặt PNG thiết kế vào đúng đường dẫn đó để ra ảnh diff.
2. **Compose Preview Screenshot Testing 0.0.1-alpha16** (https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes). Vẫn alpha, cần cờ experimental, chỉ chụp từ `@Preview`, tên file reference bị băm, nên khó so với PNG bên ngoài.

Lưu ý khi so ảnh:
- (a) `SkimTheme` luôn bật dynamic color từ API 31. Spike thấy Robolectric dựng palette động khác hẳn brand, nên cần thêm tham số `dynamicColor` để test truyền `false`.
- (b) Robolectric không vẽ status bar, mà thiết kế dành 24dp trên cùng cho status bar, nên phải bù offset hoặc crop.
- (c) Diff pixel sẽ không bao giờ bằng 0, vì khác biệt raster của font và WebView. Dùng `changeThreshold` và người xem duyệt lại.

## 12. Thư viện UI khác mà thiết kế ngụ ý

- **androidx.webkit 1.17.1:** cần cho `ProfileStore`/`WebViewCompat.setProfile` (incognito).
- **Coil: không cần.** Favicon lấy từ bitmap của WebView (`onReceivedIcon`), preview tab là bitmap chụp từ WebView, 05d Image dùng ô icon (đã xem PNG), Home dùng ô chữ cái.
- **core-splashscreen: không cần.** Không có board splash. Android 12+ tự hiện splash hệ thống.
- Icon dùng vector drawable có sẵn, không cần `material-icons`.

## 13. Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| material3 1.5.0 còn beta, API có thể đổi trước stable (beta01 vừa đổi PolygonShape/Slider) | Trung bình | Pin `1.5.0-beta01`, theo dõi rc. Cô lập các chỗ opt-in |
| KGP 2.4.21 nằm ngoài bảng "fully supported" (tối đa AGP 9.3.1) | Thấp | Spike với 2.4.20 build sạch. Nếu hỏng có thể lùi về 2.4.20 |
| Lỗi weight trong Type.kt | Cao (sai thị giác toàn app) | Sửa theo mục 9 trước khi dựng UI |
| Nav3: key restore bằng reflection khi R8 bật | Trung bình | Dùng overload `SavedStateConfiguration` và test `am kill` trên bản release |
| Robolectric/RNG trên JDK 25, SDK 37 | Thấp–TB | Cờ add-opens. Spike chạy SDK 36, jar android-all của SDK 37 chưa tải |
| Tách khỏi khu vực UI: `ACCESS_LOCAL_NETWORK` (targetSdk 37) có thể chặn duyệt trang LAN (router) | Chưa rõ | Kiểm tra riêng |

## Câu hỏi chưa giải quyết

1. Thiết kế muốn ngưỡng 24 tính từ cạnh màn hình hay từ trên thanh điều hướng? Code mẫu ở trên giả định "trên nav bar", như brief ghi.
2. Phần trang web nằm dưới toolbar, khi cuộn tới cuối có bị che không? Có cần ẩn toolbar khi cuộn (`FloatingToolbarDefaults.exitAlwaysScrollBehavior`) hay dùng safe-area của WebView không? Thiết kế không nói.
3. Spike không cover: runtime Robolectric SDK 37, plugin AboutLibraries chạy thực tế (keep resource và định nghĩa tuỳ chỉnh), Room 3, render trên thiết bị thật.
4. `optimization.packageScope = androidx/kotlin/kotlinx` trong build hiện tại: nếu package của app nằm ngoài scope này thì rủi ro reflection của Nav3 giảm. Chưa kiểm tra semantics của DSL này.
5. Quyết định thuộc về chủ sản phẩm: chọn DI thủ công hay Hilt ngay từ đầu (nếu đã định tách module sớm thì nên chọn Hilt).
