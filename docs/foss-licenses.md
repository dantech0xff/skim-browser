# FOSS licenses

Skim Browser is GPL-3.0-only. The terms are in `LICENSE`. Third-party code and bundled assets keep their own licenses. This page is the check that a `release` runtime classpath and the bundled assets are free software.

The check is the resolved `releaseRuntimeClasspath` of `:app` on this tree, after the Anthropic Java SDK was removed. 202 Maven artifacts were selected. 201 POMs name a license. Every named license is the Apache License 2.0, the MIT license, or BSD-3-Clause. One POM omits a `<licenses>` block and inherits one.

| Artifact | License |
|---|---|
| `com.google.guava:listenablefuture:1.0` | Apache-2.0, from parent `guava-parent`. |

Nothing on that classpath is Google Play services, Firebase, a proprietary SDK, Jackson, or `kotlin-reflect`. The model client is OkHttp, already listed under the Apache License 2.0.

Test-only artifacts (Robolectric, JUnit, Espresso, UiAutomator, Roborazzi, Turbine) are not in `releaseRuntimeClasspath`. They are not in the APK.

## Direct libraries

These are the versions the app modules declare. Transitive jars are included in the 202 above.

| Library | Version | License |
|---|---|---|
| AndroidX (Compose BOM and the other `androidx.*` artifacts) | catalog in `gradle/libs.versions.toml` | Apache-2.0 |
| `androidx.compose.material3:material3` | 1.5.0-beta01 | Apache-2.0 |
| `com.google.dagger:hilt-android` | 2.60.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.2 | Apache-2.0 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache-2.0 |
| `org.jsoup:jsoup` | 1.23.2 | MIT |
| `com.mikepenz:aboutlibraries-compose-m3` | 15.3.0 | Apache-2.0 |
| Kotlin stdlib, pulled by the Kotlin plugin | 2.4.21 | Apache-2.0 |

## Bundled assets

These are not Maven jars. The About screen reads `app/config/libraries/`.

| Asset | License | Text in the repo |
|---|---|---|
| Google Sans Flex | SIL OFL 1.1 | `app/licenses/OFL-GoogleSansFlex.txt` |
| Material Symbols Rounded | Apache-2.0 | `app/licenses/LICENSE-MaterialSymbols.txt` |
| Readability.js 0.6.0 | Apache-2.0 | `app/config/libraries/readability_js.json` |
| EasyList and EasyPrivacy host and cosmetic snapshots | GPL-3.0-or-later | `browser/src/main/assets/shield/`. Attribution: The EasyList authors (https://easylist.to/). |

The OFL file names the Google Sans Flex authors and does not declare a Reserved Font Name. The font ships under that name. Material Symbols stay Apache-2.0. Both can be bundled in a GPL-3.0-only program. That choice is decided.
