package com.dantech.skimbrowser.ui.reader

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakePageScripts
import com.dantech.skimbrowser.browser.FakeTabEngine
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Reader view waits for the page, then shows the article or leaves. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val stores = mutableListOf<ViewModelStore>()
    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun aLoadedArticleDropsTheRepeatedTitleAndKeepsTheSavedTextSize() = runTest(main.testDispatcher) {
        val fixture = readyPage(this, readerable = true)
        val model = model(fixture, this)

        assertTrue(model.state.value is ReaderUiState.Loading)
        val shown = model.state.first { it is ReaderUiState.Ready } as ReaderUiState.Ready
        assertEquals("daylightjournal.com", shown.article.host)
        assertEquals(TITLE, shown.article.title)
        assertEquals("Marta Lindqvist", shown.article.byline)
        assertEquals(listOf("The numbers."), shown.article.blocks.map { it.text })
        assertEquals(100, shown.scalePercent)
        assertEquals(ReaderFont.SANS_SERIF, shown.font)

        model.setScale(110)
        model.setFont(ReaderFont.SERIF)
        val saved = model.state.first { it is ReaderUiState.Ready && it.scalePercent == 110 && it.font == ReaderFont.SERIF }
        assertTrue(saved is ReaderUiState.Ready)
    }

    @Test
    fun aPageThatIsNotAnArticleIsUnavailableOnlyAfterItHasLoaded() = runTest(main.testDispatcher) {
        val fixture = readyPage(this, readerable = false)
        val model = model(fixture, this)

        assertTrue(model.state.value is ReaderUiState.Loading)
        assertTrue(model.state.first { it is ReaderUiState.Unavailable } is ReaderUiState.Unavailable)
    }

    @Test
    fun aSlowPageStaysLoadingUntilItIsReady() = runTest(main.testDispatcher) {
        val fixture = TabManagerFixture(this, folder.newFolder()).also { fixtures += it }
        fixture.manager.attach(fixture.engine)
        fixture.engine.onWebViewCreated = { tabId ->
            backgroundScope.launch(fixture.main) {
                delay(1_000)
                fixture.engine.openPage(tabId, URL, TITLE)
            }
        }
        fixture.engine.evaluator = { _, script -> if (script == FakePageScripts.EXTRACT) json(readerable = true) else null }
        val id = fixture.manager.newTab(incognito = false, url = URL)
        val model = model(fixture, this, id)

        assertTrue(model.state.value is ReaderUiState.Loading)
        advanceTimeBy(400)
        assertTrue(model.state.value is ReaderUiState.Loading)
        assertTrue(model.state.first { it is ReaderUiState.Ready } is ReaderUiState.Ready)
    }

    @Test
    fun backAndForwardMoveThatTab() = runTest(main.testDispatcher) {
        val fixture = readyPage(this, readerable = true)
        val model = model(fixture, this)

        model.goBack()
        model.goForward()

        assertTrue(fixture.engine.calls.contains(FakeTabEngine.Call.GoBack("tab-1")))
        assertTrue(fixture.engine.calls.contains(FakeTabEngine.Call.GoForward("tab-1")))
    }

    private fun readyPage(scope: TestScope, readerable: Boolean): TabManagerFixture {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.manager.attach(fixture.engine)
        val id = fixture.manager.newTab(incognito = false, url = URL)
        fixture.engine.openPage(id, URL, TITLE)
        fixture.engine.evaluator = { _, script -> if (script == FakePageScripts.EXTRACT) json(readerable) else null }
        return fixture
    }

    private fun model(fixture: TabManagerFixture, scope: TestScope, tabId: String = "tab-1"): ReaderViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = viewModelFactory {
            initializer {
                val settings = FakeSettingsRepository()
                ReaderViewModel(
                    tabId,
                    fixture.manager,
                    settings,
                    scope,
                )
            }
        }
        return ViewModelProvider(store, factory)[ReaderViewModel::class.java]
    }

    private fun json(readerable: Boolean) =
        """{"title":"$TITLE","byline":"Marta Lindqvist","readerable":$readerable,"blocks":[{"id":"1","tag":"h1","text":"$TITLE"},{"id":"2","tag":"p","text":"The numbers."}]}"""

    private companion object {
        const val URL = "https://daylightjournal.com/cold"
        const val TITLE = "Why heat pumps are winning in cold climates"
    }
}
