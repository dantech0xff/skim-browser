package com.dantech.skimbrowser.di

import javax.inject.Qualifier

/**
 * The settings store's job, a child of [AppScope]. The test release joins it after cancelling
 * that scope, so the next test method's component can open the settings file again.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsJob

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
