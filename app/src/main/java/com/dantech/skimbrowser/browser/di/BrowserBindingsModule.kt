package com.dantech.skimbrowser.browser.di

import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.page.PageScriptSource
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.browsing.SiteDataCleaner
import com.dantech.skimbrowser.domain.tab.IncognitoDecisions
import com.dantech.skimbrowser.domain.tab.IncognitoSupport
import com.dantech.skimbrowser.domain.tab.TabsController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Process browser types. The activity pool is not built here. */
@Module
@InstallIn(SingletonComponent::class)
object BrowserBindingsModule {
    @Provides fun tabManager(graph: ProcessGraph): TabManager = graph.tabManager()
    @Provides fun tabsController(graph: ProcessGraph): TabsController = graph.tabManager()
    @Provides fun networkMonitor(graph: ProcessGraph): NetworkMonitor = graph.networkMonitor()
    @Provides fun networkAvailability(graph: ProcessGraph): NetworkAvailability = graph.networkMonitor()
    @Provides fun profiles(graph: ProcessGraph): BrowserProfiles = graph.browserProfiles()
    @Provides fun incognito(graph: ProcessGraph): IncognitoSession = graph.incognitoSession()
    @Provides fun incognitoDecisions(graph: ProcessGraph): IncognitoDecisions = graph.incognitoSession()
    @Provides fun incognitoSupport(graph: ProcessGraph): IncognitoSupport = graph.incognitoSession()
    @Provides fun tabState(graph: ProcessGraph): TabStateStore = graph.tabStateStore()
    @Provides fun thumbnails(graph: ProcessGraph): ThumbnailStore = graph.thumbnails()
    @Provides fun pageScripts(graph: ProcessGraph): PageScriptSource = graph.pageScripts()
    @Provides fun downloads(graph: ProcessGraph): DownloadTracker = graph.downloadTracker()
    @Provides fun browsingDataCleaner(graph: ProcessGraph): BrowsingDataCleaner = graph.browsingDataCleaner()
    @Provides fun siteDataCleaner(graph: ProcessGraph): SiteDataCleaner = graph.browsingDataCleaner()
}
