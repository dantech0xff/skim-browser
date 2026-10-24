package com.dantech.skimbrowser.di.ai

import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.ai.ClaudeClientProvider
import com.dantech.skimbrowser.domain.settings.SettingsRepository
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
    @Provides
    @Singleton
    fun claudeClients(factory: ClaudeClientFactory, settings: SettingsRepository): ClaudeClientProvider =
        factory.create(settings::installId)
}
