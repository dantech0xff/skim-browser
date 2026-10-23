---
title: "Skim: triển khai thiết kế AI browser thành app Android Kotlin + Compose"
description: "Hiện thực mọi board của design/ thành app Skim native: WebView engine, Material 3 Expressive, Skim AI qua Claude."
status: in-progress
priority: P1
effort: 291h
branch: feat/skim-design-implementation
tags: [feature, frontend, android, ai]
blockedBy: []
blocks: []
created: 2026-10-21
---

# Skim: triển khai thiết kế thành app Android

## Overview

Biến handoff `design/` thành app Android native (Kotlin, Jetpack Compose, Material 3 Expressive 1.5.0-beta01) trên scaffold hiện có (`com.dantech.skimbrowser`, AGP 9.4.1, minSdk 26, targetSdk 37): trình duyệt một WebView mỗi tab, incognito bằng profile riêng mỗi phiên, Skim AI gọi Claude (`claude-haiku-5-5`) qua SDK Java chính thức. 13 phase, thực thi tuần tự (chỉ phase 2 và 3 song song), mỗi phase kết thúc bằng một commit checkpoint; phase 4, 5, 7, 9, 13 có cổng thiết bị.

## Phạm vi

- **Trong phạm vi:** board 01, 03, 04, 04b, 04c, 05 (+loading), 05b, 05c, 05d (link, image), 06, 06b, 06c (3 state), 06d, 08, 08b, 09, 09b, 09c, 10, 11 (3 state), 12, 13, 14, mọi overlay của board 15, toàn bộ "Rules for every screen" và "Android system UI, not drawn" của navigation-map.md, dark theme cho màn thường qua Theme (12). Board 16–18 chỉ là tài liệu. Ánh xạ từng mục: [coverage-matrix.md](./coverage-matrix.md).
- **Non-goals:** thiết kế mới cho mọi mục "Not designed yet" của README (UI trạng thái bảo mật/SSL/Safe Browsing, dialog do trang mở, UI lỗi server/usage limit của AI, no-results và đa số empty state, thư mục đã mở ngoài "cùng layout, tên thư mục làm tiêu đề", Find không match, màn Downloads trong app, danh sách site trong AI & privacy, layout ngang/tablet); xây server proxy release (chỉ viết hợp đồng bảo mật tối thiểu mà proxy phải đạt); Play Integrity. Mặc định an toàn của nền tảng giữ nguyên: SSL `cancel()`, interstitial Safe Browsing, dialog JS mặc định.

## Quyết định đã chốt (không mở lại)

<!-- Updated: Validation Session 1 - AI dùng Haiku 5.5, endpoint GA, không fallback -->
- AI: `com.anthropic:anthropic-java:2.73.0`; debug gọi `https://api.anthropic.com` với key `skim.anthropicApiKey` từ `local.properties` (key riêng có spend limit); release gọi proxy `skim.aiProxyUrl` (từ `local.properties`/CI, không commit), không gửi key; model `claude-haiku-5-5`, endpoint GA `/v1/messages`, không `fallbacks`, không header beta, effort tường minh, không gửi `thinking`/sampling; stream `callbackFlow` có Stop; refusal bỏ text; citations cho 06, structured output (không citations) cho 06b và gom nhóm; một chốt kiểm incognito + quyền site. Phase 11, 12.
- Build: Kotlin 2.4.21 (built-in Kotlin của AGP 9), KSP 2.3.12, BOM 2026.09.00 + material3 1.5.0-beta01, Navigation 3 1.2.0, DI thủ công, Room 2.8.5, DataStore 1.2.1, webkit 1.17.1, Jsoup 1.23.2, Readability.js 0.6.0, AboutLibraries 15.3.0, Roborazzi 1.76.0 + Robolectric 4.17; bỏ appcompat/material Views. Phase 1.
- Theme port + sửa weight font + `dynamicColor`; lệch component theo bản vẽ và 19 mâu thuẫn của scout. Phase 1, 2. Engine (A1–A8) và tích hợp hệ thống (B9–B10). Phase 4–7.

## Quyết định D1–D8

