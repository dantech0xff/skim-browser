---
phase: 13
title: "Verification, hardening & docs"
status: done-with-concerns
priority: P1
effort: "22h"
dependencies: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
---

# Phase 13: Verification, hardening & docs

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §11 (Roborazzi `w412dp-h915dp-xhdpi` = 824×1830, lưu ý dynamic color, status bar, ngưỡng diff), §13 (rủi ro Nav3 + R8).
- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` bảng rủi ro (reflection chỉ hỏng ở release, size, memory), mục "Giới hạn".
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-scope-complexity-critic.md` Finding 1, 7; `red-team-assumption-destroyer.md` Finding 10; `red-team-failure-mode-analyst.md` Finding 1, 10; `red-team-security-adversary.md` Finding 1, 7.
- Scout `plans/reports/scout-261020-2319-design-inventory.md` §3(b) (danh sách cd), §3(e) (19 mâu thuẫn), §4 (icon).
- `plans/261020-1611-skim-design-implementation/coverage-matrix.md` (mọi board/row/overlay → phase); mục "Cổng thiết bị" trong `plan.md`.
- `design/README.md` (touch target, cd, dynamic color), `design/spec/tokens.md` (Remove animations), toàn bộ `design/screens/*.png`.

## Overview

Gom và chốt bằng chứng phát hành: tổng hợp parity (golden từng phase đã duyệt + task `designParity` tạo ảnh diff để duyệt tổng), lượt TalkBack và kiểm phủ content description, kiểm khôi phục sau process death (tự động với pool rỗng + checklist adb), build release R8 + smoke test instrumented trên bản đã minify với MockWebServer chạy ngay trên thiết bị, kiểm không lọt key cho mọi variant không phải debug, lint, kiểm bộ nhớ WebView/trim, FLAG_SECURE trên emulator, cổng thiết bị cuối trên `skim_api36` và `skim_api28`; rồi viết README và bộ `docs/` (gồm hợp đồng bảo mật tối thiểu của proxy, điều kiện chặn phát hành).

## Key insights

<!-- Red Team: Cổng kiểm chứng — parity theo phase — 2026-10-21 -->
- Golden Roborazzi của từng phase, đã được người duyệt so với PNG, là kiểm tra parity; lệch đã được ghi ở từng phase vào `docs/design-guidelines.md`. Phase 13 không dựng bộ fixture thứ hai. Task `designParity` tách khỏi `verifyRoborazziDebug`: chỉ ghép ảnh board (`app/src/test/screenshots/boards/<tên>.png`, trùng tên PNG thiết kế) với `design/screens/<tên>.png` và sinh ảnh diff + tỉ lệ khác biệt để duyệt tổng; không làm đỏ build.
- Robolectric không vẽ WebView: vùng trang mẫu (05–06d) được bỏ qua khi duyệt. Diff pixel không bao giờ bằng 0 (raster font), quyết định khớp là của người duyệt.
<!-- Red Team: Smoke test release trên bản minify, bỏ server Python — 2026-10-21 -->
- Lỗi reflection/R8 chỉ lộ ở release: smoke test là test instrumented chạy trên build type `releaseSmoke` (minify như release, ký debug, `AI_BASE_URL = http://127.0.0.1:8787`), dùng MockWebServer chạy ngay trong process test trên thiết bị và chính các fixture `.sse` của phase 11/12. Test điều khiển qua UI (tìm theo text), nên không phụ thuộc tên lớp đã bị đổi.
<!-- Red Team: Skip không phải pass — 2026-10-21 -->
- Kết quả instrumented phải ghi `MULTI_PROFILE`/`HTTP_CACHE_MANAGER` của thiết bị và số test incognito thực sự chạy. Trên `skim_api36`, test incognito bị skip nghĩa là cổng **không đạt**.
- `adb shell am kill` không giết được process từ chính instrumentation của nó; khôi phục được kiểm tự động bằng cách xoá pool và tạo lại Activity (`ProcessDeathRestoreTest`), cộng `RestoreAcrossContainersTest` (container dựng lại trên cùng thư mục tạm), cộng checklist adb thủ công.
<!-- Red Team: Proxy relay mở — chặn phát hành — 2026-10-21 -->
- Proxy release nằm ngoài phạm vi xây dựng, nhưng **phát hành bị chặn** tới khi proxy đạt hợp đồng bảo mật tối thiểu ghi trong `docs/deployment-guide.md`.

## Requirements

