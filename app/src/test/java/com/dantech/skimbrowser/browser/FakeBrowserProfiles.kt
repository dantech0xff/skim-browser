package com.dantech.skimbrowser.browser

/**
 * [BrowserProfiles] in memory, with the default profile present. Like `ProfileStore`, every call but
 * the feature checks fails when profiles are not supported, and a profile used in this process
 * cannot be deleted.
 */
class FakeBrowserProfiles(
    override var isMultiProfileSupported: Boolean = true,
    override var hasHttpCacheManager: Boolean = true,
) : BrowserProfiles {

    class Profile(override val name: String) : BrowserProfile {
        /** The clean-up calls made on this profile, in order. */
        val calls = mutableListOf<String>()

        /** A call that throws, to check that the other steps still run. */
        var failing: String? = null

        override fun disableHttpCache() = record("disableHttpCache")

        override fun clearCookies() = record("clearCookies")

        override fun clearWebStorage() = record("clearWebStorage")

        override fun clearGeolocationPermissions() = record("clearGeolocationPermissions")

        /** The `Cookie` header of each URL, as the profile's cookie store would send it. */
        val cookies = mutableMapOf<String, String>()

        /** The URLs whose cookies were asked for, in order. */
        val cookieLookups = mutableListOf<String>()

        override fun cookieHeader(url: String): String? {
            cookieLookups += url
            return cookies[url]
        }

        override fun expireCookiesForHost(host: String) {
            record("expireCookies:$host")
            expireHostCookies(
                host,
                read = { cookies[it] },
                write = { url, _ -> cookies.remove(url) },
                flush = { record("flushCookies") },
            )
        }

        override fun deleteOriginsForHost(host: String) {
            record("deleteOrigins:$host")
        }

        private fun record(call: String) {
            calls += call
            if (failing == call) throw IllegalStateException("$call failed")
        }
    }

    val profiles = linkedMapOf(DEFAULT to Profile(DEFAULT))

    /** Profiles loaded in this process; they cannot be deleted until it ends. */
    val loaded = mutableSetOf<String>()
    val created = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    /** Every profile [delete] was asked for, whether or not it could be deleted. */
    val deleteCalls = mutableListOf<String>()

    /** Makes [delete] throw instead of answering, as a misbehaving WebView might. */
    var deleteThrows = false

    /** Calls made while profiles were not supported; each of them threw. */
    var callsWhileUnsupported = 0
        private set

    override fun getOrCreate(name: String): BrowserProfile {
        requireSupported()
        loaded += name
        return profiles.getOrPut(name) {
            created += name
            Profile(name)
        }
    }

    override fun get(name: String): BrowserProfile? {
        requireSupported()
        return profiles[name]?.also { loaded += name }
    }

    override fun names(): List<String> {
        requireSupported()
        return profiles.keys.toList()
    }

    override fun delete(name: String): Boolean {
        requireSupported()
        deleteCalls += name
        if (deleteThrows) throw IllegalStateException("Profile $name is in use")
        if (name in loaded || name == DEFAULT) return false
        val removed = profiles.remove(name) != null
        if (removed) deleted += name
        return removed
    }

    fun profile(name: String): Profile = profiles.getValue(name)

    private fun requireSupported() {
        if (!isMultiProfileSupported) {
            callsWhileUnsupported++
            throw UnsupportedOperationException("MULTI_PROFILE is not supported")
        }
    }

    companion object {
        const val DEFAULT = "Default"
    }
}
