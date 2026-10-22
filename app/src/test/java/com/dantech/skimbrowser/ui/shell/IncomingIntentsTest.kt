package com.dantech.skimbrowser.ui.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingIntentsTest {

    @Test
    fun anHttpsLinkOpens() {
        assertEquals("https://example.com/a?b=c#d", IncomingIntents.urlFrom(view("https://example.com/a?b=c#d")))
    }

    @Test
    fun anHttpLinkOpens() {
        assertEquals("http://example.com/", IncomingIntents.urlFrom(view("http://example.com/")))
    }

    @Test
    fun theSchemeIsReadInAnyLetterCase() {
        assertEquals("HTTPS://Example.com/Path", IncomingIntents.urlFrom(view("HTTPS://Example.com/Path")))
    }

    @Test
    fun aFileLinkNeverOpens() {
        assertNull(IncomingIntents.urlFrom(view("file:///data/data/com.dantech.skimbrowser/databases/skim.db")))
        assertNull(IncomingIntents.urlFrom(view("file:///sdcard/Download/page.html")))
    }

    @Test
    fun aJavascriptLinkNeverOpens() {
        assertNull(IncomingIntents.urlFrom(view("javascript:alert(document.cookie)")))
        assertNull(IncomingIntents.urlFrom(view("JavaScript:fetch('https://evil.example/')")))
    }

    @Test
    fun anIntentLinkNeverOpens() {
        assertNull(IncomingIntents.urlFrom(view("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end")))
    }

    @Test
    fun otherSchemesNeverOpen() {
        for (url in listOf("content://media/external/file/1", "data:text/html,hello", "about:blank", "market://details?id=x", "mailto:a@example.com")) {
            assertNull(url, IncomingIntents.urlFrom(view(url)))
        }
    }

    @Test
    fun aWebLinkWithoutAHostNeverOpens() {
        assertNull(IncomingIntents.urlFrom(view("https://")))
        assertNull(IncomingIntents.urlFrom(view("http:")))
    }

    @Test
    fun anIntentWithoutDataOpensNothingWhateverItsExtras() {
        val noData = Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "https://example.com/")
        val shared = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.com/")

        assertNull(IncomingIntents.urlFrom(noData))
        assertNull(IncomingIntents.urlFrom(shared))
    }

    @Test
    fun extrasNeverChangeTheLink() {
        val headers = Bundle().apply { putString("Cookie", "session=stolen") }
        val intent = view("https://example.com/")
            .putExtra(Browser.EXTRA_HEADERS, headers)
            .putExtra(Browser.EXTRA_APPLICATION_ID, "com.other.app")
            .putExtra(Intent.EXTRA_TEXT, "javascript:alert(1)")
            .putExtra("url", "https://evil.example/")

        assertEquals("https://example.com/", IncomingIntents.urlFrom(intent))
    }

    @Test
    fun onlyViewIntentsCarryLinks() {
        assertNull(IncomingIntents.urlFrom(null))
        assertNull(IncomingIntents.urlFrom(Intent(Intent.ACTION_MAIN).setData(Uri.parse("https://example.com/"))))
        assertNull(IncomingIntents.urlFrom(Intent(Intent.ACTION_WEB_SEARCH).setData(Uri.parse("https://example.com/"))))
        assertNull(IncomingIntents.urlFrom(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
    }

    @Test
    fun aLinkRecentsHandsOverAgainIsNotOpenedAgain() {
        val reopened = view("https://example.com/").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(IncomingIntents.urlFrom(reopened))
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
}
