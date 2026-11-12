package com.dantech.skimbrowser.domain.phishing

import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.isWebUrl
import java.net.IDN
import kotlin.math.abs

/**
 * The on-device scam check for one http(s) URL. Null when the address is not one Skim warns on:
 * a real listed site, a local address, or a name that does not imitate the bundled list.
 *
 * The brand table is read here, on first call, not when the process starts.
 */
fun classifyPhishing(url: String): SafetyWarning.Heuristic? {
    if (!isWebUrl(url)) return null
    val trimmed = url.trim()
    val host = hostOf(trimmed) ?: return null
    if (hasUserInfo(trimmed)) {
        return SafetyWarning.Heuristic(trimmed, host, PhishingReason.USER_INFO, brand = null)
    }
    if (isIpHost(host)) {
        if (isLocalNetwork(host)) return null
        return SafetyWarning.Heuristic(trimmed, host, PhishingReason.RAW_IP, brand = null)
    }
    if (isOfficialHost(host)) return null
    val labels = host.split('.')
    // google.ca and paypal.co.uk are the brand, not a lookalike. A typo still warns.
    if (isExactBrandOnCountrySuffix(labels)) return null
    embeddedBrand(labels)?.let { brand ->
        return SafetyWarning.Heuristic(trimmed, host, PhishingReason.SUBDOMAIN, brand.name)
    }
    plantedBrand(labels)?.let { brand ->
        return SafetyWarning.Heuristic(trimmed, host, PhishingReason.SUBDOMAIN, brand.name)
    }
    val decoded = labels.map(::decodeLabel)
    val mixed = decoded.any(::mixesScripts)
    val sld = registrableLabel(labels)?.let(::decodeLabel)
    val matched = sld?.let(::matchBrand)
    val plantedFold = decoded
        .dropLast(suffixLength(labels).coerceAtMost(decoded.size))
        .firstNotNullOfOrNull { label -> matchBrand(label)?.takeIf { it.second } }
    return when {
        matched != null && matched.second ->
            SafetyWarning.Heuristic(trimmed, host, PhishingReason.HOMOGRAPH, matched.first.name)
        plantedFold != null ->
            SafetyWarning.Heuristic(trimmed, host, PhishingReason.HOMOGRAPH, plantedFold.first.name)
        mixed -> {
            val named = matched?.first
            if (named != null) {
                SafetyWarning.Heuristic(trimmed, host, PhishingReason.HOMOGRAPH, named.name)
            } else {
                SafetyWarning.Heuristic(trimmed, host, PhishingReason.MIXED_SCRIPT, brand = null)
            }
        }
        matched != null ->
            SafetyWarning.Heuristic(trimmed, host, PhishingReason.LOOKALIKE, matched.first.name)
        else -> null
    }
}

/**
 * Hosts the person chose to open anyway, for this process only. A Safe Browsing allow also
 * covers the on-device check. An allow in incognito does not cover a normal tab.
 */
class PhishingSession {
    private val heuristicAllowed = HashSet<String>()
    private val safeBrowsingAllowed = HashSet<String>()

    fun heuristic(url: String, incognito: Boolean, enabled: Boolean): SafetyWarning.Heuristic? {
        if (!enabled) return null
        val hit = classifyPhishing(url) ?: return null
        val key = allowKey(hit.host, incognito)
        if (key in heuristicAllowed || key in safeBrowsingAllowed) return null
        return hit
    }

    fun allowsSafeBrowsing(host: String, incognito: Boolean): Boolean =
        allowKey(host, incognito) in safeBrowsingAllowed

    fun allowHeuristic(host: String, incognito: Boolean) {
        heuristicAllowed += allowKey(host, incognito)
    }

    fun allowSafeBrowsing(host: String, incognito: Boolean) {
        val key = allowKey(host, incognito)
        safeBrowsingAllowed += key
        heuristicAllowed += key
    }
}

private fun allowKey(host: String, incognito: Boolean): String =
    (if (incognito) "i:" else "n:") + host

