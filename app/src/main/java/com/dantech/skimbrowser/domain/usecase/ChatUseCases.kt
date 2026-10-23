package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.DeletedChat
import com.dantech.skimbrowser.domain.chat.SavedChat

class LoadSavedChat(private val chats: ChatRepository) {
    suspend operator fun invoke(id: Long): SavedChat? = chats.load(id)
}

class RemoveSkimChat(private val chats: ChatRepository) {
    suspend operator fun invoke(id: Long): DeletedChat? = chats.delete(id)
}

class RestoreSkimChat(private val chats: ChatRepository) {
    suspend operator fun invoke(deleted: DeletedChat) = chats.restore(deleted)
}
