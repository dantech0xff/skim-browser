package com.dantech.skimbrowser.domain

import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dependencies point inward. The check reads every source line, so a fully qualified name fails
 * the same way an import does. Route files that still draw a WebView, a tab thumbnail,
 * or a site-permission prompt are the only `ui` files that may name `browser`.
 */
class DomainBoundaryTest {

    @Test
    fun domainImportsNeitherAndroidNorOuterLayers() {
        val violations = violationsIn("domain")
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun uiSourcesDoNotImportTheDataLayer() {
        val violations = violationsIn("ui").filter { it.contains(DATA) }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun layersStayInward() {
        val violations = listOf("domain", "ui", "ai", "browser", "data").flatMap { violationsIn(it) }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun onlyTheCompositionRootConstructsTheConcreteChatRepository() {
        val root = sourceRoot()
        val violations = kotlinFiles(root).filter { file ->
            val path = file.path.replace('\\', '/')
            !path.contains("/data/")
        }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (chatRepositoryOutsideData(file.path, line)) "${file.path}:${index + 1}" else null
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun browserWiringAllowlistNamesRealFiles() {
        val root = sourceRoot()
        BROWSER_WIRING.forEach { relative ->
            assertTrue(relative, root.resolve(relative).isFile)
        }
    }

    @Test
    fun oldLayerViolationsAreRejected() {
        val rejected = listOf(
            "ui/tabs/TabsViewModel.kt" to "import com.dantech.skimbrowser.browser.TabManager",
            "ui/ai/AskPageViewModel.kt" to "import com.dantech.skimbrowser.ai.SkimAiService",
            "ui/history/HistoryViewModel.kt" to "import com.dantech.skimbrowser.data.repo.HistoryRepository",
            "ui/tabs/TabsViewModel.kt" to "val tabs: com.dantech.skimbrowser.browser.TabManager",
            "ai/AiDataEraser.kt" to "import com.dantech.skimbrowser.ui.snackbar.SnackbarController",
            "browser/BrowsingDataCleaner.kt" to "import com.dantech.skimbrowser.ui.snackbar.UndoRegistry",
            "data/repo/ChatRepository.kt" to "import com.dantech.skimbrowser.ui.snackbar.SnackbarController",
            "ai/TabsContext.kt" to "import com.dantech.skimbrowser.data.db.entity.SkimAiState",
            "ai/AiGate.kt" to "import com.dantech.skimbrowser.data.hostOf",
            "browser/TabManager.kt" to "import com.dantech.skimbrowser.data.isWebUrl",
            "browser/TabManager.kt" to "import com.dantech.skimbrowser.data.withoutFragment",
            "browser/WebViewPool.kt" to "import com.dantech.skimbrowser.data.hostOf",
            "browser/BrowsingDataCleaner.kt" to "import com.dantech.skimbrowser.data.normalizeHost",
            "ui/settings/SettingsScreen.kt" to "import com.dantech.skimbrowser.browser.LocalBrowserHost",
            "ui/shell/SkimApp.kt" to "import com.dantech.skimbrowser.browser.BrowserHost",
            "ui/shell/SnackbarActions.kt" to "import com.dantech.skimbrowser.browser.ActivityHost",
            "ui/navigation/SkimNavDisplay.kt" to "import com.dantech.skimbrowser.browser.LocalBrowserHost",
            "ui/browsing/DownloadSnackbarEffect.kt" to "import com.dantech.skimbrowser.browser.downloads.DownloadTracker",
            "ai/AiGate.kt" to "val state = com.dantech.skimbrowser.data.db.entity.SkimAiState.ASK",
            "domain/tab/Tab.kt" to "import javax.inject.Inject",
            "domain/tab/Tab.kt" to "import dagger.Module",
            "domain/tab/Tab.kt" to "import android.os.Bundle",
            "domain/tab/Tab.kt" to "import androidx.compose.runtime.Composable",
            "domain/tab/Tab.kt" to "import okhttp3.OkHttpClient",
            "domain/tab/TabsController.kt" to "import com.dantech.skimbrowser.browser.TabManager",
            "domain/tab/TabsController.kt" to "val clock = android.os.SystemClock.uptimeMillis()",
            "domain/ai/AskPageAccess.kt" to "import com.dantech.skimbrowser.ui.navigation.SkimNavigator",
            "domain/ai/AskPageAccess.kt" to "import com.dantech.skimbrowser.data.repo.ChatRepository",
            "domain/ai/AskPageAccess.kt" to "import com.dantech.skimbrowser.ai.SkimAiService",
            "domain/ai/AskPageAccess.kt" to "import com.dantech.skimbrowser.system.VoiceInput",
            "domain/ai/AskPageAccess.kt" to "import com.dantech.skimbrowser.search.SuggestionParser",
            "ui/settings/SettingsScreen.kt" to "import com.dantech.skimbrowser.ai.SkimAiService",
            "ui/settings/SettingsScreen.kt" to "import com.dantech.skimbrowser.data.repo.HistoryRepository",
        )
        rejected.forEach { (path, line) ->
            assertTrue("$path should reject: $line", violation(path, line) != null)
        }

        assertNull(violation("browser/BrowsingDataCleaner.kt", "import com.dantech.skimbrowser.domain.url.normalizeHost"))
        assertNull(violation("domain/ai/AskPageAccess.kt", "package com.dantech.skimbrowser.domain.ai"))
        assertNull(violation("domain/ai/AskPageAccess.kt", "import com.dantech.skimbrowser.domain.tab.Tab"))
        assertNull(violation("domain/url/Host.kt", "import java.net.IDN"))
        assertNull(violation("SkimApplication.kt", "import com.dantech.skimbrowser.browser.TabManager"))
        assertTrue(!chatRepositoryOutsideData("data/di/DataBindingsModule.kt", "import com.dantech.skimbrowser.data.repo.ChatRepository as ChatStore"))
        assertTrue(chatRepositoryOutsideData("SkimApplication.kt", "import com.dantech.skimbrowser.data.repo.ChatRepository as ChatStore"))
    }

    private fun violationsIn(layer: String): List<String> {
        val roots = when (layer) {
            "domain" -> listOf(moduleSourceRoot("domain", "kotlin"))
            // :app's di/browser and di/ai are activity and BuildConfig wiring, not library sources.
            "data", "browser", "ai" -> listOf(moduleSourceRoot(layer, "java"))
            else -> listOf(sourceRoot())
        }
        val found = roots.flatMap { root ->
            listOf(root.resolve(layer), root.resolve("di").resolve(layer))
                .filter { it.isDirectory }
                .map { root to it }
        }
        assertTrue("$layer sources", found.isNotEmpty())
        return found.flatMap { (root, layerRoot) ->
            kotlinFiles(layerRoot).flatMap { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                file.readLines().mapIndexedNotNull { index, line ->
                    violation(relative, line)?.let { "$it:${index + 1}" }
                }
            }
        }
    }

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/dantech/skimbrowser"),
            File("app/src/main/java/com/dantech/skimbrowser"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Main sources were not found from ${File(".").absolutePath}")
    }

    /** Sources for [module]. Unit tests run with the working directory at `:app` or the repo root. */
    private fun moduleSourceRoot(module: String, language: String): File {
        val candidates = listOf(
            File("../$module/src/main/$language/com/dantech/skimbrowser"),
            File("$module/src/main/$language/com/dantech/skimbrowser"),
        )
        return candidates.firstOrNull { it.resolve(module).isDirectory }
            ?: error("$module sources were not found from ${File(".").absolutePath}")
    }

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private companion object {
        const val DATA = "com.dantech.skimbrowser.data"
        const val UI = "com.dantech.skimbrowser.ui"
        const val BROWSER = "com.dantech.skimbrowser.browser"
        const val AI = "com.dantech.skimbrowser.ai"
        const val DATA_ENTITY = "com.dantech.skimbrowser.data.db.entity"
        const val DATA_HOST_OF = "com.dantech.skimbrowser.data.hostOf"
        const val DATA_IS_WEB_URL = "com.dantech.skimbrowser.data.isWebUrl"
        const val DATA_NORMALIZE_HOST = "com.dantech.skimbrowser.data.normalizeHost"
        const val DATA_WITHOUT_FRAGMENT = "com.dantech.skimbrowser.data.withoutFragment"

        val DATA_URL_HELPERS = listOf(DATA_HOST_OF, DATA_IS_WEB_URL, DATA_NORMALIZE_HOST, DATA_WITHOUT_FRAGMENT)

        val DOMAIN_FORBIDDEN = listOf(
            "android.",
            "androidx.",
            "okhttp3.",
            DATA,
            UI,
            BROWSER,
            AI,
            "com.dantech.skimbrowser.system",
            "com.dantech.skimbrowser.search",
            "javax.inject.",
            "dagger.",
        )

        /**
         * These routes still name a WebView, a tab thumbnail, or a site-permission prompt.
         * Screens and ViewModels are not on this list.
         */
        val BROWSER_WIRING = setOf(
            "ui/browsing/BrowsingRoute.kt",
            "ui/tabs/TabsScreen.kt",
            "ui/browsing/LinkOptionsSheet.kt",
            "ui/browsing/SitePermissionDialog.kt",
        )

        fun chatRepositoryOutsideData(path: String, line: String): Boolean {
            val normalized = path.replace('\\', '/')
            val inData = normalized.contains("/data/") || normalized.startsWith("data/")
            if (inData) return false
            return line.contains("com.dantech.skimbrowser.data.repo.ChatRepository")
        }

        fun violation(relativePath: String, line: String): String? {
            val token = forbiddenToken(layerOf(relativePath), relativePath, line) ?: return null
            return "$relativePath contains $token"
        }

        /** A file under `di/data`, `di/browser`, or `di/ai` is held to that layer's rules. */
        fun layerOf(relativePath: String): String {
            val head = relativePath.substringBefore('/', missingDelimiterValue = "")
            if (head != "di") return head
            val binding = relativePath.removePrefix("di/").substringBefore('/', missingDelimiterValue = "")
            return if (binding == "data" || binding == "browser" || binding == "ai") binding else head
        }

        fun forbiddenToken(layer: String, relativePath: String, line: String): String? = when (layer) {
            "domain" -> DOMAIN_FORBIDDEN.firstOrNull { line.contains(it) }
            "ui" -> when {
                line.contains(DATA) -> DATA
                line.contains(AI) -> AI
                line.contains(BROWSER) && relativePath !in BROWSER_WIRING -> BROWSER
                else -> null
            }
            "ai" -> when {
                line.contains(UI) -> UI
                line.contains(DATA_ENTITY) -> DATA_ENTITY
                else -> DATA_URL_HELPERS.firstOrNull { line.contains(it) }
            }
            "browser" -> when {
                line.contains(UI) -> UI
                else -> DATA_URL_HELPERS.firstOrNull { line.contains(it) }
            }
            "data" -> if (line.contains(UI)) UI else null
            else -> null
        }
    }
}