private fun hasUserInfo(url: String): Boolean {
    val rest = url.trim().substringAfter("://").replace('\\', '/')
    val authority = rest.substringBefore('#').substringBefore('?').substringBefore('/')
    val at = authority.lastIndexOf('@')
    if (at < 0) return false
    val info = authority.substring(0, at)
    val user = info.substringBefore(':')
    val password = if (':' in info) info.substringAfter(':') else ""
    return user.isNotEmpty() || password.isNotEmpty()
}

private fun isIpHost(host: String): Boolean = ipv4FromHost(host) != null || (host.startsWith("[") && host.endsWith("]"))

/**
 * Loopback, private, shared, and link-local hosts. A router, a NAS, or a dev server on one of
 * these ranges is not a public scam address.
 */
private fun isLocalNetwork(host: String): Boolean {
    ipv4FromHost(host)?.let { return isLocalIpv4(it) }
    if (host.startsWith("[") && host.endsWith("]")) {
        return isLocalIpv6(host.substring(1, host.length - 1))
    }
    return false
}

/**
 * The IPv4 address [host] names, including the decimal, hex, and octal forms a browser accepts
 * (`8.8.8.8`, `0x8.0x8.0x8.0x8`, `134744072`). Null when [host] is a name.
 */
private fun ipv4FromHost(host: String): IntArray? {
    if (host.isEmpty() || host.any { it == ':' || it == '[' || it == ']' }) return null
    val body = if (host.endsWith('.')) host.dropLast(1) else host
    if (body.isEmpty()) return null
    val parts = body.split('.')
    if (parts.size > 4) return null
    val numbers = LongArray(parts.size)
    for (index in parts.indices) {
        numbers[index] = ipv4Number(parts[index]) ?: return null
    }
    for (index in 0 until numbers.size - 1) {
        if (numbers[index] > 255) return null
    }
    var limit = 1L
    repeat(5 - numbers.size) { limit *= 256 }
    if (numbers.last() >= limit) return null
    var ipv4 = numbers.last()
    for (index in 0 until numbers.size - 1) {
        ipv4 += numbers[index] * powerOf256(3 - index)
    }
    if (ipv4 !in 0..0xFFFF_FFFFL) return null
    return intArrayOf(
        ((ipv4 shr 24) and 0xFF).toInt(),
        ((ipv4 shr 16) and 0xFF).toInt(),
        ((ipv4 shr 8) and 0xFF).toInt(),
        (ipv4 and 0xFF).toInt(),
    )
}

/** One IPv4 piece: decimal, `0x` hex, or a leading-zero octal number. */
private fun ipv4Number(input: String): Long? {
    if (input.isEmpty()) return null
    val radix: Int
    val digits: String
    if (input.length >= 2 && input[0] == '0' && (input[1] == 'x' || input[1] == 'X')) {
        radix = 16
        digits = input.substring(2)
        if (digits.isEmpty()) return null
    } else if (input.length >= 2 && input[0] == '0') {
        radix = 8
        digits = input.substring(1)
        if (digits.isEmpty()) return 0
    } else {
        radix = 10
        digits = input
    }
    var value = 0L
    for (char in digits) {
        val digit = when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> return null
        }
        if (digit >= radix) return null
        if (value > (Long.MAX_VALUE - digit) / radix) return null
        value = value * radix + digit
    }
    return value
}

private fun powerOf256(exponent: Int): Long {
    var value = 1L
    repeat(exponent) { value *= 256 }
    return value
}

private fun isLocalIpv4(octets: IntArray): Boolean {
    val a = octets[0]
    val b = octets[1]
    return a == 127 ||
        a == 10 ||
        (a == 172 && b in 16..31) ||
        (a == 192 && b == 168) ||
        (a == 169 && b == 254) ||
        (a == 100 && b in 64..127)
}

private fun isLocalIpv6(inner: String): Boolean {
    val groups = expandIpv6(inner) ?: return false
    if ((0..6).all { groups[it] == 0 } && groups[7] == 1) return true
    val first = groups[0]
    if (first in 0xFC00..0xFDFF || first in 0xFE80..0xFEBF) return true
    if ((0..4).all { groups[it] == 0 } && groups[5] == 0xFFFF) {
        val octets = intArrayOf(groups[6] ushr 8, groups[6] and 0xFF, groups[7] ushr 8, groups[7] and 0xFF)
        return isLocalIpv4(octets)
    }
    return false
}

