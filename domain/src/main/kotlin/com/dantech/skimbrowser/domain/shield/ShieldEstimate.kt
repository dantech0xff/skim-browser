package com.dantech.skimbrowser.domain.shield

/**
 * Estimates for a request Skim never downloads.
 *
 * The WebView request does not report a resource type, and a blocked
 * request has no response, so Content-Length is not known. The type is the path suffix. A suffix
 * this table does not name counts as [BlockedResource.OTHER]. Fetch and XHR calls usually have no
 * suffix, so they count as other rather than as a beacon.
 *
 * Byte averages are conservative stand-ins for one blocked file, not a measurement of that file:
 * a script is 16 KiB, an image is 32 KiB, an XHR or JSON body is 1 KiB, a media file is 128 KiB,
 * and anything else is 4 KiB.
 *
 * Time saved is
 * `bytes / 125_000 seconds + requests * 50 milliseconds`.
 * 125_000 bytes per second is 1 Mbps, a slow mobile link, so the bandwidth term stays small.
 * 50 milliseconds is one round trip per blocked request. Popups, redirects, and cosmetic rules
 * add neither bytes nor round trips. Both terms use integer arithmetic and saturate at
 * [Long.MAX_VALUE].
 */
object ShieldEstimate {
    const val SCRIPT_BYTES = 16L * 1024
    const val IMAGE_BYTES = 32L * 1024
    const val XHR_BYTES = 1024L
    const val MEDIA_BYTES = 128L * 1024
    const val OTHER_BYTES = 4L * 1024

    /** 1 Mbps, in bytes per second. */
    const val BANDWIDTH_BYTES_PER_SECOND = 125_000L

    /** One saved round trip, in milliseconds. */
    const val ROUND_TRIP_MILLIS = 50L

    fun bytes(resource: BlockedResource): Long = when (resource) {
        BlockedResource.SCRIPT -> SCRIPT_BYTES
        BlockedResource.IMAGE -> IMAGE_BYTES
        BlockedResource.XHR -> XHR_BYTES
        BlockedResource.MEDIA -> MEDIA_BYTES
        BlockedResource.OTHER -> OTHER_BYTES
    }

    /** The resource class of [url], from the last path segment's suffix. */
    fun resourceOf(url: String): BlockedResource {
        val path = url.substringBefore('#').substringBefore('?')
        val slash = path.lastIndexOf('/')
        val segment = if (slash >= 0) path.substring(slash + 1) else path
        val dot = segment.lastIndexOf('.')
        if (dot < 0 || dot == segment.lastIndex) return BlockedResource.OTHER
        return when (segment.substring(dot + 1).lowercase()) {
            "js", "mjs", "cjs" -> BlockedResource.SCRIPT
            "png", "jpg", "jpeg", "gif", "webp", "svg", "avif", "ico", "bmp" -> BlockedResource.IMAGE
            "json", "xml" -> BlockedResource.XHR
            "mp4", "webm", "m4v", "mp3", "m4a", "ogg", "wav", "aac", "m3u8" -> BlockedResource.MEDIA
            else -> BlockedResource.OTHER
        }
    }

    /**
     * Milliseconds not spent downloading [bytes] at [BANDWIDTH_BYTES_PER_SECOND], plus
     * [ROUND_TRIP_MILLIS] for each of [requests]. Negative inputs count as zero.
     */
    fun timeSavedMillis(bytes: Long, requests: Long): Long {
        val savedBytes = bytes.coerceAtLeast(0)
        val savedRequests = requests.coerceAtLeast(0)
        val wholeSeconds = savedBytes / BANDWIDTH_BYTES_PER_SECOND
        val remainder = savedBytes % BANDWIDTH_BYTES_PER_SECOND
        val fromBandwidth = saturatingAdd(saturatingTimes(wholeSeconds, 1_000L), remainder * 1_000L / BANDWIDTH_BYTES_PER_SECOND)
        val fromLatency = saturatingTimes(savedRequests, ROUND_TRIP_MILLIS)
        return saturatingAdd(fromBandwidth, fromLatency)
    }
}

enum class BlockedResource { SCRIPT, IMAGE, XHR, MEDIA, OTHER }

enum class ByteUnit { BYTE, KILOBYTE, MEGABYTE, GIGABYTE }

/** A byte count split so the UI can pick one unit string. [tenths] is used for megabytes and gigabytes. */
data class ScaledBytes(val unit: ByteUnit, val whole: Long, val tenths: Int)

enum class DurationUnit { NONE, SECOND, MINUTE, HOUR }

/** A duration split so the UI can pick one unit string. [NONE] is under one second, including zero. */
data class ScaledDuration(val unit: DurationUnit, val amount: Long)

private const val KIB = 1024L
private const val MIB = KIB * 1024
private const val GIB = MIB * 1024

/** Groups [bytes] into one unit. Negative counts as zero. */
fun scaleBytes(bytes: Long): ScaledBytes {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe < KIB -> ScaledBytes(ByteUnit.BYTE, safe, 0)
        safe < MIB -> ScaledBytes(ByteUnit.KILOBYTE, safe / KIB, 0)
        safe < GIB -> ScaledBytes(ByteUnit.MEGABYTE, safe / MIB, ((safe % MIB) * 10 / MIB).toInt())
        else -> ScaledBytes(ByteUnit.GIGABYTE, safe / GIB, ((safe % GIB) * 10 / GIB).toInt())
    }
}

/** Groups [millis] into one unit. Under one second is [DurationUnit.NONE]. */
fun scaleDuration(millis: Long): ScaledDuration {
    val safe = millis.coerceAtLeast(0)
    if (safe < 1_000L) return ScaledDuration(DurationUnit.NONE, 0)
    val seconds = safe / 1_000L
    if (seconds < 60L) return ScaledDuration(DurationUnit.SECOND, seconds)
    val minutes = seconds / 60L
    if (minutes < 60L) return ScaledDuration(DurationUnit.MINUTE, minutes)
    return ScaledDuration(DurationUnit.HOUR, minutes / 60L)
}

internal fun saturatingAdd(left: Long, right: Long): Long {
    if (right <= 0L) return left.coerceAtLeast(0L)
    val base = left.coerceAtLeast(0L)
    if (base > Long.MAX_VALUE - right) return Long.MAX_VALUE
    return base + right
}

internal fun saturatingTimes(value: Long, factor: Long): Long {
    if (value <= 0L || factor <= 0L) return 0L
    if (value > Long.MAX_VALUE / factor) return Long.MAX_VALUE
    return value * factor
}
