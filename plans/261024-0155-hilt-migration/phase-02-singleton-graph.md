---
phase: 2
title: "Singleton graph"
status: completed
priority: P2
effort: 16h
dependencies: [1]
---

# Phase 2: Singleton graph

## Goal

Move the process graph onto `SingletonComponent`, keep route signatures compiling through an `@Inject` facade, and make `close()` open the same files again inside one test method, with a single `SkimDatabase.open` in the process.

## Context links

- [researcher-02-hilt-migration-patterns.md](./research/researcher-02-hilt-migration-patterns.md): component map, `@TestInstallIn`, startup entry point, and why `@EarlyEntryPoint` is rejected. The report's advice to split `closeReleasesTheSettingsFileForTheNextContainerAtOnce` into two methods is not in force.
- [researcher-01-hilt-toolchain.md](./research/researcher-01-hilt-toolchain.md) section 6: no `HiltTestRunner` class; `@HiltAndroidTest` plus `HiltAndroidRule`; instrumented runner overrides `newApplication`.
- [scout-manual-di.md](./research/scout-manual-di.md) sections 1, 3, 6, and 8: bindings, the 25 production consumers, the 10 test files, `SkimAppRule`, and `BrowserRig`.
- Close and laziness: `AppContainer.kt` lines 117–153 and 394–415. Reopen tests: `AppContainerStorageTest.kt` lines 36–153. Database open: `SkimDatabase.kt` lines 81–82. DataStore file lock: `data/settings/SettingsRepository.kt` lines 113–124. Boundary: `DomainBoundaryTest.kt` lines 34–49 and 129–139.

## Key Insights

- `@Singleton` is created on first injection, not when the component is built. `SkimApplication.onCreate` must not inject `SkimDatabase`, `OkHttpClient`, or `DataStore`. Resolve `TabManager` inside the coroutine, matching today's `container.appScope.launch { container.tabManager.restore() }` (`SkimApplication.kt` lines 14–15).
- A bare `@Singleton SkimDatabase`, repository, or `TabManager` cannot be closed and opened again inside one Hilt component. `closeReleasesTheSettingsFileForTheNextContainerAtOnce` (`AppContainerStorageTest.kt` lines 87–101) builds a new `AppContainer` on each of six rounds, then a seventh. `SettingsRepository` (`data/settings/SettingsRepository.kt` lines 29–31) stores the `DataStore` and builds `settings` once. `TabRepository` (`data/repo/TabRepository.kt` lines 24–26) stores the DAO. `close()` on one of those objects does not give the next read a new file. One `@Singleton` `ProcessGraph` owns a generation. `get()` builds that generation on first use, with today's constructors. `close()` closes it and drops every object in it, including `TabManager`. The next `get()` builds another. <!-- Updated: Red Team - generation holder, do not rewrite repository bodies -->
- Hilt never calls `Closeable.close`. `ProcessShutdown.close()` is explicit. Production does not call it. Process death does not need it. Test rules call it.
- The concrete chat repository is constructed only under `data/`. `onlyTheCompositionRootConstructsTheConcreteChatRepository` (`DomainBoundaryTest.kt` lines 34–45) today flags a line only when the trim starts with `import com.dantech.skimbrowser.data.repo.ChatRepository`, and it skips any path containing `/data/`. A fully qualified name in `di/` would pass that test. In this same change, also flag a line outside `/data/` that contains `com.dantech.skimbrowser.data.repo.ChatRepository`. Keep the `AppContainer.kt` filename skip until phase 4. The `@Provides` in `data/di` uses an alias, the same way `data/repo/ChatRepository.kt` line 7 imports the domain type `as ChatStore`. Do not write `@Binds fun bind(impl: ChatRepository): ChatRepository`. `di/` does not import or name the concrete class. Add `"javax.inject."` and `"dagger."` to `DOMAIN_FORBIDDEN` (`DomainBoundaryTest.kt` lines 129–139). Use cases stay free of those imports. <!-- Updated: Red Team - chat binding alias and FQCN check -->
- The facade is `@Inject @Singleton` and only exposes Hilt objects. It has no `AppContainer(app, storage)` constructor. Nothing calls `AppContainer()` after this phase. Routes may still take the facade. Phase 4 deletes it.
- `robolectric.properties` lines 1–6 force `android.app.Application` for every JVM test. Do not set `application` to `HiltTestApplication` there. JVM graph tests opt in with `@Config(application = HiltTestApplication::class)`. There is no `HiltTestRunner` class. `SkimTestRunner` extends `AndroidJUnitRunner`.

