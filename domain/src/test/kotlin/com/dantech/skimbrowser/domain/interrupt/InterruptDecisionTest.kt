package com.dantech.skimbrowser.domain.interrupt

import com.dantech.skimbrowser.domain.url.registrableDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class InterruptDecisionTest {

    @Test
    fun registrableDomainKeepsThePublicSuffix() {
        assertEquals("google.com", registrableDomain("accounts.google.com"))
        assertEquals("example.co.uk", registrableDomain("www.example.co.uk"))
        assertEquals("example.co.uk", registrableDomain("blog.example.co.uk"))
        assertEquals("example.com", registrableDomain("example.com"))
        assertEquals("127.0.0.1", registrableDomain("127.0.0.1"))
    }

    @Test
    fun popupsAndRedirectsFollowTheTable() {
        cases.forEach { case ->
            assertEquals(case.name, case.verdict, decideInterrupt(case.request, case.policy))
        }
    }

    private data class Case(val name: String, val request: InterruptRequest, val verdict: InterruptVerdict, val policy: InterruptPolicy = InterruptPolicy())

    private val cases = listOf(
        Case(
            "a popup with a gesture opens",
            popup(gesture = true),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a popup without a gesture is blocked",
            popup(gesture = false),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "popup blocking off lets a page open a window",
            popup(gesture = false),
            InterruptVerdict.ALLOW,
            InterruptPolicy(blockPopups = false),
        ),
        Case(
            "a site allowed to open popups does",
            popup(gesture = false, source = "https://www.shop.example/sale"),
            InterruptVerdict.ALLOW,
            InterruptPolicy(popupAllowHosts = setOf("shop.example")),
        ),
        Case(
            "an allow entry for another site does not open this popup",
            popup(gesture = false, source = "https://shop.example/sale"),
            InterruptVerdict.BLOCK,
            InterruptPolicy(popupAllowHosts = setOf("other.example")),
        ),
        Case(
            "a click to another site loads",
            nav(gesture = true, source = PAGE, target = "https://ads.example/landing"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a search result click loads",
            nav(gesture = true, source = "https://www.google.com/search?q=skim", target = "https://shop.example/item"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "the same site can navigate on its own",
            nav(gesture = false, source = "https://blog.example.com/a", target = "https://example.com/b"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a co.uk site stays one site",
            nav(gesture = false, source = "https://blog.example.co.uk/a", target = "https://www.example.co.uk/b"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a jump to another site right after load is blocked",
            nav(gesture = false, source = PAGE, target = "https://ads.example/offer", sinceTouch = null),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "a jump while the page sits idle is blocked",
            nav(gesture = false, source = PAGE, target = "https://ads.example/offer", sinceTouch = IDLE_AFTER_TOUCH_MS),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "a jump just after a touch is allowed",
            nav(gesture = false, source = PAGE, target = "https://pay.example/checkout", sinceTouch = 1_000),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a touch 29 seconds ago still counts",
            nav(gesture = false, source = PAGE, target = "https://pay.example/checkout", sinceTouch = IDLE_AFTER_TOUCH_MS - 1),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "odd redirect blocking off allows the jump",
            nav(gesture = false, source = PAGE, target = "https://ads.example/offer"),
            InterruptVerdict.ALLOW,
            InterruptPolicy(blockOddRedirects = false),
        ),
        Case(
            "a site allowed to redirect may leave",
            nav(gesture = false, source = PAGE, target = "https://ads.example/offer"),
            InterruptVerdict.ALLOW,
            InterruptPolicy(redirectAllowHosts = setOf("news.example")),
        ),
        Case(
            "an oauth return with code and state is allowed",
            nav(
                gesture = false,
                source = "https://idp.example/resume",
                target = "https://shop.example/callback?code=abc&state=xyz",
            ),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a token in the fragment is not an oauth return",
            nav(gesture = false, source = PAGE, target = "https://shop.example/cb#id_token=eyJ"),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "an id token query is allowed",
            nav(gesture = false, source = PAGE, target = "https://shop.example/cb?id_token=eyJ"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a lone state parameter is not an oauth return",
            nav(gesture = false, source = PAGE, target = "https://ads.example/land?state=1"),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "a login domain may send the person back",
            nav(gesture = false, source = "https://accounts.google.com/o/oauth2", target = "https://shop.example/cb"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "paypal may finish a payment",
            nav(gesture = false, source = "https://www.paypal.com/checkout", target = "https://shop.example/paid"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a publisher may not jump to paypal on its own",
            nav(gesture = false, source = PAGE, target = "https://www.paypal.com/checkout"),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "the first load of a tab is not a redirect",
            nav(gesture = false, source = null, target = "https://shop.example/"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "a frame inside the page is left alone",
            nav(gesture = false, source = PAGE, target = "https://ads.example/pixel", mainFrame = false),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "an intent without a gesture is blocked",
            nav(gesture = false, source = PAGE, target = "intent://scan/#Intent;scheme=zxing;end"),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "an intent without a gesture stays blocked when odd redirects are off",
            nav(gesture = false, source = PAGE, target = "intent://scan/#Intent;scheme=zxing;end"),
            InterruptVerdict.BLOCK,
            InterruptPolicy(blockOddRedirects = false),
        ),
        Case(
            "an intent after a gesture is allowed",
            nav(gesture = true, source = PAGE, target = "tel:+6561234567"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "an unknown scheme without a gesture is blocked",
            nav(gesture = false, source = PAGE, target = "skimapp://open"),
            InterruptVerdict.BLOCK,
        ),
        Case(
            "a site allowed to redirect may open an app link",
            nav(gesture = false, source = PAGE, target = "skimapp://open"),
            InterruptVerdict.ALLOW,
            InterruptPolicy(redirectAllowHosts = setOf("news.example")),
        ),
        Case(
            "file javascript and content are not prompted here",
            nav(gesture = false, source = PAGE, target = "file:///sdcard/secret.txt"),
            InterruptVerdict.ALLOW,
        ),
        Case(
            "about blank stays in the page",
            nav(gesture = false, source = PAGE, target = "about:blank"),
            InterruptVerdict.ALLOW,
        ),
    )

    private fun popup(gesture: Boolean, source: String? = PAGE) = InterruptRequest(
        kind = InterruptKind.POPUP,
        sourceUrl = source,
        targetUrl = "",
        hasGesture = gesture,
    )

    private fun nav(
        gesture: Boolean,
        source: String?,
        target: String,
        sinceTouch: Long? = null,
        mainFrame: Boolean = true,
    ) = InterruptRequest(
        kind = InterruptKind.TOP_FRAME,
        sourceUrl = source,
        targetUrl = target,
        hasGesture = gesture,
        isMainFrame = mainFrame,
        millisSinceInteraction = sinceTouch,
    )

    private companion object {
        const val PAGE = "https://news.example/story"
    }
}
