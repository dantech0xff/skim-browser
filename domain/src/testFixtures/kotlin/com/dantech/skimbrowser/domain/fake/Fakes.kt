package com.dantech.skimbrowser.domain.fake

import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolderSummary
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.bookmark.BookmarksRoot
import com.dantech.skimbrowser.domain.bookmark.FolderContent
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.ChatSummary
import com.dantech.skimbrowser.domain.chat.DeletedChat
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SitePermission
import com.dantech.skimbrowser.domain.permission.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.isWebUrl
import com.dantech.skimbrowser.domain.url.normalizeHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: SkimSettings = SkimSettings()) : SettingsRepository {
    private val current = MutableStateFlow(initial)
    private var install = "install-id"

    override val settings: Flow<SkimSettings> = current

    override suspend fun setSearchEngine(engine: SearchEngine) = update { it.copy(searchEngine = engine) }

    override suspend fun setTheme(theme: ThemeSetting) = update { it.copy(theme = theme) }

    override suspend fun setAskBeforeSending(enabled: Boolean) = update { it.copy(askBeforeSending = enabled) }

    override suspend fun setKeepChatHistory(enabled: Boolean) = update { it.copy(keepChatHistory = enabled) }

    override suspend fun setReaderTextScale(percent: Int) =
        update { it.copy(readerTextScalePercent = percent.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE)) }

    override suspend fun setReaderFont(font: ReaderFont) = update { it.copy(readerFont = font) }

    override suspend fun setOnboardingCompleted() = update { it.copy(onboardingCompleted = true) }

    override suspend fun setLastTabId(id: String?) = update { it.copy(lastTabId = id) }

    override suspend fun installId(): String = install

    private fun update(change: (SkimSettings) -> SkimSettings) {
        current.value = change(current.value)
    }
}

class FakeHistoryRepository : HistoryRepository {
    private val rows = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var nextId = 1L

    /** When set, [matches] fails the way a broken history table would. */
    var failMatches = false

    /** How many times [matches] was called since [clearReads]. */
    var matchReads = 0
        private set

    fun clearReads() {
        matchReads = 0
    }

    override suspend fun recordVisit(url: String, title: String, at: Long) {
        if (!isWebUrl(url) || url.length > HistoryRepository.MAX_URL_LENGTH) return
        val host = hostOf(url) ?: return
        val titleCut = title.take(HistoryRepository.MAX_TITLE_LENGTH)
        val existing = rows.value.firstOrNull { it.url == url }
        rows.value = if (existing == null) {
            rows.value + HistoryEntry(id = nextId++, url = url, title = titleCut, host = host, visitedAt = at)
        } else {
            val latest = at >= existing.visitedAt
            rows.value.map { row ->
                if (row.id != existing.id) {
                    row
                } else {
                    row.copy(
                        title = if (latest && titleCut.isNotBlank()) titleCut else row.title,
                        visitedAt = maxOf(row.visitedAt, at),
                    )
                }
            }
        }
    }

    override suspend fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        rows.value = rows.value.map { if (it.url == url) it.copy(title = title.take(HistoryRepository.MAX_TITLE_LENGTH)) else it }
    }

    override fun observe(query: String?): Flow<List<HistoryEntry>> = rows.map { entries ->
        val text = query?.trim().orEmpty()
        entries
            .filter { text.isEmpty() || it.title.contains(text, ignoreCase = true) || it.url.contains(text, ignoreCase = true) }
            .sortedWith(compareByDescending<HistoryEntry> { it.visitedAt }.thenByDescending { it.id })
            .take(HistoryRepository.LIST_LIMIT)
    }

    override suspend fun matches(text: String, limit: Int): List<HistoryEntry> {
        matchReads++
        if (failMatches) error("history cannot be read")
        val trimmed = text.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        return rows.value
            .filter { it.title.contains(trimmed, ignoreCase = true) || it.url.contains(trimmed, ignoreCase = true) }
            .sortedWith(compareByDescending<HistoryEntry> { it.visitedAt }.thenByDescending { it.id })
            .take(limit)
    }

    override suspend fun remove(id: Long): HistoryEntry? {
        val entry = rows.value.firstOrNull { it.id == id } ?: return null
        rows.value = rows.value.filterNot { it.id == id }
        return entry
    }

    override suspend fun restore(entry: HistoryEntry) {
        val existing = rows.value.firstOrNull { it.url == entry.url }
        rows.value = if (existing == null) {
            rows.value + entry
        } else {
            val latest = entry.visitedAt >= existing.visitedAt
            rows.value.map { row ->
                if (row.id != existing.id) {
                    row
                } else {
                    row.copy(
                        title = if (latest && entry.title.isNotBlank()) entry.title else row.title,
                        visitedAt = maxOf(row.visitedAt, entry.visitedAt),
                    )
                }
            }
        }
    }

    override suspend fun clearAll() {
        rows.value = emptyList()
    }
}

