package com.dantech.skimbrowser.ui.shell

import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.HiltRouteActivity
import com.dantech.skimbrowser.ReleaseProcessResources
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.ui.navigation.TabsKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The shell over the app's real container on a temporary directory, in an Activity with the browser
 * host of its graph. The view model outlives the shell's composition, so its navigator has to reach
 * the window of the shell that is shown, and that window only.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE_QUALIFIERS)
class SkimAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @get:Rule(order = 2)
    val storage = object : TestWatcher() {
        override fun starting(description: Description) {
            TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        }
    }

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<HiltRouteActivity>()

    @Inject lateinit var resources: ReleaseProcessResources
    @Inject lateinit var downloads: DownloadTracker

    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        if (::resources.isInitialized) resources.release()
        TestStorage.current.set(null)
    }

    @Test
    fun theNavigatorTurnsFlagSecureOnInTheShownWindowBeforeAnyFrameAndNotOnceTheShellIsGone() {
        val model = showApp()
        val window = composeRule.activity.window
        assertFalse(isSecure(window.attributes))

        composeRule.runOnUiThread {
            model.navigator.openTabs(incognito = true)
            // Nothing was composed since: the navigator itself reached the window.
            assertTrue(isSecure(window.attributes))
        }
        composeRule.waitForIdle()
        assertTrue(isSecure(window.attributes))

        shown = false
        composeRule.waitForIdle()
        assertFalse(isSecure(window.attributes))

        composeRule.runOnUiThread {
            model.navigator.openTabs(incognito = true, focusTabId = "elsewhere")
            assertFalse(isSecure(window.attributes))
        }
        assertEquals(TabsKey(incognito = true, focusTabId = "elsewhere"), model.backStack.last())
    }

    /** Shows the shell as MainActivity does, with its view model from the Activity's store, once it has started. */
    private fun showApp(): SkimAppViewModel {
        val activity = composeRule.activity
        val model = ViewModelProvider(activity)[SkimAppViewModel::class.java]
        val host = activity.host
        composeRule.setContent {
            if (shown) {
                CompositionLocalProvider(LocalBrowserHost provides host) {
                    SkimApp(host, downloads, Channel(), model)
                }
            }
        }
        // Start-up resumes on the main looper, which only runs here; its clock stays put, so no start-up timeout fires.
        val deadline = System.nanoTime() + START_WAIT_MS * 1_000_000
        while (!model.state.value.started) {
            check(System.nanoTime() < deadline) { "The app did not start" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        composeRule.waitForIdle()
        return model
    }

    private fun isSecure(attrs: WindowManager.LayoutParams) = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private companion object {
        const val START_WAIT_MS = 10_000L
    }
}
