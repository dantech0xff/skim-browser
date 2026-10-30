package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.ShortcutDao
import com.dantech.skimbrowser.data.shortcut.ShortcutSeeder
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository as ShortcutStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Home shortcuts (04), in tile order.
 *
 * [prepare] runs when a collection starts. Production passes [ShortcutSeeder.seed], so the
 * first list already contains the presets. Callers that build the repository from a DAO
 * alone leave it empty.
 */
@Singleton
class ShortcutRepository(
    private val dao: ShortcutDao,
    private val prepare: suspend () -> Unit = {},
) : ShortcutStore {

    @Inject constructor(database: SkimDatabase, seeder: ShortcutSeeder) : this(database.shortcutDao(), seeder::seed)

    override fun observe(): Flow<List<Shortcut>> = flow {
        prepare()
        emitAll(dao.observe().map { rows -> rows.map { it.toDomain() } })
    }

    /** Adds a shortcut after the last one. */
    override suspend fun add(name: String, url: String) {
        require(url.isNotBlank()) { "A shortcut needs a URL" }
        dao.add(name.trim(), url.trim())
    }

    override suspend fun update(id: Long, name: String, url: String) {
        require(url.isNotBlank()) { "A shortcut needs a URL" }
        dao.update(id, name.trim(), url.trim())
    }

    /** Deletes shortcut [id] and returns it for Undo. */
    override suspend fun remove(id: Long): Shortcut? = dao.remove(id)?.toDomain()

    /** The Undo of [remove]: the shortcut returns with its id and position. */
    override suspend fun restore(shortcut: Shortcut) = dao.restore(shortcut.toEntity())
}
