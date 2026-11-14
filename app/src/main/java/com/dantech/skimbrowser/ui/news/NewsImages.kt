package com.dantech.skimbrowser.ui.news

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

internal fun loadNewsBitmapIfAllowed(allow: Boolean, url: String): Bitmap? {
    if (!allow) return null
    return loadNewsBitmap(url)
}

/** Decodes a story image down to about [TARGET_PX] on its long side. Null when the bytes are unusable. */
internal fun loadNewsBitmap(url: String): Bitmap? {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("Accept", "image/*")
    }
    try {
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { input ->
            val buffer = ByteArray(MAX_BYTES)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            if (input.read() >= 0) return null
            buffer.copyOf(total)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = newsSampleSize(bounds.outWidth, bounds.outHeight, TARGET_PX)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } finally {
        connection.disconnect()
    }
}

internal fun newsSampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    var longSide = maxOf(width, height)
    while (longSide / (sample * 2) >= target) sample *= 2
    return sample
}

private const val TARGET_PX = 256
private const val MAX_BYTES = 1_000_000
