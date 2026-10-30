# Codebase summary

Five Gradle modules. `:domain` is a Kotlin JVM library (`org.jetbrains.kotlin.jvm`, Java 11 bytecode) and holds the `domain` package. `:data` is an Android library: Room, DataStore, concrete repositories, bookmark import, and `AppStorage`. `:browser` is an Android library: the tab engine, WebView pool, downloads, site permissions, and page scripts. `:ai` is an Android library: the Claude adapter, chat sessions, and tab grouping. `:app` is the Android application; the Android Gradle Plugin compiles its Kotlin. Entry points are `SkimApplication` and `MainActivity`. `:data`, `:browser`, and `:ai` each hold the `@Binds` for their own implementations. `:app` holds `ProcessResourcesModule` (Room, DataStore, OkHttp, and the process scope), `UseCaseModule`, and `ClaudeClientFactory`, which stays there because it reads `BuildConfig`.

Hilt (`SingletonComponent`) is the process-wide container. `SkimApplication` is `@HiltAndroidApp`. Tests install a temporary `AppStorage` before Hilt first asks for it, so Room, DataStore, tab state, and thumbnails land in a temporary directory. Each test's `@After` calls `ReleaseProcessResources`, which stops the network monitor, shuts down OkHttp, closes the database, then cancels the process scope and waits for the settings store's job, so the next test method's component can open the same files.

## Packages

| Package | Role |
|---|---|
| `domain/` | The `:domain` module. Pure Kotlin. Models, repository interfaces, and use cases (`domain/usecase`, `domain/ai`). Subpackages: `history`, `bookmark`, `shortcut`, `chat`, `permission`, `tab`, `page`, `settings`, `url`, `ai`, `text`, `browsing`, `undo`. |
| `ui/` | Compose screens and ViewModels. Subpackages match the screens: `onboarding`, `home`, `search`, `browsing`, `reader`, `tabs`, `history`, `bookmarks`, `skimhistory`, `settings`, `ai`, `shell`, `navigation`, `components`, `theme`. ViewModels take use cases or domain interfaces. |
| `browser/` | The `:browser` module. Its Hilt bindings are in `com.dantech.skimbrowser.browser.di`. `TabManager` implements `domain.tab.TabsController` (process-wide tab list). `WebViewPool` (the Activity's WebViews) reaches it only through `TabEngineBridge`, `TabsController`, and `TabEventSink`. `BrowserHost`, `IncognitoSession`, downloads, site permissions, page scripts. Persistence goes through domain repository interfaces. Tab models live in `domain/tab`. |
| `data/` | The `:data` module. Its Hilt bindings, including the `AppStorage` provider tests replace, are in `com.dantech.skimbrowser.data.di`. Room (`data/db`), concrete repositories (`data/repo`) that implement the domain interfaces and map entities, settings DataStore (`data/settings`), bookmark import (`BookmarkFiles`). `AppStorage` lives in this module. |
| `ai/` | The `:ai` module. Its Hilt bindings are in `com.dantech.skimbrowser.ai.di`; only the `ClaudeClientProvider` provider stays in `:app` because it reads `BuildConfig`. `AiGate`, `SkimAiService` (`AiAssistant`), Claude streaming, chat sessions (`PageChats`), tab grouping. The only request builder is `SkimAiService`. SDK types stay in this package. Site, settings, and chat reads and writes use domain interfaces and use cases. |
| `search/` | Suggestion fetches for the search field. |
| `system/` | Share sheet, clipboard, share-image files. |

Navigation keys live in `ui/navigation/SkimKeys.kt`. `SkimNavDisplay` registers an entry per destination. `SkimNavigator` is the only writer of the back stack once the app has started. `ui/shell/SkimAppViewModel` holds both, and the start up.

## Persistence

Room database `skim.db` (schema under `data/schemas`). Preferences DataStore `skim_settings.preferences_pb`. Tab state files in `tabstate/`, thumbnails in `thumbs/`. Incognito share files under `cache/shared/incognito`, removed when the session ends.

Home shortcuts are seeded once from `data/src/main/assets/config/default-shortcuts.json` while that table is still empty. `ShortcutCatalog` is the source, so a remote config replaces the binding and not the seeder. The `shortcuts_seeded` preference is install bookkeeping, not a user setting. Removing every shortcut leaves the Add tile and does not seed again.

Incognito tabs are not rows in Room. A chat id that is still negative (memory only) is stored as null (`TabRepository` keeps `chatId` only when it is positive).

## AI

`AiConfig` names the model `claude-haiku-5-5`, max tokens, and effort. Debug uses `BuildConfig.AI_BASE_URL` (`https://api.anthropic.com`) and `BuildConfig.ANTHROPIC_API_KEY` when `skim.anthropicApiKey` is set. Release sets the key field to null and the base URL to `skim.aiProxyUrl`. `releaseSmoke` uses `http://127.0.0.1:8787` and a null key.

SSE fixtures used by unit tests and the release smoke test are in `ai/src/testFixtures/resources/sse/`. Unit tests load them as classpath resources. The androidTest source set exposes the same directory as assets.

## Tests

| Tree | Runs |
|---|---|
| `domain/src/test` | Use cases and pure model rules. |
| `data/src/test` | Room, DataStore, repositories, and bookmark import. |
| `browser/src/test` | The tab engine, WebView, downloads, and page scripts. |
| `ai/src/test` | The Claude adapter, chat sessions, and tab grouping. |
| `app/src/test` | Screens, ViewModels, search, system, and the Hilt container. Robolectric, Turbine, Roborazzi. `DomainBoundaryTest`, `BrowsingDataCleanerTest`, and `AiDataEraserTest` stay here because they name `ui`. |
| `app/src/androidTest` | Instrumented tests, including `ReleaseSmokeTest`. |
| `app/src/test/screenshots` | Recorded goldens. Board images share names with `design/screens/`. |

Fakes shared with `:app` tests live in each module's test fixtures: `domain/src/testFixtures` (repository fakes, `RecordingTabs`, and `MainDispatcherRule`), `data/src/testFixtures` (the in-memory database), `browser/src/testFixtures` (`TabManagerFixture` and the WebView fakes), and `ai/src/testFixtures` (`FakeClaudeServer` and the session helpers). `:app:testDebugUnitTest` runs the four module suites first.

`RestoreAcrossContainersTest` writes on one method, releases in `@After`, and restores on a second method that opens a new component against the same `@ClassRule` directory.
