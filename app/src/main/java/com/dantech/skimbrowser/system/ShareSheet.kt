package com.dantech.skimbrowser.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** The system share sheet for a page, a link or an image file Skim already prepared. */
object ShareSheet {

    /** Shares [url] as text, with [title] as the share title. */
    fun sharePage(context: Context, title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(TEXT)
            .putExtra(Intent.EXTRA_TEXT, url)
            .putExtra(Intent.EXTRA_TITLE, title)
        launch(context, Intent.createChooser(send, null))
    }

    /** Shares [text] with no separate title. */
    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType(TEXT).putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, null))
    }

    /**
     * Shares an image [uri] from the app's file provider. The receiver gets temporary read access,
     * including through the chooser.
     */
    fun shareImage(context: Context, uri: Uri, mime: String) {
        val type = mime.ifBlank { IMAGE }
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("", uri) }
        launch(context, Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app accepted the share: ${e.javaClass.simpleName}")
        }
    }

    private const val TAG = "ShareSheet"
    private const val TEXT = "text/plain"
    private const val IMAGE = "image/*"
}
