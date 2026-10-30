# Codebase summary

Modules are declared in `settings.gradle.kts`.

| Module | Start here |
|---|---|
| `:domain` | `domain/src/main/kotlin/com/dantech/skimbrowser/domain/`. Pure Kotlin. Models, repository interfaces, use cases. |
| `:data` | `data/src/main/java/com/dantech/skimbrowser/data/`. Room, DataStore, repositories, bookmark import. `@Binds` in `data/src/main/java/com/dantech/skimbrowser/data/di/`. |
| `:browser` | `browser/src/main/java/com/dantech/skimbrowser/browser/`. Tab engine, WebView pool, downloads, page scripts. `@Binds` in `browser/src/main/java/com/dantech/skimbrowser/browser/di/`. Depends on `:domain` and `:data`. |
| `:ai` | `ai/src/main/java/com/dantech/skimbrowser/ai/`. Claude adapter, chat sessions, tab grouping. `@Binds` in `ai/src/main/java/com/dantech/skimbrowser/ai/di/`. Depends on `:domain` and `:browser`. |
| `:app` | `SkimApplication` and `MainActivity`. Screens in `app/src/main/java/com/dantech/skimbrowser/ui/`. Process resources, use cases, and `ClaudeClientFactory` (it reads `BuildConfig`) in `app/src/main/java/com/dantech/skimbrowser/di/`. |
| `:baselineprofile` | `baselineprofile/` |
| `:macrobenchmark` | `macrobenchmark/` |

`app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimKeys.kt` names destinations. `SkimNavigator` is the only writer of the back stack once the app has started. `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimAppViewModel.kt` holds that stack.

Boundaries and the decisions behind them are `docs/system-architecture.md`.

## Tests

| Tree | What it owns |
|---|---|
| `<module>/src/test` | Unit tests for that module. |
| `<module>/src/testFixtures` | Fakes shared with `:app` tests. |
| `app/src/androidTest` | Instrumented tests, including `ReleaseSmokeTest`. |
| `app/src/test/screenshots` | Roborazzi goldens. Board images share names with `design/screens/`. |

`DomainBoundaryTest` is the layer check (`app/src/test/java/com/dantech/skimbrowser/domain/DomainBoundaryTest.kt`). `:app:testDebugUnitTest` runs the other modules' unit tests first. That dependency is in `app/build.gradle.kts`.
