package com.dantech.skimbrowser.domain.permission

import kotlinx.coroutines.flow.Flow

/**
 * Per-site decisions of normal tabs, keyed by host. Every host passed in is lowercased and loses a
 * leading "www.". Incognito decisions never reach this store.
 */
interface SitePermissionRepository {
    /** The decisions for [host], or null while there are none. Always null for a blank host. */
    fun observe(host: String): Flow<SitePermission?>

    suspend fun get(host: String): SitePermission?

    /** The Skim AI state of each of [hosts], keyed by the host as given. A site without a row is UNSET. */
    suspend fun skimAiStates(hosts: Set<String>): Map<String, SkimAiState>

    suspend fun setSkimAi(host: String, state: SkimAiState)

    suspend fun setPermission(host: String, kind: SitePermissionKind, state: PermissionState)

    /** Every site's Skim AI state returns to UNSET. Rows with no other decision are deleted. */
    suspend fun deleteAllSkimAiData()
}
