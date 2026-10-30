# Code standards

## Language and UI

Kotlin. UI is Jetpack Compose. Strings and content descriptions live in `app/src/main/res/values/strings.xml`. Plurals are real plural resources. UI copy is English. Do not concatenate sentences in code.

A composable reads state and sends events. Whether a page may be sent to the model, whether a tab is incognito, and how a layout is stored are decisions in domain use cases and in `AiGate`, `SkimAiService`, and `TabManager`. Opening a tab, allowing a site, grouping tabs, and deleting browsing or Skim data go through a ViewModel or `SkimNavigator`.

## ViewModels

A ViewModel takes use cases, or a domain repository interface when a read only forwards the call. Navigation arguments are assisted parameters. The only `SavedStateHandle` value is the back stack, kept by `SkimAppViewModel` with the `SkimNavigator` that writes it.

## Domain and data

`domain/` is pure Kotlin: the models screens use, the repository interfaces, and the use cases. A use case is one action, `operator fun invoke`, and it exists when the action is not a single repository method. Android, OkHttp, Room, and the `data` package stay out of `domain/`.

Concrete repositories in `data/` implement the interfaces and map Room entities at that boundary. DAOs stay inside the repository. Settings go through `domain.settings.SettingsRepository`. A screen does not read the DataStore file.

`app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt` is the check. It reads source text, so a fully qualified name fails the same way an import does. The route files it already names are the exceptions. A new exception is a boundary change.

## Dependencies

Hilt is the only composition root. `domain/` does not import `javax.inject` or `dagger`. A `@Module` exists where Hilt cannot see the constructor: Room, DataStore, OkHttp, the process scope, `ClaudeClientFactory`, qualified values, and domain use cases, which carry no injection annotations. `@Binds` lives in the library that owns the implementation. Where those modules sit is `docs/codebase-summary.md`.

Process singletons last as long as the process. A test that needs a second open of the same files uses another `@HiltAndroidTest` method. `@After` calls `ReleaseProcessResources` while the test component is still alive.

## Coroutines

Process work uses the `@AppScope` binding in `app/src/main/java/com/dantech/skimbrowser/di/ProcessResourcesModule.kt`. A failure is logged and stored in `lastError`. It does not cancel sibling work.

Unit tests that construct a `TabManager` or a ViewModel replace `Dispatchers.Main` with `MainDispatcherRule` (`domain/src/testFixtures/kotlin/com/dantech/skimbrowser/testing/MainDispatcherRule.kt`).

## AI requests

Only `SkimAiService.send` opens a model connection. Do not add a second HTTP client for Claude, a fallback model, a beta header, or sampling parameters. The model id and token limits stay in `AiConfig`.

## Secrets

No API keys, proxy URLs, or machine paths in source, tests, docs, or `gradle.properties`. Where a debug key or a local proxy URL may live is the README section "local.properties". Tests talk to MockWebServer.

## Tests

Test the rule Skim owns, at the lowest layer that can prove it. A JVM test of a use case or ViewModel covers a decision that does not need a device WebView. Instrumented tests cover WebView, profiles, and the minified release-smoke path.

Unit tests use JUnit 4 and `runTest`. Do not lower a timeout, delete an assertion, or drop a test to make a red build pass. Every use case has a unit test against a fake of its repository interface in `domain/src/testFixtures`. ViewModel tests use those fakes.

Kover reports coverage. It does not fail the build on a percentage. Filters are the `kover` block in `app/build.gradle.kts`.
