package com.dantech.skimbrowser.di

import javax.inject.Inject
import javax.inject.Singleton

/** Test rules call this so the next case can open Room. Production does not. */
@Singleton
class ProcessShutdown @Inject constructor(private val graph: ProcessGraph) {
    fun close() = graph.close()
}
