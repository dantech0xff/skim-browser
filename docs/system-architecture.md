# System architecture

Product rules: `docs/product-vision.md`. Engineering rules: `docs/code-standards.md`. Where to open each module: `docs/codebase-summary.md`.

```
:app
  SkimApplication, MainActivity, ui/
  di/   process resources, use cases, ClaudeClientFactory
        |
        v
:domain   models, repository interfaces, use cases
        ^
        |
:data     Room, DataStore, repositories          bindings in data/di
:browser  tab engine, WebViews, downloads        bindings in browser/di
            depends on :domain and :data
:ai       Claude adapter, chats, grouping        bindings in ai/di
            depends on :domain and :browser
```

`:baselineprofile` and `:macrobenchmark` measure the app. They are not on this path.

Dependencies point inward. The check is `DomainBoundaryTest`.

`TabManager` is the process-wide tab list and does not hold WebViews. `WebViewPool` is created with the Activity (`BrowserHost`). They meet only through `TabEngineBridge`, between attach and detach. `MainActivity` plugs the pool in on `onCreate` and unplugs it on `onDestroy`.

## Decisions

| Decision | Owner |
|---|---|
| Incognito tabs are never restored. Undo can bring them back only while that session is still open. A device check is still open. | `domain/src/main/kotlin/com/dantech/skimbrowser/domain/tab/TabRules.kt`. Open check: `docs/project-roadmap.md`. |
| Incognito uses its own WebView profile and never falls back to the default profile. | `browser/src/main/java/com/dantech/skimbrowser/browser/IncognitoSession.kt` |
| A chat is about the page URL without its fragment. A fragment change keeps the chat. | `domain/src/main/kotlin/com/dantech/skimbrowser/domain/url/UrlParts.kt` (`withoutFragment`), `ai/src/main/java/com/dantech/skimbrowser/ai/ChatSessionStore.kt` |
| Downloads cover http(s), `data:`, and `blob:` only. An in-app downloads screen was never designed. | `browser/src/main/java/com/dantech/skimbrowser/browser/downloads/DownloadRouter.kt`. Gap: `design/README.md` |
| One consent rule covers asking about a page, the site switch, and the request that is about to be sent. Incognito never reaches the model. | `domain/src/main/kotlin/com/dantech/skimbrowser/domain/permission/ResolveSkimAiConsent.kt`, `ai/src/main/java/com/dantech/skimbrowser/ai/AiGate.kt`, `ai/src/main/java/com/dantech/skimbrowser/ai/SkimAiService.kt` |
| An across-tabs ask leaves out incognito tabs and sites set to ask again. Live page text is read only from a tab that already has a WebView. | `domain/src/main/kotlin/com/dantech/skimbrowser/domain/ai/CollectOpenTabs.kt` |
| Grouping normal tabs does not send page text. Undo is offered while the tabs screen is open. | `ai/src/main/java/com/dantech/skimbrowser/ai/TabGrouping.kt` |
| The only model id is the one in `AiConfig`. | `ai/src/main/java/com/dantech/skimbrowser/ai/AiConfig.kt` |
| The install id is created once, kept for the life of the installation, sent as `X-Skim-Install` on a release request, and never logged. | `data/src/main/java/com/dantech/skimbrowser/data/settings/SettingsRepository.kt` (`installId`). Header: `ai/src/main/java/com/dantech/skimbrowser/ai/ClaudeClientProvider.kt`. Proxy rules: `docs/deployment-guide.md` |
| Browsing history, bookmarks, Skim chats, WebView cookies, and the install id stay on the phone. | The backup comment in `app/src/main/AndroidManifest.xml`, and `app/src/main/res/xml/data_extraction_rules.xml` |
| Home shortcuts are seeded once. Clearing them does not bring the presets back. A different catalog replaces the binding, not the seeder. | `data/src/main/java/com/dantech/skimbrowser/data/shortcut/ShortcutSeeder.kt`, `domain/src/main/kotlin/com/dantech/skimbrowser/domain/shortcut/ShortcutCatalog.kt` |
| Cleartext is allowed so pages can be `http://`. Skim's own AI and legal endpoints stay https. | `app/src/main/res/xml/network_security_config.xml` |
| The local network permission is not requested. Pages that need it fail the way the platform fails them. | `app/src/main/AndroidManifest.xml` |
| Incognito content is kept off screenshots and off the recents preview while it is on screen. | `browser/src/main/java/com/dantech/skimbrowser/browser/WindowSecureController.kt`. Clipboard sensitivity: `app/src/main/java/com/dantech/skimbrowser/system/Clipboard.kt` |
