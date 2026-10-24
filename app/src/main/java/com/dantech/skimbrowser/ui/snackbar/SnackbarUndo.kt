package com.dantech.skimbrowser.ui.snackbar

import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.domain.undo.UndoSubject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PendingUndo] over the process-wide undo registry and snackbar. Cancel finalizes the tokens and
 * dismisses the snackbar that offers them, in that order.
 */
@Singleton
class SnackbarUndo @Inject constructor(
    private val undo: UndoRegistry,
    private val snackbar: SnackbarController,
) : PendingUndo {
    override suspend fun cancel(subject: UndoSubject) {
        val kind = when (subject) {
            UndoSubject.HISTORY -> UndoKind.HISTORY
            UndoSubject.SKIM_CHAT -> UndoKind.SKIM_CHAT
        }
        undo.cancelAll(kind)
        snackbar.dismissFor(kind)
    }
}
