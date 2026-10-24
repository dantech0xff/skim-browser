package com.dantech.skimbrowser.di

import javax.inject.Qualifier

/** The process scope. A new one is created after [ProcessGraph.close]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** Wall time for history sections and Continue cards. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallClock

/** The zone those calendar days are counted in. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallZone

/** The main thread, immediate. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainImmediate

/** Whether an app takes speech input. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VoiceAvailable

/** Whether Skim is the default browser right now. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultBrowserCheck
