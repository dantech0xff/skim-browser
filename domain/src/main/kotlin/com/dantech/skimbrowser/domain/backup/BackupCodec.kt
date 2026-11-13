package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.text.toVisibleText
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.isWebUrl
import com.dantech.skimbrowser.domain.url.normalizeHost
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull

/** A backup file after it has been read, or the reason it was refused. */
sealed interface BackupDecode {
    data class Ok(val document: BackupDocument) : BackupDecode

    data class Rejected(val failure: BackupFailure) : BackupDecode
}

/**
 * The backup file format. [encode] and [decode] are pure. A file is refused when it is not this
 * schema, names a schema version this build does not read, or is larger or more deeply nested
 * than [MAX_BYTES] and [MAX_DEPTH]. Unknown fields are ignored, so a token or a chat that a
 * crafted file adds is dropped and cannot come back out.
 */
object BackupCodec {
    const val SCHEMA_VERSION = 1

    /** The most bytes read from a file. Ten megabytes holds a large export and stops a huge one. */
    const val MAX_BYTES = 10 * 1024 * 1024

    /** Nesting deeper than this is refused before the parser walks it. */
    const val MAX_DEPTH = 32

    const val MAX_BOOKMARKS = 20_000
    const val MAX_HISTORY = 20_000
    const val MAX_TABS = 500
    const val MAX_PERMISSIONS = 5_000
    const val MAX_FOLDERS = 5_000

    private const val MAX_APP_VERSION = 32

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val schemaVersion = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")

    fun encode(document: BackupDocument): String = json.encodeToString(WireDocument.serializer(), document.toWire())

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeTo(document: BackupDocument, output: OutputStream) {
        json.encodeToStream(WireDocument.serializer(), document.toWire(), output)
    }

    fun decode(text: String): BackupDecode = decode(text.byteInputStream())

    /**
     * Reads [input] up to [maxBytes]. Parsing stops as soon as the cap or the depth cap is hit,
     * so the rest of a huge file is never pulled in.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: InputStream, maxBytes: Int = MAX_BYTES): BackupDecode {
        val guarded = GuardedInputStream(input, maxBytes, MAX_DEPTH)
        return try {
            val element = json.decodeFromStream<kotlinx.serialization.json.JsonElement>(guarded)
            val obj = element as? JsonObject ?: return BackupDecode.Rejected(BackupFailure.MALFORMED)
            when (val version = schemaVersionOf(obj)) {
                null -> BackupDecode.Rejected(BackupFailure.MALFORMED)
                SCHEMA_VERSION -> decodeWire(json.decodeFromJsonElement(WireDocument.serializer(), obj))
                else -> BackupDecode.Rejected(BackupFailure.UNSUPPORTED_VERSION)
            }
        } catch (error: Exception) {
            if (error.tooLarge()) return BackupDecode.Rejected(BackupFailure.TOO_LARGE)
            if (error !is SerializationException && error !is IllegalArgumentException) throw error
            val version = schemaVersion.find(guarded.header())?.groupValues?.get(1)?.toIntOrNull()
            val failure = if (version != null && version != SCHEMA_VERSION) {
                BackupFailure.UNSUPPORTED_VERSION
            } else {
                BackupFailure.MALFORMED
            }
            BackupDecode.Rejected(failure)
        }
    }

    /** True when [bytes] look like a Netscape bookmark file rather than a JSON backup. */
    fun looksLikeHtml(bytes: ByteArray): Boolean = sniff(bytes) == Sniff.HTML

