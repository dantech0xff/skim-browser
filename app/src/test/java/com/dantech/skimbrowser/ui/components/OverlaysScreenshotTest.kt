package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Where component goldens live, relative to the module directory tests run in. */
private const val COMPONENT_GOLDENS = "src/test/screenshots/components"

/**
 * Captures the whole screen as golden [name] and checks the accessibility rules. A sheet, a dialog
 * and a menu each open in a window of their own, so the capture takes every window: the screen
 * behind, the scrim or dim, then the overlay.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun OverlaysScreenshotTest.capture(name: String) {
    composeRule.waitForIdle()
    captureScreenRoboImage("$COMPONENT_GOLDENS/$name.png")
    composeRule.assertA11y()
}

/**
 * Sheets, dialogs and menus as their boards draw them, light and dark. The goldens are whole screens
 * at the size of a phone board: those of 06 and 06d compare with the board directly. Board 15 draws
 * each overlay in a 412 dp panel at 1x: scale the panel 2x and compare the overlay itself.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class OverlaysScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Sheet: Ask about this page (06) over the page, at full height; and at half height, as it opens.

    @Test
    fun askPageSheet() = captureAskPageSheet("sheet-06", dark = false)

    @Test
    fun askPageSheetInTheDarkTheme() = captureAskPageSheet("sheet-06-dark", dark = true)

    @Test
    fun askPageSheetAtHalfHeight() = captureAskPageSheet("sheet-06-half", dark = false, halfHeight = true)

    private fun captureAskPageSheet(name: String, dark: Boolean, halfHeight: Boolean = false) {
        composeRule.setSkimContent(dark = dark) {
            PageBehind()
            SkimBottomSheet(
                onDismiss = {},
                paneTitle = stringResource(R.string.ai_ask_about_page),
                state = rememberSkimSheetState(skipPartiallyExpanded = !halfHeight),
            ) {
                AskPageSheetContent()
            }
        }
        capture(name)
    }

    // Dialog with an icon: AI permission (06d), over the page and the toolbar.

    @Test
    fun aiPermissionDialog() = captureAiPermissionDialog("dialog-06d", dark = false)

    @Test
    fun aiPermissionDialogInTheDarkTheme() = captureAiPermissionDialog("dialog-06d-dark", dark = true)

    private fun captureAiPermissionDialog(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            PageBehind(toolbar = true)
            SkimAlertDialog(
                title = stringResource(R.string.ai_permission_title),
                confirm = DialogAction(stringResource(R.string.action_allow), onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(stringResource(R.string.action_not_now), onClick = {}),
                icon = { SunnyMark(size = 56.dp, iconSize = 28.dp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ai_permission_body, PAGE_HOST))
                        Text(stringResource(R.string.ai_permission_note))
                    }
                },
            )
        }
        capture(name)
    }

    // Dialogs of 15: a destructive confirm with its icon, a plain one, a two-line title, fields.

    @Test
    fun deleteAllAiDataDialog() = captureDeleteAllAiDataDialog("dialog-delete-ai-data-15", dark = false)

    @Test
    fun deleteAllAiDataDialogInTheDarkTheme() = captureDeleteAllAiDataDialog("dialog-delete-ai-data-15-dark", dark = true)

    private fun captureDeleteAllAiDataDialog(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            ScreenBehind()
            SkimAlertDialog(
                title = stringResource(R.string.delete_ai_data_title),
                confirm = DialogAction(stringResource(R.string.action_delete), onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = {}),
                destructiveConfirm = true,
                icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                iconTint = MaterialTheme.colorScheme.error,
                text = { Text(stringResource(R.string.delete_ai_data_body)) },
            )
        }
        capture(name)
    }

    @Test
    fun clearSkimHistoryDialog() {
        composeRule.setSkimContent {
            ScreenBehind()
            SkimAlertDialog(
                title = stringResource(R.string.clear_skim_history_title),
                confirm = DialogAction(stringResource(R.string.action_clear), onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = {}),
                text = { Text(stringResource(R.string.clear_skim_history_body)) },
            )
        }
        capture("dialog-clear-skim-history-15")
    }

    @Test
    fun sitePermissionDialog() {
        composeRule.setSkimContent {
            ScreenBehind()
            SkimAlertDialog(
                title = stringResource(R.string.site_permission_location_title, PAGE_HOST),
                confirm = DialogAction(stringResource(R.string.action_allow), onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(stringResource(R.string.action_block), onClick = {}),
                icon = { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) },
                iconTint = MaterialTheme.colorScheme.primary,
                text = { Text(stringResource(R.string.site_permission_body)) },
            )
        }
        capture("dialog-site-permission-15")
    }

    @Test
    fun addShortcutDialog() {
        composeRule.setSkimContent {
            ScreenBehind()
            val url = remember { FocusRequester() }
            SkimAlertDialog(
                title = stringResource(R.string.dialog_add_shortcut_title),
                confirm = DialogAction(stringResource(R.string.action_save), onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = {}),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SkimTextField(rememberTextFieldState("Daylight Journal"), stringResource(R.string.field_name))
                        SkimTextField(rememberTextFieldState(PAGE_HOST), stringResource(R.string.field_url), focusRequester = url)
                    }
                    LaunchedEffect(Unit) { url.requestFocus() }
                },
            )
        }
        capture("dialog-add-shortcut-15")
    }

    // Choice dialog: Search engine (15).

    @Test
    fun searchEngineDialog() = captureSearchEngineDialog("choice-dialog-15", dark = false)

    @Test
    fun searchEngineDialogInTheDarkTheme() = captureSearchEngineDialog("choice-dialog-15-dark", dark = true)

    private fun captureSearchEngineDialog(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            ScreenBehind()
            ChoiceDialog(
                title = stringResource(R.string.settings_search_engine),
                options = SearchEngines,
                selected = R.string.search_engine_google,
                label = { stringResource(it) },
                onSelect = {},
                onDismiss = {},
            )
        }
        capture(name)
    }

    // Menu: the tab group menu of 15, opened from Group options on a group header of 09.

    @Test
    fun tabGroupMenu() = captureTabGroupMenu("menu-tab-group-15", dark = false)

    @Test
    fun tabGroupMenuInTheDarkTheme() = captureTabGroupMenu("menu-tab-group-15-dark", dark = true)

    private fun captureTabGroupMenu(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark, statusBarInset = 0.dp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // The group header 24 dp from the top of the panel, as on 15.
                Row(
                    Modifier
                        .padding(start = 16.dp, top = 24.dp)
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(GROUP, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMediumEmphasized)
                    CountBadge(4)
                }
                // Group options is drawn 40 dp, centered on the header and 8 dp from the edge; its
                // 48 dp frame reaches 4 dp past it, and the menu anchors to that frame.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 4.dp),
                ) {
                    CompactIconButton(R.drawable.ic_more_vert, stringResource(R.string.cd_group_options), onClick = {}, iconSize = 24.dp)
                    SkimMenu(
                        expanded = true,
                        onDismiss = {},
                        groups = persistentListOf(
                            persistentListOf(
                                MenuEntry(stringResource(R.string.rename_group), R.drawable.ic_drive_file_rename_outline) {},
                                MenuEntry(stringResource(R.string.tab_group_ungroup), R.drawable.ic_layers_clear) {},
                            ),
                            persistentListOf(MenuEntry(pluralStringResource(R.plurals.tab_group_close_tabs, 4, 4), R.drawable.ic_close) {}),
                        ),
                        anchorContentDescription = stringResource(R.string.cd_tab_group_menu, GROUP),
                    )
                }
            }
        }
        capture(name)
    }

    private companion object {
        const val GROUP = "Heat pump research"

        val SearchEngines = persistentListOf(
            R.string.search_engine_google,
            R.string.search_engine_bing,
            R.string.search_engine_duckduckgo,
            R.string.search_engine_ecosia,
        )
    }
}

/** The screen behind a dialog of 15: surface, under the dim. */
@Composable
private fun ScreenBehind() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    )
}

/**
 * A page as 05 shows it under 06 and 06d: the status bar, the address bar on surface, then the page,
 * left blank here; with the floating toolbar for 06d.
 */
@Composable
internal fun PageBehind(toolbar: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(Modifier.statusBarsPadding()) {
            AddressBar(
                address = PAGE_HOST,
                indicator = SiteIndicator.Secure,
                readerAvailable = true,
                loadProgress = null,
                onSiteInfo = {},
                onEditAddress = {},
                onReader = {},
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.surfaceContainerLowest),
            )
        }
        if (toolbar) {
            BrowserToolbar(
                canGoBack = true,
                canGoForward = false,
                tabCount = 7,
                incognito = false,
                onBack = {},
                onForward = {},
                onTabs = {},
                onMore = {},
                aiFab = { AiFab(enabled = true, stringResource(R.string.cd_ask_skim_about_page), onClick = {}) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = ToolbarMetrics.BottomOffset),
            )
        }
    }
}
