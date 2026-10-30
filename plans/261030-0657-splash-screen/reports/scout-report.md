# Splash-screen scout

There is no splash implementation in source, resources, Gradle, or tests. The pre-first-frame window is `Theme.SkimBrowser`'s `windowBackground` (white / `#202124`). Compose then draws an empty `Surface` until `SkimAppViewModel` sets `started`, which can take up to 8 seconds. `MainActivity` calls `enableEdgeToEdge()` before `super.onCreate()`, and `SystemBarsEffect` calls it again as soon as that empty window is composed.

## 1. `MainActivity.onCreate` order

`app/src/main/java/com/dantech/skimbrowser/MainActivity.kt` is the only activity (`MainActivity.kt:34-35`, `AndroidManifest.xml:58-59`). `onCreate` runs in this order:

1. `enableEdgeToEdge()` with no bar styles (`MainActivity.kt:70-71`).
2. `super.onCreate(savedInstanceState)` (`MainActivity.kt:72`).
3. Log, attach the WebView pool, apply a pending cache clear (`MainActivity.kt:73-75`).
4. `window.decorView.post { incognito.purgeStale() }` — the comment says this waits until the first frame is drawn (`MainActivity.kt:76-77`).
5. If `savedInstanceState == null`, queue the launching intent's link (`MainActivity.kt:78-79`).
6. `setContent` hosts `SkimApp` (`MainActivity.kt:82-94`).

`enableEdgeToEdge` is already before `super.onCreate`. `setContent` is after it. A splash install has to be placed in that same prefix: the library requires the call before `super.onCreate` / `setContentView`, and this activity already uses that prefix for edge-to-edge.

`SkimApplication.onCreate` only restores tabs and purges old shares (`SkimApplication.kt:13-20`). It does not touch the window theme.

## 2. Application and activity theme

`android:theme="@style/Theme.SkimBrowser"` is on `<application>` only (`AndroidManifest.xml:42-52`).

`<activity android:name=".MainActivity">` has no `android:theme` (`AndroidManifest.xml:58-63`). It inherits the application theme. It is `exported`, `singleTask`, and lists `uiMode` in `configChanges` (`AndroidManifest.xml:60-62`), so a night-mode change does not recreate the activity. The XML night theme applies at process start, not when the in-app theme changes later.

Launcher icon attributes on the application, not the activity: `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` (`AndroidManifest.xml:47-50`).

Debug and instrumented manifests add activities with no theme of their own, so they also inherit `Theme.SkimBrowser`:

- `HiltRouteActivity` (`app/src/debug/AndroidManifest.xml:3-6`, `HiltRouteActivity.kt:10`).
- `BrowserHostActivity` (`app/src/androidTest/AndroidManifest.xml:3-6`).

Putting a splash parent on the application theme would change those test windows. The activity element is the place to hang a splash theme.

## 3. `Theme.SkimBrowser` and window colors

Day (`app/src/main/res/values/themes.xml:9-13`):

- Comment: Compose draws every screen edge to edge; the window theme only sets the background shown before the first frame (`themes.xml:9`).
- Parent `android:Theme.Material.Light.NoActionBar` (`themes.xml:10`).
- `android:windowBackground` = `@color/skim_window_light` (`themes.xml:11`).
- `colorControlNormal` is declared because drawable icons tint with that AppCompat attribute and the app has no AppCompat (`themes.xml:2-7`, `themes.xml:12`).

Night (`app/src/main/res/values-night/themes.xml:2-5`):

- Parent `android:Theme.Material.NoActionBar` (`themes.xml:2` in `values-night`).
- `android:windowBackground` = `@color/skim_window_dark` (`values-night/themes.xml:3`).
- Same `colorControlNormal` item (`values-night/themes.xml:4`).

Colors live only in `app/src/main/res/values/colors.xml`. There is no `values-night/colors.xml`.

- `skim_window_light` `#FFFFFF` (`colors.xml:3-4`). Comment says it matches `SkimLightColors.surface` (`colors.xml:3`). That surface is `Color(0xFFFFFFFF)` (`Color.kt:25`).
- `skim_window_dark` `#202124` (`colors.xml:5`). `SkimDarkColors.surface` is `Color(0xFF202124)` (`Color.kt:48-49`, `Color.kt:65`).

