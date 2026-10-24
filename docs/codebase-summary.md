# Codebase summary

Three Gradle modules. `:domain` is a Kotlin JVM library (`org.jetbrains.kotlin.jvm`, Java 11 bytecode) and holds the `domain` package. `:data` is an Android library: Room, DataStore, concrete repositories, bookmark import, and `AppStorage`. `:app` is the Android application; the Android Gradle Plugin compiles its Kotlin. Entry points are `SkimApplication` and `MainActivity`. Hilt modules that read `ProcessGraph` stay in `:app`, including `data/di`.

Hilt (`SingletonComponent`) is the process-wide graph. `SkimApplication` is `@HiltAndroidApp`. Tests install a temporary `AppStorage` before the graph opens, so Room, DataStore, tab state, and thumbnails land in a temporary directory. `ProcessGraph.close()` drops the database and cancels the process scope so the next graph can open the same files.

## Packages

| Package | Role |
|---|---|
| `domain/` | The `:domain` module. Pure Kotlin. Models, repository interfaces, and use cases (`domain/usecase`, `domain/ai`). Subpackages: `history`, `bookmark`, `shortcut`, `chat`, `permission`, `tab`, `page`, `settings`, `url`, `ai`, `text`, `browsing`, `undo`. |
| `ui/` | Compose screens and ViewModels. Subpackages match the screens: `onboarding`, `home`, `search`, `browsing`, `reader`, `tabs`, `history`, `bookmarks`, `skimhistory`, `settings`, `ai`, `shell`, `navigation`, `components`, `theme`. ViewModels take use cases or domain interfaces. |
| `browser/` | `TabManager` implements `domain.tab.TabsController` (process-wide tab list). `WebViewPool` (the Activity's WebViews), `BrowserHost`, `IncognitoSession`, downloads, site permissions, page scripts. Persistence goes through domain repository interfaces. Tab models live in `domain/tab`. |
| `data/` | The `:data` module, except `data/di`, which stays in `:app` because those Hilt bindings read `ProcessGraph`. Room (`data/db`), concrete repositories (`data/repo`) that implement the domain interfaces and map entities, settings DataStore (`data/settings`), bookmark import (`BookmarkFiles`). `AppStorage` lives in this module. |
| `ai/` | `AiGate`, `SkimAiService` (`AiAssistant`), Claude streaming, chat sessions (`PageChats`), tab grouping. The only request builder is `SkimAiService`. SDK types stay in this package. Site, settings, and chat reads and writes use domain interfaces and use cases. |
| `search/` | Suggestion fetches for the search field. |
| `system/` | Share sheet, clipboard, share-image files. |

Navigation keys live in `ui/navigation/SkimKeys.kt`. `SkimNavDisplay` registers an entry per destination. `SkimNavigator` is the only writer of the back stack.

## Persistence

Room database `skim.db` (schema under `data/schemas`). Preferences DataStore `skim_settings.preferences_pb`. Tab state files in `tabstate/`, thumbnails in `thumbs/`. Incognito share files under `cache/shared/incognito`, removed when the session ends.

Incognito tabs are not rows in Room. A chat id that is still negative (memory only) is stored as null (`TabRepository` keeps `chatId` only when it is positive).

## AI

`AiConfig` names the model `claude-haiku-5-5`, max tokens, and effort. Debug uses `BuildConfig.AI_BASE_URL` (`https://api.anthropic.com`) and `BuildConfig.ANTHROPIC_API_KEY` when `skim.anthropicApiKey` is set. Release sets the key field to null and the base URL to `skim.aiProxyUrl`. `releaseSmoke` uses `http://127.0.0.1:8787` and a null key.

SSE fixtures used by unit tests and the release smoke test are in `app/src/sharedTest/sse/`. Unit tests load them as classpath resources. The androidTest source set exposes the same directory as assets.

## Tests

| Tree | Runs |
|---|---|
| `app/src/test` | JVM unit tests: Robolectric, Turbine, Roborazzi. |
| `app/src/androidTest` | Instrumented tests, including `ProcessDeathRestoreTest` and `ReleaseSmokeTest`. |
| `app/src/test/screenshots` | Recorded goldens. Board images share names with `design/screens/`. |

`RestoreAcrossContainersTest` opens the process graph, closes it, and restores with a second graph on the same directory.
