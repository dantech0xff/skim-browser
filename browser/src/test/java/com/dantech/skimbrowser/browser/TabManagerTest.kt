package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.RestoreResult

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.IncognitoSession.State
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.PersistedTabGroup
import com.dantech.skimbrowser.data.repo.TabPlacement
import com.dantech.skimbrowser.data.repo.TabRepository
import com.dantech.skimbrowser.testing.execRaw
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TabManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close(closeDatabase = false) }
        fixtures.map { it.db }.distinct().forEach { it.close() }
    }

    // New tabs

    @Test
    fun aNewTabBecomesCurrentAndIsSaved() = runTest {
        val f = fixture()

        val id = f.manager.newTab(incognito = false, url = "https://kotlinlang.org/")

        assertEquals(id, f.manager.state.value.currentTabId)
        val tab = f.tab(id) ?: throw AssertionError("no tab")
        assertEquals("https://kotlinlang.org/", tab.url)
        assertFalse(tab.incognito)
        assertNull(tab.groupId)
        assertEquals(f.now(), tab.createdAt)
        f.settle()
        assertEquals("https://kotlinlang.org/", f.openRows().single { it.id == id }.url)
        assertEquals(id, f.lastTabId())
    }

    @Test
    fun aBackgroundTabStaysBehindTheCurrentTab() = runTest {
        val f = fixture()

        val first = f.manager.newTab(incognito = false, background = true)
        val second = f.manager.newTab(incognito = false, url = "https://example.com/", background = true)

        assertEquals("with no current tab, even a background tab becomes current", first, f.manager.state.value.currentTabId)
        assertEquals(listOf(first, second), f.tabIds())
        assertEquals(1, f.tab(second)?.position)
    }

    @Test
    fun aUrlATabCannotOpenLeavesItOnTheNewTabPage() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)

        val id = f.manager.newTab(incognito = false, url = "javascript:alert(document.cookie)")
        f.manager.load(id, "file:///data/data/com.dantech.skimbrowser/databases/skim.db")

        assertNull(f.tab(id)?.url)
        assertTrue(f.engine.callsOf<FakeTabEngine.Call.Load>().isEmpty())
    }

    @Test
    fun withoutWebViewProfilesNoIncognitoTabOpens() = runTest {
        val f = fixture(supportsIncognito = false)

        val error = runCatching { f.manager.newTab(incognito = true) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(f.tabIds().isEmpty())
        assertEquals(0, f.profiles.callsWhileUnsupported)
    }

    @Test
    fun incognitoTabsNeverReachRoomHistoryTheLastTabSettingOrTheDisk() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val normal = f.manager.newTab(incognito = false, url = "https://a.example/")
        val private = f.manager.newTab(incognito = true, url = "https://b.example/")

        f.engine.openPage(private, "https://b.example/inbox", "Inbox")
        f.engine.navigateInDocument(private, "https://b.example/inbox/42")
        f.manager.bindChat(private, 9)
        f.manager.setDesktopSite(private, true)
        f.manager.select(private)
        f.engine.evict(private)
        f.thumbnails.put(private, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), incognito = true)
        advanceTimeBy(10_000)
        f.settle()

        assertEquals(listOf(normal), f.openRows().map { it.id })
        assertEquals(1L, f.rowCount())
        assertTrue(f.historyEntries().isEmpty())
        assertEquals(normal, f.lastTabId())
        assertFalse(f.stateDir.exists() && f.stateDir.list().orEmpty().isNotEmpty())
        assertFalse(f.thumbsDir.exists() && f.thumbsDir.list().orEmpty().isNotEmpty())
        assertNotNull("its state is kept in memory", f.stateStore.load(private, incognito = true))
        assertTrue(f.incognito.state.value is State.Active)
    }

    // Close, Undo and final close

    @Test
    fun closeHidesTabsAtOnceAndUndoPutsThemBackInTheirGroupAndPlace() = runTest {
        val f = fixture()
        val (a, b, c, d) = (1..4).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Heat pump research", listOf(a, b, c))), ungrouped = listOf(d)))
        val group = f.manager.state.value.groups.single().id
        f.settle()

        val token = f.manager.close(listOf(b))

        assertEquals(listOf(a, c, d), f.tabIds())
        assertEquals(setOf(b), f.manager.pendingCloseIds())
        f.settle()
        assertEquals(1L, f.closedRowCount())
        assertEquals(setOf(a, c, d), f.openRows().map { it.id }.toSet())

        f.manager.undoClose(token)

        assertEquals(listOf(a, b, c, d), f.tabIds())
        assertEquals(group, f.tab(b)?.groupId)
        assertEquals(1, f.tab(b)?.position)
        assertTrue(f.manager.pendingCloseIds().isEmpty())
        f.settle()
        assertEquals(0L, f.closedRowCount())
        assertEquals(group to 1, f.openRows().single { it.id == b }.let { it.groupId to it.position })
    }

    @Test
    fun undoOfATabWhoseGroupIsGoneSendsItToTheEndOfOther() = runTest {
        val f = fixture()
        val (a, b, c) = (1..3).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Osaka trip", listOf(a, b))), ungrouped = listOf(c)))
        val token = f.manager.close(listOf(a))
        f.manager.applyLayout(GroupLayout(emptyList(), ungrouped = listOf(b, c)))

        f.manager.undoClose(token)

        val restored = f.tab(a) ?: throw AssertionError("not back")
        assertNull(restored.groupId)
        assertEquals(2, restored.position)
        assertTrue(f.manager.state.value.groups.isEmpty())
        assertEquals(listOf(b, c, a), f.tabIds())
        f.settle()
        val row = f.openRows().single { it.id == a }
        assertNull(row.groupId)
        assertEquals(2, row.position)
    }

    @Test
    fun aTabIsWrittenLastWithTheStateItHasOnceTheQueueReachesIt() = runTest {
        // Held back until the test lets it run, the write queue meets the final state of every tab: the
        // layout and close writes queued before the last command must not leave an older row behind.
        val f = fixture(ioDispatcher = StandardTestDispatcher(testScheduler))
        val (a, b, c) = (1..3).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Osaka trip", listOf(a, b))), ungrouped = listOf(c)))
        val token = f.manager.close(listOf(a))
        f.manager.applyLayout(GroupLayout(emptyList(), ungrouped = listOf(b, c)))
        f.manager.undoClose(token)

        f.settle()

        val rows = f.openRows().associate { it.id to (it.groupId to it.position) }
        assertEquals(mapOf(a to (null to 2), b to (null to 0), c to (null to 1)), rows)
    }

    @Test
    fun aGroupWhoseTabsAreAllClosedForGoodIsDropped() = runTest {
        val f = fixture()
        val (a, b, c) = (1..3).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Osaka trip", listOf(a, b))), ungrouped = listOf(c)))
        val token = f.manager.close(listOf(a, b))
        assertEquals("the group waits for Undo", 1, f.manager.state.value.groups.size)

        f.manager.finalizeClose(token)

        assertTrue(f.manager.state.value.groups.isEmpty())
        assertEquals(listOf(c), f.tabIds())
    }

    @Test
    fun closingTheCurrentTabMakesTheMostRecentTabOfTheSameKindCurrent() = runTest {
        val f = fixture()
        val n1 = f.manager.newTab(incognito = false, url = "https://one.example/")
        advanceTimeBy(10)
        val n2 = f.manager.newTab(incognito = false, url = "https://two.example/")
        advanceTimeBy(10)
        val i1 = f.manager.newTab(incognito = true, url = "https://three.example/")
        advanceTimeBy(10)
        f.manager.select(n2)

        f.manager.close(listOf(n2))
        assertEquals("the incognito tab is newer, but of the other kind", n1, f.manager.state.value.currentTabId)

        f.manager.close(listOf(n1))
        assertNull(f.manager.state.value.currentTabId)
        assertNotNull(f.tab(i1))
    }

    @Test
    fun undoMakesTheClosedCurrentTabCurrentAgainUnlessAnotherWasChosen() = runTest {
        val f = fixture()
        val a = f.manager.newTab(incognito = false, url = "https://a.example/")
        advanceTimeBy(10)
        val b = f.manager.newTab(incognito = false, url = "https://b.example/")
        advanceTimeBy(10)
        val c = f.manager.newTab(incognito = false, url = "https://c.example/")

        val first = f.manager.close(listOf(c))
        assertEquals(b, f.manager.state.value.currentTabId)
        f.manager.undoClose(first)
        assertEquals(c, f.manager.state.value.currentTabId)

        val second = f.manager.close(listOf(c))
        f.manager.select(a)
        f.manager.undoClose(second)
        assertEquals(a, f.manager.state.value.currentTabId)
    }

    @Test
    fun aFinalCloseDeletesTheRowTheSavedStateAndTheThumbnail() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/", "A")
        f.thumbnails.put(id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), incognito = false)
        val thumbnail = f.thumbnails.observe(id)

        val token = f.manager.close(listOf(id))
        f.settle()
        assertTrue("the close saved the WebView state", File(f.stateDir, "$id.bin").isFile)
        assertTrue(File(f.thumbsDir, "$id.webp").isFile)
        assertNotNull("Undo still needs it", f.stateStore.load(id, incognito = false))

        f.manager.finalizeClose(token)
        f.settle()

        assertEquals(0L, f.rowCount())
        assertFalse(File(f.stateDir, "$id.bin").exists())
        assertFalse(File(f.thumbsDir, "$id.webp").exists())
        assertNull(thumbnail.value)
        assertEquals(TabRuntime(), f.manager.runtime(id).value)
    }

    @Test
    fun aCloseIsResolvedOnlyOnce() = runTest {
        val f = fixture()
        val (a, b) = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }

        val finalized = f.manager.close(listOf(a))
        f.manager.finalizeClose(finalized)
        f.manager.undoClose(finalized)
        assertNull(f.tab(a))

        val undone = f.manager.close(listOf(b))
        f.manager.undoClose(undone)
        f.manager.undoClose(undone)
        f.manager.finalizeClose(undone)
        assertEquals(listOf(b), f.tabIds())

        f.manager.undoClose(CloseToken(999))
        f.manager.finalizeClose(CloseToken(999))
        f.manager.undoClose(f.manager.close(emptyList()))
        f.manager.undoClose(f.manager.close(listOf("unknown")))
        assertEquals(listOf(b), f.tabIds())
        f.settle()
        assertEquals(listOf(b), f.openRows().map { it.id })
        assertEquals(1L, f.rowCount())
    }

    @Test
    fun aClosedTabIsNeverWrittenAgain() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/", "Before")
        f.settle()

        f.events.onTitle(id, "After")
        val token = f.manager.close(listOf(id))
        advanceTimeBy(10_000)
        f.settle()
        assertEquals(1L, f.closedRowCount())
        assertTrue(f.openRows().isEmpty())

        reportAndCommandLate(f, id)
        f.settle()
        assertEquals("still closed: nothing reopened it", 1L, f.closedRowCount())
        assertTrue(f.openRows().isEmpty())

        f.manager.finalizeClose(token)
        f.settle()
        assertEquals(0L, f.rowCount())

        reportAndCommandLate(f, id)
        f.settle()
        assertEquals("gone for good: nothing recreated it", 0L, f.rowCount())
        assertFalse(File(f.stateDir, "$id.bin").exists())
    }

    @Test
    fun undoWritesTheWholeTabEvenWhenItHasNoRowToReopen() = runTest {
        val f = fixture()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        val token = f.manager.close(listOf(id))
        f.settle()
        // As when the tab closed before its first write reached Room: that write was then dropped.
        f.db.execRaw("DELETE FROM tabs WHERE id = ?", id)

        f.manager.undoClose(token)
        f.settle()

        assertEquals(listOf(id), f.openRows().map { it.id })
        assertEquals("https://a.example/", f.openRows().single().url)
    }

    @Test
    fun writesReachRoomInTheOrderOfTheCommands() = runTest {
        val f = fixture()
        val a = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.manager.setDesktopSite(a, true)
        val first = f.manager.close(listOf(a))
        f.manager.undoClose(first)
        f.manager.bindChat(a, 12)
        val second = f.manager.close(listOf(a))
        f.manager.finalizeClose(second)
        val b = f.manager.newTab(incognito = false, url = "https://b.example/")
        f.manager.setDesktopSite(b, true)
        val third = f.manager.close(listOf(b))
        f.manager.undoClose(third)

        f.settle()

        assertEquals(listOf(b), f.openRows().map { it.id })
        assertEquals(1L, f.rowCount())
        assertTrue(f.openRows().single().desktopSite)
    }

    @Test
    fun finalizingPendingClosesMakesEveryWaitingCloseFinal() = runTest {
        val f = fixture()
        val (a, b, c) = (1..3).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val first = f.manager.close(listOf(a))
        f.manager.close(listOf(b))

        f.manager.finalizePendingCloses()
        f.manager.undoClose(first)

        assertTrue(f.manager.pendingCloseIds().isEmpty())
        assertEquals(listOf(c), f.tabIds())
        f.settle()
        assertEquals(listOf(c), f.openRows().map { it.id })
        assertEquals(1L, f.rowCount())
    }

    // The incognito session

    @Test
    fun closingTheLastIncognitoTabEndsTheSessionOnceTheCloseIsFinal() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val id = f.manager.newTab(incognito = true, url = "https://b.example/")
        val profile = activeProfile(f)
        f.engine.openPage(id, "https://b.example/", "B")

        val token = f.manager.close(listOf(id))
        assertEquals(State.Ending(profile, token), f.incognito.state.value)
        assertNotNull(f.stateStore.load(id, incognito = true))

        f.manager.finalizeClose(token)

        assertEquals(State.None, f.incognito.state.value)
        assertEquals(listOf(FakeTabEngine.Call.TemporaryWebView(profile)), f.engine.callsOf<FakeTabEngine.Call.TemporaryWebView>())
        assertTrue("clearCookies" in f.profiles.profile(profile).calls)
        assertTrue("clearWebStorage" in f.profiles.profile(profile).calls)
        assertNull(f.stateStore.load(id, incognito = true))
    }

    @Test
    fun undoOfTheLastIncognitoTabKeepsItsSession() = runTest {
        val f = fixture()
        val id = f.manager.newTab(incognito = true, url = "https://b.example/")
        val profile = activeProfile(f)

        f.manager.undoClose(f.manager.close(listOf(id)))

        assertEquals(State.Active(profile), f.incognito.state.value)
        assertNotNull(f.tab(id))
        assertEquals(listOf(profile), f.profiles.created)
    }

    @Test
    fun aNewIncognitoTabWhileTheLastOneWaitsForUndoStartsANewProfile() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val old = f.manager.newTab(incognito = true, url = "https://b.example/")
        val oldProfile = activeProfile(f)
        val token = f.manager.close(listOf(old))

        val new = f.manager.newTab(incognito = true, url = "https://c.example/")
        val newProfile = activeProfile(f)

        assertNotEquals(oldProfile, newProfile)
        assertFalse(old in f.manager.pendingCloseIds())
        assertTrue("the old profile was cleared", "clearCookies" in f.profiles.profile(oldProfile).calls)
        f.manager.undoClose(token)
        assertNull("Undo of the old session does nothing", f.tab(old))
        assertEquals(listOf(new), f.tabIds())
        assertEquals(State.Active(newProfile), f.incognito.state.value)
        assertNull(f.stateStore.load(old, incognito = true))
    }

    @Test
    fun closingAllIncognitoTabsEndsTheSessionAtOnce() = runTest {
        val f = fixture()
        val tabs = (1..3).map { f.manager.newTab(incognito = true, url = "https://site$it.example/") }
        val normal = f.manager.newTab(incognito = false, url = "https://normal.example/")

        f.manager.finalizeClose(f.manager.close(tabs))

        assertEquals(State.None, f.incognito.state.value)
        assertEquals(listOf(normal), f.tabIds())
    }

    @Test
    fun incognitoClosesWaitingForUndoEndWithTheSession() = runTest {
        val f = fixture()
        val normal = f.manager.newTab(incognito = false, url = "https://normal.example/")
        val first = f.manager.newTab(incognito = true, url = "https://one.example/")
        val second = f.manager.newTab(incognito = true, url = "https://two.example/")
        val mixed = f.manager.close(listOf(normal, first))
        assertTrue("an incognito tab is still open", f.incognito.state.value is State.Active)
        val last = f.manager.close(listOf(second))

        f.manager.finalizeClose(last)
        f.manager.undoClose(mixed)

        assertEquals(State.None, f.incognito.state.value)
        assertEquals("only the normal tab of the mixed close comes back", listOf(normal), f.tabIds())
        assertNull(f.stateStore.load(first, incognito = true))
    }

    @Test
    fun endingTheSessionDropsTheStateAndThumbnailOfEarlierIncognitoCloses() = runTest {
        val f = fixture()
        f.manager.attach(f.engine)
        val first = f.manager.newTab(incognito = true, url = "https://one.example/")
        val second = f.manager.newTab(incognito = true, url = "https://two.example/")
        f.engine.openPage(first, "https://one.example/", "One")
        f.thumbnails.put(first, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), incognito = true)
        val thumbnail = f.thumbnails.observe(first)
        val earlier = f.manager.close(listOf(first))
        assertNotNull("Undo still needs the state", f.stateStore.load(first, incognito = true))
        assertNotNull("and the thumbnail", thumbnail.value)

        f.manager.finalizeClose(f.manager.close(listOf(second)))

        assertEquals(State.None, f.incognito.state.value)
        assertTrue(f.manager.pendingCloseIds().isEmpty())
        assertNull(f.stateStore.load(first, incognito = true))
        assertNull(thumbnail.value)
        f.manager.undoClose(earlier)
        assertTrue("nothing of the ended session comes back", f.tabIds().isEmpty())
    }

    // Restore after process death

    @Test
    fun restoreBringsBackNormalTabsTheirGroupsAndTheLastTabButNoIncognitoTab() = runTest {
        val db = inMemorySkimDatabase()
        val before = fixture(db = db)
        val (n1, n2, n3) = (1..3).map { before.manager.newTab(incognito = false, url = "https://site$it.example/") }
        before.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Heat pump research", listOf(n1, n2))), listOf(n3)))
        before.manager.select(n2)
        before.manager.newTab(incognito = true, url = "https://private.example/")
        before.settle()
        before.closeSettings()

        val after = fixture(db = db, idPrefix = "after")
        val result = after.manager.restore()

        assertEquals(RestoreResult.Restored(3), result)
        assertEquals(listOf(n1, n2, n3), after.tabIds())
        assertEquals(n2, after.manager.state.value.currentTabId)
        val group = after.manager.state.value.groups.single()
        assertEquals("Heat pump research", group.title)
        assertEquals(listOf(group.id, group.id, null), after.manager.state.value.tabs.map { it.groupId })
        assertTrue(after.manager.state.value.tabs.none { it.incognito })
        assertEquals("https://site2.example/", after.tab(n2)?.url)
    }

    @Test
    fun restoreLeavesOutTabsClosedWhileUndoWasOffered() = runTest {
        val db = inMemorySkimDatabase()
        val before = fixture(db = db)
        val (kept, closed) = (1..2).map { before.manager.newTab(incognito = false, url = "https://site$it.example/") }
        before.manager.close(listOf(closed))
        before.settle()
        before.closeSettings()

        val after = fixture(db = db, idPrefix = "after")
        after.manager.restore()

        assertEquals(listOf(kept), after.tabIds())
        assertEquals(kept, after.manager.state.value.currentTabId)
    }

    @Test
    fun restoreStartsAboveTheLayoutVersionOfTheSavedSnapshot() = runTest {
        val db = inMemorySkimDatabase()
        val before = fixture(db = db)
        val (a, b) = (1..2).map { before.manager.newTab(incognito = false, url = "https://site$it.example/") }
        before.manager.applyLayout(GroupLayout(emptyList(), listOf(b, a)))
        val snapshot = GroupingSnapshot(previousLayoutJson = "{}", tabSetKey = "k", appliedLayoutVersion = 0, bannerVisible = true, createdAt = 1)
        assertTrue(before.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Grouped", listOf(a, b), createdBySkim = true)), emptyList()), expectedVersion = 1, snapshot = snapshot))
        before.settle()
        assertEquals("stamped with the version the layout produced", 2L, before.tabRepo.snapshot()?.appliedLayoutVersion)
        before.closeSettings()

        val after = fixture(db = db, idPrefix = "after")
        after.manager.restore()

        assertEquals(3L, after.manager.state.value.layoutVersion)
    }

    @Test
    fun restoreFallsBackToANewTabWhenTheDatabaseDoesNotAnswerAndThenSavesNothing() = runTest {
        // The restore timeout runs on the real clock, with the database read; a short one keeps the test quick.
        val f = fixture(restoreTimeoutMs = 300)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread {
            f.db.runInTransaction(
                Runnable {
                    locked.countDown()
                    release.await()
                },
            )
        }
        locked.await()
        val result = try {
            f.manager.restore()
        } finally {
            release.countDown()
            holder.join()
        }

        assertEquals(RestoreResult.Fallback(timedOut = true), result)
        val current = f.manager.state.value.currentTab ?: throw AssertionError("no tab to show")
        assertNull(current.url)
        assertFalse(current.incognito)
        f.manager.newTab(incognito = false, url = "https://a.example/")
        f.settle()
        assertEquals("the saved tabs stay as they were", 0L, f.rowCount())
    }

    @Test
    fun restoreDeletesSavedStateAndThumbnailsOfTabsThatAreGone() = runTest {
        val db = inMemorySkimDatabase()
        val before = fixture(db = db)
        before.manager.attach(before.engine)
        val kept = before.manager.newTab(incognito = false, url = "https://kept.example/")
        val gone = before.manager.newTab(incognito = false, url = "https://gone.example/")
        listOf(kept, gone).forEach { id ->
            before.engine.openPage(id, "https://$id.example/")
            before.engine.evict(id)
            before.thumbnails.put(id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), incognito = false)
        }
        before.manager.close(listOf(gone))
        before.settle()
        File(before.stateDir, "stray.bin.new").writeText("interrupted write")
        before.closeSettings()

        val after = fixture(db = db, idPrefix = "after")
        after.manager.restore()
        after.settle()

        assertEquals(setOf("$kept.bin"), after.stateDir.list().orEmpty().toSet())
        assertEquals(setOf("$kept.webp"), after.thumbsDir.list().orEmpty().toSet())
    }

    @Test
    fun restoreRunsOnceAndEveryCallerGetsItsResult() = runTest {
        val f = fixture()

        val first = f.manager.restore()
        f.tabRepo.upsert(PersistedTab(id = "late", url = "https://late.example/", title = "", position = 0, createdAt = 1, lastAccessedAt = 1))
        val second = f.manager.restore()

        assertEquals(RestoreResult.Restored(0), first)
        assertEquals(first, second)
        assertTrue(f.tabIds().isEmpty())
    }

    @Test
    fun aNewGroupNeverGetsTheIdOfAGroupThatWasDeleted() = runTest {
        val db = inMemorySkimDatabase()
        seedGroups(db)
        val f = fixture(db = db)
        f.manager.restore()
        assertEquals(listOf(3L, 7L), f.manager.state.value.groups.map { it.id })
        val (x, y) = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }

        f.manager.applyLayout(f.manager.state.value.layout().let { it.copy(groups = it.groups + GroupLayout.Group(null, "New", listOf(x, y)), ungrouped = it.ungrouped - setOf(x, y)) })
        val created = f.manager.state.value.groups.last().id
        assertEquals(8L, created)
        val token = f.manager.close(listOf(x))
        f.manager.applyLayout(f.manager.state.value.layout().let { layout -> layout.copy(groups = layout.groups.filter { it.id != created }, ungrouped = layout.ungrouped + y) })
        f.manager.applyLayout(f.manager.state.value.layout().let { it.copy(groups = it.groups + GroupLayout.Group(null, "Another", listOf(y)), ungrouped = it.ungrouped - y) })

        val another = f.manager.state.value.groups.last().id
        assertEquals(9L, another)
        f.manager.undoClose(token)
        assertNull("its group is gone; it does not join the new one", f.tab(x)?.groupId)
    }

    // Layout

    @Test
    fun aLayoutBasedOnAnOldVersionChangesNothing() = runTest {
        val f = fixture()
        val (a, b) = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val start = f.manager.state.value.layoutVersion
        val snapshot = GroupingSnapshot(previousLayoutJson = "{}", tabSetKey = "a,b", appliedLayoutVersion = 99, bannerVisible = true, createdAt = 1)

        assertTrue(f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Research", listOf(a, b), createdBySkim = true)), emptyList()), start, snapshot))
        val applied = f.manager.state.value
        assertFalse(f.manager.applyLayout(GroupLayout(emptyList(), listOf(b, a)), expectedVersion = start))

        assertEquals(applied, f.manager.state.value)
        assertEquals(start + 1, applied.layoutVersion)
        f.settle()
        assertEquals(listOf("Research"), f.db.tabDao().groups().map { it.title })
        assertEquals(start + 1, f.tabRepo.snapshot()?.appliedLayoutVersion)
        assertTrue(f.manager.applyLayout(GroupLayout(emptyList(), listOf(b, a))))
        assertEquals(start + 2, f.manager.state.value.layoutVersion)
    }

    @Test
    fun aLayoutSkipsTabsThatAreNotOpenAndKeepsTheTabsItDoesNotMention() = runTest {
        val f = fixture()
        val (a, b, c, d) = (1..4).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val private = f.manager.newTab(incognito = true, url = "https://private.example/")
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "Trip", listOf(a, b))), listOf(c, d)))
        val group = f.manager.state.value.groups.single().id
        f.manager.close(listOf(d))

        f.manager.applyLayout(
            GroupLayout(
                groups = listOf(
                    GroupLayout.Group(group, "Osaka trip", listOf(b, private, d, "ghost")),
                    GroupLayout.Group(null, "Nothing open", listOf("ghost-2")),
                ),
                ungrouped = listOf(c),
            ),
        )

        val state = f.manager.state.value
        assertEquals(listOf(TabGroup(group, "Osaka trip", 0)), state.groups)
        assertEquals(listOf(b, a, c, private), state.tabs.map { it.id })
        assertEquals(listOf(group to 0, group to 1, null to 0), listOf(b, a, c).map { f.tab(it)?.groupId to f.tab(it)?.position })
        assertNull(f.tab(private)?.groupId)
        assertEquals(GroupLayout(listOf(GroupLayout.Group(group, "Osaka trip", listOf(b, a))), listOf(c)), state.layout())
        f.settle()
        assertEquals(mapOf(b to (group to 0), a to (group to 1), c to (null to 0)), f.openRows().associate { it.id to (it.groupId to it.position) })
    }

    @Test
    fun aLayoutListingATabOrAGroupTwiceIsRefused() = runTest {
        val f = fixture()
        val (a, b) = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }

        val twiceTab = runCatching { f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "G", listOf(a))), listOf(a, b))) }
        f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(null, "G", listOf(a))), listOf(b)))
        val group = f.manager.state.value.groups.single().id
        val twiceGroup = runCatching {
            f.manager.applyLayout(GroupLayout(listOf(GroupLayout.Group(group, "G", listOf(a)), GroupLayout.Group(group, "H", listOf(b))), emptyList()))
        }

        assertTrue(twiceTab.exceptionOrNull() is IllegalArgumentException)
        assertTrue(twiceGroup.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1L, f.manager.state.value.layoutVersion)
    }

    // The last tab setting

    @Test
    fun theLastTabSettingFollowsNormalTabsOnly() = runTest {
        val f = fixture()
        val n1 = f.manager.newTab(incognito = false, url = "https://one.example/")
        advanceTimeBy(10)
        val n2 = f.manager.newTab(incognito = false, url = "https://two.example/")
        advanceTimeBy(10)
        f.manager.newTab(incognito = true, url = "https://private.example/")
        f.settle()
        assertEquals(n2, f.lastTabId())

        f.manager.close(listOf(n2))
        f.settle()
        assertEquals("the most recent normal tab left", n1, f.lastTabId())

        f.manager.close(listOf(n1))
        f.settle()
        assertNull(f.lastTabId())
    }

    private fun TestScope.fixture(
        db: SkimDatabase = inMemorySkimDatabase(),
        supportsIncognito: Boolean = true,
        idPrefix: String = "tab",
        restoreTimeoutMs: Long = TabManager.RESTORE_TIMEOUT_MS,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    ) = TabManagerFixture(this, folder.root, db, supportsIncognito, restoreTimeoutMs, idPrefix, ioDispatcher).also { fixtures += it }

    private val TabManagerFixture.events get() = manager.events

    private fun activeProfile(f: TabManagerFixture): String =
        (f.incognito.state.value as? State.Active)?.profile ?: throw AssertionError("no active session: ${f.incognito.state.value}")

    /** Every report and command that could write a tab, for a tab that is no longer open. */
    private suspend fun TestScope.reportAndCommandLate(f: TabManagerFixture, id: String) {
        f.events.onTitle(id, "Late title")
        f.events.onPageStarted(id, "https://late.example/")
        f.events.onVisitedHistory(id, "https://late.example/")
        f.events.onPageFinished(id, "https://late.example/")
        f.events.onStateSaved(id, FakeTabEngine.stateOf(id))
        f.manager.bindChat(id, 5)
        f.manager.setDesktopSite(id, true)
        f.manager.select(id)
        f.manager.load(id, "https://later.example/")
        f.manager.returnToNewTabPage(id)
        advanceTimeBy(10_000)
    }

    /** Two saved groups, ids 3 and 7, each with a tab, as an earlier process left them. */
    private suspend fun seedGroups(db: SkimDatabase) {
        val repo = TabRepository(db)
        listOf("seed-1", "seed-2").forEachIndexed { index, id ->
            repo.upsert(PersistedTab(id = id, url = "https://seed$index.example/", title = "", position = 0, createdAt = 1, lastAccessedAt = 1))
        }
        repo.saveLayout(
            listOf(PersistedTabGroup(id = 3, title = "Three", position = 0), PersistedTabGroup(id = 7, title = "Seven", position = 1)),
            listOf(TabPlacement("seed-1", 0, 0), TabPlacement("seed-2", 1, 0)),
        )
    }
}
