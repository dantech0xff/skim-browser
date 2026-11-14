package com.dantech.skimbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLedgerTest {

    @Test
    fun aLeavingRequestCannotClearThePageThatReplacedIt() {
        val ledger = MediaLedger()
        val oldPage = "https://old.example/story"
        val newPage = "https://new.example/watch"
        ledger.noteDocumentStart(oldPage)
        val leaving = ledger.capture(oldPage)
        assertTrue(ledger.commit(leaving.epoch, oldPage, "https://cdn.example/old.mp4"))

        // The old request has been classified. The new document starts before that commit lands.
        ledger.noteMainFrame(newPage)
        val arriving = ledger.capture(newPage)
        assertTrue(ledger.commit(arriving.epoch, newPage, "https://cdn.example/new.mp4"))
        ledger.noteDocumentStart(newPage)

        assertFalse(ledger.commit(leaving.epoch, oldPage, "https://cdn.example/late.mp4"))
        assertEquals(listOf("https://cdn.example/new.mp4"), ledger.recorded(newPage))
        assertEquals(emptyList<String>(), ledger.recorded(oldPage))
    }

    @Test
    fun aReloadOfTheSameAddressDoesNotLetTheOldDocumentResetTheNewSet() {
        val ledger = MediaLedger()
        val page = "https://news.example/story"
        ledger.noteMainFrame(page)
        val first = ledger.capture(page)
        assertTrue(ledger.commit(first.epoch, page, "https://cdn.example/first.mp4"))

        ledger.noteMainFrame(page)
        val second = ledger.capture(page)
        assertTrue(ledger.commit(second.epoch, page, "https://cdn.example/second.mp4"))
        assertFalse(ledger.commit(first.epoch, page, "https://cdn.example/first-late.mp4"))
        ledger.noteDocumentStart(page)

        assertEquals(listOf("https://cdn.example/second.mp4"), ledger.recorded(page))
    }

    @Test
    fun aSegmentUsesThePlaylistsCapturedBeforeClassification() {
        val ledger = MediaLedger()
        val page = "https://news.example/story"
        ledger.noteMainFrame(page)
        val manifest = ledger.capture(page)
        assertEquals(emptyList<String>(), manifest.known)
        assertTrue(ledger.commit(manifest.epoch, page, "https://cdn.example/a.m3u8"))

        val segment = ledger.capture(page)
        assertEquals(listOf("https://cdn.example/a.m3u8"), segment.known)
        assertFalse(ledger.commit(segment.epoch, page, "https://cdn.example/a.m3u8"))
        assertEquals(listOf("https://cdn.example/a.m3u8"), ledger.recorded(page))
    }

    @Test
    fun aDocumentStartThatOnlyRespellsTheAddressKeepsTheInFlightFiles() {
        val ledger = MediaLedger()
        val fetched = "https://news.example/story"
        ledger.noteMainFrame(fetched)
        val watch = ledger.capture(fetched)
        assertTrue(ledger.commit(watch.epoch, fetched, "https://cdn.example/clip.mp4"))

        ledger.noteDocumentStart("https://www.news.example/story/")
        assertEquals(listOf("https://cdn.example/clip.mp4"), ledger.recorded("https://news.example/story#part"))
        assertTrue(ledger.commit(watch.epoch, fetched, "https://cdn.example/late.mp4"))
        assertEquals(
            listOf("https://cdn.example/clip.mp4", "https://cdn.example/late.mp4"),
            ledger.recorded("https://www.news.example/story"),
        )
    }

    @Test
    fun anAbandonedDownloadReturnsRecordingToThePageStillShowing() {
        val ledger = MediaLedger()
        val story = "https://news.example/story"
        ledger.noteDocumentStart(story)
        val before = ledger.capture(story)
        assertTrue(ledger.commit(before.epoch, story, "https://cdn.example/a.m3u8"))

        ledger.noteMainFrame("https://cdn.example/file.pdf")
        val during = ledger.capture(story)
        ledger.abandonTo(story)

        assertFalse(ledger.commit(during.epoch, "https://cdn.example/file.pdf", "https://cdn.example/file.pdf"))
        assertTrue(ledger.commit(during.epoch, story, "https://cdn.example/v.mp4"))
        assertEquals(listOf("https://cdn.example/a.m3u8", "https://cdn.example/v.mp4"), ledger.recorded(story))
    }
}
