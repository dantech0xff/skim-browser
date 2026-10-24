# System architecture

```
MainActivity
  BrowserHost (WebViewPool, chrome client)   lives with the Activity
  SkimNavigator (back stack)
        |
        v
ui/  screens and ViewModels
        |
        v
domain/  models, repository interfaces, use cases     pure Kotlin, Gradle module :domain
        ^
        |
data/  Room, DataStore, concrete repositories     Android library :data
        ^
        |
Hilt SingletonComponent                lives with the process
  wires data implementations into domain interfaces and use cases
  TabManager, IncognitoSession, SkimAiService, AiGate, TabGrouping
  ChatSessionStore and AiDataEraser call chat use cases

browser/  tab engine, WebViews, downloads         Android library :browser
  depends on :domain and :data; its Hilt bindings stay in :app

ai/  Claude adapter, chat sessions, tab grouping    Android library :ai
  depends on :domain and :browser; its Hilt bindings stay in :app
```

Dependencies point inward: `ui` depends on `domain`, `data` depends on `domain`, `browser` depends on `domain` and `data`, and `ai` depends on `domain` and `browser`. `domain` does not import Android, `data`, `ui`, `browser`, or `ai`. `DomainBoundaryTest` reads every source line, so a fully qualified name fails the same way an import does. It rejects `domain` naming Android, OkHttp, or an outer layer; `ui` naming `data` or `ai`; `ui` naming `browser` outside the nine route files that attach the Activity host (`DownloadSnackbarEffect`, `BrowsingRoute`, `SettingsScreen`, `TabsScreen`, `SkimNavDisplay`, `LinkOptionsSheet`, `SitePermissionDialog`, `SkimApp`, `SnackbarActions`); `ai`, `browser`, or `data` naming `ui`; and `ai` naming `data.db.entity` or `data.hostOf`. It also checks that the concrete chat repository is constructed only under `data/`. Hilt is the composition root. `browser` and `ai` take domain repository interfaces. `SkimNavigator` depends on `TabsController`, `IncognitoSupport`, `WindowPrivacy`, and `AskPageAccess`. Site information clears a site through `SiteDataCleaner`. Visible-text cutting lives in `domain/text`. `data` does not call `ui` or `browser`. `browser` still calls a few `data` helpers (`normalizeHost`, `isWebUrl`, `hostOf`); that edge is outside the test.

A ViewModel reads through a repository interface when the call is a pass-through (`observe`, `load`). A write goes through a use case in `domain/usecase`: one action, `operator fun invoke`. The concrete repositories in `data/repo` implement the interfaces and map entities to domain models in `data/repo/DomainMappers.kt`. Settings types (`SearchEngine`, `ThemeSetting`, `ReaderFont`, `SkimSettings`) live in `domain/settings`. The old `data` and `ui.theme` names are typealiases so Room and existing call sites keep compiling.

`ChatSessionStore` writes a transcript through use cases (`CreateSavedChat`, `ReadSavedMessages`, `AddSavedMessage`, `UpdateSavedChat`, `UpdateSavedMessage`) on `domain.chat.ChatRepository`. `AiDataEraser` clears chats with `DeleteAllSkimChats`. Neither type constructs the Room repository. The concrete repository is constructed only under `data/`.

## Tabs and WebViews

Tab models (`Tab`, `TabsState`, `TabRuntime`, `GroupLayout`) live in `domain/tab`. ViewModels depend on `TabsController`, and `TabManager` implements it. Incognito restore and undo-close decisions are pure functions in `domain/tab/TabRules.kt`. `TabManager` owns the live tab list and the restore path. It is created once per process. It does not hold WebViews. Android types (`Bundle`, `SystemClock`, `Log`, WebView) stay in `browser/`.

`WebViewPool` is created with the Activity (`BrowserHost`). It keeps a small number of live WebViews and rebinds them when the current tab changes. `MainActivity.browserHostForTests` exposes the host to instrumented tests. `pool.destroyAll()` is what a process-death test uses to drop every WebView before `Activity.recreate()`.

Opening a tab is `TabManager.newTab` on the main thread. Layout changes (group, reorder, ungroup) go through `applyLayout`, which returns false if the caller's layout version is stale. That is how a grouping result is dropped when the person moved a tab while the request was in flight.

`restore()` reads Room on a background dispatcher and applies the result on the main thread. Incognito tabs are not in the database, so they do not come back. A restore that cannot read the database starts a new tab and does not overwrite the saved rows (`RestoreResult.Fallback`).

