# Phase 3: Data layer

- Plan: `plans/261020-1611-skim-design-implementation/`, `phase-03-data-layer.md` (toàn bộ bước 1–11; bước 12 commit do controller làm).
- Trạng thái: **DONE_WITH_CONCERNS**. Mọi lệnh kiểm chứng xanh; 29/29 đột biến làm test đỏ; cụm data xanh 3 lần liên tiếp sau `cleanTestDebugUnitTest`. Concern: vài lệch chữ ký có chủ đích (dưới), phần SQLite cũ chỉ kiểm bằng đọc SQL.
- 2026-10-21 14:34 Asia/Singapore. Không `git add`, không commit, không đổi nhánh. Chỉ sửa `AppContainer.kt` (+65/−2); không đụng UI/theme/docs/strings/drawable, `build.gradle.kts`, version catalog; không thêm dependency. Không chạy emulator/adb. Không sửa phase file (trạng thái plan do controller cập nhật qua `ak plan`).

## Kiểm chứng (output thật)

| Lệnh | Kết quả |
|---|---|
| `:app:kspDebugKotlin` | Xanh. Xoá `1.json` rồi chạy `--rerun-tasks`: Room sinh lại, md5 `19cfb263…` trùng bản cũ |
| `ls app/schemas/com.dantech.skimbrowser.data.db.SkimDatabase/1.json` | Có, 20 711 byte, chưa track (controller commit) |
| `:app:testDebugUnitTest --tests 'com.dantech.skimbrowser.data.*' --tests '…AppContainerStorageTest'` | Xanh 129 test, 0 fail, 0 skip; ×3 liên tiếp sau `cleanTestDebugUnitTest`, cả 3 xanh |
| `:app:testDebugUnitTest` (toàn bộ) | Xanh 381 test (252 cũ + 129 mới), 0 fail, 0 skip, 45 lớp |
| `:app:assembleDebug` | Xanh |
| `:app:lintDebug` | 0 error, không baseline/suppress. Cảnh báo y hệt phase 2: UnusedResources 118, NewerVersionAvailable 3, AndroidGradlePluginVersion 1, InsecureBaseConfiguration 1. 0 issue trong file của phase này |
| Biên dịch lại main + unit test `--rerun-tasks -Pkotlin.incremental=false` | 0 cảnh báo Kotlin |

Số test theo lớp: `TabDaoTest` 8, `HistoryDaoTest` 8, `BookmarkDaoTest` 9, `SkimChatDaoTest` 6, `SitePermissionDaoTest` 5, `SecureDeleteTest` 6, `TabRepositoryTest` 16, `HistoryRepositoryTest` 8, `BookmarkRepositoryTest` 15, `ShortcutRepositoryTest` 6, `ChatRepositoryTest` 13, `SettingsRepositoryTest` 10, `SearchEngineTest` 5, `UrlPartsTest` 8, `AppContainerStorageTest` 6.

## Đột biến (harness thay đúng 1 đoạn, chạy lớp liên quan, khôi phục; md5 59 file main trước/sau trùng)

