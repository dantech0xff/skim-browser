package com.dantech.skimbrowser.data.news

import com.dantech.skimbrowser.domain.news.NewsCatalog
import com.dantech.skimbrowser.domain.news.NewsPreferences
import com.dantech.skimbrowser.domain.news.NewsRepository
import com.dantech.skimbrowser.domain.news.NewsSnapshot
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.domain.news.NewsUrls
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * News choices and cached stories in [directory], separate from settings and the database.
 * A missing or unreadable file is the default: news on, Top, nothing cached.
 */
class FileNewsRepository(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : NewsRepository {
    private val gate = Mutex()
    private val preferenceTick = MutableStateFlow(0)
    private val snapshotTick = MutableStateFlow(0)

    override fun preferences(): Flow<NewsPreferences> = preferenceTick.transform { emit(currentPreferences()) }

    override suspend fun currentPreferences(): NewsPreferences = withContext(io) {
        gate.withLock { readPreferences() }
    }

    override suspend fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    override suspend fun setTopic(topic: NewsTopic) = edit { it.copy(topic = topic) }

    override suspend fun setSourceShown(sourceId: String, shown: Boolean) = edit { prefs ->
        val hidden = prefs.hiddenSourceIds.toMutableSet()
        if (shown) hidden.remove(sourceId) else hidden.add(sourceId)
        prefs.copy(hiddenSourceIds = hidden)
    }

    override suspend fun addCustomFeed(url: String): Boolean {
        val canonical = NewsUrls.canonical(url) ?: return false
        var added = false
        edit { prefs ->
            when {
                canonical in prefs.customFeedUrls -> prefs
                prefs.customFeedUrls.size >= NewsCatalog.MAX_CUSTOM_FEEDS -> prefs
                else -> {
                    added = true
                    prefs.copy(customFeedUrls = prefs.customFeedUrls + canonical)
                }
            }
        }
        return added
    }

    override fun snapshot(language: String, topic: NewsTopic): Flow<NewsSnapshot?> =
        snapshotTick.transform { emit(currentSnapshot(language, topic)) }

    override suspend fun currentSnapshot(language: String, topic: NewsTopic): NewsSnapshot? = withContext(io) {
        gate.withLock { readSnapshot(language, topic) }
    }

    override suspend fun saveSnapshot(language: String, topic: NewsTopic, snapshot: NewsSnapshot) {
        withContext(io) {
            gate.withLock { writeSnapshot(language, topic, snapshot) }
        }
        snapshotTick.value += 1
    }

    private suspend fun edit(transform: (NewsPreferences) -> NewsPreferences) {
        withContext(io) {
            gate.withLock { writePreferences(transform(readPreferences())) }
        }
        preferenceTick.value += 1
    }

    private fun readPreferences(): NewsPreferences {
        val file = File(directory, PREFERENCES)
        if (!file.isFile) return NewsPreferences()
        return try {
            NewsJson.decodePreferences(file.readText())
        } catch (e: IllegalArgumentException) {
            NewsPreferences()
        } catch (e: IllegalStateException) {
            NewsPreferences()
        } catch (e: IndexOutOfBoundsException) {
            NewsPreferences()
        } catch (e: NumberFormatException) {
            NewsPreferences()
        }
    }

    private fun writePreferences(prefs: NewsPreferences) {
        writeAtomic(File(directory, PREFERENCES), NewsJson.encodePreferences(prefs))
    }

    private fun readSnapshot(language: String, topic: NewsTopic): NewsSnapshot? {
        val file = cacheFile(language, topic)
        if (!file.isFile) return null
        return try {
            NewsJson.decodeSnapshot(file.readText())
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: IndexOutOfBoundsException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun writeSnapshot(language: String, topic: NewsTopic, snapshot: NewsSnapshot) {
        writeAtomic(cacheFile(language, topic), NewsJson.encodeSnapshot(snapshot))
    }

    private fun cacheFile(language: String, topic: NewsTopic): File {
        val group = NewsCatalog.languageGroup(language)
        return File(directory, "cache-$group-${topic.name}.json")
    }

    private fun writeAtomic(file: File, text: String) {
        directory.mkdirs()
        val temporary = File(directory, file.name + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.writeText(text)
            temporary.delete()
        }
    }

    private companion object {
        const val PREFERENCES = "preferences.json"
    }
}
