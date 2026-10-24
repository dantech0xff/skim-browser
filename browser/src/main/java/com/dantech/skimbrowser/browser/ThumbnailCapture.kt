package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.annotation.MainThread
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import kotlin.math.min
import kotlin.math.roundToInt

/** How much a page shrinks in its tab card. */
internal const val THUMBNAIL_SCALE = 0.35f

/** The tab card's preview frame is 168 × 92 dp; the thumbnail keeps the top of the page at that shape. */
private const val PREVIEW_ASPECT = 168f / 92f

private const val TAG = "SkimThumbnail"

/**
 * The thumbnail size, in pixels, for a view of [width] × [height] pixels: the view scaled by
 * [THUMBNAIL_SCALE], cut at the preview frame's aspect ratio from the top (or the whole view when
 * it is shorter). Null for a view that has no size yet.
 */
fun thumbnailSize(width: Int, height: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val thumbnailWidth = (width * THUMBNAIL_SCALE).roundToInt().coerceAtLeast(1)
    val thumbnailHeight = min((thumbnailWidth / PREVIEW_ASPECT).roundToInt(), (height * THUMBNAIL_SCALE).roundToInt())
        .coerceAtLeast(1)
    return thumbnailWidth to thumbnailHeight
}

/**
 * What [view] shows, from its top, drawn into a small software bitmap: only the view's own
 * content, never the toolbars or sheets drawn above it. Null while the view is not in a window or
 * has not been laid out, or when drawing fails.
 */
@MainThread
fun captureThumbnail(view: View): Bitmap? {
    if (!view.isAttachedToWindow || !view.isLaidOut) return null
    val (width, height) = thumbnailSize(view.width, view.height) ?: return null
    return try {
        createBitmap(width, height).applyCanvas {
            scale(THUMBNAIL_SCALE, THUMBNAIL_SCALE)
            // A view draws its content at its scroll offset; this keeps the part on screen.
            translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.draw(this)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Thumbnail not drawn", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "No memory for a thumbnail", e)
        null
    }
}
