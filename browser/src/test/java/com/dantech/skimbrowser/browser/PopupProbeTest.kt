package com.dantech.skimbrowser.browser

import android.app.Activity
import android.os.Looper
import android.webkit.TestWebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PopupProbeTest {

    private val activity: Activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun aBlankWindowIsDestroyedWhenOpenWasAlreadyAsked() {
        val view = TrackingWebView(activity)
        val reported = mutableListOf<String?>()
        val probe = PopupProbe(view) { reported += it }
        view.webViewClient = probe.client

        probe.client.onPageStarted(view, "about:blank", null)
        probe.client.onPageFinished(view, "about:blank")
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        assertTrue(reported.isEmpty())
        assertFalse(view.destroyed)

        looper.idleFor(Duration.ofMillis(POPUP_BLANK_GRACE_MS))
        looper.idle()

        assertEquals(listOf<String?>(null), reported)
        assertTrue(view.destroyed)
    }

    @Test
    fun aBlankPageThenARealAddressKeepsTheAddress() {
        val view = TrackingWebView(activity)
        val reported = mutableListOf<String?>()
        val probe = PopupProbe(view) { reported += it }
        view.webViewClient = probe.client
        val looper = shadowOf(Looper.getMainLooper())

        probe.client.onPageFinished(view, "about:blank")
        looper.idle()

        assertTrue(
            probe.client.shouldOverrideUrlLoading(
                view,
                TestWebResourceRequest("https://ads.example/offer", mainFrame = true, gesture = false),
            ),
        )
        looper.idle()
        looper.idleFor(Duration.ofMillis(POPUP_BLANK_GRACE_MS))

        assertEquals(listOf("https://ads.example/offer"), reported)
        assertTrue(view.destroyed)
    }

    @Test
    fun aRealAddressIsKeptAndTheBlankFinishDoesNotReplaceIt() {
        val view = TrackingWebView(activity)
        val reported = mutableListOf<String?>()
        val probe = PopupProbe(view) { reported += it }
        view.webViewClient = probe.client

        assertTrue(
            probe.client.shouldOverrideUrlLoading(
                view,
                TestWebResourceRequest("https://ads.example/offer", mainFrame = true, gesture = false),
            ),
        )
        probe.client.onPageFinished(view, "about:blank")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("https://ads.example/offer"), reported)
        assertTrue(view.destroyed)
        assertTrue(
            probe.client.shouldOverrideUrlLoading(
                view,
                TestWebResourceRequest("https://ads.example/again", mainFrame = true, gesture = false),
            ),
        )
        assertEquals(listOf("https://ads.example/offer"), reported)
    }

    private class TrackingWebView(activity: Activity) : WebView(activity) {
        var destroyed = false
            private set

        override fun destroy() {
            destroyed = true
            super.destroy()
        }
    }
}
