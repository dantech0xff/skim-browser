package com.dantech.skimbrowser.ui.search

import android.content.Context
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeBookmarkRepository
import com.dantech.skimbrowser.domain.fake.FakeHistoryRepository
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.usecase.MatchBookmarks
import com.dantech.skimbrowser.domain.usecase.MatchHistory
import com.dantech.skimbrowser.search.SuggestionService
import com.dantech.skimbrowser.testing.MainDispatcherRule
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search field over an in-memory database that logs every statement it runs, so a test sees
 * whether history or bookmarks were read at all. The engine is a stand-in that records each request
 * and answers at once, or when the test says so; one test goes through the real service instead.
 * Time is the test's: the 150 ms rest of the text passes only when the test moves the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {

    // Room answers on its own threads; the view model's work still runs on the test's thread.
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var history: FakeHistoryRepository
    private lateinit var bookmarks: FakeBookmarkRepository
    private val engine = MutableStateFlow(SearchEngine.GOOGLE)

    private class EngineRequest(val engine: SearchEngine, val text: String) {
        val reply = CompletableDeferred<List<String>>()

        @Volatile
        var cancelled = false
    }

    private val engineRequests = CopyOnWriteArrayList<EngineRequest>()

    /** How the engine answers at once; null leaves every request waiting for [EngineRequest.reply]. */
    private var engineAnswers: ((String) -> List<String>)? = { text ->
        listOf(text, "$text vs furnace", "$text cost", "$text rebates 2026", "$text water heater")
    }

    private val fakeEngine: suspend (SearchEngine, String) -> List<String> = { chosen, text ->
        val request = EngineRequest(chosen, text).also { engineRequests += it }
        try {
            engineAnswers?.invoke(text) ?: request.reply.await()
        } catch (e: CancellationException) {
            request.cancelled = true
            throw e
        }
    }

    private val servers = mutableListOf<MockWebServer>()
    private val clients = mutableListOf<OkHttpClient>()

    /** Every view model of the test, cleared after it so none of its work runs on into the next test. */
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0

    @Before
    fun setUp() {
        history = FakeHistoryRepository()
        bookmarks = FakeBookmarkRepository()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        clients.forEach { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
        servers.forEach { it.shutdown() }
    }

    // When something is looked up

    @Test
    fun blankTextSendsNothingAndShowsNoRows() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = false)

        type(search, "   ")
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(engineRequests.isEmpty())
        assertFalse(readPages())
        assertEquals(SearchUiState(incognito = false), search.state.value)
    }

    @Test
    fun theTextMustRest150MsAndOnlyItsLastFormIsLookedUp() = runTest {
        val search = search()
        search.open(incognito = false)

        listOf("h", "he", "hea").forEach { text ->
            type(search, text)
            advanceTimeBy(100)
        }
        type(search, "heat")
        advanceTimeBy(149)
        runCurrent()
        assertTrue("not before 150 ms", engineRequests.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("heat"), engineRequests.map { it.text })
    }

    @Test
    fun aNewTextCancelsTheLookupStillRunning() = runTest {
        seedPages()
        engineAnswers = null
        val search = search()
        search.open(incognito = false)
        type(search, "heat")
        passTheRest()
        val first = engineRequests.single()
        // The database part of the lookup is done; only the engine request still runs.
        search.state.first { it.pageMatches.isNotEmpty() }

        type(search, "heat pump")

        assertTrue("cancelled as soon as the text changed", first.cancelled)
        passTheRest()
        val second = engineRequests.last()
        assertEquals("heat pump", second.text)
        second.reply.complete(listOf("heat pump vs furnace"))
        first.reply.complete(listOf("heat vs cold"))
        runCurrent()
        assertEquals(listOf("heat pump vs furnace"), search.state.value.engineSuggestions)
    }

    @Test
    fun theEngineInUseIsTheOneChosenNow() = runTest {
        val search = search()
        search.open(incognito = false)
        engine.value = SearchEngine.ECOSIA
        runCurrent()

        type(search, "heat pump")
        passTheRest()

        assertEquals(SearchEngine.ECOSIA, engineRequests.single().engine)
    }

    // Engine suggestions

    @Test
    fun theEngineSuggestsAtMostThreeWithoutTheTextItself() = runTest {
        engineAnswers = { listOf("heat pump", "heat pump vs furnace", "Heat Pump", "heat pump cost", "heat pump rebates 2026", "heat pump water heater") }
        val search = search()
        search.open(incognito = false)

        type(search, "heat pump ")
        passTheRest()

        assertEquals(listOf("heat pump vs furnace", "heat pump cost", "heat pump rebates 2026"), search.state.value.engineSuggestions)
    }

    @Test
    fun aFailingEngineHidesItsRowsButHistoryStays() = runTest {
        seedPages()
        val failures = listOf<(String) -> List<String>>({ emptyList() }, { throw IOException("offline") })
        failures.forEach { failure ->
            engineAnswers = failure
            val search = search()
            search.open(incognito = false)

            type(search, "heat")
            passTheRest()

            val state = search.state.first { it.pageMatches.isNotEmpty() }
            assertTrue(state.engineSuggestions.isEmpty())
            assertTrue(state.askSkim)
        }
    }

    // History and bookmarks

    @Test
    fun historyComesBeforeBookmarksOneRowPerUrlAtMostThree() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = false)

        type(search, "heat")
        passTheRest()

        assertEquals(
            listOf(
                PageMatch("Heat pump FAQ", "example.com", "https://example.com/heat-pump-faq", isBookmark = false),
                PageMatch("Why heat pumps are winning in cold climates", "daylightjournal.com", DAYLIGHT, isBookmark = false),
                PageMatch("Heat pump rebates 2026", "rebates.example", "https://rebates.example/heat-pump", isBookmark = true),
            ),
            search.state.first { it.pageMatches.isNotEmpty() }.pageMatches,
        )
        assertTrue(readPages())
    }

    @Test
    fun pageRowsShowPlainTitlesAndTheUrlWhenThereIsNoTitle() = runTest {
        history.recordVisit("https://untitled.example/heat", "", at = 1)
        bookmarks.addToMobile("\u202Eheat\u200B pump", "https://reversed.example/")
        val search = search()
        search.open(incognito = false)

        type(search, "heat")
        passTheRest()

        assertEquals(
            listOf("https://untitled.example/heat", "heat pump"),
            search.state.first { it.pageMatches.size == 2 }.pageMatches.map { it.title },
        )
    }

    @Test
    fun aHistoryThatCannotBeReadHidesOnlyItsOwnRows() = runTest {
        seedPages()
        history.failMatches = true
        val search = search()
        search.open(incognito = false)

        type(search, "heat")
        passTheRest()

        val state = search.state.first { it.pageMatches.isNotEmpty() }
        assertTrue(state.pageMatches.all { it.isBookmark })
        assertEquals(listOf(DAYLIGHT, "https://rebates.example/heat-pump"), state.pageMatches.map { it.url })
        assertEquals(listOf("heat vs furnace", "heat cost", "heat rebates 2026"), state.engineSuggestions)
    }

    // Ask Skim and incognito

    @Test
    fun askSkimShowsWhileThereIsTextBeforeAnyLookup() = runTest {
        val search = search()
        search.open(incognito = false)

        type(search, "heat")
        assertTrue(search.state.value.askSkim)
        assertTrue("shown before the text has rested", engineRequests.isEmpty())

        type(search, "")
        assertFalse(search.state.value.askSkim)
    }

    @Test
    fun incognitoNeverReadsHistoryOrBookmarksAndHasNoAskSkimButTheEngineStillSuggests() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = true)

        type(search, "heat")
        passTheRest()

        val state = search.state.value
        assertTrue(state.incognito)
        assertFalse(state.askSkim)
        assertEquals(listOf("heat vs furnace", "heat cost", "heat rebates 2026"), state.engineSuggestions)
        assertTrue(state.pageMatches.isEmpty())
        afterEveryLookupOfTheTextEnded(search)
        assertFalse("history and bookmarks were never read", readPages())
        assertTrue(search.state.value.pageMatches.isEmpty())
    }

    @Test
    fun beforeItsFirstOpeningTheFieldIsTreatedAsIncognito() = runTest {
        seedPages()
        val search = search()

        type(search, "heat")
        passTheRest()

        assertTrue(search.state.value.incognito)
        assertFalse(search.state.value.askSkim)
        afterEveryLookupOfTheTextEnded(search)
        assertFalse(readPages())
    }

    // Openings

    @Test
    fun openedFromAPageTheWholeAddressIsSelected() = runTest {
        val search = search()

        search.open(DAYLIGHT, selectAll = true, incognito = false)

        assertEquals(DAYLIGHT, search.query.text.toString())
        assertEquals(TextRange(0, DAYLIGHT.length), search.query.selection)
    }

    @Test
    fun openedWithSpokenTextTheCursorIsAfterItAndItIsLookedUp() = runTest {
        val search = search()

        search.open("heat pump", incognito = false)

        assertEquals("heat pump", search.query.text.toString())
        assertEquals(TextRange("heat pump".length), search.query.selection)
        assertTrue(search.state.value.askSkim)
        settle()
        passTheRest()
        assertEquals(listOf("heat pump"), engineRequests.map { it.text })
    }

    @Test
    fun aNewOpeningDropsTheRowsOfTheLastOneAtOnce() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = false)
        type(search, "heat")
        passTheRest()
        search.state.first { it.pageMatches.isNotEmpty() && it.engineSuggestions.isNotEmpty() }

        search.open(incognito = true)

        assertEquals(SearchUiState(incognito = true), search.state.value)
        assertEquals("", search.query.text.toString())
    }

    @Test
    fun aLateAnswerOfAnEarlierOpeningIsNotShown() = runTest {
        engineAnswers = null
        val search = search()
        search.open(incognito = false)
        type(search, "heat")
        passTheRest()
        val earlier = engineRequests.single()

        // The same text in a new opening, answered before the lookups hear of the new opening.
        search.open("heat", incognito = true)
        earlier.reply.complete(listOf("heat pump"))
        runCurrent()

        assertTrue(search.state.value.engineSuggestions.isEmpty())
    }

    // Clear text and the arrow

    @Test
    fun clearEmptiesTheFieldAndEveryRowAtOnce() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = false)
        type(search, "heat")
        passTheRest()
        search.state.first { it.pageMatches.isNotEmpty() && it.engineSuggestions.isNotEmpty() }

        search.clear()

        assertEquals("", search.query.text.toString())
        assertEquals(SearchUiState(incognito = false), search.state.value)
    }

    @Test
    fun anAnswerArrivingAfterClearTextIsNotShown() = runTest {
        engineAnswers = null
        val search = search()
        search.open(incognito = false)
        type(search, "heat pump")
        passTheRest()

        search.clear()
        engineRequests.single().reply.complete(listOf("heat pump vs furnace"))
        runCurrent()

        assertTrue(search.state.value.engineSuggestions.isEmpty())
    }

    @Test
    fun theArrowPutsTheSuggestionInTheFieldWithTheCursorAtItsEnd() = runTest {
        seedPages()
        val search = search()
        search.open(incognito = false)
        type(search, "heat")
        passTheRest()
        search.state.first { it.pageMatches.isNotEmpty() && it.engineSuggestions.isNotEmpty() }

        search.editSuggestion("heat pump vs furnace")

        assertEquals("heat pump vs furnace", search.query.text.toString())
        assertEquals(TextRange("heat pump vs furnace".length), search.query.selection)
        assertTrue(search.state.value.askSkim)
        settle()
        passTheRest()
        assertEquals(listOf("heat", "heat pump vs furnace"), engineRequests.map { it.text })
    }

    // What Enter and a suggestion open

    @Test
    fun enterOpensAnAddressOrSearchesWithTheChosenEngine() = runTest {
        val search = search()
        runCurrent()

        assertEquals("https://example.com", search.submitTarget("example.com"))
        assertEquals("https://www.google.com/search?q=heat+pump", search.submitTarget(" heat pump "))
        assertNull(search.submitTarget("   "))

        engine.value = SearchEngine.DUCKDUCKGO
        runCurrent()

        assertEquals("https://duckduckgo.com/?q=heat+pump", search.submitTarget("heat pump"))
        assertEquals("https://duckduckgo.com/?q=javascript%3Aalert%281%29", search.submitTarget("javascript:alert(1)"))
    }

    @Test
    fun untilTheEngineIsReadOnlyAddressesOpen() = runTest {
        val search = search(engineSetting = MutableSharedFlow())
        runCurrent()

        assertEquals("https://example.com", search.submitTarget("example.com"))
        assertNull(search.submitTarget("heat pump"))
        assertNull(search.suggestionTarget("heat pump"))
    }

    @Test
    fun aSuggestionOpensTheEnginesResultsEvenWhenItLooksLikeAnAddress() = runTest {
        val search = search()
        engine.value = SearchEngine.BING
        runCurrent()

        assertEquals("https://www.bing.com/search?q=heat+pump+vs+furnace", search.suggestionTarget("heat pump vs furnace"))
        assertEquals("https://www.bing.com/search?q=example.com", search.suggestionTarget("example.com"))
        assertNull(search.suggestionTarget("  "))
    }

    // With the real service

    @Test
    fun withTheRealServiceTheEnginesFirstThreeSuggestionsShowAndAPageAddressIsNeverSent() = runTest {
        val server = MockWebServer().also { servers += it }
        server.start()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/javascript; charset=UTF-8").setBody(fixture("google")))
        val service = SuggestionService(clientTo(server))
        val search = kept { SearchViewModel(engine, service::suggestions, MatchHistory(history), MatchBookmarks(bookmarks)) }

        search.open("https://bank.example/reset?token=secret", selectAll = true, incognito = false)
        settle()
        passTheRest()
        search.state.first { it.askSkim }
        assertEquals("the address being edited is not sent", 0, server.requestCount)

        type(search, "heat pump")
        passTheRest()

        assertEquals(
            listOf("heat pump vs furnace", "heat pump cost", "heat pump rebates 2026"),
            search.state.first { it.engineSuggestions.isNotEmpty() }.engineSuggestions,
        )
        assertEquals(1, server.requestCount)
    }

    // Helpers

    private fun search(engineSetting: Flow<SearchEngine> = engine) =
        kept { SearchViewModel(engineSetting, fakeEngine, MatchHistory(history), MatchBookmarks(bookmarks)) }

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["view-model-${viewModelCount++}", T::class]

    /** Types [text] into the field as the input field would, then lets the view model see it. */
    private fun TestScope.type(search: SearchViewModel, text: String) {
        search.query.setTextAndPlaceCursorAtEnd(text)
        settle()
    }

    /** What a frame does: field changes reach their observers, and work ready to run runs. */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /** Lets the text rest the 150 ms after which it is looked up. */
    private fun TestScope.passTheRest() {
        advanceTimeBy(150)
        runCurrent()
    }

    /**
     * Types more and waits for the engine rows of the longer text. A new text's lookup starts only
     * once every lookup of the text before has ended, database reads included (they run on Room's
     * threads), so whatever those reads did is logged by then.
     */
    private suspend fun TestScope.afterEveryLookupOfTheTextEnded(search: SearchViewModel) {
        val longer = search.query.text.toString() + " pump"
        type(search, longer)
        passTheRest()
        search.state.first { it.engineSuggestions.firstOrNull() == "$longer vs furnace" }
    }

    /** Visits and bookmarks with "heat" in them, the newest last within each. */
    private suspend fun seedPages() {
        history.recordVisit(DAYLIGHT, "Why heat pumps are winning in cold climates", at = 1_000)
        history.recordVisit("https://example.com/heat-pump-faq", "Heat pump FAQ", at = 2_000)
        bookmarks.addToMobile("Heat pump rebates 2026", "https://rebates.example/heat-pump")
        bookmarks.addToMobile("Why heat pumps are winning in cold climates", DAYLIGHT)
        bookmarks.addToMobile("Osaka night buses: a complete guide", "https://tabi-note.jp/osaka")
        history.clearReads()
        bookmarks.clearReads()
    }

    /** Whether history or bookmarks were searched since the pages were added. */
    private fun readPages(): Boolean = history.matchReads > 0 || bookmarks.matchReads > 0

    /** A client whose requests reach [server] instead of the engine, keeping their path and query. */
    private fun clientTo(server: MockWebServer): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val local = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(local).build())
        }
        .build()
        .also { clients += it }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("suggest/$name.json")) { "Missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val DAYLIGHT = "https://daylightjournal.com/energy/heat-pumps"
    }
}
