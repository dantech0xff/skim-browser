---
phase: 3
title: "Data layer"
status: completed
priority: P1
effort: "14h"
dependencies: [1]
---

# Phase 3: Data layer

Mọi đường dẫn tính từ gốc repo `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser/`.

## Context links

- Thực thể dữ liệu: `plans/reports/scout-261020-2319-design-inventory.md` §3(c), §3(d) (Undo trên từng overlay).
- Room/DataStore: `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md` §7.
- Tìm kiếm: `plans/reports/researcher-261020-2319-webview-engine-claude-sdk.md` §B9 (bảng endpoint).
- Red team: `plans/261020-1611-skim-design-implementation/reports/red-team-failure-mode-analyst.md` Finding 8, 9; `red-team-security-adversary.md` Finding 10 và Fact Checker (Phase 3); `red-team-assumption-destroyer.md` Finding 9; `red-team-scope-complexity-critic.md` Finding 9.
- Hành vi: `design/spec/navigation-map.md` (04c, 10, 13, 14, 15), `design/spec/dialogs.md`.

## Overview

Xây lớp dữ liệu Room 2.8.5 (schema v1, export JSON) và DataStore Preferences cho cài đặt, cùng repository có API `Flow` và hỗ trợ Undo cho mọi thao tác xoá mà thiết kế có snackbar Undo. Lớp này không biết tới WebView hay incognito: phần gọi (TabManager, AI) chịu trách nhiệm không bao giờ ghi dữ liệu incognito. `AppContainer` nhận vị trí lưu trữ qua tham số và có `close()` để test dựng container trên thư mục tạm.

## Key insights

- Thiết kế có Undo cho: đóng tab, đóng nhiều tab, đóng nhóm, xoá history, xoá Skim history, xoá bookmark, xoá shortcut, và Undo gom nhóm trên banner 09. Hàm xoá trả bản ghi đã xoá; caller đăng ký hàm khôi phục vào `UndoRegistry` (phase 2), chạy trên `appScope`.
<!-- Red Team: Undo đụng UNIQUE khi URL được truy cập lại — 2026-10-21 -->
- `history.url` là unique: nếu URL được truy cập lại trong lúc Undo còn hiện, chèn lại id cũ sẽ vỡ ràng buộc. `HistoryRepository.restore` upsert theo `url` và giữ `max(visitedAt)`.
- Tab đóng có Undo 10 s; nếu process chết trong khoảng đó, tab không được sống lại: dùng cột `closedAt`, lọc `closedAt IS NULL`, và xoá hẳn các dòng `closedAt != null` khi khởi động.
<!-- Red Team: chatId âm sống qua process death — 2026-10-21 -->
- `tabs.chatId` không đặt FK và không bao giờ nhận id âm (chat chỉ trong bộ nhớ khi tắt Keep chat history): `TabRepository` ghi `null` thay cho id âm, và `loadOpen()` đặt `chatId = NULL` cho mọi id không còn trong `skim_chats`.
- Đếm bookmark của thư mục Imported phải gồm thư mục con (14: "24 bookmarks from bookmarks.html"): tính đệ quy bằng Kotlin từ hai truy vấn nhỏ (thư mục + đếm theo `folderId`), không cần CTE.
- Tab dùng id `String` (UUID) để tab incognito (chỉ trong bộ nhớ) và tab thường không đụng id.
<!-- Red Team: Dấu vết dữ liệu sau khi xoá — 2026-10-21 -->
- Xoá phải thật sự xoá: bật `PRAGMA secure_delete = ON` khi mở DB và chạy `PRAGMA wal_checkpoint(TRUNCATE)` sau mỗi lần xoá hàng loạt (Clear browsing data, Delete all AI data, Clear Skim history).
- Fact Checker ghi nhận scout định nghĩa `SitePermission` theo origin. Quyết định của plan: **giữ `host` (bỏ `www.`, lowercase) làm khoá chính**, gộp scheme và port. Lý do: copy 06d nói "once for this site", site information hiển thị theo host, và geolocation chỉ được WebView hỏi với origin HTTPS. Hệ quả chấp nhận: `http://` và `https://` cùng host dùng chung quyết định.
- App chưa phát hành: schema v1, không có migration; schema JSON được commit để các migration sau dùng `AutoMigration`.

## Requirements

