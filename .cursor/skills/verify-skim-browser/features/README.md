# Skim verification map

This directory is the maintained source for verifying Skim on an emulator. Read this index, then the feature file for the journey.

## Baseline preconditions

- One emulator is booted and `adb get-state` prints `device`.
- `ANDROID_SERIAL` names that emulator when any other device is connected.
- `app/build/outputs/apk/releaseSmoke/app-releaseSmoke.apk` exists. The debug APK is the wrong build for Ask Skim.
- `python3 tools/verify-skim/drive.py doctor` has been run on that emulator.
- The drive clears `com.dantech.skimbrowser` before it taps anything. Do not point it at a device whose data you need.

## Driving conventions

- Run `python3 tools/verify-skim/drive.py drive` for the full journey. The three feature files are the steps of that one journey, in order.
- Find controls by visible text or content description. The script taps the bounds of that node.
- `127.0.0.1:8787` on the emulator is the host only after `adb reverse tcp:8787 tcp:8787`. The drive command sets that reverse itself.

## Proof and skip reporting

- Save screenshots and `fake-backend.log` under `VERIFY_EVIDENCE`.
- A page open is proven by a screenshot of the loaded toolbar and a `GET` line in the backend log.
- Ask Skim is proven by the summary text on screen and a `POST /v1/messages` line with no API key.
- If a step cannot run, write the command and the unmet precondition. Do not mark a later step as covering the one that failed.

## Feature entry contract

Each feature file has an H1, one paragraph, then four H2 sections in this order. `Sub-features`, `How to get to it (user POV)`, `Driving it with verify-skim`, `Gotchas`.
