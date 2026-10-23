---
phase: 8
title: "Settings, AI & privacy, History, Bookmarks"
status: pending
priority: P2
effort: "20h"
dependencies: [5, 7]
---

# Phase 8: Settings, AI & privacy, History, Bookmarks

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Scout `plans/reports/scout-261020-2319-design-inventory.md` §2 (10, 12, 13, 14, 15), §3(d) (Search engine, Theme, Clear browsing data, Delete all AI data, Bookmark menu, snackbar Removed from history, Bookmark deleted), §3(e) #6, #15.
- Red team (`plans/261020-1611-skim-design-implementation/reports/`): `red-team-security-adversary.md` Finding 10; `red-team-failure-mode-analyst.md` Finding 7, 8; `red-team-scope-complexity-critic.md` Finding 8, 9, 10.
- `design/spec/navigation-map.md` mục 12, 10, 13, 14; `design/spec/dialogs.md`.
- Researcher A §10 (AboutLibraries `produceLibraries(R.raw.aboutlibraries)` + `LibrariesContainer`).
- Board: `design/screens/12-settings.png`, `10-ai-privacy-settings.png`, `13-history.png`, `14-bookmarks.png`, `15-dialogs-menus-snackbars.png` (trong `design/screens/`).

## Overview

Xây bốn màn danh sách dùng `LargeTitleScaffold` + segmented list: 12 Settings, 10 AI & privacy, 13 History, 14 Bookmarks (gốc và thư mục đã mở), cùng các dialog Search engine, Theme, Clear browsing data, Delete all AI data, menu Bookmark options, và màn Open-source licenses (AboutLibraries). Xoá dữ liệu phải thật sự xoá ("from all time") và không đua với Undo đang chờ.

## Key insights

