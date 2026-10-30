# AGENTS.md

Rules for changing this Android app. Product authority is `docs/product-vision.md`. Engineering constraints are `docs/code-standards.md`. Where a module starts is `docs/codebase-summary.md`. Release stays blocked until `docs/deployment-guide.md` says it may ship.

## Tooling

Use `./gradlew` from the repo root. Every invocation passes `--no-configuration-cache`. KSP does not serialize into the configuration cache. Task names and device setup are the README sections "Build and test" and "Device checks".

AGP 9 compiles Kotlin. Do not apply `org.jetbrains.kotlin.android`.

Do not install a system image for this project. Do not reuse or stop another project's AVD. Instrumented tests run only when the user asked and the dedicated AVDs named in the README already exist.

## Kotlin

`:domain` is a Kotlin JVM module that emits Java 11 bytecode. It has no Android SDK and no Compose compiler. Do not add Android, OkHttp, Room, Hilt, or Compose to it. A use case is one `operator fun invoke`, and it exists only when the action is not a single repository method.

UI copy is English, in `app/src/main/res/values/strings.xml`, with real plurals. Do not concatenate sentences. Do not translate strings to match a plan.

This version does not add a second page engine, an ad blocker, or a non-Android client.

## Compose

New UI is Jetpack Compose and Material 3 Expressive. Do not add a View layout for a screen.

A composable reads state and sends events. It does not decide consent, incognito, tab layout, or deletion. Those calls go through a ViewModel or `SkimNavigator`.

The Smooth Compose rules in `docs/product-vision.md` are binding. `:domain` is compiled without the Compose compiler, so a domain class is stable in Compose only when every property is a read-only stable type and the class is listed in `app/compose_stability.conf`. The compiler trusts that file without checking. Do not list a class that fails that test.

Navigation is Navigation 3 (`androidx.navigation3`). Do not add `NavHost` or `NavController`. `SkimNavigator` is the only writer of the back stack once the app has started.

A deliberate visual change updates the known-deviations table in `docs/design-guidelines.md`. Do not record a golden to hide a difference that table does not accept. Pixel diffs against `design/screens/` are not required to be zero.

## MVVM

Each screen gets its ViewModel from `hiltViewModel`. A navigation argument is an assisted parameter on an `@AssistedFactory`. Do not read it from `SavedStateHandle`. The only `SavedStateHandle` value is the back stack in `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimAppViewModel.kt`.

A ViewModel takes use cases, or a domain repository interface when a read only forwards the call. It does not take a concrete class from `data/`. A screen does not read the DataStore file.

## Hilt

Hilt is the only composition root. `domain/` does not import `javax.inject` or `dagger`. `@Binds` lives in the library that owns the implementation. Do not move those bindings into `:app`.

A `@Module` exists where Hilt cannot see the constructor: Room, DataStore, OkHttp, the process scope, `ClaudeClientFactory`, qualified values, and domain use cases. Do not add a second client for Claude. The model id and token limits stay in `ai/src/main/java/com/dantech/skimbrowser/ai/AiConfig.kt`. Only `SkimAiService.send` opens a model connection.

Process singletons last as long as the process. In a Hilt test, `@After` calls `ReleaseProcessResources` while the test component is still alive. A second open of the same files is another `@HiltAndroidTest` method.

## Tests

Test the rule this app owns, at the lowest layer that can prove it. A JVM test covers a decision that does not need a device WebView. Do not lower a timeout, delete an assertion, or drop a test to make a build pass.

Unit tests that construct a `TabManager` or a ViewModel use `MainDispatcherRule` (`domain/src/testFixtures/kotlin/com/dantech/skimbrowser/testing/MainDispatcherRule.kt`).

`app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt` reads source text. A fully qualified name fails the same way an import does. Do not add an exception to make it pass.

Before reporting a code change done, run the affected module's unit test with `--tests` and `--no-configuration-cache`. Run `:app:testDebugUnitTest` when the change is in `:app` or crosses a module boundary. Run `:app:verifyRoborazziDebug` when a golden exists for the UI you changed. Do not run the connected suite, Macrobenchmark, or `:app:designParity` unless the task needs that evidence.

No API keys, proxy URLs, or machine paths in source, tests, docs, or `gradle.properties`. Where a debug key may live is the README section "local.properties".
