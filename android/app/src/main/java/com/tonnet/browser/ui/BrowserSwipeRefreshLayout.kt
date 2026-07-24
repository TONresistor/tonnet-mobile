package com.tonnet.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonnet.browser.R
import kotlin.math.abs
import kotlin.math.max

class BrowserSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {
    var canGoBack: () -> Boolean = { false }
    var canGoForward: () -> Boolean = { false }
    var onGoBack: () -> Unit = {}
    var onGoForward: () -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tonnet_surface_bright)
    }
    private val backIcon = ContextCompat.getDrawable(context, R.drawable.telegram_icon_back)
    private val forwardIcon = ContextCompat.getDrawable(context, R.drawable.telegram_icon_forward)

    private var downX = 0f
    private var downY = 0f
    private var deltaX = 0f
    private var horizontalGesture = false
    private var startedInSystemGestureInset = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                deltaX = 0f
                horizontalGesture = false
                startedInSystemGestureInset = isInsideSystemGestureInset(event.x)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                horizontalGesture = false
                return super.onInterceptTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!startedInSystemGestureInset && event.pointerCount == 1) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val directionAvailable = if (dx > 0f) canGoBack() else canGoForward()
                    if (
                        directionAvailable &&
                        abs(dx) > touchSlop &&
                        abs(dx) > abs(dy) * HORIZONTAL_DOMINANCE
                    ) {
                        horizontalGesture = true
                        deltaX = dx
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> resetHorizontalGesture()
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!horizontalGesture) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                deltaX = event.x - downX
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                when (
                    BrowserSwipePolicy.action(
                        deltaX = deltaX,
                        deltaY = event.y - downY,
                        threshold = swipeThreshold(),
                        canGoBack = canGoBack(),
                        canGoForward = canGoForward(),
                    )
                ) {
                    BrowserSwipeAction.BACK -> onGoBack()
                    BrowserSwipeAction.FORWARD -> onGoForward()
                    BrowserSwipeAction.NONE -> Unit
                }
                resetHorizontalGesture()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            -> resetHorizontalGesture()
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!horizontalGesture || deltaX == 0f) return
        val directionAvailable = if (deltaX > 0f) canGoBack() else canGoForward()
        if (!directionAvailable) return

        val progress = (abs(deltaX) / swipeThreshold()).coerceIn(0f, 1f)
        val radius = dp(20f)
        val centerX = if (deltaX > 0f) dp(28f) else width - dp(28f)
        val centerY = height / 2f
        feedbackPaint.alpha = (96 + 96 * progress).toInt()
        canvas.drawCircle(centerX, centerY, radius, feedbackPaint)
        drawFeedbackIcon(
            canvas = canvas,
            icon = if (deltaX > 0f) backIcon else forwardIcon,
            centerX = centerX,
            centerY = centerY,
            alpha = (160 + 95 * progress).toInt(),
        )
    }

    private fun drawFeedbackIcon(
        canvas: Canvas,
        icon: Drawable?,
        centerX: Float,
        centerY: Float,
        alpha: Int,
    ) {
        if (icon == null) return
        val half = dp(12f).toInt()
        icon.alpha = alpha
        icon.setBounds(
            centerX.toInt() - half,
            centerY.toInt() - half,
            centerX.toInt() + half,
            centerY.toInt() + half,
        )
        icon.draw(canvas)
    }

    private fun swipeThreshold(): Float = max(dp(MIN_SWIPE_DP), width * WIDTH_THRESHOLD_RATIO)

    private fun isInsideSystemGestureInset(x: Float): Boolean {
        val insets = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures())
            ?: return false
        return x <= insets.left || x >= width - insets.right
    }

    private fun resetHorizontalGesture() {
        horizontalGesture = false
        deltaX = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val MIN_SWIPE_DP = 64f
        const val WIDTH_THRESHOLD_RATIO = 0.18f
        const val HORIZONTAL_DOMINANCE = 1.25f
    }
}