/** Eight 16-bit groups, or null when [inner] is not a plain IPv6 address. */
private fun expandIpv6(inner: String): IntArray? {
    val body = inner.lowercase().substringBefore('%')
    val splitAt = body.indexOf("::")
    val leftText: String
    val rightText: String
    val compressed: Boolean
    if (splitAt < 0) {
        leftText = body
        rightText = ""
        compressed = false
    } else {
        if (body.indexOf("::", splitAt + 2) >= 0) return null
        leftText = body.substring(0, splitAt)
        rightText = body.substring(splitAt + 2)
        compressed = true
    }
    val left = if (leftText.isEmpty()) emptyList() else leftText.split(':')
    val right = if (rightText.isEmpty()) emptyList() else rightText.split(':')
    if (left.any { '.' in it } || right.dropLast(1).any { '.' in it }) return null
    val groups = ArrayList<Int>(8)
    for (part in left) groups += ipv6Group(part) ?: return null
    val tail = ArrayList<Int>(right.size)
    if (right.isNotEmpty() && '.' in right.last()) {
        val octets = ipv4FromHost(right.last()) ?: return null
        for (part in right.dropLast(1)) tail += ipv6Group(part) ?: return null
        tail += (octets[0] shl 8) or octets[1]
        tail += (octets[2] shl 8) or octets[3]
    } else {
        for (part in right) tail += ipv6Group(part) ?: return null
    }
    val missing = 8 - groups.size - tail.size
    if (compressed) {
        if (missing < 1) return null
        repeat(missing) { groups += 0 }
    } else if (missing != 0) {
        return null
    }
    groups += tail
    if (groups.size != 8) return null
    return groups.toIntArray()
}

private fun ipv6Group(text: String): Int? {
    if (text.isEmpty() || text.length > 4) return null
    if (text.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return text.toInt(16)
}

/**
 * A real country site whose name is exactly the brand: `google.ca`, `paypal.co.uk`. A free
 * phishing suffix such as `.tk` is not treated as a country. Typos still warn.
 */
private fun isExactBrandOnCountrySuffix(labels: List<String>): Boolean {
    val suffixLen = suffixLength(labels)
    if (labels.size <= suffixLen) return false
    val suffix = labels.takeLast(suffixLen).joinToString(".")
    if (!isTrustedCountrySuffix(suffix)) return false
    val label = labels[labels.size - suffixLen - 1]
    if (label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }) return false
    return PhishingBrands.all.any { brand -> label in brand.keys }
}

/**
 * A ccTLD, or a multi-part suffix that ends in one. A one-label suffix one edit from com, net, or
 * org (`cm`) still warns. That check does not apply to a real multi-part suffix such as `com.co`.
 * Suffixes that are given away for phishing are left out.
 */
private fun isTrustedCountrySuffix(suffix: String): Boolean {
    val country = suffix.substringAfterLast('.')
    if (country.length != 2 || country in ABUSIVE_COUNTRY_SUFFIXES) return false
    if (suffix != country) return suffix in MULTI_PART_SUFFIXES
    if (withinOneEdit(country, "com") || withinOneEdit(country, "net") || withinOneEdit(country, "org")) return false
    return true
}

private val ABUSIVE_COUNTRY_SUFFIXES = setOf("tk", "ml", "ga", "cf", "gq")

private fun isOfficialHost(host: String): Boolean {
    val official = PhishingBrands.officialDomains
    if (host in official) return true
    var rest = host
    while (true) {
        val dot = rest.indexOf('.')
        if (dot < 0) return false
        rest = rest.substring(dot + 1)
        if (rest in official) return true
    }
}

