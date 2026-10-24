package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.di.ActivityHostModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.testing.TestInstallIn

/** Device tests observe this host instead of registering real result launchers. */
@Module
@TestInstallIn(components = [ActivityComponent::class], replaces = [ActivityHostModule::class])
object TestActivityHostModule {
    @Provides
    @ActivityScoped
    fun activityHost(): ActivityHost = TestActivityHost()
}
