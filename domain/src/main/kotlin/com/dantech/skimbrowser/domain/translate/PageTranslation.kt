package com.dantech.skimbrowser.domain.translate

sealed interface PageTranslation {
    data object Idle : PageTranslation

    data class Translating(val done: Int, val total: Int) : PageTranslation

    data class Ready(val showingOriginal: Boolean, val note: Note?) : PageTranslation

    data class Failed(val reason: Note, val host: String) : PageTranslation

    enum class Note { Incomplete, Offline, Limit, Unavailable, NotAllowed, Empty, Incognito }
}