private fun embeddedBrand(labels: List<String>): PhishingBrand? {
    for (brand in PhishingBrands.all) {
        for (domain in brand.domains) {
            val brandLabels = domain.split('.')
            if (brandLabels.size < 2) continue
            val lastStart = labels.size - brandLabels.size
            for (start in 0 until lastStart) {
                if (labels.subList(start, start + brandLabels.size) == brandLabels) return brand
            }
        }
    }
    return null
}

/** A long brand name used as a label in front of someone else's site (`paypal.evil.example`). */
private fun plantedBrand(labels: List<String>): PhishingBrand? {
    val skip = suffixLength(labels) + 1
    if (labels.size <= skip) return null
    val prefix = labels.subList(0, labels.size - skip)
    for (label in prefix) {
        val text = fold(decodeLabel(label)).ascii ?: continue
        val brand = PhishingBrands.all.firstOrNull { candidate ->
            candidate.keys.any { key -> key.length >= PLANTED_KEY && key == text }
        }
        if (brand != null) return brand
    }
    return null
}

/**
 * [Pair.second] is true when a non-ASCII confusable was used, so the warning can say the letters
 * themselves are the trick rather than a typo.
 */
private fun matchBrand(label: String): Pair<PhishingBrand, Boolean>? {
    val folded = fold(label)
    val ascii = folded.ascii ?: return null
    if (ascii.length < 3) return null
    val forms = candidateForms(ascii)
    var fuzzy: PhishingBrand? = null
    for (brand in PhishingBrands.all) {
        for (key in brand.keys) {
            if (key.length < 3) continue
            if (forms.any { it == key }) return brand to folded.confusable
            if (key.length >= FUZZY_KEY && ascii.length == key.length && fuzzy == null && withinOneEdit(ascii, key)) {
                fuzzy = brand
            }
        }
    }
    return fuzzy?.let { it to folded.confusable }
}

/** The raw ASCII form, the leet-folded form, and the form with a trailing digit run removed. */
private fun candidateForms(ascii: String): List<String> {
    val leet = leetFold(ascii)
    val stripped = ascii.dropLastWhile { it.isDigit() }
    val strippedLeet = if (stripped == ascii) stripped else leetFold(stripped)
    return listOf(ascii, leet, stripped, strippedLeet).distinct()
}

private data class Fold(val ascii: String?, val confusable: Boolean)

private fun fold(label: String): Fold {
    if (label.isEmpty()) return Fold(null, confusable = false)
    val out = StringBuilder(label.length)
    var confusable = false
    var index = 0
    while (index < label.length) {
        val code = label.codePointAt(index)
        index += Character.charCount(code)
        val mapped = foldCodePoint(code)
        when {
            mapped == null && Character.isLetter(code) -> return Fold(null, confusable = false)
            mapped == null -> Unit
            else -> {
                if (code > 0x7F) confusable = true
                out.append(mapped)
            }
        }
    }
    if (out.isEmpty()) return Fold(null, confusable = false)
    return Fold(out.toString(), confusable)
}

private fun leetFold(ascii: String): String = buildString(ascii.length) {
    ascii.forEach { char -> append(LEET[char] ?: char) }
}

/** ASCII letters and digits pass through. A confusable code point becomes its ASCII letter. */
private fun foldCodePoint(code: Int): String? {
    val lower = Character.toLowerCase(code)
    if (lower in 'a'.code..'z'.code || lower in '0'.code..'9'.code) return lower.toChar().toString()
    FULLWIDTH[lower]?.let { return it }
    return CONFUSABLE[lower]
}

private fun decodeLabel(label: String): String {
    if (!label.startsWith("xn--")) return label
    return try {
        IDN.toUnicode(label)
    } catch (_: IllegalArgumentException) {
        label
    }
}

private fun mixesScripts(label: String): Boolean {
    var seen: Character.UnicodeScript? = null
    var index = 0
    while (index < label.length) {
        val code = label.codePointAt(index)
        index += Character.charCount(code)
        if (!Character.isLetter(code)) continue
        val script = Character.UnicodeScript.of(code)
        if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) continue
        val first = seen
        if (first == null) {
            seen = script
        } else if (first != script) {
            return true
        }
    }
    return false
}

