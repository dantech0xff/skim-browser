package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dantech.skimbrowser.domain.url.isWebUrl
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A WebView that exists only long enough to learn the address of a blocked `window.open`.
 * It is destroyed once that address is known, or once the window stays on `about:blank`.
 * [onUrl] runs after the WebView is scheduled to be destroyed. The returned function destroys
 * it early, without reporting an address.
 */
internal class PopupProbe(
    private val probe: WebView,
    private val onUrl: (String?) -> Unit,
) {
    private val done = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var blankFinish: Runnable? = null
    val client: WebViewClient = Client()

    fun abandon() {
        finish(null, notify = false)
    }

    private fun finish(url: String?, notify: Boolean) {
        if (!done.compareAndSet(false, true)) return
        blankFinish?.let { main.removeCallbacks(it) }
        blankFinish = null
        main.post {
            probe.stopLoading()
            probe.destroy()
        }
        if (notify) onUrl(url)
    }

    /** A blank document is not the address. The real one often loads after that document finishes. */
    private fun scheduleBlankFinish() {
        if (done.get()) return
        blankFinish?.let { main.removeCallbacks(it) }
        val pending = Runnable { finish(null, notify = true) }
        blankFinish = pending
        main.postDelayed(pending, POPUP_BLANK_GRACE_MS)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return true
            val url = request.url?.toString().orEmpty()
            if (isWebUrl(url)) {
                finish(url, notify = true)
                return true
            }
            // about:blank has to commit so a window with no address can finish. Anything else is not a page.
            if (url.isBlank() || url.startsWith("about:")) return false
            finish(null, notify = true)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val target = url.orEmpty()
            if (isWebUrl(target)) finish(target, notify = true)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (!isWebUrl(url.orEmpty())) scheduleBlankFinish()
        }
    }
}

internal const val POPUP_BLANK_GRACE_MS = 1_000L
