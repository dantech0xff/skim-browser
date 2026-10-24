package com.dantech.skimbrowser.di

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import okhttp3.OkHttpClient

/** The process objects Hilt cannot construct. This is the only caller of [SkimDatabase.open]. */
@Module
@InstallIn(SingletonComponent::class)
object ProcessResourcesModule {
    private const val TAG = "SkimAppScope"
    private const val HTTP_TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun database(app: Application, storage: AppStorage): SkimDatabase = SkimDatabase.open(app, storage.databaseName)

    @Provides
    @Singleton
    @AppScope
    fun appScope(lastError: MutableStateFlow<Throwable?>): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Uncaught exception in appScope", error)
            lastError.value = error
        },
    )

    @Provides
    @Singleton
    @SettingsJob
    fun settingsJob(@AppScope appScope: CoroutineScope): CompletableJob = SupervisorJob(appScope.coroutineContext.job)

    @Provides
    @Singleton
    fun dataStore(
        app: Application,
        storage: AppStorage,
        @AppScope appScope: CoroutineScope,
        @SettingsJob settingsJob: CompletableJob,
    ): DataStore<Preferences> = SettingsRepository.createDataStore(
        scope = CoroutineScope(appScope.coroutineContext + settingsJob + Dispatchers.IO),
        produceFile = { storage.settingsFile(app) },
    )

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
