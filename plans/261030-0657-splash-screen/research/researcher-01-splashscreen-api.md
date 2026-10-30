# androidx.core:core-splashscreen for Skim Browser

**Researched:** 2026-10-30
**App constraints:** minSdk 26, targetSdk 37, compileSdk 37, Jetpack Compose, `ComponentActivity`, `enableEdgeToEdge()` already called before `super.onCreate()`. Single activity (`MainActivity`). Application theme is `Theme.SkimBrowser` (`android:Theme.Material.Light.NoActionBar` / night `android:Theme.Material.NoActionBar`), whose only window job is the pre-first-frame `windowBackground`. No app source was changed.

## Outcome

Use **`androidx.core:core-splashscreen:1.2.0`**. **1.0.1 is not the latest stable.** It was the previous stable (19 April 2023). There is no 1.1.0 stable. 1.2.0 shipped 5 November 2025 and is still the only stable, with no newer RC, beta, or alpha on Google Maven as of this date.

Put a `Theme.SplashScreen` theme on **the launcher activity only**, point `postSplashScreenTheme` at `@style/Theme.SkimBrowser`, and call `installSplashScreen()` **before** the existing `enableEdgeToEdge()`, which stays before `super.onCreate()`. Do not hold the splash for WebView or network work. Draw a splash vector whose artwork sits inside the 192 dp circle of a 288 dp canvas. A 108 dp adaptive foreground whose art is only in the center 66 dp is not clipped, but it does not fill the splash circle.

## Contents