Chức năng (kiểm tra):
- Duyệt tổng parity cho: 01, 03, 04, 04b, 04c, 05 (Loaded, Loading), 05b, 05c, 05d (Link, Image), 06, 06b, 06c (Generating, Not enough text, No connection), 06d, 08, 08b, 09, 09b, 09c, 10, 11 (Unreachable, No internet, Timed out), 12, 13, 14; overlay của board 15 so bằng mắt với vùng tương ứng trên `15-dialogs-menus-snackbars.png`. Mỗi board có ảnh diff từ `designParity`.
- A11y: mọi screenshot test đã gọi `assertA11y()` (phase 2–12); phase này kiểm mọi `cd_*` trong `strings.xml` đều được dùng trong code, và chạy một lượt TalkBack thủ công cho Main path (01 → 03 → 04 → 05 → 06d → 06) trên `skim_api36`.
- Reduced motion: ảnh tĩnh đã có ở golden phase 2/7/11; phase này duyệt chung.
- Process death: tab (danh sách, thứ tự, nhóm, tab hiện tại), vị trí cuộn của trang và Reader, sheet đang mở (05b, 05d, 06, 06b), chat của tab (Keep chat history bật); key của tab incognito bị lọc; 06/06b khôi phục không gửi request; tab incognito không khôi phục (D2).
- Release: `assembleRelease` và `bundleRelease` xanh với D1; `verifyNoApiKey<Variant>` 0 match cho mọi variant không phải debug (kể cả `releaseSmoke`); smoke test `releaseSmoke` xanh; ghi kích thước APK và số dex.
- Lint không có error; warning được xử lý hoặc ghi lý do (không dùng baseline để giấu).
- Bộ nhớ: tối đa 3 WebView sống với 10 tab; trim mức critical hạ về 1; `BACKGROUND` chỉ lưu state.
- FLAG_SECURE: chụp màn hình bị chặn, ảnh Recents trống khi đang ở tab incognito/09b và dưới sheet 05b/05d của tab incognito.
- Tài liệu: `README.md`, `docs/project-overview-pdr.md`, `docs/codebase-summary.md`, `docs/code-standards.md`, `docs/system-architecture.md`, `docs/design-guidelines.md` (hoàn thiện; mục "Known deviations" đã có từ phase 2), `docs/deployment-guide.md`, `docs/project-roadmap.md` — ngắn, dựa trên bằng chứng.

Phi chức năng:
- Không thêm fixture vào mã sản phẩm; mọi dữ liệu mẫu chỉ ở `app/src/test` hoặc `app/src/androidTest`.
- Không giấu test đỏ, không hạ ngưỡng để qua.

## Architecture

`designParity` (trong `app/build.gradle.kts`): task có `@InputDirectory` = `app/src/test/screenshots/boards`, `@InputDirectory` = `design/screens`, `@OutputDirectory` = `app/build/design-parity`; với mỗi cặp cùng tên, đọc hai PNG bằng `javax.imageio.ImageIO`, sinh `<tên>_diff.png` (pixel khác tô đỏ) và dòng `<tên>: <tỉ lệ pixel khác>%` vào `summary.txt`. Không gắn vào `check`.

Smoke test release:
- Build type `releaseSmoke` (`initWith(release)`, `signingConfig = debug`, `matchingFallbacks += "release"`, `AI_BASE_URL = "http://127.0.0.1:8787"`, `ANTHROPIC_API_KEY = null`); `android.testBuildType = providers.gradleProperty("skim.testBuildType").getOrElse("debug")`.
- Fixture SSE dùng chung: chuyển `app/src/test/resources/sse/` sang `app/src/sharedTest/sse/` và khai báo `sourceSets.test.resources.srcDir("src/sharedTest")`, `sourceSets.androidTest.assets.srcDir("src/sharedTest")` (một nguồn duy nhất).
- `ReleaseSmokeTest` (androidTest): `MockWebServer` lắng nghe `127.0.0.1:8787` trong process test (cũng là process app), phục vụ một trang bài viết và `POST /v1/messages` trả `summary-with-citations.sse`; test mở trang qua `ACTION_VIEW`, bấm FAB (tìm theo cd `cd_ask_skim_about_page`), Allow ở 06d, khẳng định bullet tóm tắt và chip "From paragraph" xuất hiện; Stop giữa `slow-stream.sse` đóng kết nối; `refusal.sse` hiện thẻ refusal; khẳng định request không có `x-api-key`, có `X-Skim-Install`, path `/v1/messages`. Thêm: tạo lại Activity khi 06 mở → không có request mới (Nav3 key giải mã được dưới R8).

