# Splash icon mask for the Sunny launcher foreground

**Date:** 2026-10-30
**Scope:** Android 12+ splash icon masking (`windowSplashScreenAnimatedIcon`, `windowSplashScreenIconBackgroundColor`) and `androidx.core:core-splashscreen` on API 26–30. Measured against this app’s `ic_launcher_foreground` (108×108 viewport, even-odd `#0B57D0` path). No device capture in this pass.

## Outcome

Add a dedicated static splash drawable. Do not point the splash icon at `ic_launcher_foreground` or at `@mipmap/ic_launcher`.

The splash mask keeps the center two-thirds of whatever drawable you give it. On this foreground that circle is 72 units across, and the scallops peak at radius 33.18, so they are not clipped. The unused 2.8 units is transparent, not a painted white ring. Reuse still fails the mark you want: the sparkle “whites” are even-odd holes, so they take the color behind them, and the shape sits about 7.5 dp inside the 192 dp no-background circle.

Draw the same silhouette in a vector whose diameter is that inner two-thirds, with the sparkle subpaths filled `#FFFFFF`. Leave `windowSplashScreenIconBackgroundColor` unset. Put the window color in `values` and `values-night`. The system splash cannot read the DataStore theme.

## What this icon actually measures

`app/src/main/res/drawable/ic_launcher_foreground.xml` is one even-odd path, fill `#0B57D0` (the same blue as `SkimLightColors.primary`). There is no white rect in the foreground. White comes only from `ic_launcher_background.xml`, a full 108×108 `#FFFFFF` square behind the adaptive icon.

Path bounds, center (54, 54), 223 vertices:

| Measure | Value |
|---|---|
| X span | 21.204 … 86.796 (65.592) |
| Y span | 21.000 … 87.000 (66.000) |
| Max radius | 33.181 (diameter 66.361) |
| Vertices outside the 66 dp safe circle (r = 33) | 8, by at most 0.181 |
| Vertices outside the circular mask (r = 36) | 0 |
| Clearance to r = 36 | 2.819 |

The in-app `SunnyMark` is a different drawing: `MaterialShapes.Sunny` clipped and filled, with `ic_auto_awesome_filled` centered on top. The launcher asset is the silhouette to reuse. The splash drawable still has to paint those sparkles white, because the launcher only punches them out.

## 1. Default icon on API 31+, and API 26–30 without the compat library

**API 31+ with no animated icon uses the launcher icon.** The platform reads `android:windowSplashScreenAnimatedIcon` and, when that drawable is null, loads the activity icon through `IconProvider` and runs it through adaptive-icon processing. The public docs say the launcher icon is the default. Cold start and warm start show that system splash; a hot start does not. From API 33, a start that is not from the launcher can omit the icon unless the theme sets `android:windowSplashScreenBehavior` to `icon_preferred`.

If the icon is this app’s adaptive icon and `windowSplashScreenIconBackgroundColor` is unset, current AOSP draws the foreground alone. The background is a plain white (`#FFFFFF`), which is grayscale and not complex, and the blue foreground contrasts with both the light window (`#FFFFFF`) and the dark window (`#202124`). Those are the conditions under which `SplashscreenContentDrawer` keeps only the foreground. It then scales the icon view by `192/160` when the foreground’s opaque area is under `(72×72)/(108×108)` ≈ 0.444. This shape’s opaque area is well under that (a ~66 dp blob on a 108 dp canvas, minus the holes).

**API 26–30 with no compat library shows no icon.** The splash-screen API starts at API 31. The starting window is `android:windowBackground`. This app’s `Theme.SkimBrowser` sets that to `@color/skim_window_light` (`#FFFFFF`) or, in `values-night`, `@color/skim_window_dark` (`#202124`). The launcher icon stays on the home screen.

`androidx.core:core-splashscreen` is what paints an icon on API 23–30 (so on this minSdk 26). Its pre-31 `Theme.SplashScreen` sets `android:windowBackground` to a layer-list, which the system shows before any app code. That theme’s default icon is `@android:drawable/sym_def_app_icon`, not the launcher icon. Override `windowSplashScreenAnimatedIcon`. On API 31+ the library’s `values-v31` theme only forwards `?windowSplashScreenAnimatedIcon` to the platform attribute; an unset value falls through to the launcher-icon path above. Call `installSplashScreen()` before `super.onCreate()` so the compat window background is removed and `postSplashScreenTheme` is applied. Animated vectors do not run on the launch screen below API 31.

