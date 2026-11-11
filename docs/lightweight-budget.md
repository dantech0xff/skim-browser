# Lightweight budget

This page records the size of the minified app and the cuts that are not made yet. The gate is `size-budget/releaseSmoke.json`. CI runs `.github/workflows/size.yml`.

The measured artifact is the universal `releaseSmoke` APK from `./gradlew :app:assembleReleaseSmoke --no-configuration-cache`. That variant is minified like `release` and signed with the debug key. It is the build the README uses for smoke checks. There are no ABI splits, so one APK contains the native libraries for every ABI. `release` is the same minify settings plus the proxy URL check. It was not the measured file, because that check needs URLs this measurement does not need.

RAM and startup are out of scope. A cloud emulator crashes in the system WebView, and a software-rendered emulator does not produce a number worth keeping. This page has no device memory or startup figure.

## What limits the file

One assemble on JDK 25, Android Gradle Plugin 9.4.1, and R8 9.4.24 produced these file sizes. The font subset and the thin model client are both in this APK.

| Measure | Bytes |
|---|---|
| APK file size | 3,004,741 |
| apkanalyzer download-size estimate | 2,616,255 |
| Defined methods in `classes.dex` | 23,128 |
| Method references in `classes.dex` | 26,462 |

The baseline before either cut was an APK of 5,752,084 bytes (download estimate 5,341,980). This file is 2,747,343 bytes smaller. The font-only APK was 4,033,448 bytes. The thin-client-only APK, still carrying the full font, was 4,724,249 bytes. Those two cuts were measured separately. This page is the build that contains both.

The table above is the build before rating sync and before the request shield. One later assemble measured 3,763,034 bytes, 739,197 above 3,023,837. The download-size estimate is 3,356,087. `assets/shield/blocklist.txt` is 722,110 compressed bytes and 2,016,730 raw. It is the compiled EasyList and EasyPrivacy host snapshot (91,047 hosts). Rebuild it with `python3 tools/compile_blocklist.py`. A hundred thousand lookups of a hashed host took 46 ms in `HostBlocklistAssetTest` on the machine that ran it. Cosmetic rules are not in the file. The app does not download a newer list.

There is one dex file. 26,462 references is under the 65,536 reference limit.

`classes.dex` is most of the file.

| Entry | Compressed bytes in the APK | Uncompressed bytes |
|---|---|---|
| `classes.dex` | 2,264,656 | 4,426,840 |
| `resources.arsc` | 273,416 | 273,416 |
| `res/fU.ttf` (Google Sans Flex, wght only) | 89,941 | 244,336 |
| `res/` other than the font | 75,157 | 217,340 |
| Native libraries, all ABIs | 73,584 | 73,584 |
| `assets/` | 45,426 | 123,214 |
| `okhttp3/` | 41,558 | 41,612 |
| `META-INF/` | 31,484 | 85,932 |
| Other entries | 15,883 | 66,059 |

Compressed entry bytes sum to 2,911,105. The APK file is 93,636 bytes larger than that sum. That difference is the zip structure. The other entries are Kotlin built-in metadata, `AndroidManifest.xml`, and `DebugProbesKt.bin`.

`res/fU.ttf` is `app/src/main/res/font/google_sans_flex.ttf`. The shipped file keeps the `wght` axis and drops `opsz`, `wdth`, `ROND`, and `slnt`, pinned to the source defaults (18, 100, 0, and 0). It has 637 glyphs: Latin, Vietnamese (precomposed and combining marks), and the punctuation the UI draws. `tools/subset_google_sans_flex.py` rebuilds it from `tools/fonts/GoogleSansFlex-full.ttf`. `Type.kt` still builds weights 400, 500, and 600 with `FontStyle.Normal`.

Resource shrinking is already on. The `releaseSmoke` build ran `optimizeReleaseSmokeResources`, and `resources.txt` lists the resources R8 kept. Turning shrinking on is not a remaining cut. `resources.arsc` stays uncompressed in the APK because the platform reads it that way.

Native libraries are not split. Compressed size inside the universal APK:

| ABI | `libandroidx.graphics.path.so` | `libdatastore_shared_counter.so` | ABI total |
|---|---|---|---|
| arm64-v8a | 10,096 | 10,360 | 20,456 |
| x86_64 | 10,760 | 9,424 | 20,184 |
| x86 | 9,284 | 7,976 | 17,260 |
| armeabi-v7a | 7,252 | 8,432 | 15,684 |

## Where the dex bytes went

The shares below were measured on the 5,752,084 byte APK, before either cut. R8 obfuscates almost every class into the default package, so a package listing of the dex is not a library listing. The shares map each obfuscated class through `mapping.txt` to its original name, then scale apkanalyzer's per-class size so the shares sum to the compressed `classes.dex` size of 3,274,757 bytes. R8 also merges classes. A merged class is charged to the original name on the mapping header, so a share can include a few bytes from another library. This is one run. It is an estimate of APK contribution, not a second build with the library removed. Compressed `classes.dex` on the current APK is 2,264,656 bytes.