Khôi phục:
- `RestoreAcrossContainersTest` (Robolectric): `AppContainer(app, AppStorage.temp(dir))` → tạo tab thường + incognito + nhóm + chat → `close()` → container mới trên cùng thư mục → `tabManager.restore()` trả đúng tab thường, nhóm, tab hiện tại, `chatId`; không có tab incognito; `chatId` âm không tồn tại.
- `ProcessDeathRestoreTest` (instrumented): với `ActivityScenario`: mở Reader trên một tab đã cuộn, mở 06 trên tab khác, mở 05b trên một tab incognito; gọi `pool.destroyAll()` (pool rỗng như sau process death) rồi `recreate()`; khẳng định Reader hiện bài sau khi `awaitPageReady` xong và giữ vị trí cuộn, 06 hiện chat đã lưu và không có request mới, key 05b của tab incognito bị lọc nếu tab đó đã bị bỏ.

Quy tắc đọc kết quả cổng thiết bị: đọc dòng `SkimGate` (`MULTI_PROFILE`, `HTTP_CACHE_MANAGER`, version WebView) từ `DeviceCapabilitiesReportTest`, và số test bị skip trong `app/build/outputs/androidTest-results/connected/**/TEST-*.xml`. Trên `skim_api36`: mọi test incognito phải chạy (skip = 0). Trên `skim_api28`: nếu không có `MULTI_PROFILE` thì `IncognitoUnavailableTest` phải chạy và xanh.

## Related code files

Tạo:
- `app/src/test/java/com/dantech/skimbrowser/RestoreAcrossContainersTest.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/ProcessDeathRestoreTest.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/ReleaseSmokeTest.kt`
- `app/src/sharedTest/sse/` (chuyển các file `.sse` từ `app/src/test/resources/sse/` vào đây)
- `README.md`
- `docs/project-overview-pdr.md`, `docs/codebase-summary.md`, `docs/code-standards.md`, `docs/system-architecture.md`, `docs/deployment-guide.md`, `docs/project-roadmap.md`

Sửa:
- `app/build.gradle.kts`: build type `releaseSmoke`, `testBuildType` theo property, `sourceSets` cho `src/sharedTest`, task `designParity`.
- `docs/design-guidelines.md`: hoàn thiện (trỏ tới `design/`, quy tắc token, 19 mâu thuẫn, quy trình parity, tổng hợp "Known deviations").
- Bất kỳ file sản phẩm nào cần sửa do kiểm tra của phase này phát hiện (ghi rõ trong commit; sửa lỗi, không nới test).

## Implementation steps

1. `designParity` + chạy, duyệt tổng từng cặp ảnh và ảnh overlay của board 15, sửa lệch thật trong code; lệch do nền tảng/raster bổ sung vào "Known deviations".
2. Kiểm phủ cd: mọi `name="cd_` trong `strings.xml` xuất hiện ít nhất một lần trong `app/src/main/java` (lệnh ở Verification); thiếu thì sửa. Lượt TalkBack thủ công Main path trên `skim_api36`.
3. `RestoreAcrossContainersTest`, `ProcessDeathRestoreTest`; checklist process death thủ công (Verification).
4. D1 (đã chốt — Validation Session 1): build release đầy đủ R8; ghi kích thước APK, số file dex (kỳ vọng 1) và so với 9,65 MB/4 dex của cấu hình `packageScope` cũ; kiểm `R.raw.aboutlibraries` còn trong APK (`apkanalyzer resources`).
5. `releaseSmoke` + `sharedTest` + `ReleaseSmokeTest`; chạy trên `skim_api36`; đọc logcat không có `AnthropicInvalidDataException`, `ClassNotFoundException`, `SerializationException`.
6. Chạy `verifyNoApiKeyRelease` và `verifyNoApiKeyReleaseSmoke` (cả APK lẫn AAB) với `skim.anthropicApiKey` có giá trị trong `local.properties` để chứng minh key debug không lọt.
7. Lint và xử lý.
8. Cổng thiết bị cuối (quy trình chung ở `plan.md`): toàn bộ `connectedDebugAndroidTest` trên `skim_api36` và `skim_api28`, cộng `ReleaseSmokeTest` trên `skim_api36`; kiểm bộ nhớ, trim, FLAG_SECURE, pull to refresh.
9. Viết `README.md` và các file `docs/` (đọc code thật, trỏ link tới script/manifest/schema thay vì chép chi tiết):
   - README: cài đặt `local.properties` (`skim.anthropicApiKey=…` chỉ debug — dùng key riêng có spend limit, không phân phối APK debug; `skim.aiProxyUrl` cho release khi build cục bộ), Gradle property URL pháp lý, lệnh build/test/screenshot/`designParity`, cách tạo/chạy AVD của cổng thiết bị.
   - `docs/deployment-guide.md`: biến thể build; R8 (D1); `skim.aiProxyUrl` đến từ secret của CI, không commit; checklist phát hành (`check`, `verifyNoApiKey*`, smoke test, cổng thiết bị); **hợp đồng bảo mật tối thiểu của proxy** (bên dưới) và quy định không phát hành khi proxy chưa đạt.
   - `docs/project-roadmap.md`: trạng thái D1–D8 (D2, D8 còn chờ), mục "Not designed yet" (non-goals), câu hỏi mở, giới hạn đã biết (blob listener, highlight Chromium, `ACCESS_LOCAL_NETWORK`, `EXTRA_IS_SENSITIVE` < API 33).
   - `docs/system-architecture.md`: TabManager/WebViewPool/BrowserHost, vòng đời phiên incognito, luồng AI (chốt incognito + quyền site + TOCTOU), lưu trữ và xoá dữ liệu.
