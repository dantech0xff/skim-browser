# Báo cáo: lượt 4A của phase 4 (logic thuần + state của browser engine)

## Kết quả

- Status: DONE. Package `com.dantech.skimbrowser.browser` có đủ phần logic thuần và state theo phạm vi 4A; không tạo WebView, không đụng UI, không sửa file nào của phase 1–3 (git chỉ có hai thư mục mới `browser/`).
- 20 file main (2.660 dòng), 21 file test (173 test mới). Toàn bộ dự án 557 test xanh (384 cũ + 173), 0 skip.
- 45 đột biến, cả 45 đều làm test đỏ; mọi file được khôi phục và md5 tổng của `browser/` trùng trước và sau.
- Lint giữ đúng bộ cảnh báo cũ (123); biên dịch lại không incremental: 0 cảnh báo Kotlin.

## Kiểm chứng (chạy thật)

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests 'com.dantech.skimbrowser.browser.*' --console=plain` ×3 liên tiếp | 3/3 BUILD SUCCESSFUL; mỗi lần 173 test, 0 lỗi, 0 skip (14 lớp test) |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain` | BUILD SUCCESSFUL; 557 test, 0 lỗi, 0 skip |
| `./gradlew :app:assembleDebug --console=plain` | BUILD SUCCESSFUL |
| `./gradlew :app:lintDebug --console=plain` | 0 lỗi; cảnh báo: UnusedResources 118, NewerVersionAvailable 3, AndroidGradlePluginVersion 1, InsecureBaseConfiguration 1; 0 cảnh báo trong `browser/` |
| `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks -Pkotlin.incremental=false --console=plain` | BUILD SUCCESSFUL; 0 dòng `w:` (trước khi làm cũng 0) |

Kết quả test và lint đọc từ JUnit XML/SARIF bằng script `python3 -I` trong scratchpad.

## Đột biến

Harness: `/private/tmp/claude-501/-Users-dan-Desktop-Development-Android-Kotlin-skim-browser/3d14ada1-e9fa-439c-b06d-9431b3c2b42b/scratchpad/tools/mutate.py`. Mỗi đột biến thay đúng đoạn nguồn (khớp đúng 1 lần), chạy lớp test liên quan, khôi phục file, so md5.

