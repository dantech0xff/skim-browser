package com.dantech.skimbrowser.ai.di

import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.ai.AiDataEraser
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.ai.ChatSessionStore
import com.dantech.skimbrowser.ai.ClaudeClientProvider
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.ai.TabGrouping
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AskPageAccess
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the Claude client. The credential expression stays in this package. */
@Singleton
class ClaudeClientFactory @Inject constructor() {
    fun create(installId: suspend () -> String): ClaudeClientProvider =
        ClaudeClientProvider(
            baseUrl = BuildConfig.AI_BASE_URL,
            apiKey = (BuildConfig.ANTHROPIC_API_KEY as String?).takeIf { !it.isNullOrBlank() },
            installId = installId,
        )
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides fun claudeClients(graph: ProcessGraph): ClaudeClientProvider = graph.claudeClients()
    @Provides fun skimAi(graph: ProcessGraph): SkimAiService = graph.skimAi()
    @Provides fun assistant(graph: ProcessGraph): AiAssistant = graph.skimAi()
    @Provides fun askPageAccess(graph: ProcessGraph): AskPageAccess = graph.aiGate()
    @Provides fun aiGate(graph: ProcessGraph): AiGate = graph.aiGate()
    @Provides fun pageChats(graph: ProcessGraph): PageChats = graph.chatSessions()
    @Provides fun chatSessions(graph: ProcessGraph): ChatSessionStore = graph.chatSessions()
    @Provides fun tabGrouping(graph: ProcessGraph): TabGrouping = graph.tabGrouping()
    @Provides fun aiDataEraser(graph: ProcessGraph): AiDataEraser = graph.aiDataEraser()
    @Provides fun skimDataEraser(graph: ProcessGraph): SkimDataEraser = graph.aiDataEraser()
}
