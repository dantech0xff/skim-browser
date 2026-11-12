package com.dantech.skimbrowser.domain.certificate

import java.security.MessageDigest

/**
 * What is wrong with a site certificate, taken from the platform primary error.
 *
 * The numbers are the platform's public primary-error values. An unknown value is [Invalid].
 */
enum class CertificateFault {
    NotYetValid,
    Expired,
    HostnameMismatch,
    Untrusted,
    DateInvalid,
    Invalid,
}

data class CertificateKey(val host: String, val fingerprint: String)

/** A main-frame certificate failure the tab shows. The load was already cancelled. */
data class CertificateWarning(
    val url: String,
    val host: String,
    val fingerprint: String,
    val fault: CertificateFault,
)

enum class CertificateAction { Proceed, Cancel }

/**
 * Allowlists for this process. Normal tabs and incognito tabs do not share one.
 *
 * Nothing here is written down. A new instance is empty, which is what a new process starts with.
 * [clearIncognito] drops the private list when that session ends. The normal list lasts until the
 * process dies.
 */
class CertificateSessions {
    private val normal = HashSet<CertificateKey>()
    private val incognito = HashSet<CertificateKey>()

    fun action(incognito: Boolean, key: CertificateKey): CertificateAction = synchronized(this) {
        if (!key.isUsable()) return CertificateAction.Cancel
        if (key in list(incognito)) CertificateAction.Proceed else CertificateAction.Cancel
    }

    fun allow(incognito: Boolean, key: CertificateKey) {
        synchronized(this) {
            if (key.isUsable()) list(incognito).add(key)
        }
    }

    fun clearIncognito() {
        synchronized(this) { incognito.clear() }
    }

    private fun list(incognito: Boolean): MutableSet<CertificateKey> = if (incognito) this.incognito else normal
}

fun certificateFingerprint(material: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
    val hex = "0123456789abcdef".toCharArray()
    val out = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        out[index * 2] = hex[value ushr 4]
        out[index * 2 + 1] = hex[value and 0x0f]
    }
    return String(out)
}

fun certificateFault(primaryError: Int): CertificateFault = when (primaryError) {
    0 -> CertificateFault.NotYetValid
    1 -> CertificateFault.Expired
    2 -> CertificateFault.HostnameMismatch
    3 -> CertificateFault.Untrusted
    4 -> CertificateFault.DateInvalid
    else -> CertificateFault.Invalid
}

private fun CertificateKey.isUsable(): Boolean = host.isNotEmpty() && fingerprint.isNotEmpty()
