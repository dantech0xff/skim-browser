package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.permissions.PendingSitePermission
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.components.SkimBottomSheet
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * More options (05b), Link and Image options (05d), site information and the site permission dialog.
 * Boards that have their own PNG are captured under that name. The New tab menu, the incognito
 * menu, site information and the permission dialog live on board 15's collage, so they are component
 * goldens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class BrowsingOverlaysScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreMenuOnAPage() {
        showSheet(paneTitle = { stringResource(R.string.cd_more_options) }) {
            MoreMenuSheet(PageMenu, onStar = {}, onShare = {}, onFind = {}, onDesktop = {}, onNewTab = {}, onNewIncognito = {}, onBookmarks = {}, onHistory = {}, onSettings = {})
        }
        captureBoard("05b-more-menu")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun moreMenuOnTheNewTabPage() {
        showSheet(paneTitle = { stringResource(R.string.cd_more_options) }) {
            MoreMenuSheet(NewTabMenu, onStar = {}, onShare = {}, onFind = {}, onDesktop = {}, onNewTab = {}, onNewIncognito = {}, onBookmarks = {}, onHistory = {}, onSettings = {})
        }
        captureComponent("more-menu-05b-new-tab")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun moreMenuInIncognito() {
        showSheet(incognito = true, paneTitle = { stringResource(R.string.cd_more_options) }) {
            MoreMenuSheet(
                PageMenu.copy(incognito = true),
                onStar = {},
                onShare = {},
                onFind = {},
                onDesktop = {},
                onNewTab = {},
                onNewIncognito = {},
                onBookmarks = {},
                onHistory = {},
                onSettings = {},
            )
        }
        captureComponent("more-menu-05b-incognito")
    }

    @Test
    fun linkOptions() {
        showSheet(paneTitle = { stringResource(R.string.cd_link_options) }) {
            LinkOptionsSheet(
                LinkOptionsUiState(
                    title = "2026 rebate guide",
                    subtitle = "energysaver.example/rebates-2026",
                    rows = listOf(LinkRow.OpenNewTab, LinkRow.OpenIncognito, LinkRow.CopyLink, LinkRow.ShareLink),
                ),
                onRow = {},
            )
        }
        captureBoard("05d-link-image-options")
        composeRule.assertA11y()
    }

    @Test
    fun imageOptions() {
        showSheet(paneTitle = { stringResource(R.string.cd_image_options) }) {
            LinkOptionsSheet(
                LinkOptionsUiState(
                    image = true,
                    title = "A heat pump unit mounted outside a timber house in snow",
                    subtitle = "daylightjournal.com/images/heat-pump-snow.jpg",
                    rows = listOf(LinkRow.OpenImage, LinkRow.DownloadImage, LinkRow.ShareImage),
                ),
                onRow = {},
            )
        }
        captureBoard("05d-link-image-options--image")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun siteInformation() {
        showSheet(paneTitle = { stringResource(R.string.cd_site_info) }) {
            SiteInfoSheet(
                SiteInfoUiState(
                    siteKey = "daylightjournal.com",
                    title = "daylightjournal.com",
                    letter = "D",
                    secure = true,
                    showSkimAi = true,
                    skimAiOn = true,
                    permissions = listOf(SitePermissionRow(SitePermissionKind.LOCATION, allowed = false)),
                    showCookies = true,
                ),
                onSkimAi = {},
                onPermission = { _, _ -> },
                onClearCookies = {},
            )
        }
        captureComponent("site-info-15")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun sitePermission() {
        composeRule.setSkimContent {
            BrowsingScreen(Loaded, TextFieldState(), actions, page = { Box(Modifier.fillMaxSize().background(Color.White)) })
            SitePermissionDialog(
                pending = PendingSitePermission("tab-1", "daylightjournal.com", setOf(SitePermissionKind.LOCATION), incognito = false),
                onAllow = {},
                onBlock = {},
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$COMPONENT_GOLDENS/site-permission-15.png")
        composeRule.assertA11y()
    }

    private fun showSheet(
        incognito: Boolean = false,
        paneTitle: @Composable () -> String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setSkimContent(incognito = incognito) {
            BrowsingScreen(
                if (incognito) Loaded.copy(incognito = true) else Loaded,
                TextFieldState(),
                actions,
                page = { Box(Modifier.fillMaxSize().background(Color.White)) },
            )
            val title = paneTitle()
            SkimBottomSheet(onDismiss = {}, paneTitle = title) { content() }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureComponent(name: String) {
        captureScreenRoboImage("$COMPONENT_GOLDENS/$name.png")
        composeRule.assertA11y()
    }

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"

        val Loaded = BrowsingUiState(
            address = "daylightjournal.com",
            indicator = SiteIndicator.Secure,
            readerAvailable = true,
            progress = null,
            loadError = null,
            backEnabled = true,
            forwardEnabled = false,
            tabCount = 7,
            incognito = false,
            aiFab = AiFabMode.Enabled,
            find = null,
            refreshing = false,
        )

        val PageMenu = MoreMenuUiState(
            page = true,
            title = "Why heat pumps are winning in cold climates",
            host = "daylightjournal.com",
            url = "https://daylightjournal.com/energy/heat-pumps-cold",
            letter = "D",
            bookmarkKnown = true,
        )

        val NewTabMenu = MoreMenuUiState(bookmarkKnown = true)

        val actions = BrowsingActions(
            onSiteInfo = {},
            onEditAddress = {},
            onReader = {},
            onRefresh = {},
            onTryAgain = {},
            onInternetSettings = {},
            onFindPrevious = {},
            onFindNext = {},
            onCloseFind = {},
            onBack = {},
            onForward = {},
            onTabs = {},
            onMore = {},
            onAskPage = {},
        )
    }
}
