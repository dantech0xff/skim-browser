package com.dantech.skimbrowser.data.di

import com.dantech.skimbrowser.AppStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The process files. Tests replace this module and set their own [AppStorage] first. */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun storage(): AppStorage = AppStorage()
}
