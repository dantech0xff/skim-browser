---
phase: 1
title: "Splash theme and icon"
status: done
priority: P2
effort: "2h"
dependencies: []
---

# Phase 1: Splash theme and icon

## Goal

Add `core-splashscreen` 1.2.0, a splash theme for day and night, and a splash icon whose sparkles stay white on both backgrounds.

## Files to Create / Modify

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/drawable/ic_splash_icon.xml`
- Create: `app/src/main/res/values-v33/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Do not modify: `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `colors.xml`, `SkimApp.kt`

## Tasks & Steps

1. In `gradle/libs.versions.toml`, add `splashscreen = "1.2.0"` and `androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }`. In `app/build.gradle.kts`, add `implementation(libs.androidx.core.splashscreen)` next to `implementation(libs.androidx.core.ktx)` (around line 350). Do not switch the activity to AppCompat. The 1.2.0 AAR depends on `appcompat-resources`, not `appcompat`.
2. Create `ic_splash_icon.xml` from the path in `ic_launcher_foreground.xml`. Keep viewport 108 and center (54, 54). Wrap the artwork in a group with `pivotX`/`pivotY` 54 and `scaleX`/`scaleY` `1.055`. After that scale, every vertex must stay inside radius 36 (the splash mask). Do not shrink to the launcher safe radius 33. Split the single even-odd path into two paths: the Sunny outline filled `#0B57D0`, and the three sparkle subpaths filled `#FFFFFF` and drawn after it. No background rect. No `android:fillType="evenOdd"`.
3. In `values/themes.xml`, add `Theme.SkimBrowser.Splash` with parent `Theme.SplashScreen` (not `Theme.SplashScreen.IconBackground`). Set the unprefixed items `windowSplashScreenBackground` to `@color/skim_window_light`, `windowSplashScreenAnimatedIcon` to `@drawable/ic_splash_icon`, and `postSplashScreenTheme` to `@style/Theme.SkimBrowser`. Do not set `windowSplashScreenIconBackgroundColor`, animation duration, or a branding image. Do not add `androidx.appcompat:appcompat`. Keep the app's own `colorControlNormal` declaration.
4. In `values-night/themes.xml`, declare `Theme.SkimBrowser.Splash` again with `parent="Theme.SplashScreen"` and all three items. Set `windowSplashScreenBackground` to `@color/skim_window_dark`. A night style replaces the day style. The parent is not an item, so it must be written again or night falls back to the library default icon and `postSplashScreenTheme`.
5. In `values-v33/themes.xml`, add the same style with the same parent and items, plus `android:windowSplashScreenBehavior` set to `icon_preferred`. From API 33 a start that is not from the launcher omits the icon unless that platform attribute is set. The compat library does not forward it.
6. On `<activity android:name=".MainActivity">` in `app/src/main/AndroidManifest.xml`, set `android:theme="@style/Theme.SkimBrowser.Splash"`. Leave `android:theme="@style/Theme.SkimBrowser"` on `<application>`. `HiltRouteActivity` and `BrowserHostActivity` have no theme of their own and inherit the application theme either way.

## Verification

- `./gradlew :app:processDebugResources --no-configuration-cache` succeeds and `Theme.SplashScreen` resolves.
- The night style lists `parent="Theme.SplashScreen"` and all three splash items.
- `values-v33` sets `android:windowSplashScreenBehavior` to `icon_preferred`.

## Risk Assessment

- **Assumption:** scale `1.055` keeps every vertex inside radius 36. It fails if a peak crosses that circle. Response: lower the scale, keep the white sparkle fill, and do not target radius 33. Do not add an icon background disc.
- A night style that omits `parent="Theme.SplashScreen"` falls back to the library icon and a `postSplashScreenTheme` that points at the splash theme itself. Step 4 writes the parent again.

## Security Considerations

No new permissions, network calls, or stored data. Do not add `androidx.appcompat:appcompat`. On API 26–30, `installSplashScreen()` inflates the icon with `AppCompatResources` before it applies `postSplashScreenTheme`. Phase 2's test inflates `ic_splash_icon` under the splash theme so that path cannot throw unseen.

## Next Steps

Phase 2 installs the splash in `MainActivity` and locks the theme values in a test.
