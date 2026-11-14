package com.dantech.skimbrowser.ui.news

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsImagesTest {
    @Test
    fun aLargeImageIsSampledToTheThumbnailSize() {
        assertEquals(1, newsSampleSize(200, 100, 256))
        assertEquals(4, newsSampleSize(2000, 1000, 256))
    }
}
