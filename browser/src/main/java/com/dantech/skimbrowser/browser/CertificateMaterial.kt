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
    val to = certificate.issuedTo
    val by = certificate.issuedBy
    return listOf(
        to?.dName,
        to?.cName,
        to?.oName,
        to?.uName,
        by?.dName,
        by?.cName,
        by?.oName,
        by?.uName,
        certificate.validNotBeforeDate?.time?.toString(),
        certificate.validNotAfterDate?.time?.toString(),
    ).joinToString("\u0000").toByteArray(Charsets.UTF_8)
}

/** The session key of [certificate]. */
fun certificateFingerprintOf(certificate: SslCertificate): String = certificateFingerprint(certificateMaterial(certificate))

private const val ENCODED_CERTIFICATE = "x509-certificate"
