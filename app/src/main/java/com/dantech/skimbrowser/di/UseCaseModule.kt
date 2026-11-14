package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.backup.ApplyBackup
import com.dantech.skimbrowser.domain.backup.BackupLibrary
import com.dantech.skimbrowser.domain.backup.ExportBackup
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.news.NewsFeedClient
import com.dantech.skimbrowser.domain.news.NewsParser
import com.dantech.skimbrowser.domain.news.NewsRepository
import com.dantech.skimbrowser.domain.news.RefreshNews
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.translate.PageTranslator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun openUrlInCurrentTab(tabs: TabsController): OpenUrlInCurrentTab = OpenUrlInCurrentTab(tabs)

    @Provides
    @NewsLanguage
    fun newsLanguage(): String = Locale.getDefault().language

    @Provides
    fun refreshNews(
        repository: NewsRepository,
        client: NewsFeedClient,
        parser: NewsParser,
        @WallClock clock: () -> Long,
    ): RefreshNews = RefreshNews(repository, client, parser, clock, Dispatchers.IO)

    @Provides
    fun collectOpenTabs(tabs: TabsController, sites: SitePermissionRepository): CollectOpenTabs =
        CollectOpenTabs(tabs, sites)

    @Provides fun exportBackup(library: BackupLibrary): ExportBackup = ExportBackup(library)

    @Provides fun applyBackup(library: BackupLibrary): ApplyBackup = ApplyBackup(library)

    @Provides
    @Singleton
    fun pageTranslator(
        tabs: TabsController,
        assistant: AiAssistant,
        settings: SettingsRepository,
        network: NetworkAvailability,
        sites: SitePermissionRepository,
        @AppScope scope: CoroutineScope,
    ): PageTranslator = PageTranslator(tabs, assistant, settings, network, sites, scope)
}
