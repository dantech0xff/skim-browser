# Lightweight budget

This page records the size of the minified app and the cuts that are not made yet. The gate is `size-budget/releaseSmoke.json`. CI runs `.github/workflows/size.yml`.

The measured artifact is the universal `releaseSmoke` APK from `./gradlew :app:assembleReleaseSmoke --no-configuration-cache`. That variant is minified like `release` and signed with the debug key. It is the build the README uses for smoke checks. There are no ABI splits, so one APK contains the native libraries for every ABI. `release` is the same minify settings plus the proxy URL check. It was not the measured file, because that check needs URLs this measurement does not need.

RAM and startup are out of scope. A cloud emulator crashes in the system WebView, and a software-rendered emulator does not produce a number worth keeping. This page has no device memory or startup figure.

## What limits the file

One assemble on JDK 25, Android Gradle Plugin 9.4.1, and R8 9.4.24 produced these file sizes.

| Measure | Bytes |
|---|---|
| APK file size | 4,033,448 |
| apkanalyzer download-size estimate | 3,630,138 |
| Defined methods in `classes.dex` | 36,269 |
| Method references in `classes.dex` | 40,218 |

The previous baseline, before the font subset, was an APK of 5,752,084 bytes (download estimate 5,341,980). This file is 1,718,636 bytes smaller.

There is one dex file. 40,218 references is under the 65,536 reference limit.

`classes.dex` is most of the file. The font is no longer the second half.

| Entry | Compressed bytes in the APK | Uncompressed bytes |
|---|---|---|
| `classes.dex` | 3,273,370 | 6,579,680 |
| `resources.arsc` | 273,416 | 273,416 |
| `res/fU.ttf` (Google Sans Flex, wght only) | 89,941 | 244,336 |
| `res/` other than the font | 76,972 | 231,300 |
| Native libraries, all ABIs | 73,584 | 73,584 |
| `assets/` | 47,738 | 125,526 |
| `okhttp3/` | 41,558 | 41,612 |
| `META-INF/` | 39,812 | 106,073 |
| Other entries | 15,943 | 66,113 |

Compressed entry bytes sum to 3,932,334. The APK file is 101,114 bytes larger than that sum. That difference is the zip structure. The other entries are Kotlin built-in metadata, `AndroidManifest.xml`, and `DebugProbesKt.bin`.

`res/fU.ttf` is `app/src/main/res/font/google_sans_flex.ttf`. The shipped file keeps the `wght` axis and drops `opsz`, `wdth`, `ROND`, and `slnt`, pinned to the source defaults (18, 100, 0, and 0). It has 637 glyphs: Latin, Vietnamese (precomposed and combining marks), and the punctuation the UI draws. `tools/subset_google_sans_flex.py` rebuilds it from `tools/fonts/GoogleSansFlex-full.ttf`. `Type.kt` still builds weights 400, 500, and 600 with `FontStyle.Normal`.

Resource shrinking is already on. The `releaseSmoke` build ran `optimizeReleaseSmokeResources`, and `resources.txt` lists the resources R8 kept. Turning shrinking on is not a remaining cut. `resources.arsc` stays uncompressed in the APK because the platform reads it that way.

Native libraries are not split. Compressed size inside the universal APK:

| ABI | `libandroidx.graphics.path.so` | `libdatastore_shared_counter.so` | ABI total |
|---|---|---|---|
| arm64-v8a | 10,096 | 10,360 | 20,456 |
| x86_64 | 10,760 | 9,424 | 20,184 |
| x86 | 9,284 | 7,976 | 17,260 |
| armeabi-v7a | 7,252 | 8,432 | 15,684 |

## Where the dex bytes go

R8 obfuscates almost every class into the default package, so a package listing of the dex is not a library listing. The shares below were measured on the 5,752,084 byte APK, before the font subset. They map each obfuscated class through `mapping.txt` to its original name, then scale apkanalyzer's per-class size so the shares sum to the compressed `classes.dex` size of 3,274,757 bytes. R8 also merges classes. A merged class is charged to the original name on the mapping header, so a share can include a few bytes from another library. This is one run. It is an estimate of APK contribution, not a second build with the library removed. Compressed `classes.dex` on the current APK is 3,273,370 bytes.

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

The jars that entered R8 are much larger than these residues. `anthropic-java-core` 2.73.0 is 39,136,463 bytes. `kotlin-reflect` 2.3.21 is 3,633,922 bytes. `jackson-databind` 2.19.4 is 1,679,441 bytes. Those jar sizes are not in the APK. R8 already removed most of them.

`anthropic-java-core` declares dependencies on `kotlin-reflect`, Jackson, and `jackson-module-kotlin`. The app's main source does not import `kotlin.reflect` or Jackson. OkHttp stays either way. The app uses it for suggestions and downloads.

## Ranked cuts

The font cut is in the app. The dex estimate below is not, and a combined saving is not measured here.

1. Done. Unused font axes are dropped and the glyph set is Latin plus Vietnamese. The APK file fell from 5,752,084 bytes to 4,033,448 bytes, a drop of 1,718,636 bytes. The font entry fell from 1,809,451 compressed bytes to 89,941. Rebuild with `python3 tools/subset_google_sans_flex.py`.

2. Replace the Anthropic SDK with a thin HTTP client that still posts the proxy body in `docs/deployment-guide.md`. The estimated compressed dex that leaves with the SDK is Jackson 417,456 plus kotlin-reflect 379,522 plus the SDK 246,249, which is 1,043,227 bytes. OkHttp remains. The 39,136,463 byte core jar is the wrong number for this saving. The estimate assumes nothing else keeps Jackson or kotlin-reflect, and that the rest of the dex compresses in the same proportion.

3. Stop packaging `META-INF` license files. They are 39,814 compressed bytes. Do this only after checking that the open-source licenses screen does not read those entries.

4. Split the APK by ABI. An arm64 device would drop the other three ABIs, about 53,128 compressed bytes. That is small next to the font and the dex.

Compose is the largest dex share, at 983,085 bytes, and it stays. The UI is Jetpack Compose.

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

Two clean `assembleReleaseSmoke` runs of the app before the font subset both wrote an APK of 5,752,084 bytes. The SHA-256 hashes differed. The gate compares the file size, not the hash. The size spread across those two runs was 0 bytes. The subset measurement above is one assemble.
