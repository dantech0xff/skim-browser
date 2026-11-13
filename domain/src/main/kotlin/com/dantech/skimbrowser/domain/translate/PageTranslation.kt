package com.dantech.skimbrowser.domain.translate

/**
 * What Translate page is doing for one tab. [Idle] is the only state with no bar.
 * [Ready] keeps the translations in the page, and [showingOriginal] chooses which text is visible.
 * [note] says why a long page stopped early. [Failed] is a tap that changed no text.
 */
sealed interface PageTranslation {
    data object Idle : PageTranslation

    data class Translating(val done: Int, val total: Int) : PageTranslation

    data class Ready(val showingOriginal: Boolean, val note: Note?) : PageTranslation

    data class Failed(val reason: Note, val host: String) : PageTranslation

    enum class Note { Incomplete, Offline, Limit, Unavailable, NotAllowed, Empty, Incognito }
}
