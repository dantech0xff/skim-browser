package com.dantech.skimbrowser.domain.tab

import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SitePermissionKind

/** Whether this device can open an incognito tab. */
interface IncognitoSupport {
    val isSupported: Boolean
}

/** Permission answers that last only for the current incognito session. */
interface IncognitoDecisions {
    fun decision(host: String, kind: SitePermissionKind): PermissionState

    fun setDecision(host: String, kind: SitePermissionKind, state: PermissionState)
}