10. Commit cuối phase: `docs: add readme and project docs; test: add restore and release smoke tests`.

<!-- Red Team: Hợp đồng bảo mật tối thiểu của proxy — 2026-10-21 -->
<!-- Updated: Validation Session 1 - D6 UUID + hợp đồng proxy; path /v1/messages cho Haiku 5.5 -->
Hợp đồng bảo mật tối thiểu của proxy (ghi trong `docs/deployment-guide.md`; Skim chỉ là client, proxy ngoài phạm vi xây dựng):
- Nhận `POST {proxy}/v1/messages`, giữ nguyên body hợp lệ, tự chèn `x-api-key` và `anthropic-version: 2023-06-01`, không buffer SSE.
- Xác thực theo `X-Skim-Install` (UUID theo lần cài, D6) với quota theo install và giới hạn tốc độ; install id mới bị giới hạn chặt hơn.
- Allowlist phía server: `model` phải là `claude-haiku-5-5`; `max_tokens` ≤ 8192; không có `tools`, `tool_choice`, `mcp_servers`, `thinking`, tham số sampling hay header beta; giới hạn kích thước body; chỉ các khoá Skim dùng (`model`, `max_tokens`, `system`, `messages`, `output_config`, `stream`).
- Spend cap trên key Anthropic dùng cho proxy; cảnh báo khi vượt ngưỡng.
- Khuyến nghị thêm Play Integrity (standard request) trước khi mở rộng phân phối.
- Không ghi log nội dung trang hay câu hỏi (khớp copy "Page text is sent encrypted and deleted after each answer").

## Verification notes (2026-10-23)

Không có system image trong môi trường này và không cài image. Cổng thiết bị (TalkBack, `connectedDebugAndroidTest`, `ReleaseSmokeTest` trên máy, checklist adb process death, bộ nhớ, trim, `FLAG_SECURE`) chưa chạy. Phase là done-with-concerns vì cổng đó.

Đã chạy:

