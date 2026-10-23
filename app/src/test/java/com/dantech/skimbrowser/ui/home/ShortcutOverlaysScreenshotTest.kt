package com.dantech.skimbrowser.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.domain.usecase.AddShortcut
import com.dantech.skimbrowser.domain.usecase.UpdateShortcut
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The overlays of Home on 15 as the app shows them: Add shortcut over Home's surface, filled in as
 * drawn with the URL focused; and the menu of the first shortcut, opened by a long press, under its
 * tile in a grid laid out like the panel of 15 (32 dp from the top). Board 15 is at 1x: scale its
 * panels 2x to compare.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShortcutOverlaysScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val db: SkimDatabase = inMemorySkimDatabase()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewModels = ViewModelStore()

    @After
    fun tearDown() {
        viewModels.clear()
        appScope.cancel()
        db.close()
    }

    @Test
    fun addShortcutDialog() {
        val dialog = ViewModelProvider.create(
            viewModels,
            viewModelFactory {
                initializer {
                    val shortcuts = ShortcutRepository(db.shortcutDao())
                    ShortcutDialogViewModel(null, shortcuts, AddShortcut(shortcuts), UpdateShortcut(shortcuts), appScope)
                }
            },
        )[ShortcutDialogViewModel::class]
        dialog.name.setTextAndPlaceCursorAtEnd("Daylight Journal")
        dialog.url.setTextAndPlaceCursorAtEnd("daylightjournal.com")
        composeRule.setSkimContent {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            // The window the navigation's dialog scene opens for the destination.
            Dialog(onDismissRequest = {}, properties = ShortcutDialogProperties) {
                ShortcutDialog(dialog, onClose = {})
            }
        }
        composeRule.waitForIdle()

        captureScreenRoboImage("$COMPONENT_GOLDENS/home-shortcut-dialog-15.png")
        composeRule.assertA11y()
    }

    @Test
    fun shortcutMenu() {
        composeRule.setSkimContent(statusBarInset = 0.dp) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                ShortcutGrid(
                    shortcuts = HomeFixtures.Shortcuts.take(4),
                    onOpen = {},
                    onEdit = {},
                    onRemove = {},
                    onAdd = {},
                    modifier = Modifier.padding(start = 16.dp, top = 32.dp, end = 16.dp),
                )
            }
        }
        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }
        composeRule.waitForIdle()

        captureScreenRoboImage("$COMPONENT_GOLDENS/home-shortcut-menu-15.png")
        composeRule.assertA11y()
    }

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
    }
}
