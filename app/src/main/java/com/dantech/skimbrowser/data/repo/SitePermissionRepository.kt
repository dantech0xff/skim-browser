package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.SQL_IN_LIMIT
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.SitePermissionDao
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.normalizeHost
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Per-site decisions of normal tabs, keyed by host: every host passed in is lowercased and loses a
 * leading "www.", so `https://WWW.Example.com` and `http://example.com:8080` share a row. Incognito
 * decisions never reach this class.
 */
class SitePermissionRepository(
    private val dao: SitePermissionDao,
    private val db: SkimDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** The decisions for [host], or null while there are none (always null for a blank host). */
    fun observe(host: String): Flow<SitePermissionEntity?> {
        val key = normalizeHost(host)
        return if (key.isEmpty()) flowOf(null) else dao.observe(key)
    }

    suspend fun get(host: String): SitePermissionEntity? {
        val key = normalizeHost(host)
        return if (key.isEmpty()) null else dao.get(key)
    }

    /** The Skim AI state of each of [hosts], keyed by the host as given; a site without a row is UNSET. */
    suspend fun skimAiStates(hosts: Set<String>): Map<String, SkimAiState> {
        if (hosts.isEmpty()) return emptyMap()
        val keys = hosts.associateWith(::normalizeHost)
        val stored = keys.values.filter { it.isNotEmpty() }.distinct()
            .chunked(SQL_IN_LIMIT)
            .flatMap { dao.getAll(it) }
            .associate { it.host to it.skimAi }
        return keys.mapValues { (_, key) -> stored[key] ?: SkimAiState.UNSET }
    }

    suspend fun setSkimAi(host: String, s: SkimAiState) = dao.setSkimAi(siteKey(host), s, clock())

    suspend fun setPermission(host: String, kind: SitePermissionKind, s: PermissionState) =
        dao.setPermission(siteKey(host), kind, s, clock())

    /**
     * "Delete all AI data": every site's Skim AI state returns to UNSET, rows with no other decision
     * are deleted (rows that still hold a location, camera or microphone decision stay), then the
     * log is checkpointed so the deleted hosts leave no copy behind.
     */
    suspend fun deleteAllSkimAiData() {
        withContext(NonCancellable) {
            dao.deleteAllSkimAiData(clock())
            db.checkpointTruncate()
        }
    }

    private fun siteKey(host: String): String =
        normalizeHost(host).also { require(it.isNotEmpty()) { "A site decision needs a host" } }
}