| Bucket | Classes | Methods | Scaled compressed dex bytes | Share of dex |
|---|---|---|---|---|
| Compose | 2,293 | 10,214 | 983,085 | 30.0% |
| Skim app | 1,083 | 3,634 | 454,336 | 13.9% |
| Jackson | 670 | 5,313 | 417,456 | 12.7% |
| kotlin-reflect | 860 | 4,547 | 379,522 | 11.6% |
| Anthropic SDK | 771 | 3,094 | 246,249 | 7.5% |
| Kotlin stdlib | 255 | 1,530 | 111,799 | 3.4% |
| jsoup | 190 | 923 | 86,376 | 2.6% |
| OkHttp | 119 | 672 | 81,424 | 2.5% |
| DataStore | 125 | 736 | 72,643 | 2.2% |
| kotlinx-coroutines | 225 | 903 | 70,685 | 2.2% |

Those shares are why the SDK left. `anthropic-java-core` declared dependencies on `kotlin-reflect`, Jackson, and `jackson-module-kotlin`. OkHttp stays. The app uses it for suggestions, downloads, and the model stream.

## Ranked cuts

Both of the large cuts are in this APK. The file fell from 5,752,084 bytes to 3,004,741 bytes before rating sync. Sending a like or dislike added the feedback client. One later assemble measured 3,023,837 bytes, 19,096 bytes above 3,004,741. The download-size estimate moved from 2,616,255 to 2,628,238. Defined methods are 23,214 and references are 26,549.

Saving an article for offline reading then measured 3,043,601 bytes on CI, 19,764 bytes above 3,023,837. Private-by-default then measured 3,045,321 bytes, 1,720 above that baseline. The request shield, measured earlier against 3,023,837, added 739,197 bytes (the APK was 3,763,034). Dan accepted that increase. The current baseline is 3,045,321 + 739,197 = 3,784,518. `assets/shield/blocklist.txt` is 722,110 compressed bytes of that. The download-size estimate was not remeasured for the combined file. `maxGrowthBytes` stays 8,192.

1. Done. Unused font axes are dropped and the glyph set is Latin plus Vietnamese. On its own, the APK file fell from 5,752,084 bytes to 4,033,448 bytes. The font entry is 89,941 compressed bytes. Rebuild with `python3 tools/subset_google_sans_flex.py`.

2. Done. The Anthropic Java SDK is replaced by an OkHttp client that posts the proxy body in `docs/deployment-guide.md` and reads the event stream. On its own, before the font subset, the APK file fell from 5,752,084 bytes to 4,724,249 bytes, and compressed `classes.dex` fell from 3,274,757 bytes to 2,264,741. In this combined APK, compressed `classes.dex` is 2,264,656 bytes. The earlier dex estimate was 1,043,227 bytes (Jackson 417,456, kotlin-reflect 379,522, SDK 246,249).

3. Stop packaging `META-INF` license files. They are 31,484 compressed bytes in this APK. Do this only after checking that the open-source licenses screen does not read those entries.

4. Split the APK by ABI. An arm64 device would drop the other three ABIs, about 53,128 compressed bytes. That is small next to the cuts already made.

Compose was the largest dex share on the old APK, at 983,085 bytes, and it stays. The UI is Jetpack Compose.

## Update the baseline

The CI job fails when the `releaseSmoke` APK file is larger than `fileSizeBytes` plus `maxGrowthBytes`. `maxGrowthBytes` is 8,192. A pull request that grows the APK by more than that has to change `fileSizeBytes` in the same pull request and say why in this document.

`maxGrowthBytes` is a starting allowance. Two local clean builds had the same file size, so 8,192 bytes covers a small toolchain difference. A new library should fail the check. Set the allowance to 0 if any growth should fail.

To update after an intentional size change, run the following from the repo root with no proxy URL and no API key.

```
./gradlew :app:assembleReleaseSmoke --no-configuration-cache
python3 tools/check_apk_budget.py
```

The script prints the new file size and the growth. Put the new file size in `fileSizeBytes`. If you also remeasure the download estimate, put that in `downloadSizeBytes`. Update the tables on this page so they match the JSON. Commit both files.

The gate compares file size only. It does not compare the download estimate, method count, or per-ABI sizes.

## Rebuild spread

Two clean `assembleReleaseSmoke` runs before either cut both wrote an APK of 5,752,084 bytes. The SHA-256 hashes differed. The gate compares the file size, not the hash. The size spread across those two runs was 0 bytes. The combined measurement above is one assemble.
