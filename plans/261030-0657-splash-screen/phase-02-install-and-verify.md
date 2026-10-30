---
phase: 2
title: "Install and verify"
status: done
priority: P2
effort: "2h"
dependencies: [1]
---

# Phase 2: Install and verify

## Goal

Install the splash before the activity window is created, then prove the day and night theme values.

## Files to Create / Modify

- Modify: `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt`
- Create: `app/src/test/java/com/dantech/skimbrowser/ui/theme/SplashThemeTest.kt`
- Do not modify: `SkimApp.kt`, `SkimAppViewModel.kt`, `SystemBarsEffect.kt`

## Tasks & Steps

1. In `MainActivity.onCreate`, call `installSplashScreen()` first, then the existing `enableEdgeToEdge()`, then `super.onCreate(savedInstanceState)`. Leave the rest of `onCreate` as it is, including `setContent` and `window.decorView.post { incognito.purgeStale() }`. Import `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`.
2. Do not call `setKeepOnScreenCondition`. The empty window in `SkimApp` (`SkimApp.kt:56-60`) is the same color as the splash background. Holding the splash until `started` can last through both 8 second timeouts in `startUp()`.
3. Leave the second `enableEdgeToEdge()` inside `SystemBarsEffect` (`SystemBarsEffect.kt:25-28`). It runs after composition and sets bar icon colors from the app theme. Splash install only has to precede the call in `onCreate`.
4. Add `SplashThemeTest` (Robolectric). Apply `R.style.Theme_SkimBrowser_Splash`. Resolve the library attributes and, under `@Config(sdk = [31])`, `android.R.attr.windowSplashScreenBackground` and `android.R.attr.windowSplashScreenAnimatedIcon`. Assert the packed day color `#FFFFFF` and night color `#202124` (`@Config(qualifiers = "night")`), not only a resource id. Assert `postSplashScreenTheme` resolves to `R.style.Theme_SkimBrowser` in both configurations, not to the splash style. Inflate `R.drawable.ic_splash_icon` with the splash theme applied at `@Config(sdk = [26])` and `@Config(sdk = [30])`. That is the API 26–30 path that loads the icon before `setTheme`. Do not assert pixels. Do not add an instrumented `ACTION_VIEW` test.

## Verification

- `./gradlew :app:testDebugUnitTest --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.theme.SplashThemeTest'`
- `./gradlew :app:compileDebugAndroidTestKotlin --no-configuration-cache` so `MainActivityLifecycleTest` still type-checks with `installSplashScreen()` in `onCreate`. A device run of that class is the cold-start check, not part of this gate.

## Risk Assessment

- **Assumption:** `installSplashScreen()` before `enableEdgeToEdge()` lets 1.2.0 apply `postSplashScreenTheme` before the activity keeps using the splash theme. It fails if those two calls are reordered. Response: restore the order in step 1. That order does not control `isNavigationBarContrastEnforced`. Activity 1.13 sets that flag from `SystemBarStyle.auto` inside `enableEdgeToEdge()`. Leave `SystemBarsEffect` as it is.
- **Assumption:** Robolectric can resolve `Theme.SplashScreen` from the new dependency. It fails if the style parent does not inflate in the unit-test classpath. Response: keep the resource test and treat a missing parent as a dependency wiring bug, not a reason to delete the test.

## Security Considerations

`installSplashScreen()` only swaps the starting window theme. `receiveLink` stays where it is, after `super.onCreate`. This phase does not change how an external URL is queued.

## Next Steps

None. Cold start on a device, day and night, is the manual check after the gates above pass.
