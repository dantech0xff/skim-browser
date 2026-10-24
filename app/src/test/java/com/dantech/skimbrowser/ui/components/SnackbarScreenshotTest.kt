package com.dantech.skimbrowser.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarPlacement
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Snackbars as board 15 draws them, shown by [SkimSnackbarHost] from a [SnackbarController]: above
 * the floating toolbar at the bottom of a board-wide strip, and the variants of 15 stacked.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SnackbarScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val registry = UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test
    fun snackbarAboveTheToolbar() = captureAboveToolbar("snackbar-above-toolbar", dark = false)

    @Test
    fun snackbarAboveTheToolbarInTheDarkTheme() = captureAboveToolbar("snackbar-above-toolbar-dark", dark = true)

    @Test
    fun snackbarVariants() = captureVariants("snackbars-15", dark = false)

    @Test
    fun snackbarVariantsInTheDarkTheme() = captureVariants("snackbars-15-dark", dark = true)

    private fun captureAboveToolbar(name: String, dark: Boolean) {
        val controller = SnackbarController(registry)
        composeRule.setSkimContent(dark = dark) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag(FRAME),
            ) {
                BrowserToolbar(
                    canGoBack = true,
                    canGoForward = false,
                    tabCount = 6,
                    incognito = false,
                    onBack = {},
                    onForward = {},
                    onTabs = {},
                    onMore = {},
                    aiFab = { AiFab(true, stringResource(R.string.cd_ask_skim_about_page), onClick = {}) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = ToolbarMetrics.BottomOffset),
                )
                SkimSnackbarHost(
                    controller = controller,
                    placement = SnackbarPlacement.AboveToolbar,
                    onAction = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        val token = registry.register(UndoKind.TAB, undo = {})
        controller.show(UiText.Res(R.string.snackbar_tab_closed), SnackbarAction.Undo(token))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private fun captureVariants(name: String, dark: Boolean) {
        val controllers = listOf(
            Triple(UiText.Res(R.string.snackbar_tab_closed), SnackbarAction.Undo(registry.register(UndoKind.TAB, undo = {})), false),
            Triple(UiText.Plural(R.plurals.snackbar_imported_bookmarks, 24), SnackbarAction.ViewImported(2), false),
            Triple(UiText.Res(R.string.snackbar_import_failed), null, true),
            Triple(
                UiText.Res(R.string.snackbar_downloaded, persistentListOf("heat-pump-rebate-guide-2026-final-version.pdf")),
                SnackbarAction.OpenDownload(Uri.parse("content://downloads/public_downloads/1"), "application/pdf"),
                false,
            ),
            Triple(UiText.Res(R.string.snackbar_copied), null, false),
        ).map { (text, action, isError) -> SnackbarController(registry).apply { show(text, action, isError) } }
        composeRule.setSkimContent(dark = dark) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 8.dp)
                    .testTag(FRAME),
            ) {
                controllers.forEach { SkimSnackbarHost(it, SnackbarPlacement.Bottom, onAction = {}) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private companion object {
        const val FRAME = "frame"
    }
}
