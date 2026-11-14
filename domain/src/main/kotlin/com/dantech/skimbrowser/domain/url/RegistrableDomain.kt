package com.dantech.skimbrowser.domain.url

/**
 * The registrable domain of [host]: the label under the public suffix, so
 * `accounts.google.com` and `google.com` are one site and `example.co.uk` stays whole.
 * An address, a single label, or a host this list cannot split is returned as [normalizeHost]
 * left it. The list is the common multi-label suffixes, not the full public suffix list.
 */
fun registrableDomain(host: String): String {
    val key = normalizeHost(host)
    if (key.isEmpty() || key.startsWith("[")) return key
    if (key.all { it.isDigit() || it == '.' }) return key
    val labels = key.split('.')
    if (labels.size < 2) return key
    val suffix = labels.takeLast(2).joinToString(".")
    if (labels.size >= 3 && suffix in MULTI_LABEL_SUFFIXES) {
        return labels.takeLast(3).joinToString(".")
    }
    return suffix
}

private val MULTI_LABEL_SUFFIXES = setOf(
    "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
    "com.au", "net.au", "org.au",
    "co.jp", "ne.jp", "or.jp",
    "com.br", "com.mx", "com.ar", "com.tr",
    "co.nz", "com.sg", "co.kr", "co.za", "co.in",
    "com.cn", "com.tw", "com.hk", "com.vn",
    "co.id", "com.my", "co.th", "com.ph", "com.ng",
    "co.il", "com.ua", "com.pl", "com.es",
)
