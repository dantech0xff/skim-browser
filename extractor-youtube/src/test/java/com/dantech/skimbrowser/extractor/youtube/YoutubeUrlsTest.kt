package com.dantech.skimbrowser.extractor.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUrlsTest {

    @Test
    fun watchMobileShortsAndShortLinksMatch() {
        assertTrue(YoutubeUrls.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YoutubeUrls.matches("http://youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertTrue(YoutubeUrls.matches("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YoutubeUrls.matches("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YoutubeUrls.matches("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YoutubeUrls.matches("https://www.youtube.com/shorts/abc123"))
        assertTrue(YoutubeUrls.matches("https://m.youtube.com/shorts/abc123?feature=share"))
    }

    @Test
    fun homeChannelsAndOtherSitesDoNotMatch() {
        assertFalse(YoutubeUrls.matches("https://www.youtube.com/"))
        assertFalse(YoutubeUrls.matches("https://www.youtube.com/watch"))
        assertFalse(YoutubeUrls.matches("https://www.youtube.com/feed/trending"))
        assertFalse(YoutubeUrls.matches("https://www.youtube.com/@somechannel"))
        assertFalse(YoutubeUrls.matches("https://www.youtube.com/shorts/"))
        assertFalse(YoutubeUrls.matches("https://youtu.be/"))
        assertFalse(YoutubeUrls.matches("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YoutubeUrls.matches("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YoutubeUrls.matches("javascript:alert(1)"))
    }
}
