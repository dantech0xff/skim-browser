package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import kotlinx.coroutines.flow.Flow

/** Site decisions keyed by an already normalized host (`SitePermissionRepository` normalizes). */
@Dao
abstract class SitePermissionDao {

    @Query("SELECT * FROM site_permissions WHERE host = :host")
    abstract fun observe(host: String): Flow<SitePermissionEntity?>

    @Query("SELECT * FROM site_permissions WHERE host = :host")
    abstract suspend fun get(host: String): SitePermissionEntity?

    @Query("SELECT * FROM site_permissions WHERE host IN (:hosts)")
    abstract suspend fun getAll(hosts: List<String>): List<SitePermissionEntity>

    @Insert
    abstract suspend fun insert(permission: SitePermissionEntity)

    @Update
    abstract suspend fun update(permission: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE host = :host")
    abstract suspend fun delete(host: String)

    @Query("UPDATE site_permissions SET skimAi = 'UNSET', updatedAt = :now WHERE skimAi != 'UNSET'")
    abstract suspend fun resetSkimAi(now: Long)

    @Query(
        "DELETE FROM site_permissions WHERE skimAi = 'UNSET' AND location = 'UNSET' " +
            "AND camera = 'UNSET' AND microphone = 'UNSET'",
    )
    abstract suspend fun deleteUndecided()

    /** Forgets every site's Skim AI decision and drops the rows left with no decision at all. */
    @Transaction
    open suspend fun deleteAllSkimAiData(now: Long) {
        resetSkimAi(now)
        deleteUndecided()
    }

    @Transaction
    open suspend fun setSkimAi(host: String, state: SkimAiState, now: Long) {
        val current = get(host)
        write(current, (current ?: SitePermissionEntity(host = host, updatedAt = now)).copy(skimAi = state, updatedAt = now))
    }

    @Transaction
    open suspend fun setPermission(host: String, kind: SitePermissionKind, state: PermissionState, now: Long) {
        val current = get(host)
        write(current, (current ?: SitePermissionEntity(host = host, updatedAt = now)).withPermission(kind, state).copy(updatedAt = now))
    }

    /** Stores [next] over [current]; a row left without any decision is deleted instead. */
    private suspend fun write(current: SitePermissionEntity?, next: SitePermissionEntity) {
        when {
            next.holdsNoDecision() -> if (current != null) delete(next.host)
            current == null -> insert(next)
            else -> update(next)
        }
    }
}