## 2. Pointing the splash icon at this 108 dp foreground

**The center-two-thirds circle does not clip the scallops, and it does not paint a large white ring.**

The mask shows the center two-thirds of the drawable and throws away the outer sixth on each side. For a 108 dp vector that visible circle is 72 dp in diameter (radius 36). The scallops peak at radius 33.18, so they sit 2.82 viewport units inside the circle.

Two implementations do that same crop:

- **Platform (API 31+).** A plain vector is wrapped in `AdaptiveForegroundDrawable`. The foreground is drawn at 1.5× the mask (`EXTRA_INSET_PERCENTAGE = 1/4`, so the visible fraction is `1/1.5 = 2/3`) and clipped with `config_icon_mask`. With no separate icon background, the mask is drawn at `starting_surface_icon_size` (160 dp) times `NO_BACKGROUND_SCALE` (`192/160`), which is a **192 dp** circle. The shape’s 66.361 viewport diameter becomes `66.361/72 × 192 ≈ 177 dp`. The empty band is about **7.5 dp** per side, and it is transparent.
- **Compat library (API 26–30).** `Theme.SplashScreen` draws the icon at 288×288 dp, then covers the corners with an oval stroke in the window color (`410 dp` oval, `109 dp` stroke). The inner opening is `410 − 2×109 = 192 dp`, the same two-thirds. After `installSplashScreen()`, `MaskedDrawable` clips to `iconSize × 2/3` as well (`288 × 2/3 = 192`).

On `#FFFFFF` that 7.5 dp band is the window showing through. There is no second circle and no white halo. The mark is slightly smaller than the largest circle the spec allows.

A large white disk appears only if a **different** color is painted behind the transparent pixels: the adaptive-icon white background (if you pass `@mipmap/ic_launcher` and the platform keeps the whole adaptive icon), or `windowSplashScreenIconBackgroundColor` when it is not the window color. The scallops are still inside the circle; the disk is the background showing through the padding and the holes.

OEM masks use `config_icon_mask`, which can be a squircle rather than a circle. The 66 dp safe zone is what every adaptive mask is specified to contain. Eight vertices poke 0.181 units past radius 33, on the cardinal peaks. A circular mask still clears them. A squircle is widest at those cardinals, so a clip there would be a fraction of a dp. That was not checked on hardware.

## 3. Drawable size for a static splash icon with no second circle

Use the **no-background** spec, and make the artwork fill the unmasked circle.

| | No icon background (use this) | With icon background (do not use) |
|---|---|---|
| Image you supply | 288×288 dp | 240×240 dp |
| Visible circle | 192 dp (center 2/3) | 160 dp (center 2/3) |
| On-screen circle (AOSP) | 192 dp | 160 dp |

The docs say the actual pixel size does not matter for a vector; the ratio does. Anything outside the inner two-thirds is masked. A 108×108 viewport is fine if the silhouette diameter is **72** (the whole visible circle), not 66 (the adaptive safe zone).

Scale this path about (54, 54) by `72 / 66.361 ≈ 1.085` so the peaks meet the mask. Inset by about 1 viewport unit if you want antialias clearance (`diameter ≈ 70`, scale ≈ 1.055). Leave the outside of the shape transparent. Do not add a background rect.

Split the even-odd path. Fill the Sunny silhouette `#0B57D0`. Fill the sparkle subpaths `#FFFFFF` and draw them on top. Holes would show `#202124` on the dark splash, which is not the in-app mark.

Parent `Theme.SplashScreen`, not `Theme.SplashScreen.IconBackground`. Set `windowSplashScreenAnimatedIcon` to this drawable. Do not set `windowSplashScreenIconBackgroundColor`.

## 4. `windowSplashScreenIconBackgroundColor` against a white window

The attribute draws a circle **behind** the icon, in the same masked area as the icon, and only when that color is opaque and **different** from the splash window color.

AOSP `SplashscreenIconDrawableFactory` sets `drawBackground` only when `backgroundColor != TRANSPARENT && backgroundColor != themeColor`. When the colors match, or the icon background is unset, it draws no circle and enlarges the icon from 160 dp to 192 dp. White on this app’s light window (`#FFFFFF`) draws nothing. It does not fight a white `windowSplashScreenBackground`.