<!-- Updated: Validation Session 1 - D1, D3, D4, D5, D6, D7 đã chốt; D2, D8 còn chờ -->
- **D1 (đã chốt)** Release bỏ `packageScope` (R8 toàn bộ) + `-dontwarn com.google.auto.value.AutoValue`; đo size. (1, 13)
- **D2 `[DEFAULT — chờ validate]`** Tab incognito không khôi phục sau process death. (4)
- **D3 (đã chốt, có thay đổi)** Bấm "Summarize my N open tabs"/"Ask Skim about these tabs"/Ask Skim (04, 04b) là đồng ý cho mọi tab thường đang mở **trừ site đã tắt Skim AI** (`skimAi == ASK`); 09 tự gom nhóm khi mở (≥ 4 tab chưa nhóm, chỉ title + host, có Undo), cũng loại các site đó; text trang chỉ trích lúc bấm từ tab đang sống, không đọc nền. (3, 12)
- **D4 (đã chốt)** Chat theo trang: tab giữ chat khi còn ở cùng trang (URL bỏ fragment; điều hướng cùng document như SPA `pushState` không tính); sang trang khác là chat mới; chat cũ ở Skim history nếu Keep chat history bật. (4, 11)
- **D5 (đã chốt)** Đủ ba loại download: http/https (DownloadManager cho tab thường; OkHttp trong process cho tab incognito), `data:` và `blob:` lưu MediaStore Downloads; có gia cố tên tệp, trần kích thước, giới hạn download tự động, listener blob tạm thời theo origin. (4, 7)
- **D6 (đã chốt)** Client release gửi `X-Skim-Install` (UUID theo lần cài); proxy phải đạt hợp đồng bảo mật tối thiểu ở `docs/deployment-guide.md` trước khi phát hành. (3, 11, 13)
- **D7 (đã chốt, đổi)** Model `claude-haiku-5-5`, một hằng `AiConfig.MODEL_ID`. (11)
- **D8 `[DEFAULT — chờ validate]`** Trạng thái chưa thiết kế mà code không tránh được: dùng pattern gần nhất + copy tối thiểu trong `strings.xml` (bảng dưới). (1, 2, 5–9, 11, 12)

### Copy cần design review (D8)

| Tình huống | Pattern dùng lại | String |
|---|---|---|
| AI lỗi server/rate limit/SSE | Thẻ 06c + `ic_error` + Try again | `ai_server_error_title` "Skim couldn't answer"; `ai_server_error_body` "Something went wrong on Skim's side. Try again in a moment." |
| AI từ chối | Thẻ 06c + `ic_info` | `ai_refusal_title` "Skim can't help with this"; `ai_refusal_body` "Try asking about the page in a different way." |
| Trang crash lặp lại (vượt ngân sách renderer) | Bố cục 11 + SoftBurst `ic_error` + Try again | `error_crashed_title` "This page stopped working"; `error_crashed_body` "%s crashed more than once. Try again, or go back." |
| WebView không hỗ trợ incognito | Snackbar 4 s | `incognito_unavailable` "Incognito needs a newer Android System WebView. Update it from Google Play, then try again." |
| Quyền camera/mic | Dialog Site permission, hàng site info | `site_permission_camera_title` "Let %s use your camera?"; `site_permission_microphone_title` "Let %s use your microphone?"; `site_permission_camera_microphone_title` "Let %s use your camera and microphone?"; `site_info_camera` "Camera"; `site_info_microphone` "Microphone"; `site_permission_allowed` "Allowed" |
| Trang http | Nút site info với `ic_public` | `cd_site_info` "Site information" |
| Thời gian | Như "2 h ago" | `time_just_now` "Just now"; plural `time_minutes_ago` "%d min ago"; `date_earlier` "Earlier" |
| Danh sách rỗng | Như text rỗng của 04c | `history_empty` "Pages you visit show up here"; `bookmarks_empty` "Pages you bookmark show up here"; `tabs_empty` "Tabs you open show up here" |
| Phụ đề 12 chưa vẽ | Phụ đề hàng | `settings_default_browser_summary_not_default` "Skim isn't your default browser"; `settings_ai_privacy_summary_no_ask` "Reads sites when you ask" |
| Tải/chia sẻ lỗi, download tự động bị chặn | Snackbar 4 s | `snackbar_download_failed` "Couldn't download %s"; `snackbar_share_failed` "Couldn't share this image" |
| Hành vi không có copy | — | Home lần đầu ẩn hero (N = 0) và Continue; 06b không prompt chỉ có header + ô nhập; ô tìm kiếm thay tiêu đề ở 04c/09/09b/13/14, không có no-results; Find không match hiện "0/0"; tóm tắt bị gián đoạn hiện phần đã có + Try again |

