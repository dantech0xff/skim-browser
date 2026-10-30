package com.dantech.skimbrowser.data.shortcut

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shortcut.ShortcutPreset
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledShortcutCatalogTest {

    @Test
    fun theBundledFileIsSevenPresetsAndTheAddTileCompletesTheSecondRow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open(BundledShortcutCatalog.ASSET).bufferedReader().use { it.readText() }

        assertEquals(1, JSONObject(text).getInt("version"))
        assertEquals(PRESETS, BundledShortcutCatalog(context.assets).presets())
    }

    @Test
    fun aCatalogWithoutShortcutsIsEmpty() {
        assertEquals(emptyList<ShortcutPreset>(), parseShortcutPresets("""{"version": 1}"""))
    }

    @Test
    fun rowsThatCannotBeAShortcutAreDropped() {
        val parsed = parseShortcutPresets(
            """
            {
              "shortcuts": [
                { "name": " Wikipedia ", "url": " https://www.wikipedia.org " },
                { "name": " ", "url": "https://blank.example" },
                { "name": "Script", "url": "javascript:alert(1)" },
                "not an object",
                { "name": "GitHub", "url": "https://github.com" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ShortcutPreset("Wikipedia", "https://www.wikipedia.org"),
                ShortcutPreset("GitHub", "https://github.com"),
            ),
            parsed,
        )
    }

    @Test
    fun aDocumentThatIsNotTheCatalogShapeThrows() {
        assertThrows { parseShortcutPresets("[]") }
        assertThrows { parseShortcutPresets("""{"shortcuts": "nope"}""") }
    }

    private fun assertThrows(body: () -> Unit) {
        try {
            body()
        } catch (_: JSONException) {
            return
        }
        throw AssertionError("Expected JSONException")
    }

    private companion object {
        val PRESETS = listOf(
            ShortcutPreset("Wikipedia", "https://www.wikipedia.org"),
            ShortcutPreset("YouTube", "https://www.youtube.com"),
            ShortcutPreset("Reddit", "https://www.reddit.com"),
            ShortcutPreset("TikTok", "https://www.tiktok.com"),
            ShortcutPreset("Grok", "https://grok.com"),
            ShortcutPreset("X", "https://x.com"),
            ShortcutPreset("GitHub", "https://github.com"),
        )
    }
}
