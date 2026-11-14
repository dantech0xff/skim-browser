package com.dantech.skimbrowser.ui.news

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsImagesTest {
    @Test
    fun aLargeImageIsSampledToTheThumbnailSize() {
        assertEquals(1, newsSampleSize(200, 100, 256))
        assertEquals(4, newsSampleSize(2000, 1000, 256))
    }

    @Test
    fun aMeteredNetworkDoesNotOpenTheImage() {
        assertEquals(null, loadNewsBitmapIfAllowed(allow = false, url = "http://127.0.0.1:1/nope"))
    }
}