- `testDebugUnitTest` xanh, gồm `RestoreAcrossContainersTest`. Fixture SSE nằm ở `app/src/sharedTest/sse/` và unit test vẫn đọc được `/sse/`.
- `ProcessDeathRestoreTest` và `ReleaseSmokeTest` biên dịch (`compileDebugAndroidTestKotlin` và `compileReleaseSmokeAndroidTestKotlin`). Chưa cài lên thiết bị.
- Kover `:app:koverXmlReportDebug`. Loại trừ class Android sinh ra và Room `*Dao_Impl` / `*Database_Impl`. Không loại composable. Một module `:app`: LINE 83.45% (10748/12879), INSTRUCTION 77.19% (114423/148240), BRANCH 60.13% (5639/9378), METHOD 80.05% (2151/2687), CLASS 86.03% (770/895).
- `designParity` viết `app/build/design-parity/summary.txt`. Lệch lớn đã có trong Known deviations: vùng WebView trống (05, 06, 06d), preview tab trống (09, 09b), sheet 06b đầy chiều cao, font Robolectric hẹp hơn PNG.
- `lintDebug` xanh, 0 error. Warning để lại, không dùng baseline: phiên bản mới hơn bản plan đã chốt (Gradle, material3, webkit, datastore, anthropic-java, okhttp, roborazzi); `ComposableNaming` và `ModifierFactoryExtensionFunction` trên helper nội bộ; `InsecureBaseConfiguration` vì trình duyệt mở được http và smoke test gọi `127.0.0.1`; `UnusedResources` cho `ic_folder`, `ic_stop` (UI dùng bản `_filled`) và ba string `error_code_*` chưa hiện trên UI.
- Mọi `cd_*` trong `strings.xml` được tham chiếu từ `app/src/main/java` (kể cả `cd_page_actions` trên hàng Page actions).
- `assembleDebug`, `assembleRelease`, `bundleRelease`, `assembleReleaseSmoke`, `bundleReleaseSmoke` xanh với `-Pskim.aiProxyUrl=https://proxy.example.invalid` và ba URL pháp lý https. `verifyNoApiKeyRelease` và `verifyNoApiKeyReleaseSmoke`: 0 match trên APK và AAB. `local.properties` không có `skim.anthropicApiKey`, nên lượt quét chứng minh không có tiền tố `sk-ant-`, không chứng minh một giá trị key debug cụ thể.
- Release APK unsigned: 5,724,857 byte, 1 file `classes.dex` (cấu hình `packageScope` cũ trong plan: 9.65 MB / 4 dex). `apkanalyzer` không có trên máy. `res/M7.json` trong APK là catalog AboutLibraries (141 libraries, 2 licenses) — resource shrinking đổi tên file, `tools:keep="@raw/aboutlibraries"` giữ payload. Debug APK: 31,828,435 byte.

## Todo list

- [x] `designParity` + duyệt tổng + bổ sung "Known deviations"
- [x] Kiểm phủ cd
- [ ] Lượt TalkBack Main path trên `skim_api36`
- [x] RestoreAcrossContainersTest
- [ ] ProcessDeathRestoreTest trên thiết bị + checklist process death thủ công
- [x] D1: đo release APK/dex, kiểm resource AboutLibraries (không có `apkanalyzer`; đã đọc JSON trong APK)
- [x] releaseSmoke + sharedTest fixtures + ReleaseSmokeTest biên dịch
- [ ] ReleaseSmokeTest trên `skim_api36`
- [x] verifyNoApiKeyRelease và verifyNoApiKeyReleaseSmoke (APK + AAB), 0 match; không có key debug trong `local.properties`
- [x] Lint sạch error (warning ghi ở trên)
- [ ] Cổng thiết bị cuối `skim_api36` + `skim_api28` (báo cáo capability, số test incognito đã chạy; bộ nhớ, trim, FLAG_SECURE, pull to refresh)
- [x] README.md + 7 file docs/ (gồm hợp đồng proxy và checklist phát hành)
- [x] Commit cuối phase

## Success criteria