## Quyết định nhỏ (đã chốt)

- Từ orchestrator: chip 06 và "Other" là string cố định; 05b từ Home ẩn header, Add bookmark, Share, Find, Desktop site; tắt Skim AI trong site info → 06d hỏi lại; reader mặc định 100%; Clear browsing data mặc định như vẽ; "Imported N bookmarks › View" mở 14 ở thư mục Imported; dialog sửa shortcut có tiêu đề "Edit shortcut"; Q&A ở 06 không có copy/feedback; plural `one` suy ra từ `other`.
- Của planner (lý do ở phase ghi trong ngoặc): TabManager (process) tách WebViewPool (Activity), đối tượng Activity-scoped qua một `LocalBrowserHost` (4); profile incognito mới mỗi phiên, phiên `Ending` không bị nối lại (4); popup `window.open` giữ vì OAuth/thanh toán, đổi lại ghim cặp opener ↔ popup (4); Back ở trang đầu của tab mở từ Home về lại Home (5); 08b là panel không modal (10); 05 hiện host, 11 hiện URL bỏ scheme (7); một đường duy nhất đổi thư mục bookmark (`MoveToFolderKey`, lưu ngay) (7); favicon là ô chữ cái như bản vẽ (2); history một dòng mỗi URL (3); `site_permissions` khoá theo host, không theo origin (3); Keep chat history tắt → chat chỉ trong bộ nhớ (11); chat khôi phục replay dạng text (11); feedback chỉ lưu cục bộ (11); link nhóm 06b cuộn tới section của tab đầu nhóm (12); Close all incognito không Undo (9); "Recently added" 10 mục (3); snackbar ở màn không có toolbar cách nav bar 16 (2); backup + device transfer tắt (1); URL pháp lý là Gradle property, release fail nếu rỗng (1); Edit bookmark/Move to folder chuyển sang phase 7 vì 05b cần trước.

## Quy ước tích hợp và thực thi

<!-- Updated: Validation Session 1 - commit checkpoint cuối mỗi phase; nhánh feat -->
- Phase 1 bắt đầu bằng commit khởi tạo trên `master` (`chore: initial project scaffold and design handoff`, không stage `.claude/`, `.idea/workspace.xml`, `local.properties`) rồi tạo nhánh `feat/skim-design-implementation`. Mỗi phase kết thúc bằng **một** commit conventional sau khi Verification đạt (không push, không nhắc AI, không commit secret hay `local.properties`). Rollback một phase = `git revert` commit của phase đó.
- Chạy tuần tự 1 → 13, chỉ 2 ∥ 3. File đăng ký dùng chung (`ui/navigation/SkimNavDisplay.kt`, `ui/navigation/SkimNavigator.kt`, `AppContainer.kt`) được phase sau bổ sung lần lượt; phase đổi constructor phải sửa test tương ứng trong cùng phase.
- Mọi đích có key từ phase 5; entry đăng ký ở phase sở hữu đích; control trỏ tới đích chưa xây vẫn gọi `SkimNavigator` (khoảng trống giữa phase được chấp nhận; chỉ phát hành sau phase 13 và khi proxy đạt hợp đồng).
- Parity: golden Roborazzi của từng phase được duyệt so với PNG ngay trong phase đó; lệch ghi vào `docs/design-guidelines.md` ("Known deviations"). Phase 13 chỉ duyệt tổng qua task `designParity`.
- Không đưa ID plan/phase vào code, test, commit.

### Cổng thiết bị (cuối phase 4, 5, 7, 9, 13)

<!-- Updated: Validation Session 1 - AVD skim_api36 + skim_api28 -->
1. Tạo AVD riêng một lần (không đụng AVD của dự án khác `duoly_baseline_profile_api36`, `localmsg_stress_api36`):
   `~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n skim_api36 -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7`;
   `~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "system-images;android-28;google_apis;arm64-v8a"` (~1 GB) rồi `avdmanager create avd -n skim_api28 -k "system-images;android-28;google_apis;arm64-v8a" -d pixel_7`. WebView cũ của image API 28 nhiều khả năng không có `MULTI_PROFILE`, dùng để kiểm đường "incognito unavailable".
