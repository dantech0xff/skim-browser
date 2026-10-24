package com.dantech.skimbrowser.browser.di

import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
import com.dantech.skimbrowser.browser.ConnectivityNetworkMonitor
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.WebViewProfiles
import com.dantech.skimbrowser.browser.page.PageScriptSource
import com.dantech.skimbrowser.browser.page.PageScripts
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.browsing.SiteDataCleaner
import com.dantech.skimbrowser.domain.tab.IncognitoDecisions
import com.dantech.skimbrowser.domain.tab.IncognitoSupport
import com.dantech.skimbrowser.domain.tab.TabsController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Each class is the singleton it is scoped to, so every interface here is the same object. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BrowserBindingsModule {
    @Binds abstract fun tabsController(impl: TabManager): TabsController
    @Binds abstract fun networkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
    @Binds abstract fun networkAvailability(impl: ConnectivityNetworkMonitor): NetworkAvailability
    @Binds abstract fun incognitoDecisions(impl: IncognitoSession): IncognitoDecisions
    @Binds abstract fun incognitoSupport(impl: IncognitoSession): IncognitoSupport
    @Binds abstract fun profiles(impl: WebViewProfiles): BrowserProfiles
    @Binds abstract fun pageScripts(impl: PageScripts): PageScriptSource
    @Binds abstract fun siteDataCleaner(impl: BrowsingDataCleaner): SiteDataCleaner
}