- Mọi dòng của `coverage-matrix.md` có ảnh hoặc kiểm tra thủ công đã duyệt; mọi lệch còn lại nằm trong "Known deviations" kèm lý do; `designParity` sinh đủ ảnh diff cho mọi board/state.
- `testDebugUnitTest`, `verifyRoborazziDebug`, `lintDebug` xanh; `connectedDebugAndroidTest` xanh trên `skim_api36` (test incognito không skip) và `skim_api28`.
- `ReleaseSmokeTest` xanh trên `skim_api36` với build `releaseSmoke` (tóm tắt, citation, Stop, refusal, tạo lại Activity không gửi lại request).
- `verifyNoApiKey*` báo 0 match cho mọi variant không phải debug, cả APK lẫn AAB, khi có key debug.
- Process death: tab, cuộn (trang và Reader), sheet, chat được khôi phục; key incognito bị lọc; không có request AI khi khôi phục.
- Có `README.md` và đủ 7 file `docs/`; `docs/deployment-guide.md` ghi rõ phát hành bị chặn tới khi proxy đạt hợp đồng.

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:verifyRoborazziDebug
./gradlew :app:designParity && cat app/build/design-parity/summary.txt   # duyệt app/build/design-parity/*_diff.png
for n in $(grep -o 'name="cd_[a-z_]*"' app/src/main/res/values/strings.xml | cut -d'"' -f2); do grep -rq "R.string.$n\|R.plurals.$n" app/src/main/java || echo "THIẾU: $n"; done
./gradlew :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease -Pskim.aiProxyUrl=https://proxy.example.invalid -Pskim.termsUrl=https://example.invalid/terms -Pskim.privacyUrl=https://example.invalid/privacy -Pskim.aiDataPolicyUrl=https://example.invalid/ai-data
ls -l app/build/outputs/apk/release/ && unzip -l app/build/outputs/apk/release/*.apk | grep -c 'classes.*\.dex'
# Cổng thiết bị cuối (plan.md): skim_api36 (cổng 5580) và skim_api28 (cổng 5582)
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5582 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedReleaseSmokeAndroidTest -Pskim.testBuildType=releaseSmoke -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.ReleaseSmokeTest
adb -s emulator-5580 logcat -d | grep -E 'AnthropicInvalidDataException|ClassNotFoundException|SerializationException'   # phải rỗng
```

Checklist thủ công process death (trên `skim_api36`): mở 3 tab thường (một tab cuộn giữa bài, một tab ở Reader cuộn giữa bài), mở 06 trên một tab và hỏi một câu, mở thêm 1 tab incognito và mở 05d trên đó → Home → `adb -s emulator-5580 shell am kill com.dantech.skimbrowser` → mở lại từ Recents: 3 tab thường còn, đúng tab hiện tại, đúng vị trí cuộn, Reader hiện bài, sheet 06 mở lại với chat và không có request mới (log của MockWebServer/proxy), không còn tab incognito và không còn sheet 05d. Bộ nhớ: mở 10 tab → `adb -s emulator-5580 shell dumpsys meminfo com.dantech.skimbrowser` và `adb -s emulator-5580 logcat -d -s SkimWebViewPool` (≤ 3 WebView); `am send-trim-memory com.dantech.skimbrowser BACKGROUND` sau khi về Home → vẫn ≤ 3 (chỉ lưu state); `RUNNING_CRITICAL` → còn 1. FLAG_SECURE: ở tab incognito và khi 05b của tab incognito đang mở, chụp màn hình bị chặn; Recents trống.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Chèn inset status bar trên Robolectric được | Ảnh diff lệch 24dp | Bù offset 48 px trong `designParity` (đã ghi ở phase 1) |
| `testBuildType` trỏ build type minify chạy được với AGP 9 `optimization` `[UNVERIFIED]` | Test APK không cài/không chạy | Giữ `ReleaseSmokeTest` chỉ dùng UI + `MainActivity` (tên giữ bởi manifest); nếu vẫn lỗi thì chạy cùng kịch bản thủ công trên APK `releaseSmoke` với MockWebServer khởi trong test riêng, ghi kết quả |
| Image API 28 tải được và chạy trên máy dev | `sdkmanager` lỗi hoặc emulator không boot | Cổng chỉ chạy `skim_api36`; phase báo `DONE_WITH_CONCERNS` và ghi nhánh 26–28 chưa kiểm |
| Lỗi chỉ có ở release (Jackson, Nav3 key, namespace GA) | Crash/Exception trong smoke test | Thêm keep rule nhắm đúng lớp trong `rules.keep`; với SDK, chuyển sang tương đương beta đã kiểm (phase 11); chạy lại smoke |
| Trim memory RUNNING_LOW không còn được gửi ở API 34+ `[UNVERIFIED]` | Không thấy evict trên máy mới | Chính sách mới chỉ evict ở `RUNNING_CRITICAL`/`COMPLETE`/low-RAM (phase 4) |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 13 (test, docs và build type kiểm thử); sửa sản phẩm do kiểm tra phát hiện nằm trong cùng commit và được revert cùng.

## Security considerations

- MockWebServer của smoke test chỉ lắng nghe `127.0.0.1` trong process test, chỉ dùng cho `releaseSmoke` (không phát hành); không có tiến trình nền ngoài emulator của cổng.
- Docs không chứa key, đường dẫn máy cá nhân hay URL nội bộ; chỉ ghi tên property.
- Phát hành bị chặn tới khi proxy đạt hợp đồng bảo mật tối thiểu; URL proxy chỉ đến từ secret của CI.
- Xác nhận FLAG_SECURE (kể cả dưới sheet) và việc incognito không khôi phục trên emulator.

## Next steps

- Sau phase này plan có thể chuyển sang review/ship (khi proxy đạt hợp đồng). Mục chưa thiết kế (non-goals) và câu hỏi mở nằm trong `docs/project-roadmap.md`.
