package com.dantech.skimbrowser.testing

/** Runs [block], which must throw [T], and returns what it threw. */
suspend inline fun <reified T : Throwable> assertThrowsSuspending(block: suspend () -> Unit): T {
    val thrown = try {
        block()
        null
    } catch (error: Throwable) {
        error
    }
    if (thrown is T) return thrown
    throw AssertionError("Expected ${T::class.java.simpleName}, got ${thrown ?: "no exception"}", thrown)
}
