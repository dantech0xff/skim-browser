package com.dantech.skimbrowser.ui.shell

import android.content.Intent
import com.dantech.skimbrowser.data.hostOf

/** Links other apps send to Skim. */
object IncomingIntents {

    /**
     * The web page another app asks Skim to open with [intent], or null. Only the data of a VIEW
     * intent counts, and only an http or https URL with a host ([hostOf] is null for `file:`,
     * `javascript:`, `intent:`, `content:` and every other scheme); every extra (headers, an app id, a
     * text to search) is ignored. An intent that Recents hands over again when the task is reopened
     * from there is the link that already opened, not a new one.
     */
    fun urlFrom(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val url = intent.dataString ?: return null
        return url.takeIf { hostOf(it) != null }
    }
}