    /** The first meaningful byte of [bytes], skipping a UTF-8 BOM and whitespace. */
    fun sniff(bytes: ByteArray): Sniff {
        var index = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            index = 3
        }
        while (index < bytes.size && bytes[index].toInt().toChar().isWhitespace()) index++
        return when (bytes.getOrNull(index)?.toInt()?.toChar()) {
            '{' -> Sniff.JSON
            '<' -> Sniff.HTML
            else -> Sniff.OTHER
        }
    }

    enum class Sniff { JSON, HTML, OTHER }

    private fun decodeWire(wire: WireDocument): BackupDecode {
        if (wire.schemaVersion != SCHEMA_VERSION) return BackupDecode.Rejected(BackupFailure.UNSUPPORTED_VERSION)
        val version = wire.appVersion.trim().toVisibleText().cutTo(MAX_APP_VERSION)
        if (version.isEmpty()) return BackupDecode.Rejected(BackupFailure.MALFORMED)
        val bookmarks = wire.bookmarks?.let { mapFolders(it, root = true) }
        val history = wire.history?.let { mapHistory(it) }
        val tabs = wire.tabs?.let { mapTabs(it) }
        val settings = wire.settings?.let { mapSettings(it) }
        val permissions = wire.sitePermissions?.let { mapPermissions(it) }
        if (bookmarks is MapFail || history is MapFail || tabs is MapFail || settings is MapFail || permissions is MapFail) {
            val failure = listOf(bookmarks, history, tabs, settings, permissions).filterIsInstance<MapFail>().first().failure
            return BackupDecode.Rejected(failure)
        }
        @Suppress("UNCHECKED_CAST")
        return BackupDecode.Ok(
            BackupDocument(
                schemaVersion = SCHEMA_VERSION,
                appVersion = version,
                createdAt = wire.createdAt,
                bookmarks = bookmarks as List<BackupFolder>?,
                history = history as List<BackupHistory>?,
                tabs = tabs as List<BackupTab>?,
                settings = settings as BackupSettings?,
                sitePermissions = permissions as List<BackupSitePermission>?,
            ),
        )
    }

    private fun mapFolders(folders: List<WireFolder>, root: Boolean, depth: Int = 0): Any {
        if (depth > MAX_DEPTH || folders.size > MAX_FOLDERS) return MapFail(BackupFailure.TOO_LARGE)
        var bookmarks = 0
        var folderCount = folders.size
        val mapped = ArrayList<BackupFolder>(folders.size)
        var seenMobile = false
        for (folder in folders) {
            val kind = enumOrNull<FolderKind>(folder.kind) ?: return MapFail(BackupFailure.MALFORMED)
            val children = mapFolders(folder.folders, root = false, depth = depth + 1)
            if (children is MapFail) return children
            @Suppress("UNCHECKED_CAST")
            val childFolders = children as List<BackupFolder>
            if (folder.bookmarks.size > MAX_BOOKMARKS) return MapFail(BackupFailure.TOO_LARGE)
            val links = ArrayList<BackupBookmark>()
            for (bookmark in folder.bookmarks) {
                val url = bookmark.url.trim()
                if (url.length > HistoryRepository.MAX_URL_LENGTH || hostOf(url) == null) continue
                links += BackupBookmark(
                    title = bookmark.title.toVisibleText().cutTo(HistoryRepository.MAX_TITLE_LENGTH),
                    url = url,
                    addedAt = bookmark.addedAt,
                )
            }
            bookmarks += links.size + countBookmarks(childFolders)
            folderCount += countFolders(childFolders)
            if (bookmarks > MAX_BOOKMARKS || folderCount > MAX_FOLDERS) return MapFail(BackupFailure.TOO_LARGE)
            val atRoot = root && kind == FolderKind.MOBILE && !seenMobile
            if (atRoot) seenMobile = true
            mapped += BackupFolder(
                kind = if (atRoot) FolderKind.MOBILE else if (kind == FolderKind.MOBILE) FolderKind.FOLDER else kind,
                name = folder.name?.toVisibleText()?.cutTo(HistoryRepository.MAX_TITLE_LENGTH),
                sourceFileName = folder.sourceFileName?.toVisibleText()?.cutTo(HistoryRepository.MAX_TITLE_LENGTH),
                folders = childFolders,
                bookmarks = links,
            )
        }
        return mapped
    }

    private fun mapHistory(rows: List<WireHistory>): Any {
        if (rows.size > MAX_HISTORY) return MapFail(BackupFailure.TOO_LARGE)
        return rows.mapNotNull { row ->
            val url = row.url.trim()
            if (!isWebUrl(url) || url.length > HistoryRepository.MAX_URL_LENGTH || hostOf(url) == null) {
                null
            } else {
                BackupHistory(url, row.title.toVisibleText().cutTo(HistoryRepository.MAX_TITLE_LENGTH), row.visitedAt)
            }
        }
    }

    private fun mapTabs(rows: List<WireTab>): Any {
        if (rows.size > MAX_TABS) return MapFail(BackupFailure.TOO_LARGE)
        return rows.mapNotNull { row ->
            val url = row.url.trim()
            if (!isWebUrl(url) || url.length > HistoryRepository.MAX_URL_LENGTH || hostOf(url) == null) {
                null
            } else {
                BackupTab(url, row.title.toVisibleText().cutTo(HistoryRepository.MAX_TITLE_LENGTH), row.order)
            }
        }
    }

    private fun mapSettings(wire: WireSettings): Any {
        val engine = enumOrNull<SearchEngine>(wire.searchEngine) ?: return MapFail(BackupFailure.MALFORMED)
        val theme = enumOrNull<ThemeSetting>(wire.theme) ?: return MapFail(BackupFailure.MALFORMED)
        val font = enumOrNull<ReaderFont>(wire.readerFont) ?: return MapFail(BackupFailure.MALFORMED)
        if (wire.shieldDisabledHosts.size > MAX_PERMISSIONS) return MapFail(BackupFailure.TOO_LARGE)
        return BackupSettings(
            searchEngine = engine,
            theme = theme,
            askBeforeSending = wire.askBeforeSending,
            keepChatHistory = wire.keepChatHistory,
            sendRatings = wire.sendRatings,
            readerTextScalePercent = wire.readerTextScalePercent.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE),
            pageTextZoomPercent = wire.pageTextZoomPercent?.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE),
            readerFont = font,
            onboardingCompleted = wire.onboardingCompleted,
            scamWarnings = wire.scamWarnings,
            shieldDisabledHosts = wire.shieldDisabledHosts.map(::normalizeHost).filter { it.isNotEmpty() }.toSet(),
        )
    }

    private fun mapPermissions(rows: List<WirePermission>): Any {
        if (rows.size > MAX_PERMISSIONS) return MapFail(BackupFailure.TOO_LARGE)
        val mapped = ArrayList<BackupSitePermission>()
        for (row in rows) {
            val host = normalizeHost(row.host)
            if (host.isEmpty()) continue
            val skimAi = enumOrNull<SkimAiState>(row.skimAi) ?: return MapFail(BackupFailure.MALFORMED)
            val location = enumOrNull<PermissionState>(row.location) ?: return MapFail(BackupFailure.MALFORMED)
            val camera = enumOrNull<PermissionState>(row.camera) ?: return MapFail(BackupFailure.MALFORMED)
            val microphone = enumOrNull<PermissionState>(row.microphone) ?: return MapFail(BackupFailure.MALFORMED)
            mapped += BackupSitePermission(host, skimAi, location, camera, microphone)
        }
        return mapped
    }
}

