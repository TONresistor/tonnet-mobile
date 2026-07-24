package com.tonnet.browser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.tonnet.browser.R

class TelegramSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateStateDescription()
            refreshDrawableState()
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        updateStateDescription()
    }

    fun setCheckedSilently(value: Boolean) {
        isChecked = value
    }

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!isEnabled) return false
        isChecked = !isChecked
        checkedChangeListener?.invoke(isChecked)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val visualWidth = 31f * density
        val visualHeight = 17f * density
        val trackHeight = 13f * density
        val thumbRadius = visualHeight / 2f
        val left = (width - visualWidth) / 2f
        val top = (height - visualHeight) / 2f
        val centerY = top + visualHeight / 2f

        paint.color = ContextCompat.getColor(
            context,
            if (isChecked) R.color.tonnet_accent else R.color.tonnet_text_secondary,
        )
        paint.alpha = if (isEnabled) 255 else 97
        track.set(
            left,
            centerY - trackHeight / 2f,
            left + visualWidth,
            centerY + trackHeight / 2f,
        )
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, paint)

        paint.color = ContextCompat.getColor(context, R.color.tonnet_text)
        val thumbX = if (isChecked) left + visualWidth - thumbRadius else left + thumbRadius
        canvas.drawCircle(thumbX, centerY, thumbRadius, paint)
    }

    @Suppress("DEPRECATION")
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = isChecked
    }

    private fun updateStateDescription() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stateDescription = context.getString(if (isChecked) R.string.state_on else R.string.state_off)
        }
    }
}
