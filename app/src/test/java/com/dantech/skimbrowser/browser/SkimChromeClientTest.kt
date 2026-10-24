package com.dantech.skimbrowser.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.RecordingEvents.Event
import com.dantech.skimbrowser.browser.permissions.PendingSitePermission
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SkimChromeClientTest {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = controller.get()
    private val view = WebView(activity)
    private val events = RecordingEvents()
    private val host = FakeActivityHost()
    private val fullscreen = FullscreenController(activity)
    private val popupViews = mutableListOf<String>()
    private var popupView: WebView? = WebView(activity)
    private val parsed = mutableListOf<Pair<Int, Intent?>>()
    private var shown = true
    private var document = 1L
    private val dialogs = RecordingJsResults()
    private val coroutines = TestScope()
    private val db = inMemorySkimDatabase()
    private val sitePermissions = SitePermissionBridge(
        context = activity,
        tabs = MutableStateFlow(
            TabsState(
                tabs = listOf(Tab(id = TAB, incognito = true, url = "https://daylightjournal.com/energy", title = "", groupId = null, position = 0, createdAt = 0, lastAccessedAt = 0)),
                currentTabId = TAB,
            ),
        ),
        repository = SitePermissionRepository(db.sitePermissionDao(), db),
        incognitoSession = IncognitoSession(FakeBrowserProfiles(), File(activity.cacheDir, "shared"), coroutines.backgroundScope),
        activityHost = host,
        scope = coroutines.backgroundScope,
        mainDispatcher = StandardTestDispatcher(coroutines.testScheduler),
    )
    private val client = SkimChromeClient(
        tabId = TAB,
        incognito = false,
        events = events,
        activityHost = host,
        fullscreen = fullscreen,
        sitePermissions = sitePermissions,
        obtainForPopup = { popupTabId ->
            popupViews += popupTabId
            popupView
        },
        isShown = { shown },
        documentId = { document },
        parseFileChooserResult = { resultCode, data ->
            parsed += resultCode to data
            if (resultCode == Activity.RESULT_OK) arrayOf(PICKED) else null
        },
    )

    @After
    fun tearDown() {
        coroutines.backgroundScope.cancel()
        db.close()
        controller.pause().stop().destroy()
    }

    @Test
    fun progressAndTitleReachTheTab() {
        client.onProgressChanged(view, 40)
        client.onReceivedTitle(view, "Heat pumps in the cold")
        client.onReceivedTitle(view, null)

        assertEquals(
            listOf(Event.Progress(TAB, 40), Event.Title(TAB, "Heat pumps in the cold"), Event.Title(TAB, "")),
            events.events,
        )
    }

    @Test
    fun aPickedFileReachesThePageOnce() {
        val answers = Answers()

        assertTrue(client.onShowFileChooser(view, answers.callback, params()))
        val request = host.fileRequests.single()
        assertSame(PICKER, request.intent)
        assertTrue("the page waits", answers.values.isEmpty())

        val data = Intent()
        request.onResult(Activity.RESULT_OK, data)
        request.onResult(Activity.RESULT_OK, data)

        assertEquals(listOf(listOf(PICKED)), answers.values)
        assertEquals("read through the platform's parser", listOf(Activity.RESULT_OK to data), parsed)
    }

    @Test
    fun aCancelledPickerAnswersThePageWithNothing() {
        val answers = Answers()
        client.onShowFileChooser(view, answers.callback, params())

        host.fileRequests.single().onResult(Activity.RESULT_CANCELED, null)

        assertEquals(listOf<List<Uri>?>(null), answers.values)
    }

    @Test
    fun aSecondInputAnswersTheFirstWithNothingBeforeItWaits() {
        val first = Answers()
        val second = Answers()
        client.onShowFileChooser(view, first.callback, params())

        client.onShowFileChooser(view, second.callback, params())

        assertEquals("the first input is answered at once", listOf<List<Uri>?>(null), first.values)
        assertTrue(second.values.isEmpty())
        host.fileRequests[0].onResult(Activity.RESULT_OK, Intent())
        assertEquals("a late result of the first picker reaches no one", listOf<List<Uri>?>(null), first.values)
        assertTrue(second.values.isEmpty())
        host.fileRequests[1].onResult(Activity.RESULT_OK, Intent())
        assertEquals(listOf(listOf(PICKED)), second.values)
    }

    @Test
    fun anInputWaitingWhenTheWebViewGoesAwayIsAnsweredWithNothing() {
        val answers = Answers()
        client.onShowFileChooser(view, answers.callback, params())

        client.detach()
        host.fileRequests.single().onResult(Activity.RESULT_OK, Intent())

        assertEquals(listOf<List<Uri>?>(null), answers.values)
    }

    @Test
    fun anInputThatCannotMakeAPickerIsAnsweredAtOnce() {
        val answers = Answers()

        assertTrue(client.onShowFileChooser(view, answers.callback, params(failing = true)))

        assertEquals(listOf<List<Uri>?>(null), answers.values)
        assertTrue(host.fileRequests.isEmpty())
    }

    @Test
    fun aResultThatCannotBeReadAnswersThePageWithNothing() {
        val answers = Answers()
        val failing = SkimChromeClient(
            TAB, false, events, host, fullscreen, sitePermissions, { null },
            isShown = { true }, documentId = { 1 }, parseFileChooserResult = { _, _ -> throw IllegalArgumentException("bad") },
        )
        failing.onShowFileChooser(view, answers.callback, params())

        host.fileRequests.single().onResult(Activity.RESULT_OK, Intent())

        assertEquals(listOf<List<Uri>?>(null), answers.values)
    }

    @Test
    fun aDetachedClientAnswersANewInputAtOnce() {
        client.detach()
        val answers = Answers()

        assertTrue(client.onShowFileChooser(view, answers.callback, params()))

        assertEquals(listOf<List<Uri>?>(null), answers.values)
        assertTrue(host.fileRequests.isEmpty())
    }

    @Test
    fun aWindowOpensOnlyAfterAUserGesture() {
        val (message, transport) = windowMessage()

        assertFalse(client.onCreateWindow(view, false, false, message))

        assertTrue("no tab is made for it", events.events.isEmpty())
        assertTrue(popupViews.isEmpty())
        assertNull(transport.webView)
    }

    @Test
    fun aWindowAfterAGestureGetsTheNewTabsWebView() {
        val (message, transport) = windowMessage()
        val delivered = mutableListOf<Message>()
        message.target = Handler(Looper.getMainLooper()) { delivered += Message.obtain(it); true }

        assertTrue(client.onCreateWindow(view, false, true, message))

        assertEquals(listOf(Event.PopupRequested(TAB)), events.events)
        assertEquals(listOf("popup-tab"), popupViews)
        assertSame(popupView, transport.webView)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the WebView gets its window", 1, delivered.size)
    }

    @Test
    fun aRefusedWindowMakesNoWebView() {
        events.popupTabId = null
        val (message, transport) = windowMessage()

        assertFalse(client.onCreateWindow(view, false, true, message))

        assertTrue(popupViews.isEmpty())
        assertNull(transport.webView)
    }

    @Test
    fun aWindowWithoutAWebViewClosesItsTabAgain() {
        popupView = null
        val (message, transport) = windowMessage()

        assertFalse(client.onCreateWindow(view, false, true, message))

        assertEquals(listOf(Event.PopupRequested(TAB), Event.PopupClosed("popup-tab")), events.events)
        assertNull(transport.webView)
    }

    @Test
    fun aPageClosingItsWindowClosesItsTab() {
        client.onCloseWindow(view)

        assertEquals(listOf(Event.PopupClosed(TAB)), events.events)
    }

    @Test
    fun fullScreenContentShowsAndThePageCanLeaveIt() {
        val callback = CountingCallback()

        client.onShowCustomView(View(activity), callback)
        assertTrue(fullscreen.isFullscreen.value)

        client.onHideCustomView()
        assertFalse(fullscreen.isFullscreen.value)
        assertEquals(1, callback.hidden)
    }

    @Test
    fun aDetachedClientRefusesFullScreenAndReportsNothing() {
        client.detach()
        val callback = CountingCallback()

        client.onShowCustomView(View(activity), callback)
        client.onProgressChanged(view, 100)
        client.onReceivedTitle(view, "Title")
        client.onCloseWindow(view)

        assertFalse(fullscreen.isFullscreen.value)
        assertEquals(1, callback.hidden)
        assertTrue(events.events.isEmpty())
        assertFalse(client.onCreateWindow(view, false, true, windowMessage().first))
    }

    @Test
    fun aLocationRequestGoesToTheSitePermissionsWithItsTabAndMode() {
        val incognitoClient = incognitoClient()
        val location = Location()

        incognitoClient.onGeolocationPermissionsShowPrompt(ORIGIN, location)

        assertEquals(PendingSitePermission(TAB, HOST, setOf(SitePermissionKind.LOCATION), incognito = true), sitePermissions.pending.value)
        assertTrue(location.answers.isEmpty())
    }

    @Test
    fun aCameraRequestGoesToTheSitePermissionsAndAnythingElseIsDenied() {
        val incognitoClient = incognitoClient()
        val camera = Media(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val midi = Media(PermissionRequest.RESOURCE_MIDI_SYSEX)

        incognitoClient.onPermissionRequest(camera)
        client.onPermissionRequest(midi)

        assertEquals(setOf(SitePermissionKind.CAMERA), sitePermissions.pending.value?.kinds)
        assertEquals(0, camera.answers)
        assertEquals(1, midi.denied)
    }

    @Test
    fun requestsTheWebViewWithdrawsLeaveTheSitePermissions() {
        val incognitoClient = incognitoClient()
        val location = Location()
        val camera = Media(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        incognitoClient.onGeolocationPermissionsShowPrompt(ORIGIN, location)
        incognitoClient.onPermissionRequest(camera)

        incognitoClient.onGeolocationPermissionsHidePrompt()
        assertEquals(setOf(SitePermissionKind.CAMERA), sitePermissions.pending.value?.kinds)
        incognitoClient.onPermissionRequestCanceled(camera)

        assertNull(sitePermissions.pending.value)
        assertTrue(location.answers.isEmpty())
        assertEquals(0, camera.answers)
    }

    @Test
    fun theTabsWaitingRequestsAreDeniedWhenItsWebViewGoesAway() {
        val incognitoClient = incognitoClient()
        val location = Location()
        incognitoClient.onGeolocationPermissionsShowPrompt(ORIGIN, location)

        incognitoClient.detach()

        assertEquals(listOf(false), location.answers)
        assertNull(sitePermissions.pending.value)
    }

    @Test
    fun aDetachedClientDeniesPermissionRequestsAtOnce() {
        client.detach()
        val location = Location()
        val camera = Media(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

        client.onGeolocationPermissionsShowPrompt(ORIGIN, location)
        client.onPermissionRequest(camera)

        assertEquals(listOf(false), location.answers)
        assertEquals(1, camera.denied)
        assertNull(sitePermissions.pending.value)
    }

    @Test
    fun aPageOnScreenShowsItsDialogsThreeTimesPerDocument() {
        assertFalse(client.onJsAlert(view, PAGE, "one", dialogs.alert()))
        assertFalse(client.onJsConfirm(view, PAGE, "two", dialogs.alert()))
        assertFalse(client.onJsPrompt(view, PAGE, "three", "", dialogs.prompt()))
        assertTrue("the WebView showed those itself", dialogs.answers.isEmpty())

        assertTrue(client.onJsAlert(view, PAGE, "four", dialogs.alert()))
        assertTrue(client.onJsPrompt(view, PAGE, "five", "", dialogs.prompt()))
        assertEquals(listOf("cancel", "cancel"), dialogs.answers)

        document = 2
        assertFalse("a new document may show dialogs again", client.onJsAlert(view, PAGE, "again", dialogs.alert()))
    }

    @Test
    fun aPageThatIsNotOnScreenGetsNoDialog() {
        shown = false

        assertTrue(client.onJsAlert(view, PAGE, "Your session expired", dialogs.alert()))
        assertTrue(client.onJsConfirm(view, PAGE, "Continue?", dialogs.alert()))
        assertTrue(client.onJsPrompt(view, PAGE, "Password", "", dialogs.prompt()))
        assertTrue("a page in the background still navigates", client.onJsBeforeUnload(view, PAGE, "Leave?", dialogs.alert()))

        assertEquals(listOf("cancel", "cancel", "cancel", "confirm"), dialogs.answers)
    }

    @Test
    fun aLeavePageDialogPastTheLimitLetsTheUserLeave() {
        repeat(3) { client.onJsAlert(view, PAGE, "nag", dialogs.alert()) }

        assertTrue(client.onJsBeforeUnload(view, PAGE, "Leave?", dialogs.alert()))
        assertEquals(listOf("confirm"), dialogs.answers)
    }

    @Test
    fun aDetachedClientAnswersDialogsItself() {
        client.detach()

        assertTrue(client.onJsAlert(view, PAGE, "one", dialogs.alert()))
        assertEquals(listOf("cancel"), dialogs.answers)
    }

    @Test
    fun anIncognitoPagesConsoleStaysOutOfTheSystemLog() {
        val message = ConsoleMessage("signed in as ana", "https://daylightjournal.com/app.js", 3, ConsoleMessage.MessageLevel.LOG)

        assertTrue(incognitoClient().onConsoleMessage(message))
        assertFalse("a normal tab keeps the WebView's logging", client.onConsoleMessage(message))
    }

    private fun incognitoClient() = SkimChromeClient(TAB, true, events, host, fullscreen, sitePermissions, { null }, { shown }, { document })

    private fun windowMessage(): Pair<Message, WebView.WebViewTransport> {
        val transport = view.WebViewTransport()
        return Message.obtain(null, 0, transport) to transport
    }

    private fun params(failing: Boolean = false) = object : WebChromeClient.FileChooserParams() {
        override fun getMode(): Int = MODE_OPEN

        override fun getAcceptTypes(): Array<String> = arrayOf("image/*")

        override fun isCaptureEnabled(): Boolean = false

        override fun getTitle(): CharSequence? = null

        override fun getFilenameHint(): String? = null

        override fun createIntent(): Intent = if (failing) throw IllegalStateException("no picker") else PICKER
    }

    /** What a page's file input was answered, each time it was. */
    private class Answers {
        val values = mutableListOf<List<Uri>?>()
        val callback = ValueCallback<Array<Uri>> { value -> values += value?.toList() }
    }

    /** A geolocation callback that records whether each answer allowed. */
    private class Location : GeolocationPermissions.Callback {
        val answers = mutableListOf<Boolean>()

        override fun invoke(origin: String, allow: Boolean, retain: Boolean) {
            answers += allow
        }
    }

    private class Media(private vararg val asked: String) : PermissionRequest() {
        var granted = 0
            private set
        var denied = 0
            private set

        val answers: Int get() = granted + denied

        override fun getOrigin(): Uri = Uri.parse(ORIGIN)

        override fun getResources(): Array<String> = arrayOf(*asked)

        override fun grant(resources: Array<String>) {
            granted++
        }

        override fun deny() {
            denied++
        }
    }

    private class CountingCallback : WebChromeClient.CustomViewCallback {
        var hidden = 0

        override fun onCustomViewHidden() {
            hidden++
        }
    }

    private companion object {
        const val TAB = "tab-1"
        const val HOST = "daylightjournal.com"
        const val ORIGIN = "https://daylightjournal.com/"
        const val PAGE = "https://daylightjournal.com/energy"
        val PICKER = Intent(Intent.ACTION_GET_CONTENT)
        val PICKED: Uri = Uri.parse("content://media/picked/1")
    }
}
