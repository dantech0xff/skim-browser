# Codebase summary

One Gradle module, `:app`. Kotlin is compiled by the Android Gradle Plugin (built-in Kotlin). Entry points are `SkimApplication` and `MainActivity`.

`AppContainer` (`AppContainer.kt`) is the process-wide graph. It is created in `SkimApplication.onCreate`. Tests construct another container with `AppStorage.temp(dir)` so Room, DataStore, tab state, and thumbnails land in a temporary directory. `close()` drops the database and cancels the process scope so the next container can open the same files.

## Packages

| Package | Role |
|---|---|
| `domain/` | Pure Kotlin. Models, repository interfaces, and use cases (`domain/usecase`, `domain/ai`). Subpackages: `history`, `bookmark`, `shortcut`, `chat`, `permission`, `tab`, `page`, `settings`, `url`, `ai`. |
| `ui/` | Compose screens and ViewModels. Subpackages match the screens: `onboarding`, `home`, `search`, `browsing`, `reader`, `tabs`, `history`, `bookmarks`, `skimhistory`, `settings`, `ai`, `shell`, `navigation`, `components`, `theme`. ViewModels take use cases or domain interfaces. |
| `browser/` | `TabManager` implements `domain.tab.TabsController` (process-wide tab list). `WebViewPool` (the Activity's WebViews), `BrowserHost`, `IncognitoSession`, downloads, site permissions, page scripts. Persistence goes through domain repository interfaces. Tab models live in `domain/tab`. |
| `data/` | Room (`data/db`), concrete repositories (`data/repo`) that implement the domain interfaces and map entities, settings DataStore (`data/settings`), bookmark import (`BookmarkFiles`). |
| `ai/` | `AiGate`, `SkimAiService` (`AiAssistant`), Claude streaming, chat sessions (`PageChats`), tab grouping. The only request builder is `SkimAiService`. SDK types stay in this package. Site, settings, and chat reads and writes use domain interfaces and use cases. |
| `search/` | Suggestion fetches for the search field. |
| `system/` | Share sheet, clipboard, share-image files. |

Navigation keys live in `ui/navigation/SkimKeys.kt`. `SkimNavDisplay` registers an entry per destination. `SkimNavigator` is the only writer of the back stack.

## Persistence

Room database `skim.db` (schema under `app/schemas`). Preferences DataStore `skim_settings.preferences_pb`. Tab state files in `tabstate/`, thumbnails in `thumbs/`. Incognito share files under `cache/shared/incognito`, removed when the session ends.

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

`RestoreAcrossContainersTest` opens a real `AppContainer`, closes it, and restores with a second container on the same directory.
