package com.dantech.skimbrowser.domain.undo

/** A deletion that can still be undone from a snackbar. */
enum class UndoSubject {
    HISTORY,
    SKIM_CHAT,
}

/**
 * Waiting undos. A bulk delete finalizes the matching ones, and drops their snackbar, before the
 * rows are removed, so Undo cannot put them back.
 */
interface PendingUndo {
    suspend fun cancel(subject: UndoSubject)
}
