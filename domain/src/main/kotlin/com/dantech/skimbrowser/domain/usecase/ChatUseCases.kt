package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.DeletedChat
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage

class LoadSavedChat(private val chats: ChatRepository) {
    suspend operator fun invoke(id: Long): SavedChat? = chats.load(id)
}

class CreateSavedChat(private val chats: ChatRepository) {
    suspend operator fun invoke(chat: SavedChat): Long = chats.createChat(chat)
}

class ReadSavedMessages(private val chats: ChatRepository) {
    suspend operator fun invoke(chatId: Long): List<SavedMessage> = chats.messages(chatId)
}

class AddSavedMessage(private val chats: ChatRepository) {
    suspend operator fun invoke(message: SavedMessage): Long = chats.addMessage(message)
}

class UpdateSavedChat(private val chats: ChatRepository) {
    suspend operator fun invoke(chat: SavedChat) = chats.updateChat(chat)
}

class UpdateSavedMessage(private val chats: ChatRepository) {
    suspend operator fun invoke(message: SavedMessage) = chats.updateMessage(message)
}

class DeleteAllSkimChats(private val chats: ChatRepository) {
    suspend operator fun invoke() = chats.deleteAll()
}

class RemoveSkimChat(private val chats: ChatRepository) {
    suspend operator fun invoke(id: Long): DeletedChat? = chats.delete(id)
}

class RestoreSkimChat(private val chats: ChatRepository) {
    suspend operator fun invoke(deleted: DeletedChat) = chats.restore(deleted)
}
