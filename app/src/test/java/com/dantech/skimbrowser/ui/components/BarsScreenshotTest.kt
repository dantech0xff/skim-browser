package com.dantech.skimbrowser.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The address bar and the collapsed search bar at the top of their boards. Each golden is the top
 * 120 dp of a 412 dp wide screen, status bar included, so it lines up with the same strip of the
 * board PNG (04 starts 112 dp down, under the Skim mark).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class BarsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun restoreAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun addressBarOfASecurePage() = captureTop("address-bar-05", page = { MaterialTheme.colorScheme.surfaceContainerLowest }) {
        PageAddressBar("daylightjournal.com", SiteIndicator.Secure, loadProgress = null)
    }

    @Test
    fun addressBarWhileLoading() = captureTop("address-bar-05-loading", page = { MaterialTheme.colorScheme.surfaceContainerLowest }) {
        PageAddressBar("daylightjournal.com", SiteIndicator.Secure, loadProgress = 0.58f)
    }

    @Test
    fun addressBarWhileLoadingWithAnimationsRemoved() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        captureTop("address-bar-05-loading-reduced-motion", page = { MaterialTheme.colorScheme.surfaceContainerLowest }) {
            PageAddressBar("daylightjournal.com", SiteIndicator.Secure, loadProgress = 0.58f)
        }
    }

    @Test
    fun addressBarInTheDarkTheme() = captureTop("address-bar-05-dark", dark = true, page = { MaterialTheme.colorScheme.surfaceContainerLowest }) {
        PageAddressBar("daylightjournal.com", SiteIndicator.Secure, loadProgress = null)
    }

    @Test
    fun addressBarWhileLoadingInTheDarkTheme() = captureTop(
        "address-bar-05-loading-dark",
        dark = true,
        page = { MaterialTheme.colorScheme.surfaceContainerLowest },
    ) {
        PageAddressBar("daylightjournal.com", SiteIndicator.Secure, loadProgress = 0.58f)
    }

    @Test
    fun addressBarOfAPageWithoutHttps() = captureTop("address-bar-not-secure", page = { MaterialTheme.colorScheme.surfaceContainerLowest }) {
        PageAddressBar("example.com/plain", SiteIndicator.NotSecure, loadProgress = null, readerAvailable = false)
    }

    @Test
    fun addressBarWhenThePageFailedToLoad() {
        captureTop("address-bar-11", page = { MaterialTheme.colorScheme.surface }) {
            // Reader view is never offered on a page that failed to load.
            PageAddressBar("nightbus-kansai.jp/timetable", SiteIndicator.Error, loadProgress = null, readerAvailable = true)
        }
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.cd_reader_view)).assertCountEquals(0)
    }

    @Test
    fun searchBarOnHome() = captureSearchBarOfHome("search-bar-04", dark = false)

    @Test
    fun searchBarOnHomeInTheDarkTheme() = captureSearchBarOfHome("search-bar-04-dark", dark = true)

    @Test
    fun searchBarOfTheIncognitoNewTab() = captureTop("search-bar-09c", incognito = true, page = { MaterialTheme.colorScheme.surface }) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SearchBarField(incognito = true, onClick = {}, onVoice = {})
        }
    }

    @Composable
    private fun PageAddressBar(
        address: String,
        indicator: SiteIndicator,
        loadProgress: Float?,
        readerAvailable: Boolean = true,
    ) {
        AddressBar(
            address = address,
            indicator = indicator,
            readerAvailable = readerAvailable,
            loadProgress = loadProgress,
            onSiteInfo = {},
            onEditAddress = {},
            onReader = {},
        )
    }

    /** The status bar on surface, [bar] under it, and the page (a later sibling) below. */
    private fun captureTop(
        name: String,
        dark: Boolean = false,
        incognito: Boolean = false,
        page: @Composable () -> Color,
        bar: @Composable () -> Unit,
    ) {
        composeRule.setSkimContent(dark = dark, incognito = incognito) {
            Column(Modifier.fillMaxWidth().height(STRIP_HEIGHT.dp).testTag(FRAME)) {
                Spacer(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding())
                bar()
                Spacer(Modifier.fillMaxWidth().weight(1f).background(page()))
            }
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    /** On Home the bar sits 132 dp down; the strip starts at 112, under the Skim mark. */
    private fun captureSearchBarOfHome(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(STRIP_HEIGHT.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag(FRAME)
                    .padding(start = 16.dp, top = 20.dp, end = 16.dp),
            ) {
                SearchBarField(incognito = false, onClick = {}, onVoice = {})
            }
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private companion object {
        const val FRAME = "frame"
        const val STRIP_HEIGHT = 120
    }
}
