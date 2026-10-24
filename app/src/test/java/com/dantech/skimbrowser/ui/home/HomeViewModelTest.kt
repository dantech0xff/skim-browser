package com.dantech.skimbrowser.ui.home

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.fake.FakeChatRepository
import com.dantech.skimbrowser.domain.fake.FakeShortcutRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Home over the real tab manager, an in-memory database and the real snackbar and undo registry.
 * Undo runs on an app scope of the test's own, so it still restores a shortcut after Home is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Room answers on its own threads; the view model's work still runs on the test's thread.
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val homes = mutableListOf<Home>()

    /** Every view model of the test, cleared after it so none of its work runs on into the next test. */
    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = runBlocking {
        viewModels.clear()
        homes.forEach { home ->
            home.appJob.cancel()
            home.tabs.close()
        }
    }

    // The first Home

    @Test
    fun theFirstHomeHasNoHeroNoContinueAndOnlyTheAddTile() = runTest {
        val home = home()
        home.tabs.manager.newTab(incognito = false)

        val state = home.viewModel().state.first()

        assertEquals(0, state.eligibleTabCount)
        assertFalse(state.showsHero)
        assertTrue(state.shortcuts.isEmpty())
        assertTrue(state.continueChats.isEmpty())
        assertFalse(state.showsContinue)
    }

    @Test
    fun homeTurnsBackIntoTheFirstHomeOnceEverythingIsGone() = runTest {
        val home = home()
        val page = home.tabs.manager.newTab(incognito = false, url = "https://daylightjournal.com/")
        home.tabs.manager.newTab(incognito = false)
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")
        val chat = home.chats.insert(pageChat("daylightjournal.com", "Why heat pumps are winning", updatedAt = NOW - MINUTE))
        val viewModel = home.viewModel()
        val full = viewModel.state.first { it.showsHero && it.showsContinue && it.shortcuts.isNotEmpty() }
        assertEquals(1, full.eligibleTabCount)

        home.tabs.manager.finalizeClose(home.tabs.manager.close(listOf(page)))
        home.shortcuts.remove(full.shortcuts.single().id)
        home.chats.delete(chat)

        val empty = viewModel.state.first { it.shortcuts.isEmpty() && it.continueChats.isEmpty() && it.eligibleTabCount == 0 }
        assertFalse(empty.showsHero)
        assertFalse(empty.showsContinue)
    }

    // Summarize my N open tabs

    @Test
    fun theHeroCountsOnlyNormalTabsShowingAWebPage() = runTest {
        val home = home()
        val tabs = home.tabs.manager
        tabs.newTab(incognito = false, url = "https://daylightjournal.com/energy", background = true)
        tabs.newTab(incognito = false, url = "http://tabi-note.jp/osaka", background = true)
        tabs.newTab(incognito = false, background = true)
        tabs.newTab(incognito = false, url = "about:blank", background = true)
        tabs.newTab(incognito = true, url = "https://private.example/", background = true)
        tabs.newTab(incognito = true, background = true)

        val state = home.viewModel().state.first()

        assertEquals(2, state.eligibleTabCount)
        assertTrue(state.showsHero)
    }

    @Test
    fun theCountFollowsTheTabs() = runTest {
        val home = home()
        val tabs = home.tabs.manager
        val first = tabs.newTab(incognito = false, url = "https://daylightjournal.com/")
        val newTabPage = tabs.newTab(incognito = false)
        val viewModel = home.viewModel()
        assertEquals(1, viewModel.state.first().eligibleTabCount)

        tabs.load(newTabPage, "https://tabi-note.jp/")
        assertEquals(2, viewModel.state.first { it.eligibleTabCount != 1 }.eligibleTabCount)

        tabs.close(listOf(first))
        assertEquals("a tab waiting for Undo is not open", 1, viewModel.state.first { it.eligibleTabCount != 2 }.eligibleTabCount)
    }

    // Shortcuts and Continue with Skim

    @Test
    fun shortcutsComeInTileOrder() = runTest {
        val home = home()
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")
        home.shortcuts.add("GitHub", "https://github.com")
        home.shortcuts.add("YouTube", "https://youtube.com")

        val state = home.viewModel().state.first { it.shortcuts.size == 3 }

        assertEquals(listOf("Wikipedia", "GitHub", "YouTube"), state.shortcuts.map { it.name })
        assertEquals(listOf(0, 1, 2), state.shortcuts.map { it.position })
    }

    @Test
    fun continueShowsTheTwoNewestPageChats() = runTest {
        val home = home()
        val older = home.chats.insert(pageChat("old.example", "An older page", updatedAt = NOW - DAY))
        val daylight = home.chats.insert(
            pageChat("daylightjournal.com", "Why heat pumps are winning in cold climates", updatedAt = NOW - 30 * MINUTE, snippet = "Cold-climate models now hold"),
        )
        val tabi = home.chats.insert(pageChat("tabi-note.jp", "Osaka night buses: a complete guide", updatedAt = NOW - 2 * HOUR, snippet = null))
        home.chats.insert(
            SavedChat(kind = ChatKind.TABS, url = null, host = null, title = "Heat pump research", tabCount = 4, createdAt = NOW, updatedAt = NOW),
        )

        val cards = home.viewModel().state.first { it.continueChats.size == 2 }.continueChats

        assertEquals(
            listOf(
                ContinueCard(
                    chatId = daylight,
                    host = "daylightjournal.com",
                    letter = "D",
                    timeText = UiText.Plural(R.plurals.time_minutes_ago, 30),
                    title = "Why heat pumps are winning in cold climates",
                    snippet = "Cold-climate models now hold",
                ),
                ContinueCard(
                    chatId = tabi,
                    host = "tabi-note.jp",
                    letter = "T",
                    timeText = UiText.Plural(R.plurals.time_hours_ago, 2),
                    title = "Osaka night buses: a complete guide",
                    snippet = "",
                ),
            ),
            cards,
        )
        assertTrue(cards.none { it.chatId == older })
    }

    @Test
    fun theMicShowsOnlyWhenSpeechInputIsAvailable() = runTest {
        val home = home()

        assertTrue(home.viewModel(voiceAvailable = true).state.first().voiceAvailable)
        assertFalse(home.viewModel(voiceAvailable = false).state.first().voiceAvailable)
    }

    @Test
    fun theMicStillFollowsSpeechInputOnceHomeHasItsData() = runTest {
        val home = home()
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")

        val withMic = home.viewModel(voiceAvailable = true).state.first { it.shortcuts.isNotEmpty() }
        val withoutMic = home.viewModel(voiceAvailable = false).state.first { it.shortcuts.isNotEmpty() }

        assertTrue(withMic.voiceAvailable)
        assertFalse(withoutMic.voiceAvailable)
    }

    // Opening a shortcut

    @Test
    fun aShortcutOpensInTheTabOfTheNewTabPage() = runTest {
        val home = home()
        home.tabs.manager.newTab(incognito = false, url = "https://other.example/", background = true)
        val newTabPage = home.tabs.manager.newTab(incognito = false)

        home.viewModel().openShortcut("https://wikipedia.org")

        val tab = checkNotNull(home.tabs.tab(newTabPage))
        assertEquals("https://wikipedia.org", tab.url)
        assertTrue("Back returns to the New tab page", tab.fromNewTabPage)
        assertEquals(newTabPage, home.tabs.manager.state.value.currentTabId)
    }

    @Test
    fun aShortcutDoesNotOpenOverAPageNorInAnIncognitoTab() = runTest {
        val home = home()
        val viewModel = home.viewModel()
        val page = home.tabs.manager.newTab(incognito = false, url = "https://daylightjournal.com/")

        viewModel.openShortcut("https://wikipedia.org")
        assertEquals("a second tap of the same frame", "https://daylightjournal.com/", home.tabs.tab(page)?.url)

        val incognito = home.tabs.manager.newTab(incognito = true)
        viewModel.openShortcut("https://wikipedia.org")
        assertNull(home.tabs.tab(incognito)?.url)
    }

    // Removing a shortcut

    @Test
    fun removeDeletesTheShortcutAtOnceAndOffersUndo() = runTest {
        val home = home()
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")
        home.shortcuts.add("GitHub", "https://github.com")
        val (wikipedia, github) = home.shortcuts.observe().first()
        val viewModel = home.viewModel()

        viewModel.removeShortcut(github.id)

        val shown = home.snackbar.current.filterNotNull().first()
        assertEquals(UiText.Res(R.string.snackbar_shortcut_removed), shown.text)
        val undo = shown.action as SnackbarAction.Undo
        assertEquals(UndoKind.SHORTCUT, home.undo.kindOf(undo.token))
        assertFalse(shown.isError)
        assertEquals(listOf(wikipedia), viewModel.state.first { it.shortcuts.size == 1 }.shortcuts)
    }

    @Test
    fun undoPutsTheShortcutBackInItsPlaceAfterHomeIsGone() = runTest {
        val home = home()
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")
        home.shortcuts.add("GitHub", "https://github.com")
        home.shortcuts.add("YouTube", "https://youtube.com")
        val before = home.shortcuts.observe().first()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { home.newViewModel() } })[HomeViewModel::class]

        viewModel.removeShortcut(before[1].id)
        val shown = home.snackbar.current.filterNotNull().first()
        assertEquals(listOf(before[0], before[2]), home.shortcuts.observe().first { it.size == 2 })
        // Home leaves the composition (another shortcut opened a page): its view model is gone.
        store.clear()
        assertFalse(viewModel.viewModelScope.isActive)
        home.snackbar.onActionClicked(shown.id)

        assertEquals("same ids, same positions", before, home.shortcuts.observe().first { it.size == 3 })
    }

    @Test
    fun aShortcutThatIsAlreadyGoneShowsNoSnackbar() = runTest {
        val home = home()
        home.shortcuts.add("Wikipedia", "https://wikipedia.org")
        val viewModel = home.viewModel()

        viewModel.removeShortcut(id = 999)
        main.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(home.snackbar.current.value)
        assertEquals(1, home.shortcuts.observe().first().size)
    }

    // Helpers

    private class Home(
        val tabs: TabManagerFixture,
        val shortcuts: FakeShortcutRepository,
        val chats: FakeChatRepository,
        val undo: UndoRegistry,
        val snackbar: SnackbarController,
        val appJob: Job,
        private val viewModels: ViewModelStore,
    ) {
        private var count = 0

        /** Home's view model, kept in the test's store. */
        fun viewModel(voiceAvailable: Boolean = true): HomeViewModel =
            ViewModelProvider.create(viewModels, viewModelFactory { initializer { newViewModel(voiceAvailable) } })["home-${count++}", HomeViewModel::class]

        /** Home's view model, for a store of the caller's. */
        fun newViewModel(voiceAvailable: Boolean = true) = HomeViewModel(
            tabs = tabs.manager,
            shortcuts = shortcuts,
            chats = chats,
            snackbar = snackbar,
            undo = undo,
            voiceAvailable = voiceAvailable,
            clock = { NOW },
            zone = { ZoneOffset.UTC },
        )
    }

    private fun TestScope.home(): Home {
        val tabs = TabManagerFixture(this, folder.newFolder())
        val appJob = SupervisorJob()
        val appScope = CoroutineScope(appJob + StandardTestDispatcher(testScheduler))
        val undo = UndoRegistry(appScope)
        return Home(
            tabs = tabs,
            shortcuts = FakeShortcutRepository(),
            chats = FakeChatRepository(),
            undo = undo,
            snackbar = SnackbarController(undo),
            appJob = appJob,
            viewModels = viewModels,
        ).also { homes += it }
    }

    private fun pageChat(host: String, title: String, updatedAt: Long, snippet: String? = "A summary") = SavedChat(
        kind = ChatKind.PAGE,
        url = "https://$host/page",
        host = host,
        title = title,
        snippet = snippet,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private companion object {
        val NOW: Long = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
