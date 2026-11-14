package com.dantech.skimbrowser.domain.shield

class ShieldStatBuffer {
    private val lock = Any()
    private val days = LinkedHashMap<Long, Day>()
    private var pendingEvents = 0

    fun pending(): Int = synchronized(lock) { pendingEvents }

    fun noteRequest(localEpochDay: Long, bytes: Long, host: String?) = synchronized(lock) {
        val day = days.getOrPut(localEpochDay) { Day() }
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

    fun notePopup(localEpochDay: Long) = note(localEpochDay) { it.popups = saturatingAdd(it.popups, 1) }

    fun noteRedirect(localEpochDay: Long) = note(localEpochDay) { it.redirects = saturatingAdd(it.redirects, 1) }

    fun noteCosmetic(localEpochDay: Long, rules: Int) {
        if (rules <= 0) return
        note(localEpochDay) { it.cosmetic = saturatingAdd(it.cosmetic, rules.toLong()) }
    }

    fun drain(): List<ShieldDelta> = synchronized(lock) {
        if (days.isEmpty()) return emptyList()
        val out = ArrayList<ShieldDelta>(days.size)
        for ((localEpochDay, day) in days) {
            out += ShieldDelta(
                localEpochDay = localEpochDay,
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

    private fun note(localEpochDay: Long, edit: (Day) -> Unit) = synchronized(lock) {
        val day = days.getOrPut(localEpochDay) { Day() }
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
        const val FLUSH_AT = 32

        const val MAX_HOSTS = 64
    }
}
