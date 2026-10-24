package com.dantech.skimbrowser.di

import android.app.Application
import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.system.DefaultBrowser
import com.dantech.skimbrowser.system.VoiceInput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Defaults Kotlin hides from Dagger. Unqualified [String] and [Int] are not bound. */
@Module
@InstallIn(SingletonComponent::class)
object ViewModelBindingsModule {
    @Provides
    @WallClock
    fun wallClock(): () -> Long = System::currentTimeMillis

    @Provides
    @WallZone
    fun wallZone(): @JvmSuppressWildcards () -> ZoneId = ZoneId::systemDefault

    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()

    @Provides
    @MainImmediate
    fun mainImmediate(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @VoiceAvailable
    fun voiceAvailable(app: Application): Boolean = VoiceInput.isAvailable(app)

    @Provides
    @DefaultBrowserCheck
    fun defaultBrowser(app: Application): () -> Boolean = { DefaultBrowser.isDefault(app) }

    @Provides
    fun appVersion(): AppVersion = AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
}
