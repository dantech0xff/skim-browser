package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.domain.undo.UndoSubject
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Clear browsing data drops history, cancels its Undo, and clears the default profile's cache. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BrowsingDataCleanerTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close() }
    }

    @Test
    fun clearingHistoryRemovesVisitsAndFinalizesUndo() = runTest {
        val f = fixture()
        f.manager.newTab(incognito = false, url = PAGE)
        runCurrent()
        f.history.recordVisit(PAGE, "Heat pumps", f.now())
        f.db.tabDao().putSnapshot(
            TabGroupingSnapshotEntity(
                previousLayoutJson = "[]",
                tabSetKey = "tabs",
                appliedLayoutVersion = 1,
                bannerVisible = true,
                createdAt = 1,
            ),
        )
        File(f.stateDir.apply { mkdirs() }, "old.bin").writeText("state")
        File(f.thumbsDir.apply { mkdirs() }, "old.webp").writeBytes(byteArrayOf(1))
        f.settle()

        val undo = UndoRegistry(backgroundScope)
        var finalized = false
        val token = undo.register(UndoKind.HISTORY, undo = {}, finalize = { finalized = true })
        val snackbar = SnackbarController(undo)
        snackbar.show(UiText.Raw("Removed from history"), SnackbarAction.Undo(token))
        val site = RecordingSiteData()
        val pending = object : PendingUndo {
            override suspend fun cancel(subject: UndoSubject) {
                assertEquals(UndoSubject.HISTORY, subject)
                assertTrue(f.history.observe(null).first().isNotEmpty())
                SnackbarUndo(undo, snackbar).cancel(subject)
            }
        }

        cleaner(f, pending, site).clearAll(history = true, cookies = false, cache = true, pages = f.engine.toOpenPages())
        runCurrent()
        f.settle()

        assertTrue(f.history.observe(null).first().isEmpty())
        assertTrue(finalized)
        assertNull(snackbar.current.value)
        assertNull(f.tabRepo.snapshot())
        assertTrue(f.stateDir.listFiles().isNullOrEmpty())
        assertTrue(f.thumbsDir.listFiles().isNullOrEmpty())
        assertTrue(f.engine.calls.any { it is Call.ClearHistory })
        assertEquals(listOf(Call.TemporaryWebView(null)), f.engine.callsOf<Call.TemporaryWebView>())
        assertTrue(site.clearedAll.not())
    }

    @Test
    fun clearingCookiesReloadsNormalTabsAndAMissingEngineDefersCache() = runTest {
        val f = fixture()
        val normal = f.manager.newTab(incognito = false, url = PAGE)
        f.incognito.profileForNewTab { }
        val private = f.manager.newTab(incognito = true, url = "https://example.com/secret")
        runCurrent()
        val site = RecordingSiteData()
        val idle = UndoRegistry(backgroundScope)
        val cleaner = cleaner(f, SnackbarUndo(idle, SnackbarController(idle)), site)

        cleaner.clearAll(history = false, cookies = true, cache = true, pages = null)
        runCurrent()

        assertTrue(site.clearedAll)
        assertTrue(f.engine.calls.contains(Call.Reload(normal)))
        assertTrue(f.engine.calls.none { it is Call.Reload && it.tabId == private })

        cleaner.applyPendingCacheClear(f.engine)
        assertEquals(listOf(Call.TemporaryWebView(null)), f.engine.callsOf<Call.TemporaryWebView>())
    }

    private fun TestScope.fixture(): TabManagerFixture {
        val f = TabManagerFixture(this, folder.newFolder()).also { fixtures += it }
        f.manager.attach(f.engine)
        return f
    }

    private fun cleaner(
        f: TabManagerFixture,
        pendingUndo: PendingUndo,
        site: RecordingSiteData,
    ) = BrowsingDataCleaner(
        history = f.history,
        tabRepo = f.tabRepo,
        tabs = f.manager,
        stateStore = f.stateStore,
        thumbnails = f.thumbnails,
        incognito = f.incognito,
        pendingUndo = pendingUndo,
        siteData = site,
        mainDispatcher = f.main,
    )

    private class RecordingSiteData : DefaultProfileSiteData {
        var clearedAll = false
        override fun clearHost(host: String) = Unit
        override suspend fun clearAll() {
            clearedAll = true
        }
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy"
    }
}
