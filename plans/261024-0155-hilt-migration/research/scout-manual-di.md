# Manual DI inventory

Read-only inventory of the process container and the Activity graph. No Hilt graph is proposed here.

Repo root: `/Users/dan/Desktop/Development/Android-Kotlin/skim-browser`.

Counts:

- ViewModel classes: **23** (24 production `viewModel { }` construction sites; `TabsViewModel` has two).
- Production files that construct, hold, or take `AppContainer`: **25**. This excludes `AppContainer.kt` itself and `AppStorage.kt` (KDoc only).
- Test files that call `AppContainer(` or read `app.container`: **10** (6 unit, 4 androidTest). `DomainBoundaryTest` names the source file and does neither.

`AppContainer` is the only composition root. It is created once per process in `SkimApplication.onCreate` with the default `AppStorage()`. Nothing in `main` imports `javax.inject`, `jakarta.inject`, `dagger`, `hilt`, or kotlin-inject.

## 1. AppContainer bindings

File: `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`.

```117:117:app/src/main/java/com/dantech/skimbrowser/AppContainer.kt
class AppContainer(val app: Application, storage: AppStorage = AppStorage()) : Closeable {
```

`app` is a public eager `Application`. `storage` is a constructor parameter, not a property. Lazy blocks close over it. There is no `storage` field for reflection.

`storage` reaches these bindings only: `database` (`storage.databaseName`), `dataStore` (`storage.settingsFile(app)`), `incognitoSession` (`storage.incognitoSharedDir(app)`), `tabStateStore` (`storage.tabStateDir(app)`), `thumbnails` (`storage.thumbnailDir(app)`).

### Eager

| Member | Visibility | Type | Line | Notes |
|---|---|---|---|---|
| `app` | public val | `Application` | 117 | Constructor argument. |
| `lastError` | public val | `MutableStateFlow<Throwable?>` | 123 | `MutableStateFlow(null)`. No reader outside this file. KDoc at 120–122 says the shell reads it. `SkimApp` does not. |
| `appScope` | public val | `CoroutineScope` | 129–134 | `SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler`. External: `Dispatchers.Default`. Handler writes `lastError`. |
| `settingsJob` | private val | `SupervisorJob` | 137 | Parent is `appScope.coroutineContext.job`. |
| `closed` | private val | `AtomicBoolean` | 153 | Starts `false`. |

`init` at 394–396 is eager and not a binding: `appScope.launch(Dispatchers.IO) { purgeOldShares(app.cacheDir) }`. External: `Dispatchers.IO`, `app.cacheDir`.

Private companion at 417–419: `TAG = "AppContainer"`, `HTTP_TIMEOUT_SECONDS = 15L`.

### Lazy holders and private stores

These are `by lazy` or `= lazy { }`. First read creates them once.

| Member | Visibility | Type | Line | External args (not other container vals) |
|---|---|---|---|---|
| `databaseHolder` | private | `Lazy<SkimDatabase>` | 139 | `SkimDatabase.open(app, storage.databaseName)`. `Application`, storage name. |
| `dataStoreHolder` | private | `Lazy<DataStore<Preferences>>` | 140–145 | `SettingsStore.createDataStore`. Scope adds `Dispatchers.IO`. File is `storage.settingsFile(app)`. |
| `networkMonitorHolder` | private | `Lazy<ConnectivityNetworkMonitor>` | 146 | `ConnectivityNetworkMonitor(app)`. |
| `httpClientHolder` | private | `Lazy<OkHttpClient>` | 147–152 | Builder only. Connect and read timeout `HTTP_TIMEOUT_SECONDS` (15) `TimeUnit.SECONDS`. No interceptor. |
| `settingsStore` | private | `SettingsStore` | 159 | `SettingsStore(dataStore)`. Container only. |
| `tabStore` | private | `TabStore` | 164 | `TabStore(database)`. |
| `historyStore` | private | `HistoryStore` | 168 | `HistoryStore(database.historyDao(), database)`. |
| `bookmarkStore` | private | `BookmarkStore` | 172 | `BookmarkStore(database.bookmarkDao())`. |
| `shortcutStore` | private | `ShortcutStore` | 176 | `ShortcutStore(database.shortcutDao())`. |
| `chatStore` | private | `ChatStore` | 184 | `ChatStore(database.skimChatDao(), database)`. |
| `siteStore` | private | `SitePermissionStore` | 188–190 | `SitePermissionStore(database.sitePermissionDao(), database)`. |

The repository getters are not themselves lazy. They return the private store, so the first get builds that store:

| Getter | Type | Line | Returns |
|---|---|---|---|
| `settings` | `SettingsRepository` | 162 | `settingsStore` |
| `tabRepository` | `TabRepository` | 166 | `tabStore` |
| `historyRepository` | `HistoryRepository` | 170 | `historyStore` |
| `bookmarkRepository` | `BookmarkRepository` | 174 | `bookmarkStore` |
| `shortcutRepository` | `ShortcutRepository` | 178 | `shortcutStore` |
| `chatRepository` | `ChatRepository` | 186 | `chatStore` |
| `sitePermissionRepository` | `SitePermissionRepository` | 192 | `siteStore` |

### Public lazy vals

"Container only" means every constructor argument is another member of this container (or a lambda over one).