1. [Version](#1-version)
2. [Theme and attributes](#2-theme-and-attributes)
3. [installSplashScreen call order](#3-installsplashscreen-call-order)
4. [setKeepOnScreenCondition](#4-setkeeponscreencondition)
5. [Which theme, and what postSplashScreenTheme restores](#5-which-theme-and-what-postsplashscreentheme-restores)
6. [Icon canvas, mask, and a 108 dp foreground](#6-icon-canvas-mask-and-a-108-dp-foreground)
7. [Fit for this app](#7-fit-for-this-app)
8. [Limitations](#8-limitations)
9. [Sources](#9-sources)

## 1. Version

| | |
|---|---|
| Coordinate | `androidx.core:core-splashscreen:1.2.0` |
| Packaging | AAR |
| Gradle status | `release` |
| Published | 5 November 2025 |
| Maven `latest` / `release` | `1.2.0` |
| Maven `lastUpdated` | `20251105180234` |
| Library minSdk | 21 (AAR manifest). The guide describes support from API 23. minSdk 26 is inside both. |
| Runtime deps | `androidx.appcompat:appcompat-resources:1.7.0`, `androidx.annotation:annotation:1.8.1`, Kotlin stdlib. `appcompat-resources` 1.7.0 depends on `androidx.core:core`, `vectordrawable`, and `vectordrawable-animated`. It does **not** depend on `appcompat` itself, so this does not force `AppCompatActivity` or an AppCompat theme. |

Google Maven `maven-metadata.xml` lists every version and stops at `1.2.0`. The same list is in `androidx/core/group-index.xml`. The [Core release page](https://developer.android.com/jetpack/androidx/releases/core) table shows `core-splashscreen` stable **1.2.0** and blanks for RC, beta, and alpha. The [stable channel](https://developer.android.com/jetpack/androidx/versions/stable-channel) records "Core-splashscreen Version 1.2.0" on 5 November 2025 and no later splashscreen line through the 21 October 2026 entries. The [splash screen guide](https://developer.android.com/develop/ui/views/launch/splash-screen), updated 2026-10-29, tells you to depend on `1.2.0`.

Stable history that matters:

| Version | Date | What it actually changed |
|---|---|---|
| 1.0.0 | 27 July 2022 | First stable. Backport from API 23. |
| 1.0.1 | 19 April 2023 | Wear default icon size only (48 dp). |
| 1.1.0-rc01 | 3 April 2024 | Never promoted to 1.1.0 stable. |
| 1.2.0-alpha01 | 17 April 2024 | The fixes this app needs. See below. |
| 1.2.0-alpha02 | 4 September 2024 | `isLightTheme` on splash themes; icon loaded via `AppCompatResources`. |
| 1.2.0-beta01 through 1.2.0 | 26 Feb 2025 – 5 Nov 2025 | No further change notes. Those two alphas are the 1.2.0 behavior. |

1.2.0-alpha01, and therefore 1.2.0, fixed three bugs that 1.0.1 still has:

- Reset `enforceNavigationBarContrast` so the splash theme does not leak onto the activity. From API 31 the splash is not a `PhoneWindow`, so inheriting the API 29 contrast value was wrong. The library also stopped copying that attribute back onto the activity in `applyAppSystemUiTheme`.
- Default light navigation bar follows day/night, so API 33+ does not flicker when the splash is removed.
- On API 33+, do not overwrite the activity theme after the platform delivers the splash view.

The 1.2.0 AAR still sets `android:enforceNavigationBarContrast` to `true` on the **splash** theme at API 31+ (`values-v31`). The fix is that this value is no longer forced onto the post-splash activity. That only holds if `installSplashScreen()` applies `postSplashScreenTheme` before edge-to-edge code reads window flags. See [call order](#3-installsplashscreen-call-order).

**Adoption risk.** Low. The artifact is the platform compat library, Apache-2.0, from the Android Open Source Project. It has been stable since 2022. The 1.0.1 → 1.2.0 jump is the risk of staying behind, not of moving forward: 1.0.1 predates the contrast and API 33 theme fixes, and this app targets SDK 37 and already calls `enableEdgeToEdge()`. No deprecation of the library itself. The stale migration guide still shows `core-splashscreen:1.0.0-beta02` and `compileSdkVersion 31`. Ignore that snippet. The guide updated 2026-10-29 and Maven agree on 1.2.0.

This app already depends on `androidx.core:core-ktx:1.19.1` and `androidx.activity:activity:1.13.0`. Splashscreen is a separate artifact. It is not included in `core` 1.19.1.

## 2. Theme and attributes

Parent must be `Theme.SplashScreen`. Use `Theme.SplashScreen.IconBackground` only when you want a solid disc behind the icon. The library says that second parent is what makes the icon scale and mask match Android 12. Both themes clear the action bar and title, set an opaque window, draw system bars, and use a transparent status and navigation bar.

`Theme.SplashScreen` in the 1.2.0 AAR defaults:

- `windowSplashScreenBackground` = `?android:colorBackground`
- `windowSplashScreenAnimatedIcon` = `@android:drawable/sym_def_app_icon`
- `windowSplashScreenAnimationDuration` = `10000` (`default_icon_animation_duration`)
- `postSplashScreenTheme` = `?android:attr/theme` (the splash theme itself — override this)
- `splashScreenIconSize` = `288dp`

Set the **library** attribute names, without the `android:` prefix. The AAR defines those names in `res/values`. Only `values-v31` copies them onto `android:windowSplashScreenBackground`, `android:windowSplashScreenAnimatedIcon`, `android:windowSplashScreenAnimationDuration`, and, for `Theme.SplashScreen.IconBackground`, `android:windowSplashScreenIconBackgroundColor`. The customize section of the views guide shows the `android:` prefix. The [migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) shows the unprefixed names. For minSdk 26, unprefixed is the one that works on API 26–30 and is forwarded to the platform on API 31+.

The background must be one opaque color. Day and night are supported.

| Attribute | Required? | Who honors it |
|---|---|---|
| `windowSplashScreenBackground` | Set it. Default is `?android:colorBackground`, which is not `Theme.SkimBrowser`'s window color. | Compat library on API 21+ (this app: 26–30). Platform `android:` attr from API 31 (present in SDK 31, absent in SDK 30). Still in SDK 37.1. |
| `windowSplashScreenAnimatedIcon` | Required by the migration guide ("one of these is required"). Static drawable below API 31. Animated vector only on API 31+. | Same split. If you omit it, API 31+ falls back to the launcher icon. |
| `postSplashScreenTheme` | Required. The migration sample says so, and the library default points at the splash theme. | Library only. Not a platform attribute. Applied on every API by `activity.setTheme()` inside `installSplashScreen()`. |
| `windowSplashScreenIconBackgroundColor` | Optional. Ignored unless the parent is `Theme.SplashScreen.IconBackground`. | Compat: oval behind the icon, then the 160 dp mask. Platform attr from API 31, and the v31 `IconBackground` style is what copies the library attr onto it. The platform docs say the shape is masked like an icon. |
| `windowSplashScreenAnimationDuration` | Optional for a static icon. The migration guide says required for an animated icon. The API reference says it does **not** change how long the splash stays. It only feeds `SplashScreenViewProvider.iconAnimationDurationMillis`. | Platform uses it on API 31 and 32. [SDK 37.1 `attrs.xml`](https://developer.android.com/reference/android/R.attr#windowSplashScreenAnimationDuration) and the API 33 SDK both mark it deprecated: "Not used by framework starting from API level 33. The system estimates the duration of the vector animation automatically." [Android 13 features](https://developer.android.com/about/versions/13/features) say the same. The views guide's phrase "targeting Android 12 (API level 32) only" is wrong: API 32 is Android 12L, and the attribute exists and is read on API 31 as well. Cap the animation at 1000 ms. A start delay cannot exceed 166 ms. |
| `windowSplashScreenBrandingImage` | Optional. Design guidance says not to use it. Must be 200×80 dp. | Platform `android:` attr from API 31 through at least SDK 37.1. It is not deprecated there. The compat library does not declare the attr and `splash_screen_view.xml` has no bottom image, so API 26–30 never show it. AOSP `SplashscreenContentDrawer` only attaches it for a real splash starting window (`STARTING_WINDOW_TYPE_SPLASH_SCREEN`). |

`windowSplashScreenBehavior` is not one of the attributes above, but it is the other platform knob. It exists from API 33 (absent on API 31 and 32, present on 33 and 37.1). Values are `default` (0) and `icon_preferred` (1). The compat library does not implement it. Leave it unset unless API 33+ is dropping the icon.

Animated icon on older APIs: put the still drawable in `res/drawable` and the animated vector in `res/drawable-v31`. Below API 31 the launch icon does not animate. The API 21–22 "icon appears only after start" note does not apply at minSdk 26.

## 3. installSplashScreen call order

Documented order, which matches this activity:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent { /* existing SkimApp */ }
}
```

`setKeepOnScreenCondition` and `setOnExitAnimationListener`, if used, go after `super.onCreate()` and before `setContent`. The views guide sample is that shape. The official [edge-to-edge codelab](https://developer.android.com/codelabs/edge-to-edge) uses the same order: `installSplashScreen()`, then `enableEdgeToEdge()`, then `super.onCreate()`, then `setContent`. [`EdgeToEdge`](https://developer.android.com/reference/androidx/activity/EdgeToEdge) itself says to call `enableEdgeToEdge()` before `super.onCreate()`. Splashscreen inserts one call in front of that. It does not move `enableEdgeToEdge()` to after `super.onCreate()`.

Two library sentences, both in the 1.2.0 sources and on the [API reference](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen) (updated 2026-08-06):

- Usage: call `installSplashScreen` **just before** `super.onCreate()`.
- Method: call it **before** `setContentView` or any other view operation on the root view, "e.g setting flags."

`enableEdgeToEdge()` sets those flags. Calling it first breaks the method contract even though the activity already calls it before `super.onCreate()`.

What `install()` actually does in 1.2.0:

- **API 31+ (`Impl31`):** `activity.setTheme(postSplashScreenTheme)`. It does not read the icon here. The system splash was already built from the manifest theme. Below API 33 it also watches the decor for `SplashScreenView` so an exit-animation listener can restore `setDecorFitsSystemWindows`, which the platform splash otherwise forces off (content jumps under the status bar, then jumps back).
- **API 26–30 (`Impl`):** read background and icon from the current theme, then `setTheme(postSplashScreenTheme)`. The splash the user sees is the theme `windowBackground` (`compat_splash_screen*.xml`), which the system shows as soon as the window exists. `setTheme` must happen before content inflation or that background and the splash system-bar colors stay on the activity.

### If the call is late

| When `installSplashScreen()` runs | What goes wrong |
|---|---|
| After `enableEdgeToEdge()`, before `super.onCreate()` | `setTheme(postSplash)` runs after edge-to-edge has already read the splash theme. On API 31+ that theme sets `enforceNavigationBarContrast=true` (1.2.0 `values-v31`). 1.2.0 no longer copies contrast back onto the activity during exit, but it does not re-run `enableEdgeToEdge()`. System-bar colors and the 3-button scrim can stay on the splash theme's values. The codelab and the method contract both put install first. |
| After `super.onCreate()`, before `setContent` | `setTheme` still affects the Compose hierarchy, because `setContent` has not inflated yet. Anything `super.onCreate()` or an earlier `enableEdgeToEdge()` already read from the splash theme is not replayed. The usage text still says before `super.onCreate()`. `ComponentActivity` is less fragile than `AppCompatActivity` here (no AppCompat decor install in `super.onCreate()`), but this activity also calls `enableEdgeToEdge()` before `super.onCreate()`, so the late-install case is the row above. |
| After `setContent` | `setTheme` does not re-inflate. The composition was created under the splash theme: splash `windowBackground`, transparent system bars, and the v31 contrast flag. On API 31+ the system splash can still appear, because the platform shows it from the manifest theme before `onCreate`. You lose the theme swap, and a keep-on-screen listener attached after the first draw is too late to hold the splash. |

Known bugs tied to this, from the [1.2.0 release notes](https://developer.android.com/jetpack/androidx/releases/core#core-splashscreen-1.2.0) and the 1.2.0 source, not from blog posts:

- **1.0.1, fixed in 1.2.0:** splash theme corrupts activity navigation-bar contrast; API 33+ flicker when the splash is removed; API 33+ overwrites the activity theme after the splash view arrives. Staying on 1.0.1 reopens all three. The API 33 overwrite is the one that makes `postSplashScreenTheme` look like it "did not apply" even when install order was right.
- **API 31–32 exit animation:** `SplashScreenView` sets `setDecorFitsSystemWindows(false)` and the content jumps. 1.2.0 records the previous value and puts it back. Skipping `installSplashScreen()` loses that. This app does not need a custom exit animation; the default removal is enough.
- **Calling `enableEdgeToEdge()` first** is not a numbered bug in the release notes. It is the combination of the method contract ("before setting flags"), the v31 theme setting `enforceNavigationBarContrast=true`, and the codelab order. Community reports describe the same symptom (splash system bars stuck). Treat those as lower-credibility confirmation of the contract, not as a separate platform bug.

The splash shows on cold start (process not running) and warm start (process running, activity not created). It never shows on a hot start. `configChanges` on this activity avoids recreation for rotation, dark mode, and font scale, so those are hot paths and will not show the splash again. That is what you want.

## 4. setKeepOnScreenCondition

The splash goes away when the app draws its first frame. `setKeepOnScreenCondition` is the only supported way to hold it, and only for a **small local** read such as settings from disk.

Contract, from the API reference and the 1.2.0 KDoc:

- The splash stays until `shouldKeepOnScreen()` returns false.
- The lambda runs on the main thread before every draw request. It must be fast. A slow lambda blocks the UI and can ANR.
- Implementation: an `OnPreDrawListener` on `android.R.id.content` returns false while the condition is true, so the activity is not drawn. There is **no timeout** in the library.

If the condition stays true:

- The splash remains for as long as it returns true. The activity is resumed underneath and is not visible. A fast lambda that always returns true does not by itself ANR. The user sees a frozen splash and cannot use the app.
- The first frame never commits, so anything waiting on that frame waits with it.
- The migration guide's `setKeepOnScreenCondition { true }` is only for a **routing activity that immediately starts the next activity and finishes**. It is not a pattern for the main activity.

Guidance against using it as a loading screen, from the [migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) (updated 2026-10-28):

- Local data, small amount: you may hold the first frame.
- Network or other indeterminate work: dismiss the splash and show placeholders. Cache what you can for the next launch.
- Do not keep a dedicated splash activity. The icon animation should stay under 1000 ms and must still look acceptable if the app is ready sooner and the animation is skipped. That 1000 ms cap is the **animation**, not a license to hold the splash.

`windowSplashScreenAnimationDuration` does not extend the splash. Neither does a long animated vector, except that the platform may still be playing it when the first frame is ready, and the exit can cut it.

For this browser, do not hold the splash until a WebView page commits. That is an indeterminate network load. The migration guide says to dismiss and show the app's own placeholder.

## 5. Which theme, and what postSplashScreenTheme restores

The [API reference](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen) allows the splash theme on the `<application>` **or** on the starting `<activity>`. The migration guide's step is narrower: replace the **starting activity** theme, and set `postSplashScreenTheme` to "the theme that the Activity must use."

**Rank: activity theme.** Leave `android:theme` on `<application>` as `@style/Theme.SkimBrowser`. Set the splash theme on `MainActivity` only.

Reason: `installSplashScreen()` only swaps the theme of the activity that calls it. An application-level splash theme is the process default for every context that does not set its own theme. This app has one activity, so both placements can be made to look the same on the launcher path, but the application theme is also the pre-first-frame window theme the code comment in `themes.xml` describes. Keeping that as `Theme.SkimBrowser` and limiting the splash theme to the activity matches the migration step and avoids the splash theme as the process default.

`postSplashScreenTheme` is applied in `setPostSplashScreenTheme`: if the attr resolves to a non-zero style, `activity.setTheme(thatStyle)`. That is the theme for content inflation and for window attributes read after the call. It does not edit the manifest, and it does not rebuild the system splash already shown on API 31+ (that splash used the manifest theme). It replaces the splash theme so `windowBackground`, status bar, and navigation bar come from `Theme.SkimBrowser` (`@color/skim_window_light` / `@color/skim_window_dark`) instead of the splash window background and the splash theme's transparent system bars.

If you leave the library default (`?android:attr/theme`), `setTheme` is handed the splash theme and the activity keeps it. Set:

```xml
<item name="postSplashScreenTheme">@style/Theme.SkimBrowser</item>
```

The KDoc's phrase "the application's theme" means that style, not "put the splash theme on the application tag."

Alternative the API allows: skip `installSplashScreen()` and call `setTheme(R.style.Theme.SkimBrowser)` yourself before `super.onCreate()`. You then have no `SplashScreen` instance, so no keep-on-screen condition and no exit listener, and you lose the API 31–32 decor-fits fix. Not worth it here.

## 6. Icon canvas, mask, and a 108 dp foreground

The splash icon uses the adaptive-icon rule: a circle whose diameter is **2/3** of the icon canvas. Everything outside that circle is masked. The 1.2.0 source constant is `MASK_FACTOR = 2f / 3`. The views guide and the API reference agree on the sizes:

| | Canvas you author | Visible circle |
|---|---|---|
| No icon background (`Theme.SplashScreen`) | 288 × 288 dp | 192 dp |
| With icon background (`Theme.SplashScreen.IconBackground`) | 240 × 240 dp | 160 dp |
| Branding image, if used | 200 × 80 dp | bottom of the screen, API 31+ only |

The library dimens match the canvas: `splashscreen_icon_size_no_background` = 288 dp, `splashscreen_icon_size_with_background` = 240 dp. `MaskedDrawable` clips to `iconSize * 2/3`, so 192 dp or 160 dp. The pre-31 window background fakes that mask with a stroked oval. For the 288 dp icon the hole is exact: oval 410 dp, stroke 109 dp, inner diameter `410 - 2×109 = 192`. For the 240 dp icon the same formula is `342 - 2×92 = 158` dp, 2 dp under the documented 160 dp. Design to 160 dp. The 158 dp hole is only the static layer-list on API 26–30.

An animated vector, if you use one, is specified at 4× the adaptive icon: 432 dp canvas, inner 288 dp visible (4 × 72). Duration at most 1000 ms.

Platform on-screen size, from SDK 31 and SDK 37.1 `dimens.xml`: `starting_surface_icon_size` is **160 dp**. AOSP `SplashscreenContentDrawer` multiplies by `NO_BACKGROUND_SCALE = 192f / 160` when there is no separate icon background, which lands on the **192 dp** circle. Those are the on-screen circles, not a second canvas. The 288 dp and 240 dp figures are the asset size whose inner 2/3 becomes that circle.

### A 108 dp adaptive foreground with art in the center 66 dp

Adaptive-icon layers are 108 × 108 dp. The inner 72 × 72 dp is the masked viewport. The outer 18 dp on each side is parallax padding. The safe zone that a shaped mask never clips is a **66 dp** circle ([adaptive icon guide](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [AdaptiveIconDrawable](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable)). `AdaptiveIconDrawable` uses `SAFEZONE_SCALE = 66f/72f` and an extra inset of 1/4.

The splash screen then masks **one third of the foreground** (views guide: "As with adaptive icons, one-third of the foreground is masked"). Applied to a 108 dp foreground, the visible diameter is `108 × 2/3 = 72` dp. That is the same 72 dp viewport, scaled up to the splash circle (192 dp without a disc, 160 dp with one).

What is clipped: the outer **18 dp** on each side of the 108 dp canvas, plus any of the 72 dp square's corners that fall outside the circle. Those pixels are the adaptive padding, not the logo.

What is not clipped: artwork that already sits in the center **66 dp** circle. It is 3 dp inside the 72 dp mask on every side (`(72 - 66) / 2`). On screen, without an icon disc, that 66 dp becomes `66 / 72 × 192 = 176` dp inside the 192 dp circle, with an 8 dp empty ring. With a disc, it becomes `66 / 72 × 160 ≈ 147` dp inside the 160 dp circle. The logo looks slightly small. It is not cut off.

Do not pass the `<adaptive-icon>` XML as `windowSplashScreenAnimatedIcon` on API 26–30. The 1.2.0 KDoc states the known bug: below API 31 an adaptive icon "will be cropped and scaled." The compat path draws `AdaptiveIconDrawable` (center 72 dp of the 108 dp layer blown up to the 288 dp slot) and then applies the 192 dp circle on top. The visible slice of the original layer is 48 dp across, so **9 dp of the 66 dp artwork is cut off on each side**. The documented workaround is to set `windowSplashScreenAnimatedIcon` to the **foreground** drawable and `windowSplashScreenIconBackgroundColor` to the adaptive background, using `Theme.SplashScreen.IconBackground`.

On API 31+, if you do not set `windowSplashScreenAnimatedIcon` at all, the platform may take the launcher adaptive icon, and it sometimes draws only the foreground and enlarges it by 192/160 when the opaque foreground area is below `(72/108)²`. That path is for the default launcher icon, not for an icon you supply. Set the splash drawable explicitly so you are not on that heuristic.

Best asset for this app: one vector (or a static vector plus `drawable-v31` AVD) authored at **288 × 288 dp** with the mark inside the **192 dp** circle, parent `Theme.SplashScreen`, no branding image. Reusing the launcher foreground is acceptable only if a slightly inset mark is good enough. It will not match a purpose-drawn splash icon.

## 7. Fit for this app

Ranked:

1. **`core-splashscreen:1.2.0`**, activity theme parent `Theme.SplashScreen`, `postSplashScreenTheme=@style/Theme.SkimBrowser`, unprefixed background and icon attributes, icon drawn for the 288/192 spec. Call `installSplashScreen()` then the existing `enableEdgeToEdge()` then `super.onCreate()` then `setContent`. No keep-on-screen condition. This matches the guide, the 1.2.0 source, the codelab, and the current `MainActivity` shape.
2. **Same, plus `Theme.SplashScreen.IconBackground`**, only if the mark needs a contrasting disc. Then set `windowSplashScreenIconBackgroundColor` and design to 240/160.
3. **Stay on 1.0.1 or call `installSplashScreen()` after `enableEdgeToEdge()`.** Reject. 1.0.1 still has the contrast leak and the API 33 theme overwrite. Calling install second violates the "before setting flags" contract on an app that already opts into edge-to-edge and targets SDK 37, where the 3-button nav scrim is enforced unless contrast is turned off.

Do not put the splash theme on `<application>`. Do not hold the splash for the WebView. Do not add a branding image. Do not add a second splash activity.

Trade-offs of the recommended path: a transitive `appcompat-resources` plus vectordrawable (not AppCompat). The splash is a solid color plus one icon, so it will not match a full Compose first frame pixel for pixel; `postSplashScreenTheme` exists so the window color after dismiss is `skim_window_light` / `skim_window_dark`. Hot starts show no splash. API 26–30 show a still icon. API 31+ can animate.

## 8. Limitations

- Google Maven and the AndroidX pages were read on 2026-10-30. A release published after the Maven `lastUpdated` of 2025-11-05 18:02:34 UTC would not be in that metadata. None was listed, and the Core page showed no splashscreen alpha.
- The migration guide's Gradle snippet is stale (1.0.0-beta02). The views guide's "API level 32" parenthetical for animation duration disagrees with SDK attrs (present on 31, deprecated from 33). This report follows the SDK and the API reference where they disagree.
- AOSP `SplashscreenContentDrawer` was read from a public git snapshot, not from an `android-37.1` framework tag. The 160 dp and 192/160 scale match SDK 31 and SDK 37.1 dimens, so the on-screen circle sizes are current. The older `SplashscreenIconDrawableFactory` that wraps a plain drawable in `AdaptiveIconDrawable` was not re-verified against current `main` (googlesource returned 403 for the raw file). The clip math in section 6 uses the published 2/3 rule and the 1.2.0 compat code, which is the behavior on this app's API 26–30 devices.
- No device was flashed. Order and mask behavior are from docs, the 1.2.0 sources jar, and the 1.2.0 AAR.

## 9. Sources

- Google Maven metadata: `https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/maven-metadata.xml` (`latest`/`release` = 1.2.0, `lastUpdated` 20251105180234). Group index: `https://dl.google.com/dl/android/maven2/androidx/core/group-index.xml`.
- POM, module metadata (`org.gradle.status=release`), sources jar, and AAR of `androidx.core:core-splashscreen:1.2.0`, downloaded 2026-10-30 from `dl.google.com`.
- [Core release notes](https://developer.android.com/jetpack/androidx/releases/core) and [stable channel](https://developer.android.com/jetpack/androidx/versions/stable-channel), 5 November 2025 entry.
- [Splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen) (updated 2026-10-29) and [migration](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) (updated 2026-10-28).
- [SplashScreen API reference](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen) (updated 2026-08-06) and [KeepOnScreenCondition](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen.KeepOnScreenCondition).
- [android.R.attr](https://developer.android.com/reference/android/R.attr) plus SDK `attrs.xml` / `dimens.xml` for API 30 (attrs absent), 31, 32, 33, and 37.1.
- [Android 13 features](https://developer.android.com/about/versions/13/features) (animation duration inferred, `windowSplashScreenBehavior`).
- [Edge-to-edge codelab](https://developer.android.com/codelabs/edge-to-edge) and [EdgeToEdge](https://developer.android.com/reference/androidx/activity/EdgeToEdge).
- [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) and [AdaptiveIconDrawable](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable).
- AOSP `SplashscreenContentDrawer` (public snapshot `769f04c8f03a`): `NO_BACKGROUND_SCALE = 192f/160`, foreground-only path. Secondary for the default-launcher heuristic only.
