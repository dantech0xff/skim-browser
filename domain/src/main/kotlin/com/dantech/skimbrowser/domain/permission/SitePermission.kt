package com.dantech.skimbrowser.domain.permission

/**
 * Whether Skim AI may read a site. [ASK] asks again on the next use and keeps the site out of the
 * open-tabs overview and grouping. [UNSET] asks only while "Ask before sending" is on.
 */
enum class SkimAiState { UNSET, ALLOWED, ASK }

/** A site's decision for one browser permission. [UNSET] means the site has not been answered. */
enum class PermissionState { UNSET, ALLOWED, BLOCKED }

/** The browser permissions a site can ask for through the site permission dialog. */
enum class SitePermissionKind { LOCATION, CAMERA, MICROPHONE }

/** Per-site decisions, keyed by host (lowercase, without "www."). */
data class SitePermission(
    val host: String,
    val skimAi: SkimAiState = SkimAiState.UNSET,
    val location: PermissionState = PermissionState.UNSET,
    val camera: PermissionState = PermissionState.UNSET,
    val microphone: PermissionState = PermissionState.UNSET,
    val updatedAt: Long,
) {
    fun permission(kind: SitePermissionKind): PermissionState = when (kind) {
        SitePermissionKind.LOCATION -> location
        SitePermissionKind.CAMERA -> camera
        SitePermissionKind.MICROPHONE -> microphone
    }
}