| Val | Type | Line | External args |
|---|---|---|---|
| `database` | `SkimDatabase` | 155 | Delegates to `databaseHolder`. |
| `dataStore` | `DataStore<Preferences>` | 157 | Delegates to `dataStoreHolder`. |
| `removeHistoryEntry` | `RemoveHistoryEntry` | 194 | Container only (`historyRepository`). |
| `restoreHistoryEntry` | `RestoreHistoryEntry` | 195 | Container only. |
| `matchHistory` | `MatchHistory` | 196 | Container only. |
| `addBookmark` | `AddBookmark` | 197 | Container only. |
| `updateBookmark` | `UpdateBookmark` | 198 | Container only. |
| `moveBookmark` | `MoveBookmark` | 199 | Container only. |
| `deleteBookmark` | `DeleteBookmark` | 200 | Container only. |
| `restoreBookmark` | `RestoreBookmark` | 201 | Container only. |
| `matchBookmarks` | `MatchBookmarks` | 202 | Container only. |
| `addShortcut` | `AddShortcut` | 203 | Container only. |
| `updateShortcut` | `UpdateShortcut` | 204 | Container only. |
| `removeShortcut` | `RemoveShortcut` | 205 | Container only. |
| `restoreShortcut` | `RestoreShortcut` | 206 | Container only. |
| `setSearchEngine` | `SetSearchEngine` | 207 | Container only (`settings`). |
| `setTheme` | `SetTheme` | 208 | Container only. |
| `setAskBeforeSending` | `SetAskBeforeSending` | 209 | Container only. |
| `setKeepChatHistory` | `SetKeepChatHistory` | 210 | Container only. |
| `setReaderTextScale` | `SetReaderTextScale` | 211 | Container only. |
| `setReaderFont` | `SetReaderFont` | 212 | Container only. |
| `completeOnboarding` | `CompleteOnboarding` | 213 | Container only. |
| `loadSavedChat` | `LoadSavedChat` | 214 | Container only (`chatRepository`). |
| `createSavedChat` | `CreateSavedChat` | 215 | Container only. |
| `readSavedMessages` | `ReadSavedMessages` | 216 | Container only. |
| `addSavedMessage` | `AddSavedMessage` | 217 | Container only. |
| `updateSavedChat` | `UpdateSavedChat` | 218 | Container only. |
| `updateSavedMessage` | `UpdateSavedMessage` | 219 | Container only. |
| `deleteAllSkimChats` | `DeleteAllSkimChats` | 220 | Container only. |
| `removeSkimChat` | `RemoveSkimChat` | 221 | Container only. |
| `restoreSkimChat` | `RestoreSkimChat` | 222 | Container only. |
| `setSkimAi` | `SetSkimAi` | 223 | Container only (`sitePermissionRepository`). |
| `setSitePermission` | `SetSitePermission` | 224 | Container only. |
| `httpClient` | `OkHttpClient` | 232 | Delegates to `httpClientHolder`. |
| `networkMonitor` | `NetworkMonitor` | 235 | Delegates to `networkMonitorHolder`. |
| `browserProfiles` | `BrowserProfiles` | 238 | `WebViewProfiles()`. No args. |
| `incognitoSession` | `IncognitoSession` | 240–242 | `storage.incognitoSharedDir(app)` plus `browserProfiles`, `appScope`. |
| `tabStateStore` | `TabStateStore` | 244–246 | `storage.tabStateDir(app)`, `{ TabStateStore.webViewVersionOf(app) }`, `appScope`. |
| `thumbnails` | `ThumbnailStore` | 248 | `storage.thumbnailDir(app)`, `appScope`. |
| `pageScripts` | `PageScriptSource` | 250 | `PageScripts(app.assets) { PageScripts.runOnDevice(app) }`. |
| `downloadTracker` | `DownloadTracker` | 253 | `AndroidSystemDownloads(app)`, `appScope`. |
| `tabManager` | `TabManager` | 255–267 | Container only: `tabRepository`, `historyRepository`, `settings`, `incognitoSession`, `tabStateStore`, `thumbnails`, `networkMonitor`, `appScope`, `pageScripts`. |
| `openUrlInCurrentTab` | `OpenUrlInCurrentTab` | 270 | Container only (`tabManager`). |
| `undoRegistry` | `UndoRegistry` | 273 | Container only (`appScope`). |
| `snackbarController` | `SnackbarController` | 276 | Container only (`undoRegistry`). |
| `pendingUndo` | `PendingUndo` | 279 | `SnackbarUndo(undoRegistry, snackbarController)`. |
| `readerHighlights` | `ReaderHighlights` | 282 | `ReaderHighlights()`. No args. |
| `bookmarkImporter` | `BookmarkFiles` | 285 | `BookmarkImporter(app.contentResolver, bookmarkStore)`. |
| `importBookmarks` | `ImportBookmarks` | 287 | Container only (`bookmarkImporter`). |
| `suggestionService` | `SuggestionService` | 290 | Container only (`httpClient`). |
| `shareImage` | `ShareImage` | 293–300 | `app.cacheDir`, plus `httpClient`, `incognitoSession`, `browserProfiles`. |
| `browsingDataCleaner` | `BrowsingDataCleaner` | 303–313 | Container only: `historyRepository`, `tabRepository`, `tabManager`, `tabStateStore`, `thumbnails`, `incognitoSession`, `pendingUndo`. |
| `claudeClients` | `ClaudeClientProvider` | 319–325 | `BuildConfig.AI_BASE_URL`, `(BuildConfig.ANTHROPIC_API_KEY as String?).takeIf { !it.isNullOrBlank() }`, `settings::installId`. |
| `skimAi` | `SkimAiService` | 328 | Container only: `claudeClients`, `sitePermissionRepository`, `settings`. |
| `summarizePage` | `SummarizePage` | 330 | Container only (`skimAi`). |
| `askPage` | `AskPage` | 332 | Container only. |
| `retryAnswer` | `RetryAnswer` | 334 | Container only. |
| `overviewTabs` | `OverviewTabs` | 336 | Container only. |
| `askTabs` | `AskTabs` | 338 | Container only. |
| `collectOpenTabs` | `CollectOpenTabs` | 340 | Container only: `tabManager`, `sitePermissionRepository`. |
| `chatSessions` | `ChatSessionStore` | 343–355 | Container only: `chatRepository`, `createSavedChat`, `readSavedMessages`, `addSavedMessage`, `updateSavedChat`, `updateSavedMessage`, `tabManager`, `settings`, `appScope`. |
| `tabGrouping` | `TabGrouping` | 358–367 | Container only: `skimAi`, `tabManager`, `tabRepository`, `sitePermissionRepository`, `networkMonitor`, `appScope`. |
| `aiGate` | `AiGate` | 370 | Container only: `sitePermissionRepository`, `settings`, `setSkimAi`. |
| `clearBrowsingData` | `ClearBrowsingData` | 373–376 | Lambda forwards to `browsingDataCleaner.clearAll`. |
| `aiDataEraser` | `AiDataEraser` | 380–386 | Container only: `deleteAllSkimChats`, `sitePermissionRepository`, `pendingUndo`. |
| `deleteAllAiData` | `DeleteAllAiData` | 389 | Container only (`aiDataEraser`). |
| `clearSkimHistory` | `ClearSkimHistory` | 392 | Container only (`aiDataEraser`). |

Public vals with no production read outside this file: `lastError`, `database`, `dataStore`, `pageScripts`, `pendingUndo`, `bookmarkImporter`, `claudeClients`, `skimAi`, `aiDataEraser`. They are used inside the container, and some are read by tests (`database`, `dataStore`, `pageScripts`). `dataStore` is also read by `SkimAppRule`.

### close()

```405:415:app/src/main/java/com/dantech/skimbrowser/AppContainer.kt
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (networkMonitorHolder.isInitialized()) networkMonitorHolder.value.close()
        if (httpClientHolder.isInitialized()) {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
        if (databaseHolder.isInitialized()) database.close()
        appScope.cancel()
        runBlocking { settingsJob.join() }
    }
```

Idempotent. Does not close `tabManager`, `incognitoSession`, `tabStateStore`, `thumbnails`, or `downloadTracker` itself. Cancelling `appScope` is what those scopes see. Blocks until `settingsJob` joins.

### Reflection (`getDeclaredField`)

Two tests reflect on lazy fields. Kotlin `by lazy` properties reflect as `name$delegate`. A `val holder = lazy { }` reflects as the holder name.

`app/src/test/java/com/dantech/skimbrowser/AppContainerBrowserTest.kt` 137–139, `made()`:

- `"httpClientHolder"` (142, 57)
- `"browserProfiles\$delegate"` (42)
- `"downloadTracker\$delegate"` (42)

`app/src/test/java/com/dantech/skimbrowser/search/SuggestionServiceTest.kt` 412–415, same helper:

- `"suggestionService\$delegate"` (360)
- `"httpClientHolder"` (361)

`app/src/test/java/com/dantech/skimbrowser/system/ShareImageTest.kt` 42 reflects `FileProvider.sCache`, not `AppContainer`.

## 2. AppStorage

File: `app/src/main/java/com/dantech/skimbrowser/AppStorage.kt`. One type, two ways to build it.

Production path is the default constructor, used because `SkimApplication` calls `AppContainer(this)` and the storage parameter defaults to `AppStorage()` (`AppContainer.kt` 117, `SkimApplication.kt` 13).

