# Code standards

## Language and UI

Kotlin. UI is Jetpack Compose. Strings and content descriptions live in `app/src/main/res/values/strings.xml`. Plurals are real plural resources. UI copy is English. Do not concatenate sentences in code.

A composable reads state and sends events. It does not decide whether a page may be sent to the model, whether a tab is incognito, or how a layout is stored. Those decisions sit in domain use cases and tab rules. `AiGate`, `SkimAiService`, and `TabManager` apply them. ViewModels call `TabsController`, `AiAssistant` use cases, and `PageChats`.

A composable does not open a tab, allow a site, group tabs, or delete browsing or Skim data. It calls a ViewModel or `SkimNavigator`. `OpenUrlInCurrentTab` opens a URL in the current tab, or a new normal tab when none is current. Clear browsing data, delete all AI data, and clear Skim history run on `appScope`. Clear Skim history closes its dialog after that delete finishes. The other two close at once.

## ViewModels

ViewModels expose `StateFlow` (and `SharedFlow` for one-shot events). Screens collect them with `collectAsStateWithLifecycle`. A ViewModel is created with `hiltViewModel`. Navigation arguments are assisted parameters, not values read from `SavedStateHandle`. It receives use cases, or a domain repository interface for a read that does nothing but forward the call. It does not take a concrete class from `data/`. `onCleared` is where a chat in progress is saved or dropped.

## Domain and data

`domain/` is pure Kotlin. It holds the models the screens use, the repository interfaces, and the use cases. A use case is one action with `operator fun invoke`. `kotlinx.coroutines` (`Flow`) is allowed there. Android, OkHttp, Room, and the `data` package are not.

Concrete repositories in `data/repo` implement those interfaces. Mapping between a Room entity and a domain model happens in `data`, at the DAO boundary. DAOs stay inside the repository. A repository does not know about Compose. `TabsController` is the API ViewModels use for tabs. `TabManager` implements it. Screens do not write tab rows themselves.

Settings go through `domain.settings.SettingsRepository`. Do not read the DataStore file from a screen. `ui` sources do not import `com.dantech.skimbrowser.data`.

## Dependencies

Hilt is the only composition root. Process types are `@Singleton` on `SingletonComponent`, with `@Inject` on the class. A `@Module` exists only where Hilt cannot call the constructor: Room, DataStore, `@AppScope`, `@SettingsJob`, the `lastError` flow, OkHttp, `AppStorage`, `ClaudeClientProvider`, qualified values such as the wall clock, and one unscoped `@Provides` per domain use case. `@Binds` lives in the library that owns the implementation. Activity-lifetime types are `@ActivityScoped`, and `MainActivity` destroys them in `onDestroy`. `domain/` does not import `javax.inject` or `dagger`.

Production does not close these singletons; they last as long as the process. A test that needs a second open of the same files uses another `@HiltAndroidTest` method and a directory that outlives `@Rule TemporaryFolder`. `@After` calls `ReleaseProcessResources` while the test component is still alive.

## Coroutines

Process work uses the `@AppScope` binding (`SupervisorJob` + `Dispatchers.Default`). A failure is logged and stored in `lastError`. It does not cancel sibling work. Room and settings writes that must stay ordered go through `SerialWriter`.

`Dispatchers.Main` in production is the Android main thread. Unit tests that construct a `TabManager` or a ViewModel replace it with `MainDispatcherRule` so `runTest` can advance the work.

## AI requests

Only `SkimAiService.send` opens a model connection. Callers pass sources and a consent mode. Incognito sources are rejected before any request. The Anthropic request types stay inside `ai/`. Sheets depend on `AiAssistant` use cases and receive `AiReply`, which carries no SDK type. Do not add a second HTTP client for Claude, a fallback model, a beta header, or sampling parameters. Model id and token limits stay in `AiConfig`.

## Secrets

No API keys, proxy URLs, or machine paths in source, tests, docs, or `gradle.properties`. Debug key and proxy URL come from `local.properties` or `-P` / CI variables. Tests talk to MockWebServer.

## Tests

Unit tests use JUnit 4, Robolectric (`AndroidJUnit4`), and `runTest`. Flows are asserted with Turbine where a test collects more than one value. Screenshot tests call `assertA11y()` and `captureBoard` when a `design/screens` PNG exists. Do not lower a timeout or delete an assertion to make a red test pass.

Every use case has a unit test against a fake of its repository interface (`domain/fake`). ViewModel tests use those fakes. `DomainBoundaryTest` reads every source line, so a fully qualified name fails the same way an import does. It fails when `domain` names Android, OkHttp, or an outer layer (`data`, `ui`, `browser`, `ai`, `system`, `search`); when `ui` names `data` or `ai`; when `ui` names `browser` outside the route files that still draw a WebView, a tab thumbnail, or a site-permission prompt (`BrowsingRoute`, `TabsScreen`, `LinkOptionsSheet`, `SitePermissionDialog`); when `ai`, `browser`, or `data` name `ui`; and when `ai` names `data.db.entity` or a `data` URL helper (`hostOf`, `isWebUrl`, `normalizeHost`, `withoutFragment`). The same URL helpers are rejected in `browser`. Callers use `domain.url`.

Kover excludes Android generated classes and Room `*Dao_Impl` / `*Database_Impl` only.
