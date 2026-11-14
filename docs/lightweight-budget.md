# Lightweight budget

This page records the size of the minified app and the cuts that are not made yet. The gate is `size-budget/releaseSmoke.json`. CI runs `.github/workflows/size.yml`.

The primary artifact is the universal `playReleaseSmoke` APK from `./gradlew :app:assemblePlayReleaseSmoke --no-configuration-cache`. That variant is minified like `release` and signed with the debug key. It is the Play flavor, which has no YouTube extractor. The full flavor is measured separately in `size-budget/fullReleaseSmoke.json`. There are no ABI splits, so one APK contains the native libraries for every ABI. `release` is the same minify settings plus the proxy URL check. It was not the measured file, because that check needs URLs this measurement does not need.

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

The table above is the build before rating sync and before the request shield. One later assemble measured 3,763,034 bytes, 739,197 above 3,023,837. The download-size estimate is 3,356,087. `assets/shield/blocklist.txt` is 722,110 compressed bytes and 2,016,730 raw. It is the compiled EasyList and EasyPrivacy host snapshot (91,047 hosts). `assets/shield/cosmetic.txt` is the cosmetic rules from the same pin. Rebuild both with `python3 tools/compile_blocklist.py`. A hundred thousand lookups of a hashed host took 46 ms in `HostBlocklistAssetTest` on the machine that ran it. The app does not download a newer list.

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

Saving an article for offline reading then measured 3,043,601 bytes on CI, 19,764 bytes above 3,023,837. Private-by-default then measured 3,045,321 bytes, 1,720 above that baseline. The request shield, measured earlier against 3,023,837, added 739,197 bytes (the APK was 3,763,034). Dan accepted that increase. That baseline was 3,045,321 + 739,197 = 3,784,518. `assets/shield/blocklist.txt` is 722,110 compressed bytes of that. The download-size estimate was not remeasured for the combined file.

The Downloads screen measured 3,807,014 bytes, 22,496 above 3,784,518. Compressed `classes.dex` grew by 16,297 bytes and `resources.arsc` by 6,240. The list is a private file (`downloads-log`), not a new Room table, so the growth is the screen, the log, and the new strings.

Cosmetic rules then added `assets/shield/cosmetic.txt`, 211,572 compressed bytes and 785,374 raw (13,636 generic hides, 13,715 domain hides, 658 exceptions). CI measured that change as 227,956 bytes on the pre-Downloads baseline (4,012,474 minus 3,784,518). Added to the Downloads file, that is 3,807,014 + 227,956 = 4,034,970.

The certificate interstitial, measured on that cosmetic APK, is 4,039,430 bytes, 4,460 above 4,034,970. The added bytes are the warning page, the in-memory allowlist, and the WebView certificate handling. No library was added. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192.

A page hides at most 2,300 selectors. The shortest 2,000 are generic rules, and the rest are the rules for that host. `CosmeticFiltersAssetTest` parsed the shipped file in 33 ms and built the finance.yahoo.com sheet (2,084 rules) in about 1 ms. That was one run. The time is the single thread reading each line and appending the sheet. The test checks that known selectors are present, so the timed work ran. The read starts on a background thread when the first WebView is created.

Scam and phishing warnings on top of the certificate interstitial measured 4,062,758 bytes, 23,328 above 4,039,430. The dex is the on-device host check and the warning page. The resource table grew because each new string is indexed once per configuration. Safe Browsing itself is Android System WebView. `maxGrowthBytes` stays 8,192. The new baseline is 4,062,758.

Backup and restore, measured on the scam-warning APK, is 4,121,754 bytes, 58,996 above 4,062,758. Compressed `classes.dex` grew by 40,083 bytes and `resources.arsc` by 9,844. No library was added. `kotlinx.serialization` was already in the APK. The bytes are the backup codec, the settings screen, and the new strings. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new baseline is 4,121,754.

Find in page now clears its highlights when the tab changes or a new document starts, and Translate page sends visible text through the existing `/v1/messages` proxy. One assemble on top of the backup APK is 4,154,515 bytes, 32,761 above 4,121,754. `assets/js/skim-translate.js` is 1,366 compressed bytes. Compressed `classes.dex` in this APK is 2,414,429. No library was added. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new baseline is 4,154,515.

