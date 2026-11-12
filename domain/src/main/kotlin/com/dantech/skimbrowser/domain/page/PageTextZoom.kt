package com.dantech.skimbrowser.domain.page

import kotlin.math.roundToInt

/**
 * WebView text zoom for a system font scale. 1 is 100 percent. The result stays inside
 * [MIN]..[MAX] so a broken or extreme scale cannot shrink the page away or blow it up.
 * Reader view has its own size and does not use this.
 */
object PageTextZoom {
    const val MIN = 50
    const val MAX = 200
    const val NORMAL = 100

    fun ofFontScale(scale: Float): Int {
        if (!scale.isFinite() || scale <= 0f) return NORMAL
        return (scale * NORMAL).roundToInt().coerceIn(MIN, MAX)
    }
}
