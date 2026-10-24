package com.dantech.skimbrowser.browser.permissions

import android.Manifest
import android.app.Application
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeActivityHost
import com.dantech.skimbrowser.browser.FakeBrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.withSkimSetup
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The bridge over a real in-memory database (its queries on the test's dispatcher), a real
 * incognito session and Robolectric's runtime permissions; the WebView's callbacks and the
 * Activity's permission prompt are fakes that record every answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SitePermissionBridgeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prompts = FakeActivityHost()
    private val tabs = MutableStateFlow(pages(TAB to PAGE, OTHER_TAB to OTHER_PAGE))
    private val profiles = FakeBrowserProfiles()
    private var db: SkimDatabase? = null
    private lateinit var repository: SitePermissionRepository
    private lateinit var incognito: IncognitoSession

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun aSiteWithoutADecisionWaitsForTheUser() = runTest {
        val bridge = bridge()
        val location = Location()

        bridge.requestLocation(TAB, incognito = false, origin = ORIGIN, callback = location)
        runCurrent()

        assertEquals(PendingSitePermission(TAB, HOST, setOf(SitePermissionKind.LOCATION), incognito = false), bridge.pending.value)
        assertTrue(location.answers.isEmpty())
    }

    @Test
    fun anAllowanceTheUserKeepsIsStoredForTheSiteAndGranted() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        val location = Location()
        bridge.requestLocation(TAB, false, ORIGIN, location)
        runCurrent()

        bridge.allow(bridge.pending.value!!, remember = true)
        runCurrent()

        assertEquals(listOf(Location.Answer(ORIGIN, allow = true, retain = false)), location.answers)
        assertNull(bridge.pending.value)
        assertEquals(PermissionState.ALLOWED, repository.get(HOST)?.location)
        assertTrue("Android already allowed it", prompts.permissionRequests.isEmpty())
    }

    @Test
    fun anAllowanceForThisTimeIsGrantedAndNotKept() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        val location = Location()
        bridge.requestLocation(TAB, false, ORIGIN, location)
        runCurrent()

        bridge.allow(bridge.pending.value!!, remember = false)
        runCurrent()

        assertEquals(listOf(true), location.answers.map { it.allow })
        assertNull(repository.get(HOST))
    }

    @Test
    fun aStoredAllowanceIsGrantedWithoutAsking() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        grantRuntime(Manifest.permission.ACCESS_COARSE_LOCATION)
        val location = Location()

        bridge.requestLocation(TAB, false, "https://www.DaylightJournal.com/", location)
        runCurrent()

        assertEquals(listOf(true), location.answers.map { it.allow })
        assertNull(bridge.pending.value)
        assertTrue(prompts.permissionRequests.isEmpty())
    }

    @Test
    fun aStoredAllowanceWithoutTheRuntimePermissionAsksAndroidFirst() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        val location = Location()

        bridge.requestLocation(TAB, false, ORIGIN, location)
        runCurrent()

        val prompt = prompts.permissionRequests.single()
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), prompt.permissions)
        assertTrue("not granted before Android says so", location.answers.isEmpty())
        assertNull("no dialog for a decided site", bridge.pending.value)
        prompt.onResult(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false, Manifest.permission.ACCESS_COARSE_LOCATION to true))

        assertEquals("approximate location is enough", listOf(true), location.answers.map { it.allow })
    }

    @Test
    fun theUserAllowsButAndroidRefusesSoTheSiteIsDeniedAndNothingIsKept() = runTest {
        val bridge = bridge()
        val location = Location()
        bridge.requestLocation(TAB, false, ORIGIN, location)
        runCurrent()

        bridge.allow(bridge.pending.value!!, remember = true)
        assertNull("the dialog closes while Android asks", bridge.pending.value)
        prompts.permissionRequests.single().onResult(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false, Manifest.permission.ACCESS_COARSE_LOCATION to false))
        runCurrent()

        assertEquals(listOf(false), location.answers.map { it.allow })
        assertNull("ALLOWED is only kept with Android's permission", repository.get(HOST))
    }

    @Test
    fun aBlockedSiteIsDeniedAtOnce() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.CAMERA, PermissionState.BLOCKED)
        grantRuntime(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val media = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        bridge.requestMedia(TAB, false, media)
        runCurrent()

        assertEquals(1, media.denied)
        assertTrue(media.granted.isEmpty())
        assertNull(bridge.pending.value)
    }

    @Test
    fun blockKeepsTheSiteBlocked() = runTest {
        val bridge = bridge()
        val first = Location()
        bridge.requestLocation(TAB, false, ORIGIN, first)
        runCurrent()

        bridge.block(bridge.pending.value!!)
        runCurrent()
        val second = Location()
        bridge.requestLocation(TAB, false, ORIGIN, second)
        runCurrent()

        assertEquals(listOf(false), first.answers.map { it.allow })
        assertEquals(PermissionState.BLOCKED, repository.get(HOST)?.location)
        assertEquals("denied without asking again", listOf(false), second.answers.map { it.allow })
        assertNull(bridge.pending.value)
    }

    @Test
    fun dismissDeniesThisTimeAndKeepsNothing() = runTest {
        val bridge = bridge()
        val first = Location()
        bridge.requestLocation(TAB, false, ORIGIN, first)
        runCurrent()

        bridge.dismiss(bridge.pending.value!!)
        runCurrent()
        val second = Location()
        bridge.requestLocation(TAB, false, ORIGIN, second)
        runCurrent()

        assertEquals(listOf(false), first.answers.map { it.allow })
        assertNull(repository.get(HOST))
        assertEquals("asked again next time", HOST, bridge.pending.value?.host)
        assertTrue(second.answers.isEmpty())
    }

    @Test
    fun incognitoDecisionsStayInTheSessionAndNeverTouchTheRepository() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        val normalRow = repository.get(HOST)
        grantRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        val first = Location()

        bridge.requestLocation(TAB, incognito = true, origin = ORIGIN, callback = first)
        runCurrent()
        assertEquals("the normal tabs' decision is not read", PendingSitePermission(TAB, HOST, setOf(SitePermissionKind.LOCATION), incognito = true), bridge.pending.value)
        bridge.allow(bridge.pending.value!!, remember = true)
        runCurrent()
        val second = Location()
        bridge.requestLocation(TAB, incognito = true, origin = ORIGIN, callback = second)
        runCurrent()

        assertEquals(listOf(true), first.answers.map { it.allow })
        assertEquals("the session's decision answers", listOf(true), second.answers.map { it.allow })
        assertEquals(PermissionState.ALLOWED, incognito.decision(HOST, SitePermissionKind.LOCATION))
        assertEquals("nothing written", normalRow, repository.get(HOST))
        assertEquals(1L, rowCount())
    }

    @Test
    fun anIncognitoBlockIsGoneWithTheSession() = runTest {
        val bridge = bridge()
        val first = Location()
        bridge.requestLocation(TAB, true, ORIGIN, first)
        runCurrent()
        bridge.block(bridge.pending.value!!)
        runCurrent()
        assertEquals(PermissionState.BLOCKED, incognito.decision(HOST, SitePermissionKind.LOCATION))
        assertEquals(0L, rowCount())

        incognito.end(engine = null)
        incognito.profileForNewTab {}
        val second = Location()
        bridge.requestLocation(TAB, true, ORIGIN, second)
        runCurrent()

        assertTrue("a new session asks again", second.answers.isEmpty())
        assertEquals(HOST, bridge.pending.value?.host)
    }

    @Test
    fun cameraAndMicrophoneAreAskedTogetherAndGrantedWhole() = runTest {
        val bridge = bridge()
        val media = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        bridge.requestMedia(TAB, false, media)
        runCurrent()
        assertEquals(setOf(SitePermissionKind.CAMERA, SitePermissionKind.MICROPHONE), bridge.pending.value?.kinds)
        bridge.allow(bridge.pending.value!!, remember = true)
        val prompt = prompts.permissionRequests.single()
        assertEquals(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), prompt.permissions)
        prompt.onResult(mapOf(Manifest.permission.CAMERA to true, Manifest.permission.RECORD_AUDIO to true))
        runCurrent()

        assertEquals(listOf(listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)), media.granted)
        assertEquals(0, media.denied)
        assertEquals(PermissionState.ALLOWED, repository.get(HOST)?.camera)
        assertEquals(PermissionState.ALLOWED, repository.get(HOST)?.microphone)
    }

    @Test
    fun theDialogOnlyAsksAboutWhatTheSiteHasNoDecisionFor() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.CAMERA, PermissionState.ALLOWED)
        val media = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        bridge.requestMedia(TAB, false, media)
        runCurrent()
        assertEquals(setOf(SitePermissionKind.MICROPHONE), bridge.pending.value?.kinds)
        bridge.block(bridge.pending.value!!)
        runCurrent()

        assertEquals(1, media.denied)
        assertEquals("the earlier answer stays", PermissionState.ALLOWED, repository.get(HOST)?.camera)
        assertEquals(PermissionState.BLOCKED, repository.get(HOST)?.microphone)
    }

    @Test
    fun anyResourceButTheCameraAndMicrophoneIsDeniedAtOnce() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.CAMERA)
        repository.setPermission(HOST, SitePermissionKind.CAMERA, PermissionState.ALLOWED)
        val requests = listOf(
            Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID),
            Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_MIDI_SYSEX),
            Media(Uri.parse(ORIGIN)),
        )

        requests.forEach { bridge.requestMedia(TAB, false, it) }
        runCurrent()

        assertEquals(listOf(1, 1, 1), requests.map { it.denied })
        assertTrue(requests.all { it.granted.isEmpty() })
        assertNull(bridge.pending.value)
    }

    @Test
    fun onlySecurePagesMayAskForLocation() = runTest {
        val bridge = bridge()
        val plain = Location()
        val noHost = Location()

        bridge.requestLocation(TAB, false, "http://daylightjournal.com/", plain)
        bridge.requestLocation(TAB, false, "https:", noHost)
        runCurrent()

        assertEquals(listOf(false), plain.answers.map { it.allow })
        assertEquals(listOf(false), noHost.answers.map { it.allow })
        assertNull(bridge.pending.value)
    }

    @Test
    fun theShownTabsRequestsAreAskedOneAtATimeAndOtherTabsWait() = runTest {
        val bridge = bridge()
        val other = Location()
        val location = Location()
        val camera = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        tabs.value = pages(TAB to PAGE, OTHER_TAB to "https://maps.example/route")
        bridge.requestLocation(OTHER_TAB, false, "https://maps.example/", other)
        runCurrent()
        assertNull("a tab that is not shown asks nobody", bridge.pending.value)
        bridge.requestLocation(TAB, false, ORIGIN, location)
        bridge.requestMedia(TAB, false, camera)
        runCurrent()
        assertEquals(setOf(SitePermissionKind.LOCATION), bridge.pending.value?.kinds)
        bridge.dismiss(bridge.pending.value!!)
        assertEquals(setOf(SitePermissionKind.CAMERA), bridge.pending.value?.kinds)
        bridge.dismiss(bridge.pending.value!!)
        assertNull(bridge.pending.value)

        tabs.value = pages(TAB to PAGE, OTHER_TAB to "https://maps.example/route", current = OTHER_TAB)
        runCurrent()

        assertEquals(PendingSitePermission(OTHER_TAB, "maps.example", setOf(SitePermissionKind.LOCATION), false), bridge.pending.value)
        assertTrue(other.answers.isEmpty())
    }

    @Test
    fun aDecisionAlsoAnswersTheSitesOtherWaitingRequests() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        val shown = Location()
        val hidden = Location()
        bridge.requestLocation(OTHER_TAB, false, ORIGIN, hidden)
        bridge.requestLocation(TAB, false, ORIGIN, shown)
        runCurrent()

        bridge.allow(bridge.pending.value!!, remember = true)
        runCurrent()

        assertEquals(listOf(true), shown.answers.map { it.allow })
        assertEquals(listOf(true), hidden.answers.map { it.allow })
    }

    @Test
    fun aRequestTheWebViewWithdrawsNeedsNoAnswer() = runTest {
        val bridge = bridge()
        val location = Location()
        val media = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        bridge.requestLocation(TAB, false, ORIGIN, location)
        bridge.requestMedia(TAB, false, media)
        runCurrent()

        bridge.locationRequestWithdrawn(TAB)
        assertEquals(setOf(SitePermissionKind.MICROPHONE), bridge.pending.value?.kinds)
        bridge.mediaRequestWithdrawn(media)
        bridge.destroy()

        assertNull(bridge.pending.value)
        assertTrue(location.answers.isEmpty())
        assertEquals(0, media.answers)
    }

    @Test
    fun aTabWhoseWebViewGoesAwayHasItsRequestsDenied() = runTest {
        val bridge = bridge()
        val location = Location()
        val other = Location()
        bridge.requestLocation(TAB, false, ORIGIN, location)
        bridge.requestLocation(OTHER_TAB, false, ORIGIN, other)
        runCurrent()

        bridge.cancelTab(TAB)

        assertEquals(listOf(false), location.answers.map { it.allow })
        assertTrue(other.answers.isEmpty())
        assertNull(bridge.pending.value)
    }

    @Test
    fun destroyDeniesEveryWaitingRequestExactlyOnceAndEveryLaterOneAtOnce() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.MICROPHONE, PermissionState.ALLOWED)
        val asking = Location()
        val incognitoAsking = Location()
        val prompting = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val reading = Location()
        bridge.requestMedia(TAB, false, prompting)
        bridge.requestLocation(TAB, false, ORIGIN, asking)
        bridge.requestLocation(OTHER_TAB, true, ORIGIN, incognitoAsking)
        runCurrent()
        bridge.requestLocation(TAB, false, "https://late.example/", reading)
        assertEquals("Android's prompt for the stored allowance is up", 1, prompts.permissionRequests.size)

        bridge.destroy()
        prompts.permissionRequests.single().onResult(mapOf(Manifest.permission.RECORD_AUDIO to true))
        runCurrent()
        val later = Location()
        bridge.requestLocation(TAB, false, ORIGIN, later)
        val seen = PendingSitePermission(TAB, HOST, setOf(SitePermissionKind.LOCATION), incognito = false)
        bridge.allow(seen, remember = true)
        bridge.dismiss(seen)

        assertEquals(1, prompting.denied)
        assertTrue(prompting.granted.isEmpty())
        listOf(asking, incognitoAsking, reading, later).forEach { assertEquals(listOf(false), it.answers.map { answer -> answer.allow }) }
        assertNull(bridge.pending.value)
        assertNull(repository.get("late.example"))
    }

    @Test
    fun aSiteEmbeddedInAnotherPageGetsNoneOfTheDecisionsMadeForIt() = runTest {
        val bridge = bridge()
        repository.setPermission("meet.example", SitePermissionKind.CAMERA, PermissionState.ALLOWED)
        repository.setPermission("meet.example", SitePermissionKind.MICROPHONE, PermissionState.ALLOWED)
        grantRuntime(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        tabs.value = pages(TAB to "https://evil.example/landing")
        val iframe = Media(Uri.parse("https://meet.example/"), PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        bridge.requestMedia(TAB, false, iframe)
        runCurrent()

        assertEquals("nothing granted on the embedded site's word", 0, iframe.answers)
        assertEquals("the dialog names the page the user sees", "evil.example", bridge.pending.value?.host)
    }

    @Test
    fun aFrameAskingForLocationIsAskedAboutAsThePageItIsIn() = runTest {
        val bridge = bridge()
        repository.setPermission("maps.example", SitePermissionKind.LOCATION, PermissionState.ALLOWED)
        grantRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
        tabs.value = pages(TAB to "https://evil.example/landing")
        val frame = Location()

        bridge.requestLocation(TAB, false, "https://maps.example/", frame)
        runCurrent()

        assertTrue("nothing granted on the frame's word", frame.answers.isEmpty())
        assertEquals("evil.example", bridge.pending.value?.host)
    }

    @Test
    fun aFrameWithoutAWebOriginGetsNothingFromThePagesSite() = runTest {
        val bridge = bridge()
        repository.setPermission(HOST, SitePermissionKind.CAMERA, PermissionState.ALLOWED)
        grantRuntime(Manifest.permission.CAMERA)
        val sandboxed = Media(Uri.parse("null"), PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        bridge.requestMedia(TAB, false, sandboxed)
        runCurrent()

        assertEquals(1, sandboxed.denied)
        assertTrue(sandboxed.granted.isEmpty())
        assertNull(bridge.pending.value)
    }

    @Test
    fun anAnswerOnTheTopLevelPageCoversItsFramesOnly() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.CAMERA)
        tabs.value = pages(TAB to "https://evil.example/landing", OTHER_TAB to "https://meet.example/room")
        val iframe = Media(Uri.parse("https://meet.example/"), PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        bridge.requestMedia(TAB, false, iframe)
        runCurrent()

        bridge.allow(bridge.pending.value!!, remember = true)
        runCurrent()

        assertEquals(1, iframe.granted.size)
        assertEquals("kept for the page that asked, by its own site", PermissionState.ALLOWED, repository.get("evil.example")?.camera)
        assertNull("never for the embedded site", repository.get("meet.example"))
    }

    @Test
    fun aTabWithoutAWebPageOfItsOwnGetsNothing() = runTest {
        val bridge = bridge()
        tabs.value = pages(TAB to null, OTHER_TAB to "about:blank")
        val location = Location()
        val camera = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val unknown = Location()

        bridge.requestLocation(TAB, false, ORIGIN, location)
        bridge.requestMedia(OTHER_TAB, false, camera)
        bridge.requestLocation("tab-404", false, ORIGIN, unknown)
        runCurrent()

        assertEquals(listOf(false), location.answers.map { it.allow })
        assertEquals(1, camera.denied)
        assertEquals(listOf(false), unknown.answers.map { it.allow })
        assertNull(bridge.pending.value)
    }

    @Test
    fun locationNeedsAnHttpsPageAsWellAsAnHttpsFrame() = runTest {
        val bridge = bridge()
        tabs.value = pages(TAB to "http://daylightjournal.com/energy")
        val location = Location()

        bridge.requestLocation(TAB, false, ORIGIN, location)
        runCurrent()

        assertEquals(listOf(false), location.answers.map { it.allow })
    }

    @Test
    fun anAnswerForARequestNoLongerShownChangesNothing() = runTest {
        val bridge = bridge()
        grantRuntime(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        val location = Location()
        val camera = Media(Uri.parse(ORIGIN), PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        bridge.requestLocation(TAB, false, ORIGIN, location)
        bridge.requestMedia(TAB, false, camera)
        runCurrent()
        val seen = bridge.pending.value!!

        // The page swaps its request just as the user taps.
        bridge.locationRequestWithdrawn(TAB)
        bridge.allow(seen, remember = true)
        bridge.block(seen)
        bridge.dismiss(seen)
        runCurrent()

        assertEquals(setOf(SitePermissionKind.CAMERA), bridge.pending.value?.kinds)
        assertEquals("the camera was never answered", 0, camera.answers)
        assertNull(repository.get(HOST))
    }

    private fun TestScope.bridge(): SitePermissionBridge {
        val main = StandardTestDispatcher(testScheduler)
        val database = Room.inMemoryDatabaseBuilder(app, SkimDatabase::class.java)
            .withSkimSetup()
            .setQueryCoroutineContext(main)
            .build()
        db = database
        repository = SitePermissionRepository(database.sitePermissionDao(), database)
        incognito = IncognitoSession(profiles, folder.newFolder("shared"), backgroundScope, main)
        incognito.profileForNewTab {}
        return SitePermissionBridge(app, tabs, repository, incognito, prompts, backgroundScope, main)
    }

    private fun pages(vararg pages: Pair<String, String?>, current: String = TAB) = TabsState(
        tabs = pages.mapIndexed { position, (id, url) ->
            Tab(id = id, incognito = false, url = url, title = "", groupId = null, position = position, createdAt = 0, lastAccessedAt = 0)
        },
        currentTabId = current,
    )

    private fun grantRuntime(vararg permissions: String) {
        shadowOf(app).grantPermissions(*permissions)
    }

    private fun rowCount(): Long =
        db!!.openHelper.readableDatabase.query("SELECT COUNT(*) FROM site_permissions").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    /** A geolocation callback that records every answer. */
    private class Location : GeolocationPermissions.Callback {
        data class Answer(val origin: String, val allow: Boolean, val retain: Boolean)

        val answers = mutableListOf<Answer>()

        override fun invoke(origin: String, allow: Boolean, retain: Boolean) {
            answers += Answer(origin, allow, retain)
        }
    }

    /** A camera or microphone request that records every answer. */
    private class Media(private val origin: Uri, private vararg val asked: String) : PermissionRequest() {
        val granted = mutableListOf<List<String>>()
        var denied = 0
            private set

        val answers: Int get() = granted.size + denied

        override fun getOrigin(): Uri = origin

        override fun getResources(): Array<String> = arrayOf(*asked)

        override fun grant(resources: Array<String>) {
            granted += resources.toList()
        }

        override fun deny() {
            denied++
        }
    }

    private companion object {
        const val TAB = "tab-1"
        const val OTHER_TAB = "tab-2"
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps"
        const val OTHER_PAGE = "https://daylightjournal.com/energy/batteries"
        const val HOST = "daylightjournal.com"
        const val ORIGIN = "https://daylightjournal.com/"
    }
}