A color that differs from the window does draw the disk. Transparent parts of the foreground — the padding around a 66 dp shape, and even-odd holes — show that disk. White icon background on the dark window (`#202124`) is a white circle on dark gray, which is the second circle to avoid. Sparkle holes would be white, but so would the ring around the shape.

The compat `Theme.SplashScreen.IconBackground` always selects the smaller slot (icon drawn at 240 dp, mask opening `342 − 2×92 = 158 dp`) and always layers `@drawable/icon_background` under the icon. Same-colored white on white is still invisible, but the mark is the 160 dp-class size instead of 192 dp. That is a reason to leave the attribute unset even for the light theme.

Below API 31, an adaptive-icon XML in `windowSplashScreenAnimatedIcon` is cropped and scaled. The library’s own workaround is to point the icon at the **foreground** and the icon background color at the adaptive **background**. Doing that here would put white behind the foreground and produce the disk above whenever the window is not white. Skip that workaround; use the dedicated drawable instead.

## 5. Light and dark: DataStore versus system night mode

**The system splash follows `uiMode` (`values` / `values-night`), which is the system night mode unless a previous run persisted an app night mode. It does not read DataStore.**

The starting window is drawn before `Application` or `Activity` code runs, from the manifest theme and the configuration the system already has. This app stores `ThemeSetting` (`System`, `Light`, `Dark`) in DataStore and applies it only inside Compose `SkimTheme`, which picks `SkimLightColors` or `SkimDarkColors` from `isSystemInDarkTheme()` at composition time. Nothing in the app calls `UiModeManager.setApplicationNightMode` or `AppCompatDelegate.setDefaultNightMode`. The activity also lists `uiMode` in `android:configChanges`, so a night-mode change does not recreate it.

What actually reaches the splash:

- **API 31+.** `UiModeManager.setApplicationNightMode` persists `MODE_NIGHT_NO`, `MODE_NIGHT_YES`, or follow-system for this package. The next process start, including the system splash, uses that `uiMode`. The call has to happen when the user changes the setting. Reading DataStore in `Application.onCreate` is too late for the starting window already on screen; it only fixes the following cold start. Now in Android hit this: the splash stayed on the system theme until night mode was persisted for the app.
- **API 26–30.** `setApplicationNightMode` does not exist. `AppCompatDelegate.setDefaultNightMode` runs after process start and does not change the starting window the system already chose. This app does not use AppCompat. The compat splash layer-list is inflated with the system `uiMode`, so it follows `values` / `values-night` only.
- **First launch.** Nothing has been persisted, and the default `ThemeSetting` is `System`, so system night mode is the right splash until the user picks Light or Dark.

A splash theme still needs both resource buckets (or a `DayNight` parent): light window `#FFFFFF`, dark window `#202124`, same as `Theme.SkimBrowser` today. The icon drawable itself should not change with night mode if the sparkles are painted white and the shape is painted `#0B57D0`.

## Recommendation

| | Reuse `ic_launcher_foreground` | Dedicated splash drawable |
|---|---|---|
| Scallops vs 72-unit mask | Clear by 2.82 units | Clear, by construction |
| Visible ring on a white window | None (transparent margin ~7.5 dp) | None |
| Shape size in the 192 dp circle | ~177 dp | ~192 dp (or ~187 dp with 1 unit of clearance) |
| Sparkles on `#FFFFFF` | White, through holes | White, painted |
| Sparkles on `#202124` | Dark gray holes | White |
| Second circle | None, if icon background color is unset | None |
| Launcher icon coupling | One asset, but the 66 dp safe-zone padding is wrong for this mask | Second asset, same silhouette |

**Use the dedicated drawable.** Ranked above reuse because dark-mode sparkles and the safe-zone inset are properties of this file, not of the mask. Reuse is acceptable only for a light-only white splash where a 177 dp mark is close enough. Pointing the splash at `@mipmap/ic_launcher` is worse: the white adaptive background becomes a disk whenever the platform keeps the full adaptive icon, and the compat library mis-scales adaptive XML below API 31.

Splash theme, both API ranges: parent `Theme.SplashScreen`, set the animated icon to the new drawable, set the background color per `values` / `values-night`, leave icon background color unset. Persist Light/Dark with `UiModeManager.setApplicationNightMode` on API 31+ when the setting changes if the splash should follow DataStore on the next cold start. API 26–30 will keep following the system night mode.

