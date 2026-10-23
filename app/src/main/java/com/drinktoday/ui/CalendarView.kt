package com.drinktoday.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.drinktoday.util.DateX
import com.drinktoday.util.dp
import java.time.LocalDate
import kotlin.math.min

/**
 * 月历网格：达标 / 未达标 / 未记录 三态标记，今日描边。
 * 固定 6 行，避免切换月份时高度跳动；全部自绘，无子 View。
 */
class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var year: Int = LocalDate.now().year
    private var month: Int = LocalDate.now().monthValue
    private var totals: Map<Long, Int> = emptyMap()
    private var goalMl = 2000
    private var today = DateX.todayEpochDay()

    private var daysInMonth = 31
    private var firstOffset = 0
    private var pressedDay = -1

    private var cellWidth = 0f
    private var cellHeight = 0f

    var onDayClick: ((Long) -> Unit)? = null

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11.5f)
        color = Palette.TEXT_3
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(13.5f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12000000 }
    private val cellRect = RectF()

    private val weekLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")

    init {
        isClickable = true
    }

    fun setMonth(year: Int, month: Int) {
        this.year = year
        this.month = month
        this.daysInMonth = DateX.daysInMonth(year, month)
        this.firstOffset = DateX.firstDayOffset(year, month)
        this.pressedDay = -1
        requestLayout()
        invalidate()
    }

    fun setData(totals: Map<Long, Int>, goalMl: Int, today: Long) {
        this.totals = totals
        this.goalMl = goalMl.coerceAtLeast(1)
        this.today = today
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availW = (width - paddingLeft - paddingRight).toFloat()
        cellWidth = if (availW > 0) availW / 7f else 0f
        cellHeight = cellWidth * 1.02f
        val desiredHeight = (paddingTop + headerHeight() + cellHeight * ROWS + paddingBottom).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun headerHeight(): Float = dp(26f)

    private fun gridTop(): Float = paddingTop + headerHeight()

    override fun onDraw(canvas: Canvas) {
        if (cellWidth <= 0f) return
        drawHeader(canvas)
        drawCells(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        val top = paddingTop + headerHeight() * 0.58f
        for (i in 0 until 7) {
            val cx = paddingLeft + cellWidth * (i + 0.5f)
            canvas.drawText(weekLabels[i], cx, top, headerPaint)
        }
    }

    private fun drawCells(canvas: Canvas) {
        val top = gridTop()
        val corner = min(dp(14f), cellWidth * 0.27f)

        for (day in 1..daysInMonth) {
            val index = firstOffset + day - 1
            val col = index % 7
            val row = index / 7
            val epochDay = LocalDate.of(year, month, day).toEpochDay()
            val isFuture = epochDay > today
            val isToday = epochDay == today
            val total = totals[epochDay] ?: 0
            val reached = total >= goalMl
            val hasRecord = total > 0

            cellRect.set(
                paddingLeft + col * cellWidth + dp(3f),
                top + row * cellHeight + dp(3f),
                paddingLeft + (col + 1) * cellWidth - dp(3f),
                top + (row + 1) * cellHeight - dp(3f)
            )

            when {
                reached -> {
                    fillPaint.shader = null
                    fillPaint.color = Palette.ACCENT
                    canvas.drawRoundRect(cellRect, corner, corner, fillPaint)
                    dayPaint.color = Palette.SURFACE
                }

                hasRecord && !isFuture -> {
                    fillPaint.shader = null
                    fillPaint.color = Palette.ACCENT_SOFT
                    canvas.drawRoundRect(cellRect, corner, corner, fillPaint)
                    dayPaint.color = Palette.ACCENT
                }

                else -> {
                    dayPaint.color = if (isFuture) 0xFFD3D9E4.toInt() else Palette.TEXT_3
                }
            }

            if (pressedDay == day && !isFuture) {
                canvas.drawRoundRect(cellRect, corner, corner, pressPaint)
            }

            if (isToday) {
                ringPaint.color = if (reached) 0x99FFFFFF.toInt() else 0x8A3482FF.toInt()
                val inset = if (reached) dp(2.4f) else dp(1f)
                cellRect.inset(inset, inset)
                canvas.drawRoundRect(cellRect, corner, corner, ringPaint)
                cellRect.inset(-inset, -inset)
            }

            val metrics = dayPaint.fontMetrics
            canvas.drawText(
                day.toString(),
                cellRect.centerX(),
                cellRect.centerY() - (metrics.ascent + metrics.descent) / 2f,
                dayPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val day = dayAt(event.x, event.y)
                if (day <= 0) return false
                pressedDay = day
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val day = dayAt(event.x, event.y)
                if (day != pressedDay) {
                    pressedDay = day
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val day = dayAt(event.x, event.y)
                if (day > 0 && day == pressedDay) {
                    pressedDay = -1
                    invalidate()
                    performClick()
                    onDayClick?.invoke(LocalDate.of(year, month, day).toEpochDay())
                } else {
                    pressedDay = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedDay = -1
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dayAt(x: Float, y: Float): Int {
        if (cellWidth <= 0f) return -1
        val top = gridTop()
        if (y < top) return -1
        val col = ((x - paddingLeft) / cellWidth).toInt()
        val row = ((y - top) / cellHeight).toInt()
        if (col < 0 || col > 6 || row < 0 || row >= ROWS) return -1
        val day = row * 7 + col - firstOffset + 1
        if (day < 1 || day > daysInMonth) return -1
        val epochDay = LocalDate.of(year, month, day).toEpochDay()
        if (epochDay > today) return -1
        return day
    }

    private companion object {
        const val ROWS = 6
    }
}
