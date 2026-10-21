# Skim design handoff

Skim is an AI browser for Android: summaries and answers about the page you are reading, built into the browser. AI runs in the cloud only. This folder holds the full design, exported from the Skim Design artifact: Material 3 Expressive, built for Jetpack Compose.

Source: Design artifact https://claude.ai/artifact/KnzPzj375YTSBKApKBaKND, version 1792504196-d130, exported 2026-10-20. The artifact is private to its owner.

## What is in this folder

| Folder | Contents |
|---|---|
| screens/ | A PNG of every board. Phone boards are 412 by 915 dp at 2x; boards 15 to 18 are at 1x. Boards with a state tweak have one PNG per state (name--state.png). |
| html/ | The same boards as static HTML pages. Open them in a browser; fonts load from Google Fonts. |
| spec/ | navigation-map.md: every control on every screen and what it opens. components.md: component specs with their Compose APIs. tokens.md and tokens.json: color, type, shape, motion, spacing, elevation and icons. dialogs.md: every dialog, menu and snackbar and where it opens from. |
| compose/ | Theme.kt, Type.kt and Color.kt for the ui.theme package, the Google Sans Flex variable font for res/font, and its OFL license. |
| icons/ | The 63 Material Symbols Rounded icons the boards use, as Android vector drawables: `ic_<name>.xml`, plus `ic_<name>_filled.xml` for icons drawn filled. Apache 2.0 license included. |
| source/ | The original board files (.dc.html) and canvas.json from the Design artifact. They need the artifact runtime to render, so look at screens/ or html/ instead; these files are the exact source. |

## Boards

| Board | PNG | HTML | Source | States |
|---|---|---|---|---|
| 01 Welcome | screens/01-welcome.png | html/01-welcome.html | source/Main.dc.html |  |
| 03 Default browser & import | screens/03-default-browser-import.png | html/03-default-browser-import.html | source/03-Setup.dc.html |  |
| 04 Home / New tab | screens/04-home-new-tab.png | html/04-home-new-tab.html | source/04-Home.dc.html |  |
| 04b Search & suggestions | screens/04b-search-suggestions.png | html/04b-search-suggestions.html | source/04b-Search.dc.html |  |
| 04c Skim history | screens/04c-skim-history.png | html/04c-skim-history.html | source/04c-Skim-History.dc.html |  |
| 05 Browsing | screens/05-browsing.png | html/05-browsing.html | source/05-Browsing.dc.html | Loaded (default); Loading (05-browsing--loading.png) |
| 05b More menu | screens/05b-more-menu.png | html/05b-more-menu.html | source/05b-Menu.dc.html |  |
| 05c Find in page | screens/05c-find-in-page.png | html/05c-find-in-page.html | source/05c-Find.dc.html |  |
| 05d Link & image options | screens/05d-link-image-options.png | html/05d-link-image-options.html | source/05d-Link-Menu.dc.html | Link (default); Image (05d-link-image-options--image.png) |
| 08 Reader view | screens/08-reader-view.png | html/08-reader-view.html | source/08-Reader.dc.html |  |
| 08b Reader text settings | screens/08b-reader-text-settings.png | html/08b-reader-text-settings.html | source/08b-Reader-Text.dc.html |  |
| 11 Page failed to load | screens/11-page-failed-to-load.png | html/11-page-failed-to-load.html | source/11-Load-Error.dc.html | Unreachable (default); No internet (11-page-failed-to-load--no-internet.png); Timed out (11-page-failed-to-load--timed-out.png) |
| 06 Ask about this page | screens/06-ask-about-this-page.png | html/06-ask-about-this-page.html | source/06-AI-Panel.dc.html |  |
| 06b Ask Skim from Home & Tabs | screens/06b-ask-skim-from-home-tabs.png | html/06b-ask-skim-from-home-tabs.html | source/06b-Ask-Home.dc.html |  |
| 06c AI generating & errors | screens/06c-ai-generating-errors.png | html/06c-ai-generating-errors.html | source/06c-AI-States.dc.html | Generating (default); Not enough text (06c-ai-generating-errors--not-enough-text.png); No connection (06c-ai-generating-errors--no-connection.png) |
| 06d AI permission, first time per site | screens/06d-ai-permission-first-time-per-site.png | html/06d-ai-permission-first-time-per-site.html | source/06d-AI-Permission.dc.html |  |
| 09 Tabs | screens/09-tabs.png | html/09-tabs.html | source/09-Tabs.dc.html |  |
| 09b Incognito tabs | screens/09b-incognito-tabs.png | html/09b-incognito-tabs.html | source/09b-Incognito-Tabs.dc.html |  |
| 09c Incognito new tab | screens/09c-incognito-new-tab.png | html/09c-incognito-new-tab.html | source/09c-Incognito-New-Tab.dc.html |  |
| 12 Settings | screens/12-settings.png | html/12-settings.html | source/12-Settings.dc.html |  |
| 10 AI & privacy settings | screens/10-ai-privacy-settings.png | html/10-ai-privacy-settings.html | source/10-AI-Settings.dc.html |  |
| 13 History | screens/13-history.png | html/13-history.html | source/13-History.dc.html |  |
| 14 Bookmarks | screens/14-bookmarks.png | html/14-bookmarks.html | source/14-Bookmarks.dc.html |  |
| 15 Dialogs, menus & snackbars | screens/15-dialogs-menus-snackbars.png | html/15-dialogs-menus-snackbars.html | source/15-Dialogs.dc.html |  |
| 16 Navigation map | screens/16-navigation-map.png | html/16-navigation-map.html | source/16-Navigation-Map.dc.html |  |
| 17 Tokens | screens/17-tokens.png | html/17-tokens.html | source/17-Tokens.dc.html |  |
| 18 Components & setup | screens/18-components-setup.png | html/18-components-setup.html | source/18-Components.dc.html |  |