The XML background follows system night mode. The in-app setting does not. `SkimAppUiState` starts as `ThemeSetting.System` (`SkimAppViewModel.kt:42`, `SkimAppViewModel.kt:91-95`), and the settings flow emits `System` before the stored theme (`SkimAppViewModel.kt:92`). `ThemeSetting.isDark` then follows the system unless the setting is Light or Dark, or the surface is incognito (`Theme.kt:19-22`). A splash built on these two themes has the same limit: it cannot show the user's Dark setting while the system is in day mode.

`minSdk` is 26 and `targetSdk` / `compileSdk` are 37 (`app/build.gradle.kts:41-48`). Platform splash attributes apply from API 31; below that the window background above is what the user sees unless a compat library draws one.

## 4. Launcher adaptive icon

`minSdk` 26 means the anydpi adaptive icon is the icon on every supported device.

- `app/src/main/res/mipmap-anydpi/ic_launcher.xml:2-6` and `ic_launcher_round.xml:2-6` use the same layers: background `@drawable/ic_launcher_background`, foreground and monochrome both `@drawable/ic_launcher_foreground`.
- Background is a 108dp white square, fill `#FFFFFF` (`ic_launcher_background.xml:2-8`).
- Foreground is one 108dp vector, viewport 108, `fillType="evenOdd"`, `fillColor="#0B57D0"` (`ic_launcher_foreground.xml:2-8`). `#0B57D0` is light primary (`Color.kt:7-10`).

That foreground is a static drawing of the brand mark, not the Compose Sunny mark.

`SunnyMark` is `MaterialShapes.Sunny` plus `R.drawable.ic_auto_awesome_filled`. Brand mode uses `primary` / `onPrimary`; AI surfaces use `tertiaryContainer` (`ShapeTiles.kt:45-71`). `ic_auto_awesome` and `ic_auto_awesome_filled` are 24dp vectors of three sparkles (`ic_auto_awesome.xml:2-9`, `ic_auto_awesome_filled.xml:2-9`).

`ic_launcher_foreground` does not reference `MaterialShapes`, `SunnyMark`, or `ic_auto_awesome`. Its single even-odd path is a sun polygon centered at (54, 54) plus three inner sparkle subpaths (two small, one large) with the same quadratic sparkle construction as `ic_auto_awesome`. Even-odd fill makes those sparkles holes, so the white background shows through. The result looks like the brand logo (blue Sunny shape, white sparkles). It is not the runtime Sunny mark, and the monochrome layer is this same blue drawable (`ic_launcher.xml:5`).

Density webp fallbacks also exist (`mipmap-mdpi` through `mipmap-xxxhdpi` for `ic_launcher.webp` and `ic_launcher_round.webp`). Adaptive XML wins on API 26+.

The foreground canvas is the adaptive 108dp icon (artwork from about y=21 to y=87). A splash icon mask is smaller than that canvas, so using this drawable unchanged as `windowSplashScreenAnimatedIcon` can clip the sun points. A splash-sized drawable would be a new file, not an edit of the launcher XML.

## 5. Empty window before `state.started`, and `startUp()`

`SkimApp` draws nothing but system bars and an empty surface until startup finishes (`SkimApp.kt:29-30`, `SkimApp.kt:56-60`):

```text
if (!state.started) {
    SystemBarsEffect(...)
    SkimTheme(state.theme) { Surface(Modifier.fillMaxSize()) {} }
    return
}
```

`SkimAppUiState` is `theme` plus `started`, default `ThemeSetting.System` and `started = false` (`SkimAppViewModel.kt:41-42`). The flow is `SharingStarted.Eagerly` with that default (`SkimAppViewModel.kt:91-95`).

`init` launches `startUp()`, then writes the first back-stack entry (Welcome if not onboarded, Browser otherwise, or a reconciled saved stack), then sets `started.value = true` (`SkimAppViewModel.kt:97-107`). The back stack is empty until then (`SkimAppViewModel.kt:75-76`).

`startUp()` (`SkimAppViewModel.kt:132-145`):

