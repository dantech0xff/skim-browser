package com.dantech.skimbrowser.ai.di

import com.dantech.skimbrowser.ai.AiDataEraser
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.ai.ChatSessionStore
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AskPageAccess
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Each class is the singleton it is scoped to, so every interface here is the same object. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AiBindingsModule {
    @Binds abstract fun assistant(impl: SkimAiService): AiAssistant
    @Binds abstract fun askPageAccess(impl: AiGate): AskPageAccess
    @Binds abstract fun pageChats(impl: ChatSessionStore): PageChats
    @Binds abstract fun skimDataEraser(impl: AiDataEraser): SkimDataEraser
}
