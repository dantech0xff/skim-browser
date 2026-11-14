package com.dantech.skimbrowser.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.domain.download.DownloadRetry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aStartedDownloadKeepsItsRetryAndTheNewestRowIsFirst() = runTest {
        val log = log()
        val retry = DownloadRetry("tab-1", "https://example.com/guide.pdf", "ua", null, "application/pdf", true, false)
        val first = log.noteStarted(DownloadDraft("guide.pdf", incognito = false, retry = retry))
        val second = log.noteStarted(DownloadDraft("secret.pdf", incognito = true, retry = null))

        val items = log.observe().first()
        assertEquals(listOf(second, first), items.map { it.id })
        assertEquals(DownloadPhase.Running, items.last().phase)
        assertEquals(retry, items.last().retry)
        assertEquals(-1L, items.last().bytesTotal)
        assertNull(items.first().retry)
        assertEquals(true, items.first().incognito)
    }

    @Test
    fun progressFinishAndFailureAreStoredOnTheSameRow() = runTest {
        val log = log()
        val id = log.noteStarted(DownloadDraft("guide.pdf", incognito = false, retry = RETRY))
        log.noteSystem(id, 70)
        log.noteProgress(id, 25, 100)
        log.noteFinished(id, "content://downloads/70", "application/pdf", -1, null)

        val done = log.observe().first().single()
        assertEquals(DownloadPhase.Done, done.phase)
        assertEquals("content://downloads/70", done.uri)
        assertEquals("application/pdf", done.mime)
        assertEquals(100L, done.bytesTotal)
        assertEquals(70L, done.systemId)
        assertEquals(RETRY, done.retry)

        log.noteFailed(id)
        assertEquals(DownloadPhase.Failed, log.observe().first().single().phase)
    }

    @Test
    fun removeReturnsTheRowAndClearIncognitoKeepsTheNormalOnes() = runTest {
        val log = log()
        val normal = log.noteStarted(DownloadDraft("guide.pdf", incognito = false, retry = RETRY))
        val secret = log.noteStarted(DownloadDraft("secret.pdf", incognito = true, retry = null))

        val removed = log.remove(secret)
        assertEquals("secret.pdf", removed?.fileName)
        assertEquals(listOf(normal), log.observe().first().map { it.id })

        log.noteStarted(DownloadDraft("other.pdf", incognito = true, retry = null))
        log.clearIncognito()
        assertEquals(listOf(normal), log.observe().first().map { it.id })
    }

    @Test
    fun onlyRunningRowsWithASystemIdAreAskedAboutAgain() = runTest {
        val log = log()
        val running = log.noteStarted(DownloadDraft("guide.pdf", incognito = false, retry = RETRY))
        log.noteSystem(running, 70)
        val finished = log.noteStarted(DownloadDraft("done.pdf", incognito = false, retry = RETRY))
        log.noteSystem(finished, 71)
        log.noteFinished(finished, "content://downloads/71", "application/pdf", 10, null)
        log.noteStarted(DownloadDraft("local.pdf", incognito = false, retry = RETRY))

        assertEquals(listOf(running), log.runningWithSystemId().map { it.id })
    }

    @Test
    fun aNewRepositoryContinuesIdsFromTheSavedFile() = runTest {
        val file = folder.newFile("downloads-log")
        val first = DownloadRepository(file).noteStarted(DownloadDraft("guide.pdf", incognito = false, retry = null))

        assertEquals(first + 1, DownloadRepository(file).noteStarted(DownloadDraft("next.pdf", incognito = false, retry = null)))
    }

    @Test
    fun aNameWithANewlineOrASeparatorRoundTrips() = runTest {
        val log = log()
        log.noteStarted(DownloadDraft("a\nb\u001fc.pdf", incognito = false, retry = null))

        assertEquals("a\nb\u001fc.pdf", log.observe().first().single().fileName)
    }

    private fun log() = DownloadRepository(File(folder.root, "downloads-log"))

    private companion object {
        val RETRY = DownloadRetry("tab-1", "https://example.com/guide.pdf", null, null, null, true, false)
    }
}
