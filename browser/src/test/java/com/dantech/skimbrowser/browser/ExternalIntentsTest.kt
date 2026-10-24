package com.dantech.skimbrowser.browser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalIntentsTest {

    @Test
    fun webPagesAndEngineUrlsLoadInTheWebView() {
        listOf(
            "https://example.com/a",
            "HTTP://example.com/",
            "about:blank",
            "blob:https://example.com/5c1e",
            "data:text/plain,hi",
        ).forEach { url ->
            assertSame(url, ExternalNav.InWebView, externalNavigation(Uri.parse(url), hasGesture = false, incognito = false, ownPackage = SKIM))
        }
    }

    @Test
    fun fileContentAndJavascriptUrlsNeverLoadEvenAfterAGesture() {
        listOf(
            "file:///sdcard/Download/secret.txt",
            "FILE:///data/data/com.dantech.skimbrowser/databases/skim.db",
            "content://com.dantech.skimbrowser.fileprovider/shared/a.png",
            "javascript:alert(document.cookie)",
            "no-scheme-at-all",
        ).forEach { url ->
            assertSame(url, ExternalNav.Blocked, externalNavigation(Uri.parse(url), hasGesture = true, incognito = false, ownPackage = SKIM))
        }
    }

    @Test
    fun otherAppsOpenOnlyAfterAGesture() {
        listOf("tel:+6561234567", "mailto:ana@example.com", "market://details?id=x", "intent://scan/#Intent;scheme=zxing;end")
            .forEach { url -> assertSame(url, ExternalNav.Blocked, externalNavigation(Uri.parse(url), hasGesture = false, incognito = false, ownPackage = SKIM)) }
    }

    @Test
    fun aTelLinkBecomesABrowsableViewIntent() {
        val launch = launchOf("tel:+6561234567")

        assertEquals(Intent.ACTION_VIEW, launch.intent.action)
        assertEquals(Uri.parse("tel:+6561234567"), launch.intent.data)
        assertTrue(launch.intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(launch.intent.component)
        assertNull(launch.fallbackUrl)
    }

    @Test
    fun anIntentUrlLosesItsComponentAndEveryFlagAndKeepsItsPackage() {
        val launch = launchOf(
            "intent://callback#Intent;scheme=myapp;package=com.example.app;" +
                "component=com.example.app/.internal.AdminActivity;launchFlags=0x100000C3;end",
        )

        assertTrue(launch.intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(launch.intent.component)
        assertEquals("com.example.app", launch.intent.`package`)
        assertEquals(Uri.parse("myapp://callback"), launch.intent.data)
        assertEquals("grants and launch flags alike", 0, launch.intent.flags)
    }

    @Test
    fun aPageCannotClearATaskThroughItsLaunchFlags() {
        // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
        val launch = launchOf("intent://x#Intent;scheme=https;package=com.android.chrome;launchFlags=0x10008000;end")

        assertEquals(0, launch.intent.flags)
    }

    @Test
    fun anIntentAimedAtSkimItselfIsBlocked() {
        listOf(
            "intent://daylightjournal.com/#Intent;scheme=https;package=$SKIM;end",
            "intent://daylightjournal.com/#Intent;scheme=https;package=$SKIM;launchFlags=0x10008000;end",
        ).forEach { url ->
            assertSame(url, ExternalNav.Blocked, externalNavigation(Uri.parse(url), hasGesture = true, incognito = false, ownPackage = SKIM))
        }
    }

    @Test
    fun anIncognitoTabOnlyHandsOverCallsEmailsAndTextMessages() {
        listOf("tel:+6561234567", "mailto:ana@example.com", "sms:+6561234567", "smsto:+6561234567").forEach { url ->
            val nav = externalNavigation(Uri.parse(url), hasGesture = true, incognito = true, ownPackage = SKIM)
            assertTrue(url, nav is ExternalNav.Launch)
        }
        listOf(
            "intent://evil.example/t#Intent;scheme=https;package=com.android.chrome;end",
            "intent://evil.example/t#Intent;scheme=https;end",
            "market://details?id=x",
            "geo:1.3,103.8",
            "skype:ana?call",
        ).forEach { url ->
            assertSame(url, ExternalNav.Blocked, externalNavigation(Uri.parse(url), hasGesture = true, incognito = true, ownPackage = SKIM))
        }
        assertSame(
            "still only after a gesture",
            ExternalNav.Blocked,
            externalNavigation(Uri.parse("tel:+6561234567"), hasGesture = false, incognito = true, ownPackage = SKIM),
        )
        assertSame(ExternalNav.InWebView, externalNavigation(Uri.parse("https://example.com/"), hasGesture = false, incognito = true, ownPackage = SKIM))
    }

    @Test
    fun anIntentUrlLosesItsSelector() {
        val launch = launchOf("intent://callback#Intent;scheme=myapp;SEL;component=com.example.app/.internal.Other;end")

        assertNull(launch.intent.selector)
        assertNull(launch.intent.component)
        assertTrue(launch.intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun anHttpFallbackUrlIsKept() {
        val launch = launchOf(
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
                "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fget-app;end",
        )

        assertEquals("https://example.com/get-app", launch.fallbackUrl)
    }

    @Test
    fun aFallbackUrlThatIsNotHttpIsDropped() {
        listOf("javascript%3Aalert(1)", "file%3A%2F%2F%2Fsdcard%2Fa", "intent%3A%2F%2Fx%23Intent%3Bend", "https%3A%2F%2F")
            .forEach { fallback ->
                val launch = launchOf("intent://x#Intent;scheme=myapp;S.browser_fallback_url=$fallback;end")
                assertNull(fallback, launch.fallbackUrl)
            }
    }

    @Test
    fun anIntentAimedAtAFileOrContentUriIsBlocked() {
        listOf(
            "intent:///sdcard/Download/a.apk#Intent;scheme=file;action=android.intent.action.VIEW;end",
            "intent://com.dantech.skimbrowser.fileprovider/shared/a#Intent;scheme=content;end",
        ).forEach { url -> assertSame(url, ExternalNav.Blocked, externalNavigation(Uri.parse(url), hasGesture = true, incognito = false, ownPackage = SKIM)) }
    }

    @Test
    fun aMalformedIntentUrlIsBlocked() {
        assertSame(ExternalNav.Blocked, externalNavigation(Uri.parse("intent:#Intent;launchFlags=zz;end"), hasGesture = true, incognito = false, ownPackage = SKIM))
    }

    private fun launchOf(url: String): ExternalNav.Launch {
        val nav = externalNavigation(Uri.parse(url), hasGesture = true, incognito = false, ownPackage = SKIM)
        return nav as? ExternalNav.Launch ?: throw AssertionError("$url gave $nav")
    }

    private companion object {
        const val SKIM = "com.dantech.skimbrowser"
    }
}
