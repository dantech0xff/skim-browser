import com.android.build.api.artifact.SingleArtifact
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile
import javax.imageio.ImageIO

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin): org.jetbrains.kotlin.android is not applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
}

// The debug API key and the release proxy URL are never committed. They come from local.properties
// (git-ignored) or, for CI, from Gradle properties (-P or ORG_GRADLE_PROJECT_* variables).
val localProperties = Properties().apply {
    providers.fileContents(layout.settingsDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}

/** A Gradle property when it has a value, otherwise local.properties; empty when neither sets it. */
fun skimProperty(name: String): String =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name, "").trim()

/** buildConfigField writes its value verbatim into Java source, so strings are quoted and escaped. */
fun javaString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

val localDebugApiKey: String = localProperties.getProperty("skim.anthropicApiKey", "").trim()

android {
    namespace = "com.dantech.skimbrowser"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dantech.skimbrowser"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.dantech.skimbrowser.SkimTestRunner"

        buildConfigField("String", "TERMS_URL", javaString(skimProperty("skim.termsUrl")))
        buildConfigField("String", "PRIVACY_URL", javaString(skimProperty("skim.privacyUrl")))
        buildConfigField("String", "AI_DATA_POLICY_URL", javaString(skimProperty("skim.aiDataPolicyUrl")))
    }

    buildTypes {
        debug {
            // Debug calls Anthropic directly with the developer's own spend-limited key.
            // Debug APKs embed that key and must never be distributed.
            buildConfigField("String", "AI_BASE_URL", javaString("https://api.anthropic.com"))
            buildConfigField("String", "ANTHROPIC_API_KEY", javaString(localDebugApiKey))
        }
        release {
            // Release calls the Skim proxy, which adds the real key server-side; the app ships no key.
            buildConfigField("String", "AI_BASE_URL", javaString(skimProperty("skim.aiProxyUrl")))
            buildConfigField("String", "ANTHROPIC_API_KEY", "null")
            // No packageScope: R8 optimizes every package, so the Anthropic SDK and its
            // dependencies (Jackson, OkHttp, Kotlin reflect) are shrunk as well.
            optimization {
                enable = true
            }
        }
        // Minified like release, signed with the debug key, and pointed at a local stand-in.
        // Instrumented smoke tests use this. It is not a build that ships.
        create("releaseSmoke") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "AI_BASE_URL", javaString("http://127.0.0.1:8787"))
            buildConfigField("String", "ANTHROPIC_API_KEY", "null")
        }
    }
    sourceSets {
        getByName("test") {
            resources.directories.add("src/sharedTest")
        }
        getByName("androidTest") {
            assets.directories.add("src/sharedTest")
        }
    }
    // `./gradlew :app:connectedReleaseSmokeAndroidTest -Pskim.testBuildType=releaseSmoke`
    testBuildType = providers.gradleProperty("skim.testBuildType").orElse("debug").get()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
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

aboutLibraries {
    collect {
        // Definitions for bundled assets that are not Maven dependencies (font, icons, Readability.js).
        configPath = file("config")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

/**
 * Pairs each recorded board with the design PNG of the same name and writes a red diff plus the
 * share of pixels that differ. Review output only: it is not part of `check`.
 */
abstract class DesignParity : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val boards: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val designs: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compare() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        val designDir = designs.get().asFile
        val summary = StringBuilder()
        boards.get().asFile.listFiles { file -> file.extension == "png" }
            ?.sortedBy { it.name }
            ?.forEach { board ->
                val design = File(designDir, board.name)
                val name = board.nameWithoutExtension
                if (!design.isFile) {
                    summary.append("$name: no design PNG\n")
                    return@forEach
                }
                val left = ImageIO.read(board)
                val right = ImageIO.read(design)
                if (left.width != right.width || left.height != right.height) {
                    summary.append("$name: size ${left.width}x${left.height} vs ${right.width}x${right.height}\n")
                    return@forEach
                }
                var changed = 0
                val diff = BufferedImage(left.width, left.height, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until left.height) {
                    for (x in 0 until left.width) {
                        val a = left.getRGB(x, y)
                        val b = right.getRGB(x, y)
                        if (a != b) {
                            changed++
                            diff.setRGB(x, y, 0xFFFF0000.toInt())
                        } else {
                            diff.setRGB(x, y, a)
                        }
                    }
                }
                ImageIO.write(diff, "png", File(out, "${name}_diff.png"))
                val percent = changed * 100.0 / (left.width * left.height)
                summary.append("$name: ${"%.2f".format(Locale.US, percent)}%\n")
            }
        File(out, "summary.txt").writeText(summary.toString())
    }
}

val designParity = tasks.register<DesignParity>("designParity") {
    group = "verification"
    description = "Writes a pixel diff of each recorded board against design/screens."
    boards.set(layout.projectDirectory.dir("src/test/screenshots/boards"))
    designs.set(layout.settingsDirectory.dir("design/screens"))
    outputDir.set(layout.buildDirectory.dir("design-parity"))
}

/**
 * Fails a release build that would ship without the AI proxy or the legal links, with a non-https
 * one, or with the proxy URL committed in gradle.properties. Values are never printed: the proxy
 * URL is operational configuration.
 */