## Incognito

`IncognitoSession` asks `BrowserProfiles` for a profile per session. `WebViewFeature.MULTI_PROFILE` has to be present. When it is not, new incognito tabs are refused and the UI says the system WebView needs an update.

The session has a state (`None`, active, `Ending`). An ending session is not reused. Ending the session clears the profile (cookies, and the HTTP cache when the device can) and deletes `cache/shared/incognito`. `FLAG_SECURE` is set while an incognito tab or an incognito sheet is on screen, including while that screen is fading out. Instrumented coverage of the flag is `SecureFlagTest`; it has not been run on a device in this environment.

Incognito pages never reach `SkimAiService`. The service rejects them before the client is created. Ask about this page and Ask about tabs call `SummarizePage`, `AskPage`, `RetryAnswer`, `OverviewTabs`, `AskTabs`, and `CollectOpenTabs`. Those use cases talk to `AiAssistant`. `SkimAiService` implements it and is the only type that sees the model SDK. A reply the sheet sees is `AiReply`. `ChatSession` implements `PageConversation` and keeps the last request internally; the sheet reads `canRetry`. `ChatSessionStore` implements `PageChats`. `NetworkMonitor` implements `NetworkAvailability`.

## Ask Skim

`ResolveSkimAiConsent` is the one rule for a site's saved choice and ask-before-sending. `AiGate.decide`, the site information switch, and `SkimAiService.send` all use it. A page asks when the site is `ASK`, or `UNSET` while ask-before-sending is on. An across-tabs action refuses only `ASK`. `AiGate.decide` runs before page text is read:

- Incognito → do not ask.
- Site state `ASK`, or `UNSET` while "ask before sending" is on → permission dialog (06d).
- Otherwise the sheet may open.

`SkimAiService` is the only type that builds a Messages request. `Consent.SITE_APPROVED` requires every source host to equal the host the person allowed. A later navigation that changes the host is `AiDenied`. `Consent.TABS_ACTION` refuses an empty source list and refuses sources whose site state is `ASK`. Tab text is read from a live WebView at send time (`extractLivePage`), not from a background digest.

Streams are `callbackFlow` over the Anthropic SDK. The collector can cancel, which is Stop. Citations are enabled for a page summary. An across-tabs overview and tab grouping use a JSON schema and no citations. A follow-up on tabs is plain text. Nothing is parsed as JSON unless `stop_reason` is `end_turn`.

Chats are stored when "Keep chat history" is on. A chat id stays negative until Room assigns one. Restoring a sheet shows the saved messages and does not send a new request.

`TabGrouping` runs when the normal tabs screen's ViewModel attaches, skips fewer than four eligible tabs, skips when offline, and skips a set it already grouped. Leaving that screen cancels the request and hides the banner. Undo restores the previous layout. Incognito and rename do not start it.

## Storage and deletion

| Data | Where | Cleared by |
|---|---|---|
| Tabs, groups | Room `tabs`, `tab_groups` | Close (Undo, then delete), Clear browsing data |
| History | Room | Remove one row, or clear browsing data |
| Bookmarks, shortcuts | Room | Delete, or clear browsing data for shortcuts as that screen defines |
| Skim chats | Room | Skim history delete, Clear Skim history, Delete all AI data |
| Site AI choice | Room `site_permissions` | Delete all AI data. Clear Skim history keeps it |
| Settings, install id | DataStore | Clear browsing data does not rotate the install id |
| Tab state, thumbnails | Files | Purged for tabs that are gone |
| Incognito profile | WebView profile directory | `IncognitoSession.end` |

Clear browsing data and the two Skim chat deletes finalize a waiting undo, and dismiss its snackbar, before the rows are removed. `AiDataEraser` and `BrowsingDataCleaner` call `PendingUndo`. `SnackbarUndo` is the implementation and is the type that knows the snackbar. ViewModels start those deletes on `appScope`. Clear Skim history closes its dialog after that job finishes. The other two close at once. `OpenUrlInCurrentTab` is how History and Bookmarks open a row. A legal page from welcome is `SkimNavigator.openLegal`, which closes that tab on Back. Allowing a site is `SetSkimAi`.

Backup and device transfer are off (`allowBackup=false` and `data_extraction_rules.xml`).

The install id is a UUID created on first use inside one DataStore edit (`SettingsRepository.installId`). Release requests send it as `X-Skim-Install`. It is not logged.
