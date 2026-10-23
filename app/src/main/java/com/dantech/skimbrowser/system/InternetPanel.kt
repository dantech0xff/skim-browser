package com.dantech.skimbrowser.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/** Android's own place to turn on Wi-Fi or mobile data, for Internet settings on No internet (11). */
object InternetPanel {

    /** The Internet connectivity panel over the app from Android 10, the wireless settings before. */
    fun intent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }

    /**
     * Opens the panel from [context], an Activity; false, with nothing shown, when no app takes it
     * (a device without that settings screen).
     */
    fun open(context: Context): Boolean = try {
        context.startActivity(intent())
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app shows the Internet settings: ${e.javaClass.simpleName}")
        false
    }

    private const val TAG = "InternetPanel"
}
