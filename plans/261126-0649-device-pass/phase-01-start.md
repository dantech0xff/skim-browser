---
phase: 1
title: "Release checks on the AVDs"
status: todo
priority: P1
effort: "3h"
dependencies: []
---

# Phase 1: Release checks on the AVDs

## Goal

Run the device items of `docs/deployment-guide.md` (5, 6 and 7) on `skim_api36` and `skim_api28` and record each result.

## Before you start

1. `emulator -list-avds` lists `skim_api36` and `skim_api28`. If one is missing, stop and ask the owner. Do not install a system image.
2. `lsof -i :5580 -i :5582` shows nothing, or only this project's AVDs. If another process holds a port, ask; do not stop it.
3. Boot each AVD once and note its PID:
   - `emulator -avd skim_api36 -port 5580 -no-snapshot-save &`
   - `emulator -avd skim_api28 -port 5582 -no-snapshot-save &`
   - `adb -s emulator-5580 wait-for-device shell getprop sys.boot_completed` prints `1`, and the same for `emulator-5582`.

## Steps

1. Connected suite on API 36 (item 6). Every incognito test must run; a skip means the device gate failed. Read the `SkimGate` line from `DeviceCapabilitiesReportTest`.
   `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedPlayDebugAndroidTest --no-configuration-cache`
2. Connected suite on API 28 (item 6). If `MULTI_PROFILE` is absent, `IncognitoUnavailableTest` must run and pass.
   `ANDROID_SERIAL=emulator-5582 ./gradlew :app:connectedPlayDebugAndroidTest --no-configuration-cache`
3. Release smoke on API 36 (item 5), with logcat kept in a folder outside the repo:
   - `adb -s emulator-5580 logcat -c`
   - `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedPlayReleaseSmokeAndroidTest --no-configuration-cache -Pskim.testBuildType=releaseSmoke -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.skimbrowser.ReleaseSmokeTest`
   - `adb -s emulator-5580 logcat -d > <temp>/release-smoke-logcat.txt`
   - `grep -E "AnthropicInvalidDataException|ClassNotFoundException|SerializationException" <temp>/release-smoke-logcat.txt` prints nothing.
4. Manual pass on API 36 (item 7). The connected runs remove the app, so install the debug build first: `ANDROID_SERIAL=emulator-5580 ./gradlew :app:installPlayDebug --no-configuration-cache`.
   1. **Process death.** Open three normal tabs: a long page scrolled half way, a page in a tab group, and a page with the Ask Skim sheet open (without a proxy URL, use More options). Open one incognito tab. Show the normal tab with the sheet open, press Home, run `adb -s emulator-5580 shell am kill com.dantech.skimbrowser`, then reopen Skim from Recents. Expected (`design/spec/navigation-map.md` "Process death", `TabManager` restore): the normal tabs, their scroll position and the open sheet come back, each tab keeps its Skim chat, and the incognito tab does not come back.
   2. **Memory pressure.** With four normal tabs loaded, run `adb -s emulator-5580 shell am send-trim-memory com.dantech.skimbrowser RUNNING_CRITICAL`. Expected (`WebViewPoolPolicy`): one WebView stays live, and showing each other tab brings back its page from saved state, with no crash and no blank tab. Repeat with `RUNNING_MODERATE`: no tab reloads.
   3. **FLAG_SECURE.** Make an incognito tab current and run `adb -s emulator-5580 exec-out screencap -p > <temp>/incognito.png`. The app's area is black, and its Recents thumbnail is blank. Do the same with a sheet and a dialog of that incognito tab open, and after Back reveals an incognito tab. Then show a normal tab: a screencap shows the page again (`SkimNavigator` lets the flag go once nothing incognito is on screen).
5. Fill the Results table.

## Results

Result is pass, fail (with the test name and a log excerpt in `./reports/`), or not checked (with the reason).

| Check | Device | Result | Notes |
|---|---|---|---|
| Connected suite, every incognito test ran | skim_api36 | | |
| Connected suite, `IncognitoUnavailableTest` when `MULTI_PROFILE` is absent | skim_api28 | | |
| `ReleaseSmokeTest`, logcat clean | skim_api36 | | |
| Process death | skim_api36 | | |
| Memory pressure | skim_api36 | | |
| FLAG_SECURE | skim_api36 | | |

## Todo

- [ ] Connected suite on `skim_api36`
- [ ] Connected suite on `skim_api28`
- [ ] `ReleaseSmokeTest` with a clean logcat
- [ ] Process death
- [ ] Memory pressure
- [ ] FLAG_SECURE

## Success Criteria

- Every row has a result.
- A failure is recorded, not fixed in this pass. Its fix is a separate change with a test that fails before it.

## Risk Assessment

- `am kill` only stops a process in the background. Press Home first.
- A screencap of a secure window may fail instead of coming back black on some images. Either outcome shows the flag is on; a readable incognito frame is the failure.
- The full logcat stays out of the repo. Only an excerpt that shows a failure goes into `./reports/`.
