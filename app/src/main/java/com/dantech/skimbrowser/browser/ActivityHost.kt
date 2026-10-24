package com.dantech.skimbrowser.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread

/**
 * What the WebView layer needs from the Activity: pickers and permission prompts that answer
 * through activity results, and other apps. Every call is made on the main thread.
 *
 * Each request is answered exactly once. A request still waiting when a new one of the same kind
 * comes, or when the Activity is destroyed, is answered as cancelled, so nothing that waits on it
 * (a page's file input) is left hanging.
 */
@MainThread
interface ActivityHost {
    /**
     * Starts the picker [intent] (from `FileChooserParams.createIntent`) and hands its result code
     * and data to [onResult]; `RESULT_CANCELED` with no data when it is cancelled, replaced, no app
     * can pick, or the Activity goes away.
     */
    fun chooseFiles(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit)

    /**
     * Asks for the runtime [permissions] and hands [onResult] the answer for each of them; every
     * one is false when the prompt is cancelled, replaced or the Activity goes away.
     */
    fun requestPermissions(permissions: Array<String>, onResult: (granted: Map<String, Boolean>) -> Unit)

    /**
     * Starts [intent] in another app.
     *
     * @throws ActivityNotFoundException when no app takes it.
     * @throws SecurityException when the target app does not let Skim start it.
     */
    fun startExternal(intent: Intent)
}

/**
 * [ActivityHost] over the Activity's result launchers, which are registered here; create it in
 * `onCreate`, before the Activity is started, and call [destroy] in `onDestroy`.
 */
@MainThread
class ActivityResultHost(
    private val activity: ComponentActivity,
    registry: ActivityResultRegistry = activity.activityResultRegistry,
) : ActivityHost {

    private var pendingFiles: ((Int, Intent?) -> Unit)? = null
    private var pendingPermissions: PendingPermissions? = null
    private var destroyed = false

    private class PendingPermissions(val asked: Array<String>, val onResult: (Map<String, Boolean>) -> Unit)

    private val filePicker: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult(), registry) { result ->
            takeFiles()?.invoke(result.resultCode, result.data)
        }

    private val permissionPrompt: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), registry) { granted ->
            takePermissions()?.let { pending -> pending.onResult(pending.asked.associateWith { granted[it] == true }) }
        }

    override fun chooseFiles(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit) {
        takeFiles()?.invoke(Activity.RESULT_CANCELED, null)
        if (destroyed) {
            onResult(Activity.RESULT_CANCELED, null)
            return
        }
        pendingFiles = onResult
        try {
            filePicker.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can pick files", e)
            if (pendingFiles === onResult) takeFiles()?.invoke(Activity.RESULT_CANCELED, null)
        }
    }

    override fun requestPermissions(permissions: Array<String>, onResult: (granted: Map<String, Boolean>) -> Unit) {
        takePermissions()?.let(::deny)
        val request = PendingPermissions(permissions.copyOf(), onResult)
        if (destroyed || permissions.isEmpty()) {
            deny(request)
            return
        }
        pendingPermissions = request
        permissionPrompt.launch(request.asked)
    }

    override fun startExternal(intent: Intent) {
        activity.startActivity(intent)
    }

    /** The Activity is being destroyed: every request still waiting is answered as cancelled. */
    fun destroy() {
        destroyed = true
        takeFiles()?.invoke(Activity.RESULT_CANCELED, null)
        takePermissions()?.let(::deny)
    }

    private fun takeFiles(): ((Int, Intent?) -> Unit)? = pendingFiles.also { pendingFiles = null }

    private fun takePermissions(): PendingPermissions? = pendingPermissions.also { pendingPermissions = null }

    private fun deny(request: PendingPermissions) {
        request.onResult(request.asked.associateWith { false })
    }

    private companion object {
        const val TAG = "SkimActivityHost"
    }
}