Chức năng:
- Bảng: `tabs`, `tab_groups`, `tab_grouping_snapshot`, `history`, `bookmark_folders`, `bookmarks`, `shortcuts`, `skim_chats`, `skim_messages`, `site_permissions`.
- Thư mục hệ thống "Mobile bookmarks" (id 1, `kind = MOBILE`) được seed khi tạo DB.
- Cài đặt: search engine (Google mặc định), theme (System), ask before sending (bật), keep chat history (bật), reader text scale (100%), reader font (Sans serif), onboarding completed (tắt), last tab id, install id.
- Repository Flow cho mọi danh sách mà UI hiển thị; hàm xoá trả bản ghi để Undo; khôi phục an toàn với ràng buộc unique.
- Một tín hiệu xoá chat duy nhất (`ChatRepository.removals`) cho mọi bộ nhớ chat phía trên (phase 11).

Phi chức năng:
- Mọi truy vấn chạy ngoài main thread (DAO `suspend` + `Flow`).
- Không có dữ liệu mẫu trong DB; danh sách rỗng khi mới cài.
- `AppContainer` dựng được trên thư mục tạm và đóng được (test khôi phục ở phase 13).

## Architecture

Thực thể (Room; enum lưu dạng TEXT bằng hỗ trợ enum sẵn có của Room):

| Entity | Trường |
|---|---|
| `TabEntity` (`tabs`) | `id: String` PK, `url: String?` (null = trang New tab), `title: String`, `groupId: Long?` (FK `tab_groups`, SET NULL), `position: Int`, `desktopSite: Boolean`, `openedByExternalApp: Boolean`, `closeOnBack: Boolean`, `fromNewTabPage: Boolean` (tab bắt đầu từ trang New tab: Back ở trang đầu quay về New tab), `chatId: Long?` (không FK; chỉ id dương của chat đã lưu), `createdAt`, `lastAccessedAt: Long`, `closedAt: Long?` |
| `TabGroupEntity` (`tab_groups`) | `id: Long` PK auto, `title: String`, `position: Int`, `createdBySkim: Boolean` |
| `TabGroupingSnapshotEntity` (`tab_grouping_snapshot`) | `id: Int` PK (luôn 1), `previousLayoutJson: String`, `tabSetKey: String`, `appliedLayoutVersion: Long`, `bannerVisible: Boolean`, `createdAt: Long` |
| `HistoryEntryEntity` (`history`) | `id` PK auto, `url` (unique index), `title`, `host`, `visitedAt` (index) |
| `BookmarkFolderEntity` (`bookmark_folders`) | `id` PK auto, `parentId: Long?` (FK self, CASCADE), `name: String?` (null cho thư mục hệ thống), `kind: FolderKind {MOBILE, IMPORTED, FOLDER}`, `sourceFileName: String?`, `position`, `createdAt` |
| `BookmarkEntity` (`bookmarks`) | `id` PK auto, `folderId` (FK CASCADE, index), `title`, `url` (index), `host`, `position`, `addedAt` (index) |
| `ShortcutEntity` (`shortcuts`) | `id` PK auto, `name`, `url`, `position` |
| `SkimChatEntity` (`skim_chats`) | `id` PK auto, `kind: ChatKind {PAGE, TABS}`, `url: String?` (URL trang bỏ fragment), `host: String?`, `title: String`, `tabCount: Int`, `snippet: String?`, `createdAt`, `updatedAt` (index) |
| `SkimMessageEntity` (`skim_messages`) | `id` PK auto, `chatId` (FK CASCADE, index), `role {USER, ASSISTANT}`, `kind {QUESTION, SUMMARY, ANSWER, TABS_OVERVIEW}`, `text`, `citationsJson: String?`, `feedback {NONE, GOOD, POOR}`, `status {COMPLETE, STOPPED, REFUSED, ERROR}`, `suggestedQuestionsJson: String?`, `createdAt` |
| `SitePermissionEntity` (`site_permissions`) | `host: String` PK (quyết định ở Key insights), `skimAi: SkimAiState {UNSET, ALLOWED, ASK}`, `location/camera/microphone: PermissionState {UNSET, ALLOWED, BLOCKED}`, `updatedAt` |

