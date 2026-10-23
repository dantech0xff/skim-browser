package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.usecase.DeleteAllSkimChats
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry

/**
 * Deletes Skim's saved chats. [deleteAllAiData] also forgets every site's Skim AI decision, so the
 * next use on a site asks again. [clearSkimHistory] leaves those decisions as they are.
 *
 * Both cancel a Skim-chat Undo that is still waiting and close its snackbar first, so Undo cannot
 * put a chat back after the delete. Chats that exist only in memory hear the deletion through
 * the chat repository's removals; this class does not touch a tab's chat id itself.
 */
class AiDataEraser(
    private val deleteAllChats: DeleteAllSkimChats,
    private val sites: SitePermissionRepository,
    private val undo: UndoRegistry,
    private val snackbar: SnackbarController,
) {

    /** Deletes every saved chat and every site's Skim AI decision. */
    suspend fun deleteAllAiData() {
        dropChats()
        sites.deleteAllSkimAiData()
    }

    /** Deletes every saved chat and leaves site decisions untouched. */
    suspend fun clearSkimHistory() {
        dropChats()
    }

    private suspend fun dropChats() {
        undo.cancelAll(UndoKind.SKIM_CHAT)
        snackbar.dismissFor(UndoKind.SKIM_CHAT)
        deleteAllChats()
    }
}
