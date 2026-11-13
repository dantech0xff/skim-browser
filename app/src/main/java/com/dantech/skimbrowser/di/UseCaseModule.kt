package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.backup.ApplyBackup
import com.dantech.skimbrowser.domain.backup.BackupLibrary
import com.dantech.skimbrowser.domain.backup.ExportBackup
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.domain.tab.TabsController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun openUrlInCurrentTab(tabs: TabsController): OpenUrlInCurrentTab = OpenUrlInCurrentTab(tabs)

    @Provides
    fun collectOpenTabs(tabs: TabsController, sites: SitePermissionRepository): CollectOpenTabs =
        CollectOpenTabs(tabs, sites)

    @Provides fun exportBackup(library: BackupLibrary): ExportBackup = ExportBackup(library)

    @Provides fun applyBackup(library: BackupLibrary): ApplyBackup = ApplyBackup(library)
}
