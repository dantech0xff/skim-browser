plugins {
    // AGP 9 compiles Kotlin itself. This module is an Android library because Room and the
    // bookmark importer use the platform.
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.dantech.skimbrowser.data"
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
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":domain"))
    api(libs.androidx.room.runtime)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    ksp(libs.androidx.room.compiler)
}
