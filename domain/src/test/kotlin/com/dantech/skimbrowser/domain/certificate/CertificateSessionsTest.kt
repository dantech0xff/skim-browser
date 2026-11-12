package com.dantech.skimbrowser.domain.certificate

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateSessionsTest {

    private val sessions = CertificateSessions()
    private val key = CertificateKey("bad.example", FINGERPRINT)
    private val otherCert = CertificateKey("bad.example", OTHER)
    private val otherHost = CertificateKey("other.example", FINGERPRINT)

    @Test
    fun anUnknownCertificateIsCancelled() {
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, key))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = true, key))
    }

    @Test
    fun allowingACertificateProceedsForThatHostAndCertificateOnly() {
        sessions.allow(incognito = false, key)

        assertEquals(CertificateAction.Proceed, sessions.action(incognito = false, key))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, otherCert))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, otherHost))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = true, key))
    }

    @Test
    fun anIncognitoAllowanceStaysOnTheIncognitoList() {
        sessions.allow(incognito = true, key)

        assertEquals(CertificateAction.Proceed, sessions.action(incognito = true, key))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, key))
    }

    @Test
    fun aNewSessionIsEmptyAndTheIncognitoListCanBeDroppedOnItsOwn() {
        sessions.allow(incognito = false, key)
        sessions.allow(incognito = true, key)
        sessions.clearIncognito()

        assertEquals(CertificateAction.Proceed, sessions.action(incognito = false, key))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = true, key))
        assertEquals(CertificateAction.Cancel, CertificateSessions().action(incognito = false, key))
    }

    @Test
    fun aBlankHostOrFingerprintIsNeverAllowed() {
        val blankHost = CertificateKey("", FINGERPRINT)
        val blankFingerprint = CertificateKey("bad.example", "")
        sessions.allow(incognito = false, blankHost)
        sessions.allow(incognito = false, blankFingerprint)

        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, blankHost))
        assertEquals(CertificateAction.Cancel, sessions.action(incognito = false, blankFingerprint))
    }

    @Test
    fun theFingerprintIsTheSha256OfTheCertificateBytes() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            certificateFingerprint("abc".toByteArray()),
        )
        assertEquals(certificateFingerprint(byteArrayOf(1, 2, 3)), certificateFingerprint(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun eachPrimaryErrorMapsToOneFaultAndAnythingElseIsInvalid() {
        assertEquals(
            listOf(
                CertificateFault.NotYetValid,
                CertificateFault.Expired,
                CertificateFault.HostnameMismatch,
                CertificateFault.Untrusted,
                CertificateFault.DateInvalid,
                CertificateFault.Invalid,
            ),
            (0..5).map(::certificateFault),
        )
        assertEquals(CertificateFault.Invalid, certificateFault(-1))
        assertEquals(CertificateFault.Invalid, certificateFault(9))
    }

    private companion object {
        const val FINGERPRINT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
