# Contribute to Skim

Skim is an Android browser. The product rules are [Product vision](docs/product-vision.md). The engineering rules are [AGENTS.md](AGENTS.md) and [Code standards](docs/code-standards.md). This page is how you build, test, and open a pull request.

The license is [GPL-3.0](LICENSE). The conduct rules are [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Build

You need JDK 17 or newer. The Gradle daemon in this repo is tested on JDK 25. You need Android SDK compileSdk 37. `local.properties` is gitignored and points at the SDK.

```
sdk.dir=/path/to/android-sdk
skim.anthropicApiKey=
```

Leave `skim.anthropicApiKey` empty unless you want live answers from a debug build. A debug APK embeds that key. Do not commit `local.properties`, a proxy URL, or a key. `gradle.properties` keeps `skim.aiProxyUrl` unset.

From the repo root, pass `--no-configuration-cache`. KSP does not serialize into the configuration cache.

```
./gradlew :app:assembleDebug --no-configuration-cache
```

Release builds need https URLs. The command and the empty defaults are in the README section "Release". `releaseSmoke` is the minified build used for size and smoke checks. It does not need those URLs.

```
./gradlew :app:assembleReleaseSmoke --no-configuration-cache
```

## Test

Run the unit tests for the module you changed. When the change is in `:app` or crosses a module boundary, run the app suite. That task runs the module suites first.

```
./gradlew :app:testDebugUnitTest --no-configuration-cache
./gradlew :app:lintDebug --no-configuration-cache
./gradlew :app:koverHtmlReportDebug --no-configuration-cache
```

Kover writes `app/build/reports/kover/`. The `:app` report aggregates `:domain`, `:data`, `:browser`, and `:ai`. The Gradle build does not fail on a percentage. CI fails when the aggregated line rate is under 80 percent. The check is `tools/check_line_coverage.py` against `app/build/reports/kover/reportDebug.xml`.

```
python3 tools/check_line_coverage.py --self-test
python3 tools/check_line_coverage.py app/build/reports/kover/reportDebug.xml
```

Screenshot goldens are Roborazzi images under `app/src/test/screenshots/`. Verify them after a UI change.

```
./gradlew :app:verifyRoborazziDebug --no-configuration-cache
```

Record a golden only for a deliberate UI change, and name the test class you changed. `:app:verifyRoborazziDebug` is not in CI. A Linux run against the committed goldens failed five images. One of them, `boards/06d-ai-permission-first-time-per-site.png`, differed in 70,298 of 1,507,920 pixels. Run verify on the machine that recorded the goldens.

```
./gradlew :app:recordRoborazziDebug --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.example.*'
```

Screenshots use Robolectric at `w412dp-h915dp-xhdpi` (824 by 1830). WebView does not paint, so the page area of boards 05 to 06d is empty.

Do not install a system image for this project. Instrumented tests run on the dedicated AVDs named in the README section "Device checks", and only when you already have those images.

## Open a pull request

1. Read the product vision and AGENTS.md before you change behavior.
2. Keep the pull request to one change. UI copy stays in `app/src/main/res/values/strings.xml`, in English, with real plurals.
3. A ViewModel owns consent, incognito, tab layout, and deletion. A composable reads state and sends events.
4. Do not add a second page engine, an ad blocker, or a non-Android client.
5. Do not add a license header to each file. The root LICENSE file covers the tree.
6. Run the unit tests for the code you touched. Fill in the pull request template. CI on the pull request has to be green with no secrets.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request and on every push to `master`. The job runs unit tests, `:app:lintDebug`, the Kover line floor, and `:app:assembleReleaseSmoke`. It writes `local.properties` with an empty `skim.anthropicApiKey` and no proxy URL. It installs Android platform 37.0 and build-tools 36.0.0, which is the pair a clean SDK downloaded for `compileSdk { version = release(37) }`. Gradle dependencies are cached by `gradle/actions/setup-gradle`.