- `withTimeoutOrNull(START_TIMEOUT_MS)` on `settings.settings.first().onboardingCompleted`. Null counts as a first launch and is logged (`SkimAppViewModel.kt:133-134`, `SkimAppViewModel.kt:144`).
- Then the same timeout around `tabRestore.restore()`. Timeout is logged; other failures are logged and swallowed; `CancellationException` is rethrown (`SkimAppViewModel.kt:135-143`).
- Returns `onboarded == true` (`SkimAppViewModel.kt:144`).

`START_TIMEOUT_MS` is `8_000L` in the internal companion (`SkimAppViewModel.kt:147-154`). The comment says it is longer than the tab manager's own restore timeout, which normally ends the wait first (`SkimAppViewModel.kt:153`).

A splash that exits on the first frame leaves this empty surface on screen for the rest of that wait. Keeping the splash up means `setKeepOnScreenCondition` tied to `state.started`. The view model is created with `hiltViewModel()` inside `SkimApp` (`SkimApp.kt:48`), not in the activity, so that condition has to be bridged from `SkimApp` (or from content the activity sets) back to the `SplashScreen` installed in `onCreate`.

`MainActivity` also posts `incognito.purgeStale()` to the decor view for the first frame (`MainActivity.kt:76-77`). A splash that withholds the first draw delays that purge.

## 6. `SystemBarsEffect.enableEdgeToEdge` (the second call)

First call: parameterless `enableEdgeToEdge()` in `MainActivity.onCreate` (`MainActivity.kt:71`).

Second call: `SystemBarsEffect` (`SystemBarsEffect.kt:21-29`). `DisposableEffect(activity, dark, configuration)` calls `activity.enableEdgeToEdge` with:

- status bar `SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }`
- navigation bar `SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }`
- scrims `0xE6FFFFFF` and `0x801B1B1B` (`SystemBarsEffect.kt:34-35`)

The comment says `SystemBarStyle.auto` alone follows the system, not the app theme or incognito, and a configuration change reapplies the styles from the first edge-to-edge call, so this effect sets them again (`SystemBarsEffect.kt:13-18`).

`SkimApp` calls it in both branches:

- Empty window, before `started`: `SystemBarsEffect(state.theme.isDark(..., incognito = false))` (`SkimApp.kt:56-58`).
- After start: `SystemBarsEffect(state.theme.isDark(..., incognitoSurface))` (`SkimApp.kt:74`).

So the second `enableEdgeToEdge` runs while the empty window is up, which is the same interval a kept splash would still be showing. `LocalActivity.current as? ComponentActivity` bails out if the composition has no activity (`SystemBarsEffect.kt:22`).

A third, separate call is `WindowCompat.enableEdgeToEdge(window)` on the search overlay's dialog window (`SearchOverlay.kt:358-363`). That is not the activity window.

No test references `SystemBarsEffect` or `enableEdgeToEdge`.

## 7. Existing splash references

Repo search for `splash`, `SplashScreen`, `windowSplashScreen`, and `installSplashScreen` hits one file: `plans/261030-0657-splash-screen/plan.md`. No match under `app/`, `browser/`, Gradle, resources, or tests. `phase-01-start.md` does not contain those words. There is no `androidx.core:core-splashscreen` dependency.

## 8. Tests

No test reads `windowBackground`, `Theme.SkimBrowser`, or a splash. None assert the window theme.

### androidTest that launch `MainActivity`

`MainActivityLifecycleTest` (`app/src/androidTest/java/com/dantech/skimbrowser/MainActivityLifecycleTest.kt`) launches `ActivityScenario.launch(MainActivity::class.java)` in every test (`MainActivityLifecycleTest.kt:139`, `:151`, `:170`, `:175`, `:188`, `:222`, `:241`, `:264`, `:273`, `:295`). Assertions are about the browser lifecycle, not the theme (`MainActivityLifecycleTest.kt:62-68`):

