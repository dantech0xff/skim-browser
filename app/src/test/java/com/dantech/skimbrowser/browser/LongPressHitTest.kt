package com.dantech.skimbrowser.browser

import android.webkit.WebView.HitTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongPressHitTest {

    @Test
    fun anImageIsCompleteAtOnce() {
        assertEquals(
            LongPressHit.Ready(HitTarget.Image(src = IMAGE, alt = null, linkUrl = null)),
            longPressHitOf(HitTestResult.IMAGE_TYPE, IMAGE),
        )
    }

    @Test
    fun aLinkWaitsForItsTextFromTheFocusNode() {
        val hit = longPressHitOf(HitTestResult.SRC_ANCHOR_TYPE, LINK) as LongPressHit.AwaitingFocusNode

        assertEquals(HitTarget.Link(url = LINK, text = "Heat pumps in the cold"), hit.complete(url = LINK, title = "  Heat pumps in the cold "))
        assertEquals("a link without text", HitTarget.Link(url = LINK, text = null), hit.complete(url = LINK, title = " "))
        assertEquals(HitTarget.Link(url = LINK, text = null), hit.complete(url = null, title = null))
    }

    @Test
    fun theLinkKeepsTheAddressTheHitTestGaveEvenIfTheFocusNodeSaysOtherwise() {
        val hit = longPressHitOf(HitTestResult.SRC_ANCHOR_TYPE, LINK) as LongPressHit.AwaitingFocusNode

        assertEquals(LINK, (hit.complete(url = "https://elsewhere.example/", title = "x") as HitTarget.Link).url)
    }

    @Test
    fun anImageInALinkTakesTheLinkFromTheFocusNode() {
        val hit = longPressHitOf(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, IMAGE) as LongPressHit.AwaitingFocusNode

        assertEquals(HitTarget.Image(src = IMAGE, alt = null, linkUrl = LINK), hit.complete(url = LINK, title = "Read more"))
        assertEquals(HitTarget.Image(src = IMAGE, alt = null, linkUrl = null), hit.complete(url = "", title = ""))
    }

    @Test
    fun anythingElseIsLeftToTheWebView() {
        listOf(
            HitTestResult.UNKNOWN_TYPE,
            HitTestResult.PHONE_TYPE,
            HitTestResult.GEO_TYPE,
            HitTestResult.EMAIL_TYPE,
            HitTestResult.EDIT_TEXT_TYPE,
        ).forEach { type -> assertNull("type $type", longPressHitOf(type, "tel:+6561234567")) }
    }

    @Test
    fun aHitWithoutAnAddressIsNoHit() {
        listOf(HitTestResult.SRC_ANCHOR_TYPE, HitTestResult.IMAGE_TYPE, HitTestResult.SRC_IMAGE_ANCHOR_TYPE).forEach { type ->
            assertNull("type $type", longPressHitOf(type, null))
            assertNull("type $type", longPressHitOf(type, "  "))
        }
    }

    private companion object {
        const val LINK = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val IMAGE = "https://daylightjournal.com/img/pump.jpg"
    }
}
