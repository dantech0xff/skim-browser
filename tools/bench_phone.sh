#!/usr/bin/env bash
# Cold start, warm start, and page-scroll jank for Skim.
#
# Run this on a computer with the phone (or the skim_api36 emulator) attached.
# A software-rendered emulator is only a smoke check. Quote the phone numbers.
#
#   adb devices
#   ANDROID_SERIAL=<serial> tools/bench_phone.sh
#
# One test, for a quicker look. A dot before the method name is fine; # is too.
#   ANDROID_SERIAL=<serial> tools/bench_phone.sh com.dantech.skimbrowser.macrobenchmark.FrameBenchmarks.coldStartup
#
# The benchmark build is minified like release and is not debuggable. The first
# run compiles it. Results are JSON and a summary under
# macrobenchmark/build/outputs/connected_android_test_additional_output/
# and macrobenchmark/build/reports/androidTests/connected/.
#
# A baseline profile, after you trust a phone run:
#   ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedBenchmarkAndroidTest --no-configuration-cache
# Copy StartupProfile_generate-baseline-prof.txt from
# baselineprofile/build/outputs/connected_android_test_additional_output/
# to app/src/main/baseline-prof.txt and measure again with CompilationMode.Partial.
# Do not commit a profile that was produced on an emulator. profileinstaller
# is already in the app, so a checked-in profile is installed on first launch.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null; then
  echo "adb is not on PATH" >&2
  exit 1
fi
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  echo "Set ANDROID_SERIAL to the phone or emulator from 'adb devices'." >&2
  exit 1
fi

FILTER=()
if [[ $# -gt 0 ]]; then
  CLASS="$1"
  # AndroidJUnitRunner wants Class#method. The last dot is the method when no # is given.
  if [[ "$CLASS" != *#* ]]; then
    CLASS="${CLASS%.*}#${CLASS##*.}"
  fi
  FILTER=(-Pandroid.testInstrumentationRunnerArguments.class="$CLASS")
fi

./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --no-configuration-cache "${FILTER[@]}"

echo
echo "Read the *benchmarkData.json files under:"
echo "  macrobenchmark/build/outputs/connected_android_test_additional_output/"