2. Trước khi khởi động: `adb devices` và `lsof -i :5580` / `:5582`; emulator của chính dự án đang chạy thì dùng lại, không mở bản thứ hai.
3. Khởi động như tiến trình nền có theo dõi (ghi PID/cổng): `~/Library/Android/sdk/emulator/emulator -avd skim_api36 -port 5580 -no-snapshot-save` (và `-avd skim_api28 -port 5582`); chờ `adb -s emulator-5580 wait-for-device` và `sys.boot_completed = 1`.
4. Chạy `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest` (lọc theo package của phase), rồi tương tự với `emulator-5582`; phase 13 thêm smoke test release. Đọc báo cáo `SkimGate` (`MULTI_PROFILE`, `HTTP_CACHE_MANAGER`) và số test bị skip: trên `skim_api36` test incognito bị skip = cổng không đạt.
5. Kết thúc cổng: `adb -s emulator-5580 emu kill` (và 5582); kiểm không còn tiến trình emulator của Skim.
6. Cổng không chạy được (image không tải được, emulator không boot) → phase báo `DONE_WITH_CONCERNS`, không đánh dấu hoàn tất.

## Phases

| # | Phase | Effort | Phụ thuộc | Status |
|---|---|---|---|---|
| 1 | [Build foundation, theme & resources](./phase-01-build-foundation-theme-resources.md) | 17h | — | Completed |
| 2 | [Design-system components](./phase-02-design-system-components.md) | 23h | 1 | Completed |
| 3 | [Data layer](./phase-03-data-layer.md) | 14h | 1 | Completed |
| 4 | [Browser engine](./phase-04-browser-engine.md) | 44h | 1, 3 | Completed |
| 5 | [App shell, navigation & onboarding](./phase-05-app-shell-navigation-onboarding.md) | 22h | 2, 3, 4 | Completed |
| 6 | [Home, search & incognito new tab](./phase-06-home-search-incognito-new-tab.md) | 16h | 5 | Completed |
| 7 | [Browsing surfaces](./phase-07-browsing-surfaces.md) | 29h | 4, 5, 6 | Done with concerns |
| 8 | [Settings, AI & privacy, History, Bookmarks](./phase-08-settings-privacy-history-bookmarks.md) | 20h | 5, 7 | Done with concerns |
| 9 | [Tabs](./phase-09-tabs.md) | 21h | 2, 4, 5 | Done with concerns |
| 10 | [Reader view](./phase-10-reader-view.md) | 11h | 4, 7 | Done with concerns |
| 11 | [Skim AI core](./phase-11-skim-ai-core.md) | 33h | 3, 4, 7, 10 | Pending |
| 12 | [Skim AI across tabs & Skim history](./phase-12-skim-ai-across-tabs-history.md) | 19h | 6, 8, 9, 11 | Pending |
| 13 | [Verification, hardening & docs](./phase-13-verification-hardening-docs.md) | 22h | 1–12 | Pending |

## Dependencies

- Không phụ thuộc plan khác. Bên ngoài: Android SDK 37 (có emulator, cmdline-tools, image API 36; image API 28 tải ở cổng phase 4), JDK cho Gradle daemon, key Anthropic cá nhân có spend limit cho debug, URL pháp lý và URL proxy (proxy đạt hợp đồng bảo mật) cho release.

## Acceptance criteria (toàn plan)

- Mọi dòng của coverage-matrix.md chạy và được kiểm (test tự động hoặc checklist thủ công đã ghi); golden mọi board/state đã duyệt so với PNG, lệch còn lại ghi trong `docs/design-guidelines.md`.
- `./gradlew :app:testDebugUnitTest`, `:app:verifyRoborazziDebug`, `:app:lintDebug` xanh; `:app:connectedDebugAndroidTest` xanh trên `skim_api36` (test incognito không skip) và `skim_api28`.
- `:app:assembleRelease` và `:app:bundleRelease` xanh với R8 đầy đủ; `verifyNoApiKey*` 0 match cho mọi variant không phải debug (APK + AAB); `ReleaseSmokeTest` xanh trên build `releaseSmoke`.
- Không request AI nào từ tab incognito hay từ site đã tắt Skim AI (test); FLAG_SECURE (kể cả dưới sheet) và vòng đời phiên incognito đã kiểm trên emulator; process death khôi phục tab, cuộn, sheet, chat (incognito thì không) và không gửi request AI khi khôi phục.
- Có `README.md` và 7 file `docs/` khớp code; phát hành bị chặn tới khi proxy đạt hợp đồng bảo mật tối thiểu.
- Mỗi phase có commit checkpoint trên `feat/skim-design-implementation`.

