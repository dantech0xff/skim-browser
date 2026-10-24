package com.dantech.skimbrowser.browser

import android.content.Intent

/**
 * An [ActivityHost] without an Activity. Picker and permission requests wait until the test answers
 * them; [startExternal] records the intent or throws [startFailure].
 */
class FakeActivityHost : ActivityHost {

    class FileRequest(val intent: Intent, val onResult: (Int, Intent?) -> Unit)

    class PermissionRequest(val permissions: List<String>, val onResult: (Map<String, Boolean>) -> Unit)

    val fileRequests = mutableListOf<FileRequest>()
    val permissionRequests = mutableListOf<PermissionRequest>()
    val started = mutableListOf<Intent>()

    /** Thrown by [startExternal] instead of starting anything, when set. */
    var startFailure: RuntimeException? = null

    override fun chooseFiles(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit) {
        fileRequests += FileRequest(intent, onResult)
    }

    override fun requestPermissions(permissions: Array<String>, onResult: (granted: Map<String, Boolean>) -> Unit) {
        permissionRequests += PermissionRequest(permissions.toList(), onResult)
    }

    override fun startExternal(intent: Intent) {
        startFailure?.let { throw it }
        started += intent
    }
}
