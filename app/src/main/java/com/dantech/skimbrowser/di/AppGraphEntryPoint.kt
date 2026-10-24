package com.dantech.skimbrowser.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The process graph, for call sites that are not constructor-injected. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppGraphEntryPoint {
    fun graph(): ProcessGraph
    fun shutdown(): ProcessShutdown
}
