# Product vision

Dan set these rules for this version of Skim. When another document disagrees, this page wins. Code that does not meet a rule yet is a gap, not an exception.

## An AI browser with no ads

Skim is an AI-powered web browser for Android. The app shows no ads of its own. Pages drop requests whose hosts are on the compiled EasyList and EasyPrivacy snapshot, and leftover ad frames and empty slots are hidden with the cosmetic rules from that same snapshot. A window a page opens on its own stays closed, and a top-frame jump to another site while the page is idle is stopped, until the person allows it. Common cookie banners from the EasyList Cookie snapshot are hidden, and a page they locked can scroll. The interface uses Material 3 Expressive.

The snapshot is built by `tools/compile_blocklist.py` from a pinned upstream commit. Hosts ship as hashes. Cosmetic `##` and `#@#` rules ship as a stylesheet the page installs when blocking is on for that site. `shield/cookie.txt` is the EasyList Cookie general hide list from that same pin, capped at 1,800 selectors, and it is installed only while cookie banners are hidden. EasyList and EasyPrivacy are dual-licensed; Skim uses the GPL-3.0-or-later option and attributes The EasyList authors (https://easylist.to/). The app does not download a newer list, so a refresh is a new APK and the install does not contact EasyList. Procedural and scriptlet rules are not applied. The AI proxy stays the contract in `docs/deployment-guide.md`.

Blocked requests, popups, redirects, and cosmetic rules are counted on the device. The totals and the estimate of data and time saved stay in the local database. A private tab adds to the counts and does not add a site to the list. The formula is `docs/privacy-stats.md`.

## Android only

Skim runs on Android. This version does not include an iOS app, a desktop app, or a web client.

## MVVM and Hilt

Each screen observes a ViewModel. Hilt constructs dependencies. The `domain` module holds models, repository interfaces, and use cases. `DomainBoundaryTest` rejects a dependency that points from `domain` out to Android or to an outer module.

## Android WebView only

Pages render in Android WebView. This version does not add GeckoView, an embedded Chromium, or any other page engine.

## Scam warnings stay on the device

Scam and phishing warnings are on by default. One layer is Android System WebView Safe Browsing, which Google provides through the WebView. The other layer is a check in the app for deceptive addresses. Skim does not receive those addresses and does not send them to a Skim server. The person can turn both layers off under Privacy & security. Proceeding on a warning allows that host only until they leave Skim.

## Smooth Compose

Jetpack Compose has to stay smooth.

- State that a composable reads is immutable. Mark that state `@Immutable` or `@Stable`.
- Derive a value with `remember` or `derivedStateOf` when recomputing it would repeat work on every frame.
- Keep lambdas that composables capture stable across recomposition.
- The release build includes a baseline profile.
- Switching tabs, moving the Skim AI sheet, and navigating between screens do not drop frames.
- Measure frame time on a device with Macrobenchmark or JankStats. Report the numbers from that run.
