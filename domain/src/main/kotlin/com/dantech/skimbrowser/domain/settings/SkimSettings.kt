package com.dantech.skimbrowser.domain.settings

/**
 * Every user setting. The defaults are the states drawn in Settings (12), AI & privacy (10) and
 * Reader text settings (08b), except the reader text size, which starts at 100%.
 */
data class SkimSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val theme: ThemeSetting = ThemeSetting.System,
    val askBeforeSending: Boolean = true,
    val keepChatHistory: Boolean = true,
    /**
     * Whether a like or dislike may be sent to the proxy. Null until the person is asked.
     * False means they declined. Turning it on does not send ratings already on the phone.
     */
    val sendRatings: Boolean? = null,
    /** Always within [READER_TEXT_SCALE_RANGE]. */
    val readerTextScalePercent: Int = DEFAULT_READER_TEXT_SCALE,
    /**
     * Page text size, as a percent. Null follows the system font scale. A stored value is within
     * [READER_TEXT_SCALE_RANGE] and wins over the system scale until it is cleared.
     */
    val pageTextZoomPercent: Int? = null,
    val translationLanguageTag: String? = null,
    val readerFont: ReaderFont = ReaderFont.SANS_SERIF,
    val onboardingCompleted: Boolean = false,
    /** The normal tab shown last; incognito tabs are never remembered. */
    val lastTabId: String? = null,
    /**
     * Scam and phishing warnings, including Android System WebView Safe Browsing.
     * On until the person turns it off. The choice is kept on the phone.
     */
    val scamWarnings: Boolean = true,
    val blockPopups: Boolean = true,
    /** A page cannot jump to another site while it has not been touched, or while it sits idle. */
    val blockOddRedirects: Boolean = true,
    /** Common cookie consent banners are hidden, and a page they locked can scroll. */
    val hideCookieBanners: Boolean = true,
) {
    companion object {
        const val DEFAULT_READER_TEXT_SCALE = 100
        val READER_TEXT_SCALE_RANGE = 80..200
    }
}