| ID | Đột biến | Bị bắt bởi (ví dụ) |
|---|---|---|
| M01 | TabManager: close không ẩn tab | TabManagerTest (11 test) |
| M02 | undo luôn đưa tab về Other | closeHidesTabsAtOnceAndUndoPutsThemBackInTheirGroupAndPlace |
| M03 | undo giữ groupId của nhóm đã xoá | undoOfATabWhoseGroupIsGoneSendsItToTheEndOfOther |
| M04 | undo sai vị trí | closeHidesTabsAtOnce… |
| M05 | finalize không xoá dòng Room | aFinalCloseDeletesTheRow…, writesReachRoomInTheOrderOfTheCommands |
| M06 | finalize không xoá state/thumbnail | aFinalCloseDeletes…, aClosedTabIsNeverWrittenAgain |
| M07 | tab incognito vẫn ghi Room | incognitoTabsNeverReach…, aLayoutSkips… |
| M08 | tab incognito vẫn ghi history | incognitoTabsNeverReach…, historyRecordsVisits… |
| M09 | restore không có timeout | restoreFallsBackToANewTab… (treo tới timeout của runTest) |
| M10 | applyLayout bỏ kiểm version | aLayoutBasedOnAnOldVersionChangesNothing |
| M11 | upsert tab đã đóng (không huỷ debounce + không kiểm tab còn mở) | aClosedTabIsNeverWrittenAgain |
| M12 | tái dùng id nhóm đã xoá | aNewGroupNeverGetsTheIdOfAGroupThatWasDeleted |
| M13 | đóng tab incognito cuối không markEnding | closingTheLastIncognitoTab…, closingAllIncognitoTabs… |
| M14 | undo không cancelEnding | undoOfTheLastIncognitoTabKeepsItsSession |
| M15 | lastTabId ghi cả tab incognito | theLastTabSettingFollowsNormalTabsOnly |
| M16 | title không bị cắt | aHugeTitleIsCutWithoutSplittingACharacter |
| M17 | mọi Navigated là khác document | pageEventsTellSameDocumentNavigations… |
| M18 | highlight bỏ kiểm nonce | aHighlightRunsOnlyOnTheDocumentTheCitationCameFrom |
| M19 | tự reload không debounce 2 s | theShownTabReloadsByItselfOnceTheNetworkIsBack… |
| M20 | IncognitoSession: Ending nối lại phiên cũ | aNewTabWhileTheSessionEnds…, aNewIncognitoTabWhileTheLastOneWaits… |
| M21 | end bỏ xoá web storage | endingClears…, aFailingStepDoesNotStopTheOthers |
| M22 | end bỏ clearCache qua WebView tạm | endingClears…, withoutTheHttpCacheManager… |
| M23 | rơi về profile mặc định khi không hỗ trợ | withoutProfilesThereIsNoIncognito… |
| M24 | end không xoá quyết định quyền | permissionAnswersBelongToTheSession… |
| M25 | TabStateStore: bỏ kiểm CRC | aWrongChecksumIsDeleted |
| M26 | bỏ kiểm version WebView | aStateWrittenByAnotherWebViewIsDeleted |
| M27 | bỏ kiểm SDK_INT | aStateWrittenOnAnotherAndroidVersionIsDeleted |
| M28 | ném lỗi thay vì xoá file | 8 test của TabStateStoreTest |
| M29 | state incognito ghi ra đĩa | incognitoStateNeverReachesTheDisk |
| M30 | ThumbnailStore: thumbnail incognito ghi ra đĩa | anIncognitoThumbnailNeverReachesTheDisk |
| M31 | bỏ lọc bidi override/embedding | aRightToLeftOverrideCannotDisguiseAnApp, hiddenCharacters… |
| M32 | DownloadNames: bỏ basename | onlyTheLastPathSegmentIsKept |
| M33 | bỏ lọc zero-width | bidiAndZeroWidthCharactersAreRemoved |
| M34 | ExternalIntents: bỏ BROWSABLE | aTelLinkBecomesABrowsableViewIntent |
| M35 | fallback không phải http/https vẫn giữ | aFallbackUrlThatIsNotHttpIsDropped |
| M36 | mở app không cần gesture | otherAppsOpenOnlyAfterAGesture |
| M37 | giữ component | anIntentUrlLosesItsComponent… |
| M38 | RendererCrashBudget: biên `>` thay `>=` | aCrashOlderThanTheWindowNoLongerCounts |
| M39 | cửa sổ 6 s thay 60 s | aTabRecoversTwiceInAMinute…, aCrashJustInsideTheWindow… |
| M40 | LoadErrors: NoInternet bỏ điều kiện không có mạng | anyFailureWithoutANetworkIsNoInternet… |
| M41 | mọi lỗi DNS thành NoInternet | aDnsFailureWithANetworkIsUnreachable |
| M42 | DataUrls: bỏ trần kích thước | contentsOverTheCapAreRefused |
| M43 | HighlightRequest: không escape U+2028 | citedTextThatTriesToEndTheStringStaysInsideIt |
| M44 | không kiểm blockId | onlyDigitBlockIdsAreAccepted, aHighlightRuns… |
| M45 | PageContent: bỏ giới hạn 400 block | atMost400BlocksAreKept |

M11 gồm hai chỗ sửa vì bất biến có hai lớp bảo vệ (huỷ job debounce khi đóng, và `persistTab` chỉ ghi tab còn mở, đọc lại state lúc hàng đợi chạy); bỏ riêng một lớp thì lớp kia vẫn giữ, không phải test yếu.

## Chữ ký thật cho lượt 4B–4D

