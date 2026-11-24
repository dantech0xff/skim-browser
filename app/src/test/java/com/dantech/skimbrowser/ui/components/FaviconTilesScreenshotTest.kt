package com.dantech.skimbrowser.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.SiteIconStore
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The site tiles with the icon a site sent: list, Continue card and tab card tiles, and a shortcut,
 * each beside the letter it falls back on when the site sent none.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class FaviconTilesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: SiteIconStore
    private lateinit var icons: SiteIcons

    @Before
    fun setUp() {
        store = SiteIconStore(folder.newFolder("site-icons"), scope)
        store.put("https://www.daylightjournal.com/energy", drawnIcon(), incognito = false)
        store.put("https://tabi-note.jp/osaka", drawnIcon(), incognito = true)
        icons = SiteIcons(store::observe)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aTileWithAnIconDrawsTheIcon() {
        setTile { FaviconLetterTile("D", host = "daylightjournal.com", modifier = Modifier.testTag(TILE)) }

        assertEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun aTileWithoutAnIconDrawsTheLetter() {
        setTile { FaviconLetterTile("K", host = "kotlinlang.org", modifier = Modifier.testTag(TILE)) }

        assertNotEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun aNormalTileNeverDrawsAnIncognitoIcon() {
        setTile { FaviconLetterTile("T", host = "tabi-note.jp", modifier = Modifier.testTag(TILE)) }

        assertNotEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun anIncognitoTileDrawsTheIconItsSessionReceived() {
        setTile { FaviconLetterTile("T", host = "tabi-note.jp", incognito = true, modifier = Modifier.testTag(TILE)) }

        assertEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun aShortcutWithAnIconDrawsTheIcon() {
        setTile { ShortcutTile("D", position = 0, host = "daylightjournal.com", modifier = Modifier.testTag(TILE)) }

        assertEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun withoutAStoreEveryTileDrawsItsLetter() {
        composeRule.setSkimContent {
            FaviconLetterTile("D", host = "daylightjournal.com", modifier = Modifier.testTag(TILE))
        }

        assertNotEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun aTileDrawnBeforeItsIconArrivesShowsTheIconWhenItDoes() {
        setTile { FaviconLetterTile("K", host = "kotlinlang.org", modifier = Modifier.testTag(TILE)) }
        assertNotEquals(ICON_COLOR.toArgb(), centerPixel())

        store.put("https://kotlinlang.org/docs/home.html", drawnIcon(), incognito = false)

        assertEquals(ICON_COLOR.toArgb(), centerPixel())
    }

    @Test
    fun aTileAsksForItsIconOnceWhileItsSiteStaysTheSame() {
        var asked = 0
        val counting = SiteIcons { host, incognito ->
            asked++
            store.observe(host, incognito)
        }
        var letter by mutableStateOf("K")
        composeRule.setSkimContent {
            CompositionLocalProvider(LocalSiteIcons provides counting) {
                FaviconLetterTile(letter, host = "kotlinlang.org", modifier = Modifier.testTag(TILE))
            }
        }
        composeRule.waitForIdle()

        // The page's title changed, so the tile is drawn again for the same site.
        letter = "L"
        composeRule.waitForIdle()

        assertEquals(1, asked)
    }

    @Test
    fun faviconTiles() = capture("favicon-tiles", dark = false)

    @Test
    fun faviconTilesInTheDarkTheme() = capture("favicon-tiles-dark", dark = true)

    private fun setTile(content: @Composable () -> Unit) {
        composeRule.setSkimContent {
            CompositionLocalProvider(LocalSiteIcons provides icons, content = content)
        }
    }

    private fun centerPixel(): Int {
        val image = composeRule.onNodeWithTag(TILE).captureToImage()
        val pixels = image.toPixelMap()
        return pixels[image.width / 2, image.height / 2].toArgb()
    }

    private fun capture(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            CompositionLocalProvider(LocalSiteIcons provides icons) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag(FRAME)) {
                    Tiles()
                }
            }
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    /** Each row: the tile with the site's icon, then the letter of a site that sent none. */
    @Composable
    private fun Tiles() {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FaviconLetterTile(siteLetter("daylightjournal.com", null), host = "daylightjournal.com")
                FaviconLetterTile(siteLetter("kotlinlang.org", null), host = "kotlinlang.org")
                FaviconLetterTile(
                    siteLetter("daylightjournal.com", null),
                    size = 24.dp,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized,
                    content = colors.onSurfaceVariant,
                    host = "daylightjournal.com",
                )
                FaviconLetterTile(
                    siteLetter("kotlinlang.org", null),
                    size = 24.dp,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized,
                    content = colors.onSurfaceVariant,
                    host = "kotlinlang.org",
                )
                FaviconLetterTile(
                    siteLetter("tabi-note.jp", null),
                    size = 20.dp,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    content = colors.onSurfaceVariant,
                    host = "tabi-note.jp",
                    incognito = true,
                )
                FaviconLetterTile(
                    siteLetter("tabi-note.jp", null),
                    size = 20.dp,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    content = colors.onSurfaceVariant,
                    host = "tabi-note.jp",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShortcutTile(siteLetter(null, "Daylight Journal"), position = 0, host = "daylightjournal.com")
                ShortcutTile(siteLetter(null, "Kotlin"), position = 1, host = "kotlinlang.org")
                ShortcutTile(siteLetter(null, "Daylight Journal"), position = 3, host = "daylightjournal.com")
            }
        }
    }

    /**
     * A 48 px icon drawn in the test, with no asset file: an [ICON_COLOR] disc on a transparent
     * square, with a white bar, so the tile's container shows around it.
     */
    private fun drawnIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ICON_COLOR.toArgb()
        canvas.drawCircle(ICON_PX / 2f, ICON_PX / 2f, ICON_PX * 0.42f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(ICON_PX * 0.3f, ICON_PX * 0.62f, ICON_PX * 0.7f, ICON_PX * 0.72f, paint)
        return bitmap
    }

    private companion object {
        const val FRAME = "frame"
        const val TILE = "tile"
        const val ICON_PX = 48
        val ICON_COLOR = Color(0xFFE8710A)
    }
}
