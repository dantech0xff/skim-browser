package com.dantech.skimbrowser.browser

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.IncognitoSession.State
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class IncognitoSessionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val profiles = FakeBrowserProfiles()
    private var now = 1_000L
    private lateinit var sharedDir: File

    private val engine = FakeTabEngine(webViewFactory = { WebView(ApplicationProvider.getApplicationContext<Context>()) })

    @Before
    fun setUp() {
        sharedDir = File(folder.root, "shared/incognito")
    }

    @Test
    fun withoutProfilesThereIsNoIncognitoAndNothingFallsBackToTheDefaultProfile() = runTest {
        profiles.isMultiProfileSupported = false
        val session = session()

        assertFalse(session.isSupported)
        val error = runCatching { session.profileForNewTab { } }.exceptionOrNull()
        session.purgeStale()
        session.end(engine)

        assertTrue(error is IllegalStateException)
        assertEquals(State.None, session.state.value)
        assertEquals(0, profiles.callsWhileUnsupported)
        assertTrue(profiles.profile(FakeBrowserProfiles.DEFAULT).calls.isEmpty())
    }

    @Test
    fun theFirstIncognitoTabStartsASessionWithItsOwnProfile() = runTest {
        val session = session()

        val profile = session.profileForNewTab { throw AssertionError("nothing to finalize") }

        assertEquals("incognito-1000", profile)
        assertEquals(State.Active(profile), session.state.value)
        assertEquals(listOf(profile), profiles.created)
        assertEquals(listOf("disableHttpCache"), profiles.profile(profile).calls)
        assertTrue(profiles.profile(FakeBrowserProfiles.DEFAULT).calls.isEmpty())
    }

    @Test
    fun withoutTheHttpCacheManagerTheCacheIsLeftOnUntilTheSessionEnds() = runTest {
        profiles.hasHttpCacheManager = false
        val session = session()

        val profile = session.profileForNewTab { }

        assertFalse(session.hasHttpCacheManager)
        assertTrue(profiles.profile(profile).calls.isEmpty())
        session.end(engine)
        assertTrue(shadowOf(engine.temporaryWebViews.single()).wasClearCacheCalled())
    }

    @Test
    fun moreTabsInAnActiveSessionShareItsProfile() = runTest {
        val session = session()

        val first = session.profileForNewTab { }
        now += 5_000
        val second = session.profileForNewTab { throw AssertionError("nothing to finalize") }

        assertEquals(first, second)
        assertEquals(1, profiles.created.size)
    }

    @Test
    fun closingTheLastTabEndsTheSessionOnlyOnceTheCloseIsFinal() = runTest {
        val session = session()
        val profile = session.profileForNewTab { }

        session.markEnding(CloseToken(7))
        assertEquals(State.Ending(profile, CloseToken(7)), session.state.value)

        session.cancelEnding()
        assertEquals(State.Active(profile), session.state.value)
    }

    @Test
    fun aNewTabWhileTheSessionEndsFinalizesTheCloseAndStartsAnotherProfile() = runTest {
        val session = session()
        val old = session.profileForNewTab { }
        session.markEnding(CloseToken(7))
        val finalized = mutableListOf<CloseToken>()

        val new = session.profileForNewTab { token ->
            finalized += token
            // The tab manager ends the session when it finalizes the token.
            session.end(engine)
        }

        assertEquals(listOf(CloseToken(7)), finalized)
        assertNotEquals(old, new)
        assertEquals(State.Active(new), session.state.value)
        assertEquals(listOf(old, new), profiles.created)
        assertEquals(
            listOf("disableHttpCache", "clearCookies", "clearWebStorage", "clearGeolocationPermissions"),
            profiles.profile(old).calls,
        )
        assertEquals(listOf(FakeTabEngine.Call.TemporaryWebView(old)), engine.callsOf<FakeTabEngine.Call.TemporaryWebView>())
    }

    @Test
    fun theOldSessionEndsEvenWhenFinalizingDidNotEndIt() = runTest {
        val session = session()
        val old = session.profileForNewTab { }
        session.markEnding(CloseToken(3))

        val new = session.profileForNewTab { }

        assertNotEquals(old, new)
        assertEquals(State.Active(new), session.state.value)
        assertTrue("clearCookies" in profiles.profile(old).calls)
        assertTrue("clearWebStorage" in profiles.profile(old).calls)
    }

    @Test
    fun endingClearsEverythingTheSessionLeftInItsOwnProfileOnly() = runTest {
        val session = session()
        val profile = session.profileForNewTab { }
        session.setDecision("maps.example", SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        sharedDir.mkdirs()
        File(sharedDir, "photo.jpg").writeText("shared image")

        session.end(engine)
        runCurrent()

        assertEquals(State.None, session.state.value)
        val webView = engine.temporaryWebViews.single()
        assertTrue(shadowOf(webView).wasClearCacheCalled())
        assertTrue(shadowOf(webView).didClearCacheIncludeDiskFiles())
        assertTrue(shadowOf(webView).wasDestroyCalled())
        assertEquals(listOf(FakeTabEngine.Call.TemporaryWebView(profile)), engine.callsOf<FakeTabEngine.Call.TemporaryWebView>())
        assertEquals(
            listOf("disableHttpCache", "clearCookies", "clearWebStorage", "clearGeolocationPermissions"),
            profiles.profile(profile).calls,
        )
        assertTrue(profiles.profile(FakeBrowserProfiles.DEFAULT).calls.isEmpty())
        assertEquals(PermissionState.UNSET, session.decision("maps.example", SitePermissionKind.LOCATION))
        assertFalse(sharedDir.exists())
    }

    @Test
    fun withoutAnActivityTheCacheWaitsButTheRestIsCleared() = runTest {
        val session = session()
        val profile = session.profileForNewTab { }

        session.end(engine = null)

        assertEquals(State.None, session.state.value)
        assertEquals(
            listOf("disableHttpCache", "clearCookies", "clearWebStorage", "clearGeolocationPermissions"),
            profiles.profile(profile).calls,
        )
    }

    @Test
    fun aFailingStepDoesNotStopTheOthers() = runTest {
        val session = session()
        val profile = session.profileForNewTab { }
        profiles.profile(profile).failing = "clearCookies"
        sharedDir.mkdirs()

        session.end(engine)
        runCurrent()

        assertEquals(State.None, session.state.value)
        assertTrue("clearWebStorage" in profiles.profile(profile).calls)
        assertTrue("clearGeolocationPermissions" in profiles.profile(profile).calls)
        assertFalse(sharedDir.exists())
    }

    @Test
    fun endingWithoutASessionDoesNothing() = runTest {
        val session = session()

        session.end(engine)

        assertTrue(engine.calls.isEmpty())
        assertTrue(profiles.created.isEmpty())
    }

    @Test
    fun permissionAnswersBelongToTheSessionAndItsHost() = runTest {
        val session = session()
        session.setDecision("maps.example", SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        assertEquals("no session, no answer kept", PermissionState.UNSET, session.decision("maps.example", SitePermissionKind.LOCATION))

        session.profileForNewTab { }
        session.setDecision("WWW.Maps.Example", SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        session.setDecision("meet.example", SitePermissionKind.CAMERA, PermissionState.BLOCKED)

        assertEquals(PermissionState.ALLOWED, session.decision("maps.example", SitePermissionKind.LOCATION))
        assertEquals(PermissionState.UNSET, session.decision("maps.example", SitePermissionKind.CAMERA))
        assertEquals(PermissionState.BLOCKED, session.decision("meet.example", SitePermissionKind.CAMERA))

        session.setDecision("maps.example", SitePermissionKind.LOCATION, PermissionState.UNSET)
        assertEquals(PermissionState.UNSET, session.decision("maps.example", SitePermissionKind.LOCATION))

        session.end(engine)
        session.profileForNewTab { }
        assertEquals("a new session starts with no answers", PermissionState.UNSET, session.decision("meet.example", SitePermissionKind.CAMERA))
    }

    @Test
    fun purgeDeletesProfilesLeftByEarlierProcessesButNoOther() = runTest {
        listOf("incognito-10", "incognito-20", "work").forEach { profiles.profiles[it] = FakeBrowserProfiles.Profile(it) }
        profiles.loaded += "incognito-20"
        val session = session()
        val active = session.profileForNewTab { }
        sharedDir.mkdirs()

        session.purgeStale()
        runCurrent()

        assertEquals(listOf("incognito-10"), profiles.deleted)
        assertEquals("only stale profiles are asked for, never the active one", listOf("incognito-10", "incognito-20"), profiles.deleteCalls)
        assertEquals(setOf(FakeBrowserProfiles.DEFAULT, "incognito-20", "work", active), profiles.profiles.keys)
        assertTrue("the active session keeps its shared files", sharedDir.exists())
    }

    @Test
    fun purgeWithoutASessionDeletesTheSharedFilesAndSurvivesFailures() = runTest {
        profiles.profiles["incognito-10"] = FakeBrowserProfiles.Profile("incognito-10")
        profiles.deleteThrows = true
        sharedDir.mkdirs()
        File(sharedDir, "a.jpg").writeText("left over")
        val session = session()

        session.purgeStale()
        runCurrent()

        assertFalse(sharedDir.exists())
        assertEquals(State.None, session.state.value)
    }

    @Test
    fun everySessionGetsANameNoProfileHadEvenWithTheSameClock() = runTest {
        profiles.profiles["incognito-1001"] = FakeBrowserProfiles.Profile("incognito-1001")
        val session = session()

        val first = session.profileForNewTab { }
        session.end(engine)
        val second = session.profileForNewTab { }
        session.end(engine)
        val third = session.profileForNewTab { }

        assertEquals(listOf("incognito-1000", "incognito-1002", "incognito-1003"), listOf(first, second, third))
    }

    private fun TestScope.session() =
        IncognitoSession(profiles, sharedDir, backgroundScope, StandardTestDispatcher(testScheduler), clock = { now })
}
