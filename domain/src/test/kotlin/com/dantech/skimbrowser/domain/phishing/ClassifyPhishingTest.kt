package com.dantech.skimbrowser.domain.phishing

import java.net.IDN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifyPhishingTest {

    @Test
    fun realSitesAndLoopbackAreNotWarnings() {
        assertNull(classifyPhishing("https://www.paypal.com/login"))
        assertNull(classifyPhishing("https://accounts.google.com/"))
        assertNull(classifyPhishing("https://momo.vn/"))
        assertNull(classifyPhishing("https://zalopay.vn/"))
        assertNull(classifyPhishing("https://vietcombank.com.vn/"))
        assertNull(classifyPhishing("https://google.com.vn/"))
        assertNull(classifyPhishing("https://example.com/"))
        assertNull(classifyPhishing("https://live.example/"))
        assertNull(classifyPhishing("https://office.net/"))
        assertNull(classifyPhishing("https://crypto.example/"))
        assertNull(classifyPhishing("https://live.example.com/"))
        assertNull(classifyPhishing("https://office.example.com/"))
        assertNull(classifyPhishing("https://crypto.example.com/"))
        assertNull(classifyPhishing("https://www.live.com/"))
        assertNull(classifyPhishing("https://www.office.com/"))
        assertNull(classifyPhishing("https://www.crypto.com/"))
        assertEquals(PhishingReason.SUBDOMAIN, classifyPhishing("https://office.com.evil.tld/")?.reason)
        assertNull(classifyPhishing("https://127.0.0.1/"))
        assertNull(classifyPhishing("http://127.4.5.6/admin"))
        assertNull(classifyPhishing("https://[::1]/"))
        assertNull(classifyPhishing("https://localhost/"))
        assertNull(classifyPhishing("notaurl"))
    }

    @Test
    fun userInfoHidesTheRealHost() {
        val hit = classifyPhishing("https://paypal.com@evil.example/login")
        assertEquals(PhishingReason.USER_INFO, hit?.reason)
        assertEquals("evil.example", hit?.host)
        assertNull(hit?.brand)
        assertEquals(PhishingReason.USER_INFO, classifyPhishing("https://user:@evil.example/")?.reason)
        assertNull(classifyPhishing("https://@evil.example/"))
        assertNull(classifyPhishing("https://:@paypal.com/login"))
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("https://@8.8.8.8/")?.reason)
    }

    @Test
    fun aPublicIpWarnsAndABrandNameOnAnotherSiteDoes() {
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://8.8.8.8/paypal")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://134744072/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://0x8.0x8.0x8.0x8/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://010.010.010.010/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://8.8.2056/")?.reason)
        assertNull(classifyPhishing("http://2130706433/"))
        assertNull(classifyPhishing("http://0x7f.0.0.1/"))
        assertNull(classifyPhishing("https://google.ca/"))
        assertNull(classifyPhishing("https://www.google.com.sg/"))
        assertNull(classifyPhishing("https://amazon.de/"))
        assertNull(classifyPhishing("https://www.paypal.co.uk/"))
        assertNull(classifyPhishing("https://www.google.com.co/"))
        assertNull(classifyPhishing("https://google.com.om/"))
        assertEquals(PhishingReason.LOOKALIKE, classifyPhishing("https://google.tk/")?.reason)
        assertEquals(PhishingReason.LOOKALIKE, classifyPhishing("https://paypai.ca/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://172.15.0.1/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://172.32.0.1/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://11.0.0.1/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://100.63.0.1/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://100.128.0.1/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("https://[2001:db8::1]/")?.reason)
        assertEquals(PhishingReason.RAW_IP, classifyPhishing("http://[fec0::1]/")?.reason)

        assertNull(classifyPhishing("http://192.168.1.1/"))
        assertNull(classifyPhishing("http://10.0.0.2:8080/admin"))
        assertNull(classifyPhishing("http://172.16.0.1/"))
        assertNull(classifyPhishing("http://172.31.255.255/"))
        assertNull(classifyPhishing("http://169.254.1.1/"))
        assertNull(classifyPhishing("http://100.64.0.1/"))
        assertNull(classifyPhishing("http://100.127.255.254/"))
        assertNull(classifyPhishing("http://[fc00::1]/"))
        assertNull(classifyPhishing("http://[fd12:3456::1]/"))
        assertNull(classifyPhishing("http://[fe80::1]/"))
        assertNull(classifyPhishing("http://[febf::1]/"))
        assertNull(classifyPhishing("http://[::ffff:192.168.0.1]/"))

        val planted = classifyPhishing("https://paypal.com.evil.tld/signin")
        assertEquals(PhishingReason.SUBDOMAIN, planted?.reason)
        assertEquals("PayPal", planted?.brand)

        val word = classifyPhishing("https://paypal.evil.example/account")
        assertEquals(PhishingReason.SUBDOMAIN, word?.reason)
        assertEquals("PayPal", word?.brand)
    }

    @Test
    fun typosLeetAndConfusableLettersNameTheBrand() {
        val typo = classifyPhishing("https://paypai.com/")
        assertEquals(PhishingReason.LOOKALIKE, typo?.reason)
        assertEquals("PayPal", typo?.brand)

        val leet = classifyPhishing("https://g00gle.com/")
        assertEquals(PhishingReason.LOOKALIKE, leet?.reason)
        assertEquals("Google", leet?.brand)

        val momo = classifyPhishing("https://m0mo.vn/")
        assertEquals(PhishingReason.LOOKALIKE, momo?.reason)
        assertEquals("MoMo", momo?.brand)

        val otherRegistry = classifyPhishing("https://paypal.cm/")
        assertEquals(PhishingReason.LOOKALIKE, otherRegistry?.reason)
        assertEquals("PayPal", otherRegistry?.brand)

        val spoofed = "https://${IDN.toASCII("\u0430pple.com")}/"
        val homograph = classifyPhishing(spoofed)
        assertEquals(PhishingReason.HOMOGRAPH, homograph?.reason)
        assertEquals("Apple", homograph?.brand)

        val mixed = classifyPhishing("https://a\u044f.example/")
        assertEquals(PhishingReason.MIXED_SCRIPT, mixed?.reason)
        assertNull(mixed?.brand)
    }

    @Test
    fun aSingleScriptInternationalNameIsNotAWarning() {
        val idn = "https://${IDN.toASCII("th\u01b0.example")}/"
        assertNull(classifyPhishing(idn))
    }

    @Test
    fun proceedIsRememberedPerHostAndPerKindUntilTheProcessEnds() {
        val session = PhishingSession()
        val url = "https://paypai.com/login"
        assertEquals(PhishingReason.LOOKALIKE, session.heuristic(url, incognito = false, enabled = true)?.reason)
        assertNull(session.heuristic(url, incognito = false, enabled = false))

        session.allowHeuristic("paypai.com", incognito = false)
        assertNull(session.heuristic(url, incognito = false, enabled = true))
        assertEquals(
            PhishingReason.LOOKALIKE,
            session.heuristic(url, incognito = true, enabled = true)?.reason,
        )

        session.allowSafeBrowsing("paypai.com", incognito = true)
        assertNull(session.heuristic(url, incognito = true, enabled = true))
        assertEquals(true, session.allowsSafeBrowsing("paypai.com", incognito = true))
        assertEquals(false, session.allowsSafeBrowsing("paypai.com", incognito = false))
    }
}