29/29 đỏ:
- M01 upsert history lấy `visitedAt` mới thay vì max → `HistoryDaoTest.upsertOfAKnownUrl…`, `HistoryRepositoryTest.undoAfterTheUrlWasVisitedAgain…`.
- M02 upsert history xoá rồi chèn (đổi id, như REPLACE) → 3 test (giữ id, tiêu đề, Undo sau truy cập lại).
- M03 `loadOpen` bỏ xoá `closedAt != null` → `aTabStillClosedWhenTheProcessDies…`, `loadOpenForgetsChats…`.
- M04 `upsert` ghi `chatId` âm → `anInMemoryChatIdIsStoredAsNoChat`.
- M05 `HistoryRepository.restore` dùng `insert` → `undoAfterTheUrlWasVisitedAgain…` (UNIQUE).
- M06 restore bookmark không về Mobile khi thư mục mất → `undoIntoAFolderThatIsGone…`.
- M07/M08 restore chat đổi id message / id chat → `undoOfADeletedChatBringsBackTheSameIdsInOrder`.
- M09 đếm không đệ quy → `theImportedFolderCounts…` (24), `recursiveCountsHandleDeepNesting…`.
- M10/M11 `deleteAllSkimAiData` xoá cả dòng còn quyết định location / giữ dòng rỗng → `SitePermissionDaoTest.deleteAllSkimAiData…`, `SecureDeleteTest.deletingAllAiData…`.
- M12 bỏ `PRAGMA secure_delete = ON` → 5/6 `SecureDeleteTest` (gồm `aRemovedHistoryEntryLeavesNoBytesOnDisk`).
- M13/M14/M15 bỏ checkpoint ở `clearAll` / `ChatRepository.deleteAll` / `deleteAllSkimAiData` → test log tương ứng (WAL > 0, còn chuỗi đánh dấu).
- M16 `installId` không kiểm lại trong `edit` → `racingFirstCallsAgreeOnOneInstallId`.
- M17 `removals` không đệm → `aBusyCollectorDoesNotHoldUpDeletions…` (timeout 30 s).
- M18 `delete` phát id cả khi không xoá gì → `deletingAChatThatDoesNotExistSignalsNothing`.
- M19 LIKE không escape `%`/`_` → `matchingTreatsTheUsersWildcardsLiterally`, `listItemsFilterByTitleOrHost`.
- M20 `saveLayout` không transaction → `saveLayoutWithASnapshotWritesEverythingOrNothing`.
- M21 dọn nhóm rỗng thiếu `groupId IS NOT NULL` → `deleteGroupsWithoutTabsKeeps…`.
- M22 `upsert` giữ `groupId` không tồn tại → `aTabOfAGroupThatIsGone…` (FK).
- M23 `loadOpen` bỏ dọn `chatId` mồ côi → `loadOpenForgetsChats…`.
- M24 import giữ URL không phải web → `importKeepsOnlyWebBookmarksWithAHost`.
- M25 không chặn 80–200 khi đọc → `theReaderTextScaleStaysBetween80And200`.
- M26 enum lạ ném lỗi → `unknownNamesAndWrongTypesReadAsDefaults`.
- M27 `hostOf` giữ `www.` → 3 test.
- M28 `close()` không chờ DataStore nhả file → `closeWaitsForSettingsWritesStillInFlight` ("multiple DataStores active"). Lần đầu đột biến này sống sót, nên đã thêm test ghi dở lúc đóng.
- M29 bỏ `ReplaceFileCorruptionHandler` → `aCorruptSettingsFile…`.

Lần chạy đầu cũng lộ một test yếu: test `chatId` âm chỉ kiểm sau `loadOpen()` (vốn cũng dọn id mồ côi), nên đã sửa để kiểm bảng ngay sau `upsert`.

## Chữ ký cho phase 4–12

```kotlin
// data.repo
data class TabPlacement(val tabId: String, val groupIndex: Int?, val position: Int)  // groupIndex trỏ vào `groups` của cùng lời gọi; null = Other
suspend fun TabRepository.saveLayout(groups: List<TabGroupEntity>, placements: List<TabPlacement>,
    snapshot: TabGroupingSnapshotEntity? = null): List<Long>   // id từng nhóm theo thứ tự
data class BookmarkFolderSummary(val folder: BookmarkFolderEntity, val bookmarkCount: Int)       // đếm đệ quy
data class BookmarksRoot(val folders: List<BookmarkFolderSummary>, val recentlyAdded: List<BookmarkEntity>)
data class FolderContent(val folder: BookmarkFolderEntity?, val subfolders: List<BookmarkFolderSummary>, val bookmarks: List<BookmarkEntity>)
data class ImportedNode(val title: String, val url: String?, val children: List<ImportedNode> = emptyList())
data class ImportOutcome(val folderId: Long, val bookmarkCount: Int)
data class DeletedChat(val chat: SkimChatEntity, val messages: List<SkimMessageEntity>)
val ChatRepository.removals: SharedFlow<Long?>   // replay 0, extraBufferCapacity 64 (SUSPEND), phát sau commit; null = xoá tất cả
// restore: HistoryRepository.restore(entry), BookmarkRepository.restore(b), ShortcutRepository.restore(s),
//          ChatRepository.restore(d: DeletedChat), TabRepository.reopen(ids) — cùng id, cùng position; gọi lại khi bản ghi còn → không làm gì
// data.db.dao
data class ChatListItem(@Embedded val chat: SkimChatEntity, val questionCount: Int, val hasSummary: Boolean, val answerCount: Int)
// data.db.entity
enum class SitePermissionKind { LOCATION, CAMERA, MICROPHONE }; enum class SkimAiState { UNSET, ALLOWED, ASK }
enum class PermissionState { UNSET, ALLOWED, BLOCKED }; enum class FolderKind { MOBILE, IMPORTED, FOLDER }; enum class ChatKind { PAGE, TABS }
enum class MessageRole { USER, ASSISTANT }; enum class MessageKind { QUESTION, SUMMARY, ANSWER, TABS_OVERVIEW }
enum class MessageFeedback { NONE, GOOD, POOR }; enum class MessageStatus { COMPLETE, STOPPED, REFUSED, ERROR }
// data.settings: enum class ReaderFont { SANS_SERIF, SERIF }; SettingsRepository.createDataStore(scope, produceFile)
// data.db: SkimDatabase.open(context, name); suspend fun checkpointTruncate(): Boolean (ném nếu trong transaction)
// AppContainer: database, dataStore, settings, tabRepository, historyRepository, bookmarkRepository,
//               shortcutRepository, chatRepository, sitePermissionRepository (đều by lazy); close() đóng DB → cancel appScope → chờ DataStore nhả file
```

