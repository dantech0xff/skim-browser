---
name: verify-skim-browser
description: Drive Skim Browser on an Android emulator. Installs the releaseSmoke build, opens a page, switches tabs, and asks Skim against a local fake backend. Use when a change must be proven on a running app.
---

# Verify Skim Browser

Skim is an Android app. The primary surface is the phone UI. Instrumented tests live under `app/src/androidTest` and are a second surface. This skill drives the installed app with `adb`. It does not type into a WebView's DOM.

Two drives cannot share one emulator. If `adb devices` shows a device this run did not boot, stop and use another emulator. Do not `pm clear` a device a person is using.

## Launch

From the repo root, with `ANDROID_HOME` set and `adb` and `emulator` on `PATH`.

1. Build the APK that talks to the local stand-in. The debug APK calls `https://api.anthropic.com` and will not use the fake backend.

```
./gradlew :app:assembleReleaseSmoke --no-configuration-cache
```

The APK is `app/build/outputs/apk/releaseSmoke/app-releaseSmoke.apk`.

2. Boot one emulator and wait until `adb shell getprop sys.boot_completed` prints `1`.

```
avdmanager create avd -n skim_api36 -k "system-images;android-36;google_apis;x86_64" -d pixel_7 --force
emulator -avd skim_api36 -port 5580 -no-snapshot-save -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim
```

Set `ANDROID_SERIAL=emulator-5580` when more than one device is connected.

If the emulator process stays near 0% CPU and adb stays offline, nested KVM accepted the device node and still did not run the guest. Boot again with `-accel off`. Software CPU is slow. A cold boot can take several minutes, and the drive dismisses "isn't responding" dialogs by tapping Wait.

Ready means the boot property is `1` and `adb get-state` prints `device`. A package that is not installed yet is still a valid ready emulator. The drive command installs the APK.

## Doctor

Read-only. Run this first when a drive looks wrong.

```
python3 tools/verify-skim/drive.py doctor
```

It prints the adb state, whether `com.dantech.skimbrowser` is installed, and the `adb reverse` list. A state other than `device` means do not drive.

## Drive

`tools/verify-skim/drive.py drive` does one journey on a cleared app.

- It starts `tools/verify-skim/fake_backend.py` on `127.0.0.1:8787`.
- It runs `adb reverse tcp:8787 tcp:8787` so the emulator's localhost is the host.
- It installs `app-releaseSmoke.apk`, clears the package, and launches `com.dantech.skimbrowser/.MainActivity`.
- It taps **Get started**, then **Start browsing**.
- It opens `http://127.0.0.1:8787/article` with `ACTION_VIEW`.
- It opens the tab switcher, creates a new tab, returns to the article tab, taps **Ask Skim about this page**, then **Allow**.
- It waits until the summary text `minus 25` is on screen.

Taps use the accessibility text or content description. The script reads `uiautomator dump`, finds that node, and taps the center of its bounds.

Pass means the fake backend log contains a `GET` for the page and a `POST /v1/messages` whose `x-api-key` header was absent and whose `X-Skim-Install` header was present, and the summary text was on screen.

```
VERIFY_EVIDENCE=/tmp/skim-verify-evidence python3 tools/verify-skim/drive.py drive
```

## Evidence

The script writes under `VERIFY_EVIDENCE`, default `/tmp/skim-verify-evidence`.

- `01-welcome.png` through `07-ask.png` are screenshots after each step, not only the last screen.
- `fake-backend.log` is the host server's request log.
- `gfxinfo.txt` is `dumpsys gfxinfo com.dantech.skimbrowser` after the journey.

A proof is the screenshot of the action's result plus the log line for the network side effect. A screenshot with no matching log line does not prove the page or the model call.

## Cleanup

The drive command stops the fake backend process it started and removes `adb reverse tcp:8787`. It does not delete `VERIFY_EVIDENCE`. It does not kill the emulator. Stop an emulator this run booted with the pid from that boot, not with `killall qemu` or `killall emulator`.

## Helpers

- `python3 tools/verify-skim/fake_backend.py --port 8787 --sse ai/src/testFixtures/resources/sse/summary-with-citations.sse --log /tmp/skim-verify-evidence/fake-backend.log`
- `python3 tools/verify-skim/drive.py doctor`
- `python3 tools/verify-skim/drive.py drive`

Keep the feature map honest with `/maintain-verification-skill` when screens change. The map is `features/` next to this file.