```kotlin
@MainThread interface TabEngine {          // WebViewPool implement
    fun load(tabId: String, url: String); fun goBack(tabId: String): Boolean; fun goForward(tabId: String); fun reload(tabId: String)
    fun setDesktop(tabId: String, on: Boolean); fun find(tabId: String, q: String); fun findNext(tabId: String, forward: Boolean)
    fun clearFind(tabId: String); fun saveAndDestroy(tabId: String); fun clearHistory(tabId: String)
    fun ensureWebView(tabId: String, offscreen: Boolean)
    suspend fun evaluate(tabId: String, script: String): String?
    fun withTemporaryWebView(profile: String?, block: (WebView) -> Unit)
}
@MainThread interface TabEventSink {       // TabManager.events; client/pool gọi, chỉ main frame
    fun onPageStarted(tabId: String, url: String): Long          // trả loadNonce
    fun onProgress(tabId: String, percent: Int); fun onTitle(tabId: String, title: String)
    fun onVisitedHistory(tabId: String, url: String); fun onPageFinished(tabId: String, url: String)
    fun onLoadError(tabId: String, errorCode: Int, description: String?, url: String)
    fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onFindResult(tabId: String, activeMatchIndex: Int, matchCount: Int)
    fun onRendererGone(tabId: String, didCrash: Boolean): RendererGoneAction   // RESTORE | SHOW_ERROR
    fun onHitTarget(tabId: String, target: HitTarget)
    fun onPopupRequested(openerTabId: String): String?; fun onPopupClosed(popupTabId: String)
    fun onStateSaved(tabId: String, state: Bundle); fun onWebViewDestroyed(tabId: String)
}
class TabManager(tabRepo: TabRepository, history: HistoryRepository, settings: SettingsRepository, incognito: IncognitoSession,
    stateStore: TabStateStore, thumbnails: ThumbnailStore, network: NetworkMonitor, appScope: CoroutineScope, scripts: PageScriptSource,
    clock: () -> Long = System::currentTimeMillis, elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    newTabId: () -> String = { UUID.randomUUID().toString() }, ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, restoreTimeoutMs: Long = 5_000) {
    val state: StateFlow<TabsState>; val pageEvents: SharedFlow<PageEvent>; val hitTargets: SharedFlow<Pair<String, HitTarget>>
    val events: TabEventSink; fun runtime(tabId: String): StateFlow<TabRuntime>
    suspend fun restore(): RestoreResult        // Restored(tabCount) | Fallback(timedOut)
    fun newTab(incognito: Boolean, url: String? = null, background: Boolean = false, openedByExternalApp: Boolean = false,
               closeOnBack: Boolean = false, openerTabId: String? = null): String
    fun select(tabId: String); fun load(tabId: String, url: String); fun returnToNewTabPage(tabId: String)
    fun goBack(tabId: String): Boolean; fun goForward(tabId: String); fun reload(tabId: String); fun setDesktopSite(tabId: String, on: Boolean)
    fun find(tabId: String, query: String); fun findNext(tabId: String, forward: Boolean); fun clearFind(tabId: String)
    fun close(tabIds: List<String>): CloseToken; fun undoClose(token: CloseToken); fun finalizeClose(token: CloseToken)
    fun finalizePendingCloses(); fun pendingCloseIds(): Set<String>
    fun applyLayout(layout: GroupLayout, expectedVersion: Long? = null, snapshot: TabGroupingSnapshotEntity? = null): Boolean
    fun bindChat(tabId: String, chatId: Long?)
    suspend fun awaitPageReady(tabId: String, timeoutMs: Long = 15_000): Boolean
    suspend fun extractPage(tabId: String): PageContent?; suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean
    fun attach(engine: TabEngine); fun detach(); internal suspend fun awaitWrites()
}
@MainThread class IncognitoSession(profiles: BrowserProfiles, sharedDir: File, appScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO, clock: () -> Long = System::currentTimeMillis) {
    sealed interface State { None; Active(profile); Ending(profile, pendingToken: CloseToken) }
    val state: StateFlow<State>; val isSupported: Boolean; val hasHttpCacheManager: Boolean
    fun profileForNewTab(finalizePending: (CloseToken) -> Unit): String; fun markEnding(token: CloseToken); fun cancelEnding()
    fun end(engine: TabEngine?); fun purgeStale()
    fun decision(host: String, kind: SitePermissionKind): PermissionState; fun setDecision(host: String, kind: SitePermissionKind, s: PermissionState)
}
@MainThread interface BrowserProfiles { val isMultiProfileSupported: Boolean; val hasHttpCacheManager: Boolean
    fun getOrCreate(name: String): BrowserProfile; fun get(name: String): BrowserProfile?; fun names(): List<String>; fun delete(name: String): Boolean }
@MainThread interface BrowserProfile { val name: String; fun disableHttpCache(); fun clearCookies(); fun clearWebStorage(); fun clearGeolocationPermissions() }
class TabStateStore(dir: File, webViewVersion: () -> String, scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1), sdkInt: Int = Build.VERSION.SDK_INT) {
    fun save(tabId: String, state: Bundle, incognito: Boolean); suspend fun load(tabId: String, incognito: Boolean): Bundle?
    fun delete(tabId: String); fun purgeExcept(keep: Set<String>)
    companion object { fun webViewVersionOf(context: Context): String }   // WebViewCompat.getCurrentWebViewPackage
}
class ThumbnailStore(dir: File, scope: CoroutineScope, ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    incognitoCacheBytes: Int = 16 MB) {
    fun put(tabId: String, bitmap: Bitmap, incognito: Boolean); fun observe(tabId: String): StateFlow<ImageBitmap?>
    fun delete(tabId: String); fun clearAll(); fun purgeExcept(keep: Set<String>)
}
interface PageScriptSource { fun extractScript(): String; fun readerableScript(): String; fun highlightScript(argsJson: String): String }
fun parsePageContent(json: String, committedUrl: String, loadNonce: Long): PageContent?
fun highlightArgsJson(request: HighlightRequest): String?       // HighlightArgs(id, cited) là internal
object DownloadNames { fun sanitize(raw: String?, mime: String?): String }
fun parseDataUrl(url: String, maxBytes: Int = MAX_INLINE_DOWNLOAD_BYTES): DataUrl?   // DataUrl(mimeType, bytes); 25 MB
fun loadErrorOf(errorCode: Int, description: String?, url: String, hasActiveNetwork: Boolean): LoadError
fun externalNavigation(uri: Uri, hasGesture: Boolean): ExternalNav   // InWebView | Blocked | Launch(intent, fallbackUrl)
fun desktopUserAgent(defaultUa: String): String
class RendererCrashBudget(maxRecoveries: Int = 2, windowMs: Long = 60_000) { fun allow(tabId: String, now: Long): Boolean; fun forget(tabId: String) }
interface NetworkMonitor { val hasActiveNetwork: Boolean; val validated: StateFlow<Boolean> }
class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor, Closeable
```

