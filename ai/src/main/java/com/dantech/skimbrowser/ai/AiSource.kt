package com.dantech.skimbrowser.ai

/**
 * One page a request would send. [host] is the host the extractor actually read, so a page that
 * navigates itself after permission was granted no longer matches the host the person approved.
 */
data class AiSource(val tabId: String, val incognito: Boolean, val host: String)

/** Why a request is allowed to include page text. */
enum class Consent {
    /** A summary or question about one page the person approved. */
    SITE_APPROVED,

    /** An action across open tabs. A site set to ask again is left out. */
    TABS_ACTION,
}
