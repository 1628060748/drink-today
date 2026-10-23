package com.drinktoday.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.drinktoday.util.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 轻量滚轮选择器：中间高亮带 + 缩放淡出，惯性滑动后自动吸附。
 * 仅 5 个可见条目，单帧绘制 5 段文本，无子 View 开销。
 */
class WheelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val itemHeight = dp(38f)
    private val visibleItems = 5
    private var items: Array<String> = emptyArray()
    private var scrollPos = 0f
    private var selected = 0
    private var flingActive = false
    private var settleAnimator: ValueAnimator? = null

    private val scroller = OverScroller(context)
    private var dragging = false
    private var lastTouchY = 0f
    private var velocityTracker: android.view.VelocityTracker? = null

    var onSelectionChanged: ((Int) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(19f)
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F7FC.toInt() }
    private val bandRect = RectF()
    private val boldTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val normalTypeface = Typeface.create("sans-serif", Typeface.NORMAL)

    init {
        isClickable = true
    }

    fun setItems(values: List<String>, selectedIndex: Int = 0) {
        items = values.toTypedArray()
        selected = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        scrollPos = selected.toFloat()
        invalidate()
    }

    fun setSelected(index: Int, notify: Boolean = false) {
        val target = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        scrollPos = target.toFloat()
        if (selected != target) {
            selected = target
            if (notify) onSelectionChanged?.invoke(target)
        }
        invalidate()
    }

    fun selectedIndex(): Int = selected

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((dp(120f)).toInt(), widthMeasureSpec)
        val height = (itemHeight * visibleItems + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val centerY = height / 2f
        val radius = dp(14f)
        bandRect.set(
            paddingLeft + dp(6f),
            centerY - itemHeight / 2f,
            width - paddingRight - dp(6f),
            centerY + itemHeight / 2f
        )
        canvas.drawRoundRect(bandRect, radius, radius, bandPaint)

        if (items.isEmpty()) return
        val base = scrollPos.roundToInt()
        val from = (base - 2).coerceAtLeast(0)
        val to = (base + 2).coerceAtMost(items.size - 1)
        val metrics = textPaint.fontMetrics
        for (i in from..to) {
            val offset = i - scrollPos
            val distance = abs(offset)
            if (distance > 2.1f) continue
            val y = centerY + offset * itemHeight
            val alpha = (1f - distance / 2.6f).coerceIn(0.16f, 1f)
            val scale = 1f - 0.09f * distance
            textPaint.color = blendColor(
                Palette.ACCENT,
                Palette.TEXT_3,
                (distance / 1.6f).coerceIn(0f, 1f)
            ).let { applyAlpha(it, alpha) }
            textPaint.typeface = if (distance < 0.55f) boldTypeface else normalTypeface
            canvas.save()
            canvas.scale(scale, scale, width / 2f, y)
            canvas.drawText(
                items[i],
                width / 2f,
                y - (metrics.ascent + metrics.descent) / 2f,
                textPaint
            )
            canvas.restore()
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun blendColor(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    // ------------------------------------------------------------ 手势

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scroller.forceFinished(true)
                flingActive = false
                settleAnimator?.cancel()
                velocityTracker = android.view.VelocityTracker.obtain().also { it.addMovement(event) }
                lastTouchY = event.y
                dragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - lastTouchY
                lastTouchY = event.y
                if (dragging) applyScroll(scrollPos - dy / itemHeight)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                val velocity = velocityTracker?.let {
                    it.computeCurrentVelocity(1000)
                    it.yVelocity
                } ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                if (abs(velocity) > 400f) {
                    startFling(velocity)
                } else {
                    settleTo(scrollPos.roundToInt().toFloat())
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyScroll(value: Float) {
        val max = (items.size - 1).toFloat()
        val clamped = value.coerceIn(0f, max)
        if (clamped != scrollPos) {
            scrollPos = clamped
            invalidate()
        }
    }

    private fun startFling(velocityY: Float) {
        val max = ((items.size - 1) * itemHeight).toInt()
        scroller.fling(0, (scrollPos * itemHeight).toInt(), 0, (-velocityY).toInt(), 0, 0, 0, max)
        flingActive = true
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (flingActive && scroller.computeScrollOffset()) {
            applyScroll(scroller.currY / itemHeight)
            if (scroller.isFinished) {
                flingActive = false
                settleTo(scrollPos.roundToInt().toFloat())
            } else {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun settleTo(target: Float) {
        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(scrollPos, target).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollPos = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    commitSelection()
                }
            })
            start()
        }
    }

    private fun commitSelection() {
        val index = scrollPos.roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (index != selected) {
            selected = index
            onSelectionChanged?.invoke(index)
        }
    }
}