## Requirements

- One `SkimDatabase.open` caller in `main`: `DataGeneration`. `BrowserRig` and `DeviceSqliteDataTest` stop constructing a second `AppContainer`.
- `Application.onCreate` launches restore and `purgeOldShares` and does not request the database, OkHttp, DataStore, `ClaudeClientProvider`, or `TabManager` on that thread. The startup entry point's `tabManager()` is called inside the coroutine.
- `@TestInstallIn` replaces `StorageModule` for each test source set. A holder (`AtomicReference<AppStorage>`) is set before Hilt asks for `AppStorage`. `@BindValue` is not the default.
- Instrumented tests use `SkimTestRunner` for debug and `releaseSmoke` (one `defaultConfig.testInstrumentationRunner`; `releaseSmoke` does not override it, `app/build.gradle.kts` lines 52 and 78–84). `SkimApplication.onCreate` does not run on those tests. `SkimAppRule.before()` already calls `tabs.restore()` (line 64). Every androidTest that relied on startup (`restore`, `purgeOldShares`) calls that after the Hilt rule. `SkimAppRule`, `BrowserRig`, and `DeviceSqliteDataTest` call `ProcessShutdown.close()` in teardown before the next test's component can open Room. `ReleaseSmokeTest` (`ReleaseSmokeTest.kt` lines 52–56) puts `HiltAndroidRule` as `RuleChain.outerRule` around the chain that launches `MainActivity`. No test reads `SkimApplication` from a property initializer (`MainActivityLifecycleTest.kt` line 64, `SkimAppRule.kt` lines 52–54). `DeviceCapabilitiesReportTest` stays without `@HiltAndroidTest`; it must not cast `SkimApplication`. It still runs on `HiltTestApplication` because the runner is process-wide. <!-- Updated: Red Team - close between tests, rule order, no SkimApplication cast -->
- Drop `getDeclaredField` lazy-delegate checks (`AppContainerBrowserTest.kt` lines 137–138, `SuggestionServiceTest.kt` lines 360 and 413–414). Keep the behavioral assertions: files absent, one network callback, executor shut down, `assertSame` on two reads. Leave `ShareImageTest.kt` line 42 alone; that reflection is `FileProvider`, not the container.
- Constructors Hilt calls use `javax.inject.Inject`. Do not mix in `jakarta.inject`.
- Do not use `@EarlyEntryPoint`, `@ActivityRetainedScoped`, or a custom component. Do not build `BrowserHost` or `WebViewPool` in this phase. `createBrowserHost` stays.

## Architecture

`@HiltAndroidApp` on `SkimApplication` builds `SingletonComponent` in `super.onCreate()`. A startup entry point then starts work. The facade and `ProcessGraph` constructors store dependencies and open nothing.

```mermaid
flowchart TD
  app["SkimApplication.onCreate"] --> entry["StartupEntryPoint"]
  entry --> scope["appScope launch"]
  scope --> tabs["tabManager.restore"]
  scope --> purge["purgeOldShares"]
  graph["ProcessGraph @Singleton"] --> gen["generation: db, DataStore, OkHttp, monitor, TabManager, repositories"]
  facade["AppContainer facade"] --> graph
  routes["routes still take the facade"] --> facade
```

### Lease

<!-- Updated: Red Team - one generation, today's constructors, no repository rewrite -->

