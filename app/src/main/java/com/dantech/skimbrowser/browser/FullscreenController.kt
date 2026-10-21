package com.dantech.skimbrowser.browser

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MainThread
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full-screen web content, such as a video a page puts in full screen (`onShowCustomView`): the
 * page's view covers the window on a black frame above the decor view, the system bars hide until
 * the user swipes them in, and Back leaves.
 *
 * Whichever way full screen ends (Back, the page, the tab's WebView destroyed, a second full-screen
 * request, the Activity destroyed), the page's callback hears `onCustomViewHidden` exactly once, so
 * the page never stays in a full-screen state no one shows.
 */
@MainThread
class FullscreenController(private val activity: ComponentActivity) {

    private class Showing(
        val ownerTabId: String,
        val frame: FrameLayout,
        val callback: WebChromeClient.CustomViewCallback,
    )

    private var showing: Showing? = null
    private var destroyed = false

    private val _isFullscreen = MutableStateFlow(false)

    /** True while web content is shown full screen. */
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            exit()
        }
    }

    /** Shows [view], the full-screen content of [ownerTabId]'s page; content already shown leaves first. */
    fun enter(ownerTabId: String, view: View, callback: WebChromeClient.CustomViewCallback) {
        exit()
        if (destroyed) {
            callback.onCustomViewHidden()
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        decorView().addView(frame, ViewGroup.LayoutParams(MATCH, MATCH))
        showing = Showing(ownerTabId, frame, callback)
        insetsController().apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // Added on every entry, so it is the newest callback and Back reaches it before the screens' own.
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        _isFullscreen.value = true
    }

    /** Leaves full screen, whoever shows it; nothing happens when nothing is shown. */
    fun exit() {
        val current = showing ?: return
        showing = null
        backCallback.remove()
        (current.frame.parent as? ViewGroup)?.removeView(current.frame)
        current.frame.removeAllViews()
        insetsController().show(WindowInsetsCompat.Type.systemBars())
        _isFullscreen.value = false
        // Last, as the page may answer by asking to leave again, which then finds nothing to do.
        current.callback.onCustomViewHidden()
    }

    /** Leaves full screen if [ownerTabId]'s page shows it: the page left it, or its WebView is going away. */
    fun exit(ownerTabId: String) {
        if (showing?.ownerTabId == ownerTabId) exit()
    }

    /** The Activity is being destroyed: full screen ends and cannot start again. */
    fun destroy() {
        destroyed = true
        exit()
    }

    private fun decorView(): ViewGroup = activity.window.decorView as ViewGroup

    private fun insetsController(): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