/** How many labels the public suffix uses. Known multi-part suffixes use two. */
internal fun suffixLength(labels: List<String>): Int {
    if (labels.size >= 2) {
        val tail = labels[labels.size - 2] + "." + labels[labels.size - 1]
        if (tail in MULTI_PART_SUFFIXES) return 2
    }
    return 1
}

internal fun registrableLabel(labels: List<String>): String? {
    val index = labels.size - suffixLength(labels) - 1
    if (index < 0) return null
    return labels[index]
}

private fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    if (abs(a.length - b.length) > 1) return false
    if (a.length == b.length) {
        var diffs = 0
        var index = 0
        while (index < a.length) {
            if (a[index] != b[index]) {
                if (diffs == 0 && index + 1 < a.length && a[index] == b[index + 1] && a[index + 1] == b[index]) {
                    return a.substring(index + 2) == b.substring(index + 2)
                }
                diffs++
                if (diffs > 1) return false
            }
            index++
        }
        return diffs == 1
    }
    val shorter: String
    val longer: String
    if (a.length < b.length) {
        shorter = a
        longer = b
    } else {
        shorter = b
        longer = a
    }
    var i = 0
    var j = 0
    var skipped = false
    while (i < shorter.length && j < longer.length) {
        if (shorter[i] == longer[j]) {
            i++
            j++
        } else if (skipped) {
            return false
        } else {
            skipped = true
            j++
        }
    }
    val leftover = longer.length - j
    return if (skipped) leftover == 0 else leftover <= 1
}

/** A brand name planted as its own label. Shorter names match only as the registrable label. */
private const val PLANTED_KEY = 6

/** Edit distance applies from this length. Shorter names match only when the folded form is exact. */
private const val FUZZY_KEY = 5

private val MULTI_PART_SUFFIXES = setOf(
    "co.uk", "org.uk", "ac.uk",
    "com.vn", "net.vn", "org.vn", "gov.vn", "edu.vn",
    "com.au", "net.au", "org.au",
    "co.jp", "ne.jp", "or.jp",
    "com.br", "com.sg", "com.hk", "com.tw", "com.tr",
    "co.kr", "co.id", "com.my", "com.ph", "co.th", "com.cn",
    "co.nz", "co.za", "com.ar", "com.mx", "co.in",
    "com.co", "net.co", "nom.co", "org.co",
    "com.om", "net.om", "org.om", "co.om", "edu.om", "gov.om", "med.om",
    "com.cm", "co.cm", "net.cm",
)

private val LEET = mapOf(
    '0' to 'o',
    '1' to 'l',
    '3' to 'e',
    '4' to 'a',
    '5' to 's',
    '7' to 't',
)

/** Fullwidth Latin letters and digits (U+FF10..FF19, U+FF21..FF3A, U+FF41..FF5A), already lowercased. */
private val FULLWIDTH: Map<Int, String> = buildMap {
    for (offset in 0 until 26) {
        put(0xFF21 + offset, ('a' + offset).toString())
        put(0xFF41 + offset, ('a' + offset).toString())
    }
    for (offset in 0 until 10) put(0xFF10 + offset, ('0' + offset).toString())
}

/**
 * Letters that render like ASCII and show up in homograph domains. Latin accents are not here:
 * a Vietnamese name should not fold into a brand.
 */
private val CONFUSABLE: Map<Int, String> = mapOf(
    0x0430 to "a", // а
    0x0435 to "e", // е
    0x043E to "o", // о
    0x0440 to "p", // р
    0x0441 to "c", // с
    0x0443 to "y", // у
    0x0445 to "x", // х
    0x0455 to "s", // ѕ
    0x0456 to "i", // і
    0x0458 to "j", // ј
    0x04BB to "h", // һ
    0x0501 to "d", // ԁ
    0x051B to "q", // ԛ
    0x0261 to "g", // ɡ
    0x03B1 to "a", // α
    0x03BF to "o", // ο
    0x03C1 to "p", // ρ
    0x03B9 to "i", // ι
    0x03BD to "v", // ν
    0x03BA to "k", // κ
    0x03C4 to "t", // τ
    0x03B5 to "e", // ε
)