<!-- Updated: Validation Session 1 - D3 dùng skimAi == ASK để loại site khỏi 06b/gom nhóm -->
Ngữ nghĩa `skimAi` (dùng ở phase 7, 11, 12): trang cần hỏi (06d) khi `ASK`, hoặc khi `UNSET` và `askBeforeSending` bật. Allow ở 06d → `ALLOWED`. Switch Skim AI trong Site information: tắt → `ASK` (lần sau 06d hỏi lại, quyết định nhỏ đã chốt), bật → `ALLOWED`. Site `ASK` bị loại khỏi 06b và gom nhóm (D3). "Delete all AI data" → xoá dữ liệu AI của mọi site (bên dưới).

Repository (`com.dantech.skimbrowser.data.repo`), chữ ký chính:

```kotlin
class TabRepository(db: SkimDatabase) {
    suspend fun loadOpen(): Pair<List<TabEntity>, List<TabGroupEntity>>   // xoá dòng closedAt != null; chatId không còn trong skim_chats → NULL
    suspend fun upsert(tab: TabEntity)                                    // chatId < 0 được ghi thành NULL
    suspend fun markClosed(ids: List<String>, at: Long); suspend fun reopen(ids: List<String>); suspend fun deleteClosed(ids: List<String>)
    suspend fun saveLayout(groups: List<TabGroupEntity>, placements: List<TabPlacement>, snapshot: TabGroupingSnapshotEntity? = null)  // một transaction
    suspend fun snapshot(): TabGroupingSnapshotEntity?; suspend fun setSnapshotBanner(visible: Boolean); suspend fun clearSnapshot()
}
class HistoryRepository(dao: HistoryDao, db: SkimDatabase) {
    suspend fun recordVisit(url: String, title: String, at: Long)    // bỏ qua scheme khác http/https; upsert theo url
    suspend fun updateTitle(url: String, title: String)
    fun observe(query: String?): Flow<List<HistoryEntryEntity>>       // mới nhất trước, giới hạn 1000
    suspend fun matches(text: String, limit: Int): List<HistoryEntryEntity>
    suspend fun remove(id: Long): HistoryEntryEntity?
    suspend fun restore(entry: HistoryEntryEntity)                    // upsert theo url, visitedAt = max(cũ, hiện có)
    suspend fun clearAll()                                            // + wal_checkpoint(TRUNCATE)
}
class BookmarkRepository(dao: BookmarkDao) {
    fun observeRoot(): Flow<BookmarksRoot>            // thư mục cấp 1 kèm số đếm đệ quy + "Recently added" (10 mới nhất)
    fun observeFolder(id: Long): Flow<FolderContent>   // thư mục con + bookmark
    fun observe(id: Long): Flow<BookmarkEntity?>
    fun search(query: String): Flow<List<BookmarkEntity>>
    fun observeByUrl(url: String): Flow<BookmarkEntity?>
    suspend fun addToMobile(title: String, url: String): Long
    suspend fun update(id: Long, title: String, url: String)
    suspend fun move(id: Long, folderId: Long); suspend fun topLevelFolders(): List<BookmarkFolderEntity>
    suspend fun delete(id: Long): BookmarkEntity?
    suspend fun restore(b: BookmarkEntity)            // thư mục cũ không còn → về Mobile bookmarks
    suspend fun importTree(fileName: String, nodes: List<ImportedNode>): ImportOutcome   // tạo thư mục IMPORTED mới
    suspend fun matches(text: String, limit: Int): List<BookmarkEntity>
}
class ShortcutRepository(dao: ShortcutDao) { fun observe(): Flow<List<ShortcutEntity>>; suspend fun add(name: String, url: String)
    suspend fun update(id: Long, name: String, url: String); suspend fun remove(id: Long): ShortcutEntity?; suspend fun restore(s: ShortcutEntity) }
class ChatRepository(dao: SkimChatDao, db: SkimDatabase) {
    fun observeRecentPageChats(limit: Int): Flow<List<SkimChatEntity>>; fun observeAll(query: String?): Flow<List<ChatListItem>>
    fun observeCount(): Flow<Int>; suspend fun chat(id: Long): SkimChatEntity?; suspend fun messages(chatId: Long): List<SkimMessageEntity>
    suspend fun createChat(c: SkimChatEntity): Long; suspend fun updateChat(c: SkimChatEntity)
    suspend fun addMessage(m: SkimMessageEntity): Long; suspend fun updateMessage(m: SkimMessageEntity)
    suspend fun delete(id: Long): DeletedChat?; suspend fun restore(d: DeletedChat)
    suspend fun deleteAll()                                          // + wal_checkpoint(TRUNCATE)
    val removals: SharedFlow<Long?>     // chatId vừa bị xoá, hoặc null = xoá tất cả; tín hiệu duy nhất cho bộ nhớ chat (phase 11)
}
class SitePermissionRepository(dao: SitePermissionDao) {
    fun observe(host: String): Flow<SitePermissionEntity?>; suspend fun get(host: String): SitePermissionEntity?
    suspend fun skimAiStates(hosts: Set<String>): Map<String, SkimAiState>
    suspend fun setSkimAi(host: String, s: SkimAiState)
    suspend fun deleteAllSkimAiData()   // skimAi = UNSET mọi dòng, rồi xoá dòng mà mọi trường đều UNSET (quyết định location/camera/mic còn lại thì giữ dòng)
    suspend fun setPermission(host: String, kind: SitePermissionKind, s: PermissionState)
}
```