## Trade-offs and risk

`core-splashscreen` is the platform backport, maintained in AndroidX (API reference touched 2026-07-01). The 288/192 and 240/160 spec has been stable since the Android 12 splash docs. Abandonment risk is low. The sharp edge is OEM `config_icon_mask` and the API 33 entry-point behavior, not the library.

Fit for this project: minSdk 26 is inside the compat icon range (the library only drops the icon until process start on API 21–22). Themes are already split light/dark as solid window colors, and there is no AppCompat `DayNight` theme to lean on. The Compose theme and the splash theme will diverge whenever DataStore says Light or Dark and `uiMode` says the opposite, until `setApplicationNightMode` has been persisted.

## Sources

1. Splash screens, dimensions and elements. Android Developers. The launcher icon is the default; one-third of the foreground is masked; 240×240 with a 160 dp circle, or 288×288 with a 192 dp circle; `windowSplashScreenBehavior`. https://developer.android.com/develop/ui/views/launch/splash-screen
2. Migrate your splash screen to Android 12. The system splash starts in Android 12 and is built from the launcher icon and a single-color `windowBackground`. Compat library is what makes pre-31 look the same. https://developer.android.com/develop/ui/views/launch/splash-screen/migrate
3. `androidx.core.splashscreen.SplashScreen` reference. Themes, the two-thirds rule, adaptive-icon workaround below API 31, icon not shown on API 21–22 until the process starts. Updated 2026-07-01. https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen
4. AOSP `SplashscreenContentDrawer` (main). Null animated icon → `IconProvider`; `NO_BACKGROUND_SCALE = 192f/160`; `ENLARGE_FOREGROUND_ICON_THRESHOLD = (72²)/(108²)`; foreground-only adaptive path. Icon sizes from `core/res/res/values/dimens.xml`: `starting_surface_icon_size` 160 dp, `starting_surface_default_icon_size` 108 dp. https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java
5. AOSP `SplashscreenIconDrawableFactory` (main). Icon-background circle only when its color differs from the window color. `AdaptiveForegroundDrawable` uses a 2/3 viewport. https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenIconDrawableFactory.java
6. AndroidX `core-splashscreen` sources (androidx-main): `values/styles.xml`, `values-v31/styles.xml`, `values/dimens.xml`, `drawable/compat_splash_screen_no_icon_background.xml`, `drawable/compat_splash_screen.xml`, `MaskedDrawable.kt`. https://github.com/androidx/androidx/tree/androidx-main/core/core-splashscreen
7. `AdaptiveIconDrawable`. 108 dp layers, inner 72 dp masked, outer 18 dp inset. https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable
8. Implement dark theme. API 31+ `UiModeManager.setApplicationNightMode` is what makes the splash match an in-app choice; API 30 and below use `AppCompatDelegate`. https://developer.android.com/develop/ui/views/theming/darktheme
9. `UiModeManager.setApplicationNightMode`. The mode is persisted for the package until changed, data cleared, or uninstall. https://developer.android.com/reference/android/app/UiModeManager#setApplicationNightMode(int)
10. Now in Android issue 2012. System splash is shown before `Application.onCreate` finishes, so a DataStore theme is ignored until night mode is persisted. https://github.com/android/nowinandroid/issues/2012
11. Android 13 splash behavior. `windowSplashScreenBehavior` / `icon_preferred`. https://developer.android.com/about/versions/13/features

App files measured or read: `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `mipmap-anydpi/ic_launcher.xml`, `values/themes.xml`, `values-night/themes.xml`, `values/colors.xml`, `ui/theme/Theme.kt`, `ui/components/ShapeTiles.kt`.

## Limitations

- No install on a device or emulator, so the 7.5 dp margin and the squircle graze are from the spec and the path math, not a screenshot.
- Stroke alignment for the compat corner-cover is inferred from the dimens hitting 192 and 158, which matches the published circles. `GradientDrawable`’s stroke inset was not re-read line by line.
- `config_icon_mask` differs by OEM. The circular case is the one the public spec describes.
- Android 12 point-release splash bugs (icon skipped on some starts) are real; the crop ratio above is the current AOSP main behavior plus the stable public spec, not a re-test of every API 31 build.
