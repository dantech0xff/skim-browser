package com.dantech.skimbrowser.ui.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.download.DownloadCommands
import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun theListShowsTheLogAndRetryUsesTheSavedRequest() = runTest {
        val log = FakeDownloadLog()
        val commands = FakeDownloadCommands()
        val retry = DownloadRetry("tab-1", "https://example.com/guide.pdf", null, null, "application/pdf", true, false)
        log.rows.value = listOf(item(phase = DownloadPhase.Failed, retry = retry))
        val model = DownloadsViewModel(log, commands)

        val shown = model.state.first { it.items.size == 1 }.items.single()
        assertEquals("guide.pdf", shown.fileName)
        assertEquals(DownloadPhase.Failed, shown.phase)

        model.retry(shown)
        assertEquals(retry, commands.retried.single())
        assertEquals(1, commands.refreshes)
    }

    @Test
    fun removeForwardsWhetherTheFileGoesToo() = runTest {
        val log = FakeDownloadLog()
        val commands = FakeDownloadCommands()
        val model = DownloadsViewModel(log, commands)
        model.state.first { it.items.isEmpty() }

        model.remove(4, deleteFile = true)
        main.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4L to true, commands.removed.single())
    }

    private fun item(phase: DownloadPhase, retry: DownloadRetry?) = DownloadItem(
        id = 4,
        fileName = "guide.pdf",
        phase = phase,
        bytesSoFar = 0,
        bytesTotal = -1,
        uri = null,
        mime = null,
        incognito = false,
        retry = retry,
        systemId = null,
    )
}

private class FakeDownloadLog : DownloadLog {
    val rows = MutableStateFlow<List<DownloadItem>>(emptyList())

    override fun observe(): Flow<List<DownloadItem>> = rows

    override suspend fun noteStarted(draft: DownloadDraft): Long = 0

    override suspend fun noteSystem(localId: Long, systemId: Long) = Unit

    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) = Unit

    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long) = Unit

    override suspend fun noteFailed(localId: Long) = Unit

    override suspend fun remove(id: Long): DownloadItem? = null

    override suspend fun clearIncognito() = Unit

    override suspend fun runningWithSystemId(): List<DownloadItem> = emptyList()
}

private class FakeDownloadCommands : DownloadCommands {
    val retried = mutableListOf<DownloadRetry>()
    val removed = mutableListOf<Pair<Long, Boolean>>()
    var refreshes = 0

    override fun retry(retry: DownloadRetry) {
        retried += retry
    }

    override fun refresh() {
        refreshes += 1
    }

    override suspend fun remove(id: Long, deleteFile: Boolean) {
        removed += id to deleteFile
    }
}
