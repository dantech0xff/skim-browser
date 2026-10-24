package com.dantech.skimbrowser

import com.dantech.skimbrowser.data.di.StorageModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
    @Provides
    @Singleton
    fun storage(): AppStorage = TestStorage.current.get() ?: error("TestStorage was not set")
}