class FakeBookmarkRepository : BookmarkRepository {
    private val folders = MutableStateFlow<List<BookmarkFolder>>(emptyList())
    private val marks = MutableStateFlow<List<Bookmark>>(emptyList())
    private var nextFolderId = 2L
    private var nextBookmarkId = 1L
    private var clock = 0L

    var matchReads = 0
        private set

    fun clearReads() {
        matchReads = 0
    }

    fun stored(): List<Bookmark> = marks.value

    /** An Imported folder holding one bookmark, for screens that list a finished import. */
    fun addImported(fileName: String, title: String, url: String): Long {
        val folderId = nextFolderId++
        val folder = BookmarkFolder(
            id = folderId,
            parentId = null,
            name = null,
            kind = FolderKind.IMPORTED,
            sourceFileName = fileName,
            position = folders.value.count { it.parentId == null },
            createdAt = ++clock,
        )
        folders.value = folders.value + folder
        add(folderId, title, url)
        return folderId
    }

    override fun observeRoot(): Flow<BookmarksRoot> = kotlinx.coroutines.flow.combine(folders, marks) { folderRows, bookmarkRows ->
        BookmarksRoot(
            folders = folderRows.filter { it.parentId == null }.sortedBy { it.position }.map { folder ->
                BookmarkFolderSummary(folder, countUnder(folder.id, folderRows, bookmarkRows))
            },
            recentlyAdded = bookmarkRows.sortedByDescending { it.addedAt }.take(BookmarkRepository.RECENTLY_ADDED_LIMIT),
        )
    }

    override fun observeFolder(id: Long): Flow<FolderContent> = kotlinx.coroutines.flow.combine(folders, marks) { folderRows, bookmarkRows ->
        FolderContent(
            folder = folderRows.firstOrNull { it.id == id },
            subfolders = folderRows.filter { it.parentId == id }.sortedBy { it.position }.map { folder ->
                BookmarkFolderSummary(folder, countUnder(folder.id, folderRows, bookmarkRows))
            },
            bookmarks = bookmarkRows.filter { it.folderId == id }.sortedBy { it.position },
        )
    }

    override fun observe(id: Long): Flow<Bookmark?> = marks.map { rows -> rows.firstOrNull { it.id == id } }

    override fun search(query: String): Flow<List<Bookmark>> = marks.map { rows ->
        val text = query.trim()
        if (text.isEmpty()) {
            emptyList()
        } else {
            rows.filter { it.title.contains(text, ignoreCase = true) || it.url.contains(text, ignoreCase = true) }
                .sortedByDescending { it.addedAt }
        }
    }

    override fun observeFolders(): Flow<List<BookmarkFolder>> = folders.map { rows -> rows.sortedWith(compareBy({ it.position }, { it.id })) }

    override fun observeByUrl(url: String): Flow<Bookmark?> = marks.map { rows ->
        rows.filter { it.url == url }.maxByOrNull { it.addedAt }
    }

    override suspend fun addToMobile(title: String, url: String): Long {
        require(url.isNotBlank())
        ensureMobile()
        return add(BookmarkFolder.MOBILE_ID, title, url)
    }

    override suspend fun update(id: Long, title: String, url: String) {
        require(url.isNotBlank())
        marks.value = marks.value.map { if (it.id == id) it.copy(title = title, url = url, host = hostOf(url).orEmpty()) else it }
    }

    override suspend fun move(id: Long, folderId: Long): Boolean {
        val bookmark = marks.value.firstOrNull { it.id == id } ?: return false
        if (folders.value.none { it.id == folderId }) return false
        val position = marks.value.count { it.folderId == folderId }
        marks.value = marks.value.map { if (it.id == bookmark.id) it.copy(folderId = folderId, position = position) else it }
        return true
    }

    override suspend fun topLevelFolders(): List<BookmarkFolder> =
        folders.value.filter { it.parentId == null && (it.kind == FolderKind.MOBILE || it.kind == FolderKind.IMPORTED) }
            .sortedBy { it.position }

    override suspend fun delete(id: Long): Bookmark? {
        val bookmark = marks.value.firstOrNull { it.id == id } ?: return null
        marks.value = marks.value.filterNot { it.id == id }
        return bookmark
    }

