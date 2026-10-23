---
phase: 12
title: "Skim AI across tabs & Skim history"
status: pending
priority: P2
effort: "19h"
dependencies: [6, 8, 9, 11]
---

# Phase 12: Skim AI across tabs & Skim history

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §C14 (structured output, schema viết tay, không bật citations, gom nhóm chỉ gửi `{id, title, host}`, tóm tắt nhiều tab cắt ~4k ký tự mỗi tab, kiểm `stop_reason` trước khi parse).
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-security-adversary.md` Finding 3, 4; `red-team-failure-mode-analyst.md` Finding 8, 9; `red-team-assumption-destroyer.md` Finding 9; `red-team-scope-complexity-critic.md` Finding 3, 9, 10.
- Scout `plans/reports/scout-261020-2319-design-inventory.md` §2 (04 hero, 04c, 06b, 09 banner, 15 Clear Skim history), §3(a) plural `skim_history_questions_about`, `skim_history_answers_across_tabs`, `home_summarize_tabs`, `ai_reading_tabs`.
- `design/spec/navigation-map.md` mục 04 (Summarize my N open tabs, See all, Ask Skim), 04b (Ask Skim row), 06b, 09 (Undo in the banner, Ask Skim about these tabs), 04c; `design/spec/dialogs.md` (Clear Skim history).
- Board: `design/screens/06b-ask-skim-from-home-tabs.png`, `design/screens/04c-skim-history.png`, `design/screens/09-tabs.png` (banner), `design/screens/04-home-new-tab.png` (hero), `design/screens/15-dialogs-menus-snackbars.png`.

## Overview

Mở rộng Skim AI sang nhiều tab: 06b Ask Skim (tóm tắt các tab thường được phép dạng JSON có cấu trúc, hỏi tiếp, câu hỏi gợi ý đã làm sạch, link nhóm sang 09), banner gom nhóm tự động trên 09 với Undo an toàn trước tranh chấp, và 04c Skim history (tìm, xoá từng mục + Undo, xoá hết qua dialog, mở lại chat trang hoặc câu trả lời nhiều tab). Làm các row 04 hero, See all, Ask Skim, 04b Ask Skim row, 09 Ask Skim about these tabs chạy end-to-end.

## Key insights

- Citations + structured output = HTTP 400: request 06b và gom nhóm dùng `OutputConfig.format(JsonOutputFormat)` với schema viết tay (`additionalProperties: false` mọi object, không đệ quy, không trường optional/union), không citations. Không dùng helper `outputConfig(Class)` (sinh schema bằng reflection, R8 đổi tên field). Parse bằng kotlinx-serialization chỉ sau khi `stop_reason` là `end_turn`.
<!-- Updated: Validation Session 1 - D3 đã chốt với thay đổi: loại site đã tắt Skim AI, không đọc nền -->
- D3 (đã chốt, có thay đổi — Validation Session 1: "Bỏ site đã tắt"): bấm "Summarize my N open tabs"/"Ask Skim about these tabs"/Ask Skim từ 04 và 04b là đồng ý cho mọi tab thường đang mở **trừ site đã tắt Skim AI** (`skimAi == ASK`); gom nhóm tự động trên 09 vẫn chạy khi mở 09 (chỉ title + host, có Undo) và cũng loại các site đó. Text trang chỉ được trích lúc người dùng bấm, từ tab có WebView đang sống; tab không sống chỉ gửi title + host. Không có đọc nền (`PageDigestCache` bị bỏ).
<!-- Red Team: Prompt injection ở 06b — 2026-10-21 -->
- Mỗi tab được gửi thành một `document` riêng (title, context = host là trường riêng; không ghép chuỗi thô), nên một trang không thể giả ranh giới tab khác. Kết quả JSON được kiểm: `tabIds ⊆ refs`, mỗi ref tối đa một lần; `followUps` và tên nhóm được làm sạch (một dòng, ≤ 60 ký tự, bỏ chuỗi có URL hay dãy số giống số điện thoại); mục không hợp lệ bị bỏ.
<!-- Red Team: Gom nhóm chạy đua với thao tác người dùng — 2026-10-21 -->
- Gom nhóm dùng tương tranh lạc quan: ghi lại `tabSetKey` và `layoutVersion` lúc gửi; chỉ áp kết quả nếu cả hai chưa đổi; loại tab đang chờ Undo đóng; huỷ request khi rời 09; layout + snapshot ghi trong một transaction; Undo trên banner chỉ bật khi layout vẫn là layout vừa áp.
- Link nhóm trong 06b trỏ tới tab thật: cuộn 09 tới section đang chứa tab đầu tiên của nhóm đó, không tạo nhóm mới từ câu trả lời 06b (quyết định nhỏ của planner).
- Haiku 5.5 (D7): effort MEDIUM cho 06b, LOW cho gom nhóm; không `fallbacks`, không header beta, không tham số thinking/sampling.

## Requirements

Chức năng:
- 06b (sheet cao tối đa 760): header Sunny 44 + "Ask Skim" + plural `ai_reading_tabs` (N = số tab được đưa vào; ẩn dòng phụ khi N = 0, `[DEFAULT — chờ validate]` D8) + Close; bubble người dùng; thẻ trả lời (`AnswerCard`, gap 16): câu mở + mỗi nhóm [link `primary` 14/20 600 + `ic_chevron_right` 18 → 09 cuộn tới nhóm] + đoạn tóm tắt; hàng Copy/Good/Poor answer 40 (copy hiện snackbar cục bộ trong sheet khi SDK ≤ 32); chip câu hỏi gợi ý (từ `followUps` đã làm sạch); ô nhập "Ask anything" + Send. Nhóm không có tên trong JSON hiển thị bằng string cố định "Other" (`tabs_group_other`, quyết định nhỏ đã chốt).
- Mở 06b: từ hero 04 với prompt `home_summarize_tabs` đã gửi; từ hàng Ask Skim 04b với text làm câu hỏi (gửi ngay); từ FAB 04 và nút AI 09 → mở trống (chỉ header + ô nhập); từ 04c → câu trả lời đã lưu. Trong lúc chờ JSON hiện `AiLoadingIndicator` + `AiWavyProgress` không chữ (quyết định nhỏ). Lỗi dùng chung thẻ Offline/Server/Refused của phase 11. Khôi phục `AskTabsKey` không gửi lại request (cờ `openHandled`).
- Hỏi tiếp (chip, Send): câu trả lời text thường, stream, không citations; ngữ cảnh tab (các document) nằm ở lượt người dùng đầu tiên, `cache_control` ở document cuối.
- Close/vuốt → đóng; lưu vào Skim history (chat `TABS`) khi Keep chat history bật.
- Gom nhóm 09 (D3): khi `TabsKey(incognito = false)` mở, nếu ≥ 4 tab thường chưa nhóm có trang (không tính tab chờ Undo đóng, không tính site `ASK`) và `tabSetKey` (hash các id đã sắp xếp) khác snapshot → gọi model (effort LOW, `maxTokens` 2048, schema `{groups:[{name, tabIds[]}]}`), bỏ nhóm < 2 tab, kiểm tranh chấp như Key insights, rồi `tabs.applyLayout(layout, expectedVersion, snapshot)` (một transaction), hiện banner "Skim grouped your tabs by topic" + Undo. Undo → khôi phục layout trong snapshot (chỉ khi layout chưa đổi). Rời 09 → huỷ request đang chạy và ẩn banner (snapshot giữ lại để không gom lại cùng tập tab). Không mạng → bỏ qua im lặng.
- 04c: Large title + Search + Delete all; section Today/Yesterday/Earlier this week (và `date_earlier` D8); mục PAGE (Cookie4Sided chữ cái, title trang, phụ đề `skim_history_questions_about` khi có câu hỏi, ngược lại `skim_history_summary_of`), mục TABS (Sunny, title = prompt, phụ đề `skim_history_answers_across_tabs`), nút Remove (cd `cd_remove_from_skim_history`) → xoá + snackbar "Removed from history" + Undo (qua `UndoRegistry`, loại `SKIM_CHAT`); footer `ic_verified_user` + `skim_history_footer`; rỗng → `skim_history_empty`. Page item → `navigator.openSavedChat(chatId)`; Tabs item → `navigator.openSavedTabsChat(chatId)`; Delete all → dialog Clear Skim history (không icon; Cancel, Clear `primary`) → `AiDataEraser.clearSkimHistory()` (huỷ Undo đang chờ, giữ quyền site); Back về 04 hoặc 10.

Phi chức năng:
- Không request nào chứa dữ liệu tab incognito hay site `ASK` (chốt `SkimAiService.send` với `Consent.TABS_ACTION`).
- Request gom nhóm không chứa text trang (test kiểm body).
- Mỗi tab tối đa 4.000 ký tự; tối đa 40 tab (tab truy cập gần nhất trước), giữ prompt dưới 100K token.

## Architecture

```kotlin
// ai/TabsContext.kt
data class TabContext(val ref: String, val tab: Tab, val title: String, val host: String, val text: String?)   // ref = "1".."N"
suspend fun collectTabsContext(tabs: TabManager, sites: SitePermissionRepository, extractLive: Boolean): List<TabContext>
// tab thường, có url, chưa chờ đóng, host không phải ASK; extractLive = true chỉ trích tab có WebView đang sống (không tạo WebView mới)
// ai/TabsOverview.kt
@Serializable data class TabsOverview(val intro: String, val groups: List<OverviewGroup>, val followUps: List<String>)
@Serializable data class OverviewGroup(val name: String, val summary: String, val tabIds: List<String>)
val TABS_OVERVIEW_SCHEMA: JsonOutputFormat.Schema   // viết tay theo researcher B §C14
fun parseTabsOverview(json: String, refs: Set<String>): TabsOverview?   // kiểm tabIds ⊆ refs, ref duy nhất, làm sạch chuỗi
fun sanitizeShortText(s: String, max: Int = 60): String?               // một dòng, bỏ URL/số giống điện thoại; null nếu không hợp lệ
// ai/TabGrouping.kt
@Serializable data class GroupingResult(val groups: List<ProposedGroup>); @Serializable data class ProposedGroup(val name: String, val tabIds: List<String>)
class TabGrouping(ai: SkimAiService, tabs: TabManager, tabRepo: TabRepository, sites: SitePermissionRepository, network: NetworkMonitor, appScope: CoroutineScope) {
    fun start(): Job                 // gọi khi mở 09; Job bị huỷ khi rời 09
    suspend fun undo(); suspend fun hideBanner(); val banner: Flow<GroupingBannerState?>
}
// ai/SkimAiService.kt (bổ sung)
fun overviewTabs(session: ChatSession, context: List<TabContext>, prompt: String): Flow<AiEvent>   // effort MEDIUM, format schema, mỗi tab một document không citations
fun askTabs(session: ChatSession, question: String): Flow<AiEvent>
suspend fun groupTabs(context: List<TabContext>): GroupingResult?                                   // effort LOW, chỉ title + host
// ui/ai
class AskTabsViewModel(key: AskTabsKey, ai: SkimAiService, sessions: ChatSessionStore, tabs: TabManager, sites: SitePermissionRepository,
                       network: NetworkMonitor, savedState: SavedStateHandle)