Mô hình (`Tab.kt`): `Tab` (thêm `createdAt`), `TabGroup`, `TabsState(tabs, groups, currentTabId, layoutVersion)` + `currentTab`/`tab(id)`, `TabRuntime` (đúng trường spec), `LoadError.{Unreachable, NoInternet, TimedOut, Crashed}(url, codeName)`, `FindState(query, activeMatchIndex, matchCount)`, `HitTarget.{Link, Image}` (@Serializable, `@SerialName` "link"/"image"), `PageEvent.{PageStarted, Navigated, PageFinished}`, `CloseToken(id)` (value class), `GroupLayout(groups: List<Group(id?, title, tabIds, createdBySkim)>, ungrouped)` (@Serializable) + `TabsState.layout()`, `RestoreResult`.

Hợp đồng mà lượt 4B phải giữ (đã ghi trong KDoc):
- `saveAndDestroy` và evict: gọi `events.onStateSaved(tabId, bundle)` trước khi destroy, rồi `events.onWebViewDestroyed(tabId)`. Mọi lần lưu state đi qua sink (TabManager bỏ state của tab đã finalize hoặc đang ở New tab page; tab incognito vào bộ nhớ). Pool đọc state bằng `stateStore.load(tabId, tab.incognito)` (suspend, IO).
- `load`/`reload` cho tab chưa có WebView sống: áp dụng khi WebView được tạo lần sau, sau `restoreState` (giữ back history). TabManager tự giữ lệnh khi chưa attach và phát lại lúc `attach`.
- `evaluate`: gọi `evaluateJavascript` trước lần suspend đầu tiên, trên main (để kiểm URL + nonce ngay trước đó còn đúng); trả null khi tab không có WebView sống.
- `withTemporaryWebView(profile)`: `setProfile` trước mọi thao tác khác, destroy sau block.
- Client: `onPopupRequested` chỉ khi `isUserGesture`; `onLoadError` chỉ main frame; `onRendererGone` → pool destroy WebView; `RESTORE` = tạo lại từ state khi tab hiện; `SHOW_ERROR` = không tạo lại tới Try again (`reload`).
- Readerable: TabManager tự chạy `readerableScript` sau `PageFinished` (một lần mỗi nonce, chỉ http(s), không cho trang lỗi), nên client không cần báo readerable.

## Quyết định tự chốt

