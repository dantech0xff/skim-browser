// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // The Kotlin plugins pin the Kotlin Gradle plugin to 2.4.21; AGP 9.4.1 alone would bring 2.2.10,
    // which cannot read the Kotlin 2.4 metadata of kotlinx-serialization 1.12 and AboutLibraries 15.3.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
}