    override suspend fun restore(bookmark: Bookmark) {
        if (marks.value.any { it.id == bookmark.id }) return
        val folderId = if (folders.value.any { it.id == bookmark.folderId }) bookmark.folderId else BookmarkFolder.MOBILE_ID
        ensureMobile()
        marks.value = marks.value + bookmark.copy(folderId = folderId)
    }

    override suspend fun matches(text: String, limit: Int): List<Bookmark> {
        matchReads++
        val trimmed = text.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        return marks.value
            .filter { it.title.contains(trimmed, ignoreCase = true) || it.url.contains(trimmed, ignoreCase = true) }
            .sortedByDescending { it.addedAt }
            .take(limit)
    }

    private fun ensureMobile() {
        if (folders.value.any { it.id == BookmarkFolder.MOBILE_ID }) return
        folders.value = listOf(
            BookmarkFolder(
                id = BookmarkFolder.MOBILE_ID,
                parentId = null,
                name = null,
                kind = FolderKind.MOBILE,
                position = 0,
                createdAt = ++clock,
            ),
        ) + folders.value
    }

    private fun add(folderId: Long, title: String, url: String): Long {
        val id = nextBookmarkId++
        val bookmark = Bookmark(
            id = id,
            folderId = folderId,
            title = title,
            url = url,
            host = hostOf(url).orEmpty(),
            position = marks.value.count { it.folderId == folderId },
            addedAt = ++clock,
        )
        marks.value = marks.value + bookmark
        return id
    }

    private fun countUnder(id: Long, folderRows: List<BookmarkFolder>, bookmarkRows: List<Bookmark>): Int {
        val children = folderRows.filter { it.parentId == id }
        return bookmarkRows.count { it.folderId == id } + children.sumOf { countUnder(it.id, folderRows, bookmarkRows) }
    }
}

class FakeBookmarkFiles(var result: ImportResult = ImportResult.Failed(ImportResult.Reason.Unreadable)) : BookmarkFiles {
    val locations = mutableListOf<String>()

    override suspend fun import(location: String): ImportResult {
        locations += location
        return result
    }
}

class FakeShortcutRepository(private val held: MutableSharedFlow<List<Shortcut>>? = null) : ShortcutRepository {
    private val rows = MutableStateFlow<List<Shortcut>>(emptyList())
    private var nextId = 1L

    override fun observe(): Flow<List<Shortcut>> = held ?: rows.map { list ->
        list.sortedWith(compareBy({ it.position }, { it.id }))
    }

    override suspend fun add(name: String, url: String) {
        require(url.isNotBlank())
        val shortcut = Shortcut(
            id = nextId++,
            name = name.trim(),
            url = url.trim(),
            position = rows.value.maxOfOrNull { it.position }?.plus(1) ?: 0,
        )
        rows.value = rows.value + shortcut
    }

    override suspend fun update(id: Long, name: String, url: String) {
        require(url.isNotBlank())
        rows.value = rows.value.map { if (it.id == id) it.copy(name = name.trim(), url = url.trim()) else it }
    }

    override suspend fun remove(id: Long): Shortcut? {
        val shortcut = rows.value.firstOrNull { it.id == id } ?: return null
        rows.value = rows.value.filterNot { it.id == id }
        return shortcut
    }

    override suspend fun restore(shortcut: Shortcut) {
        if (rows.value.none { it.id == shortcut.id }) rows.value = rows.value + shortcut
    }
}

class FakeChatRepository : ChatRepository {
    private val chats = MutableStateFlow<List<SavedChat>>(emptyList())
    private val storedMessages = mutableListOf<SavedMessage>()
    private var nextChatId = 1L
    private var nextMessageId = 1L
    private val removalSignals = MutableSharedFlow<Long?>(extraBufferCapacity = 16)

    override val removals: SharedFlow<Long?> = removalSignals.asSharedFlow()

    fun insert(chat: SavedChat): Long {
        val id = if (chat.id > 0) chat.id else nextChatId++
        nextChatId = maxOf(nextChatId, id + 1)
        chats.value = chats.value.filterNot { it.id == id } + chat.copy(id = id)
        return id
    }

    override suspend fun createChat(chat: SavedChat): Long {
        val id = nextChatId++
        chats.value = chats.value + chat.copy(id = id)
        return id
    }

    override suspend fun messages(chatId: Long): List<SavedMessage> = storedMessages.filter { it.chatId == chatId }

    override suspend fun addMessage(message: SavedMessage): Long {
        require(message.chatId > 0) { "A message belongs to a saved chat" }
        val id = nextMessageId++
        storedMessages += message.copy(id = id)
        return id
    }

    override suspend fun updateChat(chat: SavedChat) {
        if (chats.value.none { it.id == chat.id }) return
        chats.value = chats.value.map { if (it.id == chat.id) chat else it }
    }

