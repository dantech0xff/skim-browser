package com.dantech.skimbrowser.data.settings

import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** The Reader view typeface (08b). */
enum class ReaderFont { SANS_SERIF, SERIF }

/**
 * Every user setting. The defaults are the states drawn in Settings (12), AI & privacy (10) and
 * Reader text settings (08b), except the reader text size, which starts at 100%.
 */
data class SkimSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val theme: ThemeSetting = ThemeSetting.System,
    val askBeforeSending: Boolean = true,
    val keepChatHistory: Boolean = true,
    /** Always within [READER_TEXT_SCALE_RANGE]. */
    val readerTextScalePercent: Int = DEFAULT_READER_TEXT_SCALE,
    val readerFont: ReaderFont = ReaderFont.SANS_SERIF,
    val onboardingCompleted: Boolean = false,
    /** The normal tab shown last; incognito tabs are never remembered. */
    val lastTabId: String? = null,
) {
    companion object {
        const val DEFAULT_READER_TEXT_SCALE = 100
        val READER_TEXT_SCALE_RANGE = 80..200
    }
}