1. Vị trí tab tính trong từng section (nhóm hoặc Other, theo chế độ). Tab mới vào cuối Other; tab incognito không bao giờ có nhóm. `TabsState.tabs` luôn theo thứ tự: tab thường theo nhóm rồi Other, tab incognito sau cùng. Nhóm mà mọi tab đang chờ Undo vẫn nằm trong `groups` (UI ẩn nhóm rỗng) tới khi close thành final.
2. `applyLayout`: bỏ qua id tab không mở (đã đóng, chờ Undo, incognito, không tồn tại); tab không được nhắc ở lại nhóm nếu nhóm còn, không thì về cuối Other; nhóm không còn tab bị bỏ; id không phải nhóm hiện có → nhóm mới, id từ bộ đếm (không tạo lại id đã xoá); tab hoặc nhóm lặp → `IllegalArgumentException`; snapshot được đóng dấu bằng layoutVersion mới.
3. `layoutVersion` sau `restore()` = `max(hiện tại, snapshot.appliedLayoutVersion + 1)`: snapshot của process trước không bao giờ trông như còn hiện hành, nên Undo gom nhóm tắt sau process death.
4. `restore()` chạy một lần (mọi caller nhận cùng kết quả), chạy trên `ioDispatcher`; phần đọc là một job riêng xếp sau các lệnh ghi trong hàng đợi, nên DB treo trong lời gọi blocking không giữ được timeout 5 s. Fallback: mở tab New tab và **tắt ghi tab/layout/lastTabId vào Room cho phần còn lại của process**, để `saveLayout` kiểu thay toàn bộ và vị trí mới không đè lên các tab chưa đọc được; history vẫn ghi.
5. Ghi Room qua `SerialWriter` (mutex FIFO, mỗi khối chạy hết kể cả lúc suspend). `persistTab` gộp mỗi tab tối đa một lệnh đang chờ và đọc lại state lúc hàng đợi chạy; title chỉ ghi row sau 1 s (debounce), huỷ khi đóng. Undo ghi lại cả row (ngoài `reopen`) phòng trường hợp lệnh insert đầu tiên chưa kịp tới Room.
6. Đóng tab hiện tại: chọn opener (popup) rồi tới tab cùng chế độ dùng gần nhất; Undo chọn lại tab vừa đóng nếu tab hiện tại vẫn là tab được chọn thay. `lastTabId` chỉ tab thường; tab nhớ bị đóng → tab thường gần nhất còn lại hoặc null.
7. Phiên incognito kết thúc (finalize token Ending, hoặc tab incognito mới lúc Ending) thì mọi tab incognito còn chờ Undo trong mọi token cũng bị finalize; token trộn thường + incognito giữ phần tab thường.
8. `newTab` chỉ nhận http(s) hoặc `about:blank` (popup), URL khác mở New tab page; `load` chỉ nhận http(s) (chặn `javascript:`/`file:` qua `loadUrl`). URL lưu Room ≤ 8.192 ký tự: bỏ fragment, rồi bỏ query, không được thì null (state file vẫn còn trang); state giữ URL đầy đủ.
9. History: chỉ tab thường, http(s) ≤ 8.192, không ghi trang lỗi (error page cũng gọi `doUpdateVisitedHistory`); title rỗng cho document mới (title được xoá lúc page started), title hiện tại cho điều hướng cùng document; tối đa 5 lần cập nhật title mỗi lượt truy cập (title động).
10. `isSameDocument` = không có `onPageStarted` từ lần visit trước. Đã đối chiếu nguồn Chromium (`AwWebContentsObserver`, `AwContentsClientBridge`): `onPageStarted` được post lúc commit, trước `doUpdateVisitedHistory`; tải lỗi post `onPageStarted` → `onReceivedError` → `onPageFinished`, rồi error page commit gọi `doUpdateVisitedHistory`.
11. Highlight so URL đã commit bỏ fragment + nonce; JSON tham số escape thêm U+2028/U+2029, `<`, `>`, `&`; blockId chỉ chữ số ASCII 1–9 ký tự; cited cắt 1.000 ký tự.
12. `PageContent`: nhận object hoặc chuỗi JSON bọc object; JSON > 2M ký tự bị từ chối; text bỏ ký tự điều khiển, bidi, zero-width, soft hyphen, tag Unicode; tag ngoài danh sách thành `p`; `lang` phải giống BCP 47; byline/siteName ≤ 256.
13. `HitTarget` có url/src > 64 KB bị bỏ (không mở 05d), text/alt cắt 1.024: target đi vào saved state của back stack.
14. `Crashed.codeName` = `RENDERER_CRASHED` hoặc `RENDERER_KILLED` (`didCrash == false`); ngân sách dùng đồng hồ monotonic; Try again (`reload`) xoá lỗi Crashed.
15. Mạng trở lại (cạnh lên của `validated`, giữ 2 s) → reload tab hiện tại đang NoInternet; tab khác reload khi `select` lúc mạng đã validated; `attach` cũng reload tab hiện tại NoInternet khi mạng đã validated.
16. ExternalIntents: `about:`, `blob:`, `data:` ở lại WebView (scheme nội bộ của engine, để chính sách Chromium xử lý); intent trỏ tới dữ liệu `file:`/`content:` bị chặn; giữ `package`.
17. DownloadNames: MIME chung (`application/octet-stream` và biến thể, `text/plain`) giữ phần mở rộng của tên; MIME cụ thể giữ phần mở rộng nếu nó ánh xạ đúng MIME đó, không thì thay hoặc thêm phần mở rộng ưu tiên của MIME; `"*:<>?|` → `_`; tối đa 200 byte UTF-8.
18. TabStateStore: header `"SKTS"`, format 1, `package/versionName` của WebView, `SDK_INT`, độ dài, CRC32, payload; đọc xong ép unparcel (`Bundle.size()`) trong try; payload ≤ 2 MB; tab id phải khớp `[A-Za-z0-9_-]{1,64}`; save/delete/load theo đúng thứ tự gọi.
19. ThumbnailStore: thumbnail tab thường giữ trong bộ nhớ sau khi được put/đọc tới khi delete/purge/clearAll; incognito LRU 16 MB, bị đẩy ra thì flow về null.
20. IncognitoSession: tên `incognito-<ms>` tăng chặt và bỏ qua tên đã có; `setDecision` bị bỏ qua khi không có phiên; `end()` đặt None trước, mỗi bước xoá chạy riêng (lỗi chỉ log).

