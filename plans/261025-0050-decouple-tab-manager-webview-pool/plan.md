# Decouple TabManager and WebViewPool

Status: done. Unit tests: 1510 of 1511 pass; `NewTabPageRouteTest.removeDeletesTheShortcutAtOnceAndItsUndoStillWorksOnceHomeIsGone` fails, and fails the same way on unmodified `master`. Device tests compile, not run.

## Outcome

`TabManager` (process singleton) and `WebViewPool` (Activity scoped) no longer depend on each other's concrete type. The process side reaches the Activity's pool only through a process-scoped bridge.

## Constraints

- `WebViewPool` stays a concrete class, created per Activity with the Activity's context.
- Behavior stays the same: pending loads and reloads replay on attach, live pages are forgotten when the pool goes or a newer one replaces it, the offline tab reloads on attach.
- Other injections in `MainActivity` stay as they are for now.

## Non-goals

- Moving `WebViewPool` out of `BrowserHost`.
- Splitting `TabManager` further, or changing `IncognitoSession` / `BrowsingDataCleaner` injection.

## Design

- `TabEngineBridge` (`:browser`, `@Singleton`) holds the attached `TabEngine`.
  - `TabEngineSource`: `engine: StateFlow<TabEngine?>`, `isAttached`, `addListener`. Read by `TabManager` and `WebViewPool`.
  - `TabEngineAttachment`: `attach` / `detach`. Called only by `MainActivity`.
- `TabManager` takes `TabEngineSource`, listens for engine changes, and loses `attach`, `detach`, `isAttached`.
- `WebViewPool` takes `TabsController`, `TabEventSink`, `TabEngineSource` instead of `TabManager`.
- `DownloadRouter` and `SitePermissionBridge` providers take `TabsController`.
- Hilt provides `TabEventSink` from `TabManager.events`.

## Acceptance

- No type in the Activity graph (`ActivityBrowserModule`, `WebViewPool`, `DownloadRouter`) names `TabManager`.
- `TabManager` names neither `WebViewPool` nor an attach API.
- `./gradlew :app:testDebugUnitTest` passes; `:app:compileDebugAndroidTestKotlin` compiles.
- `docs/system-architecture.md` describes the bridge.
