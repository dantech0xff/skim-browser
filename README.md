# Skim

Skim is an Android browser. It shows one WebView per tab, keeps an incognito session in its own WebView profile, and answers questions about the open page with Claude (`claude-haiku-5-5`). The UI is Jetpack Compose and Material 3 Expressive.

The app module is `:app` (`com.dantech.skimbrowser`). minSdk 26, targetSdk 37. There is no domain layer and no use-case package: screens talk to `AppContainer`, and repositories are concrete classes in `data/`. See `docs/system-architecture.md`.

## What you need

- JDK 17 or newer (the Gradle daemon in this repo is tested on 25).
- Android SDK with compileSdk 37. `local.properties` points at it with `sdk.dir`. That file is gitignored.
- A debug Anthropic key only if you want live answers. Tests use MockWebServer and do not need a key.

## local.properties

```
sdk.dir=/path/to/android-sdk
skim.anthropicApiKey=
```

`skim.anthropicApiKey` is read only by the debug build, which calls `https://api.anthropic.com` directly. Use a key with a spend limit. Do not distribute a debug APK: the key is inside it. Leave the property empty to build and run without live AI; Ask Skim then fails closed.

`skim.aiProxyUrl` may also live here for a local release build. Do not commit it. `gradle.properties` must keep `skim.aiProxyUrl` unset. `CheckSkimReleaseConfig` fails the release build if that property is set in the committed file.

## Legal URLs

Release needs three https URLs. They are empty in `gradle.properties` and can be passed on the command line:

```
-Pskim.termsUrl=https://example.invalid/terms
-Pskim.privacyUrl=https://example.invalid/privacy
-Pskim.aiDataPolicyUrl=https://example.invalid/ai-data
```

A debug build with empty URLs still installs. The Settings and AI rows that would open those URLs are not clickable until the properties are set.

## Build and test

From the repo root. KSP does not serialize into the configuration cache, so these commands pass `--no-configuration-cache`.

```
./gradlew :app:assembleDebug --no-configuration-cache
./gradlew :app:testDebugUnitTest --no-configuration-cache
./gradlew :app:verifyRoborazziDebug --no-configuration-cache
./gradlew :app:lintDebug --no-configuration-cache
./gradlew :app:koverHtmlReportDebug --no-configuration-cache
./gradlew :app:designParity --no-configuration-cache
```

`designParity` writes a red pixel diff of each recorded board against `design/screens/` into `app/build/design-parity/`. It is not part of `check`. Pixel diffs are not zero: Chrome and Skia rasterize type differently. The accepted differences are in `docs/design-guidelines.md`.

Record a golden after a deliberate UI change:

```
./gradlew :app:recordRoborazziDebug --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.example.*'
```

Screenshots use Robolectric at `w412dp-h915dp-xhdpi` (824×1830). WebView does not paint, so the page area of boards 05–06d is empty in the goldens.

Kover reports are under `app/build/reports/kover/`. Generated Android classes and Room `*Dao_Impl` / `*Database_Impl` are excluded. Composables are not excluded.

## Device checks

Instrumented tests are written and compile. They have not been run here: this environment has no system image, and none should be installed for this project. Dedicated AVDs, when you have the images:

- `skim_api36` on port 5580
- `skim_api28` on port 5582

Do not reuse another project's AVDs. Create them with `avdmanager` (`-d pixel_7`) from `system-images;android-36;google_apis;arm64-v8a` and `system-images;android-28;google_apis;arm64-v8a`. Boot with `-port 5580` or `-port 5582` and `-no-snapshot-save`. Then:

```
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest --no-configuration-cache
ANDROID_SERIAL=emulator-5582 ./gradlew :app:connectedDebugAndroidTest --no-configuration-cache
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedReleaseSmokeAndroidTest --no-configuration-cache -Pskim.testBuildType=releaseSmoke -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.ReleaseSmokeTest
```

On API 36 every incognito test must run. A skip means the device gate failed. On API 28, if `MULTI_PROFILE` is absent, `IncognitoUnavailableTest` must run and pass. Read the `SkimGate` line from `DeviceCapabilitiesReportTest`.

`ReleaseSmokeTest` points the minified app at `http://127.0.0.1:8787` and answers with the shared SSE fixtures. Cleartext to that host is allowed by `network_security_config.xml`.

## Release

Release is blocked until the proxy in `docs/deployment-guide.md` meets the security contract. Building it locally:

```
./gradlew :app:assembleRelease :app:bundleRelease --no-configuration-cache \
  -Pskim.aiProxyUrl=https://proxy.example.invalid \
  -Pskim.termsUrl=https://example.invalid/terms \
  -Pskim.privacyUrl=https://example.invalid/privacy \
  -Pskim.aiDataPolicyUrl=https://example.invalid/ai-data
```

`verifyNoApiKeyRelease` and `verifyNoApiKeyReleaseSmoke` run after assemble and bundle. They fail if an Anthropic key (`sk-ant-` or the debug key) is inside a non-debug APK or AAB.

## Docs

| File | What it covers |
|---|---|
| `docs/project-overview-pdr.md` | What the app is and what "done" means |
| `docs/codebase-summary.md` | Packages and the main types |
| `docs/code-standards.md` | How UI, data, and tests are written |
| `docs/system-architecture.md` | Tabs, incognito, AI, storage |
| `docs/design-guidelines.md` | How screens match `design/`, and the known gaps |
| `docs/deployment-guide.md` | Build types, R8, and the proxy contract |
| `docs/project-roadmap.md` | Decisions still open, and what was never designed |
