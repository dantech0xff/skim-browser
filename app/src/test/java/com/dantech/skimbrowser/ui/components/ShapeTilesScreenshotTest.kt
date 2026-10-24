package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The MaterialShapes tiles where their boards draw them: the shortcut grid of 04 (its strip starts
 * 352 dp down), the art of 01 and 11, the incognito hero of 09c, and the list tiles and marks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShapeTilesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortcutGridOfHome() = capture("shortcut-tiles-04") { ShortcutGrid() }

    @Test
    fun shortcutGridOfHomeInTheDarkTheme() = capture("shortcut-tiles-04-dark", dark = true) { ShortcutGrid() }

    @Test
    fun welcomeArt() = capture("shape-art-01") {
        // The 300 by 240 art of Welcome, shapes at the offsets the board gives them.
        val colors = MaterialTheme.colorScheme
        Box(Modifier.padding(start = 56.dp).size(300.dp, 240.dp)) {
            ShapeArt(
                MaterialShapes.Cookie9Sided, 104.dp, R.drawable.ic_article, 40.dp,
                colors.primaryContainer, colors.onPrimaryContainer, Modifier.offset(184.dp, 4.dp),
            )
            ShapeArt(
                MaterialShapes.Clover4Leaf, 92.dp, R.drawable.ic_travel_explore, 36.dp,
                colors.secondaryContainer, colors.onSecondaryContainer, Modifier.offset(12.dp, 140.dp),
            )
            SunnyMark(size = 148.dp, iconSize = 60.dp, modifier = Modifier.offset(76.dp, 46.dp))
        }
    }

    @Test
    fun pageFailedToLoadArt() = capture("shape-art-11") {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                MaterialShapes.SoftBurst to R.drawable.ic_public_off,
                MaterialShapes.Cookie12Sided to R.drawable.ic_wifi_off,
                MaterialShapes.Clover8Leaf to R.drawable.ic_schedule,
            ).forEach { (shape, icon) ->
                ShapeArt(shape, 128.dp, icon, 56.dp, colors.primaryContainer, colors.onPrimaryContainer)
            }
        }
    }

    @Test
    fun incognitoHero() = capture("shape-art-09c", incognito = true) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.padding(start = 24.dp, top = 16.dp, bottom = 16.dp)) {
            ShapeArt(
                MaterialShapes.Cookie12Sided, 72.dp, R.drawable.ic_visibility_off, 36.dp,
                colors.secondaryContainer, colors.onSecondaryContainer,
            )
        }
    }

    @Test
    fun marksAndListTiles() = capture("marks-and-tiles") { MarksAndTiles() }

    @Test
    fun marksAndListTilesInTheDarkTheme() = capture("marks-and-tiles-dark", dark = true) { MarksAndTiles() }

    /** The shortcut grid of 04: four columns 8 apart, rows 16 apart, the name under each tile. */
    @Composable
    private fun ShortcutGrid() {
        val shortcuts = listOf("Wikipedia", "GitHub", "YouTube", "Hacker News", "Reddit", "Medium", "Stack Overflow")
        Column(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            (shortcuts.map { it as String? } + null).chunked(4).forEachIndexed { row, names ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { column, name ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (name != null) {
                                ShortcutTile(siteLetter(host = null, title = name), position = row * 4 + column)
                            } else {
                                AddShortcutTile()
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                name ?: stringResource(R.string.home_add_shortcut),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MarksAndTiles() {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SunnyMark(size = 64.dp, iconSize = 32.dp, contentDescription = stringResource(R.string.app_name))
                SunnyMark(size = 44.dp, iconSize = 22.dp)
                SunnyMark(size = 40.dp, iconSize = 20.dp)
                SunnyMark(size = 24.dp, iconSize = 14.dp)
                SunnyMark(size = 36.dp, iconSize = 20.dp, brand = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FaviconLetterTile(siteLetter(host = "www.daylightjournal.com", title = null))
                FaviconLetterTile(
                    siteLetter(host = "tabi-note.jp", title = null),
                    size = 24.dp,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized,
                    content = colors.onSurfaceVariant,
                )
                FaviconLetterTile(
                    siteLetter(host = "kotlinlang.org", title = null),
                    size = 20.dp,
                    // The tab cards of 09 draw this letter at 10/12.
                    textStyle = MaterialTheme.typography.labelMediumEmphasized.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    content = colors.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_search, colors.secondaryContainer, colors.onSecondaryContainer)
                IconTile(R.drawable.ic_public, colors.primaryContainer, colors.onPrimaryContainer)
                IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer)
                IconTile(R.drawable.ic_shield, colors.surfaceContainer, colors.onSurfaceVariant)
                IconTile(
                    R.drawable.ic_folder_filled,
                    colors.secondaryContainer,
                    colors.onSecondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }

    private fun capture(
        name: String,
        dark: Boolean = false,
        incognito: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setSkimContent(dark = dark, incognito = incognito) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag(FRAME)) {
                content()
            }
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private companion object {
        const val FRAME = "frame"
    }
}
