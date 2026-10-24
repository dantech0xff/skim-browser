package com.dantech.skimbrowser.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs

/** Per channel, on a 0..1 scale: 4 of 255, for rounding in the raster. */
private const val TOLERANCE = 4f / 255f

/**
 * Whether some pixel of this capture has [color]. Glyphs and shapes have pixels of their full color
 * inside their antialiased edges, so a label or a mark drawn in [color] has some.
 */
internal fun ImageBitmap.hasPixel(color: Color): Boolean {
    val pixels = toPixelMap()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - color.red) < TOLERANCE &&
                abs(pixel.green - color.green) < TOLERANCE &&
                abs(pixel.blue - color.blue) < TOLERANCE
            ) {
                return true
            }
        }
    }
    return false
}
