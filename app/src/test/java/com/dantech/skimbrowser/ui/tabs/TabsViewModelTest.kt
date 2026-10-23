package com.dantech.skimbrowser.ui.tabs

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ai.GroupingBannerState
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.tab.layout
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

/** Tabs group, filter, close and undo through the real tab manager. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TabsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val stores = mutableListOf<ViewModelStore>()
    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() = runBlocking {
        stores.forEach { it.clear() }
        fixtures.forEach { it.close() }
    }

    @Test
    fun sectionsFollowGroupsThenOtherAndSearchHidesEmptyOnes() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        val tabs = fixture.manager
        val heat = tabs.newTab(incognito = false, url = "https://daylightjournal.com/cold")
        val sizing = tabs.newTab(incognito = false, url = "https://example.com/sizing")
        val osaka = tabs.newTab(incognito = false, url = "https://tabi-note.jp/osaka")
        val notes = tabs.newTab(incognito = false, url = "https://kotlinlang.org/notes")
        tabs.applyLayout(
            GroupLayout(
                groups = listOf(
                    GroupLayout.Group(null, "Heat pump research", listOf(heat, sizing)),
                    GroupLayout.Group(null, "Osaka trip", listOf(osaka)),
                ),
                ungrouped = listOf(notes),
            ),
        )
        val model = model(fixture)

        val shown = model.state.first { it.count == 4 && it.sections.size == 3 }
        assertEquals(listOf("Heat pump research", "Osaka trip", null), shown.sections.map { it.group?.title })
        assertEquals(listOf(heat, sizing), shown.sections[0].tabs.map { it.id })
        assertTrue(shown.sections[2].showHeader)
        assertEquals("D", shown.sections[0].tabs[0].letter)

        model.setQuery("osaka")
        val found = model.state.first { it.query == "osaka" }
        assertEquals(listOf("Osaka trip"), found.sections.map { it.group?.title })
        assertEquals(4, found.count)
    }

    @Test
    fun withoutGroupsTheGridHasNoHeader() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        fixture.manager.newTab(incognito = false, url = "https://kotlinlang.org/")
        val model = model(fixture)

        val shown = model.state.first { it.count == 1 }
        assertFalse(shown.sections.single().showHeader)
    }

    @Test
    fun undoRestoresAClosedTabInPlaceAfterTheScreenIsGone() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        val tabs = fixture.manager
        val first = tabs.newTab(incognito = false, url = "https://daylightjournal.com/a")
        val second = tabs.newTab(incognito = false, url = "https://daylightjournal.com/b")
        val third = tabs.newTab(incognito = false, url = "https://daylightjournal.com/c")
        tabs.applyLayout(
            GroupLayout(
                groups = listOf(GroupLayout.Group(null, "Heat", listOf(first, second, third))),
                ungrouped = emptyList(),
            ),
        )
        // The test scope, not backgroundScope: advanceUntilIdle only runs foreground work, and the
        // undo block hops to the tab manager's main dispatcher.
        val undo = UndoRegistry(this)
        val snackbar = SnackbarController(undo)
        val model = model(fixture, undo, snackbar)
        model.state.first { it.sections.singleOrNull()?.tabs?.size == 3 }

        model.close(second)
        val closed = model.state.first { it.count == 2 && it.sections.singleOrNull()?.tabs?.size == 2 }
        assertEquals(listOf(first, third), closed.sections.single().tabs.map { it.id })
        stores.forEach { it.clear() }

        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        undo.undo(token)
        advanceUntilIdle()
        val restored = tabs.state.value.tabs.filter { !it.incognito }.sortedBy { it.position }.map { it.id }
        assertEquals(listOf(first, second, third), restored)
    }

    @Test
    fun undoRestoresAClosedGroup() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        val tabs = fixture.manager
        val first = tabs.newTab(incognito = false, url = "https://daylightjournal.com/a")
        val second = tabs.newTab(incognito = false, url = "https://daylightjournal.com/b")
        tabs.applyLayout(
            GroupLayout(
                groups = listOf(GroupLayout.Group(null, "Heat pump research", listOf(first, second))),
                ungrouped = emptyList(),
            ),
        )
        val undo = UndoRegistry(this)
        val snackbar = SnackbarController(undo)
        val model = model(fixture, undo, snackbar)
        val groupId = model.state.first { it.sections.size == 1 }.sections.single().group!!.id

        model.closeGroup(groupId)
        val message = snackbar.current.value
        assertEquals(UiText.Plural(com.dantech.skimbrowser.R.plurals.snackbar_tabs_closed, 2), message?.text)
        assertTrue(model.state.first { it.empty }.empty)

        undo.undo((message!!.action as SnackbarAction.Undo).token)
        advanceUntilIdle()
        val back = tabs.state.value
        assertEquals("Heat pump research", back.groups.single().title)
        assertEquals(listOf(first, second), back.tabs.map { it.id })
    }

    @Test
    fun ungroupRenameAndMoveGoThroughTheLayout() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        val tabs = fixture.manager
        val first = tabs.newTab(incognito = false, url = "https://daylightjournal.com/a")
        val second = tabs.newTab(incognito = false, url = "https://daylightjournal.com/b")
        val other = tabs.newTab(incognito = false, url = "https://kotlinlang.org/")
        tabs.applyLayout(
            GroupLayout(
                groups = listOf(GroupLayout.Group(null, "Heat", listOf(first, second))),
                ungrouped = listOf(other),
            ),
        )
        val model = model(fixture)
        val groupId = model.state.first { it.sections.size == 2 }.sections.first().group!!.id

        model.rename(groupId, "  ")
        assertEquals("Heat", tabs.state.value.groups.single().title)
        model.rename(groupId, " Research ")
        assertEquals("Research", tabs.state.value.groups.single().title)

        model.move(second, 0)
        assertEquals(listOf(second, first), tabs.state.value.layout().groups.single().tabIds)

        model.ungroup(groupId)
        val layout = tabs.state.value.layout()
        assertTrue(layout.groups.isEmpty())
        assertEquals(listOf(other, second, first), layout.ungrouped)
    }

    @Test
    fun closeAllIncognitoEndsTheSessionAndANewOneLeavesTheOldTabGone() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        val tabs = fixture.manager
        val secret = tabs.newTab(incognito = true, url = "https://example.com/secret")
        val undo = UndoRegistry(this)
        val snackbar = SnackbarController(undo)
        val model = model(fixture, undo, snackbar, incognito = true)
        model.state.first { it.count == 1 }

        model.close(secret)
        assertTrue(fixture.incognito.state.value is IncognitoSession.State.Ending)
        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        assertEquals(UndoKind.TAB, undo.kindOf(token))

        tabs.newTab(incognito = true, url = "https://example.com/next")
        undo.undo(token)
        advanceUntilIdle()
        assertFalse(tabs.state.value.tabs.any { it.id == secret })
        assertTrue(fixture.incognito.state.value is IncognitoSession.State.Active)

        model.closeAll()
        assertTrue(tabs.state.value.tabs.none { it.incognito })
        assertTrue(fixture.incognito.state.value is IncognitoSession.State.None)
    }

    @Test
    fun theGroupingBannerFollowsTheFlowAndStaysOffInIncognito() = runTest(main.testDispatcher) {
        val fixture = fixture(this)
        fixture.manager.newTab(incognito = false, url = "https://example.com/a")
        val banner = MutableStateFlow<GroupingBannerState?>(GroupingBannerState(undoEnabled = true))
        val model = model(fixture, grouping = banner)

        val shown = model.state.first { it.grouping?.undoEnabled == true }
        assertTrue(shown.grouping!!.undoEnabled)
        banner.value = null
        assertNull(model.state.first { it.grouping == null }.grouping)

        val privateBanner = MutableStateFlow<GroupingBannerState?>(GroupingBannerState(undoEnabled = true))
        val hidden = model(fixture, grouping = privateBanner, incognito = true)
        advanceUntilIdle()
        assertNull(hidden.state.value.grouping)
    }

    private fun fixture(scope: TestScope): TabManagerFixture =
        TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }

    private fun model(
        fixture: TabManagerFixture,
        undo: UndoRegistry = UndoRegistry(TestScope().backgroundScope),
        snackbar: SnackbarController = SnackbarController(undo),
        incognito: Boolean = false,
        grouping: Flow<GroupingBannerState?> = flowOf(null),
    ): TabsViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = viewModelFactory {
            initializer {
                TabsViewModel(
                    incognito,
                    fixture.manager,
                    snackbar,
                    undo,
                    grouping = grouping,
                    mainDispatcher = fixture.main,
                )
            }
        }
        return ViewModelProvider(store, factory)[TabsViewModel::class.java]
    }
}