    override suspend fun updateMessage(message: SavedMessage) {
        val index = storedMessages.indexOfFirst { it.id == message.id }
        if (index >= 0) storedMessages[index] = message
    }

    override suspend fun deleteAll() {
        chats.value = emptyList()
        storedMessages.clear()
        removalSignals.emit(null)
    }

    override fun observeRecentPageChats(limit: Int): Flow<List<SavedChat>> = chats.map { rows ->
        rows.filter { it.kind == ChatKind.PAGE }
            .sortedWith(compareByDescending<SavedChat> { it.updatedAt }.thenByDescending { it.id })
            .take(limit.coerceAtLeast(0))
    }

    override fun observeAll(query: String?): Flow<List<ChatSummary>> = chats.map { rows ->
        val text = query?.trim().orEmpty()
        rows.filter { text.isEmpty() || it.title.contains(text, ignoreCase = true) || it.host.orEmpty().contains(text, ignoreCase = true) }
            .sortedWith(compareByDescending<SavedChat> { it.updatedAt }.thenByDescending { it.id })
            .map { chat -> summaryOf(chat) }
    }

    override fun observeCount(): Flow<Int> = chats.map { it.size }

    override suspend fun load(id: Long): SavedChat? = chats.value.firstOrNull { it.id == id }

    override suspend fun delete(id: Long): DeletedChat? {
        val chat = chats.value.firstOrNull { it.id == id } ?: return null
        val kept = storedMessages.filter { it.chatId == id }
        chats.value = chats.value.filterNot { it.id == id }
        storedMessages.removeAll { it.chatId == id }
        removalSignals.emit(id)
        return DeletedChat(chat, kept)
    }

    override suspend fun restore(deleted: DeletedChat) {
        if (chats.value.any { it.id == deleted.chat.id }) return
        chats.value = chats.value + deleted.chat
        storedMessages += deleted.messages
    }

    private fun summaryOf(chat: SavedChat): ChatSummary {
        val own = storedMessages.filter { it.chatId == chat.id }
        val kept = own.filter { it.status == MessageStatus.COMPLETE || it.status == MessageStatus.STOPPED }
        return ChatSummary(
            chat = chat,
            questionCount = own.count { it.kind == MessageKind.QUESTION },
            hasSummary = kept.any { it.kind == MessageKind.SUMMARY },
            answerCount = kept.count { it.role == MessageRole.ASSISTANT && (it.kind == MessageKind.ANSWER || it.kind == MessageKind.TABS_OVERVIEW) },
        )
    }
}

class FakeSitePermissionRepository : SitePermissionRepository {
    private val rows = MutableStateFlow<List<SitePermission>>(emptyList())
    private var clock = 0L

    override fun observe(host: String): Flow<SitePermission?> {
        val key = normalizeHost(host)
        return if (key.isEmpty()) kotlinx.coroutines.flow.flowOf(null) else rows.map { list -> list.firstOrNull { it.host == key } }
    }

    override suspend fun get(host: String): SitePermission? {
        val key = normalizeHost(host)
        return if (key.isEmpty()) null else rows.value.firstOrNull { it.host == key }
    }

    override suspend fun skimAiStates(hosts: Set<String>): Map<String, SkimAiState> {
        val stored = rows.value.associate { it.host to it.skimAi }
        return hosts.associateWith { host -> stored[normalizeHost(host)] ?: SkimAiState.UNSET }
    }

    override suspend fun setSkimAi(host: String, state: SkimAiState) {
        write(host) { it.copy(skimAi = state, updatedAt = ++clock) }
    }

    override suspend fun setPermission(host: String, kind: SitePermissionKind, state: PermissionState) {
        write(host) { current ->
            val next = when (kind) {
                SitePermissionKind.LOCATION -> current.copy(location = state)
                SitePermissionKind.CAMERA -> current.copy(camera = state)
                SitePermissionKind.MICROPHONE -> current.copy(microphone = state)
            }
            next.copy(updatedAt = ++clock)
        }
    }

    override suspend fun deleteAllSkimAiData() {
        rows.update { list ->
            list.map { it.copy(skimAi = SkimAiState.UNSET) }.filter { row ->
                row.location != PermissionState.UNSET || row.camera != PermissionState.UNSET || row.microphone != PermissionState.UNSET
            }
        }
    }

    private fun write(host: String, change: (SitePermission) -> SitePermission) {
        val key = normalizeHost(host)
        require(key.isNotEmpty())
        val current = rows.value.firstOrNull { it.host == key } ?: SitePermission(host = key, updatedAt = clock)
        val next = change(current)
        rows.value = rows.value.filterNot { it.host == key } + next
    }
}
