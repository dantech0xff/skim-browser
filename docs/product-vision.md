# Product vision

Dan set these rules for this version of Skim. When another document disagrees, this page wins. Code that does not meet a rule yet is a gap, not an exception.

## An AI browser with no ads

Skim is an AI-powered web browser for Android. The app shows no ads of its own. Blocking ads on the pages a person opens is deferred to a later phase. The interface uses Material 3 Expressive.

This phase does not add a filter list or a request blocker. The AI proxy stays the contract in `docs/deployment-guide.md`.

## Android only

Skim runs on Android. This version does not include an iOS app, a desktop app, or a web client.

## MVVM and Hilt

Each screen observes a ViewModel. Hilt constructs dependencies. The `domain` module holds models, repository interfaces, and use cases. `DomainBoundaryTest` rejects a dependency that points from `domain` out to Android or to an outer module.

## Android WebView only

Pages render in Android WebView. This version does not add GeckoView, an embedded Chromium, or any other page engine.

## Smooth Compose

Jetpack Compose has to stay smooth.

- State that a composable reads is immutable. Mark that state `@Immutable` or `@Stable`.
- Derive a value with `remember` or `derivedStateOf` when recomputing it would repeat work on every frame.
- Keep lambdas that composables capture stable across recomposition.
- The release build includes a baseline profile.
- Switching tabs, moving the Skim AI sheet, and navigating between screens do not drop frames.
- Measure frame time on a device with Macrobenchmark or JankStats. Report the numbers from that run.