| Test | Asserts |
|---|---|
| `theTabsHaveWebViewsWhileItLivesAndNoneOnceItIsGone` | A tab has a page while the activity lives, and not after `scenario.close()` (`:138-146`). |
| `javaScriptTimersPauseWhileItIsStoppedAndRunAgainOnceItStarts` | JS ticks pause in `CREATED` and resume in `RESUMED` (`:150-164`). |
| `javaScriptTimersRunInTheNextActivityAfterTheLastOneStoppedAndWentAway` | Timers run in a second activity after the first is stopped and closed (`:168-180`). |
| `aNewerInstanceStartedOverAnOlderOneKeepsItsPagesRunningOnceTheOlderOneIsGone` | A `FLAG_ACTIVITY_CLEAR_TASK` instance keeps the page after the older one is destroyed (`:184-212`). |
| `whileStartedItHearsDownloadManagerFinishADownloadAPageStarted` | A started activity receives one `DownloadCompleted` for the test PDF (`:220-233`). |
| `aDownloadThatEndsWhileItIsStoppedIsReportedOnceItStartsAgain` | No event while `CREATED`; the same completion arrives after `RESUMED` (`:237-256`). |
| `filesSharedFromAnEarlierIncognitoSessionAreDeletedOnceItStarts` | The file posted for the first frame is gone after launch (`:260-267`). This is the test coupled to `decorView.post { incognito.purgeStale() }`. |
| `finishingMakesEveryCloseStillWaitingForUndoFinalAndEndsTheIncognitoSession` | `scenario.close()` finalizes Undo and ends incognito (`:271-290`). |
| `beingRecreatedKeepsTheClosesThatCanStillBeUndone` | `scenario.recreate()` keeps the pending close (`:294-306`). |

`ReleaseSmokeTest` uses `ActivityScenarioRule<MainActivity>` (`ReleaseSmokeTest.kt:48-53`). It asserts AI summary text, a missing API key, a refusal card, and that `recreate()` does not send another `/v1/messages` (`ReleaseSmokeTest.kt:67-101`). It waits on visible text with a 30s timeout (`ReleaseSmokeTest.kt:149`). It does not assert startup chrome.

`ExternalLinkTest` launches through `SkimAppRule.launch` (`ExternalLinkTest.kt:36`, `SkimAppRule.kt:131-137`). It asserts Back on an external link's first page backgrounds the activity (`Lifecycle.State.CREATED`), closes that tab with no Undo, and leaves no other page (`ExternalLinkTest.kt:34-45`).

`SkimAppRule` is the helper those device shell tests use. It is not a test class. `launch` defaults to a MAIN/LAUNCHER intent aimed at `MainActivity` (`SkimAppRule.kt:131-137`).

`DeviceCapabilitiesReportTest` launches `ComponentActivity`, not `MainActivity`. `BrowserRig` launches `BrowserHostActivity` (`BrowserRig.kt:83-94`).

### Unit tests

`SkimAppViewModelTest` (`app/src/test/.../ui/shell/SkimAppViewModelTest.kt`) does not launch an activity. Startup assertions:

- `theFirstLaunchOpensOnWelcome` — `started` and back stack `[WelcomeKey]` (`:86-91`).
- `aLaunchAfterOnboardingOpensOnTheBrowser` — `started` and `[BrowserKey]` (`:95-100`).
- `theWindowTakesTheThemeOfTheSettings` — `state.theme` is `ThemeSetting.Dark` when settings say so (`:104-108`). This is view-model state, not the XML window theme.
- `theAppWaitsForTheSavedTabs` — `started` is false and the back stack is empty until restore completes (`:112-123`).
- `settingsThatAreNotReadInTimeCountAsAFirstLaunch` — still not started at `START_TIMEOUT_MS - 1`; after 2ms more, `SkimAppUiState(System, started = true)` and `[WelcomeKey]` (`:127-137`).
- `savedTabsThatAreNotRestoredInTimeStillOpenTheBrowser` — same timeout boundary; then `started` and `[BrowserKey]` when onboarding was already done (`:141-154`).
- `aRestoreThatFailsStillOpensTheBrowser` — an `IOException` from restore still starts on `[BrowserKey]` (`:158-166`).

`SkimAppTest` uses `createAndroidComposeRule<HiltRouteActivity>()`, not `MainActivity` (`SkimAppTest.kt:65`). `showApp` waits until `model.state.value.started` (10s deadline) and asserts `FLAG_SECURE` on the shown window (`SkimAppTest.kt:83-104`, `:107-132`). It does not read the theme.

