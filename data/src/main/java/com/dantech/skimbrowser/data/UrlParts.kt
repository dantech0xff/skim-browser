package com.dantech.skimbrowser.data

import com.dantech.skimbrowser.domain.url.hostOf as domainHostOf
import com.dantech.skimbrowser.domain.url.isWebUrl as domainIsWebUrl
import com.dantech.skimbrowser.domain.url.normalizeHost as domainNormalizeHost
import com.dantech.skimbrowser.domain.url.withoutFragment as domainWithoutFragment

/** Delegates to the domain parser so existing callers keep compiling. */
fun isWebUrl(url: String): Boolean = domainIsWebUrl(url)

/** Delegates to the domain parser so existing callers keep compiling. */
fun hostOf(url: String): String? = domainHostOf(url)

/** Delegates to the domain parser so existing callers keep compiling. */
fun withoutFragment(url: String): String = domainWithoutFragment(url)

/** Lowercases [host] and drops a leading "www.", the form every host-keyed table stores. */
fun normalizeHost(host: String): String = domainNormalizeHost(host)
