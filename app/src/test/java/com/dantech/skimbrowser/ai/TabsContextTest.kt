package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakePageScripts
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The tabs an across-tabs request may include. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TabsContextTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun incognitoNewTabsAskSitesAndTabsWaitingToCloseAreLeftOut() = runTest(main.testDispatcher) {
        val open = open(this)
        open.web("https://ask.example/a")
        val kept = open.web("https://daylightjournal.com/a")
        open.fixture.manager.newTab(incognito = false, url = null)
        open.fixture.manager.newTab(incognito = true, url = "https://secret.example/a")
        val closing = open.web("https://closing.example/a")
        open.sites.setSkimAi("ask.example", SkimAiState.ASK)
        open.fixture.manager.close(listOf(closing))

        val context = collectTabsContext(open.fixture.manager, open.sites, extractLive = false)
        assertEquals(listOf(kept), context.map { it.tab.id })
        assertEquals("1", context.single().ref)
        assertEquals("daylightjournal.com", context.single().host)
    }

    @Test
    fun aLiveTabIsCutToFourThousandCharactersAndAClosedWebViewSendsNoPageText() = runTest(main.testDispatcher) {
        val open = open(this)
        val long = "a".repeat(5_000)
        open.fixture.engine.evaluator = { _, script ->
            if (script == FakePageScripts.EXTRACT) pageJson(long) else null
        }
        val live = open.web("https://daylightjournal.com/live")
        open.fixture.engine.openPage(live, "https://daylightjournal.com/live", "Live title")
        val quiet = open.web("https://tabi-note.jp/quiet")
        open.fixture.engine.openPage(quiet, "https://tabi-note.jp/quiet", "Quiet title")
        open.fixture.engine.evict(quiet)
        advanceUntilIdle()

        val context = collectTabsContext(open.fixture.manager, open.sites, extractLive = true)
        val byId = context.associateBy { it.tab.id }
        assertEquals(4_000, byId.getValue(live).text?.length)
        assertEquals("Live title", byId.getValue(live).title)
        assertNull(byId.getValue(quiet).text)
        assertEquals("Quiet title", byId.getValue(quiet).title)
        assertEquals("tabi-note.jp", byId.getValue(quiet).host)
        assertTrue(open.fixture.engine.callsOf<Call.EnsureWebView>().isEmpty())
    }

    @Test
    fun onlyTheFortyMostRecentTabsAreSent() = runTest(main.testDispatcher) {
        val open = open(this)
        val ids = mutableListOf<String>()
        repeat(41) { index ->
            testScheduler.advanceTimeBy(1)
            ids += open.web("https://site$index.example/p")
        }
        val context = collectTabsContext(open.fixture.manager, open.sites, extractLive = false)
        assertEquals(40, context.size)
        assertEquals(ids.last(), context.first().tab.id)
        assertEquals("1", context.first().ref)
        assertFalse(context.any { it.tab.id == ids.first() })
    }

    private fun open(scope: TestScope): Open {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.manager.attach(fixture.engine)
        val sites = SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db)
        return Open(fixture, sites)
    }

    private class Open(val fixture: TabManagerFixture, val sites: SitePermissionRepository) {
        fun web(url: String) = fixture.manager.newTab(incognito = false, url = url)
    }

    private fun pageJson(text: String): String {
        val body = Json.encodeToString(text)
        return """{"title":"Live title","readerable":true,"blocks":[{"id":"0","tag":"p","text":$body}]}"""
    }
}