`ProcessGraph.close()` follows `AppContainer.close()` (`AppContainer.kt` lines 405–414): if the network monitor was opened, close it; if OkHttp was opened, shut down its executor and evict the pool; if the database was opened, close it; cancel the app scope that was opened; join the settings `SupervisorJob` so the preferences file is released (`SettingsRepository.createDataStore` KDoc, lines 115–116); then drop the generation. A second `close()` with nothing open returns. The next getter builds a new generation. `get()` and `close()` share one lock. `SkimDatabase.open` runs only while the generation is empty.

Do not mark `SkimDatabase`, `DataStore<Preferences>`, `OkHttpClient`, `ConnectivityNetworkMonitor`, `TabManager`, or the repository classes `@Singleton`. Hilt would return the first instance for the life of the component, including after `close()`. The `@Singleton` is `ProcessGraph`. Every other provider calls a getter on it. Two injection sites therefore see one `TabManager` until `close()`, which is what `RestoreAcrossContainersTest` (`RestoreAcrossContainersTest.kt` lines 78–80) and `MainActivity` (`MainActivity.kt` lines 84 and 166) require. An unscoped `@Provides fun tabManager(...): TabManager = TabManager(...)` is the failure: Dagger calls it on every request.

Do not change `HistoryRepository`, `TabRepository`, `BookmarkRepository`, `ShortcutRepository`, `ChatRepository`, `SitePermissionRepository`, or `data.settings.SettingsRepository`. They keep today's constructors. A generation builds them once and caches them. `close()` drops those instances. The next getter constructs new ones with the new database and the new `DataStore`. Repository unit tests keep calling the constructors they call today. A reopen test must not hold a `SettingsRepository` or `TabManager` from the previous round; it reads the facade again after `close()`.

`TabManager` (`TabManager.kt` lines 71–88) keeps the scope it was given at construction. The next `tabManager()` builds another with the new scope, monitor, and repositories, omitting the defaulted clock, id, dispatcher, and timeout parameters so those Kotlin defaults stay. `@Provides` `TabsController` from `browser/` returns `processGraph.tabManager()`. Use cases that store a `TabsController` are also fields of the generation, so `close()` drops them too. Do not inject `TabManager` into a `@Singleton` that outlives `close()`.

`nothingIsOpenedBeforeFirstUse` (`AppContainerStorageTest.kt` lines 36–42) and `noBrowserPartIsMadeBeforeFirstUse` (`AppContainerBrowserTest.kt` lines 33–44) stay true only if constructing the facade does not read those getters. `theTabManagerBringsItsPartsAlongWithoutReadingAnything` (lines 72–80) still expects `skim.db` to be absent after `tabManager` is built: `SkimDatabase.open` may run, and the file appears on the first query. Do not call `restore()` from the provider.

OkHttp keeps connect and read timeouts of 15 seconds (`AppContainer.kt` lines 147–151). `SuggestionService` (`SuggestionService.kt` lines 32–40) still derives a client with a 3 second call timeout and no cookies, cache, or redirects. Recreate that service when the HTTP generation changes so it does not keep a shut-down dispatcher. `closeLetsTheHttpClientsThreadsGo` (`AppContainerBrowserTest.kt` lines 62–68) asserts the client captured before `close()` is shut down.

`closeStopsTheAppScopeAndCanBeCalledAgain` (`AppContainerStorageTest.kt` lines 130–137) must capture the scope reference before `close()`, then assert that reference is inactive and that a second `close()` does not throw. The next call to `appScope()` is a new active scope. Do not assert that a post-close getter is inactive.

### Startup

```kotlin
override fun onCreate() {
    super.onCreate()
    val entry = EntryPoints.get(this, StartupEntryPoint::class.java)
    val scope = entry.appScope()
    scope.launch { entry.tabManager().restore() }
    scope.launch(Dispatchers.IO) { purgeOldShares(cacheDir) }
}
```