Popup blocking, odd-redirect blocking, and cookie-banner hiding, measured on that find-and-translate APK, is 4,168,285 bytes, 13,770 above 4,154,515. `assets/shield/cookie.txt` is 9,689 compressed bytes and 41,781 raw (1,800 generic hides from the EasyList Cookie general list, plus the banner roots the scroll unlock looks for). The other 4,081 bytes are the decision code, the three Settings rows, and the snackbar. No library was added. `apkanalyzer apk download-size` on this file reported 3,722,681. Defined methods are 24,326 and references are 27,744. Those three figures are this APK. The new baseline is 4,168,285.

The `distribution` flavors do not change that play file. One `assemblePlayReleaseSmoke` after the flavor split wrote 4,168,285 bytes, the same as the popup-blocking APK. The output path is now `app/build/outputs/apk/play/releaseSmoke/app-play-releaseSmoke.apk`. `assembleFullReleaseSmoke` on the same machine wrote 4,739,744 bytes, 571,459 above the play file. Compressed `classes.dex` in the full APK is 2,906,190 bytes, 479,244 above the play dex of 2,426,946. The rest of the gap is Rhino's resource files and the zip structure. `apkanalyzer apk download-size` on the full file reported 4,280,974. The play download estimate was not remeasured, because the file size did not change. `maxGrowthBytes` stays 8,192 for both files. The full baseline is 4,739,744.

Saving the video on the page, measured with `assemblePlayReleaseSmoke` on that flavor split, is 4,206,653 bytes, 38,368 above 4,168,285. No library was added. Media3 and ExoPlayer are not in the APK. Compressed `classes.dex` is 2,453,554. The bytes are the in-process save, the HLS and DASH fetch, the MediaMuxer path, the sheet, and the Settings row. `apkanalyzer apk download-size` on this file reported 3,751,332. Defined methods are 24,529 and references are 27,969. The YouTube extractor is not in the play APK. `maxGrowthBytes` stays 8,192. The new play baseline is 4,206,653.

Offering a download from the page address, fetching googlevideo in ranges, and listing playlist qualities then measured 4,223,465 bytes, 16,812 above 4,206,653. No library was added. The download-size estimate and the method counts were not remeasured. The new play baseline is 4,223,465. The play file on the current tree is 4,225,733 bytes, 2,268 above that baseline and inside the 8,192 allowance.

The same save path is in the full flavor. One `assembleFullReleaseSmoke` on that machine wrote 4,843,648 bytes, 103,904 above 4,739,744. Compressed `classes.dex` in the full APK is 3,006,980. `apkanalyzer apk download-size` on the full file reported 4,381,242. The full file is 636,995 above the play file. NewPipe, Rhino, and protobuf-javalite stay only in that flavor. `maxGrowthBytes` stays 8,192. That full baseline was 4,843,648.

A fresh `assembleFullReleaseSmoke` of the page-address offer, the ranged googlevideo fetches, and the playlist qualities wrote 4,862,728 bytes, 19,080 above 4,843,648. An earlier note that this flavor grew by 428 bytes came from an APK Gradle had not rebuilt. The play file from this tree is 4,225,733, and the gap between the two files is still 636,995. No library was added. The download-size estimate was not remeasured. `maxGrowthBytes` stays 8,192. The new full baseline is 4,862,728.

Listing one row per video height, and reporting HLS progress from segments finished, measured 4,242,997 bytes on the play APK, 19,532 above 4,223,465. `classes.dex` grew by 2,492 uncompressed bytes and 1,251 compressed bytes. The APK file grew by 16,384 because zip alignment moved to the next 16 KB boundary. No library was added. The same tree's full APK is 4,863,608 bytes, 880 above 4,862,728 and inside the allowance, so the full baseline stays. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new play baseline is 4,242,997.

