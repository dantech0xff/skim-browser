package com.dantech.skimbrowser.data.importer

import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.text.toVisibleText
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.data.repo.ImportedNode
import java.io.ByteArrayInputStream
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Reads the Netscape bookmark file every browser exports (Chrome, Firefox, Safari, Edge): folders
 * are `<DT><H3>` entries with their own `<DL>` list, bookmarks are `<DT><A HREF>` entries. Jsoup
 * reads it as a browser would, so tag soup, letter case and entities need no special handling.
 */
object NetscapeBookmarkParser {

    /** The doctype every browser writes at the top of the file. */
    private const val DOCTYPE = "netscape-bookmark-file-1"

    /** The longest URL kept: a longer one could not be read back from the database in one piece. */
    const val MAX_URL_LENGTH = 8_192

    /** The longest title kept for a bookmark or a folder. */
    const val MAX_TITLE_LENGTH = 1_024

    /**
     * The most tags a file may hold. The parser builds the whole document in memory, about 170
     * bytes for every element, so 10 MB of bare tags would take well over the heap an app is given.
     * A real export has three tags for each bookmark, which leaves room for some 80,000 of them.
     */
    const val MAX_TAGS = 250_000

    /**
     * The folders and bookmarks of the file [bytes] (its encoding read from the file, UTF-8 when
     * it names none), in file order, or null when it is not a bookmark file. Only http and https
     * links with a host are kept, so a `javascript:` or `file:` link never reaches the bookmarks;
     * titles are kept as plain visible text. A bookmark file without any bookmark gives an empty
     * list or empty folders.
     *
     * @throws IOException when the bytes cannot be decoded.
     */
    fun parse(bytes: ByteArray): List<ImportedNode>? {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val root = document.selectFirst("dl") ?: return null
        val declared = document.documentType()?.name().equals(DOCTYPE, ignoreCase = true)
        if (!declared && root.children().none(::isEntry)) return null
        return read(root)
    }

    /** True when [bytes] hold more than [MAX_TAGS] tags; counted before the file is parsed. */
    fun hasTooManyTags(bytes: ByteArray): Boolean {
        val tagStart = '<'.code.toByte()
        var tags = 0
        for (byte in bytes) {
            if (byte == tagStart && ++tags > MAX_TAGS) return true
        }
        return false
    }

    /** A `<DT>` holding a bookmark link or a folder heading. */
    private fun isEntry(element: Element): Boolean =
        element.normalName() == "dt" && (linkOf(element) != null || headingOf(element) != null)

    /**
     * The tree under the `<DL>` [root]. Lists are walked with an explicit stack, so a file nested
     * thousands of levels deep cannot overflow the call stack; each folder's list of children is
     * filled when its own `<DL>` is reached, in file order.
     */
    private fun read(root: Element): List<ImportedNode> {
        val top = ArrayList<ImportedNode>()
        val lists = ArrayDeque<Pair<Element, MutableList<ImportedNode>>>()
        lists.addLast(root to top)
        while (lists.isNotEmpty()) {
            val (list, into) = lists.removeLast()
            for (entry in list.children()) {
                if (entry.normalName() != "dt") continue
                val link = linkOf(entry)
                val heading = headingOf(entry)
                when {
                    link != null -> bookmarkOf(link)?.let(into::add)
                    heading != null -> {
                        val children = ArrayList<ImportedNode>()
                        into += ImportedNode(titleOf(heading), url = null, children = children)
                        folderListOf(entry)?.let { lists.addLast(it to children) }
                    }
                }
            }
        }
        return top
    }

    private fun linkOf(entry: Element): Element? = entry.children().firstOrNull { it.normalName() == "a" && it.hasAttr("href") }

    private fun headingOf(entry: Element): Element? = entry.children().firstOrNull { it.normalName() == "h3" }

    /** A folder's own list: inside its `<DT>`, or right after it when the file closed the `<DT>`. */
    private fun folderListOf(entry: Element): Element? =
        entry.children().firstOrNull { it.normalName() == "dl" }
            ?: entry.nextElementSibling()?.takeIf { it.normalName() == "dl" }

    private fun bookmarkOf(link: Element): ImportedNode? {
        val url = link.attr("href").trim()
        if (url.length > MAX_URL_LENGTH || hostOf(url) == null) return null
        return ImportedNode(titleOf(link), url)
    }

    private fun titleOf(element: Element): String = element.text().toVisibleText().cutTo(MAX_TITLE_LENGTH)
}
