package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Whether Skim AI may read a site. [ASK] (Skim AI turned off in Site information) asks again on the
 * next use and keeps the site out of the open-tabs overview and grouping; [UNSET] asks only while
 * "Ask before sending" is on.
 */
enum class SkimAiState { UNSET, ALLOWED, ASK }

/** A site's decision for one browser permission. [UNSET] means the site has not been answered. */
enum class PermissionState { UNSET, ALLOWED, BLOCKED }

/** The browser permissions a site can ask for through the site permission dialog. */
enum class SitePermissionKind { LOCATION, CAMERA, MICROPHONE }

/**
 * Per-site decisions, keyed by host (lowercase, without "www."): http and https of one host, and
 * every port, share a row. A row only exists while at least one decision is set.
 */
@Entity(tableName = "site_permissions")
data class SitePermissionEntity(
    @PrimaryKey val host: String,
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

    fun withPermission(kind: SitePermissionKind, state: PermissionState): SitePermissionEntity = when (kind) {
        SitePermissionKind.LOCATION -> copy(location = state)
        SitePermissionKind.CAMERA -> copy(camera = state)
        SitePermissionKind.MICROPHONE -> copy(microphone = state)
    }

    /** True when the row holds no decision at all and can be deleted. */
    fun holdsNoDecision(): Boolean =
        skimAi == SkimAiState.UNSET && SitePermissionKind.entries.all { permission(it) == PermissionState.UNSET }
}
