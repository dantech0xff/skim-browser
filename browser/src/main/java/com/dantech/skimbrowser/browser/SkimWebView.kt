package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.HitTarget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The WebView of a tab.
 *
 * - Pull to refresh: `WebView` takes part in no nested scrolling, so a pull at the top of the page
 *   is handed to the nested scrolling parent (Compose's pull to refresh, through `AndroidView`).
 *   A pull starts only when the page is at its top and the WebView itself reported that it could
 *   not scroll further up (`onOverScrolled` with `clampedY`), so a page that handles the drag
 *   itself (a map) keeps it. The page then sees its touch cancelled, and when the finger lifts
 *   the parent always gets the pre-fling it decides the refresh in.
 * - Long-press on a link or an image: reported to [onHitTarget]; any other long-press is left to
 *   the WebView (text selection).
 * - The last tap on the page, for an action a page may only take right after one ([consumeGesture]).
 *   Only a tap counts (the finger lifted where it went down), not the start of a scroll or a pull,
 *   and one tap allows one action.
 * - In an incognito tab ([incognito]) the keyboard is asked not to learn from what is typed, and
 *   the autofill service is not offered the page's fields, so neither keeps anything from the tab.
 */
class SkimWebView @JvmOverloads constructor(
    context: Context,
    private val incognito: Boolean = false,
) : WebView(context), NestedScrollingChild3 {

    /** Null only while `WebView`'s constructor runs, which may already call the overrides below. */
    private val nested: NestedScrollingChildHelper? = NestedScrollingChildHelper(this)

    /** Gets each link or image the user long-presses. */
    var onHitTarget: ((HitTarget) -> Unit)? = null

    /** `SystemClock.elapsedRealtime()` of the last tap on the page not consumed yet, or [NO_GESTURE]. */
    private var lastTapAt: Long = NO_GESTURE
    private var downX = 0f
    private var downY = 0f

    /** True while the touch going on can still end as a tap. */
    private var tapping = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var lastY = 0
    private var pulling = false
    private var overscrolledAtTop = false
    private var velocity: VelocityTracker? = null
    private val scrollConsumed = IntArray(2)

    private val focusNodeHandler by lazy {
        Handler(Looper.getMainLooper()) { message ->
            val hit = message.obj as? LongPressHit.AwaitingFocusNode
            if (hit != null) {
                val data = message.data
                onHitTarget?.invoke(hit.complete(url = data.getString(FOCUS_NODE_URL), title = data.getString(FOCUS_NODE_TITLE)))
            }
            true
        }
    }

    /** Document padding, in CSS pixels, so the page starts clear of the status bar and the bottom pill. */
    internal var contentPadTop: Int = 0
        private set

    internal var contentPadBottom: Int = 0
        private set

    init {
        isNestedScrollingEnabled = true
        setOnLongClickListener { onLongPress() }
        if (incognito) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    /**
     * Pads the document, not the view. The padding is the start and end of the page, so it scrolls
     * away and the page can draw under the status bar and the bottom pill. A later page load applies
     * it again ([applyContentScrollPadding]).
     */
    fun setContentScrollPadding(topPx: Int, bottomPx: Int) {
        val top = topPx.coerceIn(0, MAX_CONTENT_PAD)
        val bottom = bottomPx.coerceIn(0, MAX_CONTENT_PAD)
        if (top == contentPadTop && bottom == contentPadBottom) return
        contentPadTop = top
        contentPadBottom = bottom
        if (url != null) applyContentScrollPadding()
    }

    /** Writes the current padding into the page that is showing. No-op until one is loaded. */
    fun applyContentScrollPadding() {
        if (url == null) return
        evaluateJavascript(contentScrollPaddingScript(contentPadTop, contentPadBottom), null)
    }

    /**
     * True, once, when the user tapped the page in the last [withinMs] milliseconds: the tap is used
     * up, so it allows one action and no more.
     */
    fun consumeGesture(withinMs: Long): Boolean {
        val at = lastTapAt
        if (at == NO_GESTURE || SystemClock.elapsedRealtime() - at !in 0..withinMs) return false
        lastTapAt = NO_GESTURE
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (incognito) outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return connection
    }

    // The page's own clicks reach it through super.onTouchEvent, so there is no click to perform here.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapping = true
                downX = event.x
                downY = event.y
                lastY = event.y.roundToInt()
                pulling = false
                overscrolledAtTop = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tapping && movedBeyondSlop(event)) tapping = false
                val y = event.y.roundToInt()
                val dy = lastY - y
                lastY = y
                velocity?.addMovement(event)
                if (!pulling && dy < 0 && event.pointerCount == 1 && overscrolledAtTop && !canScrollVertically(-1)) {
                    pulling = true
                    // The rest of the gesture pulls to refresh: the page sees it end here.
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (pulling) {
                    scrollConsumed.fill(0)
                    dispatchNestedPreScroll(0, dy, scrollConsumed, null, ViewCompat.TYPE_TOUCH)
                    dispatchNestedScroll(0, 0, 0, dy - scrollConsumed[1], null, ViewCompat.TYPE_TOUCH, scrollConsumed)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pulled = pulling
                pulling = false
                // Recorded before the page sees the touch end, which is when its click runs.
                if (event.actionMasked == MotionEvent.ACTION_UP && tapping && !pulled && !movedBeyondSlop(event)) {
                    lastTapAt = SystemClock.elapsedRealtime()
                }
                tapping = false
                if (pulled) {
                    val tracker = velocity
                    tracker?.computeCurrentVelocity(VELOCITY_UNITS)
                    // The refresh is decided in the parent's pre-fling, so it is sent however slow the release.
                    dispatchNestedPreFling(0f, -(tracker?.yVelocity ?: 0f))
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                velocity?.recycle()
                velocity = null
                if (pulled) return true
            }
            else -> {
                // A second finger makes it a pinch, not a tap.
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) tapping = false
                // A second finger during a pull is part of the pull: the page already saw its gesture end.
                if (pulling) return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun movedBeyondSlop(event: MotionEvent): Boolean =
        abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && scrollY <= 0) overscrolledAtTop = true
    }

    override fun onDetachedFromWindow() {
        nested?.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }

    private fun onLongPress(): Boolean {
        if (onHitTarget == null) return false
        val result = hitTestResult
        return when (val hit = longPressHitOf(result.type, result.extra)) {
            null -> false
            is LongPressHit.Ready -> {
                onHitTarget?.invoke(hit.target)
                true
            }
            is LongPressHit.AwaitingFocusNode -> {
                // The WebView fills in the link's address and text, then delivers the message.
                requestFocusNodeHref(Message.obtain(focusNodeHandler, 0, hit))
                true
            }
        }
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        val helper = nested
        if (helper == null) super.setNestedScrollingEnabled(enabled) else helper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = nested?.isNestedScrollingEnabled ?: super.isNestedScrollingEnabled()

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun startNestedScroll(axes: Int, type: Int): Boolean = nested?.startNestedScroll(axes, type) ?: false

    override fun stopNestedScroll() = stopNestedScroll(ViewCompat.TYPE_TOUCH)

    override fun stopNestedScroll(type: Int) {
        nested?.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun hasNestedScrollingParent(type: Int): Boolean = nested?.hasNestedScrollingParent(type) ?: false

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = nested?.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow) ?: false

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = nested?.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type) ?: false

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        nested?.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        nested?.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow) ?: false

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean =
        nested?.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type) ?: false

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        nested?.dispatchNestedFling(velocityX, velocityY, consumed) ?: false

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        nested?.dispatchNestedPreFling(velocityX, velocityY) ?: false

    companion object {
        /** No tap to consume. */
        const val NO_GESTURE = Long.MIN_VALUE

        private const val VELOCITY_UNITS = 1_000
        private const val FOCUS_NODE_URL = "url"
        private const val FOCUS_NODE_TITLE = "title"
        private const val MAX_CONTENT_PAD = 512

        /** A style tag on `html`. The padding scrolls with the page; it is not a fixed inset. */
        internal fun contentScrollPaddingScript(topPx: Int, bottomPx: Int): String =
            "(function(){var id='skim-scroll-pad';var node=document.getElementById(id);" +
                "if(!node){node=document.createElement('style');node.id=id;" +
                "(document.head||document.documentElement).appendChild(node);}" +
                "node.textContent='html{padding-top:${topPx}px !important;padding-bottom:${bottomPx}px !important;}';})();"
    }
}

