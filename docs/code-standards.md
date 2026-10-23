# Code standards

## Language and UI

Kotlin. UI is Jetpack Compose. Strings and content descriptions live in `app/src/main/res/values/strings.xml`. Plurals are real plural resources. UI copy is English. Do not concatenate sentences in code.

A composable reads state and sends events. It does not decide whether a page may be sent to the model, whether a tab is incognito, or how a layout is stored. Those decisions sit in `AiGate`, `SkimAiService`, `TabManager`, and the repositories.

## ViewModels

ViewModels expose `StateFlow` (and `SharedFlow` for one-shot events). Screens collect them with `collectAsStateWithLifecycle`. A ViewModel is created with the `viewModel { }` factory and receives what it needs from `AppContainer` or `SavedStateHandle`. `onCleared` is where a chat in progress is saved or dropped.

## Data

Repositories are concrete classes in `data/repo`. DAOs stay inside the repository. A repository does not know about Compose. `TabManager` is the browser-facing API for tabs; screens do not write tab rows themselves.

Settings go through `SettingsRepository`. Do not read the DataStore file from a screen.

## Dependencies

`AppContainer` constructs the graph. Do not add a DI framework in passing. New process-wide types are `val`s on the container, created lazily. Types that live only as long as the Activity (`WebViewPool`, `SkimNavigator`) stay off the container and on `MainActivity` / `BrowserHost`.

## Coroutines

Process work uses `AppContainer.appScope` (`SupervisorJob` + `Dispatchers.Default`). A failure is logged and stored in `lastError`. It does not cancel sibling work. Room and settings writes that must stay ordered go through `SerialWriter`.

`Dispatchers.Main` in production is the Android main thread. Unit tests that construct a `TabManager` or a ViewModel replace it with `MainDispatcherRule` so `runTest` can advance the work.

## AI requests

Only `SkimAiService.send` opens a model connection. Callers pass sources and a consent mode. Incognito sources are rejected before any request. Do not add a second HTTP client for Claude, a fallback model, a beta header, or sampling parameters. Model id and token limits stay in `AiConfig`.

## Secrets

No API keys, proxy URLs, or machine paths in source, tests, docs, or `gradle.properties`. Debug key and proxy URL come from `local.properties` or `-P` / CI variables. Tests talk to MockWebServer.

## Tests

Unit tests use JUnit 4, Robolectric (`AndroidJUnit4`), and `runTest`. Flows are asserted with Turbine where a test collects more than one value. Screenshot tests call `assertA11y()` and `captureBoard` when a `design/screens` PNG exists. Do not lower a timeout or delete an assertion to make a red test pass.

Kover excludes Android generated classes and Room `*Dao_Impl` / `*Database_Impl` only.