## Test matrix

| Lớp | Công cụ | Phase |
|---|---|---|
| Logic, ViewModel, repository | JUnit, coroutines-test, Turbine, Robolectric (Room in-memory) | 2–12 |
| Golden component/màn hình (= parity đã duyệt) + a11y | Roborazzi `w412dp-h915dp-xhdpi`, `dynamicColor = false`, `assertA11y()` | 2, 5–12 |
| Duyệt tổng parity | Task `designParity` (ảnh diff golden ↔ PNG) | 13 |
| Request/stream AI | MockWebServer + fixture SSE (gồm fixture red team) | 11, 12 |
| WebView, profile, FLAG_SECURE, cử chỉ, khôi phục | Instrumented trên `skim_api36`/`skim_api28` | 4, 5, 7, 9, 13 |
| Release R8 | `ReleaseSmokeTest` (instrumented, build `releaseSmoke`, MockWebServer trên thiết bị) | 13 |

Rollback: `git revert` commit checkpoint của phase cần bỏ (app chưa phát hành; Room schema v1 không cần migration ngược; cache `tabstate/`, `thumbs/`, `shared/` xoá an toàn).

## Nguồn

- [Researcher: build stack + Compose M3E](../reports/researcher-261020-2319-android-build-stack-compose-m3e.md)
- [Researcher: WebView engine + Claude SDK](../reports/researcher-261020-2319-webview-engine-claude-sdk.md)
- [Scout: kiểm kê thiết kế](../reports/scout-261020-2319-design-inventory.md)
- Red team: [security adversary](./reports/red-team-security-adversary.md), [failure mode analyst](./reports/red-team-failure-mode-analyst.md), [assumption destroyer](./reports/red-team-assumption-destroyer.md), [scope & complexity critic](./reports/red-team-scope-complexity-critic.md)
- Thiết kế: `design/README.md`, `design/spec/{navigation-map,components,dialogs,tokens}.md`, `design/compose/`, `design/screens/`

## Red Team Review

### Session 2026-10-21

Bốn reviewer (security adversary, failure mode analyst, assumption destroyer, scope & complexity critic). Findings: 40 thô → 15 sau khi gộp trùng; 15 chấp nhận / 0 bác bỏ; mức độ: 3 Critical, 12 High. Mọi chỗ sửa trong phase có marker `<!-- Red Team: … — 2026-10-21 -->`.

