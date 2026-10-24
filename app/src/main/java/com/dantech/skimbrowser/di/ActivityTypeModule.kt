package com.dantech.skimbrowser.di

import android.app.Activity
import androidx.activity.ComponentActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/** [ActivityComponent] binds [Activity]. The pool and the result host need [ComponentActivity]. */
@Module
@InstallIn(ActivityComponent::class)
object ActivityTypeModule {
    @Provides
    fun componentActivity(activity: Activity): ComponentActivity = activity as ComponentActivity
}