```12:32:app/src/main/java/com/dantech/skimbrowser/AppStorage.kt
data class AppStorage(
    val databaseName: String = "skim.db",
    val settingsFile: (Context) -> File = { it.preferencesDataStoreFile("skim_settings") },
    val tabStateDir: (Context) -> File = { File(it.filesDir, "tabstate") },
    val thumbnailDir: (Context) -> File = { File(it.cacheDir, "thumbs") },
    val incognitoSharedDir: (Context) -> File = { File(it.cacheDir, "shared/incognito") },
) {
    companion object {
        fun temp(dir: File) = AppStorage(
            databaseName = File(dir, "skim.db").path,
            settingsFile = { File(dir, "skim_settings.preferences_pb") },
            tabStateDir = { File(dir, "tabstate") },
            thumbnailDir = { File(dir, "thumbs") },
            incognitoSharedDir = { File(dir, "shared/incognito") },
        )
    }
}
```

`temp` ignores `Context` and puts every file under `dir`. No other functions. `AppContainerBrowserTest.theDefaultStorageIsTheAppsPrivateStorage` (126–132) asserts the default directory functions and does not construct a container.

## 3. Production call sites that take AppContainer

25 files. Members below are the ones that file reads. A file that only forwards `container` into another function is marked.

Construction and ownership:

