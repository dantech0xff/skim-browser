package com.dantech.skimbrowser.browser

import android.net.http.SslCertificate
import com.dantech.skimbrowser.domain.certificate.certificateFingerprint

/**
 * The bytes that identify [certificate] for this process.
 *
 * The encoded certificate is the identity. When the platform does not hand those bytes over, the
 * names and dates stand in, so two different certificates still do not collapse into one key.
 */
fun certificateMaterial(certificate: SslCertificate): ByteArray {
    val encoded = SslCertificate.saveState(certificate)?.getByteArray(ENCODED_CERTIFICATE)
    if (encoded != null && encoded.isNotEmpty()) return encoded
    return buildString {
        append(certificate.issuedTo?.dName)
        append('\u0000')
        append(certificate.issuedBy?.dName)
        append('\u0000')
        append(certificate.validNotBeforeDate?.time)
        append('\u0000')
        append(certificate.validNotAfterDate?.time)
    }.toByteArray(Charsets.UTF_8)
}

fun certificateFingerprintOf(certificate: SslCertificate): String = certificateFingerprint(certificateMaterial(certificate))

private const val ENCODED_CERTIFICATE = "x509-certificate"