## Lệch spec

- `IncognitoSession` nhận thêm `profiles: BrowserProfiles` (cùng `ioDispatcher`, `clock`); spec ghi `(appScope, sharedDir)`. Cần để test được và để bản androidx.webkit ở lượt 4B cắm vào.
- `TabManager` nhận thêm `scripts: PageScriptSource` (bắt buộc) và các tham số cho test; AppContainer (lượt 4D) truyền bản đọc asset.
- Sink là thuộc tính `TabManager.events` thay vì để `TabManager` implement trực tiếp, để API lệnh của UI không lẫn callback WebView.
- Sink không có `onSecurity`/`onReaderable`: `isSecure` tính từ URL https đã commit, readerable do TabManager tự chạy. Có thêm `onStateSaved`/`onWebViewDestroyed`.
- `Tab` có thêm `createdAt` (TabEntity cần, thứ tự trong DAO dùng nó).
- Fallback của `restore()` còn tắt ghi tab vào Room (quyết định 4).

## Concerns

- `restore()` cần được gọi trước các lệnh khác; lệnh đến trước được giữ (gộp sau tab khôi phục) nhưng không đánh lại vị trí.
- Ở chế độ Fallback, tab của phiên đó mất sau process death (cố ý, để không làm hỏng dữ liệu chưa đọc được).
- Bộ nhớ thumbnail tab thường tăng theo số tab đã quan sát; với ~50 tab là khoảng 15 MB native.
- Highlight vẫn còn khe nhỏ: điều hướng commit trong renderer sau lúc kiểm ở Kotlin và trước lúc script chạy; lượt 4B nên dùng `JavaScriptExecutionWorld` và để script tự kiểm `location` nếu có thể.
- Cho phase 11: `returnToNewTabPage` không gỡ `chatId` và không phát `Navigated`; `PageContent.url` có thể dài tới 2 MB (giới hạn của Chromium), prompt phải tự cắt.
- Thứ tự callback WebView mới kiểm qua nguồn Chromium, chưa chạy trên thiết bị; test instrumented ở lượt 4D nên khẳng định lại.
- Lúc viết, vài escape `\uXXXX` trong literal Kotlin bị lưu thành ký tự thật (có cả U+202E trong KDoc); đã quét và thay hết bằng escape hoặc chữ "U+XXXX", script quét xác nhận còn 0 dòng.

## Câu hỏi chưa giải quyết

1. Fallback của `restore()`: giữ "không ghi gì" hay cần đọc lại ngầm rồi gộp tab khi DB trả lời muộn?
2. Điều hướng top-level tới `data:`/`blob:`/`about:` cho WebView xử lý (như hiện tại) hay chặn hẳn như `file:`?
3. Ảnh `data:` lớn hơn 64 KB khi nhấn giữ hiện bị bỏ (không có 05d); phase 7 có cần đường khác (giữ target trong bộ nhớ, key chỉ mang id) không?
