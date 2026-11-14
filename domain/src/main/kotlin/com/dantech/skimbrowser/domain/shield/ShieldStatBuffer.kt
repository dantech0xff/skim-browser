package com.dantech.skimbrowser.domain.shield

/**
 * Pending blocking counts. The page-load path calls [noteRequest], [notePopup], [noteRedirect],
 * and [noteCosmetic]. [drain] is the only way out, and a caller does that on a background
 * coroutine after a short wait, or sooner once [pending] reaches [FLUSH_AT].
 *
 * Private-tab requests pass a null host. A flush keeps at most [MAX_HOSTS] sites per day. Further
 * new sites still increment the request count and are left off the site list until the next flush
 * has room.
 */
class ShieldStatBuffer {
    private val lock = Any()
    private val days = LinkedHashMap<Long, Day>()
    private var pendingEvents = 0

    fun pending(): Int = synchronized(lock) { pendingEvents }

    fun noteRequest(epochDay: Long, bytes: Long, host: String?) = synchronized(lock) {
        val day = days.getOrPut(epochDay) { Day() }
        day.requests = saturatingAdd(day.requests, 1)
        day.bytes = saturatingAdd(day.bytes, bytes)
        if (!host.isNullOrEmpty()) {
            val current = day.hosts[host]
            if (current != null) {
                day.hosts[host] = saturatingAdd(current, 1)
            } else if (day.hosts.size < MAX_HOSTS) {
                day.hosts[host] = 1
            }
        }
        pendingEvents = (pendingEvents + 1).coerceAtMost(Int.MAX_VALUE)
    }

    fun notePopup(epochDay: Long) = note(epochDay) { it.popups = saturatingAdd(it.popups, 1) }

    fun noteRedirect(epochDay: Long) = note(epochDay) { it.redirects = saturatingAdd(it.redirects, 1) }

    fun noteCosmetic(epochDay: Long, rules: Int) {
        if (rules <= 0) return
        note(epochDay) { it.cosmetic = saturatingAdd(it.cosmetic, rules.toLong()) }
    }

    fun drain(): List<ShieldDelta> = synchronized(lock) {
        if (days.isEmpty()) return emptyList()
        val out = ArrayList<ShieldDelta>(days.size)
        for ((epochDay, day) in days) {
            out += ShieldDelta(
                epochDay = epochDay,
                requests = day.requests,
                popups = day.popups,
                redirects = day.redirects,
                cosmetic = day.cosmetic,
                bytes = day.bytes,
                hosts = day.hosts.toMap(),
            )
        }
        days.clear()
        pendingEvents = 0
        out
    }

    fun clear() = synchronized(lock) {
        days.clear()
        pendingEvents = 0
    }

    private fun note(epochDay: Long, edit: (Day) -> Unit) = synchronized(lock) {
        val day = days.getOrPut(epochDay) { Day() }
        edit(day)
        pendingEvents = (pendingEvents + 1).coerceAtMost(Int.MAX_VALUE)
    }

    private class Day {
        var requests = 0L
        var popups = 0L
        var redirects = 0L
        var cosmetic = 0L
        var bytes = 0L
        val hosts = LinkedHashMap<String, Long>()
    }

    companion object {
        /** A flush that has this many events does not wait out the timer. */
        const val FLUSH_AT = 32

        /** Sites remembered in one flush. The request count still includes the rest. */
        const val MAX_HOSTS = 64
    }
}
