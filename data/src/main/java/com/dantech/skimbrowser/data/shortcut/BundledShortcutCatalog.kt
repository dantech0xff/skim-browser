package com.dantech.skimbrowser.data.shortcut

import android.app.Application
import android.content.res.AssetManager
import com.dantech.skimbrowser.domain.shortcut.ShortcutCatalog
import com.dantech.skimbrowser.domain.shortcut.ShortcutPreset
import com.dantech.skimbrowser.domain.shortcut.webPresets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * The preset shortcuts shipped with the app, read from [ASSET].
 *
 * The document is `{ "version": 1, "shortcuts": [ { "name", "url" } ] }`. `version` is reserved
 * for a later remote config; this reader does not branch on it. A remote source replaces
 * [ShortcutCatalog] and returns the same list. Rows that cannot open as a web page are dropped.
 * A document that is not that shape throws, so seeding can retry instead of treating a broken
 * file as an empty Home.
 */
@Singleton
class BundledShortcutCatalog(
    private val assets: AssetManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortcutCatalog {

    @Inject constructor(app: Application) : this(app.assets)

    override suspend fun presets(): List<ShortcutPreset> = withContext(ioDispatcher) {
        assets.open(ASSET).bufferedReader().use { parseShortcutPresets(it.readText()) }
    }

    companion object {
        const val ASSET = "config/default-shortcuts.json"
    }
}

/**
 * Reads the `shortcuts` array of a catalog document. A missing array is an empty catalog.
 * A value that is not an array, or a document that is not an object, throws [JSONException].
 */
internal fun parseShortcutPresets(text: String): List<ShortcutPreset> {
    val root = JSONObject(text)
    if (!root.has("shortcuts")) return emptyList()
    val rows = root.getJSONArray("shortcuts")
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            add(ShortcutPreset(name = row.optString("name"), url = row.optString("url")))
        }
    }.webPresets()
}
