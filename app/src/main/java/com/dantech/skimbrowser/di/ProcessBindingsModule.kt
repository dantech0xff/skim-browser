package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

@Module
@InstallIn(SingletonComponent::class)
object ProcessBindingsModule {
    @Provides
    @Singleton
    fun lastError(): MutableStateFlow<Throwable?> = MutableStateFlow(null)

    @Provides
    @AppScope
    fun appScope(graph: ProcessGraph): CoroutineScope = graph.appScope()

    @Provides fun snackbar(graph: ProcessGraph): SnackbarController = graph.snackbarController()
    @Provides fun undo(graph: ProcessGraph): UndoRegistry = graph.undoRegistry()
    @Provides fun pendingUndo(graph: ProcessGraph): PendingUndo = graph.pendingUndo()
    @Provides fun readerHighlights(graph: ProcessGraph): ReaderHighlights = graph.readerHighlights()
}
