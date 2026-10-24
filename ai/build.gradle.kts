plugins {
    // AGP 9 compiles Kotlin itself. This module is an Android library because the adapter calls
    // the tab engine in :browser. Bindings live in this module; BuildConfig stays in :app inside ClaudeClientFactory.
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.dantech.skimbrowser.ai"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testFixtures {
        enable = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric reflects into these JDK packages; JDK 17+ (the daemon runs JDK 25) needs them opened.
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    api(project(":domain"))
    implementation(project(":browser"))
    implementation(libs.anthropic.java)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    // FakeClaudeServer's public methods name MockWebServer types. probeParams returns an SDK params type.
    testFixturesApi(libs.okhttp.mockwebserver)
    testFixturesApi(libs.anthropic.java)
    testFixturesImplementation(project(":browser"))
    testFixturesImplementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":data")))
    testImplementation(testFixtures(project(":browser")))
    testImplementation(project(":data"))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