- `ChatListItem` (04c) = chat + `questionCount` (số message `QUESTION`) + `hasSummary` + `answerCount`, lấy bằng một `@Query` có subquery đếm.
- `ImportedNode(title: String, url: String?, children: List<ImportedNode>)`; `ImportOutcome(folderId: Long, bookmarkCount: Int)`.
- `SkimDatabase.checkpointTruncate()` chạy `PRAGMA wal_checkpoint(TRUNCATE)`; callback `onOpen` chạy `PRAGMA secure_delete = ON`.

Cài đặt (`com.dantech.skimbrowser.data.settings`):

```kotlin
enum class SearchEngine(val searchTemplate: String, val suggestTemplate: String) {
    GOOGLE("https://www.google.com/search?q={q}", "https://www.google.com/complete/search?client=firefox&q={q}"),
    BING("https://www.bing.com/search?q={q}", "https://www.bing.com/osjson.aspx?query={q}&form=OSDJAS"),
    DUCKDUCKGO("https://duckduckgo.com/?q={q}", "https://ac.duckduckgo.com/ac/?q={q}&type=list"),
    ECOSIA("https://www.ecosia.org/search?q={q}", "https://ac.ecosia.org/autocomplete?q={q}&type=list");
    fun searchUrl(q: String): String; fun suggestUrl(q: String): String   // URLEncoder UTF-8
}
enum class ReaderFont { SANS_SERIF, SERIF }
data class SkimSettings(val searchEngine: SearchEngine = SearchEngine.GOOGLE, val theme: ThemeSetting = ThemeSetting.System,
    val askBeforeSending: Boolean = true, val keepChatHistory: Boolean = true, val readerTextScalePercent: Int = 100,
    val readerFont: ReaderFont = ReaderFont.SANS_SERIF, val onboardingCompleted: Boolean = false, val lastTabId: String? = null)
class SettingsRepository(dataStore: DataStore<Preferences>) {
    val settings: Flow<SkimSettings>
    suspend fun setSearchEngine(e: SearchEngine); suspend fun setTheme(t: ThemeSetting); suspend fun setAskBeforeSending(b: Boolean)
    suspend fun setKeepChatHistory(b: Boolean); suspend fun setReaderTextScale(p: Int); suspend fun setReaderFont(f: ReaderFont)
    suspend fun setOnboardingCompleted(); suspend fun setLastTabId(id: String?)
    suspend fun installId(): String     // D6 (đã chốt — Validation Session 1): UUID ngẫu nhiên tạo lần đầu, gửi kèm request proxy release
}
```

<!-- Red Team: AppContainer dựng trên thư mục tạm + close() — 2026-10-21 -->
`AppContainer` (sửa ở phase này):

```kotlin
data class AppStorage(val databaseName: String = "skim.db", val settingsFile: (Context) -> File = { it.preferencesDataStoreFile("skim_settings") }) {
    companion object { fun temp(dir: File) = AppStorage(databaseName = File(dir, "skim.db").path, settingsFile = { File(dir, "skim_settings.preferences_pb") }) }
}
class AppContainer(val app: Application, storage: AppStorage = AppStorage()) : Closeable {
    // database, dataStore, settings, repository … (by lazy); close() đóng DB và huỷ appScope
}
```

