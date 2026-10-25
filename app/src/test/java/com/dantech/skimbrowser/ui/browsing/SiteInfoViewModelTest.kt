package com.dantech.skimbrowser.ui.browsing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
import com.dantech.skimbrowser.browser.DefaultProfileSiteData
import com.dantech.skimbrowser.browser.FakeBrowserProfiles
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.domain.fake.FakeSitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Site information for a normal tab and an incognito tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SiteInfoViewModelTest {

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
    fun aSecurePageAsksBeforeSkimAiReadsItUntilTheSwitchIsTurnedOn() = runTest {
        val open = page(PAGE, incognito = false)
        await { open.info.state.value.siteKey == "daylightjournal.com" }

        val state = open.info.state.value
        assertTrue(state.secure)
        assertTrue(state.showSkimAi)
        assertFalse(state.skimAiOn)
        assertTrue(state.showCookies)
        assertTrue(state.permissions.isEmpty())

        open.info.setSkimAi(true)
        await { open.info.state.value.skimAiOn }
        assertEquals(SkimAiState.ALLOWED, open.permissions.get("daylightjournal.com")?.skimAi)
    }

    @Test
    fun skimAiStaysOnWhenTheAppDoesNotAskFirstAndTheSiteIsUnset() = runTest {
        val open = page(PAGE, incognito = false)
        open.fixture.settings.setAskBeforeSending(false)
        await { open.info.state.value.skimAiOn }
    }

    @Test
    fun aPermissionRowFollowsTheStoredAnswer() = runTest {
        val open = page(PAGE, incognito = false)
        open.permissions.setPermission("daylightjournal.com", SitePermissionKind.LOCATION, PermissionState.BLOCKED)
        await { open.info.state.value.permissions.singleOrNull()?.allowed == false }

        open.info.setPermission(SitePermissionKind.LOCATION, allowed = true)
        await { open.info.state.value.permissions.single().allowed }
        assertEquals(PermissionState.ALLOWED, open.permissions.get("WWW.DaylightJournal.com")?.location)
    }

    @Test
    fun anIncognitoTabHidesSkimAiAndClearsTheSessionProfile() = runTest {
        val open = page(PAGE, incognito = true)
        await { open.info.state.value.siteKey == "daylightjournal.com" }
        assertFalse(open.info.state.value.showSkimAi)

        open.fixture.incognito.setDecision("daylightjournal.com", SitePermissionKind.CAMERA, PermissionState.ALLOWED)
        open.info.setPermission(SitePermissionKind.CAMERA, allowed = false)
        await { open.info.state.value.permissions.singleOrNull()?.allowed == false }

        val profile = open.fixture.profiles.profile(
            (open.fixture.incognito.state.value as com.dantech.skimbrowser.browser.IncognitoSession.State.Active).profile,
        )
        open.info.clearCookies()
        await { profile.calls.any { it == "expireCookies:daylightjournal.com" } }
        assertTrue(profile.calls.any { it == "deleteOrigins:daylightjournal.com" })
        assertTrue(open.site.hosts.isEmpty())
    }

    @Test
    fun clearingCookiesOnANormalTabUsesTheDefaultProfile() = runTest {
        val open = page(PAGE, incognito = false)
        await { open.info.state.value.showCookies }
        open.info.clearCookies()
        await { open.site.hosts == listOf("daylightjournal.com") }
    }

    private fun TestScope.page(url: String, incognito: Boolean): Open {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        f.engines.attach(f.engine)
        val id = f.manager.newTab(incognito = incognito, url = url)
        f.engine.openPage(id, url, title = "Daylight")
        runCurrent()
        val permissions = FakeSitePermissionRepository()
        val site = RecordingSiteData()
        val cleaner = BrowsingDataCleaner(
            history = HistoryRepository(f.db.historyDao(), f.db),
            tabRepo = f.tabRepo,
            tabs = f.manager,
            stateStore = f.stateStore,
            thumbnails = f.thumbnails,
            incognito = f.incognito,
            pendingUndo = SnackbarUndo(UndoRegistry(backgroundScope), SnackbarController(UndoRegistry(backgroundScope))),
            siteData = site,
            mainDispatcher = f.main,
        )
        val info = kept {
            SiteInfoViewModel(
                id,
                f.manager,
                permissions,
                f.settings,
                f.incognito,
                cleaner,
                backgroundScope,
            )
        }
        backgroundScope.launch { info.state.collect {} }
        runCurrent()
        return Open(f, permissions, site, info)
    }

    private suspend fun TestScope.await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            runCurrent()
            if (System.nanoTime() > deadline) throw AssertionError("Site information did not update")
            Thread.sleep(15)
        }
    }

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["vm-${viewModelCount++}", T::class]

    private class Open(
        val fixture: TabManagerFixture,
        val permissions: SitePermissionRepository,
        val site: RecordingSiteData,
        val info: SiteInfoViewModel,
    )

    private class RecordingSiteData : DefaultProfileSiteData {
        val hosts = mutableListOf<String>()
        override fun clearHost(host: String) {
            hosts += host
        }
        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val PAGE = "https://www.daylightjournal.com/energy"
    }
}
