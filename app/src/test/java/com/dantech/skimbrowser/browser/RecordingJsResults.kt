package com.dantech.skimbrowser.browser

import android.webkit.JsPromptResult
import android.webkit.JsResult
import java.lang.reflect.Proxy
import org.robolectric.Shadows.shadowOf

/**
 * Results of a page's JavaScript dialogs for JVM tests, made the way the WebView makes them (their
 * constructor takes a receiver the SDK hides), so a test reads how each one was answered.
 */
class RecordingJsResults {

    private val made = mutableListOf<JsResult>()
    private val confirmations = mutableListOf<JsResult>()

    /**
     * "confirm" or "cancel" for each result answered so far, in the order they were made; a result
     * answered more than once reads "twice". Robolectric records a cancel on the result itself
     * without telling the receiver; a confirm reaches the receiver.
     */
    val answers: List<String>
        get() = made.mapNotNull { result ->
            val cancelled = shadowOf(result).wasCancelled()
            val confirmed = confirmations.count { it === result }
            when {
                cancelled && confirmed == 0 -> "cancel"
                !cancelled && confirmed == 1 -> "confirm"
                !cancelled && confirmed == 0 -> null
                else -> "twice"
            }
        }

    private val receiverType = Class.forName("android.webkit.JsResult\$ResultReceiver")
    private val receiver = Proxy.newProxyInstance(receiverType.classLoader, arrayOf(receiverType)) { proxy, method, args ->
        when (method.name) {
            "onJsResultComplete" -> {
                confirmations += args!![0] as JsResult
                null
            }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "RecordingJsResults.receiver"
            else -> null
        }
    }

    fun alert(): JsResult = JsResult::class.java.getConstructor(receiverType).newInstance(receiver).also { made += it }

    fun prompt(): JsPromptResult = JsPromptResult::class.java.getConstructor(receiverType).newInstance(receiver).also { made += it }
}