@Composable fun AskTabsSheet(state: AskTabsUiState, actions: AskTabsActions, localSnackbar: SnackbarHostState)
// ui/skimhistory
class SkimHistoryViewModel(chats: ChatRepository, eraser: AiDataEraser, snackbar: SnackbarController, undo: UndoRegistry, clock: () -> Long)
@Composable fun SkimHistoryScreen(state: SkimHistoryUiState, actions: SkimHistoryActions)
@Composable fun ClearSkimHistoryDialog(onClear: () -> Unit, onDismiss: () -> Unit)
```

- Mọi request 06b/gom nhóm gọi `SkimAiService.send(params, sources = context.map { AiSource(it.tab.id, it.tab.incognito, it.host) }, Consent.TABS_ACTION)` → chốt incognito và loại site `ASK`.
- Ngữ cảnh 06b: lượt người dùng đầu = một `DocumentBlockParam` mỗi tab (`source` là content source với các đoạn text, hoặc chỉ một dòng "No page text" khi tab không sống; `title` = title trang, `context` = `"ref {ref}, {host}"`, citations tắt), rồi block text cuối là prompt; `cache_control` ở document cuối. `tabIds` trong JSON là `ref` → map ngược về `Tab.id` sau khi kiểm.
- Gom nhóm: lúc gửi ghi `(tabSetKey, layoutVersion)`; khi có kết quả: `tabs.state.value.layoutVersion == layoutVersion` và `tabSetKey` tính lại bằng nhau thì `tabs.applyLayout(newLayout, expectedVersion = layoutVersion, snapshot = TabGroupingSnapshotEntity(previousLayoutJson, tabSetKey, appliedLayoutVersion = layoutVersion + 1, bannerVisible = true))`; không thì bỏ kết quả. Undo banner chỉ khi `layoutVersion == snapshot.appliedLayoutVersion`; khôi phục layout cũ qua cùng `applyLayout(expectedVersion)`.
- Link nhóm → `navigator.openTabs(incognito = false, focusTabId = firstTabIdOfGroup)`.
- Lưu chat TABS: message `TABS_OVERVIEW` giữ JSON overview đã làm sạch ở `text`, `suggestedQuestionsJson` giữ follow-ups; chat `tabCount = N`, `snippet = intro`.
- 09: `TabsViewModel` (phase 9) nhận `GroupingBannerState` từ `TabGrouping.banner`; route gọi `tabGrouping.start()` trong `LaunchedEffect` (bị huỷ khi rời 09) và `hideBanner()` khi rời.

Luồng dữ liệu 06b: hero 04 → `AskTabsKey(prompt)` → `collectTabsContext(extractLive = true)` (TabManager + quyền site; trích tab sống lúc bấm) → `SkimAiService.overviewTabs` (SDK, JSON schema) → `AiEvent.Final` → `parseTabsOverview` (kiểm + làm sạch) → UI + `ChatSessionStore` → Room. Gom nhóm: mở 09 → `TabGrouping.start` → title/host của tab hợp lệ → model → kiểm tranh chấp → `TabManager.applyLayout` (layout + snapshot một transaction) → banner.

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ai/TabsContext.kt`, `TabsOverview.kt`, `TabGrouping.kt` (cùng thư mục `ai/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/ai/AskTabsSheet.kt`, `AskTabsViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/skimhistory/SkimHistoryScreen.kt`, `SkimHistoryViewModel.kt`, `ClearSkimHistoryDialog.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/ai/TabsOverviewTest.kt` (schema có `additionalProperties:false` mọi object; parse hợp lệ; refusal/max_tokens → không parse; `tabIds` lạ hoặc trùng bị bỏ; followUp/tên nhóm có URL, số điện thoại, xuống dòng hoặc > 60 ký tự bị bỏ), `TabGroupingTest.kt` (điều kiện ≥ 4, một lần mỗi tập tab, body chỉ có title/host, loại site `ASK` và tab chờ đóng, kết quả bị bỏ khi `layoutVersion` đổi, huỷ khi rời 09, Undo khôi phục đúng layout và tắt khi layout đã đổi, offline bỏ qua), `TabsContextTest.kt` (loại incognito/New tab/site `ASK`/tab chờ đóng, cắt 4.000 ký tự, tab không sống chỉ title + host), `TabsRequestShapeTest.kt` (FakeClaudeServer: không có `citations` bật, mỗi tab một document, có `output_config.format.type == "json_schema"`, effort `medium` cho 06b và `low` cho gom nhóm, model `claude-haiku-5-5`, không `fallbacks`/`thinking`/header beta) (cùng thư mục `test/.../ai/`); `app/src/test/java/com/dantech/skimbrowser/ui/ai/AskTabsViewModelTest.kt`, `AskTabsScreenshotTest.kt` (`captureBoard("06b-ask-skim-from-home-tabs")`); `app/src/test/java/com/dantech/skimbrowser/ui/skimhistory/SkimHistoryViewModelTest.kt` (Undo sau khi rời màn; Undo sau Clear Skim history không hồi sinh), `SkimHistoryScreenshotTest.kt` (04c `captureBoard("04c-skim-history")`, rỗng, dialog Clear) — mỗi screenshot test `assertA11y()`
- Fixture SSE: `app/src/test/resources/sse/tabs-overview.sse`, `tab-grouping.sse`, `tabs-overview-injection.sse` (followUp chứa URL và số điện thoại, tên nhóm nhiều dòng, `tabIds` có ref không tồn tại)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/ai/SkimAiService.kt`: `overviewTabs`, `askTabs`, `groupTabs`.
- `app/src/main/java/com/dantech/skimbrowser/ai/ChatSessionStore.kt`: phiên `TABS` (mở mới, mở đã lưu, ghi Room).
- `app/src/test/java/com/dantech/skimbrowser/ai/IncognitoGuardTest.kt`: thêm ca `overviewTabs` và `groupTabs` với nguồn incognito và site `ASK` → không có request.
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/TabsViewModel.kt`, `app/src/main/java/com/dantech/skimbrowser/ui/tabs/TabsRoute.kt`: banner + `tabGrouping.start()` khi mở + ẩn banner khi rời.
- `app/src/test/java/com/dantech/skimbrowser/ui/tabs/TabsViewModelTest.kt`: cập nhật theo constructor có `TabGrouping`.
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: entry sheet `AskTabsKey`, màn `SkimHistoryKey`, dialog `ClearSkimHistoryKey`.
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: `tabGrouping`.

## Implementation steps

1. `TabsContext` + test (thứ tự: tab truy cập gần nhất, tối đa 40; trích chỉ tab sống, không tạo WebView mới).
2. `TabsOverview` (schema + data class + parse + `sanitizeShortText`) và `TabGrouping` (schema, policy, tranh chấp, apply/undo) + test; bổ sung `SkimAiService` và test request shape; mở rộng `IncognitoGuardTest`.
3. `ChatSessionStore` phiên TABS; `AskTabsViewModel` (cờ `openHandled`; prompt gửi ngay ở lần mở tường minh, câu trả lời đã lưu, hỏi tiếp stream text, copy/feedback, chip follow-ups, lỗi, link nhóm).
4. `AskTabsSheet` theo scout §2 06b (header như 06; thẻ trả lời; link nhóm `TextButton` cao 32 `primary` + `ic_chevron_right` 18; hàng copy/good/poor `CompactIconButton` 40 cd `cd_copy_answer`/`cd_good_answer`/`cd_poor_answer`; ô nhập `ai_ask_anything`; `SnackbarHost(localSnackbar)` trong sheet).
5. Sửa `TabsViewModel`/`TabsRoute` cho banner (D3) + `TabsViewModelTest`.
6. `SkimHistoryViewModel` (section theo `DateBuckets`, phụ đề theo quy tắc ở Requirements, tìm kiếm, Remove + Undo qua `UndoRegistry`, Delete all) + `SkimHistoryScreen` (`LargeTitleScaffold` hai action `ic_search` cd `cd_search_skim_history`, `ic_delete_sweep` cd `cd_delete_all_skim_history`) + `ClearSkimHistoryDialog`.
7. Đăng ký entry, nối `AppContainer`.
8. Ảnh Roborazzi 06b (fixture giống board: 7 tab, ba nhóm gồm Other, hai câu hỏi gợi ý), 04c (5 mục giống board), 04c rỗng, dialog Clear Skim history; duyệt so với PNG, ghi lệch vào `docs/design-guidelines.md`.
9. Commit cuối phase: `feat(ai): add multi-tab answers, tab grouping and skim history`.

## Todo list

- [ ] TabsContext (tab sống lúc bấm, loại site ASK/incognito/New tab/chờ đóng, cắt 4k) + test
- [ ] TabsOverview schema + parse + làm sạch; TabGrouping schema + policy D3 + tranh chấp + apply/Undo + test
- [ ] SkimAiService overviewTabs/askTabs/groupTabs (mỗi tab một document, Consent.TABS_ACTION) + test request shape + IncognitoGuardTest mở rộng
- [ ] AskTabsViewModel + AskTabsSheet 06b (prompt, đã lưu, hỏi tiếp, link nhóm, chip, copy cục bộ/feedback, lỗi, không gửi lại khi khôi phục)
- [ ] Banner 09 nối TabGrouping (huỷ khi rời) + TabsViewModelTest
- [ ] 04c Skim history (sections, phụ đề plural, search, Remove + Undo qua registry, rỗng, Delete all → dialog)
- [ ] Đăng ký entry AskTabsKey, SkimHistoryKey, ClearSkimHistoryKey
- [ ] Ảnh Roborazzi 06b, 04c, 04c rỗng, dialog (+ `assertA11y`, lệch ghi docs)
- [ ] Commit cuối phase

## Success criteria

- Mọi row của 06b, 04c; row 04 "Summarize my N open tabs", "See all", "Ask Skim"; row 04b "Ask Skim row"; row 09 "Undo in the banner", "Ask Skim about these tabs" chạy đúng (coverage-matrix.md). Overlay Clear Skim history, snackbar "Removed from history" (04c) và "Copied to clipboard" (06b) đúng dialogs.md.
- `TabsRequestShapeTest`, `TabGroupingTest`, `IncognitoGuardTest` xanh: request gom nhóm không chứa text trang; không request nào có tab incognito hay site `ASK`.
- Trên emulator với key debug: 7 tab thường → hero → câu trả lời nhóm theo chủ đề; bấm link nhóm → 09 cuộn đúng section; mở 09 với ≥ 4 tab chưa nhóm → banner + Undo khôi phục thứ tự cũ; kéo một tab trong lúc chờ kết quả gom nhóm → kết quả bị bỏ, thứ tự của người dùng giữ nguyên.

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ai.*' --tests 'com.dantech.skimbrowser.ui.ai.*' --tests 'com.dantech.skimbrowser.ui.skimhistory.*' --tests 'com.dantech.skimbrowser.ui.tabs.*'
./gradlew :app:recordRoborazziDebug --tests '*AskTabsScreenshotTest*' --tests '*SkimHistoryScreenshotTest*' && ./gradlew :app:verifyRoborazziDebug
grep -rn "PageDigestCache" app/src   # phải rỗng
```

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Model trả JSON đúng schema | Parse lỗi (refusal, `max_tokens`, enum sai hoa thường) | Chỉ parse khi `end_turn`; lỗi → thẻ Server error (D8); gom nhóm lỗi → bỏ qua im lặng |
| Chỉ title + host cho tab không sống vẫn cho câu trả lời hữu ích | Câu trả lời chung chung cho tab cũ | Chấp nhận (D3 đã chốt: không đọc nền); người dùng mở tab để có text đầy đủ |
| Document không bật citations dùng được cùng structured output | HTTP 400 | Gửi text block (vẫn tách mỗi tab một block có nhãn cố định) thay cho document; test request shape cập nhật |
| Gom nhóm tự động được người dùng chấp nhận (D3) | Phản hồi tiêu cực | Undo trên banner; chính sách nằm ở một chỗ (`TabGrouping.start`) |
| Schema vượt giới hạn (24 optional, 16 union) | HTTP 400 | Schema không có trường optional/union; test kiểm cấu trúc |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 12 (gỡ entry 06b/04c/dialog và phần banner trong `TabsViewModel`; các file `ai/*` mới độc lập).

## Security considerations

- Dữ liệu gửi đi cho 06b: title, host và (với tab đang sống) ≤ 4.000 ký tự text mỗi tab thường không thuộc site `ASK`, chỉ sau hành động tường minh của người dùng (D3); gom nhóm chỉ title + host.
- Không có trích text nền; text chỉ nằm trong bộ nhớ của request đang chạy.
- Đầu ra của model được kiểm và làm sạch trước khi thành chip bấm được hay tên nhóm lưu vĩnh viễn.
- Xoá Skim history (04c) giữ quyền site; Delete all AI data (10) xoá cả dữ liệu AI theo site (phase 8).

## Next steps

- Phase 13: duyệt tổng hợp parity cho 06b/04c/09 banner, kiểm process death với sheet 06b đang mở (không gửi lại request).
