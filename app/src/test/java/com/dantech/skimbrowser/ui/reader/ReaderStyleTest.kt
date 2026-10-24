package com.dantech.skimbrowser.ui.reader

import com.dantech.skimbrowser.browser.page.PageBlock
import com.dantech.skimbrowser.browser.page.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reader type scale, and which blocks the article keeps. */
class ReaderStyleTest {

    @Test
    fun oneHundredPercentLeavesTheBodyAndOneHundredTenScalesIt() {
        val body = readerBodyStyle(ReaderParagraph, 100)
        assertEquals(17f, body.fontSize.value, 0.01f)
        assertEquals(28f, body.lineHeight.value, 0.01f)

        val larger = readerBodyStyle(ReaderParagraph, 110)
        assertEquals(18.7f, larger.fontSize.value, 0.01f)
        assertEquals(30.8f, larger.lineHeight.value, 0.01f)
    }

    @Test
    fun eightyAndTwoHundredAreTheEnds() {
        val small = readerBodyStyle(ReaderParagraph, 80)
        assertEquals(13.6f, small.fontSize.value, 0.01f)
        assertEquals(22.4f, small.lineHeight.value, 0.01f)

        val large = readerBodyStyle(ReaderParagraph, 200)
        assertEquals(34f, large.fontSize.value, 0.01f)
        assertEquals(56f, large.lineHeight.value, 0.01f)
    }

    @Test
    fun theSliderHasThirteenStops() {
        assertEquals(listOf(80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200), ReaderScalePercents.toList())
    }

    @Test
    fun aRepeatedTitleIsDroppedAndAPageThatIsNotAnArticleIsNotShown() {
        val page = page(readerable = true, blocks = listOf(block("1", "h1", TITLE), block("2", "p", "The numbers.")))
        val article = articleOf(page) ?: throw AssertionError("no article")

        assertEquals("daylightjournal.com", article.host)
        assertEquals(TITLE, article.title)
        assertEquals("Marta Lindqvist", article.byline)
        assertEquals(listOf(block("2", "p", "The numbers.")), article.blocks)
        assertEquals(3, readerHeaderCount(article))
        assertNull(articleOf(page.copy(readerable = false)))
    }

    @Test
    fun aMissingBylineShortensTheHeader() {
        val article = articleOf(page(readerable = true, byline = null, blocks = listOf(block("2", "p", "Body"))))
            ?: throw AssertionError("no article")

        assertNull(article.byline)
        assertEquals(2, readerHeaderCount(article))
    }

    private fun page(readerable: Boolean, byline: String? = "Marta Lindqvist", blocks: List<PageBlock>) = PageContent(
        url = "https://daylightjournal.com/cold",
        loadNonce = 1,
        title = TITLE,
        byline = byline,
        siteName = null,
        lang = "en",
        readerable = readerable,
        blocks = blocks,
    )

    private fun block(id: String, tag: String, text: String) = PageBlock(id, tag, text)

    private companion object {
        const val TITLE = "Why heat pumps are winning in cold climates"
    }
}
