package com.dantech.skimbrowser.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Skim as the default browser: the browser role from Android 10 on, the default app for web links
 * before that.
 */
object DefaultBrowser {

    /** A web link of no particular site: what the system opens it with is the default browser. */
    private val WEB_LINK: Uri = "https://example.com".toUri()

    /** True when web links from other apps open in Skim. */
    fun isDefault(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        } else {
            // Without a default the system resolves to its chooser, which is not Skim either.
            Intent(Intent.ACTION_VIEW, WEB_LINK).resolveActivity(context.packageManager)?.packageName == context.packageName
        }

    /**
     * What Set as default (03) starts: the system's request for the browser role when it can still
     * be given to Skim (Android 10 and later); otherwise the default apps settings, or the settings
     * home on a device without that screen.
     */
    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        val defaultApps = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        return if (defaultApps.resolveActivity(context.packageManager) != null) defaultApps else Intent(Settings.ACTION_SETTINGS)
    }
}
