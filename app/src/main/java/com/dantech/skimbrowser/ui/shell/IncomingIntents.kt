package com.dantech.skimbrowser.ui.shell

import android.content.Intent
import com.dantech.skimbrowser.domain.url.hostOf

/** Links other apps send to Skim. */
object IncomingIntents {

    /**
     * The web page another app asks Skim to open with [intent], or null. A VIEW intent uses its
     * data. A SEND of `text/plain` uses the text when that text is one http or https URL and
     * nothing else. Only a URL with a host counts ([hostOf] is null for `file:`, `javascript:`,
     * `intent:`, `content:` and every other scheme). Other extras (headers, an app id, a subject)
     * are ignored. An intent that Recents hands over again when the task is reopened from there is
     * the link that already opened, not a new one.
     */
    fun urlFrom(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> sharedLink(intent)
            else -> null
        } ?: return null
        return url.takeIf { hostOf(it) != null }
    }

    /** The shared text when it is one web URL and nothing else. A second URL or any other text is not a link. */
    private fun sharedLink(intent: Intent): String? {
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return null
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val http = "http"
        if (text.indexOf(http, ignoreCase = true) != text.lastIndexOf(http, ignoreCase = true)) return null
        return text
    }
}