`saveLayout` theo ngữ nghĩa "thay toàn bộ": `groups` là tập nhóm sau lời gọi. Nhóm `id = 0` được tạo mới. Nhóm có id thì được update, hoặc được tạo với đúng id đó nếu chưa có. Nhóm không có trong danh sách bị xoá, và tab chưa được xếp mà thuộc nhóm đó về Other. Placement chỉ di chuyển tab đã lưu, không bao giờ tạo dòng mới, nên tab incognito không thể lọt vào. Input sai (trùng tab, index ngoài `groups`, id âm hoặc trùng) ném `IllegalArgumentException` trước khi ghi. Snapshot được ghi cuối cùng, cùng transaction.

## SQL có thể khác giữa Robolectric (SQLite 3.44.3) và API 26–29 (3.18–3.22)

Chỉ kiểm bằng cách đọc lại toàn bộ SQL Room sinh ra (`INSERT OR ABORT/IGNORE/REPLACE`, `nullif`, `EXISTS`, `COALESCE`, subquery, `LIKE … ESCAPE '\'`). Không có upsert clause, window function, RETURNING, IIF, `json_*`, NULLS FIRST/LAST hay `UPDATE … FROM`.
- `IN (:list)` > 999 giá trị lỗi trên SQLite < 3.32 (API ≤ 29), còn Robolectric cho tới 32 766. Đã chia lô 500 (`SQL_IN_LIMIT`). Test 1 200 id chạy đúng đường chia lô, nhưng trên Robolectric thì vẫn qua kể cả khi không chia, nên giới hạn này chỉ được kiểm bằng đọc code.
- `LIKE` chỉ gộp hoa/thường cho ASCII. Nếu SQLite của máy được build kèm ICU, chữ không phải ASCII có thể khớp khác (chưa kiểm).
- Robolectric mặc định `secure_delete = 1` và `auto_vacuum = 1`, giống cờ build của Android. Vì vậy `SecureDeleteTest` mở DB qua open-helper ép OFF để chứng minh callback bật lại.
- Máy RAM thấp: Room dùng journal TRUNCATE, `wal_checkpoint` thành no-op (trả 0,-1,-1); `secure_delete` vẫn áp dụng.
- Cổng `skim_api28` của phase 4 nên chạy thật một lượt `recordVisit` hai lần cùng URL và một lần `clearAll` (checkpoint).

## Lệch spec và lý do

