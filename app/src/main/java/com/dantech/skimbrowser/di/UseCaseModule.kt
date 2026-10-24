package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.ai.AskPage
import com.dantech.skimbrowser.domain.ai.AskTabs
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.OverviewTabs
import com.dantech.skimbrowser.domain.ai.RetryAnswer
import com.dantech.skimbrowser.domain.ai.SummarizePage
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
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
import com.dantech.skimbrowser.search.SuggestionService
import com.dantech.skimbrowser.system.ShareImage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Each use case is the generation's instance, so [ProcessGraph.close] drops it. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun removeHistoryEntry(graph: ProcessGraph): RemoveHistoryEntry = graph.removeHistoryEntry()
    @Provides fun restoreHistoryEntry(graph: ProcessGraph): RestoreHistoryEntry = graph.restoreHistoryEntry()
    @Provides fun matchHistory(graph: ProcessGraph): MatchHistory = graph.matchHistory()
    @Provides fun addBookmark(graph: ProcessGraph): AddBookmark = graph.addBookmark()
    @Provides fun updateBookmark(graph: ProcessGraph): UpdateBookmark = graph.updateBookmark()
    @Provides fun moveBookmark(graph: ProcessGraph): MoveBookmark = graph.moveBookmark()
    @Provides fun deleteBookmark(graph: ProcessGraph): DeleteBookmark = graph.deleteBookmark()
    @Provides fun restoreBookmark(graph: ProcessGraph): RestoreBookmark = graph.restoreBookmark()
    @Provides fun matchBookmarks(graph: ProcessGraph): MatchBookmarks = graph.matchBookmarks()
    @Provides fun setSearchEngine(graph: ProcessGraph): SetSearchEngine = graph.setSearchEngine()
    @Provides fun setTheme(graph: ProcessGraph): SetTheme = graph.setTheme()
    @Provides fun setAskBeforeSending(graph: ProcessGraph): SetAskBeforeSending = graph.setAskBeforeSending()
    @Provides fun setKeepChatHistory(graph: ProcessGraph): SetKeepChatHistory = graph.setKeepChatHistory()
    @Provides fun setReaderTextScale(graph: ProcessGraph): SetReaderTextScale = graph.setReaderTextScale()
    @Provides fun setReaderFont(graph: ProcessGraph): SetReaderFont = graph.setReaderFont()
    @Provides fun completeOnboarding(graph: ProcessGraph): CompleteOnboarding = graph.completeOnboarding()
    @Provides fun loadSavedChat(graph: ProcessGraph): LoadSavedChat = graph.loadSavedChat()
    @Provides fun createSavedChat(graph: ProcessGraph): CreateSavedChat = graph.createSavedChat()
    @Provides fun readSavedMessages(graph: ProcessGraph): ReadSavedMessages = graph.readSavedMessages()
    @Provides fun addSavedMessage(graph: ProcessGraph): AddSavedMessage = graph.addSavedMessage()
    @Provides fun updateSavedChat(graph: ProcessGraph): UpdateSavedChat = graph.updateSavedChat()
    @Provides fun updateSavedMessage(graph: ProcessGraph): UpdateSavedMessage = graph.updateSavedMessage()
    @Provides fun deleteAllSkimChats(graph: ProcessGraph): DeleteAllSkimChats = graph.deleteAllSkimChats()
    @Provides fun removeSkimChat(graph: ProcessGraph): RemoveSkimChat = graph.removeSkimChat()
    @Provides fun restoreSkimChat(graph: ProcessGraph): RestoreSkimChat = graph.restoreSkimChat()
    @Provides fun setSkimAi(graph: ProcessGraph): SetSkimAi = graph.setSkimAi()
    @Provides fun setSitePermission(graph: ProcessGraph): SetSitePermission = graph.setSitePermission()
    @Provides fun openUrlInCurrentTab(graph: ProcessGraph): OpenUrlInCurrentTab = graph.openUrlInCurrentTab()
    @Provides fun importBookmarks(graph: ProcessGraph): ImportBookmarks = graph.importBookmarks()
    @Provides fun summarizePage(graph: ProcessGraph): SummarizePage = graph.summarizePage()
    @Provides fun askPage(graph: ProcessGraph): AskPage = graph.askPage()
    @Provides fun retryAnswer(graph: ProcessGraph): RetryAnswer = graph.retryAnswer()
    @Provides fun overviewTabs(graph: ProcessGraph): OverviewTabs = graph.overviewTabs()
    @Provides fun askTabs(graph: ProcessGraph): AskTabs = graph.askTabs()
    @Provides fun collectOpenTabs(graph: ProcessGraph): CollectOpenTabs = graph.collectOpenTabs()
    @Provides fun clearBrowsingData(graph: ProcessGraph): ClearBrowsingData = graph.clearBrowsingData()
    @Provides fun deleteAllAiData(graph: ProcessGraph): DeleteAllAiData = graph.deleteAllAiData()
    @Provides fun clearSkimHistory(graph: ProcessGraph): ClearSkimHistory = graph.clearSkimHistory()
    @Provides fun suggestions(graph: ProcessGraph): SuggestionService = graph.suggestionService()
    @Provides fun shareImage(graph: ProcessGraph): ShareImage = graph.shareImage()
}
