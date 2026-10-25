package com.dantech.skimbrowser.browser

import androidx.annotation.MainThread
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Activity's [TabEngine] as the process sees it. Process types read this and never name the
 * WebView pool or the Activity; nothing holds the engine while no Activity is up.
 */
interface TabEngineSource {
    /** The attached engine; null while no Activity is up. */
    val engine: StateFlow<TabEngine?>

    /** True while [engine] is the one attached. */
    @MainThread
    fun isAttached(engine: TabEngine): Boolean

    /** [listener] hears every attach and detach, on the main thread, right after [engine] changed. */
    fun addListener(listener: TabEngineListener)
}

/** Where the Activity plugs its WebView pool in while it lives. */
@MainThread
interface TabEngineAttachment {
    /** [engine] drives the tabs from now on. A newer Activity's pool replaces an older one still attached. */
    fun attach(engine: TabEngine)

    /** [engine] is gone with its Activity. Nothing changes when another engine is attached by now. */
    fun detach(engine: TabEngine)
}

/** Told when the attached engine changes: [previous] is the one before, [current] the one now (null: none). */
fun interface TabEngineListener {
    @MainThread
    fun onEngineChanged(previous: TabEngine?, current: TabEngine?)
}

/**
 * The bridge between the process and the Activity's WebView pool: the only process object that
 * holds the engine, and only between [attach] and [detach].
 */
@Singleton
class TabEngineBridge @Inject constructor() : TabEngineSource, TabEngineAttachment {

    private val current = MutableStateFlow<TabEngine?>(null)
    private val listeners = CopyOnWriteArrayList<TabEngineListener>()

    override val engine: StateFlow<TabEngine?> = current.asStateFlow()

    override fun isAttached(engine: TabEngine): Boolean = current.value === engine

    override fun addListener(listener: TabEngineListener) {
        listeners += listener
    }

    override fun attach(engine: TabEngine) {
        val previous = current.value
        current.value = engine
        listeners.forEach { it.onEngineChanged(previous, engine) }
    }

    override fun detach(engine: TabEngine) {
        if (current.value !== engine) return
        current.value = null
        listeners.forEach { it.onEngineChanged(engine, null) }
    }
}
