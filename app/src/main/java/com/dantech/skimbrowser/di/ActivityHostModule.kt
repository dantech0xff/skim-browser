package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.ActivityResultHost
import androidx.activity.ComponentActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

/** The activity-result host. Device tests replace this module with [com.dantech.skimbrowser.browser.TestActivityHost]. */
@Module
@InstallIn(ActivityComponent::class)
object ActivityHostModule {
    @Provides
    @ActivityScoped
    fun activityHost(activity: ComponentActivity): ActivityHost = ActivityResultHost(activity)
}
