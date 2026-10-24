package com.dantech.skimbrowser.ui.snackbar

import com.dantech.skimbrowser.di.AppScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Deletions that can still be undone, one entry per Undo snackbar. Created once per process and
 * runs every undo and finalize on [appScope], so an Undo tapped after its screen left the back
 * stack still runs.
 *
 * Each token resolves exactly once: [undo] runs its undo block, or [finalize] or [cancelAll] runs
 * its finalize block. Later calls with a resolved token, and calls with a token this registry never
 * issued, do nothing.
 *
 * The blocks outlive the screen that registered them, so they may only capture process-scoped
 * objects (repositories, the tab manager), never a navigator, an Activity or a view model. A block
 * that throws is reported by [appScope]'s exception handler.
 */
@Singleton
class UndoRegistry @Inject constructor(@AppScope private val appScope: CoroutineScope) {

    private class Pending(
        val kind: UndoKind,
        val undo: suspend () -> Unit,
        val finalize: suspend () -> Unit,
    )

    private val lastId = AtomicLong()
    private val pending = ConcurrentHashMap<Long, Pending>()

    /**
     * Registers a deletion of [kind] that was already applied. [undo] restores it; [finalize]
     * makes it permanent (for example purging a soft-deleted row) once Undo is no longer offered.
     */
    fun register(
        kind: UndoKind,
        undo: suspend () -> Unit,
        finalize: suspend () -> Unit = {},
    ): UndoToken {
        val token = UndoToken(lastId.incrementAndGet())
        pending[token.id] = Pending(kind, undo, finalize)
        return token
    }

    /** Runs the undo of [token] on the app scope, unless the token was already resolved. */
    fun undo(token: UndoToken) {
        val entry = pending.remove(token.id) ?: return
        appScope.launch { entry.undo() }
    }

    /** Runs the finalize of [token] on the app scope, unless the token was already resolved. */
    fun finalize(token: UndoToken) {
        val entry = pending.remove(token.id) ?: return
        appScope.launch { entry.finalize() }
    }

    /**
     * Finalizes every pending token of [kind], so their Undo no longer does anything. Used before a
     * bulk delete of that kind, which must not be partly reverted by an Undo still on screen.
     */
    fun cancelAll(kind: UndoKind) {
        pending.forEach { (id, entry) ->
            if (entry.kind == kind && pending.remove(id, entry)) {
                appScope.launch { entry.finalize() }
            }
        }
    }

    /** The kind [token] was registered with, or null once it is resolved or if it is unknown. */
    internal fun kindOf(token: UndoToken): UndoKind? = pending[token.id]?.kind
}