- `app/src/main/java/com/dantech/skimbrowser/SkimApplication.kt` 8–15. Holds `lateinit var container`. `onCreate` assigns `AppContainer(this)` and launches `container.appScope` / `container.tabManager.restore()`. Members: `appScope`, `tabManager`.
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt` 49. Getter `(application as SkimApplication).container`. Does not take `AppContainer` on the activity. `createBrowserHost` does (162). Members read on the activity: `downloadTracker` (73, 120), `tabManager` (84, 136, 142), `browsingDataCleaner` (85), `incognitoSession` (87). `createBrowserHost` also reads `tabManager`, `sitePermissionRepository`, `incognitoSession`, `appScope`, `browserProfiles`, `downloadTracker`, `httpClient`, `tabStateStore`, `thumbnails`.

Shell:

- `ui/shell/SkimApp.kt`. `SkimApp` 60–65 and `startUp` 138. Members: `settings`, `tabManager`, `incognitoSession`, `snackbarController`, `aiGate`, `downloadTracker`, `undoRegistry`, `appScope` is not read here. Navigator scope is `rememberCoroutineScope()` (80), not `appScope`.
- `ui/navigation/SkimNavDisplay.kt`. `skimEntries` 88. Direct read: `tabManager` (70). Forwards the same instance into every entry except `WelcomeKey` (91) and `LicensesKey` (159). Entry lines: Setup 99, Reader 101, Browser 103–107, Shortcut 111, More 114, Link options 123, Site info 126, Ask tabs 131, Ask page 139, AI permission 142, Edit bookmark 145, Move 148, Tabs 150, Settings 151, AI privacy 152, History 153, Skim history 154, Clear skim history 156, Bookmarks 158, Search engine 161, Theme 164, Clear browsing data 167, Delete AI data 170, Rename group 173.

Routes, one file per group. Parameter type at the route is `AppContainer` unless noted.

| File | Function | Line | Members read |
|---|---|---|---|
| `ui/browser/BrowserRoute.kt` | `BrowserRoute` | 82 | `tabManager` (88). `Tab` and `TabRuntime` stay in the composable; they are not container members. |
| `ui/browsing/BrowsingRoute.kt` | `BrowsingRoute` | 43 | `tabManager` (44). Also calls `searchViewModel(container)` (50). |
| `ui/home/NewTabPageRoute.kt` | `NewTabPageRoute` | 40 | `tabManager` (41), then `searchViewModel(container)` (43), then Home: `shortcutRepository`, `removeShortcut`, `restoreShortcut`, `chatRepository`, `snackbarController`, `undoRegistry`, `appScope` (85–91). |
| `ui/search/SearchOverlay.kt` | `searchViewModel` | 84 | `settings`, `suggestionService`, `matchHistory`, `matchBookmarks` (86–89). |
| `ui/onboarding/SetupScreen.kt` | `SetupRoute` | 149 | `importBookmarks`, `completeOnboarding`, `snackbarController`, `appScope` (155–158). |
| `ui/settings/SettingsScreen.kt` | `SettingsRoute` | 48 | `settings`, `setSearchEngine`, `setTheme` (52–54). |
| same | `SearchEngineRoute` | 91 | `settings`, `appScope`, `setSearchEngine` (92, 96). |
| same | `ThemeRoute` | 105 | `settings`, `appScope`, `setTheme` (106, 110). |
| same | `ClearBrowsingDataRoute` | 122 | `clearBrowsingData`, `appScope` (125). Pool comes from `LocalBrowserHost`, not the container. |
| `ui/settings/AiPrivacyScreen.kt` | `AiPrivacyRoute` | 40 | `settings`, `setAskBeforeSending`, `setKeepChatHistory`, `chatRepository` (43–46). |
| same | `DeleteAiDataRoute` | 65 | `deleteAllAiData`, `appScope` (66). |
| `ui/history/HistoryScreen.kt` | `HistoryRoute` | 49 | `historyRepository`, `removeHistoryEntry`, `restoreHistoryEntry`, `snackbarController`, `undoRegistry`, `openUrlInCurrentTab` (52–57). |
| `ui/bookmarks/BookmarksScreen.kt` | `BookmarksRoute` | 53 | `bookmarkRepository`, `deleteBookmark`, `restoreBookmark`, `snackbarController`, `undoRegistry`, `openUrlInCurrentTab` (57–62). |
| `ui/bookmarks/BookmarkDialogs.kt` | `EditBookmarkRoute` | 253 | `bookmarkRepository`, `updateBookmark`, `appScope` (255). |
| same | `MoveToFolderRoute` | 266 | `bookmarkRepository`, `moveBookmark`, `appScope` (268). |
| `ui/home/ShortcutDialog.kt` | `ShortcutDialogRoute` | 39 | `shortcutRepository`, `addShortcut`, `updateShortcut`, `appScope` (43–46). |
| `ui/tabs/TabsScreen.kt` | `TabsRoute` | 108 | `tabManager`, `snackbarController`, `undoRegistry`, `tabGrouping`, `appScope` (112–117), `thumbnails` (175). |
| `ui/tabs/RenameGroupDialog.kt` | `RenameGroupRoute` | 26 | `tabManager`, `snackbarController`, `undoRegistry` (27, 32–34). Does not pass `tabGrouping` or `appScope`. |
| `ui/skimhistory/SkimHistoryScreen.kt` | `SkimHistoryRoute` | 52 | `chatRepository`, `removeSkimChat`, `restoreSkimChat`, `snackbarController`, `undoRegistry` (55–59). |
| `ui/skimhistory/ClearSkimHistoryDialog.kt` | `ClearSkimHistoryRoute` | 34 | `clearSkimHistory`, `appScope` (35). |
| `ui/ai/AskPageRoute.kt` | `AskPageRoute` | 26 | `tabManager`, `chatSessions`, `summarizePage`, `askPage`, `retryAnswer`, `networkMonitor`, `readerHighlights`, `loadSavedChat`, `appScope` (30–39). |
| same | `AiPermissionRoute` | 82 | `tabManager` (83), `setSkimAi` (86). |
| `ui/ai/AskTabsSheet.kt` | `AskTabsRoute` | 83 | `chatSessions`, `overviewTabs`, `askTabs`, `collectOpenTabs`, `networkMonitor`, `appScope` (87–93). |
| `ui/reader/ReaderRoute.kt` | `ReaderRoute` | 27 | `tabManager`, `settings`, `setReaderTextScale`, `setReaderFont`, `appScope` (31–35), `tabManager` again (39–40), `readerHighlights` (63). |
| `ui/browsing/LinkOptionsSheet.kt` | `LinkOptionsRoute` | 172 | `tabManager` (174, 181), `snackbarController` (188, 197, 221), `shareImage` (220), `appScope` (223). |
| `ui/browsing/SiteInfoSheet.kt` | `SiteInfoRoute` | 241 | `tabManager`, `sitePermissionRepository`, `settings`, `setSkimAi`, `setSitePermission`, `incognitoSession`, `browsingDataCleaner`, `appScope` (245–252). |
| `ui/browsing/MoreMenuSheet.kt` | `MoreMenuRoute` | 205 | `tabManager`, `bookmarkRepository`, `addBookmark`, `snackbarController`, `appScope` (209–213). |

## 4. Activity-scoped graph

These objects are not fields of `AppContainer`. KDoc at `AppContainer.kt` 113–115 says the pool, the browser host, and the navigator stay off the container.

### Where they are built

`MainActivity.onCreate` (`MainActivity.kt` 77–94):

1. `activityHost = ActivityResultHost(this)` (82). Closes over the `ComponentActivity`.
2. `browserHost = createBrowserHost(this, container, activityHost)` (83).
3. `container.tabManager.attach(browserHost.pool)` (84).
4. `setContent` provides `LocalBrowserHost` and calls `SkimApp(container, browserHost, pendingLinks, onNavigator)` (91–92).

`onDestroy` (134–144): if the activity is finishing, `tabManager.finalizePendingCloses()`; then `browserHost.destroy()`, `activityHost.destroy()`, `tabManager.detach(browserHost.pool)`.

`createBrowserHost` is `internal` at `MainActivity.kt` 162–196. Production call: line 83. Test calls: `BrowsingRouteTest.kt` 148 and `BrowserRig.kt` 94.

It builds, in order, closing over `activity`, `activityHost`, and the container members named:

- `FullscreenController(activity)` (163).
- `SitePermissionBridge(context = activity.applicationContext, tabs = container.tabManager.state, repository = container.sitePermissionRepository, incognitoSession = container.incognitoSession, activityHost, scope = container.appScope)` (164–171).
- `BlobBridge(activity.assets)` (172). Assets, not `container.pageScripts`.
- `MediaStoreDownloads(activity.applicationContext)` (173).
- `DownloadRouter(...)` (174–184): `container.tabManager`, `container.incognitoSession`, `container.browserProfiles`, `container.downloadTracker`, `InProcessDownloader({ container.httpClient }, store)`, the MediaStore, the blob bridge, `activityHost`, `container.appScope`. The downloader lambda keeps the container.
- `WebViewPool(...)` (185–195): `activity`, `container.tabManager`, `container.incognitoSession`, `container.tabStateStore`, `container.thumbnails`, `activityHost`, `fullscreen`, `sitePermissions`, `downloads`. Defaults in `WebViewPool.kt` 71–76: `isLowRamDevice` from `ActivityManager`, `mainDispatcher = Dispatchers.Main.immediate`, `timers = WebViewTimers.process`, `drawThumbnail = ::captureThumbnail`.
- Returns `BrowserHost(pool, sitePermissions, downloads, blob, activityHost, fullscreen, WindowSecureController(activity))` (196).

### BrowserHost

`app/src/main/java/com/dantech/skimbrowser/browser/BrowserHost.kt` 16–38. Data class. Holds `pool`, `sitePermissions`, `downloads`, `blob`, `activityHost`, `fullscreen`, `secure`. `destroy()` (32–36) calls `sitePermissions.destroy()`, `blob.destroy()`, `downloads.destroy()`, `pool.destroyAll()`. It does not destroy `activityHost` or `fullscreen`. `LocalBrowserHost` is a `staticCompositionLocalOf` (41).

### WebViewPool

Class at `browser/WebViewPool.kt` 61–77. Production construction is only `createBrowserHost`. Unit tests construct it directly in `browser/WebViewPoolTest.kt` (826, 846) and `browser/ui/WebViewHostTest.kt` (137), without `AppContainer`.

### SkimNavigator

Class at `ui/navigation/SkimNavigator.kt` 34–42. Constructor closes over:

- `backStack: NavBackStack<NavKey>`
- `tabs: TabsController`
- `incognito: IncognitoSupport`
- `secure: WindowPrivacy`
- `snackbar: SnackbarController`
- `aiGate: AskPageAccess`
- `scope: CoroutineScope`

The only production construction is `SkimApp.kt` 84–92, inside `remember(backStack, scope)`:

- `backStack` from `rememberNavBackStack` (79)
- `tabs` = `container.tabManager` (77)
- `container.incognitoSession` (87)
- `host.secure` (88), the Activity `WindowSecureController`, not a container val
- `container.snackbarController` (89)
- `container.aiGate` (90)
- `scope` = `rememberCoroutineScope()` (80), not `container.appScope`

`MainActivity` stores that instance in `navigator` via `onNavigator` (`MainActivity.kt` 60, 92; `SkimApp.kt` 113–115).

## 5. ViewModels

23 classes, all `: ViewModel()`. None is an `AndroidViewModel`. Parameter lists are the constructor as declared. Call sites are the production `viewModel` factories.

(a) is a container member passed in. (b) is a nav or runtime argument: tab id, nav key, incognito flag, `SavedStateHandle`, or `TabRuntime`. No ViewModel constructor takes `TabRuntime`. `BrowserRoute` (82–86) and `BrowsingRoute` (43) receive `TabRuntime` and keep it in the composable.

Anything else (BuildConfig, `VoiceInput`, `DefaultBrowser`, clock defaults) is listed as other. Defaults the call site does not pass are marked "default, not passed".

### Keyed factories

`AskPageViewModel` — `ui/ai/AskPageViewModel.kt` 110–122.

- `key: AskPageKey`, `tabs: TabsController`, `sessions: PageChats`, `summarizePage: SummarizePage`, `askPage: AskPage`, `retryAnswer: RetryAnswer`, `network: NetworkAvailability`, `readerHighlights: ReaderHighlights`, `loadSavedChat: LoadSavedChat`, `savedState: SavedStateHandle`, `appScope: CoroutineScope`.
- (a) `tabManager`, `chatSessions`, `summarizePage`, `askPage`, `retryAnswer`, `networkMonitor`, `readerHighlights`, `loadSavedChat`, `appScope`.
- (b) `key`, `createSavedStateHandle()`.
- Call: `ui/ai/AskPageRoute.kt` 27, `viewModel(key = "ask-${key.tabId}-${key.savedChatId}-${key.fromReader}")`.

`AskTabsViewModel` — `ui/ai/AskTabsViewModel.kt` 90–99.

- `key: AskTabsKey`, `sessions: PageChats`, `overviewTabs: OverviewTabs`, `askTabs: AskTabs`, `collectOpenTabs: CollectOpenTabs`, `network: NetworkAvailability`, `savedState: SavedStateHandle`, `appScope: CoroutineScope`.
- (a) `chatSessions`, `overviewTabs`, `askTabs`, `collectOpenTabs`, `networkMonitor`, `appScope`.
- (b) `key`, `createSavedStateHandle()`.
- Call: `ui/ai/AskTabsSheet.kt` 84, `viewModel(key = "ask-tabs-${key.prompt}-${key.savedChatId}")`.

`AiPermissionViewModel` — `ui/ai/AiPermissionViewModel.kt` 10–12.

- `setSkimAi: SetSkimAi`.
- (a) `setSkimAi`. (b) none in the constructor. The route key is only the factory key. Host is read from `container.tabManager` in the composable (`AskPageRoute.kt` 83–84).
- Call: `ui/ai/AskPageRoute.kt` 85, `viewModel(key = "allow-${key.tabId}")`.

`BookmarksViewModel` — `ui/bookmarks/BookmarksViewModel.kt` 82–90.

- `folderId: Long?`, `bookmarks: BookmarkRepository`, `deleteBookmark: DeleteBookmark`, `restoreBookmark: RestoreBookmark`, `snackbar: SnackbarController`, `undo: UndoRegistry`, `openUrl: OpenUrlInCurrentTab`.
- (a) `bookmarkRepository`, `deleteBookmark`, `restoreBookmark`, `snackbarController`, `undoRegistry`, `openUrlInCurrentTab`.
- (b) `key.folderId`.
- Call: `ui/bookmarks/BookmarksScreen.kt` 54, `viewModel(key = "bookmarks-${key.folderId}")`.

`BrowsingViewModel` — `ui/browsing/BrowsingViewModel.kt` 95–98.

- `tabId: String`, `tabs: TabsController`.
- (a) `tabManager`. (b) `tab.id`. Not `TabRuntime`.
- Call: `ui/browsing/BrowsingRoute.kt` 48, `viewModel(key = "$BROWSING_VIEW_MODEL_KEY:${tab.id}")`. Constant `BROWSING_VIEW_MODEL_KEY = "browsing"` at line 109, so the key is `browsing:<tabId>`.

`HomeViewModel` — `ui/home/HomeViewModel.kt` 79–91.

- `tabs: TabsController`, `shortcuts: ShortcutRepository`, `removeShortcut: RemoveShortcut`, `restoreShortcut: RestoreShortcut`, `chats: ChatRepository`, `snackbar: SnackbarController`, `undo: UndoRegistry`, `appScope: CoroutineScope`, `voiceAvailable: Boolean`, `clock: () -> Long = System::currentTimeMillis`, `zone: () -> ZoneId = ZoneId::systemDefault`.
- (a) `tabManager`, `shortcutRepository`, `removeShortcut`, `restoreShortcut`, `chatRepository`, `snackbarController`, `undoRegistry`, `appScope`.
- (b) none.
- Other: `voiceAvailable` from `VoiceInput.isAvailable` (`NewTabPageRoute.kt` 44). `clock` and `zone` default, not passed.
- Call: `ui/home/NewTabPageRoute.kt` 82, `viewModel(key = HOME_VIEW_MODEL_KEY)`. Constant `"home"` at line 139. Only the non-incognito branch constructs it (81).

`LinkOptionsViewModel` — `ui/browsing/LinkOptionsSheet.kt` 128–132.

- `tabId: String`, `target: HitTarget`, `tabs: TabsController`.
- (a) `tabManager`. (b) `key.tabId`, `key.target`.
- Call: same file 173, `viewModel(key = "link-options:${key.tabId}:${key.target}")`.

`ReaderViewModel` — `ui/reader/ReaderViewModel.kt` 41–48.

- `tabId: String`, `tabs: TabsController`, `settings: SettingsRepository`, `saveScale: SetReaderTextScale`, `saveFont: SetReaderFont`, `writes: CoroutineScope`.
- (a) `tabManager`, `settings`, `setReaderTextScale`, `setReaderFont`, `appScope` (passed as `writes`).
- (b) `key.tabId`.
- Call: `ui/reader/ReaderRoute.kt` 28, `viewModel(key = "reader-${key.tabId}")`.

`SearchViewModel` — `ui/search/SearchViewModel.kt` 76–81.

- `engine: Flow<SearchEngine>`, `suggestions: suspend (SearchEngine, String) -> List<String>`, `matchHistory: MatchHistory`, `matchBookmarks: MatchBookmarks`.
- (a) `settings.settings.map { it.searchEngine }`, `suggestionService::suggestions`, `matchHistory`, `matchBookmarks`.
- (b) none. Incognito is `SearchViewModel.open`, not a constructor arg.
- Call: `ui/search/SearchOverlay.kt` 84, `viewModel(key = SEARCH_VIEW_MODEL_KEY)`. Constant `"search"` at line 398. Callers: `NewTabPageRoute.kt` 43 and `BrowsingRoute.kt` 50. One store entry per browser destination.

`TabsViewModel` — `ui/tabs/TabsViewModel.kt` 80–89. Two factories.

- `incognito: Boolean`, `tabs: TabsController`, `snackbar: SnackbarController`, `undo: UndoRegistry`, `grouping: Flow<GroupingBannerState?> = flowOf(null)`, `grouper: TabGrouper? = null`, `appScope: CoroutineScope? = null`, `mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate`.
- Tabs screen, `ui/tabs/TabsScreen.kt` 109, `viewModel(key = "tabs-${key.incognito}")`. (a) `tabManager`, `snackbarController`, `undoRegistry`, `tabGrouping.banner`, `tabGrouping` or null, `appScope`. (b) `key.incognito`. `mainDispatcher` default, not passed.
- Rename dialog, `ui/tabs/RenameGroupDialog.kt` 29, `viewModel { }` with no key. (a) `tabManager`, `snackbarController`, `undoRegistry`. (b) `incognito = false` literal, not the nav key's flag. `grouping`, `grouper`, `appScope`, `mainDispatcher` default, not passed.

### Factories with no key

`viewModel { }` with no `key`. The store key is the ViewModel class default.

| Class | File:lines | Parameters | (a) container | (b) nav/runtime | Other | Call site |
|---|---|---|---|---|---|---|
| `AiPrivacyViewModel` | `ui/settings/AiPrivacyViewModel.kt` 30–35 | `settings: SettingsRepository`, `saveAskBeforeSending: SetAskBeforeSending`, `saveKeepChatHistory: SetKeepChatHistory`, `chats: ChatRepository` | `settings`, `setAskBeforeSending`, `setKeepChatHistory`, `chatRepository` | none | none | `AiPrivacyScreen.kt` 41 |
| `ClearBrowsingDataViewModel` | `ui/settings/ClearBrowsingDataViewModel.kt` 13–16 | `clearBrowsingData: ClearBrowsingData`, `appScope: CoroutineScope` | both | none. `OpenPages` is an argument of `clear()`, from `LocalBrowserHost.current.pool` (`SettingsScreen.kt` 123) | none | `SettingsScreen.kt` 124 |
| `ClearSkimHistoryViewModel` | `ui/skimhistory/ClearSkimHistoryViewModel.kt` 13–16 | `clearSkimHistory: ClearSkimHistory`, `appScope: CoroutineScope` | both | none | none | `ClearSkimHistoryDialog.kt` 35 |
| `DeleteAiDataViewModel` | `ui/settings/DeleteAiDataViewModel.kt` 9–12 | `deleteAllAiData: DeleteAllAiData`, `appScope: CoroutineScope` | both | none | none | `AiPrivacyScreen.kt` 66 |
| `EditBookmarkViewModel` | `ui/bookmarks/BookmarkDialogs.kt` 115–120 | `bookmarkId: Long`, `bookmarks: BookmarkRepository`, `updateBookmark: UpdateBookmark`, `appScope: CoroutineScope` | `bookmarkRepository`, `updateBookmark`, `appScope` | `key.bookmarkId` | none | same file 254 |
| `HistoryViewModel` | `ui/history/HistoryViewModel.kt` 50–59 | `history: HistoryRepository`, `removeHistoryEntry: RemoveHistoryEntry`, `restoreHistoryEntry: RestoreHistoryEntry`, `snackbar: SnackbarController`, `undo: UndoRegistry`, `openUrl: OpenUrlInCurrentTab`, `clock: () -> Long = System::currentTimeMillis`, `zone: ZoneId = ZoneId.systemDefault()` | `historyRepository`, `removeHistoryEntry`, `restoreHistoryEntry`, `snackbarController`, `undoRegistry`, `openUrlInCurrentTab` | none | `clock`, `zone` default, not passed | `HistoryScreen.kt` 50 |
| `MoreMenuViewModel` | `ui/browsing/MoreMenuSheet.kt` 103–110 | `tabId: String`, `tabs: TabsController`, `bookmarks: BookmarkRepository`, `addBookmark: AddBookmark`, `snackbar: SnackbarController`, `appScope: CoroutineScope` | `tabManager`, `bookmarkRepository`, `addBookmark`, `snackbarController`, `appScope` | `key.tabId` | none | same file 206 |
| `MoveToFolderViewModel` | `ui/bookmarks/BookmarkDialogs.kt` 195–200 | `bookmarkId: Long`, `bookmarks: BookmarkRepository`, `moveBookmark: MoveBookmark`, `appScope: CoroutineScope` | `bookmarkRepository`, `moveBookmark`, `appScope` | `key.bookmarkId` | none | same file 267 |
| `SettingsViewModel` | `ui/settings/SettingsViewModel.kt` 34–40 | `settings: SettingsRepository`, `saveSearchEngine: SetSearchEngine`, `saveTheme: SetTheme`, `versionName: String`, `versionCode: Int` | `settings`, `setSearchEngine`, `setTheme` | none | `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE` (`SettingsScreen.kt` 55–56) | `SettingsScreen.kt` 50 |
| `SetupViewModel` | `ui/onboarding/SetupViewModel.kt` 33–39 | `isDefaultBrowser: () -> Boolean`, `importBookmarks: ImportBookmarks`, `completeOnboarding: CompleteOnboarding`, `snackbar: SnackbarController`, `appScope: CoroutineScope`. Also `DefaultLifecycleObserver`. | `importBookmarks`, `completeOnboarding`, `snackbarController`, `appScope` | none | `{ DefaultBrowser.isDefault(appContext) }` (`SetupScreen.kt` 154) | `SetupScreen.kt` 152 |
| `ShortcutDialogViewModel` | `ui/home/ShortcutDialogViewModel.kt` 48–54 | `shortcutId: Long?`, `shortcuts: ShortcutRepository`, `addShortcut: AddShortcut`, `updateShortcut: UpdateShortcut`, `appScope: CoroutineScope` | `shortcutRepository`, `addShortcut`, `updateShortcut`, `appScope` | `key.shortcutId` | none | `ShortcutDialog.kt` 40 |
| `SiteInfoViewModel` | `ui/browsing/SiteInfoSheet.kt` 111–121 | `tabId: String`, `tabs: TabsController`, `permissions: SitePermissionRepository`, `settings: SettingsRepository`, `saveSkimAi: SetSkimAi`, `savePermission: SetSitePermission`, `incognito: IncognitoDecisions`, `cleaner: SiteDataCleaner`, `appScope: CoroutineScope` | `tabManager`, `sitePermissionRepository`, `settings`, `setSkimAi`, `setSitePermission`, `incognitoSession`, `browsingDataCleaner`, `appScope` | `key.tabId` | none | same file 242 |
| `SkimHistoryViewModel` | `ui/skimhistory/SkimHistoryViewModel.kt` 61–69 | `chats: ChatRepository`, `removeSkimChat: RemoveSkimChat`, `restoreSkimChat: RestoreSkimChat`, `snackbar: SnackbarController`, `undo: UndoRegistry`, `clock: () -> Long = System::currentTimeMillis`, `zone: ZoneId = ZoneId.systemDefault()` | `chatRepository`, `removeSkimChat`, `restoreSkimChat`, `snackbarController`, `undoRegistry` | none | `clock`, `zone` default, not passed | `SkimHistoryScreen.kt` 53 |

## 6. Tests that construct AppContainer or read app.container

No test uses `@Config(application = ...)`. Searched `app/src` for `@Config(application`. Zero matches.

JVM default is `app/src/test/resources/robolectric.properties` 1–6:

```
sdk=36
application=android.app.Application
```

The comment on lines 3–5 says a test of app start-up sets `@Config(application = SkimApplication::class)` itself. No test does.

`@Config` on the two UI tests that build a container is qualifiers only: `NewTabPageRouteTest.kt` 110 `@Config(qualifiers = PHONE_QUALIFIERS)`, `BrowsingRouteTest.kt` 100 the same. `AppContainerBrowserTest` has `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (23) and no `@Config`.

### Unit tests (6)

All six call `AppContainer(app, AppStorage.temp(...))` with `ApplicationProvider.getApplicationContext()` as a plain `Application`.

`app/src/test/java/com/dantech/skimbrowser/AppContainerStorageTest.kt`. `@RunWith(AndroidJUnit4::class)` (27). No `@Config`.

- `nothingIsOpenedBeforeFirstUse` (36): after a container is created and closed, `skim.db` and `skim_settings.preferences_pb` do not exist.
- `aNewContainerOnTheSameStorageReadsEverythingBack` (46): second container on the same `AppStorage.temp` reads the tab, history URL, bookmark title, shortcut name, chat count 1, Skim AI ALLOWED, theme Dark, engine DuckDuckGo, last tab id, and the same install id. DB and preferences files exist under the temp dir, not `app.getDatabasePath("skim.db")`.
- `closeReleasesTheSettingsFileForTheNextContainerAtOnce` (87): six close/reopen rounds. Reader scale and history row count survive.
- `closeWaitsForSettingsWritesStillInFlight` (105): `close()` while `appScope` still writes `setLastTabId`. Next container reads a `lastTabId` from that round.
- `closeStopsTheAppScopeAndCanBeCalledAgain` (130): second `close()` is safe. `appScope.isActive` is false.
- `eachDependencyIsCreatedOnce` (141): `assertSame` on `database`, `dataStore`, `settings`, and the six repositories.

`app/src/test/java/com/dantech/skimbrowser/AppContainerBrowserTest.kt`. Reflects lazy fields (137–139).

- `noBrowserPartIsMadeBeforeFirstUse` (33): zero network callbacks, empty temp dir, `httpClientHolder` / `browserProfiles$delegate` / `downloadTracker$delegate` not initialized.
- `theDownloadPartsAreMadeOnceAndShared` (48): same `httpClient`, `downloadTracker`, `browserProfiles`. Connect and read timeout 15_000 ms.
- `closeLetsTheHttpClientsThreadsGo` (62): dispatcher executor is shut down.
- `theTabManagerBringsItsPartsAlongWithoutReadingAnything` (72): one network callback, `skim.db` not created by constructing `tabManager`, same monitor, incognito session, state store, thumbnails, page scripts.
- `closeStopsWatchingTheNetwork` (91): callbacks go from 1 to 0.
- `theStoresKeepTheirFilesInTheContainersStorage` (102): `tabstate/tab-1.bin` and `thumbs/tab-1.webp` under the temp dir.
- `thePageScriptsComeFromTheAppsAssets` (119): readerable script contains `function isProbablyReaderable(`.
- `theDefaultStorageIsTheAppsPrivateStorage` (126): default `AppStorage()` paths. Does not call `AppContainer(`.

`app/src/test/java/com/dantech/skimbrowser/RestoreAcrossContainersTest.kt`. `@RunWith(AndroidJUnit4::class)` (25). `MainDispatcherRule` (32).

- `normalTabsGroupsAndASavedChatComeBack` (37): close, then a new container's `tabManager.restore()` is `RestoreResult.Restored` with tab count 3. No incognito tab. Same three ids, current tab, group title "Heat pump research", grouped order, saved `chatId` on the heat tab, null `chatId` on the notes tab (a negative id was not stored).

`app/src/test/java/com/dantech/skimbrowser/search/SuggestionServiceTest.kt`. Only `theAppMakesItsServiceOnFirstUseOverItsOwnClient` (356) builds a container. Reflects fields (413–415). Asserts `suggestionService$delegate` and `httpClientHolder` start uninitialized, the service and client are stable, the service shares the container client's dispatcher and connection pool, call timeout is 3_000 ms, and the shared client has no interceptors. Other tests in the file build `SuggestionService` directly.

`app/src/test/java/com/dantech/skimbrowser/ui/home/NewTabPageRouteTest.kt` 134–137 builds the container in `@Before` and `close()`s it in `@After` (153). Also builds `SkimNavigator` over `container.tabManager`, `incognitoSession`, `snackbarController`, `aiGate` (138–146). The tests drive the New tab page: toolbar destinations, tab counts, search layer, shortcuts, continue cards, voice input. They assert Compose nodes and back-stack keys, not container lifecycle.

`app/src/test/java/com/dantech/skimbrowser/ui/browsing/BrowsingRouteTest.kt` 147–148 builds the container and `createBrowserHost`, attaches a `FakeTabEngine`, builds `SkimNavigator` the same way (151–159), `host.destroy()` and `container.close()` in `@After` (165–168). Tests assert address search, long-press link options, toolbar destinations, back/forward, find-in-page, and the error page.

### androidTest (4)

These run against the manifest application (`SkimApplication`), because there is no androidTest manifest.

`app/src/androidTest/java/com/dantech/skimbrowser/MainActivityLifecycleTest.kt` 64–66. Reads `app.container`. Does not call `AppContainer(`. Uses `tabManager`, and in teardown `incognitoSession`, `historyRepository`, `tabStateStore`, `thumbnails` (83–95). Asserts:

- WebViews exist while `MainActivity` lives and not after it is gone (105).
- JavaScript timers pause in `CREATED` and resume (117).
- Timers run in the next activity after the previous one stopped (135).
- A newer instance keeps pages running after the older one is gone (151).
- While started, the activity hears `DownloadManager` finish a download (187).
- A download that ends while stopped is reported on the next start (204).
- Files shared from an earlier incognito session are deleted once the activity starts (227).
- Finishing finalizes Undo closes and ends the incognito session (238).
- Recreation keeps closes that can still be undone (261).

`app/src/androidTest/java/com/dantech/skimbrowser/ui/shell/SkimAppRule.kt` 52–53. Reads `app.container`. `before` calls `tabs.restore()` and snapshots `container.dataStore.data` (65). The rule itself does not assert. Classes that use `SkimAppRule()` and do not name `AppContainer`:

- `app/src/androidTest/java/com/dantech/skimbrowser/ProcessDeathRestoreTest.kt` 27
- `ui/onboarding/SetupImportTest.kt` 52
- `ui/shell/BackStackRestoreTest.kt` 33
- `ReleaseSmokeTest.kt` 42
- `ui/shell/AppStartTest.kt` 27
- `ui/shell/SecureFlagTest.kt` 53
- `ui/shell/ExternalLinkTest.kt` 35

`app/src/androidTest/java/com/dantech/skimbrowser/data/DeviceSqliteDataTest.kt` 43. `AppContainer(app, AppStorage.temp(dir))` on a cache subdirectory. Asserts one history row per URL with the later visit time (56), `clearAll` leaves an empty WAL (77), closing more tabs than one SQLite statement can bind closes every tab (93), and the write connection has `secure_delete` on (112). Uses `container.database` (97).

`app/src/androidTest/java/com/dantech/skimbrowser/browser/BrowserRig.kt` 88. `AppContainer(activity.application, AppStorage.temp(dir))`, then `tabs.restore()` and `createBrowserHost` (89–96). The rule does not assert. Classes that use `BrowserRig()` and do not name `AppContainer`:

- `browser/JsDialogDeviceTest.kt` 25
- `browser/SkimWebViewEngineTest.kt` 59
- `browser/PopupAndTrimTest.kt` 24
- `browser/IncognitoProfileTest.kt` 32
- `browser/DownloadDeviceTest.kt` 36
- `browser/LongPressAndThumbnailTest.kt` 28
- `browser/BlobDownloadDeviceTest.kt` 31
- `browser/DownloadLimitsDeviceTest.kt` 31
- `browser/IncognitoPrivacyDeviceTest.kt` 27
- `browser/ExternalLinksTest.kt` 27
- `browser/SharedDownloadsFolderDeviceTest.kt` 38
- `browser/IncognitoUnavailableTest.kt` 22
- `browser/SitePermissionDeviceTest.kt` 37
- `browser/TabPoolDeviceTest.kt` 24
- `browser/PullToRefreshNestedScrollTest.kt` 34

## 7. DomainBoundaryTest forbidden tokens

File: `app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt`. Constants at 122–128, `DOMAIN_FORBIDDEN` at 129–139, `forbiddenToken` at 163–178.

Resolved domain tokens, in match order (`firstOrNull`):

```
"android."
"androidx."
"okhttp3."
"com.dantech.skimbrowser.data"
"com.dantech.skimbrowser.ui"
"com.dantech.skimbrowser.browser"
"com.dantech.skimbrowser.ai"
"com.dantech.skimbrowser.system"
"com.dantech.skimbrowser.search"
```

Other layers, quoted from `forbiddenToken`:

- `ui`: line contains `com.dantech.skimbrowser.data`, or `com.dantech.skimbrowser.ai`, or `com.dantech.skimbrowser.browser` when the file is not in `BROWSER_WIRING`.
- `ai`: line contains `com.dantech.skimbrowser.ui`, or `com.dantech.skimbrowser.data.db.entity`, or `com.dantech.skimbrowser.data.hostOf`.
- `browser` and `data`: line contains `com.dantech.skimbrowser.ui`.

`BROWSER_WIRING` (145–155), the ui files allowed to name `browser`:

```
ui/browsing/DownloadSnackbarEffect.kt
ui/browsing/BrowsingRoute.kt
ui/settings/SettingsScreen.kt
ui/tabs/TabsScreen.kt
ui/navigation/SkimNavDisplay.kt
ui/browsing/LinkOptionsSheet.kt
ui/browsing/SitePermissionDialog.kt
ui/shell/SkimApp.kt
ui/shell/SnackbarActions.kt
```

`onlyTheCompositionRootConstructsTheConcreteChatRepository` (34–38) skips files under `/data/` and the file named `AppContainer.kt`. Any other main source line whose trim starts with `import com.dantech.skimbrowser.data.repo.ChatRepository` fails. `oldLayerViolationsAreRejected` (94) asserts that `violation("AppContainer.kt", "import com.dantech.skimbrowser.browser.TabManager")` is null, because the path's first segment is not a checked layer.

## 8. Application class and instrumented runner

`app/src/main/AndroidManifest.xml` 42–43:

```
<application
    android:name=".SkimApplication"
```

That is the only manifest in the repo. No `src/androidTest` manifest, so instrumented tests get `SkimApplication` and therefore `AppContainer(this)` from `SkimApplication.onCreate` (13).

`app/build.gradle.kts` 52:

```
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

One assignment. No custom runner class in the repo.

## 9. DI framework imports

None. Searched `*.kt`, `*.kts`, and `*.toml` for `javax.inject`, `jakarta.inject`, `dagger`, `hilt`, and kotlin-inject (`me.tatarka`, `kotlin-inject`). No matches. No `hilt-android` or `libs.hilt` / `libs.dagger` dependency.

## 10. Files that would change if AppContainer is deleted

### Production source (must change to compile)

The class file plus every main file that names the type:

- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt`
- `app/src/main/java/com/dantech/skimbrowser/SkimApplication.kt`
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/browser/BrowserRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/LinkOptionsSheet.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/SiteInfoSheet.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/MoreMenuSheet.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/NewTabPageRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/home/ShortcutDialog.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/search/SearchOverlay.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/SetupScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/settings/AiPrivacyScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/bookmarks/BookmarksScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/bookmarks/BookmarkDialogs.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/TabsScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/tabs/RenameGroupDialog.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/skimhistory/SkimHistoryScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/skimhistory/ClearSkimHistoryDialog.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/ai/AskPageRoute.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/ai/AskTabsSheet.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/reader/ReaderRoute.kt`

`AppStorage.kt` mentions `[AppContainer]` in KDoc (lines 8–9) and does not reference the type in code. It still compiles if the class is deleted. KDoc would be stale.

ViewModel classes do not name `AppContainer`. They would not change for a rename of the wiring type unless their constructor signatures change.

### Unit test

Must change to compile:

- `app/src/test/java/com/dantech/skimbrowser/AppContainerStorageTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/AppContainerBrowserTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/RestoreAcrossContainersTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/search/SuggestionServiceTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/home/NewTabPageRouteTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/browsing/BrowsingRouteTest.kt`

`app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt` does not reference the type. It special-cases the filename `AppContainer.kt` (38, 94). It still compiles if the file is gone. The chat-repository allowlist would then exclude nothing by that name.

`app/src/test/resources/robolectric.properties` names `SkimApplication`, not `AppContainer`.

### androidTest

Must change to compile:

- `app/src/androidTest/java/com/dantech/skimbrowser/MainActivityLifecycleTest.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/ui/shell/SkimAppRule.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/data/DeviceSqliteDataTest.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/browser/BrowserRig.kt`

The `SkimAppRule` and `BrowserRig` callers listed in section 6 do not name `AppContainer`. They break only if those helpers' public surface changes.

### Docs

Living docs that describe the current graph:

- `README.md` line 5
- `docs/system-architecture.md` lines 18, 24, 28
- `docs/code-standards.md` lines 13, 25, 29
- `docs/codebase-summary.md` lines 5, 41
- `docs/project-overview-pdr.md` line 30
- `docs/project-roadmap.md` line 34

`docs/code-standards.md` 25 says not to add a DI framework in passing, and that Activity-scoped `WebViewPool` and `SkimNavigator` stay off the container.

Historical plans that name `AppContainer` (not required to compile; 30 files):

- `plans/261020-1611-skim-design-implementation/plan.md`
- `plans/261020-1611-skim-design-implementation/phase-01-build-foundation-theme-resources.md`
- `plans/261020-1611-skim-design-implementation/phase-02-design-system-components.md`
- `plans/261020-1611-skim-design-implementation/phase-03-data-layer.md`
- `plans/261020-1611-skim-design-implementation/phase-04-browser-engine.md`
- `plans/261020-1611-skim-design-implementation/phase-05-app-shell-navigation-onboarding.md`
- `plans/261020-1611-skim-design-implementation/phase-06-home-search-incognito-new-tab.md`
- `plans/261020-1611-skim-design-implementation/phase-07-browsing-surfaces.md`
- `plans/261020-1611-skim-design-implementation/phase-08-settings-privacy-history-bookmarks.md`
- `plans/261020-1611-skim-design-implementation/phase-09-tabs.md`
- `plans/261020-1611-skim-design-implementation/phase-10-reader-view.md`
- `plans/261020-1611-skim-design-implementation/phase-11-skim-ai-core.md`
- `plans/261020-1611-skim-design-implementation/phase-12-skim-ai-across-tabs-history.md`
- `plans/261020-1611-skim-design-implementation/phase-13-verification-hardening-docs.md`
- `plans/261020-1611-skim-design-implementation/reports/red-team-assumption-destroyer.md`
- `plans/261020-1611-skim-design-implementation/reports/red-team-failure-mode-analyst.md`
- `plans/261020-1611-skim-design-implementation/reports/red-team-scope-complexity-critic.md`
- `plans/reports/code-reviewer-261022-0033-phase-04-security-review.md`
- `plans/reports/code-reviewer-261022-0814-phase-05-review.md`
- `plans/reports/fullstack-developer-261021-0956-phase-01-build-foundation.md`
- `plans/reports/fullstack-developer-261021-1434-phase-03-data-layer.md`
- `plans/reports/fullstack-developer-261021-1604-phase-04a-tab-state.md`
- `plans/reports/fullstack-developer-261021-1806-phase-04b-webview-core.md`
- `plans/reports/fullstack-developer-261022-0019-phase-04c-downloads-permissions.md`
- `plans/reports/fullstack-developer-261022-0611-phase-04d2b-device-api28-data-gating.md`
- `plans/reports/fullstack-developer-261022-0759-phase-05-shell-main.md`
- `plans/reports/fullstack-developer-261022-1356-phase-06a-logic.md`
- `plans/reports/fullstack-developer-261022-1544-phase-06b-ui.md`
- `plans/reports/researcher-261020-2319-android-build-stack-compose-m3e.md`

Other: `.claude/agent-memory/kongming/user-agent-workflow-contracts.md` line 12 says ViewModels are created with `viewModel { }` and a manual `AppContainer`.

## Unresolved questions

- `AppContainer.lastError` (line 123) is documented as what the shell reads (lines 120–122). No production or test code reads it. `docs/code-standards.md` line 29 still says a failure is stored there. Is the shell read unfinished, or is the KDoc stale?
- `robolectric.properties` lines 3–5 tell a startup test to set `@Config(application = SkimApplication::class)`. No test has that annotation. Is every JVM test intentionally on `android.app.Application`, with `SkimApplication.onCreate` covered only by instrumented tests?
- `TabsViewModel` is created twice: keyed `"tabs-${key.incognito}"` on the tabs screen, and with no key (the class default) in `RenameGroupRoute`. The rename factory also omits `tabGrouping` and `appScope`. Is the second store intentional?
- `docs/code-standards.md` line 13 says a ViewModel receives use cases or a domain repository "from `AppContainer` or `SavedStateHandle`". Constructors also take nav keys (`AskPageKey`, `AskTabsKey`, tab id, bookmark id, shortcut id, folder id, `HitTarget`, incognito), `BuildConfig.VERSION_NAME` / `VERSION_CODE`, `VoiceInput.isAvailable`, and `DefaultBrowser.isDefault`. The inventory treats those as call-site args. The standards sentence does not list them.
