# System architecture

```
MainActivity
  BrowserHost (WebViewPool, chrome client)   lives with the Activity
  SkimNavigator (back stack)
        |
        v
AppContainer                         lives with the process
  TabManager  ---- TabRepository, HistoryRepository, SettingsRepository
  IncognitoSession ---- BrowserProfiles
  SkimAiService ---- ClaudeClientProvider, SitePermissionRepository
  AiGate, ChatSessionStore, TabGrouping
  Room + DataStore
```

Dependencies point inward toward `data/` and `browser/`. `ui/` calls those types. `data/` does not call `ui/` or `browser/`.

## Tabs and WebViews

`TabManager` owns the tab list, groups, the current tab, and the restore path. It is created once per process. It does not hold WebViews.

`WebViewPool` is created with the Activity (`BrowserHost`). It keeps a small number of live WebViews and rebinds them when the current tab changes. `MainActivity.browserHostForTests` exposes the host to instrumented tests. `pool.destroyAll()` is what a process-death test uses to drop every WebView before `Activity.recreate()`.

Opening a tab is `TabManager.newTab` on the main thread. Layout changes (group, reorder, ungroup) go through `applyLayout`, which returns false if the caller's layout version is stale. That is how a grouping result is dropped when the person moved a tab while the request was in flight.

`restore()` reads Room on a background dispatcher and applies the result on the main thread. Incognito tabs are not in the database, so they do not come back. A restore that cannot read the database starts a new tab and does not overwrite the saved rows (`RestoreResult.Fallback`).

## Incognito

`IncognitoSession` asks `BrowserProfiles` for a profile per session. `WebViewFeature.MULTI_PROFILE` has to be present. When it is not, new incognito tabs are refused and the UI says the system WebView needs an update.

The session has a state (`None`, active, `Ending`). An ending session is not reused. Ending the session clears the profile (cookies, and the HTTP cache when the device can) and deletes `cache/shared/incognito`. `FLAG_SECURE` is set while an incognito tab or an incognito sheet is on screen, including while that screen is fading out. Instrumented coverage of the flag is `SecureFlagTest`; it has not been run on a device in this environment.

Incognito pages never reach `SkimAiService`. The service rejects them before the client is created.

## Ask Skim

`AiGate.decide` runs before page text is read:

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

Backup and device transfer are off (`allowBackup=false` and `data_extraction_rules.xml`).

The install id is a UUID created on first use inside one DataStore edit (`SettingsRepository.installId`). Release requests send it as `X-Skim-Install`. It is not logged.