Screen numbers skip 02 and 07 on purpose: those screens were removed during design.

## Build notes

- Jetpack Compose with androidx.compose.material3 1.5.0. At export time it is 1.5.0-beta01 (October 7, 2026); move to the stable release when it ships. 1.4.0 lacks some APIs the design uses, such as segmented list items and menu groups. Opt in to ExperimentalMaterial3ExpressiveApi where the compiler asks.
- Theme: MaterialExpressiveTheme with MotionScheme.expressive(). Android 12 and later use dynamic color; below that, use the brand schemes in compose/Color.kt.
- Copy compose/*.kt into your ui.theme package and change the package line and the R import; they assume com.skim.browser.
- Copy compose/res/font/google_sans_flex.ttf to app/src/main/res/font/. The weight axis needs API 26 or later. List the font under Open-source licenses (12).
- Copy icons/*.xml to app/src/main/res/drawable/.
- Skim AI is tertiary: every AI surface uses the tertiary roles and the Sunny shape, and nothing else does. Primary marks selection and main actions.
- UI copy is English, exactly as drawn, kept in strings.xml with plurals for counts. Never join pieces of text with a dot separator.
- Touch targets are 48 dp or more. Every icon button has a content description; use the aria-label text from html/.
- The top 24 dp of each phone board stands for the status bar. Draw edge to edge with WindowInsets; pages run under the floating toolbar.
- The pages drawn inside 05 to 05d and 06 to 06d are sample site content, not app tokens.
- Each phone board has a Dynamic color tweak in the artifact that previews four seed colors. The PNGs use the brand seed #1A56DB.

## Not designed yet

These are known gaps; the boards do not cover them yet:

- Security states: the address bar and site information for http pages, SSL certificate errors, and dangerous site warnings (Safe Browsing).
- Dialogs opened by web pages: alert, confirm, prompt, Leave site, and HTTP sign-in.
- AI errors beyond No connection: server errors, and a usage limit if Skim AI gets one.
- Search mode and no-results states on Tabs, Incognito tabs, Skim history, History and Bookmarks.
- Empty states: Home on first launch, Skim history, History, Bookmarks, and Tabs after Close all tabs.
- An opened bookmark folder, Find in page with no matches, the page highlight after tapping a citation, and the Shortcut removed and import failed snackbars.
- Open decisions: an in-app Downloads screen, a list of sites Skim AI may read (in AI & privacy), dark versions of the regular screens, and landscape or tablet layouts.

## Using this with Claude Code

Start from this README, then spec/navigation-map.md for behavior, spec/components.md and spec/tokens.md for the build, and screens/ for the visuals. html/ shows exact sizes, colors and copy when a PNG is not enough.