private class MapFail(val failure: BackupFailure)

private const val HEADER_BYTES = 1_024

private class BackupTooLarge : RuntimeException()

private fun Throwable.tooLarge(): Boolean {
    val seen = HashSet<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is BackupTooLarge) return true
        current = current.cause
    }
    return false
}

/**
 * Counts bytes and brace depth as the JSON parser reads. A string's braces do not count.
 * Throws [BackupTooLarge] at the cap so the parser never sees the rest.
 */
private class GuardedInputStream(
    private val upstream: InputStream,
    private val maxBytes: Int,
    private val maxDepth: Int,
) : InputStream() {
    private var bytes = 0
    private var depth = 0
    private var inString = false
    private var escape = false
    private val headerBytes = ByteArrayOutputStream()

    fun header(): String = headerBytes.toString(Charsets.UTF_8)

    override fun read(): Int {
        val value = upstream.read()
        if (value >= 0) observe(value)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = upstream.read(buffer, offset, length)
        if (count > 0) {
            for (index in offset until offset + count) observe(buffer[index].toInt() and 0xff)
        }
        return count
    }

    private fun observe(value: Int) {
        if (headerBytes.size() < HEADER_BYTES) headerBytes.write(value)
        if (++bytes > maxBytes) throw BackupTooLarge()
        if (inString) {
            when {
                escape -> escape = false
                value == '\\'.code -> escape = true
                value == '"'.code -> inString = false
            }
            return
        }
        when (value) {
            '"'.code -> inString = true
            '{'.code, '['.code -> if (++depth > maxDepth) throw BackupTooLarge()
            '}'.code, ']'.code -> if (depth > 0) depth--
        }
    }
}

