package com.dantech.skimbrowser.domain.shield

object ShieldEstimate {
    const val SCRIPT_BYTES = 16L * 1024
    const val IMAGE_BYTES = 32L * 1024
    const val XHR_BYTES = 1024L
    const val MEDIA_BYTES = 128L * 1024
    const val OTHER_BYTES = 4L * 1024

    const val ONE_MEGABIT_BYTES_PER_SECOND = 125_000L

    const val ROUND_TRIP_MILLIS = 50L

    fun bytes(resource: BlockedResource): Long = when (resource) {
        BlockedResource.SCRIPT -> SCRIPT_BYTES
        BlockedResource.IMAGE -> IMAGE_BYTES
        BlockedResource.XHR -> XHR_BYTES
        BlockedResource.MEDIA -> MEDIA_BYTES
        BlockedResource.OTHER -> OTHER_BYTES
    }

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

    fun timeSavedMillis(bytes: Long, requests: Long): Long {
        val savedBytes = bytes.coerceAtLeast(0)
        val savedRequests = requests.coerceAtLeast(0)
        val wholeSeconds = savedBytes / ONE_MEGABIT_BYTES_PER_SECOND
        val remainder = savedBytes % ONE_MEGABIT_BYTES_PER_SECOND
        val fromBandwidth = saturatingAdd(saturatingTimes(wholeSeconds, 1_000L), remainder * 1_000L / ONE_MEGABIT_BYTES_PER_SECOND)
        val fromLatency = saturatingTimes(savedRequests, ROUND_TRIP_MILLIS)
        return saturatingAdd(fromBandwidth, fromLatency)
    }
}

enum class BlockedResource { SCRIPT, IMAGE, XHR, MEDIA, OTHER }

enum class ByteUnit { BYTE, KILOBYTE, MEGABYTE, GIGABYTE }

data class ScaledBytes(val unit: ByteUnit, val whole: Long, val tenths: Int)

enum class DurationUnit { NONE, SECOND, MINUTE, HOUR }

data class ScaledDuration(val unit: DurationUnit, val amount: Long)

private const val KIB = 1024L
private const val MIB = KIB * 1024
private const val GIB = MIB * 1024

fun scaleBytes(bytes: Long): ScaledBytes {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe < KIB -> ScaledBytes(ByteUnit.BYTE, safe, 0)
        safe < MIB -> ScaledBytes(ByteUnit.KILOBYTE, safe / KIB, 0)
        safe < GIB -> ScaledBytes(ByteUnit.MEGABYTE, safe / MIB, ((safe % MIB) * 10 / MIB).toInt())
        else -> ScaledBytes(ByteUnit.GIGABYTE, safe / GIB, ((safe % GIB) * 10 / GIB).toInt())
    }
}

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