`StartupEntryPoint` returns `@AppScope CoroutineScope` and `TabManager` only. Both types are public. It does not expose the database, OkHttp, or DataStore. `purgeOldShares` is `system/ShareImage.kt` line 166. Two launches keep purge from waiting on `restore()`, which is how `AppContainer.init` (lines 394–396) behaves today. Do not use `@EarlyEntryPoint`.

`appScope` is `SupervisorJob() + Dispatchers.Default` plus the current handler that logs and writes `lastError` (`AppContainer.kt` lines 129–134). `lastError` stays a `@Singleton` `MutableStateFlow<Throwable?>` even though no screen reads it. The settings scope stays a child of that job plus `Dispatchers.IO`.

### Facade

Replace the body of `AppContainer` with `@Singleton class AppContainer @Inject constructor(private val graph: ProcessGraph)`. Properties the 25 consumers read (`scout-manual-di.md` section 3) call getters on `graph` and do not store a repository, a database, or a `TabManager` of their own. The constructor does not touch those getters. `close()` delegates to `ProcessShutdown`. Delete the `AppContainer(Application, AppStorage)` constructor. `MainActivity`'s getter (`MainActivity.kt` line 49) becomes `EntryPoints.get(application, AppGraphEntryPoint::class.java).container()`. Remove `SkimApplication.container`. `AppGraphEntryPoint` returns the facade only.

### Storage in tests

```kotlin
object TestStorage {
    val current: AtomicReference<AppStorage?> = AtomicReference()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
    @Provides @Singleton
    fun storage(): AppStorage =
        TestStorage.current.get() ?: error("TestStorage was not set")
}
```

Put one copy in `app/src/test` and one in `app/src/androidTest`. `@TestInstallIn` is per source set. `src/sharedTest` is resources and assets only (`app/build.gradle.kts` lines 86–92), so do not drop Kotlin there without a source-set change.

Set the holder before Hilt asks for `AppStorage`. For JVM tests, set it in `@Before` before `hiltRule.inject()`, and before any activity rule. `HiltAndroidRule` is `@Rule(order = 0)`. If a failure shows the holder was read inside the rule itself, move the set into the test instance initializer and create the directory there. Clear the holder in `@After`, after `ProcessShutdown.close()`.

`@Singleton` `AppStorage` is per Hilt test case, not pinned for the process. The next test's component reads `TestStorage.current` again. That only works if the previous case has closed Room and joined the settings job. `SkimAppRule.after()` does not close anything today (`SkimAppRule.kt` lines 73–108). Add `ProcessShutdown.close()` there, and in `BrowserRig.after()` (which already calls `container.close()` at lines 125–127) and `DeviceSqliteDataTest`'s `@After`.