`SkimThemeTest` asserts Compose scheme colors, including light `surface` `0xFFFFFFFF` and incognito `surface` `0xFF202124` (`SkimThemeTest.kt:40-54`). Those match the window colors. The test never loads `Theme.SkimBrowser`.

`DefaultBrowserTest` builds `ComponentName(context, MainActivity::class.java)` and checks intent filters (`DefaultBrowserTest.kt:31`, `:38-40`). It does not launch the activity or read the theme.

### Startup benchmarks (not theme tests)

`FrameBenchmarks.coldStartup` measures `StartupTimingMetric` around `startActivityAndWait()` (`macrobenchmark/.../FrameBenchmarks.kt:22-30`). A splash kept until `started` lengthens that metric. `StartupProfile.generate` calls `startActivityAndWait()` and then waits up to 15s for the text "Get started" (`baselineprofile/.../StartupProfile.kt:18-22`).

## 9. How `androidx.core` is declared

`gradle/libs.versions.toml`:

- Version `coreKtx = "1.19.1"` (`libs.versions.toml:10`).
- Library `androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }` (`libs.versions.toml:40`).

No `core-splashscreen` version or library. Splash is a separate artifact (`androidx.core:core-splashscreen`), not a transitive of `core-ktx`.

`app/build.gradle.kts:350` is the app's only androidx.core line: `implementation(libs.androidx.core.ktx)`. Nearby lines are Compose, Activity, and other AndroidX artifacts (`app/build.gradle.kts:345-358`). `browser/build.gradle.kts:58` also has `implementation(libs.androidx.core.ktx)`. The app module is the one that needs the splash artifact.

## Files a splash implementation must edit

Must edit:

- `app/src/main/java/com/dantech/skimbrowser/MainActivity.kt` — install the splash in the existing `enableEdgeToEdge` / `super.onCreate` / `setContent` prefix.
- `app/src/main/AndroidManifest.xml` — splash theme on `<activity>`, not by replacing the application theme that `HiltRouteActivity` and `BrowserHostActivity` inherit.
- `app/src/main/res/values/themes.xml` — day splash theme; `Theme.SkimBrowser` is the current pre-first-frame background.
- `app/src/main/res/values-night/themes.xml` — night counterpart.
- `gradle/libs.versions.toml` — add `androidx.core:core-splashscreen` next to `core-ktx` 1.19.1.
- `app/build.gradle.kts` — `implementation` for that catalog entry.

Edit as well if the splash should cover the empty window (up to `START_TIMEOUT_MS`):

- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimApp.kt` — `state.started` is only known inside this composable (`SkimApp.kt:48`, `SkimApp.kt:56-60`). The second `enableEdgeToEdge` also runs here before `started` (`SkimApp.kt:58`).

Reuse without editing, unless the splash color or icon artwork itself changes:

- `app/src/main/res/values/colors.xml` — `skim_window_light` / `skim_window_dark` already match the Compose surfaces.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` and the two `mipmap-anydpi` adaptive icons — brand-mark drawing, wrong canvas for a splash mask if used as-is. A splash icon would be a new drawable.
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SkimAppViewModel.kt` — timeout and `startUp()` already exist; no splash hook.
- `app/src/main/java/com/dantech/skimbrowser/ui/shell/SystemBarsEffect.kt` — leave it unless the second `enableEdgeToEdge` must not run while the splash is kept. The call site is `SkimApp.kt:58`.

No test file asserts the window theme, so none of them must be edited for the splash to compile. `MainActivityLifecycleTest.filesSharedFromAnEarlierIncognitoSessionAreDeletedOnceItStarts` waits on the first-frame purge. `FrameBenchmarks.coldStartup` measures time to first frame.

## Unresolved

- Whether the splash should dismiss on the first frame or stay until `state.started`. That choice decides if `SkimApp.kt` is in the edit set.
- Whether the splash icon is a new drawable or the existing launcher foreground. The foreground is the brand mark flattened into one even-odd vector, not `SunnyMark`.
