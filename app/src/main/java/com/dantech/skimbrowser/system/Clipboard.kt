package com.dantech.skimbrowser.system

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copies text to the clipboard. Returns true when the app should show its own "Copied to
 * clipboard" confirmation: Android 13 and later show one of their own, so the app stays quiet.
 *
 * [sensitive] marks the clip so a keyboard does not learn it (an incognito link). From Android 13
 * that is [ClipDescription.EXTRA_IS_SENSITIVE]; earlier releases use the same extra's string key.
 */
object Clipboard {

    fun copy(context: Context, text: String, sensitive: Boolean): Boolean {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(PLAIN_LABEL, text)
        if (sensitive) {
            val extras = PersistableBundle()
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                LEGACY_SENSITIVE
            }
            extras.putBoolean(key, true)
            clip.description.extras = extras
        }
        manager.setPrimaryClip(clip)
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
    }

    /** A label that does not repeat the copied text. */
    private const val PLAIN_LABEL = "text"

    /** [ClipDescription.EXTRA_IS_SENSITIVE] before it was a constant. */
    private const val LEGACY_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