Shell instrumented tests that today use the process container set `AppStorage()` (the app's private files, `AppStorage.kt` lines 12–22). That matches `SkimAppRule` now. `BrowserRig` and `DeviceSqliteDataTest` set `AppStorage.temp(dir)` before the activity or the entry point is used. Do not describe those temp directories as the path shell tests use. <!-- Updated: Red Team - per-test holder plus explicit close -->

### What `@Singleton` means here

| Object | Binding | Why |
|---|---|---|
| `ProcessGraph`, `lastError`, `AppStorage` | `@Singleton` | One generation and one file set per component |
| Room repositories, `SettingsRepository`, `TabManager`, incognito session, suggestion service, download tracker, tab state, thumbnails, snackbar, undo, use cases | Fields of the generation. Providers return the getter. Not `@Singleton` themselves | `assertSame` inside a round; `close()` drops them so the next getter builds new ones |
| `PageChats`, `AskPageAccess`, `SiteDataCleaner`, `IncognitoDecisions`, `NetworkAvailability`, `TabsController` | `@Binds` or `@Provides` to the generation's instance, from `data/`, `ai/`, or `browser/`, never from `ui/` or `domain/` | Phase 3's `@Inject` constructors need these. The facade's `viewModel { }` lambdas hide the gap until then |
| `BrowserHost`, `WebViewPool` | Not in this phase | Activity scope is phase 4 |

`di/` may import domain interfaces and `browser.TabManager`. It must not import `data.repo.ChatRepository`. The concrete chat type is constructed only in `data/`.

`data/di` builds the database, the settings store, and the six repositories with today's constructors, and provides the domain interfaces. `browser/di` builds `TabManager` and the browser singletons and binds `TabsController`. `ai/di` builds `ClaudeClientProvider`, `SkimAiService`, `AiGate` (`AskPageAccess`), and `ChatSessionStore` (`PageChats`). The provider copies `AppContainer.kt` lines 321–322 unchanged: `(BuildConfig.ANTHROPIC_API_KEY as String?).takeIf { !it.isNullOrBlank() }`. Do not bind `String` for the URL or the key, do not log them, and do not name the credential field in new docs.

The generation also caches the objects phase 3 will inject and that the use-case list does not cover: `SnackbarController`, `UndoRegistry`, `PendingUndo`, `ReaderHighlights`, `BrowsingDataCleaner` (`SiteDataCleaner`), `IncognitoSession` (`IncognitoDecisions` and `IncognitoSupport`), `NetworkMonitor` (`NetworkAvailability`), and every use case `AppContainer` constructs at lines 194–392, including `OpenUrlInCurrentTab`, `ClearBrowsingData` (lines 373–376; the lambda stays inside the provider), `ImportBookmarks`, and `CollectOpenTabs`. <!-- Updated: Red Team - missing ViewModel bindings and the null-safe key read -->

`AppGraphEntryPoint` returns the facade. It does not return `ClaudeClientProvider`, `OkHttpClient`, `DataStore`, or `SkimDatabase`. Callers that need those go through a generation getter inside `data/`, `browser/`, or `ai/`. `StartupEntryPoint` returns `@AppScope CoroutineScope` and `TabManager` only.

## Related Code Files

Create:

- `app/src/main/java/com/dantech/skimbrowser/di/Qualifiers.kt` — `@AppScope`.
- `app/src/main/java/com/dantech/skimbrowser/di/ProcessGraph.kt` — generation, getters, `close()`.
- `app/src/main/java/com/dantech/skimbrowser/di/ProcessShutdown.kt` — the type test rules call.
- `app/src/main/java/com/dantech/skimbrowser/di/StartupEntryPoint.kt`
- `app/src/main/java/com/dantech/skimbrowser/di/AppGraphEntryPoint.kt` — returns the facade.
- `app/src/main/java/com/dantech/skimbrowser/di/UseCaseModule.kt`
- `app/src/main/java/com/dantech/skimbrowser/di/ProcessBindingsModule.kt` — `lastError`, `@AppScope` `CoroutineScope`.
- `app/src/main/java/com/dantech/skimbrowser/data/di/StorageModule.kt`
- `app/src/main/java/com/dantech/skimbrowser/data/di/DataGeneration.kt` — builds Room, DataStore, and the repositories with today's constructors. Provides the domain interfaces, including chat via a `ChatStore` alias.
- `app/src/main/java/com/dantech/skimbrowser/browser/di/BrowserBindingsModule.kt` — `TabManager` as `TabsController`, monitor, profiles, incognito, scripts, downloads, tab state, thumbnails. Getters go through `ProcessGraph`. `PageScripts` is this process binding, not an activity binding.
- `app/src/main/java/com/dantech/skimbrowser/ai/di/AiModule.kt`
- `app/src/test/java/com/dantech/skimbrowser/TestStorage.kt` and `TestStorageModule.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/TestStorage.kt` and `TestStorageModule.kt`
- `app/src/androidTest/java/com/dantech/skimbrowser/SkimTestRunner.kt`

Modify:

- `app/src/main/java/com/dantech/skimbrowser/AppContainer.kt` — facade only.
- `app/src/main/java/com/dantech/skimbrowser/SkimApplication.kt` — `@HiltAndroidApp`, startup entry point, remove `container`.
- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt` — facade from `AppGraphEntryPoint`. Keep `createBrowserHost` (lines 162–196).
- `app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt` — `DOMAIN_FORBIDDEN`, plus the fully qualified chat-repository check. Leave the `AppContainer.kt` filename skip until phase 4.
- `app/build.gradle.kts` line 52 — `testInstrumentationRunner`.
- JVM graph tests: `AppContainerStorageTest.kt`, `AppContainerBrowserTest.kt`, `RestoreAcrossContainersTest.kt`, `search/SuggestionServiceTest.kt`, `ui/home/NewTabPageRouteTest.kt`, `ui/browsing/BrowsingRouteTest.kt`.
- androidTest: `SkimAppRule.kt`, `BrowserRig.kt`, `MainActivityLifecycleTest.kt`, `data/DeviceSqliteDataTest.kt`, `ReleaseSmokeTest.kt`, and every class below that launches the app or uses those rules.

Do not modify the repository classes or `data/settings/SettingsRepository.kt`.

Delete: none. `AppContainer.kt` stays as the facade until phase 4.

`DeviceCapabilitiesReportTest.kt` launches a plain `ComponentActivity` and does not read the container. Leave it without `@HiltAndroidTest`.

## Implementation Steps

1. Add `@HiltAndroidApp` to `SkimApplication`. Replace `onCreate` with the two launches above. Delete `lateinit var container`.
2. Add `StorageModule` providing `@Singleton AppStorage()`. Add `ProcessGraph` and `DataGeneration`. `SkimDatabase.open` (`SkimDatabase.kt` line 81) is called only from `DataGeneration`, under the same lock as `close()`, and only when the generation is empty.
3. Do not edit repository bodies. `DataGeneration` calls today's constructors and provides the domain interfaces. The chat provider aliases `data.repo.ChatRepository` as `ChatStore`, matching `ChatRepository.kt` line 7. Extend `onlyTheCompositionRootConstructsTheConcreteChatRepository` so a line outside `/data/` that contains that fully qualified name fails, not only an import line.
4. Add `BrowserBindingsModule` and `AiModule`. `AiModule` copies the key read at `AppContainer.kt` lines 321–322. Add `UseCaseModule` with one `@Provides` per use case, each calling the existing constructor and reading the instance from the generation. No use-case file under `domain/` gains an import. Bind `PageChats`, `AskPageAccess`, `SiteDataCleaner`, `IncognitoDecisions`, `NetworkAvailability`, `SnackbarController`, `UndoRegistry`, and `ReaderHighlights` the same way.
5. Rewrite `AppContainer` as the facade. Point `MainActivity` at `AppGraphEntryPoint`. Keep `createBrowserHost` and its call at `MainActivity.kt` line 83.
6. Add `"javax.inject."` and `"dagger."` to `DOMAIN_FORBIDDEN`. Extend `oldLayerViolationsAreRejected` with a `domain/` line that imports `javax.inject.Inject` and one that imports `dagger.Module`, using the same synthetic-path style as line 72. Add a case for the fully qualified concrete chat repository outside `data/`.
7. Add `SkimTestRunner` and set `testInstrumentationRunner` to its class name. `newApplication` passes `HiltTestApplication::class.java.name`.
8. Rewrite the six JVM graph tests. Annotate them `@HiltAndroidTest`, `@Config(application = HiltTestApplication::class)`, `@RunWith(AndroidJUnit4::class)`, with `HiltAndroidRule` at order 0. Set `TestStorage` to `AppStorage.temp` before `inject()`. Replace each `AppContainer(...)` with the facade from an entry point. `BrowsingRouteTest` still calls `createBrowserHost` (line 148). `NewTabPageRouteTest` still builds `SkimNavigator` by hand (lines 138–146). Delete the `made()` helpers. Keep `assertSame`, file, timeout, callback, and executor assertions.
9. For `closeStopsTheAppScopeAndCanBeCalledAgain`, hold the scope from before `close()`. For the six-round test, one component: each round writes through the facade, calls `close()`, then the next read uses the facade again and must not reuse the previous repository. The method stays one `@Test`. Do not split it.
10. androidTest: add `@HiltAndroidTest` and `HiltAndroidRule(order = 0)` to `MainActivityLifecycleTest`, `DeviceSqliteDataTest`, `ReleaseSmokeTest`, `ProcessDeathRestoreTest`, `ui/onboarding/SetupImportTest`, `ui/shell/BackStackRestoreTest`, `ui/shell/AppStartTest`, `ui/shell/SecureFlagTest`, `ui/shell/ExternalLinkTest`, and every `BrowserRig` caller (`JsDialogDeviceTest`, `SkimWebViewEngineTest`, `PopupAndTrimTest`, `IncognitoProfileTest`, `DownloadDeviceTest`, `LongPressAndThumbnailTest`, `BlobDownloadDeviceTest`, `DownloadLimitsDeviceTest`, `IncognitoPrivacyDeviceTest`, `ExternalLinksTest`, `SharedDownloadsFolderDeviceTest`, `IncognitoUnavailableTest`, `SitePermissionDeviceTest`, `TabPoolDeviceTest`, `PullToRefreshNestedScrollTest`). Do not add `@Config` on androidTest. The runner supplies the application.
11. `SkimAppRule` stops casting to `SkimApplication` and reading `.container` (lines 52–54). Read the facade in `before()`, after the Hilt rule, not in a property initializer. `before()` calls `tabs.restore()` and `purgeOldShares(cacheDir)`. `after()` calls `ProcessShutdown.close()`. `MainActivityLifecycleTest` line 64 moves off `getApplicationContext<SkimApplication>()`. `ReleaseSmokeTest` makes `HiltAndroidRule` the outer rule of the `RuleChain` at lines 52–56, so `MainActivity` cannot start before the test component exists. `DeviceCapabilitiesReportTest` stays without `@HiltAndroidTest` and must not cast `SkimApplication`.
12. `BrowserRig.before()` (lines 83–98) sets `TestStorage.current` to `AppStorage.temp(dir)` before any entry-point or activity use, then takes `TabManager` from the graph and still calls `createBrowserHost`. It does not call `AppContainer()`. `after()` calls `ProcessShutdown.close()` before deleting the directory. `DeviceSqliteDataTest` (line 43) does the same and reads `database` from the generation. Shell tests that need the process files set `AppStorage()`, then close in teardown. One open at a time.
13. Search the main and androidTest sources for `AppContainer(`. The only remaining mentions are the type and the facade class. No call with arguments.

## Todo

- [x] Add `@HiltAndroidApp` and the startup entry point without eager database, OkHttp, or DataStore.
- [x] Add `ProcessGraph`, `DataGeneration`, `ProcessShutdown`, and the facade. Remove `AppContainer()`. Leave repository classes unchanged.
- [x] Provide domain interfaces from `data/`, including chat through a `ChatStore` alias. Provide use cases and the ViewModel ports (`PageChats`, `AskPageAccess`, `SiteDataCleaner`, snackbar, undo, reader highlights) outside `domain/`.
- [x] Extend `DOMAIN_FORBIDDEN` with `javax.inject.` and `dagger.`. Flag a fully qualified concrete `ChatRepository` outside `data/`.
- [x] Add `SkimTestRunner` for debug and `releaseSmoke`.
- [x] Rewrite the six JVM graph tests and the androidTest rules so each process opens Room once.
- [x] Keep the six-round close test as one method. Drop lazy-field reflection.
- [x] Pass `:app:testDebugUnitTest` and `:app:assembleDebugAndroidTest`.

## Success Criteria

- `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebugAndroidTest` pass with `--no-configuration-cache`.
- `rg "AppContainer\\(" app/src` finds no constructor call.
- `rg "SkimDatabase.open" app/src/main` finds only `DataGeneration`.
- `AppContainerStorageTest.closeReleasesTheSettingsFileForTheNextContainerAtOnce` is still one `@Test` and still loops six times.
- `DomainBoundaryTest` fails a `domain/` file that imports `javax.inject` or `dagger`, and `domain/` has no such import.
- `robolectric.properties` still sets `application=android.app.Application`.
- `createBrowserHost` still exists and is still the only builder of `WebViewPool` in `main`.

## Risk Assessment

- Two Room databases. Signal: `SkimDatabase.open` appears twice in one process, or `BrowserRig` / `DeviceSqliteDataTest` still call `AppContainer(`. Response: delete the second construction. Set `TestStorage` before the component is used. Do not add `@EarlyEntryPoint`.
- Startup opens files. Signal: `nothingIsOpenedBeforeFirstUse` sees `skim.db` or `skim_settings.preferences_pb`, or `noBrowserPartIsMadeBeforeFirstUse` sees a network callback before a getter. Response: remove the database, OkHttp, and DataStore from `StartupEntryPoint` and from the facade constructor. Do not call `tabManager()` on the `onCreate` thread.
- Settings file stays locked. Signal: the six-round test or `closeWaitsForSettingsWritesStillInFlight` cannot open the preferences file. Response: join the settings job after cancelling the app scope, and only then null the DataStore. Match `AppContainer.kt` lines 412–414.
- `close()` kills the scope the assertion still holds. Signal: `closeStopsTheAppScopeAndCanBeCalledAgain` fails because a post-close getter is active, or a later round has a cancelled scope. Response: assert the reference captured before `close()`. The next getter starts a new scope.
- A `@Singleton` repository or `TabManager` keeps the first instance. Signal: the second round reads a closed database, or two `tabManager()` calls in one round are not `assertSame`. Response: those types are fields of the generation. Providers return the getter. `close()` drops the generation. Do not `@Singleton` the class, and do not rewrite repository methods.
- Holder read before it is set. Signal: `TestStorage was not set` during `HiltAndroidRule`. Response: set the holder in the instance initializer and keep `AppStorage` off types the rule creates. Do not switch the design to `@BindValue`.
- `kspAndroidTest` does not run. Signal: an instrumented test crashes looking for a generated test component supplier. Response: confirm the `kspAndroidTest` task ran for `:app`. This module is `com.android.application`, which is outside the library-module report in the toolchain notes. Do not add a second compiler plugin.
- Stale `TabManager` after `close()`. Signal: `RestoreAcrossContainersTest` restores incognito tabs or misses the saved group. Response: `close()` nulls the manager and the next getter constructs a new one. Do not `@Singleton` the class.

## Security Considerations

The Claude client provider stays in `ai/`. The credential `BuildConfig` field is read only there, with the same `as String?` / `takeIf { !it.isNullOrBlank() }` expression as `AppContainer.kt` lines 321–322. It is not a Hilt `String` binding and it is not on an entry point. The existing `appScope` handler still logs the throwable; this phase does not change that log. `installId` stays out of new log calls, as `SettingsRepository` already requires (`domain/settings/SettingsRepository.kt` lines 26–29). `ProcessShutdown` does not log database or preferences contents. `BrowserRig` and `DeviceSqliteDataTest` use `AppStorage.temp`. Shell tests that set `AppStorage()` use the app's private files, which are not a temp directory.

## Verification

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-configuration-cache
```

Do not run a connected emulator. Confirm with search, not a device:

- `rg "AppContainer\\(" app/src` is empty.
- `rg "SkimDatabase.open" app/src/main` is `DataGeneration` only.
- `rg "EarlyEntryPoint|ActivityRetainedScoped" app/src` is empty.

## Next Steps

Phase 3 annotates the 23 ViewModels and switches route factories to `hiltViewModel`. The facade parameter stays. Activity scope waits until phase 4.
