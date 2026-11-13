package com.dantech.skimbrowser.browser

import androidx.annotation.MainThread
import com.dantech.skimbrowser.domain.phishing.PhishingSession
import com.dantech.skimbrowser.domain.phishing.SafeBrowsingThreat
import com.dantech.skimbrowser.domain.phishing.SafetyWarning
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether scam warnings are on, and which hosts this process may open anyway.
 * Safe Browsing callbacks stay here until the person chooses, so a destroyed WebView can still
 * answer the one it was holding. The switch is applied by the window; this object does not read
 * the settings file itself.
 */
@MainThread
interface SafetyGate {
    val enabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun heuristic(url: String, incognito: Boolean): SafetyWarning.Heuristic?

    /**
     * [respond] runs once. True continues the load. False goes back. A second hit for the same
     * tab answers the previous callback by going back.
     */
    fun offerSafeBrowsing(
        tabId: String,
        url: String,
        host: String,
        threat: SafeBrowsingThreat,
        incognito: Boolean,
        respond: (proceed: Boolean) -> Unit,
    ): SafetyWarning.SafeBrowsing?

    fun allowHeuristic(host: String, incognito: Boolean)

    /**
     * Allows [host] and resumes its paused WebView callback when one is still waiting.
     * Returns false when that callback was already answered, so the caller loads [host] itself.
     */
    fun proceedSafeBrowsing(tabId: String, host: String, incognito: Boolean): Boolean

    fun cancelSafeBrowsing(tabId: String)

    companion object {
        /** Heuristics off. A Safe Browsing callback is allowed through so a test WebView cannot hang. */
        val Off: SafetyGate = object : SafetyGate {
            override val enabled: Boolean = true
            override fun setEnabled(enabled: Boolean) = Unit
            override fun heuristic(url: String, incognito: Boolean): SafetyWarning.Heuristic? = null
            override fun offerSafeBrowsing(
                tabId: String,
                url: String,
                host: String,
                threat: SafeBrowsingThreat,
                incognito: Boolean,
                respond: (proceed: Boolean) -> Unit,
            ): SafetyWarning.SafeBrowsing? {
                respond(true)
                return null
            }

            override fun allowHeuristic(host: String, incognito: Boolean) = Unit
            override fun proceedSafeBrowsing(tabId: String, host: String, incognito: Boolean) = false
            override fun cancelSafeBrowsing(tabId: String) = Unit
        }
    }
}

@Singleton
class ProcessSafetyGate @Inject constructor() : SafetyGate {
    private val session = PhishingSession()
    private val pending = HashMap<String, (Boolean) -> Unit>()

    @Volatile
    override var enabled: Boolean = true
        private set

    override fun setEnabled(enabled: Boolean) {
        val turningOff = this.enabled && !enabled
        this.enabled = enabled
        if (turningOff) answerPending(proceed = true)
    }

    override fun heuristic(url: String, incognito: Boolean): SafetyWarning.Heuristic? =
        session.heuristic(url, incognito, enabled = enabled)

    override fun offerSafeBrowsing(
        tabId: String,
        url: String,
        host: String,
        threat: SafeBrowsingThreat,
        incognito: Boolean,
        respond: (proceed: Boolean) -> Unit,
    ): SafetyWarning.SafeBrowsing? {
        if (!enabled || session.allowsSafeBrowsing(host, incognito)) {
            respond(true)
            return null
        }
        pending.remove(tabId)?.invoke(false)
        pending[tabId] = respond
        return SafetyWarning.SafeBrowsing(url, host, threat)
    }

    override fun allowHeuristic(host: String, incognito: Boolean) {
        session.allowHeuristic(host, incognito)
    }

    override fun proceedSafeBrowsing(tabId: String, host: String, incognito: Boolean): Boolean {
        session.allowSafeBrowsing(host, incognito)
        val respond = pending.remove(tabId) ?: return false
        respond(true)
        return true
    }

    override fun cancelSafeBrowsing(tabId: String) {
        pending.remove(tabId)?.invoke(false)
    }

    private fun answerPending(proceed: Boolean) {
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.invoke(proceed) }
    }
}

/** Platform threat codes from `onSafeBrowsingHit`. An unrecognized code stays [SafeBrowsingThreat.UNKNOWN]. */
fun safeBrowsingThreat(code: Int): SafeBrowsingThreat = when (code) {
    THREAT_MALWARE -> SafeBrowsingThreat.MALWARE
    THREAT_PHISHING -> SafeBrowsingThreat.PHISHING
    THREAT_UNWANTED -> SafeBrowsingThreat.UNWANTED_SOFTWARE
    THREAT_BILLING -> SafeBrowsingThreat.BILLING
    else -> SafeBrowsingThreat.UNKNOWN
}

/** Values of `WebViewClient.SAFE_BROWSING_THREAT_*`, kept as numbers so API 26 can compile them. */
private const val THREAT_MALWARE = 1
private const val THREAT_PHISHING = 2
private const val THREAT_UNWANTED = 3
private const val THREAT_BILLING = 4
