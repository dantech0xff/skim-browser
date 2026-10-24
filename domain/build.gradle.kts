import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    `java-test-fixtures`
}

// Same bytecode as :app. The daemon may be a newer JDK; this module still emits Java 11 class files.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)

    testImplementation(testFixtures(project()))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
