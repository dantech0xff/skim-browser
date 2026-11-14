package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebMessageCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayingMessageTest {

    private val page = "https://news.example/story"
    private val origin = "https://news.example"
    private val body = """
        {"page":"https://news.example/story","urls":[
          {"src":"https://cdn.example/stream","duration":30,"width":640,"height":360}
        ],"docs":[]}
    """.trimIndent()

    @Test
    fun aStringFromTheMainFrameIsDelivered() {
        assertEquals(body, textPlayingMessage(WebMessageCompat.TYPE_STRING, true, origin, page) { body })
    }

    @Test
    fun anArrayBufferIsIgnoredAndItsDataIsNeverRead() {
        var read = false
        val message = WebMessageCompat(byteArrayOf(1))
        assertEquals(WebMessageCompat.TYPE_ARRAY_BUFFER, message.type)
        val delivered = textPlayingMessage(message.type, true, origin, page) {
            read = true
            message.data
        }
        assertNull(delivered)
        assertFalse(read)
        assertThrows(IllegalStateException::class.java) { message.data }
    }

    @Test
    fun aStringReadThatThrowsIsIgnored() {
        assertNull(
            textPlayingMessage(WebMessageCompat.TYPE_STRING, true, origin, page) {
                throw IllegalStateException("data")
            },
        )
    }

    @Test
    fun anIframeOrAnotherOriginIsIgnored() {
        assertNull(textPlayingMessage(WebMessageCompat.TYPE_STRING, false, "https://player.vimeo.com", page) { body })
        assertNull(textPlayingMessage(WebMessageCompat.TYPE_STRING, true, "https://player.vimeo.com", page) { body })
        val otherPage = """{"page":"https://news.example/next","urls":[],"docs":[]}"""
        assertNull(textPlayingMessage(WebMessageCompat.TYPE_STRING, true, origin, page) { otherPage })
    }
}
