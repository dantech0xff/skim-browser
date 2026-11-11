package com.dantech.skimbrowser.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.dantech.skimbrowser.R

/**
 * Asks the platform to confirm the person before the first incognito tab of a session.
 * A device that cannot authenticate (or is older than the prompt) opens the tab without asking.
 */
object IncognitoBiometric {
    fun canAuthenticate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: Activity, onGranted: () -> Unit) {
        if (!canAuthenticate(activity)) {
            onGranted()
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.incognito_lock_title))
            .setDescription(activity.getString(R.string.incognito_lock_body))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onGranted()
                }
            },
        )
    }
}

internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Opens incognito at once when this session already has a private tab or the device cannot confirm. */
fun Context.confirmIncognito(sessionOpen: Boolean, open: () -> Unit) {
    val activity = findActivity()
    if (sessionOpen || activity == null) {
        open()
    } else {
        IncognitoBiometric.authenticate(activity, open)
    }
}
