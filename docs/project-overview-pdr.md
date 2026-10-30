# Skim — product overview

The product rules are in `docs/product-vision.md`. When this page and that one disagree, the vision wins.

Skim is a phone browser. The person opens pages, keeps them in tabs, and can ask Skim about the page they are reading or about the normal tabs they have open. Answers come from Claude in the cloud. The app does not run a model on the device.

The visual source is `design/`. Phone boards are 412×915 dp. Boards 16–18 are documentation (navigation map, tokens, components), not screens.

## Who it is for

Someone who wants a small browser with a page summary and follow-up questions, plus the usual tab, bookmark, history, and incognito controls. Copy is English.

## What this version includes

- Welcome, then a setup step that can set Skim as the default browser and import a bookmarks file.
- Home with shortcuts, a continue card, and search. Search suggestions come from the selected engine.
- One WebView per tab, a floating toolbar, find in page, reader view, and link or image actions.
- Tabs, including groups. Opening the normal tabs screen can group ungrouped tabs, with Undo.
- Incognito tabs in their own WebView profile. They are never sent to Skim.
- History, bookmarks (Mobile bookmarks and Imported), and Skim history (saved chats).
- Settings: search engine, theme, AI and privacy, clear browsing data, licenses, and legal links when those URLs are configured.
- Ask Skim about the current page after the site is allowed, and about the open normal tabs. Sites set to off are left out.

How those rules are enforced is `docs/code-standards.md` and `docs/system-architecture.md`.

## What it does not include

The gaps are the "Not designed yet" list in `design/README.md`. Work that is still open is `docs/project-roadmap.md`. The release proxy is not part of this repository. Its contract is `docs/deployment-guide.md`.

## Release bar

A release build must not contain an API key. It must call a proxy that meets `docs/deployment-guide.md`. Until that proxy exists, release stays blocked even when `assembleRelease` succeeds against a placeholder URL.

UI follows `design/`. Accepted visual gaps are `docs/design-guidelines.md`.

Coverage from `:app:koverHtmlReportDebug` is reported with the build. It is not a failing Gradle rule. The filters are the `kover` block in `app/build.gradle.kts`.