abstract class CheckSkimReleaseConfig : DefaultTask() {
    @get:Input
    abstract val urls: MapProperty<String, String>

    @get:Input
    abstract val proxyUrlInCommittedProperties: Property<Boolean>

    @TaskAction
    fun check() {
        val problems = buildList {
            urls.get().forEach { (name, value) ->
                when {
                    value.isBlank() -> add("$name is not set.")
                    !value.startsWith("https://") -> add("$name must be an https:// URL.")
                }
            }
            if (proxyUrlInCommittedProperties.get()) {
                add("skim.aiProxyUrl must not be set in the committed gradle.properties.")
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                (listOf("Release configuration is invalid:") + problems.map { "  - $it" } + listOf(
                    "Set skim.aiProxyUrl in local.properties or pass -Pskim.aiProxyUrl=https://...;",
                    "the legal URLs (skim.termsUrl, skim.privacyUrl, skim.aiDataPolicyUrl) can also live in gradle.properties.",
                )).joinToString("\n"),
            )
        }
    }
}

/**
 * Opens every entry of the packaged APKs and the bundle and fails when one contains the Anthropic
 * key prefix or the exact debug key from local.properties, in UTF-8 or UTF-16LE.
 */
abstract class VerifyNoApiKey : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val apkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val bundleFile: RegularFileProperty

    // Not an up-to-date input on purpose: the task declares no outputs, so it runs on every build.
    @get:Internal
    abstract val debugApiKey: Property<String>

    @TaskAction
    fun verify() {
        val needles = buildList {
            add("the sk-ant- key prefix" to "sk-ant-")
            debugApiKey.get().takeIf { it.isNotBlank() }?.let { add("the debug API key from local.properties" to it) }
        }.flatMap { (label, text) ->
            // A byte-for-byte search: ISO-8859-1 maps every byte to exactly one char.
            listOf(Charsets.UTF_8, Charsets.UTF_16LE).map { label to String(text.toByteArray(it), Charsets.ISO_8859_1) }
        }
        val apks = apkDirectory.get().asFileTree.matching { include("**/*.apk") }.files.sortedBy { it.name }
        if (apks.isEmpty()) throw GradleException("No APK found in ${apkDirectory.get().asFile}.")

        val findings = (apks + bundleFile.get().asFile).flatMap { archive ->
            var entries = 0
            val matches = ZipFile(archive).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.flatMap { entry ->
                    entries++
                    val content = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    needles.filter { (_, needle) -> content.contains(needle) }
                        .map { (label, _) -> "${archive.name}!${entry.name} contains $label" }
                        .distinct()
                }.toList()
            }
            logger.lifecycle("Scanned ${archive.name}: $entries entries, ${matches.size} matches.")
            matches
        }
        if (findings.isNotEmpty()) {
            throw GradleException(
                (listOf("An Anthropic API key is packaged in a non-debug build:") + findings.map { "  - $it" })
                    .joinToString("\n"),
            )
        }
    }
}

val committedGradleProperties = providers.fileContents(layout.settingsDirectory.file("gradle.properties")).asText

val checkSkimReleaseConfig = tasks.register<CheckSkimReleaseConfig>("checkSkimReleaseConfig") {
    group = "verification"
    description = "Checks that the release AI proxy and legal URLs are set and use https."
    urls.set(
        listOf("skim.aiProxyUrl", "skim.termsUrl", "skim.privacyUrl", "skim.aiDataPolicyUrl")
            .associateWith { skimProperty(it) },
    )
    proxyUrlInCommittedProperties.set(
        committedGradleProperties.map { text ->
            !Properties().apply { load(text.reader()) }.getProperty("skim.aiProxyUrl").isNullOrBlank()
        }.orElse(false),
    )
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        if (variant.buildType == "release") {
            variant.lifecycleTasks.registerPreBuild(checkSkimReleaseConfig)
        }
        if (variant.buildType == "debug") return@onVariants

        val verifyNoApiKey = tasks.register<VerifyNoApiKey>("verifyNoApiKey$variantName") {
            group = "verification"
            description = "Fails when the $variantName APK or bundle contains an Anthropic API key."
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            debugApiKey.set(localDebugApiKey)
        }
        tasks.named { it == "assemble$variantName" || it == "bundle$variantName" }
            .configureEach { finalizedBy(verifyNoApiKey) }
        tasks.named("check") { dependsOn(verifyNoApiKey) }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":browser"))
    kover(project(":domain"))
    kover(project(":data"))
    kover(project(":browser"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.anthropic.java)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":data")))
    testImplementation(testFixtures(project(":browser")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    kspTest(libs.kotlin.metadata.jvm)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    kspAndroidTest(libs.kotlin.metadata.jvm)
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(":domain:test", ":data:testDebugUnitTest", ":browser:testDebugUnitTest")
}
tasks.matching { it.name == "koverHtmlReportDebug" || it.name == "koverXmlReportDebug" }.configureEach {
    dependsOn(":domain:test", ":data:testDebugUnitTest", ":browser:testDebugUnitTest")
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes(
                    "*Dao_Impl",
                    "*Dao_Impl$*",
                    "*Database_Impl",
                    "*Database_Impl$*",
                    "Hilt_*",
                    "*_Factory",
                    "*_MembersInjector",
                    "*_HiltModules*",
                )
            }
        }
    }
}
