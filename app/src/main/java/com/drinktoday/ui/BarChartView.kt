package com.drinktoday.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.drinktoday.util.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 本月每日饮水量迷你柱状图。
 * 柱体按达标/未达标着色，虚线表示目标水位，数据更新时自左向右生长。
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: IntArray = IntArray(0)
    private var goalMl = 2000
    private var progress = 1f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x403482FF
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    fun setData(values: IntArray, goalMl: Int, animate: Boolean) {
        this.values = values
        this.goalMl = goalMl.coerceAtLeast(1)
        animator?.cancel()
        if (!animate) {
            progress = 1f
            invalidate()
            return
        }
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 680L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(200f).toInt(), widthMeasureSpec)
        val height = resolveSize(dp(104f).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val count = values.size
        if (count == 0) return
        val bottom = height - paddingBottom.toFloat()
        val usableHeight = bottom - paddingTop - dp(4f)
        if (usableHeight <= 0f) return

        var maxValue = goalMl * 1.12f
        for (v in values) if (v > maxValue) maxValue = v.toFloat()

        // 目标虚线（位于柱体之下）
        val goalY = bottom - usableHeight * (goalMl / maxValue)
        canvas.drawLine(0f, goalY, width.toFloat(), goalY, goalPaint)

        val slot = width.toFloat() / count
        val barWidth = max(slot * 0.5f, dp(2.5f))
        val radius = barWidth / 2f

        for (i in 0 until count) {
            val value = values[i]
            val factor = barFactor(i, count)
            val barHeight = if (value <= 0) {
                dp(2f)
            } else {
                max(usableHeight * (value / maxValue) * factor, dp(2.5f))
            }
            val left = i * slot + (slot - barWidth) / 2f
            val top = bottom - barHeight
            barPaint.color = when {
                value <= 0 -> 0xFFE7EBF2.toInt()
                value >= goalMl -> Palette.ACCENT
                else -> 0xFFB9D7FF.toInt()
            }
            canvas.drawRoundRect(left, top, left + barWidth, min(top + barWidth, bottom), radius, radius, barPaint)
            if (barHeight > barWidth) {
                canvas.drawRect(left, top + radius, left + barWidth, bottom, barPaint)
            }
        }
    }

    private fun barFactor(index: Int, count: Int): Float {
        val stagger = (progress * 1.5f - (index.toFloat() / count) * 0.5f).coerceIn(0f, 1f)
        val inverted = 1f - stagger
        return 1f - inverted * inverted
    }
}
