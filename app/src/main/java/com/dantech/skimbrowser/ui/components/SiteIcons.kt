package com.dantech.skimbrowser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * The icons sites sent, as the tiles read them: the process's icon store, which the shell adapts to
 * this, so screens never name the browser layer.
 */
fun interface SiteIcons {
    /** The icon of [host]; null until there is one. [incognito] also sees the session's icons. */
    fun observe(host: String, incognito: Boolean): StateFlow<ImageBitmap?>
}

/**
 * The icons sites sent, provided once by the shell. Null where none is provided (previews and
 * screenshot tests), so every tile draws its letter there.
 */
val LocalSiteIcons = staticCompositionLocalOf<SiteIcons?> { null }

/**
 * The icon [host] sent, or null while there is none (or no [LocalSiteIcons]). With [incognito] an
 * icon an incognito tab received comes first; without, it is never returned.
 */
@Composable
fun siteIcon(host: String?, incognito: Boolean = false): ImageBitmap? {
    val icons = LocalSiteIcons.current ?: return null
    if (host.isNullOrBlank()) return null
    val flow = remember(icons, host, incognito) { icons.observe(host, incognito) }
    val icon by flow.collectAsStateWithLifecycle()
    return icon
}
