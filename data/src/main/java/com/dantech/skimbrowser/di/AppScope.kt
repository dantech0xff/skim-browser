package com.dantech.skimbrowser.di

import javax.inject.Qualifier

/**
 * The process scope, one per `SingletonComponent`. Production drops it when the process dies.
 * Tests release it in `@After`, before the next method's component makes another.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