- Nút xác nhận phá huỷ: chỉ "Delete" của Delete all AI data dùng màu `error`; "Clear" dùng `primary` (mâu thuẫn #6). Chữ hàng Delete ở 10 là `error` 500, còn chữ Clear browsing data ở 12/13 là `onSurface` (scout §2 10).
- Casing khác nhau giữa 01 ("Terms of Service") và 12 ("Terms of service") là chủ ý: hai cặp string riêng (mâu thuẫn #15).
- Hàng có switch ở 10: cả hàng 72 là một `toggleable` (role Switch, label = text hàng) thay cho `aria-labelledby` (scout §2 10).
<!-- Red Team: Một cơ chế xoá chat trong bộ nhớ — 2026-10-21 -->
- Xoá chat chỉ đi qua một cơ chế: `ChatRepository.deleteAll()` phát `removals(null)`; `ChatSessionStore` (phase 11) là nơi duy nhất gỡ phiên trong bộ nhớ và `chatId` của tab. `AiDataEraser` không tự gọi `bindChat`.
<!-- Red Team: Undo đua với Delete all / Clear data — 2026-10-21 -->
- Xoá hàng loạt huỷ mọi Undo đang chờ cùng loại (`UndoRegistry.cancelAll`) và đóng snackbar Undo đang hiện (`SnackbarController.dismissFor`), để Undo không hồi sinh dữ liệu vừa "xoá hết".
<!-- Red Team: Clear browsing data để lại dấu vết — 2026-10-21 -->
- "Removes data saved on this phone, from all time" nghĩa là xoá luôn back/forward list của tab (WebView đang sống `clearHistory()`, xoá file `tabstate/`), thumbnail, snapshot gom nhóm; cookie phải `flush()` và tab đang sống được tải lại để không ghi lại cookie cũ; DB checkpoint sau khi xoá.
- Nhóm ngày của 13 dùng cùng `DateBuckets` với 04c (Today, Yesterday, Earlier this week, Earlier) để không tạo thêm copy.

## Requirements

Chức năng:
- 12: General (Search engine + giá trị hiện tại; Default browser + "Skim is your default" hoặc copy D8 khi chưa default; Theme + giá trị), Skim AI (AI & privacy + "Asks before reading new sites" hoặc copy D8 khi ask-before tắt), Privacy (Clear browsing data), About (Privacy policy, Terms of service → mở tab mới; Open-source licenses → danh sách AboutLibraries; Version `versionName (versionCode)` không bấm được). Default browser: đang không default → hộp thoại role/Android default apps; đã default → Android default apps (quyết định nhỏ của planner). Kiểm lại trạng thái ở `ON_RESUME`.
- Dialog Search engine (bấm = lưu + đóng; Cancel), Theme (áp dụng ngay; Cancel), Clear browsing data (Browsing history ✓, Cookies and site data ✓ + "Signs you out of most sites", Cached images and files ✗ mỗi lần mở — quyết định nhỏ đã chốt; Clear tắt khi không chọn gì; footnote "Skim history is managed in AI & privacy.").
- 10: Ask before sending (switch), Keep chat history (switch; tắt thì chat mới không được lưu), Skim history + "N conversations on this phone" → 04c, Delete all AI data → dialog (icon `ic_delete` `error`, tiêu đề căn giữa, Cancel, Delete `error`) → xoá Skim history + xoá dữ liệu AI của mọi site (lần sau mọi site hỏi lại); link "How Skim AI handles your data" → tab mới.
- 13: Back; Search (ô thay tiêu đề, lọc tại chỗ theo title/URL); hàng Clear browsing data → dialog; mục history (Cookie4Sided chữ cái `onSecondaryContainer`, title, host, nút `ic_close` cd `cd_remove_from_history`) → mở ở tab hiện tại; Remove → snackbar "Removed from history" + Undo (qua `UndoRegistry`, loại `HISTORY`); không bao giờ có trang incognito (đảm bảo ở phase 4). Rỗng → `history_empty` `[DEFAULT — chờ validate]` D8.
- 14: Folders (Mobile bookmarks + mỗi thư mục Imported; tile 40 bo 12 `secondaryContainer` `ic_folder_filled`; phụ đề `bookmarks_count` hoặc `bookmarks_imported_count_from`), Recently added (Cookie4Sided chữ `onSurfaceVariant`, nút `ic_more_vert` cd `cd_bookmark_options`); thư mục → cùng layout với tên thư mục làm tiêu đề (thư mục con trong mục Folders, rồi danh sách bookmark không nhãn); bookmark → mở ở tab hiện tại; Bookmark options → menu [Edit, Move to folder] [Delete] (Edit → `EditBookmarkKey(id)`, Move → `MoveToFolderKey(id)` của phase 7; Delete xoá ngay + snackbar "Bookmark deleted" + Undo loại `BOOKMARK`); Search → tìm mọi thư mục. Rỗng → `bookmarks_empty` (D8).
- Licenses: `LargeTitleScaffold` tiêu đề `settings_open_source_licenses` + `LibrariesContainer` (gồm định nghĩa tuỳ chỉnh của phase 1: Google Sans Flex, Material Symbols, Readability.js; và dependency transitive của SDK Anthropic).

Phi chức năng:
- Danh sách 13 giới hạn 1000 mục mới nhất (phase 3), tìm kiếm quét toàn bảng.
- Mọi hàng ≥ 56/72 như bản vẽ, vùng chạm ≥ 48.

## Architecture

```kotlin
// ui/settings
class SettingsViewModel(settings: SettingsRepository, defaultBrowser: DefaultBrowser)   // state: engine, theme, isDefault, askBeforeSending, version
@Composable fun SettingsScreen(state: SettingsUiState, actions: SettingsActions)
@Composable fun SearchEngineDialog(selected: SearchEngine, onSelect: (SearchEngine) -> Unit, onDismiss: () -> Unit)   // ChoiceDialog
@Composable fun ThemeDialog(selected: ThemeSetting, onSelect: (ThemeSetting) -> Unit, onDismiss: () -> Unit)
@Composable fun ClearBrowsingDataDialog(onClear: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit, onDismiss: () -> Unit)
class AiPrivacyViewModel(settings: SettingsRepository, chats: ChatRepository, eraser: AiDataEraser)
@Composable fun AiPrivacyScreen(state: AiPrivacyUiState, actions: AiPrivacyActions)
@Composable fun DeleteAiDataDialog(onDelete: () -> Unit, onDismiss: () -> Unit)
@Composable fun LicensesScreen(onBack: () -> Unit)
// ui/history
class HistoryViewModel(history: HistoryRepository, snackbar: SnackbarController, undo: UndoRegistry, clock: () -> Long)   // sections theo DateBuckets
// ui/bookmarks
class BookmarksViewModel(folderId: Long?, bookmarks: BookmarkRepository, snackbar: SnackbarController, undo: UndoRegistry)
@Composable fun BookmarkMenu(expanded: Boolean, onEdit: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit)
// ai/AiDataEraser.kt
class AiDataEraser(chats: ChatRepository, sites: SitePermissionRepository, undo: UndoRegistry, snackbar: SnackbarController) {
    suspend fun deleteAllAiData()     // cancelAll(SKIM_CHAT) + dismissFor(SKIM_CHAT) + chats.deleteAll() + sites.deleteAllSkimAiData()
    suspend fun clearSkimHistory()    // cancelAll(SKIM_CHAT) + dismissFor(SKIM_CHAT) + chats.deleteAll(); giữ quyền site
}
// browser/BrowsingDataCleaner.kt (bổ sung; constructor đã định nghĩa ở phase 7)
suspend fun clearAll(history: Boolean, cookies: Boolean, cache: Boolean, engine: TabEngine?)
```

<!-- Red Team: Clear browsing data xoá trọn — 2026-10-21 -->
`clearAll` (chỉ profile mặc định; phiên incognito có cơ chế riêng ở phase 4):
- `history`: `undo.cancelAll(HISTORY)` + `snackbar.dismissFor(HISTORY)`; `HistoryRepository.clearAll()` (kèm checkpoint); với mọi tab thường đang sống `engine.clearHistory(tabId)` (WebView `clearHistory()`), xoá mọi file `tabstate/` của tab thường (tab bị evict sẽ tải lại từ URL), `thumbnails.clearAll()`, `tabRepo.clearSnapshot()`.
- `cookies`: `CookieManager.getInstance().removeAllCookies { flush() }` (chờ callback), `WebStorage.getInstance().deleteAllData()`, `GeolocationPermissions.getInstance().clearAll()`, rồi `reload` các tab thường đang sống để trang không ghi lại cookie từ bộ nhớ.
- `cache`: `engine.withTemporaryWebView(profile = null) { it.clearCache(true) }` (WebView tạm của profile mặc định, kể cả khi WebView sống duy nhất là incognito); không có engine → làm ở lần Activity attach kế tiếp.
- `engine` lấy từ `LocalBrowserHost.current.pool` tại màn gọi (truyền theo lời gọi, cleaner không giữ tham chiếu Activity).

- Mở URL ở tab hiện tại (13, 14): `tabs.load(currentTabId, url)` rồi `navigator.showBrowser()`. URL pháp lý (12, 10): `navigator.openNewTab(incognito = false, url = BuildConfig.X_URL)`; URL rỗng (debug) → hàng không bấm được.
- Undo (13, 14): `val e = history.remove(id); val t = undo.register(HISTORY, undo = { history.restore(e) }); snackbar.show(snackbar_removed_from_history, SnackbarAction.Undo(t))` (tương tự bookmark).
- Entry đăng ký: `SettingsKey`, `AiPrivacyKey`, `HistoryKey`, `BookmarksKey`, `LicensesKey` (màn hình); `SearchEngineDialogKey`, `ThemeDialogKey`, `ClearBrowsingDataKey`, `DeleteAiDataKey` (dialog). Menu Bookmark options là UI state của 14.

Luồng dữ liệu xoá: dialog → ViewModel → `BrowsingDataCleaner`/`AiDataEraser` → `UndoRegistry` (huỷ token) + Room/WebView storage → `Flow` cập nhật danh sách (13 rỗng, số conversation ở 10 về 0, `ChatRepository.removals` → badge chat trên tab card biến mất).

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsDialogs.kt`, `ClearBrowsingDataDialog.kt`, `AiPrivacyScreen.kt`, `AiPrivacyViewModel.kt`, `DeleteAiDataDialog.kt`, `LicensesScreen.kt` (cùng thư mục `ui/settings/`)
- `app/src/main/java/com/dantech/skimbrowser/ui/history/HistoryScreen.kt`, `HistoryViewModel.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/bookmarks/BookmarksScreen.kt`, `BookmarksViewModel.kt`, `BookmarkMenu.kt`
- `app/src/main/java/com/dantech/skimbrowser/ai/AiDataEraser.kt`
- Test: `app/src/test/java/com/dantech/skimbrowser/ui/settings/SettingsViewModelTest.kt`, `AiPrivacyViewModelTest.kt`, `ClearBrowsingDataDialogTest.kt` (Clear tắt khi bỏ chọn hết), `SettingsScreenshotTest.kt` (12, 10, bốn dialog; `captureBoard("12-settings")`, `captureBoard("10-ai-privacy-settings")`); `app/src/test/java/com/dantech/skimbrowser/ai/AiDataEraserTest.kt`; `app/src/test/java/com/dantech/skimbrowser/browser/BrowsingDataCleanerTest.kt` (với `FakeTabEngine`: sau `clearAll(history = true)` không còn file trong `tabstate/` và `thumbs/`, snapshot bị xoá, `clearHistory` được gọi cho tab sống, token HISTORY bị huỷ); `app/src/test/java/com/dantech/skimbrowser/ui/history/HistoryViewModelTest.kt` (Undo sau khi rời màn, Undo sau khi URL được truy cập lại, Undo sau Clear browsing data không làm gì), `HistoryScreenshotTest.kt` (`captureBoard("13-history")`); `app/src/test/java/com/dantech/skimbrowser/ui/bookmarks/BookmarksViewModelTest.kt`, `BookmarksScreenshotTest.kt` (14 gốc `captureBoard("14-bookmarks")`, thư mục Imported đã mở, menu) — mọi screenshot test gọi `assertA11y()`

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/browser/BrowsingDataCleaner.kt`: thêm `clearAll`.
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`: 9 entry ở trên.
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: thêm `aiDataEraser`.

## Implementation steps

1. `SettingsViewModel` + `SettingsScreen` theo scout §2 12 (tile Circle 40: `ic_search` `secondaryContainer`, `ic_public` `primaryContainer`, `ic_palette` `secondaryContainer`, Sunny cho AI & privacy, `errorContainer` `ic_delete` cho Clear browsing data với chữ `onSurface`, About dùng `surfaceContainer`/`onSurfaceVariant`; Version có giá trị trailing `bodyMedium`, không `onClick`).
2. `SettingsDialogs.kt` dùng `ChoiceDialog` (phase 2); tên engine từ `search_engine_*`, theme từ `theme_*`.
3. `ClearBrowsingDataDialog` (checkbox 3 hàng, phụ đề hàng cookies, footnote, Clear `primary` tắt khi không chọn) + `BrowsingDataCleaner.clearAll` (dùng `TabEngine.clearHistory` đã có từ phase 4); entry dùng chung từ 12 và 13.
4. `AiDataEraser` + test (token SKIM_CHAT bị huỷ và Undo sau đó không hồi sinh chat; `removals(null)` phát; dữ liệu AI của site bị xoá chỉ với `deleteAllAiData`).
5. `AiPrivacyScreen` + ViewModel theo scout §2 10 (switch `SkimSwitch` + `toggleableRow`; hàng Skim history tile Sunny, phụ đề plural `ai_settings_conversations`, không chevron; hàng Delete `error` 500; footer `ic_info` + text + link `primary` 600).
6. `DeleteAiDataDialog` (`SkimAlertDialog(icon = ic_delete, iconTint = error, destructiveConfirm = true)`).
7. `HistoryViewModel` + `HistoryScreen` (hàng Clear browsing data trên cùng, margin 8 16 0; section theo `DateBuckets`; search thay tiêu đề; Remove + Undo qua registry).
8. `BookmarksViewModel` + `BookmarksScreen` (gốc/thư mục/search) + `BookmarkMenu` (`SkimMenu`: [Edit `ic_edit`, Move to folder `ic_drive_file_move`], [Delete `ic_delete`], cd neo `cd_bookmark_options`).
9. `LicensesScreen` với `produceLibraries(R.raw.aboutlibraries)`; kiểm release giữ được `R.raw.aboutlibraries` (keep của phase 1).
10. Đăng ký entry, nối `AppContainer`.
11. Ảnh Roborazzi với fixture giống board; duyệt so với PNG; lệch ghi vào `docs/design-guidelines.md`.
12. Commit cuối phase: `feat(settings): add settings, ai privacy, history and bookmarks`.

## Todo list

- [ ] 12 Settings + ViewModel (engine, default browser, theme, AI & privacy, clear data, About, Version)
- [ ] Dialog Search engine, Theme (ChoiceDialog)
- [ ] Dialog Clear browsing data + BrowsingDataCleaner.clearAll (tabstate, thumbs, snapshot, clearHistory, flush + reload, cache qua WebView tạm, huỷ Undo HISTORY)
- [ ] AiDataEraser (huỷ Undo SKIM_CHAT, một cơ chế xoá chat) + test
- [ ] 10 AI & privacy + dialog Delete all AI data
- [ ] 13 History (sections, search, remove + Undo qua registry, mở ở tab hiện tại, rỗng D8)
- [ ] 14 Bookmarks (gốc, thư mục, search, menu, delete + Undo qua registry, rỗng D8)
- [ ] Licenses (AboutLibraries)
- [ ] Đăng ký 9 entry
- [ ] Test ViewModel/cleaner + ảnh Roborazzi 12/10/13/14 + dialog/menu (+ `assertA11y`, lệch ghi docs)
- [ ] Commit cuối phase

## Success criteria

- Mọi row của 12, 10, 13, 14 trong navigation-map.md chạy đúng (row 10 "Skim history" mở 04c, end-to-end khi phase 12 xong).
- Overlay Search engine, Theme, Clear browsing data, Delete all AI data, Bookmark menu và snackbar Removed from history (13), Bookmark deleted đúng dialogs.md; Undo vẫn chạy sau khi rời màn, không crash khi URL vừa được truy cập lại, và không hồi sinh dữ liệu sau khi xoá hàng loạt.
- Đổi Theme sang Dark làm mọi màn thường chuyển dark scheme ngay (dark theme cho màn thường thuộc phạm vi).
- Sau Clear browsing data (history): Back trong tab cũ không còn quay về trang đã xem trước đó; 09 không còn thumbnail cũ.
- Sau Delete all AI data: 10 hiện "0 conversations…", 04c rỗng, lần dùng AI kế tiếp trên site cũ hiện lại 06d (khi ask-before bật).

## Verification

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.ui.settings.*' --tests 'com.dantech.skimbrowser.ui.history.*' --tests 'com.dantech.skimbrowser.ui.bookmarks.*' --tests 'com.dantech.skimbrowser.ai.AiDataEraserTest' --tests 'com.dantech.skimbrowser.browser.BrowsingDataCleanerTest'
./gradlew :app:recordRoborazziDebug --tests '*SettingsScreenshotTest*' --tests '*HistoryScreenshotTest*' --tests '*BookmarksScreenshotTest*'
./gradlew :app:verifyRoborazziDebug
./gradlew :app:assembleRelease -Pskim.aiProxyUrl=https://proxy.example.invalid -Pskim.termsUrl=https://example.invalid/terms -Pskim.privacyUrl=https://example.invalid/privacy -Pskim.aiDataPolicyUrl=https://example.invalid/ai-data   # release giữ R.raw.aboutlibraries
```

Thủ công trên emulator: Clear browsing data với Cookies → site đang đăng nhập bị đăng xuất, kể cả sau khi kill app ngay rồi mở lại; Licenses hiển thị Google Sans Flex (OFL-1.1), Material Symbols (Apache-2.0), Readability.js (Apache-2.0), anthropic-java, OkHttp, Jackson.

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| `clearCache(true)` trên WebView tạm của profile mặc định xoá cache dùng chung | Ảnh cũ vẫn tải từ cache | Thêm `profile.httpCache` clear khi có `HTTP_CACHE_MANAGER` `[UNVERIFIED tên API]` |
| AboutLibraries giữ được JSON ở release | Màn Licenses rỗng ở release | Kiểm `res/raw/keep.xml`; kiểm bằng `apkanalyzer resources` ở phase 13 |
| Định nghĩa thư viện tuỳ chỉnh được plugin đọc | Thiếu 3 mục tuỳ chỉnh | Sửa đường dẫn/format theo README AboutLibraries 15.3.0 |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 8 (gỡ entry, package mới, `clearAll` là bổ sung).

## Security considerations

- Clear browsing data chỉ chạm profile mặc định; incognito có cơ chế riêng.
- Delete all AI data xoá mọi chat và message (kể cả phiên chỉ trong bộ nhớ, qua `ChatRepository.removals`) và xoá dữ liệu AI của mọi site; không thể hoàn tác (đúng copy); Undo SKIM_CHAT đang chờ bị huỷ.
- `secure_delete` + `wal_checkpoint(TRUNCATE)` (phase 3) để dữ liệu đã xoá không còn trong file DB.
- Link pháp lý luôn mở tab thường.

## Next steps

- Phase 12 dùng `AiDataEraser.clearSkimHistory()` cho dialog Clear Skim history và cùng mẫu Undo cho 04c.