@Serializable
private data class WireDocument(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val bookmarks: List<WireFolder>? = null,
    val history: List<WireHistory>? = null,
    val tabs: List<WireTab>? = null,
    val settings: WireSettings? = null,
    val sitePermissions: List<WirePermission>? = null,
)

@Serializable
private data class WireFolder(
    val kind: String,
    val name: String? = null,
    val sourceFileName: String? = null,
    val folders: List<WireFolder> = emptyList(),
    val bookmarks: List<WireBookmark> = emptyList(),
)

@Serializable
private data class WireBookmark(
    val title: String,
    val url: String,
    val addedAt: Long = 0,
)

@Serializable
private data class WireHistory(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

@Serializable
private data class WireTab(
    val url: String,
    val title: String,
    val order: Int,
)

@Serializable
private data class WireSettings(
    val searchEngine: String,
    val theme: String,
    val askBeforeSending: Boolean,
    val keepChatHistory: Boolean,
    val sendRatings: Boolean? = null,
    val readerTextScalePercent: Int,
    val pageTextZoomPercent: Int? = null,
    val readerFont: String,
    val onboardingCompleted: Boolean,
    val scamWarnings: Boolean = true,
    val shieldDisabledHosts: List<String> = emptyList(),
)

@Serializable
private data class WirePermission(
    val host: String,
    val skimAi: String = "UNSET",
    val location: String = "UNSET",
    val camera: String = "UNSET",
    val microphone: String = "UNSET",
)

private fun BackupDocument.toWire(): WireDocument = WireDocument(
    schemaVersion = schemaVersion,
    appVersion = appVersion,
    createdAt = createdAt,
    bookmarks = bookmarks?.map { it.toWire() },
    history = history?.map { WireHistory(it.url, it.title, it.visitedAt) },
    tabs = tabs?.map { WireTab(it.url, it.title, it.order) },
    settings = settings?.toWire(),
    sitePermissions = sitePermissions?.map {
        WirePermission(it.host, it.skimAi.name, it.location.name, it.camera.name, it.microphone.name)
    },
)

private fun BackupFolder.toWire(): WireFolder = WireFolder(
    kind = kind.name,
    name = name,
    sourceFileName = sourceFileName,
    folders = folders.map { it.toWire() },
    bookmarks = bookmarks.map { WireBookmark(it.title, it.url, it.addedAt) },
)

private fun BackupSettings.toWire(): WireSettings = WireSettings(
    searchEngine = searchEngine.name,
    theme = theme.name,
    askBeforeSending = askBeforeSending,
    keepChatHistory = keepChatHistory,
    sendRatings = sendRatings,
    readerTextScalePercent = readerTextScalePercent,
    pageTextZoomPercent = pageTextZoomPercent,
    readerFont = readerFont.name,
    onboardingCompleted = onboardingCompleted,
    scamWarnings = scamWarnings,
    shieldDisabledHosts = shieldDisabledHosts.sorted(),
)

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name == name }

/** The schema version when it is a JSON number. A string is refused, so `"1"` is not version 1. */
private fun schemaVersionOf(obj: JsonObject): Int? {
    val value = obj["schemaVersion"] as? JsonPrimitive ?: return null
    if (value.isString) return null
    return value.intOrNull
}

internal fun countBookmarks(folders: List<BackupFolder>): Int =
    folders.sumOf { it.bookmarks.size + countBookmarks(it.folders) }

private fun countFolders(folders: List<BackupFolder>): Int =
    folders.size + folders.sumOf { countFolders(it.folders) }
