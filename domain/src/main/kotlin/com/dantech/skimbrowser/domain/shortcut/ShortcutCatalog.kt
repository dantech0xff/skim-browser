package com.dantech.skimbrowser.domain.shortcut

import com.dantech.skimbrowser.domain.url.hostOf

/**
 * One preset shortcut. A remote config can supply the same two fields; [webPresets] is what
 * seeding keeps.
 */
data class ShortcutPreset(
    val name: String,
    val url: String,
)

/**
 * Where the preset shortcuts come from, in tile order. The bundled JSON is the current source.
 * Replacing this binding with a remote config leaves seeding unchanged: it still applies the
 * list once, and only while the user has no shortcuts yet.
 */
interface ShortcutCatalog {
    suspend fun presets(): List<ShortcutPreset>
}

/**
 * The presets that can open as a web page, trimmed, in the same order. A blank name, or a URL
 * that is not an http(s) address with a host, is dropped.
 */
fun List<ShortcutPreset>.webPresets(): List<ShortcutPreset> = mapNotNull { preset ->
    val name = preset.name.trim()
    val url = preset.url.trim()
    if (name.isEmpty() || hostOf(url) == null) null else preset.copy(name = name, url = url)
}
