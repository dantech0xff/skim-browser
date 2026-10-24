package com.dantech.skimbrowser.domain.tab

/** The normal tabs saved before the process went away, brought back once per process. */
interface TabRestore {
    /** Waits until the saved tabs are back, or given up on. Every call returns the result of the first. */
    suspend fun restore(): RestoreResult
}