| # | Finding | Severity | Disposition | Áp dụng ở |
|---|---|---|---|---|
| 1 | Khôi phục sau process death: Reader/06 không có WebView/trang đã tải; key khôi phục trỏ tab không còn; tự tóm tắt khi khôi phục | Critical | Accepted | 4, 5, 10, 11, 13 |
| 2 | Phiên incognito bị dùng lại trong 10 s Undo | Critical | Accepted | 4, 9 |
| 3 | Proxy release thành relay mở; URL bị commit; cổng chặn key bỏ sót AAB | Critical | Accepted (giữ quyết định proxy ngoài phạm vi; thêm hợp đồng + chặn phát hành) | 1, 11, 13 |
| 4 | Ranh giới dữ liệu incognito: cache, cookie cho DownloadManager, file share, clipboard, FLAG_SECURE dưới sheet/lúc chuyển cảnh, gia cố download | High | Accepted | 4, 5, 7 |
| 5 | Threading/atomicity: `restore()` gọi ProfileStore off-main, state file ghi không nguyên tử → crash loop | High | Accepted | 1, 4 |
| 6 | `incomingLinks` replay mở lại link cũ khi tạo lại Activity | High | Accepted | 5 |
| 7 | Snackbar/Undo giữ lambda của navigator chết, Undo trong scope bị huỷ, đua với Delete all, vỡ UNIQUE; registry nhiều host | High | Accepted | 2, 3, 5, 6, 8, 9, 11, 12 |
| 8 | UXSS qua `highlightBlock` | High | Accepted | 4, 11 |
| 9 | 06b/gom nhóm bỏ qua quyền site, đọc nền (digest), TOCTOU, gom nhóm đua với thao tác | High | Accepted (kết hợp D3 Validation Session 1) | 3, 4, 11, 12 |
| 10 | Prompt injection không có biện pháp giảm thiểu | High | Accepted | 4, 11, 12 |
| 11 | Dữ liệu tồn đọng: D2D transfer, Clear data để lại dấu vết, xoá quyền AI không xoá dòng | High | Accepted | 1, 3, 8 |
| 12 | D4 kích hoạt cả với điều hướng cùng document; stream khi điều hướng/đóng sheet | High | Accepted | 4, 11 |
| 13 | Vòng đời WebView: crash lặp vô hạn, reload mạng, trim phá popup, không pause | High | Accepted | 4, 7 |
| 14 | Hợp đồng giữa phase: đối tượng Activity-scoped, chốt thiếu dữ liệu Tab, lệch chữ ký, test không cập nhật, key thiếu tham số, nhiều đường ghi, owner sai trong matrix | High | Accepted | 3, 4, 5, 7, 8, 9, 11, 12, coverage-matrix |
| 15 | Cổng kiểm chứng: parity làm hai lần, smoke test bằng server Python, skip tính là pass, không có cổng thiết bị, rollback không có commit | High | Accepted | 2, 4, 5, 7, 9, 13, plan.md |

Đề xuất con bị bác bỏ (không áp dụng): một profile incognito cố định cho cả process (giữ xoay vòng theo phiên); chuyển dựng prompt sang server (mâu thuẫn quyết định proxy pass-through); tách file đăng ký navigation theo tính năng (các phase chạy tuần tự, không xung đột); share ảnh incognito qua stream không file (dùng `shared/incognito/` xoá khi phiên kết thúc).

### Whole-Plan Consistency Sweep

- File đọc lại: `plan.md`, `coverage-matrix.md`, `phase-01` … `phase-13` (15 file).
- Delta quyết định đã đối chiếu: D1, D3, D4, D5, D6, D7 chuyển sang "đã chốt" ở mọi phase áp dụng (D1: 1, 13; D3: 3, 6, 12; D4: 4, 11; D5: 4, 7; D6: 3, 11, 13; D7: 11); D2 còn 1 tag `[DEFAULT — chờ validate]` (phase 4) và D8 còn 13 tag (phase 1, 2, 5, 6, 7, 8, 9, 11, 12) — đúng chủ ý; thay `LocalWebViewPool` → `LocalBrowserHost`, `focusGroupId` → `focusTabId`, `ReaderTextKey` → panel 08b, `cleared` → `removals`, `resetAllSkimAi` → `deleteAllSkimAiData`, `onDismissed`/`Run`/`StartIntent` → `SnackbarAction` + `UndoRegistry`; `httpClient` chuyển sở hữu sang phase 4; `TabEngine.clearHistory` chuyển về phase 4; `applyLayout` thêm `expectedVersion` + `snapshot`.
- Thuật ngữ cũ đã quét (0 lần dùng còn hiệu lực): `fallbacksDefault`, `FellBack`, `answer-with-fallback`, `claude-opus-5-5`, `CLAUDE_OPUS_5_5`, `replay = 1`, `fake-claude-sse`, `revert commit của phase`, `registerHost`/`topHost`/`InSheet`, `newTab(url, openedByExternalApp`, `LocalWebViewPool`, `captureThumbnail`, `apiMessageJson`, `ParityFixtures`, `adb reverse`, `commit/push`. Các lần nhắc còn lại là chủ ý: `SERVER_SIDE_FALLBACK` và `?beta=true` chỉ trong lệnh/test kiểm phủ định (phase 11); `PageDigestCache` chỉ trong câu giải thích việc bỏ và lệnh grep phủ định (phase 12); `packageScope` chỉ mô tả việc bỏ (phase 1, 13); `allowBackup` đi cùng `dataExtractionRules` (phase 1); `incomingLinks` chỉ trong marker red team (phase 5).
- Mâu thuẫn chưa giải quyết: 0.

