package com.dantech.skimbrowser.browser

import androidx.webkit.WebMessageCompat
import com.dantech.skimbrowser.domain.url.sameNavigation
import com.dantech.skimbrowser.domain.url.sameWebOrigin
import com.dantech.skimbrowser.domain.video.MAX_PLAYING_REPORT_CHARS
import com.dantech.skimbrowser.domain.video.bridgeUrl
import com.dantech.skimbrowser.domain.video.playingPage

/**
 * The string body of a `SkimVideo` message, or null when it is not a string from the main frame of
 * the page now showing. [readData] runs only for a string message, and a throw from it is ignored:
 * an array-buffer message makes [WebMessageCompat.getData] throw.
 *
 * An embedded player is not this port. Its file is the one the request sniffer already saw.
 */
internal fun textPlayingMessage(
    type: Int,
    isMainFrame: Boolean,
    sourceOrigin: String,
    pageUrl: String?,
    readData: () -> String?,
): String? {
    if (!isMainFrame) return null
    val page = pageUrl ?: return null
    if (!sameWebOrigin(page, sourceOrigin)) return null
    if (type != WebMessageCompat.TYPE_STRING) return null
    val data = try {
        readData()
    } catch (_: RuntimeException) {
        return null
    }
    if (data.isNullOrBlank() || data.length > MAX_PLAYING_REPORT_CHARS) return null
    val reported = playingPage(data) ?: return null
    if (bridgeUrl(reported) == null || !sameNavigation(page, reported)) return null
    return data
}
