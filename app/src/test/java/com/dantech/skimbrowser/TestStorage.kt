package com.dantech.skimbrowser

import java.util.concurrent.atomic.AtomicReference

/** Set before Hilt asks for [AppStorage]. Cleared after [ReleaseProcessResources.release]. */
object TestStorage {
    val current: AtomicReference<AppStorage?> = AtomicReference()
}