## Validation Log

### Session 1 — 2026-10-21

Câu hỏi ghi theo bản ghi của orchestrator (giữ nguyên chữ "…" ở chỗ bản ghi rút gọn).

1. **[Scope/Privacy]** "Khi người dùng bấm "Summarize my N open tabs" hoặc "Ask Skim about these tabs" (06b), và khi 09 tự gom nhóm tab, Skim được gửi gì lên cloud?" Lựa chọn: Bỏ site đã tắt (Recommended) | Gom nhóm chỉ khi bấm | Chặt: chỉ site đã Allow. **Trả lời: Bỏ site đã tắt.** Lý do: giữ trải nghiệm một chạm của 06b và banner gom nhóm như thiết kế, nhưng tôn trọng lựa chọn tắt Skim AI theo site; bỏ đọc nền để khớp copy "Skim reads a page only when you ask". → D3 đã chốt có thay đổi (phase 3, 6, 12; `PageDigestCache` bị bỏ).
2. **[Assumption]** "Chat của Skim gắn với tab hay với từng trang trong tab?" Lựa chọn: Theo trang (Recommended) | Theo tab. **Trả lời: Theo trang.** Lý do: câu trả lời và citation chỉ đúng với trang đã đọc. → D4 đã chốt; điều hướng cùng document giữ chat (phase 4, 11).
3. **[Scope]** "Phạm vi download: ngoài link http/https (DownloadManager), có hỗ trợ link data: và blob: …?" Lựa chọn: Đủ cả ba loại (Recommended) | Chỉ http/https. **Trả lời: Đủ cả ba loại.** Lý do: nhiều trang sinh file bằng `blob:`/`data:`; đi kèm gia cố của red team finding 4. → D5 đã chốt; tab incognito tải trong process (phase 4, 7).
4. **[Architecture]** "Model Claude cho Skim AI?" Lựa chọn: claude-opus-5-5 (Recommended) | claude-sonnet-5-5 | claude-haiku-5-5. **Trả lời: claude-haiku-5-5.** Lý do: chi phí và độ trễ cho trình duyệt (giá $0,10/$0,50 mỗi MTok khi prompt ≤ 100K token). Hệ quả: Haiku 5.5 không có server-side fallback → bỏ `fallbacks`, header `server-side-fallback-2026-07-01`, `AiEvent.FellBack`, fixture `answer-with-fallback.sse`; refusal xử lý phía client; dùng endpoint GA `/v1/messages` và kiểu GA (chữ ký đã kiểm bằng `javap` ngày 2026-10-21, runtime ART `[UNVERIFIED]`, dự phòng là kiểu beta); thinking thích ứng bật mặc định (đọc block theo loại, không gửi thinking/budget/sampling/prefill); effort MEDIUM cho trang và 06b, LOW cho gom nhóm; hội thoại append-only (system hằng, chỉ dẫn ngôn ngữ ở lượt người dùng); ngưỡng cache 512 token. → D7 đổi (phase 11, 12, 13).
5. **[Security]** "Proxy release sẽ có key thật. App nên xác thực với proxy thế nào…?" Lựa chọn: UUID + hợp đồng proxy (Recommended) | Thêm Play Integrity vào app | Bỏ X-Skim-Install. **Trả lời: UUID + hợp đồng proxy.** Lý do: giữ proxy ngoài phạm vi nhưng đặt điều kiện tối thiểu (auth theo install + quota, allowlist phía server, spend cap, khuyến nghị Play Integrity) và chặn phát hành tới khi đạt; URL proxy không commit. → D6 đã chốt (phase 1, 3, 11, 13).
6. **[Architecture]** "R8 cho bản release: … xử lý thế nào?" Lựa chọn: Bỏ packageScope (Recommended) | Thêm package của SDK. **Trả lời: Bỏ packageScope.** Lý do: shrink toàn bộ, APK nhỏ (probe 1,34 MB so với 9,65 MB). → D1 đã chốt (phase 1, 13).
7. **[Risk]** "Kiểm trên thiết bị …: máy có sẵn 2 AVD API 36 nhưng chưa có image API 26-28. Dùng gì?" Lựa chọn: AVD 36 + tải image API 28 (Recommended) | Chỉ AVD API 36 | Điện thoại thật của bạn. **Trả lời: AVD 36 + tải image API 28.** Lý do: phủ cả nhánh API 26–28 và đường "incognito unavailable". → Cổng thiết bị ở phase 4, 5, 7, 9, 13 với `skim_api36`/`skim_api28` (mục "Cổng thiết bị").
8. **[Process]** "Rollback: plan hiện không có bước commit nào. Có cho phép commit checkpoint sau mỗi phase không?" Lựa chọn: Commit cuối mỗi phase (Recommended) | Hỏi trước mỗi commit | Không commit. **Trả lời: Commit cuối mỗi phase.** Lý do: rollback một phase bằng `git revert`. → Commit khởi tạo + nhánh `feat/skim-design-implementation` ở phase 1; mỗi phase một commit cuối; frontmatter `branch` đổi.

