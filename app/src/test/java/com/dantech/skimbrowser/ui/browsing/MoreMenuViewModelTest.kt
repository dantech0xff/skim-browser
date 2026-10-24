package com.dantech.skimbrowser.ui.browsing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.FakeTabEngine
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.fake.FakeBookmarkRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

/** The More menu over a real tab and the bookmark database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MoreMenuViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0

    @After
    fun tearDown() = runBlocking {
        viewModels.clear()
        fixtures.forEach { it.close() }
    }

    @Test
    fun aPageShowsItsTitleHostAndAnEmptyStarUntilItIsBookmarked() = runTest {
        val open = page(PAGE, "Why heat pumps are winning in cold climates")
        await { open.menu.state.value.bookmarkKnown }

        val state = open.menu.state.value
        assertTrue(state.page)
        assertEquals("Why heat pumps are winning in cold climates", state.title)
        assertEquals("daylightjournal.com", state.host)
        assertEquals("D", state.letter)
        assertNull(state.bookmarkedId)
        assertFalse(state.desktopSite)
    }

    @Test
    fun theNewTabPageHidesTheHeader() = runTest {
        val f = fixture()
        val id = f.manager.newTab(incognito = false)
        runCurrent()
        val menu = menuOf(f, id, FakeBookmarkRepository(), snackbar())
        await { menu.state.value.bookmarkKnown }

        assertFalse(menu.state.value.page)
        assertFalse(menu.state.value.tabMissing)
    }

    @Test
    fun addingABookmarkClosesTheMenuAndOffersEdit() = runTest {
        val open = page(PAGE, "Heat pumps")
        await { open.menu.state.value.bookmarkKnown }
        val closed = mutableListOf<Unit>()
        backgroundScope.launch { open.menu.closed.collect { closed += Unit } }

        open.menu.addBookmark()
        await { open.snackbar.current.value != null }

        val message = open.snackbar.current.value
        assertEquals(UiText.Res(R.string.snackbar_bookmark_added), message?.text)
        val id = (message?.action as SnackbarAction.EditBookmark).bookmarkId
        await { open.menu.state.value.bookmarkedId == id && closed.isNotEmpty() }
        assertEquals(PAGE, open.bookmarks.observe(id).first()?.url)
    }

    @Test
    fun aSecondAddDoesNothingWhileThePageIsAlreadyBookmarked() = runTest {
        val open = page(PAGE, "Heat pumps")
        await { open.menu.state.value.bookmarkKnown }
        open.menu.addBookmark()
        await { open.menu.state.value.bookmarkedId != null }

        open.menu.addBookmark()
        runCurrent()

        assertEquals(1, open.bookmarks.stored().size)
    }

    @Test
    fun desktopSiteTogglesAndFindOpensTheBar() = runTest {
        val open = page(PAGE, "Heat pumps")
        val id = open.fixture.manager.state.value.currentTabId!!

        open.menu.toggleDesktop()
        runCurrent()
        assertTrue(open.menu.state.value.desktopSite)
        assertTrue(open.fixture.engine.calls.any { it is FakeTabEngine.Call.SetDesktop })

        open.menu.findInPage()
        runCurrent()
        assertEquals("", open.fixture.manager.runtime(id).value.find?.query)
    }

    private fun TestScope.page(url: String, title: String): Open {
        val f = fixture()
        val id = f.manager.newTab(incognito = false, url = url)
        f.engine.openPage(id, url, title = title)
        runCurrent()
        val bookmarks = FakeBookmarkRepository()
        val snackbar = snackbar()
        return Open(f, bookmarks, snackbar, menuOf(f, id, bookmarks, snackbar))
    }

    private fun TestScope.menuOf(
        f: TabManagerFixture,
        tabId: String,
        bookmarks: FakeBookmarkRepository,
        snackbar: SnackbarController,
    ): MoreMenuViewModel {
        val menu = kept { MoreMenuViewModel(tabId, f.manager, bookmarks, snackbar, backgroundScope) }
        backgroundScope.launch { menu.state.collect {} }
        runCurrent()
        return menu
    }

    private fun TestScope.fixture(): TabManagerFixture {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        f.manager.attach(f.engine)
        return f
    }

    private fun TestScope.snackbar() = SnackbarController(UndoRegistry(backgroundScope))

    private suspend fun TestScope.await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            runCurrent()
            if (System.nanoTime() > deadline) throw AssertionError("The menu did not reach the expected state")
            Thread.sleep(15)
        }
    }

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["vm-${viewModelCount++}", T::class]

    private class Open(
        val fixture: TabManagerFixture,
        val bookmarks: FakeBookmarkRepository,
        val snackbar: SnackbarController,
        val menu: MoreMenuViewModel,
    )

    private companion object {
        const val PAGE = "https://www.daylightjournal.com/energy/heat-pumps-cold"
    }
}