1. Bước 1 được thay theo chỉ đạo: `upsertKeepingLatest` là một transaction đọc theo `url` rồi insert/update, giữ id cũ, `visitedAt = max`. Quy tắc title: title của lượt mới nhất thắng, nhưng title trống không xoá title đã có (tránh trang chưa có title làm trống lịch sử). `updateTitle` cũng bỏ qua title trống.
2. `loadOpen()` còn xoá nhóm không còn tab sau khi purge. Lý do: đóng cả nhóm rồi process chết trong 10 s Undo sẽ để lại một nhóm rỗng.
3. `upsert(tab)` ghi `groupId` không tồn tại thành NULL thay vì crash FK. `chatId <= 0` thành NULL (spec ghi `< 0`; id 0 cũng không phải chat đã lưu).
4. `saveLayout` trả `List<Long>` (spec: Unit) và kiểm input. `BookmarkRepository.move` trả `Boolean` (false khi bookmark hoặc thư mục không còn). `checkpointTruncate()` trả `Boolean`.
5. `SitePermissionRepository(dao, db, clock)` thêm `db` để checkpoint sau "Delete all AI data", thêm `clock` để test. Dòng không còn quyết định nào thì bị xoá ở mọi setter, không chỉ trong `deleteAllSkimAiData`. Host luôn được chuẩn hoá (lowercase, bỏ `www.`).
6. `clearSnapshot()` cũng checkpoint, vì phase 8 gọi nó trong Clear browsing data.
7. `importTree` chỉ giữ bookmark http(s) có host hợp lệ: `javascript:` (bookmarklet), `place:`, `file:`, `data:` bị bỏ để không chạy được từ danh sách bookmark. `bookmarkCount` là số đã giữ. Thư mục Imported vẫn được tạo khi số này bằng 0.
8. `ChatRepository.createChat`/`addMessage` bỏ qua id của caller (DB cấp id dương), nên Room không bao giờ chứa id âm. `restore` của chat, bookmark, shortcut không làm gì nếu bản ghi còn tồn tại.
9. `ChatListItem.hasSummary`/`answerCount` chỉ đếm message assistant có text (COMPLETE/STOPPED); REFUSED/ERROR không tính. `answerCount` gồm ANSWER và TABS_OVERVIEW.
10. `hostOf` dùng `okhttp3.HttpUrl` (đã là dependency), parse như trình duyệt: backslash kết thúc host, Unicode thành punycode, IPv6 giữ `[...]`. Trả null cho mọi URL không phải http(s), kể cả `ftp:` và `blob:`. `isWebUrl` chỉ xét scheme.
11. Thêm factory `SkimDatabase.open`, `withSkimSetup()` (internal) và `SettingsRepository.createDataStore` để production và test dùng chung cấu hình (callback, corruption handler).
12. Thêm file ngoài danh sách: `data/db/SecureDeleteTest.kt` (theo lưu ý kỹ thuật 5) và `testing/TestDatabases.kt` (helper DB in-memory, ghi thô qua Room, Turbine `awaitItemMatching`).
13. `close()` chặn (`runBlocking { join }`) tới khi DataStore nhả file; ngoài ra giữ đúng thứ tự đã chỉ định (đóng DB rồi mới huỷ `appScope`).

## Concerns / câu hỏi chưa giải quyết

1. `saveLayout` dùng ngữ nghĩa "thay toàn bộ" (nhóm không liệt kê thì bị xoá). Controller cần duyệt trước phase 4. Nếu TabManager tự cấp id nhóm (vì `applyLayout` đồng bộ), nó không được tái dùng id của nhóm đã xoá: AUTOINCREMENT chỉ bảo vệ id do DB cấp, nên một tab đang chờ Undo có thể rơi vào nhóm mới trùng id.
2. URL và title history không có giới hạn độ dài. Trang có thể `pushState` URL rất dài để làm phình DB. Cần quyết định có đặt trần không (ở `recordVisit` hoặc phase 4).
3. `observeRoot`/`observeFolder` là `combine` của 3 Flow Room, nên có thể phát trạng thái trung gian (mỗi phần vẫn đúng). UI qua `stateIn` sẽ gộp lại.
4. Chặn hồi sinh dữ liệu khi Undo sau xoá hàng loạt dựa vào `UndoRegistry.cancelAll` (phase 8); lớp data không có cơ chế chặn riêng.
5. Phase 5: nên báo lỗi import khi file không có bookmark hợp lệ thay vì gọi `importTree` (vì nó vẫn tạo thư mục rỗng).