Thay đổi được đánh dấu `<!-- Updated: Validation Session 1 - … -->` tại chỗ.

#### Verification Results

- Tier: Full (dùng lại kết quả kiểm của red team).
- Fact Checker: 80 claim — 69 verified, 5 failed, 6 unverified.
- 5 claim sai đã được xử lý: (1) `SitePermission` theo origin trong scout → plan giữ `host` làm khoá chính và ghi thành quyết định tường minh (phase 3); (2) "tab incognito không bao giờ ghi đĩa" → sửa thành thư mục profile có trên đĩa và bị xoá/purge (finding 4, phase 4); (3) "Profile deleted: … cache" chưa đạt khi thiếu `HTTP_CACHE_MANAGER` → `end()` gọi `clearCache(true)` qua WebView tạm của profile (finding 4); (4) chốt AI thiếu kiểm quyền site → chốt kiểm incognito + quyền site + host trích được (finding 9, phase 11); (5) phase 10 nói Readability chỉ chạy khi mở Reader trong khi phase 12 có digest nền → bỏ digest (finding 9, D3).
- 6 claim chưa kiểm: `usesCleartextTraffic` bị bỏ qua từ target 38 (nguồn: trang docs `<application>` đọc 2026-10-21, ghi ở phase 1); schema JSON tuỳ chỉnh của AboutLibraries 15.3.0 (vẫn `[UNVERIFIED]`); blob qua `addWebMessageListener` (vẫn `[UNVERIFIED]`, có phương án báo lỗi); khung thumbnail 168×92 (nay suy từ số đo scout trong phase 4); ngưỡng cache 512 token (giữ theo orchestrator cho Haiku 5.5); tên `app-release-unsigned.apk` (lệnh nay dùng wildcard).

#### Whole-Plan Consistency Sweep

- File đọc lại: 15 file như mục Red Team Review.
- Delta quyết định đã đối chiếu: 8 câu trả lời → D1, D3, D4, D5, D6, D7 (đổi model) và hai quy ước mới (cổng thiết bị, commit checkpoint) có mặt ở mọi phase liên quan; frontmatter `branch: feat/skim-design-implementation`; câu "không có bước commit/push" đã bỏ; câu hỏi mở về digest 06b đã gỡ.
- Tham chiếu cũ đã đối soát: như danh sách ở Red Team Review (cùng một lượt quét).
- Mâu thuẫn chưa giải quyết: 0.

## Câu hỏi chưa giải quyết

1. Giá trị `skim.termsUrl`, `skim.privacyUrl`, `skim.aiDataPolicyUrl`, `skim.aiProxyUrl` (cần trước khi build release; debug chạy được khi rỗng), và ai sở hữu proxy đạt hợp đồng bảo mật.
2. Icon launcher chưa có trong thiết kế: giữ icon template hay chờ asset?
3. `ACCESS_LOCAL_NETWORK` (target 37) có thể chặn trang LAN; có cần xin quyền (chưa có thiết kế)?
4. Toolbar cách nav bar 24 (đã chốt) khác board (24 từ cạnh màn hình) trên máy có thanh cử chỉ: xác nhận.
5. Màu highlight find-in-page và link/ảnh khi nhấn giữ do Chromium vẽ, không khớp bản vẽ: chấp nhận?
6. Bảng copy D8 cần designer/copywriter duyệt (D2 và D8 vẫn chờ validate).

<!-- slug: skim-design-implementation -->
