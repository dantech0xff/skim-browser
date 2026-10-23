package com.dantech.skimbrowser.ui.reader

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Citations that ask Reader view to scroll to a block and flash it. One for the process, so a
 * sheet opened over the reader can point at a paragraph without holding the screen.
 *
 * A request for a tab the reader is not showing, or for a block the article does not have, is
 * ignored by the screen. Nothing is replayed: a citation that arrives while the reader is gone
 * is dropped.
 */
class ReaderHighlights {
    private val events = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 16)

    val requests: SharedFlow<Pair<String, Int>> = events.asSharedFlow()

    /** Asks the reader of [tabId] to show block [blockIndex] of the article on screen. */
    suspend fun request(tabId: String, blockIndex: Int) {
        events.emit(tabId to blockIndex)
    }
}