Tiện ích URL dùng chung: `data/UrlParts.kt` với `hostOf(url): String?` (lowercase, bỏ `www.`), `withoutFragment(url)`, `isWebUrl(url)` (http/https).

Luồng dữ liệu: UI/ViewModel → repository → DAO → SQLite (`skim.db`); DataStore file `skim_settings.preferences_pb`. Ngược lại `Flow` đẩy thay đổi về UI. `AppContainer` giữ một instance cho mỗi repository (sống theo process).

## Related code files

Tạo:
- `app/src/main/java/com/dantech/skimbrowser/data/db/SkimDatabase.kt` (entities, version 1, `exportSchema = true`, callback seed thư mục Mobile + `secure_delete`, `checkpointTruncate()`)
- `app/src/main/java/com/dantech/skimbrowser/data/db/entity/TabEntity.kt`, `TabGroupEntity.kt`, `TabGroupingSnapshotEntity.kt`, `HistoryEntryEntity.kt`, `BookmarkFolderEntity.kt`, `BookmarkEntity.kt`, `ShortcutEntity.kt`, `SkimChatEntity.kt`, `SkimMessageEntity.kt`, `SitePermissionEntity.kt` (cùng thư mục `data/db/entity/`)
- `app/src/main/java/com/dantech/skimbrowser/data/db/dao/TabDao.kt`, `HistoryDao.kt`, `BookmarkDao.kt`, `ShortcutDao.kt`, `SkimChatDao.kt`, `SitePermissionDao.kt` (cùng thư mục `data/db/dao/`)
- `app/src/main/java/com/dantech/skimbrowser/data/settings/SearchEngine.kt`, `SkimSettings.kt`, `SettingsRepository.kt`
- `app/src/main/java/com/dantech/skimbrowser/data/repo/TabRepository.kt`, `HistoryRepository.kt`, `BookmarkRepository.kt`, `ShortcutRepository.kt`, `ChatRepository.kt`, `SitePermissionRepository.kt`
- `app/src/main/java/com/dantech/skimbrowser/data/UrlParts.kt`
- `app/src/main/java/com/dantech/skimbrowser/AppStorage.kt`
- `app/schemas/com.dantech.skimbrowser.data.db.SkimDatabase/1.json` (Room sinh, commit)
- Test: `app/src/test/java/com/dantech/skimbrowser/data/db/TabDaoTest.kt`, `HistoryDaoTest.kt`, `BookmarkDaoTest.kt`, `SkimChatDaoTest.kt`, `SitePermissionDaoTest.kt`; `app/src/test/java/com/dantech/skimbrowser/data/repo/BookmarkRepositoryTest.kt`, `ChatRepositoryTest.kt`, `HistoryRepositoryTest.kt`, `ShortcutRepositoryTest.kt`, `TabRepositoryTest.kt`; `app/src/test/java/com/dantech/skimbrowser/data/settings/SettingsRepositoryTest.kt`, `SearchEngineTest.kt`; `app/src/test/java/com/dantech/skimbrowser/data/UrlPartsTest.kt`; `app/src/test/java/com/dantech/skimbrowser/AppContainerStorageTest.kt` (dựng trên thư mục tạm, `close()`, dựng lại trên cùng thư mục đọc lại được dữ liệu)

