# FOSS licenses

Skim Browser is GPL-3.0-only. The terms are in `LICENSE`. Third-party code and bundled assets keep their own licenses. This page is the check that a `release` runtime classpath and the bundled assets are free software.

The check is the resolved `releaseRuntimeClasspath` of `:app` on this tree. 224 Maven artifacts were selected. 216 POMs name a license. Every named license is the Apache License 2.0, the MIT license, or BSD-3-Clause. Eight POMs omit a `<licenses>` block and inherit one. Those eight are still free software.

| Artifact | License |
|---|---|
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.19.4` | Apache-2.0, inherited. The module POM has no license tag. Jackson publishes under the Apache License 2.0. |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.4` | Apache-2.0, same as the row above. |
| `com.github.victools:jsonschema-generator:4.38.0` | Apache-2.0, from parent `jsonschema-generator-parent`. |
| `com.github.victools:jsonschema-module-jackson:4.38.0` | Apache-2.0, same parent. |
| `com.github.victools:jsonschema-module-swagger-2:4.38.0` | Apache-2.0, same parent. |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0, from parent `guava-parent`. |
| `io.swagger.core.v3:swagger-annotations:2.2.50` | Apache-2.0, from parent `swagger-project`. |
| `org.slf4j:slf4j-api:2.0.16` | MIT. The POM has no license tag. `META-INF/LICENSE.txt` in the jar is the MIT license. |

Nothing on that classpath is Google Play services, Firebase, or a proprietary SDK. `com.anthropic:anthropic-java:2.73.0` is on the classpath of this tree and its POM says MIT. A separate change removes that SDK. OkHttp, which replaces the model call, is already here under the Apache License 2.0.

Test-only artifacts (Robolectric, JUnit, Espresso, UiAutomator, Roborazzi, Turbine) are not in `releaseRuntimeClasspath`. They are not in the APK.

## Direct libraries

These are the versions the app modules declare. Transitive jars are included in the 224 above.

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
| `com.anthropic:anthropic-java` | 2.73.0 | MIT |
| Kotlin stdlib, pulled by the Kotlin plugin | 2.4.21 | Apache-2.0 |

## Bundled assets

These are not Maven jars. The About screen reads `app/config/libraries/`.

| Asset | License | Text in the repo |
|---|---|---|
| Google Sans Flex | SIL OFL 1.1 | `app/licenses/OFL-GoogleSansFlex.txt` |
| Material Symbols Rounded | Apache-2.0 | `app/licenses/LICENSE-MaterialSymbols.txt` |
| Readability.js 0.6.0 | Apache-2.0 | `app/config/libraries/readability_js.json` |

The OFL file names the Google Sans Flex authors and does not declare a Reserved Font Name. The font can ship under that name. OFL and Apache-2.0 can both be bundled in a GPL-3.0-only program.
