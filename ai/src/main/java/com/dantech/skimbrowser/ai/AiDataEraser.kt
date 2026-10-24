package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.domain.undo.UndoSubject
import com.dantech.skimbrowser.domain.usecase.DeleteAllSkimChats
import javax.inject.Inject
import javax.inject.Singleton

typealias AiDataCleared = com.dantech.skimbrowser.domain.ai.AiDataCleared

/**
 * Deletes Skim's saved chats. [deleteAllAiData] also forgets every site's Skim AI decision, so the
 * next use on a site asks again. [clearSkimHistory] leaves those decisions as they are.
 *
 * Both cancel a Skim-chat Undo that is still waiting and close its snackbar first, so Undo cannot
 * put a chat back after the delete. Chats that exist only in memory hear the deletion through
 * the chat repository's removals; this class does not touch a tab's chat id itself.
 */
@Singleton
class AiDataEraser @Inject constructor(
    private val deleteAllChats: DeleteAllSkimChats,
    private val sites: SitePermissionRepository,
    private val pendingUndo: PendingUndo,
) : SkimDataEraser {

    /** Deletes every saved chat and every site's Skim AI decision. */
    override suspend fun deleteAllAiData(): AiDataCleared {
        dropChats()
        sites.deleteAllSkimAiData()
        return AiDataCleared.CHATS_AND_SITE_CHOICES
    }

    /** Deletes every saved chat and leaves site decisions untouched. */
    override suspend fun clearSkimHistory(): AiDataCleared {
        dropChats()
        return AiDataCleared.CHATS
    }

    private suspend fun dropChats() {
        pendingUndo.cancel(UndoSubject.SKIM_CHAT)
        deleteAllChats()
    }
}
