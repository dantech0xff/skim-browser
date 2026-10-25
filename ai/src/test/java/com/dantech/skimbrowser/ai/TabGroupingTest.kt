package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Grouping runs once per set of tabs, and a change while it runs keeps the person's layout. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TabGroupingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()
    private val jobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun fourUngroupedTabsAreGroupedOnceAndUndoRestoresThem() = runTest {
        FakeClaudeServer().use { server ->
            server.enqueue("tab-grouping.sse")
            server.enqueue("tab-grouping.sse")
            val open = open(this, server.baseUrl)
            val ids = open.tabs(4)
            open.grouping.start().join()
            advanceUntilIdle()

            val groups = open.fixture.manager.state.value.groups
            assertEquals(listOf("Energy", "News"), groups.map { it.title })
            assertTrue(groups.all { it.createdBySkim })
            assertEquals(1, server.requestCount)
            val body = server.take().body.readUtf8()
            assertTrue(body.contains("Tab 4"))
            assertTrue(body.contains(NO_PAGE_TEXT))
            assertTrue(!body.contains("PAGE_TEXT_SHOULD_NOT_APPEAR"))

            open.grouping.start().join()
            assertEquals(1, server.requestCount)

            open.grouping.undo()
            advanceUntilIdle()
            assertTrue(open.fixture.manager.state.value.groups.isEmpty())
            assertEquals(ids.toSet(), open.fixture.manager.state.value.tabs.map { it.id }.toSet())
        }
    }

    @Test
    fun fewerThanFourTabsAnAskSiteAClosingTabAndNoNetworkSendNothing() = runTest {
        FakeClaudeServer().use { server ->
            server.enqueue("tab-grouping.sse")
            val open = open(this, server.baseUrl)
            open.tabs(3)
            open.grouping.start().join()
            assertEquals(0, server.requestCount)

            val ask = open.tab("https://ask.example/a", "Ask site")
            open.sites.setSkimAi("ask.example", SkimAiState.ASK)
            val closing = open.tab("https://closing.example/a", "Closing tab")
            open.tabs(3)
            open.fixture.manager.close(listOf(closing))
            open.grouping.start().join()
            advanceUntilIdle()
            val body = server.take().body.readUtf8()
            assertTrue(!body.contains("Ask site"))
            assertTrue(!body.contains("Closing tab"))
            assertEquals(1, server.requestCount)
            assertTrue(open.fixture.manager.state.value.tabs.none { it.id == ask && it.groupId != null })

            val again = open(this, server.baseUrl)
            again.fixture.network.goOffline()
            again.tabs(4)
            again.grouping.start().join()
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun undoDoesNothingOnceTheLayoutHasChanged() = runTest {
        FakeClaudeServer().use { server ->
            server.enqueue("tab-grouping.sse")
            val open = open(this, server.baseUrl)
            val ids = open.tabs(4)
            open.grouping.start().join()
            advanceUntilIdle()
            val version = open.fixture.manager.state.value.layoutVersion
            val moved = GroupLayout(emptyList(), ids.reversed())
            assertTrue(open.fixture.manager.applyLayout(moved, expectedVersion = version))
            open.grouping.undo()
            advanceUntilIdle()
            assertEquals(ids.reversed(), open.fixture.manager.state.value.tabs.map { it.id })
            assertTrue(open.fixture.manager.state.value.groups.isEmpty())
        }
    }

    @Test
    fun leavingTheScreenCancelsTheRequest() = runBlocking {
        FakeClaudeServer().use { server ->
            val slow = server.serveSlowly("tab-grouping.sse", pauseAfter = "text_delta", pauseMs = 2_000)
            slow.use { leaving(slow) }
        }
    }

    private fun leaving(slow: FakeClaudeServer.SlowBody) {
        val open = open(TestScope(), slow.baseUrl, realTime = true)
        open.tabs(4)
        val job = open.grouping.start()
        val started = System.currentTimeMillis()
        while (slow.written() == 0 && System.currentTimeMillis() - started < 8_000) Thread.sleep(50)
        assertTrue(slow.written() > 0)
            job.cancel()
            runBlocking { job.join() }
            assertTrue(open.fixture.manager.state.value.groups.isEmpty())
        assertEquals(0, open.fixture.manager.state.value.layoutVersion)
    }

    @Test
    fun aLayoutChangeWhileGroupingDropsTheResult() = runBlocking {
        FakeClaudeServer().use { server ->
            val slow = server.serveSlowly("tab-grouping.sse", pauseAfter = "text_delta", pauseMs = 5_000)
            slow.use { changed(slow) }
        }
    }

    private fun changed(slow: FakeClaudeServer.SlowBody) {
        val open = open(TestScope(), slow.baseUrl, realTime = true)
        val ids = open.tabs(4)
        val job = open.grouping.start()
        val started = System.currentTimeMillis()
        while (slow.written() == 0 && System.currentTimeMillis() - started < 8_000) Thread.sleep(50)
        assertTrue("the request started", slow.written() > 0)
            assertTrue(open.fixture.manager.applyLayout(GroupLayout(emptyList(), ids.reversed()), expectedVersion = 0))
            runBlocking { job.join() }
            assertTrue(open.fixture.manager.state.value.groups.isEmpty())
        assertEquals(ids.reversed(), open.fixture.manager.state.value.tabs.map { it.id })
        assertEquals(1, open.fixture.manager.state.value.layoutVersion)
    }

    private fun open(scope: TestScope, baseUrl: String, realTime: Boolean = false): Open {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.engines.attach(fixture.engine)
        val sites = SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db)
        val appJob = SupervisorJob().also { jobs += it }
        val dispatcher = if (realTime) Dispatchers.Default else fixture.main
        val grouping = TabGrouping(
            ai = SkimAiService(provider(baseUrl), sites, fixture.settings),
            tabs = fixture.manager,
            tabRepo = fixture.tabRepo,
            sites = sites,
            network = fixture.network,
            appScope = CoroutineScope(appJob + dispatcher),
            mainDispatcher = dispatcher,
            clock = fixture::now,
        )
        return Open(fixture, sites, grouping, scope)
    }

    private class Open(
        val fixture: TabManagerFixture,
        val sites: SitePermissionRepository,
        val grouping: TabGrouping,
        val scope: TestScope,
    ) {
        fun tabs(count: Int): List<String> = List(count) { index ->
            scope.testScheduler.advanceTimeBy(1)
            tab("https://site$index.example/p", "Tab ${index + 1}")
        }

        fun tab(url: String, title: String): String {
            val id = fixture.manager.newTab(incognito = false, url = url)
            fixture.engine.openPage(id, url, title)
            return id
        }
    }
}
