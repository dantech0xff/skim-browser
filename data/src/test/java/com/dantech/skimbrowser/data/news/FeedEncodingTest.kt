package com.dantech.skimbrowser.data.news

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEncodingTest {
    @Test
    fun latin1AndUtf16TitlesSurvive() {
        val latin = """<?xml version="1.0" encoding="ISO-8859-1"?><rss><channel><item><title>Café</title></item></channel></rss>"""
            .toByteArray(Charset.forName("ISO-8859-1"))
        assertTrue(decodeFeed(latin, null).contains("Café"))

        val utf16 = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><title>Café</title>"
            .toByteArray(Charsets.UTF_16)
        assertTrue(decodeFeed(utf16, null).contains("Café"))
        assertEquals("Café", decodeFeed("Café".toByteArray(Charsets.ISO_8859_1), "ISO-8859-1"))
    }
}
