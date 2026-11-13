package com.dantech.skimbrowser.domain.phishing

/**
 * A scam or phishing warning for one navigation. [Heuristic] is Skim's on-device check.
 * [SafeBrowsing] is a hit from Android System WebView. Neither kind leaves the phone toward Skim.
 */
sealed interface SafetyWarning {
    val url: String
    val host: String

    /** The address itself looks deceptive. [brand] is the well-known name it imitates, when one does. */
    data class Heuristic(
        override val url: String,
        override val host: String,
        val reason: PhishingReason,
        val brand: String?,
    ) : SafetyWarning

    /** WebView Safe Browsing refused the navigation. The threat is the platform's category. */
    data class SafeBrowsing(
        override val url: String,
        override val host: String,
        val threat: SafeBrowsingThreat,
    ) : SafetyWarning
}

/** Why the on-device check stopped the address. */
enum class PhishingReason {
    /** The URL has user info (`name@host`). The host after @ is the site that would open. */
    USER_INFO,

    /** The host is a public raw IP. Loopback, private, and link-local addresses are left alone. */
    RAW_IP,

    /** A well-known site's name is in front of a different registrable domain. */
    SUBDOMAIN,

    /** The registrable name is a typo, a leet spelling, or the brand's name on another registry. */
    LOOKALIKE,

    /** Letters from another writing system fold into a well-known name, including punycode. */
    HOMOGRAPH,

    /** One label mixes writing systems and does not fold into a listed brand. */
    MIXED_SCRIPT,
}

/** The platform threat passed to `onSafeBrowsingHit`. Unknown covers a code this version does not name. */
enum class SafeBrowsingThreat {
    UNKNOWN,
    MALWARE,
    PHISHING,
    UNWANTED_SOFTWARE,
    BILLING,
}
