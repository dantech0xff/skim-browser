package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    @Test
    fun aBackupRoundTripsAndDropsAnythingThatIsNotASection() {
        val document = sample()
        val encoded = BackupCodec.encode(document)
        val decoded = BackupCodec.decode(encoded) as BackupDecode.Ok

        assertEquals(document, decoded.document)
        assertFalse(encoded.contains("installId"))
        assertFalse(encoded.contains("lastTabId"))
        assertFalse(encoded.contains("apiKey"))
        assertFalse(encoded.contains("token"))
        assertFalse(encoded.contains("\"chat\""))
    }

    @Test
    fun aCraftedFileCannotSmuggleASecretBackOut() {
        val text = """
            {
              "schemaVersion": 1,
              "appVersion": "1.0",
              "createdAt": 10,
              "installId": "secret-install",
              "apiKey": "sk-ant-secret",
              "token": "bearer",
              "messages": [{"role": "user", "text": "private chat"}]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(text) as BackupDecode.Ok
        val again = BackupCodec.encode(decoded.document)

        assertNull(decoded.document.bookmarks)
        assertNull(decoded.document.settings)
        assertFalse(again.contains("secret"))
        assertFalse(again.contains("sk-ant"))
        assertFalse(again.contains("private chat"))
    }

    @Test
    fun filesThatAreNotThisSchemaAreRefused() {
        val cases = listOf(
            "" to BackupFailure.MALFORMED,
            "{" to BackupFailure.MALFORMED,
            "not json" to BackupFailure.MALFORMED,
            """{"schemaVersion":1}""" to BackupFailure.MALFORMED,
            """{"schemaVersion":"1","appVersion":"1.0","createdAt":1}""" to BackupFailure.MALFORMED,
            """{"schemaVersion":2,"appVersion":"9.0","createdAt":1}""" to BackupFailure.UNSUPPORTED_VERSION,
            """{"schemaVersion":2,"nope":true}""" to BackupFailure.UNSUPPORTED_VERSION,
        )

        for ((text, failure) in cases) {
            val decoded = BackupCodec.decode(text)
            assertEquals(text, BackupDecode.Rejected(failure), decoded)
        }
    }

    @Test
    fun aFilePastTheCapOrNestedTooDeepIsTooLarge() {
        val small = """{"schemaVersion":1,"appVersion":"1.0","createdAt":1}"""
        val capped = BackupCodec.decode(ByteArrayInputStream(small.toByteArray()), maxBytes = 8)
        assertEquals(BackupDecode.Rejected(BackupFailure.TOO_LARGE), capped)

        val deep = StringBuilder("""{"schemaVersion":1,"appVersion":"1.0","createdAt":1,"bookmarks":[""")
        repeat(BackupCodec.MAX_DEPTH + 2) { deep.append("""{"kind":"FOLDER","name":"n","folders":[""") }
        deep.append("{}")
        repeat(BackupCodec.MAX_DEPTH + 2) { deep.append("]}") }
        deep.append("]}")
        assertEquals(BackupDecode.Rejected(BackupFailure.TOO_LARGE), BackupCodec.decode(deep.toString()))
    }

    @Test
    fun unsafeLinksAreDroppedAndABadSettingNameIsRefused() {
        val text = """
            {
              "schemaVersion": 1,
              "appVersion": "1.0",
              "createdAt": 5,
              "bookmarks": [{
                "kind": "MOBILE",
                "bookmarks": [
                  {"title": "Ok", "url": "https://example.com/a", "addedAt": 1},
                  {"title": "Script", "url": "javascript:alert(1)", "addedAt": 2}
                ]
              }],
              "history": [
                {"url": "https://example.com/h", "title": "H", "visitedAt": 3},
                {"url": "file:///tmp/x", "title": "Local", "visitedAt": 4}
              ],
              "tabs": [
                {"url": "https://example.com/t", "title": "T", "order": 0},
                {"url": "about:blank", "title": "Blank", "order": 1}
              ]
            }
        """.trimIndent()

        val document = (BackupCodec.decode(text) as BackupDecode.Ok).document
        assertEquals(listOf("https://example.com/a"), document.bookmarks?.single()?.bookmarks?.map { it.url })
        assertEquals(listOf("https://example.com/h"), document.history?.map { it.url })
        assertEquals(listOf("https://example.com/t"), document.tabs?.map { it.url })

        val badEngine = text.replace(""""createdAt": 5""", """"createdAt": 5, "settings": {"searchEngine": "YAHOO", "theme": "System", "askBeforeSending": true, "keepChatHistory": true, "readerTextScalePercent": 100, "readerFont": "SANS_SERIF", "onboardingCompleted": false}""")
        assertEquals(BackupDecode.Rejected(BackupFailure.MALFORMED), BackupCodec.decode(badEngine))
    }

    @Test
    fun anOutOfRangeTextSizeIsClampedAndAnOmittedSectionStaysAbsent() {
        val text = """
            {
              "schemaVersion": 1,
              "appVersion": "1.0",
              "createdAt": 5,
              "settings": {
                "searchEngine": "DUCKDUCKGO",
                "theme": "Dark",
                "askBeforeSending": false,
                "keepChatHistory": true,
                "readerTextScalePercent": 999,
                "pageTextZoomPercent": 1,
                "readerFont": "SERIF",
                "onboardingCompleted": true,
                "scamWarnings": false,
                "shieldDisabledHosts": ["WWW.Example.com", ""]
              }
            }
        """.trimIndent()

        val document = (BackupCodec.decode(text) as BackupDecode.Ok).document
        val settings = document.settings!!
        assertEquals(200, settings.readerTextScalePercent)
        assertEquals(80, settings.pageTextZoomPercent)
        assertEquals(setOf("example.com"), settings.shieldDisabledHosts)
        assertFalse(settings.scamWarnings)
        assertEquals(SearchEngine.DUCKDUCKGO, settings.searchEngine)
        assertEquals(ThemeSetting.Dark, settings.theme)
        assertEquals(ReaderFont.SERIF, settings.readerFont)
        assertNull(document.bookmarks)
        assertNull(document.history)
    }

    @Test
    fun theStartOfAFileSaysWhetherItIsJsonOrABookmarkPage() {
        val cases = listOf(
            """{"schemaVersion":1}""" to BackupCodec.Sniff.JSON,
            "\n  <DL><p>" to BackupCodec.Sniff.HTML,
            "\uFEFF<!DOCTYPE NETSCAPE-Bookmark-file-1>" to BackupCodec.Sniff.HTML,
            "hello" to BackupCodec.Sniff.OTHER,
        )
        for ((text, kind) in cases) {
            assertEquals(text, kind, BackupCodec.sniff(text.toByteArray(Charsets.UTF_8)))
        }
        assertTrue(BackupCodec.looksLikeHtml("<!DOCTYPE NETSCAPE-Bookmark-file-1>".toByteArray()))
    }

    private fun sample() = BackupDocument(
        schemaVersion = BackupCodec.SCHEMA_VERSION,
        appVersion = "1.0",
        createdAt = 1_700_000_000_000,
        bookmarks = listOf(
            BackupFolder(
                kind = FolderKind.MOBILE,
                name = null,
                sourceFileName = null,
                bookmarks = listOf(BackupBookmark("Kotlin", "https://kotlinlang.org/", 10)),
                folders = listOf(
                    BackupFolder(
                        kind = FolderKind.FOLDER,
                        name = "Work",
                        sourceFileName = null,
                        bookmarks = listOf(BackupBookmark("Docs", "https://developer.android.com/", 11)),
                    ),
                ),
            ),
        ),
        history = listOf(BackupHistory("https://example.com/", "Example", 20)),
        tabs = listOf(BackupTab("https://example.com/tab", "Tab", 0)),
        settings = BackupSettings(
            searchEngine = SearchEngine.ECOSIA,
            theme = ThemeSetting.Light,
            askBeforeSending = true,
            keepChatHistory = false,
            sendRatings = false,
            readerTextScalePercent = 120,
            pageTextZoomPercent = null,
            readerFont = ReaderFont.SANS_SERIF,
            onboardingCompleted = true,
            scamWarnings = true,
            shieldDisabledHosts = setOf("ads.example"),
        ),
        sitePermissions = listOf(
            BackupSitePermission(
                host = "example.com",
                skimAi = SkimAiState.ALLOWED,
                location = PermissionState.BLOCKED,
                camera = PermissionState.UNSET,
                microphone = PermissionState.ALLOWED,
            ),
        ),
    )
}