/** What a long-press hit, from the WebView's hit test. */
sealed interface LongPressHit {
    /** An image outside any link: complete as it is. */
    data class Ready(val target: HitTarget) : LongPressHit

    /**
     * A link ([WebView.HitTestResult.SRC_ANCHOR_TYPE], [extra] its address) or an image inside a
     * link ([WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE], [extra] the image's address). The link's
     * text, or the address the image links to, comes from `requestFocusNodeHref`.
     */
    data class AwaitingFocusNode(val type: Int, val extra: String) : LongPressHit {
        /** The target, given the focus node's link address [url] and link text [title]. */
        fun complete(url: String?, title: String?): HitTarget = when (type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> HitTarget.Link(url = extra, text = title?.trim()?.ifEmpty { null })
            else -> HitTarget.Image(src = extra, alt = null, linkUrl = url?.trim()?.ifEmpty { null })
        }
    }
}

/**
 * The long-press hit for a hit test of [type] with [extra]: a link, an image, or an image inside a
 * link. Null for anything else (text, a phone number, an address, an editable field), which the
 * WebView handles itself.
 */
fun longPressHitOf(type: Int, extra: String?): LongPressHit? {
    val value = extra?.takeIf { it.isNotBlank() } ?: return null
    return when (type) {
        WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
            LongPressHit.AwaitingFocusNode(type, value)
        WebView.HitTestResult.IMAGE_TYPE -> LongPressHit.Ready(HitTarget.Image(src = value, alt = null, linkUrl = null))
        else -> null
    }
}
