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
domain/  models, repository interfaces, use cases     pure Kotlin
        ^
        |
data/  Room, DataStore, concrete repositories, mappers
        ^
        |
AppContainer                         lives with the process
  wires data implementations into domain interfaces and use cases
  TabManager, IncognitoSession, SkimAiService, AiGate, TabGrouping
  ChatSessionStore and AiDataEraser call chat use cases
```

Dependencies point inward: `ui` depends on `domain`, and `data` depends on `domain`. `domain` does not import Android, `data`, `ui`, `browser`, or `ai`. `DomainBoundaryTest` checks that, checks that `ui` sources do not import `data`, and checks that only `AppContainer` outside `data/` imports the concrete chat repository. `AppContainer` is the composition root, so it sees both sides. `browser` and `ai` take domain repository interfaces. `data` does not call `ui` or `browser`.

A ViewModel reads through a repository interface when the call is a pass-through (`observe`, `load`). A write goes through a use case in `domain/usecase`: one action, `operator fun invoke`. The concrete repositories in `data/repo` implement the interfaces and map entities to domain models in `data/repo/DomainMappers.kt`. Settings types (`SearchEngine`, `ThemeSetting`, `ReaderFont`, `SkimSettings`) live in `domain/settings`. The old `data` and `ui.theme` names are typealiases so Room and existing call sites keep compiling.

`ChatSessionStore` writes a transcript through use cases (`CreateSavedChat`, `ReadSavedMessages`, `AddSavedMessage`, `UpdateSavedChat`, `UpdateSavedMessage`) on `domain.chat.ChatRepository`. `AiDataEraser` clears chats with `DeleteAllSkimChats`. Neither type constructs the Room repository. `AppContainer` is the only type outside `data/` that does.

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

`TabGrouping` runs when the normal tabs screen opens, skips fewer than four eligible tabs, skips when offline, and skips a set it already grouped. Undo restores the previous layout.

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

Clear browsing data and the two Skim chat deletes finalize a waiting undo, and dismiss its snackbar, before the rows are removed. `AiDataEraser` and `BrowsingDataCleaner` call `PendingUndo`. `SnackbarUndo` is the implementation and is the type that knows the snackbar.

Backup and device transfer are off (`allowBackup=false` and `data_extraction_rules.xml`).

The install id is a UUID created on first use inside one DataStore edit (`SettingsRepository.installId`). Release requests send it as `X-Skim-Install`. It is not logged.