Blocking stats, measured with `assemblePlayReleaseSmoke` and `assembleFullReleaseSmoke` on that tree, is 4,270,381 bytes on play, 27,384 above 4,242,997, and 4,890,992 bytes on full, 28,264 above 4,862,728. No library was added. Compressed `classes.dex` in these APKs is 2,495,758 (play) and 3,049,762 (full). The bytes are the stats screen, the two Room tables, and the in-memory batch. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new play baseline is 4,270,381. The new full baseline is 4,890,992.

The news wall on top of blocking stats, measured with `assemblePlayReleaseSmoke` and `assembleFullReleaseSmoke` on that tree, is 4,380,533 bytes on play, 110,152 above 4,270,381, and 5,017,528 bytes on full, 126,536 above 4,890,992. No library was added. A Kotlin JSON library was tried and removed, because it kept a runtime the release build had been able to drop. The bytes are the feed parser, the file cache, and the New tab list. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new play baseline is 4,380,533. The new full baseline is 5,017,528.

Saving a video from any page, measured with `assemblePlayReleaseSmoke` and `assembleFullReleaseSmoke` on JDK 25 on top of the news wall, is 4,396,917 bytes on play, 16,384 above 4,380,533, and 5,033,912 bytes on full, 16,384 above 5,017,528. Both files moved one 16 KB alignment page. Compressed `classes.dex` is 2,610,674 (play) and 3,166,373 (full). No library was added. The bytes are the media sniffer, the playing-file report, and the extensionless playlist peek. The download-size estimate and the method counts were not remeasured. `maxGrowthBytes` stays 8,192. The new play baseline is 4,396,917. The new full baseline is 5,033,912.

1. Done. Unused font axes are dropped and the glyph set is Latin plus Vietnamese. On its own, the APK file fell from 5,752,084 bytes to 4,033,448 bytes. The font entry is 89,941 compressed bytes. Rebuild with `python3 tools/subset_google_sans_flex.py`.

2. Done. The Anthropic Java SDK is replaced by an OkHttp client that posts the proxy body in `docs/deployment-guide.md` and reads the event stream. On its own, before the font subset, the APK file fell from 5,752,084 bytes to 4,724,249 bytes, and compressed `classes.dex` fell from 3,274,757 bytes to 2,264,741. In this combined APK, compressed `classes.dex` is 2,264,656 bytes. The earlier dex estimate was 1,043,227 bytes (Jackson 417,456, kotlin-reflect 379,522, SDK 246,249).

3. Stop packaging `META-INF` license files. They are 31,484 compressed bytes in this APK. Do this only after checking that the open-source licenses screen does not read those entries.

4. Split the APK by ABI. An arm64 device would drop the other three ABIs, about 53,128 compressed bytes. That is small next to the cuts already made.

Compose was the largest dex share on the old APK, at 983,085 bytes, and it stays. The UI is Jetpack Compose.

## Update the baseline

The CI job fails when either releaseSmoke APK is larger than its `fileSizeBytes` plus `maxGrowthBytes`. `maxGrowthBytes` is 8,192 on both files. A pull request that grows an APK by more than that has to change that file's `fileSizeBytes` in the same pull request and say why in this document.

`maxGrowthBytes` is a starting allowance. Two local clean builds had the same file size, so 8,192 bytes covers a small toolchain difference. A new library should fail the check. Set the allowance to 0 if any growth should fail.

To update after an intentional size change, run the following from the repo root with no proxy URL and no API key.

```
./gradlew :app:assemblePlayReleaseSmoke :app:assembleFullReleaseSmoke --no-configuration-cache
python3 tools/check_apk_budget.py --baseline size-budget/releaseSmoke.json
python3 tools/check_apk_budget.py --baseline size-budget/fullReleaseSmoke.json
```

The script prints the new file size and the growth. Put the new file size in `fileSizeBytes`. If you also remeasure the download estimate, put that in `downloadSizeBytes`. Update the tables on this page so they match the JSON. Commit both files.

The gate compares file size only. It does not compare the download estimate, method count, or per-ABI sizes.

## Rebuild spread

Two clean `assembleReleaseSmoke` runs before either cut both wrote an APK of 5,752,084 bytes. The SHA-256 hashes differed. The gate compares the file size, not the hash. The size spread across those two runs was 0 bytes. The combined measurement above is one assemble.
