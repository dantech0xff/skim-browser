# Skim — product overview

Skim is a phone browser. The person opens pages, keeps them in tabs, and can ask Skim about the page they are reading or about the normal tabs they have open. Answers come from Claude in the cloud. The app does not run a model on the device.

The visual source is `design/`. Phone boards are 412×915 dp. Boards 16–18 are documentation (navigation map, tokens, components), not screens.

## Who it is for

Someone who wants a small browser with a page summary and follow-up questions, plus the usual tab, bookmark, history, and incognito controls. Copy is English.

## What it does

- Welcome, then a setup step that can set Skim as the default browser and import a bookmarks file.
- Home with shortcuts, a continue card, and search. Search suggestions come from the selected engine.
- One WebView per tab, a floating toolbar, find in page, reader view, and link or image actions.
- Tabs, including groups. Opening the normal tabs screen can group ungrouped tabs (four or more, titles and hosts only) with Undo.
- Incognito tabs in a separate WebView profile. They are not restored after the process dies, and they are never sent to Skim.
- History, bookmarks (Mobile bookmarks and Imported), and Skim history (saved chats).
- Settings: search engine, theme, AI and privacy, clear browsing data, licenses, legal links when those URLs are configured.
- Ask Skim about the current page (summary, citations, follow-ups) after the site is allowed. Ask Skim about the open normal tabs. Sites set to off are left out.

## What it does not do

Listed in `design/README.md` under "Not designed yet", and repeated in `docs/project-roadmap.md`. In short: no SSL or Safe Browsing UI of our own (the platform defaults stay), no page-opened JS dialogs of our own, no in-app downloads screen, no per-site AI list, no tablet or landscape layout. The release proxy is not part of this repository.

## Requirements that bound the build

- The debug app builds with `assembleDebug` and the main flows are implemented: browse, tabs, reader, settings, and Ask Skim against a configured endpoint.
- Screens observe ViewModels. ViewModels hold UI state. Navigation and side effects go through `SkimNavigator` and Hilt-provided use cases, not through composables deciding policy.
- Data access goes through repository interfaces and use cases in `domain/`. Concrete repositories in `data/` implement the interfaces and map Room and DataStore types there. Hilt constructs both. ViewModels do not depend on concrete `data` classes. `DomainBoundaryTest` keeps `domain` free of Android and outer layers, keeps `ui` off `data` and `ai` and off concrete `browser` types except the route files that attach the Activity host, and keeps `ai`, `browser`, and `data` from naming `ui`.
- Unit tests cover ViewModels, repositories, and the AI and browser logic. Coverage is measured with Kover. `:app` aggregates `:domain` and `:data`. Generated classes are excluded. Composables are not excluded to inflate the number. The measured figure is reported with the build, not asserted as a failing Gradle rule.
- UI follows `design/` (layout, color, type, spacing, icons). Remaining differences are listed in `docs/design-guidelines.md`.

## Release bar

A release build must not contain an API key. It must call a proxy that meets `docs/deployment-guide.md`. Until that proxy exists, release is blocked even if `assembleRelease` succeeds against a placeholder URL.
