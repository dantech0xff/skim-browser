package com.dantech.skimbrowser.browser

/**
 * What the page scripts read from the HTML fixtures in the test APK's assets: the blocks (tag and
 * text) of each page, as the same scripts read them in desktop Chrome. The page text repeats one
 * sentence three times after each block's own words.
 */
internal object Fixtures {
    private const val SENTENCE = "Heat pumps move heat instead of making it, which is why they keep working in the cold."
    private val PARA = List(3) { SENTENCE }.joinToString(" ")

    private fun block(tag: String, start: String) = tag to "$start $PARA"

    /** article.html: hidden paragraphs and spans left out, invisible characters removed. */
    val ARTICLE_BLOCKS = listOf(
        "p" to "By Ana Lima",
        block("p", "First paragraph."),
        block("p", "Second paragraph with a hidden instruction inside."),
        block("p", "Third paragraph with zerowidth and bidi evil characters."),
        block("p", "List paragraph inside an item."),
        block("li", "Plain list item."),
        block("blockquote", "A quote."),
        "h2" to "Why it works",
        block("p", "Fourth paragraph ends here."),
        block("p", "Fifth paragraph."),
    )

    /**
     * divs.html, a forum thread without paragraph elements: the visible posts as paragraphs, the
     * heading and list item that wrap a div as themselves, the hidden post and the comment (which
     * Readability keeps out of the article) left out.
     */
    val DIVS_BLOCKS = listOf(
        block("p", "First post body in a div without any paragraph element."),
        block("p", "Second post body, with bold and a link inside."),
        block("p", "Third post is visible."),
        block("p", "Fourth post with a break second line."),
        "h2" to "Heading wrapped in a div",
        block("li", "List item wrapped in a div."),
        block("p", "A real paragraph inside a wrapper div."),
    )

    /** mixed.html: loose text divs between paragraphs, in document order. */
    val MIXED_BLOCKS = listOf(
        block("p", "Intro paragraph."),
        block("p", "Loose text in a div that follows a paragraph."),
        block("p", "Second paragraph."),
        "p" to "Another loose div. $PARA Still the same div after two breaks. $PARA",
        block("p", "Closing paragraph."),
    )

    /** plain.html, a settings page that is no article: the visible blocks of the whole page. */
    val PLAIN_BLOCKS = listOf("h3" to "Account", "li" to "Profile", "li" to "Security", "p" to "Short text.")

    /** How many times long.html shows the word "lighthouse". */
    const val LIGHTHOUSE_MATCHES = 7

    /** Characters a reader cannot see or that reorder text; none may reach the extracted text. */
    val INVISIBLE = listOf('\u200B', '\u200C', '\u200D', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2069', '\uFEFF')
}
