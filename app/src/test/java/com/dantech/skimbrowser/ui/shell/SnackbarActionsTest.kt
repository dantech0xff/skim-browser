package com.dantech.skimbrowser.ui.shell

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.browser.FakeActivityHost
import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.ui.navigation.BookmarksKey
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.SettingsKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Each snackbar action, carried out on the window's navigator, the undo registry and the Activity. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SnackbarActionsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activityHost = FakeActivityHost()

    // Unconfined runs undo and finalize blocks as soon as the registry launches them.
    private val undo = UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close() }
        activity.pause().stop().destroy()
        Unit
    }

    @Test
    fun switchShowsTheTabThatOpenedBehind() = runTest {
        val app = app(BrowserKey, LinkOptionsKey("tab-1", HitTarget.Link("https://example.com/next", text = null)))
        app.tabs.manager.newTab(incognito = false, url = "https://example.com/")
        val behind = app.tabs.manager.newTab(incognito = false, url = "https://example.com/next", background = true)

        interpret(SnackbarAction.SwitchToTab(behind), app.navigator, undo, activityHost)

        assertEquals(behind, app.tabs.manager.state.value.currentTabId)
        assertEquals(listOf<NavKey>(BrowserKey), app.backStack.toList())
    }

    @Test
    fun undoRunsTheUndoOfItsTokenOnce() = runTest {
        val app = app(BrowserKey)
        val calls = mutableListOf<String>()
        val token = undo.register(UndoKind.BOOKMARK, undo = { calls += "undo" }, finalize = { calls += "finalize" })

        interpret(SnackbarAction.Undo(token), app.navigator, undo, activityHost)
        interpret(SnackbarAction.Undo(token), app.navigator, undo, activityHost)

        assertEquals(listOf("undo"), calls)
        assertEquals(listOf<NavKey>(BrowserKey), app.backStack.toList())
    }

    @Test
    fun editOpensEditBookmarkForTheBookmarkJustAdded() = runTest {
        val app = app(BrowserKey)

        interpret(SnackbarAction.EditBookmark(7), app.navigator, undo, activityHost)

        assertEquals(listOf(BrowserKey, EditBookmarkKey(7)), app.backStack.toList())
    }

    @Test
    fun viewOpensBookmarksAtTheImportedFolder() = runTest {
        val app = app(BrowserKey, SettingsKey)

        interpret(SnackbarAction.ViewImported(5), app.navigator, undo, activityHost)

        assertEquals(listOf(BrowserKey, SettingsKey, BookmarksKey(folderId = 5)), app.backStack.toList())
    }

    @Test
    fun openStartsTheAppForTheFileWithReadAccessToIt() = runTest {
        val app = app(BrowserKey)
        val file = Uri.parse("content://media/external/downloads/12")

        interpret(SnackbarAction.OpenDownload(file, "application/pdf"), app.navigator, undo, activityHost)

        val intent = activityHost.started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(file, intent.data)
        assertEquals("application/pdf", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(listOf<NavKey>(BrowserKey), app.backStack.toList())
    }

    @Test
    fun openWithoutAnAppForTheFileDoesNothing() = runTest {
        val app = app(BrowserKey)
        activityHost.startFailure = ActivityNotFoundException("No app")

        interpret(SnackbarAction.OpenDownload(Uri.parse("content://media/external/downloads/12"), "application/x-unknown"), app.navigator, undo, activityHost)

        assertTrue(activityHost.started.isEmpty())
        assertEquals(listOf<NavKey>(BrowserKey), app.backStack.toList())
    }

    @Test
    fun openThatAnotherAppRefusesDoesNothing() = runTest {
        val app = app(BrowserKey)
        activityHost.startFailure = SecurityException("Not exported")

        interpret(SnackbarAction.OpenDownload(Uri.parse("content://media/external/downloads/12"), "text/plain"), app.navigator, undo, activityHost)

        assertTrue(activityHost.started.isEmpty())
    }

    private class App(val navigator: SkimNavigator, val backStack: NavBackStack<NavKey>, val tabs: TabManagerFixture)

    private fun TestScope.app(vararg keys: NavKey): App {
        val fixture = TabManagerFixture(this, folder.root).also { fixtures += it }
        val backStack = NavBackStack(*keys)
        val navigator = SkimNavigator(
            backStack = backStack,
            tabs = fixture.manager,
            incognito = fixture.incognito,
            secure = WindowSecureController(activity.get().window, setRecentsScreenshotEnabled = null),
            snackbar = SnackbarController(undo),
            aiGate = AiGate(SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db), fixture.settings),
            scope = this,
        )
        return App(navigator, backStack, fixture)
    }
}
