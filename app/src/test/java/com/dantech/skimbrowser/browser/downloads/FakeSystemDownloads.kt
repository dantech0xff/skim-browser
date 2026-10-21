package com.dantech.skimbrowser.browser.downloads

import android.net.Uri

/**
 * [SystemDownloads] without DownloadManager: it records what was enqueued and answers with the
 * status, URI and type a test sets for each id.
 */
class FakeSystemDownloads : SystemDownloads {

    val enqueued = mutableListOf<SystemDownloadRequest>()
    val statuses = mutableMapOf<Long, Int>()
    val uris = mutableMapOf<Long, Uri>()
    val mimeTypes = mutableMapOf<Long, String>()

    /** Thrown by [enqueue] instead of taking the download, when set. */
    var refusal: RuntimeException? = null

    /** How many times each id's status was asked for. */
    val statusQueries = mutableMapOf<Long, Int>()

    private var lastId = 100L

    override fun enqueue(request: SystemDownloadRequest): Long {
        refusal?.let { throw it }
        enqueued += request
        return ++lastId
    }

    override fun status(id: Long): Int? {
        statusQueries[id] = (statusQueries[id] ?: 0) + 1
        return statuses[id]
    }

    override fun uriFor(id: Long): Uri? = uris[id]

    override fun mimeTypeFor(id: Long): String? = mimeTypes[id]
}