Sửa:
- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`: nhận `AppStorage`, implement `Closeable`; thêm `database` (`Room.databaseBuilder(app, SkimDatabase::class.java, storage.databaseName)`), `dataStore` (`PreferenceDataStoreFactory.create { storage.settingsFile(app) }`), `settings`, sáu repository (đều `by lazy`).

## Implementation steps

1. Viết entity và DAO theo bảng trên; index cho `history.url` (unique), `history.visitedAt`, `bookmarks.url`, `bookmarks.addedAt`, `skim_chats.updatedAt`, mọi FK. `HistoryDao.upsertKeepingLatest(entry)` = `INSERT … ON CONFLICT(url) DO UPDATE SET title = excluded.title, visitedAt = MAX(visitedAt, excluded.visitedAt)` (Room `@Query`); id của dòng hiện có được giữ.
2. `SkimDatabase` v1 + callback `onCreate` chèn thư mục `MOBILE` (id 1, `name = null`), `onOpen` bật `secure_delete`. Build một lần để Room sinh `app/schemas/.../1.json`; commit file.
3. `TabRepository.loadOpen()` trong một transaction: `DELETE FROM tabs WHERE closedAt IS NOT NULL`, `UPDATE tabs SET chatId = NULL WHERE chatId IS NOT NULL AND chatId NOT IN (SELECT id FROM skim_chats)`, rồi đọc tab mở theo `groupId, position`. `saveLayout` ghi nhóm, vị trí tab và snapshot (nếu có) trong cùng transaction.
4. `HistoryRepository.recordVisit` upsert theo `url` (một dòng mỗi URL, quyết định nhỏ của planner để 13 không lặp dòng); bỏ qua URL không phải http/https; `restore` dùng `upsertKeepingLatest`.
5. `BookmarkRepository`: `observeRoot` gộp `observeFolders()` + `observeCountsByFolder()` và cộng dồn đệ quy; "Recently added" = 10 bookmark mới nhất mọi thư mục (quyết định nhỏ); `importTree` tạo thư mục `IMPORTED` mới cho mỗi lần import, giữ cấu trúc con dạng `FOLDER`, chèn trong một transaction; `topLevelFolders` trả Mobile + mọi thư mục Imported cấp 1 (cho dialog Move to folder).
6. `ChatRepository.delete` trả `DeletedChat(chat, messages)` và phát `removals.emit(id)`; `restore` chèn lại cùng id; `deleteAll` xoá mọi chat, checkpoint, rồi `removals.emit(null)`.
7. `SitePermissionRepository.deleteAllSkimAiData()` như chữ ký; `skimAiStates(hosts)` cho 06b/gom nhóm (phase 12).
8. `SettingsRepository` với key Preferences; `installId()` đọc hoặc tạo `UUID.randomUUID().toString()`.
9. `SearchEngine.searchUrl/suggestUrl` mã hoá query bằng `URLEncoder.encode(q, UTF_8)` thay `{q}`.
10. `AppStorage` + sửa `AppContainer` (tham số, `close()`).
11. Test DAO/repository bằng Robolectric + `Room.inMemoryDatabaseBuilder` + `runTest` + Turbine: Undo trả lại đúng dữ liệu (cùng id, cùng vị trí); `HistoryRepository.restore` sau khi URL được ghi lại với id mới không ném lỗi và giữ một dòng với `visitedAt` lớn nhất; đếm đệ quy Imported; soft-close bị xoá khi `loadOpen`; `chatId` mồ côi về `NULL`; `upsert` với `chatId` âm lưu `NULL`; `deleteAllSkimAiData` xoá dòng chỉ có dữ liệu AI và giữ dòng có quyết định location; `removals` phát đúng id/null; `saveLayout` có snapshot là một transaction (lỗi giữa chừng không để lại layout mới); DataStore mặc định đúng như bản vẽ (Google, System, ask bật, keep bật, 100%, Sans serif); `AppContainerStorageTest`.
12. Commit cuối phase: `feat(data): add room schema, settings and repositories`.

## Todo list

- [x] 10 entity + index + FK (chatId không FK, không âm)
- [x] 6 DAO với Flow/suspend, upsert history giữ visitedAt lớn nhất
- [x] SkimDatabase v1, seed Mobile bookmarks, `secure_delete`, `checkpointTruncate`, export schema 1.json
- [x] TabRepository (soft close, reconcile chatId, layout + snapshot một transaction)
- [x] HistoryRepository (upsert theo URL, chỉ http/https, restore an toàn)
- [x] BookmarkRepository (root/folder/search/import/move/Undo, đếm đệ quy)
- [x] ShortcutRepository, ChatRepository (Undo, `removals`), SitePermissionRepository (host PK, `deleteAllSkimAiData`)
- [x] SearchEngine + SkimSettings + SettingsRepository (installId D6)
- [x] UrlParts, AppStorage, AppContainer (tham số + close)
- [x] Test DAO, repository, settings, container
- [x] Commit cuối phase

## Success criteria

- `testDebugUnitTest` cho package `data` xanh; mỗi luồng Undo của scout §3(d) (Tab closed, N tabs closed, Removed from history, Bookmark deleted, Shortcut removed) có test khôi phục đúng id và thứ tự; Undo history sau khi URL được truy cập lại không crash.
- `app/schemas/.../1.json` tồn tại và được commit.
- Cài đặt mặc định khớp trạng thái vẽ ở 10, 12, 08b (trừ cỡ chữ 100% theo quyết định nhỏ).

## Verification

```bash
./gradlew :app:kspDebugKotlin
./gradlew :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.data.*' --tests 'com.dantech.skimbrowser.AppContainerStorageTest'
ls app/schemas/com.dantech.skimbrowser.data.db.SkimDatabase/1.json
```

## Risk assessment

| Giả định | Dấu hiệu hỏng | Phản ứng đã định |
|---|---|---|
| Room 2.8.5 lưu enum dạng TEXT không cần converter | Lỗi KSP "Cannot figure out how to save this field" | Thêm `TypeConverters` cho enum đó |
| SQLite trên API 26 hỗ trợ `ON CONFLICT … DO UPDATE` (upsert, cần SQLite ≥ 3.24) `[UNVERIFIED trên API 26–29]` | Lỗi cú pháp SQL trên máy cũ | Dùng transaction "đọc theo url → update hoặc insert" thay cho upsert SQL |
| Lịch sử tối đa 1000 dòng đủ cho danh sách 13 | Người dùng thấy thiếu mục cũ | Tìm kiếm vẫn quét toàn bảng; tăng giới hạn hoặc thêm Paging sau |
| Upsert theo URL đủ cho 13 | Thiết kế muốn mỗi lượt truy cập một dòng | Đổi sang insert theo lượt; schema không đổi |

<!-- Updated: Validation Session 1 - rollback bằng git revert -->
Rollback: `git revert` commit cuối phase 3 (bảng mới, chưa phát hành). Từ bản phát hành đầu tiên, mọi đổi schema phải có migration và test.

## Security considerations

- DB và DataStore nằm trong storage riêng của app; cloud backup và device-to-device transfer đã chặn ở phase 1.
- `secure_delete` + checkpoint sau xoá hàng loạt để chat/history đã xoá không còn trong free page hay file WAL.
- Lớp data không có đường ghi incognito; phase 4 và phase 11 có test khẳng định tab incognito không gọi `HistoryRepository`/`ChatRepository`.
- Bảng chat chỉ chứa nội dung chat của trang thường (không lưu text trang, không có key).

## Next steps

- Phase 4 dùng `TabRepository`, `HistoryRepository`, `SettingsRepository`; phase 8 dùng `deleteAllSkimAiData`, `checkpointTruncate`; phase 11 dùng `ChatRepository.removals`, `SitePermissionRepository`.

## Kết quả thực thi (2026-10-21)

Commit: `feat(data): add room schema, settings and repositories`. Một lượt agent làm cả phase (10 entity, 6 DAO, `SkimDatabase` v1, 6 repository, settings, `UrlParts`, `AppStorage`, `AppContainer` có `close()`), rồi kiểm chứng độc lập: `kspDebugKotlin` sinh lại `1.json` trùng md5 với bản agent; cụm `data.*` + `AppContainerStorageTest` xanh nhiều lần liên tiếp sau `cleanTestDebugUnitTest` (132 test); toàn bộ `testDebugUnitTest` 384 test, 0 lỗi, 0 bỏ qua; `verifyRoborazziDebug`, `assembleDebug` xanh; `lintDebug` 0 lỗi, cảnh báo y hệt phase 2 (123), 0 cảnh báo trong file mới. Agent chạy 29 đột biến (đều làm test đỏ, nguồn khôi phục trùng md5); controller chạy thêm 4 đột biến cho phần tự bổ sung (đều đỏ đúng một test).

Chữ ký thực tế (khác spec, đã chấp nhận):
- `TabRepository.saveLayout(groups: List<TabGroupEntity>, placements: List<TabPlacement>, snapshot: TabGroupingSnapshotEntity? = null): List<Long>` với `TabPlacement(tabId, groupIndex: Int?, position)` (`groupIndex` trỏ vào `groups` của cùng lời gọi, `null` = Other). Ngữ nghĩa thay toàn bộ trong một transaction: nhóm `id = 0` được tạo; nhóm có id được cập nhật, hoặc tạo với đúng id đó nếu chưa có; nhóm không liệt kê bị xoá và tab của nó về Other; placement chỉ di chuyển tab đã lưu, không tạo dòng (tab incognito không thể lọt vào); input sai ném `IllegalArgumentException` trước khi ghi; snapshot ghi cuối cùng; trả id từng nhóm theo thứ tự.
- `HistoryRepository`: `upsertKeepingLatest` là transaction đọc theo `url` rồi insert/update (SQLite API 26–29 chưa có upsert); title trống không ghi đè title đã có; **bổ sung khi kiểm chứng:** URL dài hơn 8.192 ký tự không được ghi, title cắt ở 1.024 ký tự (không cắt giữa cặp surrogate), vì trang tự tạo được URL hàng MB bằng `history.pushState`.
- `BookmarkRepository.move` trả `Boolean`; `importTree` trả `ImportOutcome?`: chỉ giữ bookmark http(s) có host hợp lệ, và trả `null` (không tạo thư mục) khi tệp không có bookmark nào như vậy (bổ sung khi kiểm chứng, để không để lại thư mục Imported rỗng).
- `checkpointTruncate(): Boolean` (ném nếu gọi trong transaction); `SitePermissionRepository(dao, db, clock)`: host luôn chuẩn hoá, dòng không còn quyết định nào bị xoá ở mọi setter; `ChatRepository.createChat/addMessage` bỏ qua id của caller (DB cấp id dương); `removals` replay 0, buffer 64, phát sau commit.
- `loadOpen()` còn xoá nhóm không còn tab; `upsert(tab)` ghi `groupId` không tồn tại và `chatId <= 0` thành `NULL`; `hostOf` dùng `okhttp3.HttpUrl` (parse như trình duyệt, trả `null` cho URL không phải http(s)).

Việc để các phase sau:
- Phase 4: `TabManager` cấp id nhóm mới bằng bộ đếm tăng đơn điệu khởi tạo từ `max(id) + 1` lúc `restore()` (hoặc truyền `id = 0` rồi dùng id trả về), không tái dùng id nhóm đã xoá, vì AUTOINCREMENT chỉ bảo vệ id do DB cấp và một tab đang chờ Undo có thể rơi vào nhóm mới trùng id. Không gọi `upsert` cho tab đã `markClosed` (ghi chồng `closedAt = NULL` hoặc tạo lại dòng sau `deleteClosed`): huỷ job ghi đang chờ của tab trước khi đóng; mọi ghi Room đi qua một luồng tuần tự. Cắt `title`/`url` do trang cung cấp trước khi đưa vào state UI. Cổng `skim_api28` phải chạy thật phần data trên SQLite cũ bằng test instrumented: `recordVisit` hai lần cùng URL, `clearAll` (checkpoint), `markClosed` với 1.200 id (giới hạn 999 giá trị), `PRAGMA secure_delete` của kết nối ghi.
- Phase 5: `BookmarkImporter` xử lý `importTree == null` thành lỗi "không có bookmark" thay vì snackbar thành công.
- Phase 8: gọi `undo.cancelAll(...)` trước mọi thao tác xoá hàng loạt (lớp data không tự chặn Undo hồi sinh dữ liệu); các hàm `HistoryRepository.clearAll`, `ChatRepository.deleteAll`, `SitePermissionRepository.deleteAllSkimAiData`, `TabRepository.clearSnapshot` đã tự checkpoint.
- Phase 11: `ChatRepository.removals` không replay nên bộ nhớ chat phải đăng ký trước khi có thể xoá.

Giới hạn đã biết:
- SQL mới chỉ chạy trên SQLite của Robolectric (3.44); với API 26–29 (3.18–3.22) mới kiểm bằng đọc lại toàn bộ SQL Room sinh ra (không có upsert clause, window function, RETURNING, `IIF`, `json_*`, `NULLS FIRST/LAST`). Giới hạn 999 giá trị của `IN` chỉ được kiểm bằng đọc code vì Robolectric cho tới 32.766.
- `LIKE` chỉ không phân biệt hoa/thường với ASCII ("đà nẵng" không khớp "Đà Nẵng"); dùng cho tìm kiếm history, bookmark, Skim history. Nếu cần khớp Unicode phải đổi thiết kế (cột khoá tìm kiếm hoặc lọc trong Kotlin), chưa quyết định.
- Lịch sử không tự hết hạn và không có trần số dòng (chỉ có trần độ dài mỗi dòng); danh sách hiển thị 1.000 dòng mới nhất, tìm kiếm quét cả bảng.
- `observeRoot`/`observeFolder` gộp ba Flow Room nên có thể phát trạng thái trung gian (mỗi phần đều đúng); UI dùng `stateIn` sẽ gộp lại.
