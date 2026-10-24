# Codebase summary

Five Gradle modules. `:domain` is a Kotlin JVM library (`org.jetbrains.kotlin.jvm`, Java 11 bytecode) and holds the `domain` package. `:data` is an Android library: Room, DataStore, concrete repositories, bookmark import, and `AppStorage`. `:browser` is an Android library: the tab engine, WebView pool, downloads, site permissions, and page scripts. `:ai` is an Android library: the Claude adapter, chat sessions, and tab grouping. `:app` is the Android application; the Android Gradle Plugin compiles its Kotlin. Entry points are `SkimApplication` and `MainActivity`. Hilt modules that read `ProcessGraph` stay in `:app`, under `di/data`, `di/browser`, and `di/ai`.

Hilt (`SingletonComponent`) is the process-wide graph. `SkimApplication` is `@HiltAndroidApp`. Tests install a temporary `AppStorage` before the graph opens, so Room, DataStore, tab state, and thumbnails land in a temporary directory. `ProcessGraph.close()` drops the database and cancels the process scope so the next graph can open the same files.

## Packages

| Package | Role |
|---|---|
| `domain/` | The `:domain` module. Pure Kotlin. Models, repository interfaces, and use cases (`domain/usecase`, `domain/ai`). Subpackages: `history`, `bookmark`, `shortcut`, `chat`, `permission`, `tab`, `page`, `settings`, `url`, `ai`, `text`, `browsing`, `undo`. |
| `ui/` | Compose screens and ViewModels. Subpackages match the screens: `onboarding`, `home`, `search`, `browsing`, `reader`, `tabs`, `history`, `bookmarks`, `skimhistory`, `settings`, `ai`, `shell`, `navigation`, `components`, `theme`. ViewModels take use cases or domain interfaces. |
| `browser/` | The `:browser` module. Its Hilt bindings stay in `:app` at `di/browser` because they read `ProcessGraph`. `TabManager` implements `domain.tab.TabsController` (process-wide tab list). `WebViewPool` (the Activity's WebViews), `BrowserHost`, `IncognitoSession`, downloads, site permissions, page scripts. Persistence goes through domain repository interfaces. Tab models live in `domain/tab`. |
| `data/` | The `:data` module. Its Hilt bindings stay in `:app` at `di/data` because they read `ProcessGraph`. Room (`data/db`), concrete repositories (`data/repo`) that implement the domain interfaces and map entities, settings DataStore (`data/settings`), bookmark import (`BookmarkFiles`). `AppStorage` lives in this module. |
| `ai/` | The `:ai` module. Its Hilt bindings stay in `:app` at `di/ai` because they read `ProcessGraph` and `BuildConfig`. `AiGate`, `SkimAiService` (`AiAssistant`), Claude streaming, chat sessions (`PageChats`), tab grouping. The only request builder is `SkimAiService`. SDK types stay in this package. Site, settings, and chat reads and writes use domain interfaces and use cases. |
| `search/` | Suggestion fetches for the search field. |
| `system/` | Share sheet, clipboard, share-image files. |

Navigation keys live in `ui/navigation/SkimKeys.kt`. `SkimNavDisplay` registers an entry per destination. `SkimNavigator` is the only writer of the back stack.

## Persistence

Room database `skim.db` (schema under `data/schemas`). Preferences DataStore `skim_settings.preferences_pb`. Tab state files in `tabstate/`, thumbnails in `thumbs/`. Incognito share files under `cache/shared/incognito`, removed when the session ends.

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
| `app/src/test` | Screens, ViewModels, search, system, and the process graph. Robolectric, Turbine, Roborazzi. `DomainBoundaryTest`, `BrowsingDataCleanerTest`, and `AiDataEraserTest` stay here because they name `ui`. |
| `app/src/androidTest` | Instrumented tests, including `ReleaseSmokeTest`. |
| `app/src/test/screenshots` | Recorded goldens. Board images share names with `design/screens/`. |

Fakes shared with `:app` tests live in each module's test fixtures: `domain/src/testFixtures` (repository fakes, `RecordingTabs`, and `MainDispatcherRule`), `data/src/testFixtures` (the in-memory database), `browser/src/testFixtures` (`TabManagerFixture` and the WebView fakes), and `ai/src/testFixtures` (`FakeClaudeServer` and the session helpers). `:app:testDebugUnitTest` runs the four module suites first.

`RestoreAcrossContainersTest` opens the process graph, closes it, and restores with a second graph on the same directory.
