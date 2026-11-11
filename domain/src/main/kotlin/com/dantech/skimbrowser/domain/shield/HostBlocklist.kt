package com.dantech.skimbrowser.domain.shield

import com.dantech.skimbrowser.domain.url.normalizeHost
import java.io.BufferedReader

/**
 * Hosts compiled from EasyList and EasyPrivacy. A request is blocked when its host, or a parent
 * of that host, is on the list. The first matching suffix wins, so a more specific allow beats a
 * broader block.
 *
 * Hosts are stored as sorted 64-bit FNV-1a hashes. The raw names are not kept, so a loaded list of
 * the shipped snapshot stays near the size of the hashes. [fnv64] hashes the names once at load.
 */
class HostBlocklist internal constructor(
    private val blockAll: LongArray,
    private val blockThird: LongArray,
    private val allowAll: LongArray,
    private val allowThird: LongArray,
) {
    /**
     * True when [requestHost] should not be loaded for a page on [documentHost]. A null document
     * is treated as a third party, so tracker hosts are still blocked before the page URL is known.
     * Hosts are the form returned by hostOf.
     */
    fun blocks(documentHost: String?, requestHost: String): Boolean {
        if (!requestHost.contains('.')) return false
        val third = documentHost == null || isThirdParty(documentHost, requestHost)
        for (suffix in hostSuffixes(requestHost)) {
            val hash = fnv64(suffix)
            if (contains(allowAll, hash) || (third && contains(allowThird, hash))) return false
            if (contains(blockAll, hash) || (third && contains(blockThird, hash))) return true
        }
        return false
    }

    companion object {
        val EMPTY = HostBlocklist(LongArray(0), LongArray(0), LongArray(0), LongArray(0))

        /** A list built from host names. Tests and the asset loader both use this. */
        fun of(
            blockAll: Set<String> = emptySet(),
            blockThird: Set<String> = emptySet(),
            allowAll: Set<String> = emptySet(),
            allowThird: Set<String> = emptySet(),
        ): HostBlocklist = HostBlocklist(
            hashes(blockAll),
            hashes(blockThird),
            hashes(allowAll),
            hashes(allowThird),
        )

        /**
         * Reads the compiled asset. Lines are `B host`, `T host` (third party), `A host` (allow)
         * or `X host` (allow third party). `#` lines are the licence header.
         */
        fun load(reader: BufferedReader): HostBlocklist {
            val blockAll = ArrayList<Long>()
            val blockThird = ArrayList<Long>()
            val allowAll = ArrayList<Long>()
            val allowThird = ArrayList<Long>()
            reader.forEachLine { line ->
                if (line.length < 3 || line[0] == '#') return@forEachLine
                val host = normalizeHost(line.substring(2))
                if (!host.contains('.')) return@forEachLine
                val hash = fnv64(host)
                when (line[0]) {
                    'B' -> blockAll += hash
                    'T' -> blockThird += hash
                    'A' -> allowAll += hash
                    'X' -> allowThird += hash
                }
            }
            return HostBlocklist(
                blockAll.toLongArray().also { it.sort() },
                blockThird.toLongArray().also { it.sort() },
                allowAll.toLongArray().also { it.sort() },
                allowThird.toLongArray().also { it.sort() },
            )
        }

        /** FNV-1a 64-bit over the UTF-8 bytes of [host]. */
        fun fnv64(host: String): Long {
            var hash = FNV_OFFSET
            for (byte in host.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= FNV_PRIME
            }
            return hash
        }

        private fun hashes(hosts: Set<String>): LongArray =
            hosts.map { fnv64(normalizeHost(it)) }.toLongArray().also { it.sort() }

        private fun contains(sorted: LongArray, hash: Long): Boolean =
            java.util.Arrays.binarySearch(sorted, hash) >= 0

        private const val FNV_OFFSET = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
    }
}

/** True when [request] is not [page] and neither host is a subdomain of the other. */
internal fun isThirdParty(page: String, request: String): Boolean {
    if (page == request) return false
    if (request.length > page.length && request.endsWith(".$page")) return false
    if (page.length > request.length && page.endsWith(".$request")) return false
    return true
}

/** [host] and each parent that still contains a dot. `a.b.com` yields `a.b.com`, then `b.com`. */
internal fun hostSuffixes(host: String): Sequence<String> = sequence {
    var start = 0
    while (start < host.length) {
        val suffix = host.substring(start)
        if (!suffix.contains('.')) break
        yield(suffix)
        val dot = host.indexOf('.', start)
        if (dot < 0) break
        start = dot + 1
    }
}
