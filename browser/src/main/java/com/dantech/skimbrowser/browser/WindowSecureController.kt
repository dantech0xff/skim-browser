package com.dantech.skimbrowser.browser

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.MainThread
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy

/**
 * The window's two privacy switches, each held by any number of owners (the surfaces that need it
 * on, by name), so one surface turning off cannot uncover another one's content:
 *
 * - [setSecure]: `FLAG_SECURE`, on while at least one owner holds it. No screenshots, no screen
 *   recording, and a blank Recents preview.
 * - [setRecentsHidden]: the Recents preview alone (`setRecentsScreenshotEnabled`, API 33 and
 *   later; earlier versions have no such switch and only `FLAG_SECURE` hides the preview).
 *
 * Either switch turns on at once, before the next frame is drawn, and turns off only once the
 * current frame is done (a post to the decor view), so a frame still showing private content while
 * the screen changes is never captured. Which surfaces need them is the caller's decision.
 */
@MainThread
class WindowSecureController internal constructor(
    private val window: Window,
    /** `Activity.setRecentsScreenshotEnabled`; null before API 33. */
    private val setRecentsScreenshotEnabled: ((Boolean) -> Unit)?,
) : WindowPrivacy {
    constructor(activity: Activity) : this(
        activity.window,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            { enabled -> activity.setRecentsScreenshotEnabled(enabled) }
        } else {
            null
        },
    )

    private val secureOwners = HashSet<String>()
    private val recentsOwners = HashSet<String>()
    private var recentsHidden = false

    /** True while `FLAG_SECURE` is set on the window. */
    val isSecure: Boolean
        get() = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    /** Holds `FLAG_SECURE` on for [owner] while [secure], or lets go of it. */
    override fun setSecure(owner: String, secure: Boolean) {
        if (secure) {
            secureOwners += owner
            if (!isSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (secureOwners.remove(owner) && secureOwners.isEmpty()) {
            window.decorView.post {
                if (secureOwners.isEmpty()) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    /** Keeps the Recents preview hidden for [owner] while [hidden], or lets go of it; nothing before API 33. */
    override fun setRecentsHidden(owner: String, hidden: Boolean) {
        val setEnabled = setRecentsScreenshotEnabled ?: return
        if (hidden) {
            recentsOwners += owner
            if (!recentsHidden) {
                recentsHidden = true
                setEnabled(false)
            }
        } else if (recentsOwners.remove(owner) && recentsOwners.isEmpty()) {
            window.decorView.post {
                if (recentsOwners.isEmpty() && recentsHidden) {
                    recentsHidden = false
                    setEnabled(true)
                }
            }
        }
    }
}
