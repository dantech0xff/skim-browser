package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.browser.BrowsingDataCleaner
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AskPage
import com.dantech.skimbrowser.domain.ai.AskTabs
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.OverviewTabs
import com.dantech.skimbrowser.domain.ai.RetryAnswer
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import com.dantech.skimbrowser.domain.ai.SummarizePage
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.usecase.AddBookmark
import com.dantech.skimbrowser.domain.usecase.AddSavedMessage
import com.dantech.skimbrowser.domain.usecase.ClearBrowsingData
import com.dantech.skimbrowser.domain.usecase.ClearSkimHistory
import com.dantech.skimbrowser.domain.usecase.CompleteOnboarding
import com.dantech.skimbrowser.domain.usecase.CreateSavedChat
import com.dantech.skimbrowser.domain.usecase.DeleteAllAiData
import com.dantech.skimbrowser.domain.usecase.DeleteAllSkimChats
import com.dantech.skimbrowser.domain.usecase.DeleteBookmark
import com.dantech.skimbrowser.domain.usecase.ImportBookmarks
import com.dantech.skimbrowser.domain.usecase.LoadSavedChat
import com.dantech.skimbrowser.domain.usecase.MatchBookmarks
import com.dantech.skimbrowser.domain.usecase.MatchHistory
import com.dantech.skimbrowser.domain.usecase.MoveBookmark
import com.dantech.skimbrowser.domain.usecase.ReadSavedMessages
import com.dantech.skimbrowser.domain.usecase.RemoveHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RemoveSkimChat
import com.dantech.skimbrowser.domain.usecase.RestoreBookmark
import com.dantech.skimbrowser.domain.usecase.RestoreHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RestoreSkimChat
import com.dantech.skimbrowser.domain.usecase.SetAskBeforeSending
import com.dantech.skimbrowser.domain.usecase.SetKeepChatHistory
import com.dantech.skimbrowser.domain.usecase.SetReaderFont
import com.dantech.skimbrowser.domain.usecase.SetReaderTextScale
import com.dantech.skimbrowser.domain.usecase.SetSearchEngine
import com.dantech.skimbrowser.domain.usecase.SetSitePermission
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import com.dantech.skimbrowser.domain.usecase.SetTheme
import com.dantech.skimbrowser.domain.usecase.UpdateBookmark
import com.dantech.skimbrowser.domain.usecase.UpdateSavedChat
import com.dantech.skimbrowser.domain.usecase.UpdateSavedMessage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun removeHistoryEntry(history: HistoryRepository): RemoveHistoryEntry = RemoveHistoryEntry(history)
    @Provides fun restoreHistoryEntry(history: HistoryRepository): RestoreHistoryEntry = RestoreHistoryEntry(history)
    @Provides fun matchHistory(history: HistoryRepository): MatchHistory = MatchHistory(history)
    @Provides fun addBookmark(bookmarks: BookmarkRepository): AddBookmark = AddBookmark(bookmarks)
    @Provides fun updateBookmark(bookmarks: BookmarkRepository): UpdateBookmark = UpdateBookmark(bookmarks)
    @Provides fun moveBookmark(bookmarks: BookmarkRepository): MoveBookmark = MoveBookmark(bookmarks)
    @Provides fun deleteBookmark(bookmarks: BookmarkRepository): DeleteBookmark = DeleteBookmark(bookmarks)
    @Provides fun restoreBookmark(bookmarks: BookmarkRepository): RestoreBookmark = RestoreBookmark(bookmarks)
    @Provides fun matchBookmarks(bookmarks: BookmarkRepository): MatchBookmarks = MatchBookmarks(bookmarks)
    @Provides fun importBookmarks(files: BookmarkFiles): ImportBookmarks = ImportBookmarks(files)
    @Provides fun setSearchEngine(settings: SettingsRepository): SetSearchEngine = SetSearchEngine(settings)
    @Provides fun setTheme(settings: SettingsRepository): SetTheme = SetTheme(settings)
    @Provides fun setAskBeforeSending(settings: SettingsRepository): SetAskBeforeSending = SetAskBeforeSending(settings)
    @Provides fun setKeepChatHistory(settings: SettingsRepository): SetKeepChatHistory = SetKeepChatHistory(settings)
    @Provides fun setReaderTextScale(settings: SettingsRepository): SetReaderTextScale = SetReaderTextScale(settings)
    @Provides fun setReaderFont(settings: SettingsRepository): SetReaderFont = SetReaderFont(settings)
    @Provides fun completeOnboarding(settings: SettingsRepository): CompleteOnboarding = CompleteOnboarding(settings)
    @Provides fun loadSavedChat(chats: ChatRepository): LoadSavedChat = LoadSavedChat(chats)
    @Provides fun createSavedChat(chats: ChatRepository): CreateSavedChat = CreateSavedChat(chats)
    @Provides fun readSavedMessages(chats: ChatRepository): ReadSavedMessages = ReadSavedMessages(chats)
    @Provides fun addSavedMessage(chats: ChatRepository): AddSavedMessage = AddSavedMessage(chats)
    @Provides fun updateSavedChat(chats: ChatRepository): UpdateSavedChat = UpdateSavedChat(chats)
    @Provides fun updateSavedMessage(chats: ChatRepository): UpdateSavedMessage = UpdateSavedMessage(chats)
    @Provides fun deleteAllSkimChats(chats: ChatRepository): DeleteAllSkimChats = DeleteAllSkimChats(chats)
    @Provides fun removeSkimChat(chats: ChatRepository): RemoveSkimChat = RemoveSkimChat(chats)
    @Provides fun restoreSkimChat(chats: ChatRepository): RestoreSkimChat = RestoreSkimChat(chats)
    @Provides fun setSkimAi(sites: SitePermissionRepository): SetSkimAi = SetSkimAi(sites)
    @Provides fun setSitePermission(sites: SitePermissionRepository): SetSitePermission = SetSitePermission(sites)
    @Provides fun openUrlInCurrentTab(tabs: TabsController): OpenUrlInCurrentTab = OpenUrlInCurrentTab(tabs)
    @Provides fun summarizePage(ai: AiAssistant): SummarizePage = SummarizePage(ai)
    @Provides fun askPage(ai: AiAssistant): AskPage = AskPage(ai)
    @Provides fun retryAnswer(ai: AiAssistant): RetryAnswer = RetryAnswer(ai)
    @Provides fun overviewTabs(ai: AiAssistant): OverviewTabs = OverviewTabs(ai)
    @Provides fun askTabs(ai: AiAssistant): AskTabs = AskTabs(ai)
    @Provides fun collectOpenTabs(tabs: TabsController, sites: SitePermissionRepository): CollectOpenTabs = CollectOpenTabs(tabs, sites)
    @Provides fun deleteAllAiData(eraser: SkimDataEraser): DeleteAllAiData = DeleteAllAiData(eraser)
    @Provides fun clearSkimHistory(eraser: SkimDataEraser): ClearSkimHistory = ClearSkimHistory(eraser)

    @Provides
    fun clearBrowsingData(cleaner: BrowsingDataCleaner): ClearBrowsingData = ClearBrowsingData { history, cookies, cache, pages ->
        cleaner.clearAll(history, cookies, cache, pages)
    }
}
