package com.dantech.skimbrowser.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Domain is pure Kotlin: screens depend on it, and data implements it. Neither Android nor the
 * data layer may be imported from domain, and the UI sources do not import data.
 */
class DomainBoundaryTest {

    @Test
    fun domainImportsNeitherAndroidNorOuterLayers() {
        val domain = sourceRoot().resolve("domain")
        assertTrue("domain sources", domain.isDirectory)
        val forbidden = listOf(
            "import android.",
            "import androidx.",
            "import okhttp3.",
            "import com.dantech.skimbrowser.data",
            "import com.dantech.skimbrowser.ui",
            "import com.dantech.skimbrowser.browser",
            "import com.dantech.skimbrowser.ai",
            "import com.dantech.skimbrowser.system",
            "import com.dantech.skimbrowser.search",
        )
        val violations = kotlinFiles(domain).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                forbidden.firstOrNull { prefix -> trimmed.startsWith(prefix) }?.let { hit ->
                    "${file.path}:${index + 1} $hit"
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun uiSourcesDoNotImportTheDataLayer() {
        val ui = sourceRoot().resolve("ui")
        assertTrue("ui sources", ui.isDirectory)
        val violations = kotlinFiles(ui).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.trim().startsWith("import com.dantech.skimbrowser.data")) {
                    "${file.path}:${index + 1}"
                } else {
                    null
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun onlyTheCompositionRootConstructsTheConcreteChatRepository() {
        val root = sourceRoot()
        val violations = kotlinFiles(root).filter { file ->
            val path = file.path.replace('\\', '/')
            !path.contains("/data/") && file.name != "AppContainer.kt"
        }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.trim().startsWith("import com.dantech.skimbrowser.data.repo.ChatRepository")) {
                    "${file.path}:${index + 1}"
                } else {
                    null
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/dantech/skimbrowser"),
            File("app/src/main/java/com/dantech/skimbrowser"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Main sources were not found from ${File(".").absolutePath}")
    }

    private fun kotlinFiles(root: File): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
