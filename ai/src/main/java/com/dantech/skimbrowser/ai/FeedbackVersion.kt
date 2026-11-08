package com.dantech.skimbrowser.ai

import javax.inject.Qualifier

/** The app version name sent with a rating. It is not a device id. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeedbackVersion
