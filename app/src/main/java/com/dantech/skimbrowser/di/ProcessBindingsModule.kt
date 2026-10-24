package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

@Module
@InstallIn(SingletonComponent::class)
object ProcessBindingsModule {
    @Provides
    @Singleton
    fun lastError(): MutableStateFlow<Throwable?> = MutableStateFlow(null)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UndoBindingsModule {
    @Binds abstract fun pendingUndo(impl: SnackbarUndo): PendingUndo
}
