package com.drinktoday.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.drinktoday.util.dp
import kotlin.math.min
import kotlin.math.sin

/**
 * 水杯视图：玻璃杯轮廓 + 双层正弦水波 + 上升气泡 + 水位弹性动画。
 *
 * 性能要点：
 *  - 路径 / 画笔 / 矩形全部复用，onDraw 内不产生新对象；
 *  - 帧循环由可见性驱动：杯子为空、页面不可见、Activity 暂停时立即停止；
 *  - 不使用 Bitmap、不做离屏缓存，单帧只有数十个 Path 采样点。
 */
class CupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ------------------------------------------------------------ 数据

    private var goalMl = 2000
    private var totalMl = 0
    private var level = 0f
    private var targetLevel = 0f
    private var levelAnimator: ValueAnimator? = null

    // ------------------------------------------------------------ 动效状态

    private var phase = 0f
    private var slosh = 0f
    private var timeSec = 0f
    private var lastFrameNanos = 0L
    private var frameScheduled = false
    private var running = false
    private var paused = false

    // ------------------------------------------------------------ 几何与绘制缓存

    private val cupRect = RectF()
    private val innerRect = RectF()
    private val rimRect = RectF()
    private val glossRect = RectF()
    private val groundRect = RectF()
    private var cupInset = 0f
    private var rimHalf = 0f
    private var bandRadius = 0f

    private val cupPath = Path()
    private val innerPath = Path()
    private val waterPath = Path()
    private val backWaterPath = Path()
    private val surfacePath = Path()

    private val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backWaterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.4f)
        color = 0x66FFFFFF
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Palette.GLASS_STROKE
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 气泡参数（固定分布，避免每帧随机闪烁）
    private val bubbleX = floatArrayOf(0.26f, 0.44f, 0.63f, 0.36f, 0.71f, 0.53f, 0.31f)
    private val bubbleSpeed = floatArrayOf(0.085f, 0.135f, 0.075f, 0.115f, 0.095f, 0.155f, 0.105f)
    private val bubbleRadius = floatArrayOf(2.0f, 1.4f, 2.6f, 1.7f, 2.3f, 1.2f, 1.8f)
    private val bubbleOffset = floatArrayOf(0.1f, 0.42f, 0.68f, 0.25f, 0.86f, 0.55f, 0.33f)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!running) return
            step(frameTimeNanos)
            scheduleFrame()
        }
    }

    // ------------------------------------------------------------ 对外接口

    /** 更新数据。[animate] 为 true 时水位做弹性补间。 */
    fun setData(goalMl: Int, totalMl: Int, animate: Boolean = true) {
        this.goalMl = goalMl.coerceAtLeast(1)
        this.totalMl = totalMl
        val target = (totalMl.toFloat() / this.goalMl).coerceIn(0f, 1f)
        val rising = target > targetLevel + 0.0005f
        targetLevel = target

        levelAnimator?.cancel()
        levelAnimator = null

        if (!animate || width == 0 || !isLaidOut) {
            level = target
            slosh = 0f
            invalidate()
            updateLoopState()
            return
        }

        if (rising) slosh = 1f
        levelAnimator = ValueAnimator.ofFloat(level, target).apply {
            duration = if (rising) 900L else 420L
            interpolator = if (rising) OvershootInterpolator(0.5f) else DecelerateInterpolator()
            addUpdateListener {
                level = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    level = target
                    invalidate()
                    updateLoopState()
                }
            })
            start()
        }
        updateLoopState()
    }

    /** 页面切换到后台/不可见时暂停帧循环，恢复时继续。 */
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        updateLoopState()
    }

    // ------------------------------------------------------------ 生命周期

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoopState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateLoopState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLoopState()
    }

    // ------------------------------------------------------------ 帧循环

    private fun updateLoopState() {
        val needed = isAttachedToWindow &&
            visibility == VISIBLE &&
            windowVisibility == VISIBLE &&
            !paused &&
            (level > 0.002f || targetLevel > 0.002f || levelAnimator?.isRunning == true)
        if (needed && !running) {
            running = true
            lastFrameNanos = 0L
            scheduleFrame()
        } else if (!needed && running) {
            stopLoop()
        }
    }

    private fun stopLoop() {
        running = false
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
        }
    }

    private fun scheduleFrame() {
        if (!frameScheduled && running) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun step(nanos: Long) {
        if (lastFrameNanos == 0L) lastFrameNanos = nanos
        val dt = ((nanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = nanos
        timeSec += dt

        // 水面晃动后逐渐平息
        if (slosh > 0f) slosh = (slosh - dt / 1.5f).coerceAtLeast(0f)
        phase += dt * (1.45f + slosh * 3.4f)
        if (phase > TWO_PI) phase -= TWO_PI
        invalidate()
    }

    // ------------------------------------------------------------ 尺寸与形状

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val availW = (w - paddingLeft - paddingRight).toFloat()
        val availH = (h - paddingTop - paddingBottom).toFloat()
        if (availW <= 0f || availH <= 0f) return

        var cupW = min(availW * 0.70f, dp(246f))
        var cupH = cupW * 1.24f
        if (cupH > availH) {
            cupH = availH
            cupW = cupH / 1.24f
        }
        val cx = paddingLeft + availW / 2f
        val cy = paddingTop + availH / 2f
        cupRect.set(cx - cupW / 2f, cy - cupH / 2f, cx + cupW / 2f, cy + cupH / 2f)

        cupInset = dp(1.6f)
        rimHalf = dp(7.5f)
        bandRadius = cupW * 0.5f

        buildShader()
        buildCupPath(cupPath, 0f)
        buildCupPath(innerPath, dp(2.2f))
        innerRect.set(
            cupRect.left + dp(3f),
            cupRect.top + dp(3f),
            cupRect.right - dp(3f),
            cupRect.bottom - dp(3f)
        )
    }

    private fun buildShader() {
        cupPaint.shader = LinearGradient(
            0f, cupRect.top, 0f, cupRect.bottom,
            intArrayOf(Palette.GLASS_TOP, 0xFFF8FBFF.toInt(), Palette.GLASS_BOTTOM),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        waterPaint.shader = LinearGradient(
            0f, cupRect.top, 0f, cupRect.bottom,
            intArrayOf(Palette.WATER_TOP, 0xFF4FA5F6.toInt(), Palette.WATER_BOTTOM),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        glossPaint.shader = LinearGradient(
            0f, cupRect.top, 0f, cupRect.bottom,
            intArrayOf(0x8CFFFFFF.toInt(), 0x14FFFFFF),
            null,
            Shader.TileMode.CLAMP
        )
        edgePaint.shader = LinearGradient(
            cupRect.left, 0f, cupRect.right, 0f,
            intArrayOf(0x1F14355F, 0x0014355F, 0x0014355F, 0x1F14355F),
            floatArrayOf(0f, 0.14f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )
        val gx = cupRect.centerX()
        val gy = cupRect.bottom + dp(2f)
        val gradius = cupRect.width() * 0.62f
        groundPaint.shader = RadialGradient(
            gx, gy, gradius,
            intArrayOf(0x1A1B2C46, 0x001B2C46),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        groundRect.set(gx - cupRect.width() * 0.44f, cupRect.bottom - dp(6f), gx + cupRect.width() * 0.44f, cupRect.bottom + dp(12f))
        rimPaint.color = 0x0F2C5C96
        rimPaint.style = Paint.Style.FILL
    }

    /** 上宽下窄、侧壁微微外鼓的杯型 */
    private fun buildCupPath(path: Path, inset: Float) {
        val l = cupRect.left + inset
        val r = cupRect.right - inset
        val t = cupRect.top + inset
        val b = cupRect.bottom - inset
        val w = r - l
        val h = b - t
        val cx = (l + r) / 2f
        val topHalf = w / 2f
        val bottomHalf = topHalf * 0.815f
        val rTop = dp(13f).coerceAtMost(w * 0.11f)
        val rBottom = dp(26f).coerceAtMost(w * 0.15f)

        path.reset()
        path.moveTo(cx - topHalf, t + rTop)
        path.quadTo(cx - topHalf, t, cx - topHalf + rTop, t)
        path.lineTo(cx + topHalf - rTop, t)
        path.quadTo(cx + topHalf, t, cx + topHalf, t + rTop)
        // 右侧壁：中部略微外鼓，接近底部收窄
        path.cubicTo(
            cx + topHalf, t + h * 0.44f,
            cx + bottomHalf + dp(3f), b - h * 0.30f,
            cx + bottomHalf, b - rBottom
        )
        path.quadTo(cx + bottomHalf, b, cx + bottomHalf - rBottom, b)
        path.lineTo(cx - bottomHalf + rBottom, b)
        path.quadTo(cx - bottomHalf, b, cx - bottomHalf, b - rBottom)
        path.cubicTo(
            cx - bottomHalf - dp(3f), b - h * 0.30f,
            cx - topHalf, t + h * 0.44f,
            cx - topHalf, t + rTop
        )
        path.close()
    }

    // ------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        if (cupRect.width() <= 0f) return
        drawGround(canvas)
        canvas.drawPath(cupPath, cupPaint)
        drawWater(canvas)
        drawRim(canvas)
        drawGloss(canvas)
        canvas.drawPath(cupPath, strokePaint)
    }

    private fun drawGround(canvas: Canvas) {
        canvas.drawOval(groundRect, groundPaint)
    }

    private fun drawWater(canvas: Canvas) {
        if (level <= 0.0015f) return
        val top = cupRect.top + dp(6f)
        val bottom = cupRect.bottom - dp(3f)
        val height = bottom - top
        val surfaceY = bottom - level * height
        val waterHeight = bottom - surfaceY
        if (waterHeight < dp(2.5f)) return

        val amp = min(dp(2.0f) + slosh * dp(3.4f), waterHeight * 0.45f)

        canvas.save()
        canvas.clipPath(innerPath)

        // 后层水波：更高、更淡，形成前后层次
        buildWave(backWaterPath, surfaceY - amp * 0.55f, amp * 0.75f, 1.9f)
        backWaterPaint.shader = null
        backWaterPaint.color = Palette.WATER_BACK
        canvas.drawPath(backWaterPath, backWaterPaint)

        // 前层水波（主水面）
        buildWave(waterPath, surfaceY, amp, 0f)
        canvas.drawPath(waterPath, waterPaint)

        // 水面高光
        buildSurfaceLine(surfacePath, surfaceY, amp)
        canvas.drawPath(surfacePath, surfacePaint)

        // 气泡
        drawBubbles(canvas, surfaceY, bottom)

        // 杯壁内侧的轻微暗边，增强玻璃厚度感
        canvas.drawRect(innerRect, edgePaint)

        canvas.restore()
    }

    private fun buildWave(path: Path, surfaceY: Float, amp: Float, phaseShift: Float) {
        val left = cupRect.left - dp(4f)
        val right = cupRect.right + dp(4f)
        val width = right - left
        path.reset()
        path.moveTo(left, surfaceY + waveOffset(0f, amp, phaseShift))
        var i = 1
        while (i <= WAVE_STEPS) {
            val t = i.toFloat() / WAVE_STEPS
            path.lineTo(left + width * t, surfaceY + waveOffset(t, amp, phaseShift))
            i++
        }
        path.lineTo(right, cupRect.bottom + dp(4f))
        path.lineTo(left, cupRect.bottom + dp(4f))
        path.close()
    }

    private fun buildSurfaceLine(path: Path, surfaceY: Float, amp: Float) {
        val left = cupRect.left - dp(4f)
        val right = cupRect.right + dp(4f)
        val width = right - left
        path.reset()
        path.moveTo(left, surfaceY + waveOffset(0f, amp, 0f))
        var i = 1
        while (i <= WAVE_STEPS) {
            val t = i.toFloat() / WAVE_STEPS
            path.lineTo(left + width * t, surfaceY + waveOffset(t, amp, 0f))
            i++
        }
    }

    /** 双频正弦叠加，形成不规则但连续的水面 */
    private fun waveOffset(t: Float, amp: Float, phaseShift: Float): Float {
        val a = sin(t * TWO_PI * 1.12f + phase + phaseShift)
        val b = sin(t * TWO_PI * 2.37f - phase * 1.28f + phaseShift * 1.7f)
        return amp * (0.62f * a + 0.38f * b)
    }

    private fun drawBubbles(canvas: Canvas, surfaceY: Float, bottom: Float) {
        val waterHeight = bottom - surfaceY
        if (waterHeight < dp(16f)) return
        val left = innerRect.left + innerRect.width() * 0.18f
        val span = innerRect.width() * 0.64f
        for (i in bubbleX.indices) {
            val progress = ((timeSec * bubbleSpeed[i] + bubbleOffset[i]) % 1f)
            val y = bottom - dp(4f) - progress * (waterHeight - dp(8f))
            if (y < surfaceY + dp(6f)) continue
            val fade = ((y - surfaceY) / (waterHeight * 0.5f)).coerceIn(0f, 1f)
            bubblePaint.color = (0x4D + (0x2E * fade).toInt()) shl 24 or 0xFFFFFF
            canvas.drawCircle(
                left + span * bubbleX[i],
                y,
                dp(bubbleRadius[i]) * (0.75f + 0.35f * progress),
                bubblePaint
            )
        }
    }

    private fun drawRim(canvas: Canvas) {
        rimRect.set(cupRect.left + cupInset, cupRect.top - rimHalf, cupRect.right - cupInset, cupRect.top + rimHalf)
        canvas.save()
        canvas.clipPath(innerPath)
        canvas.drawOval(rimRect, rimPaint)
        canvas.restore()
    }

    private fun drawGloss(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(innerPath)
        val left = innerRect.left + innerRect.width() * 0.115f
        val width = dp(7f)
        val top = innerRect.top + dp(16f)
        val height = innerRect.height() * 0.52f
        glossRect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(glossRect, width / 2f, width / 2f, glossPaint)
        canvas.restore()
    }

    private companion object {
        const val WAVE_STEPS = 44
        const val TWO_PI = 6.2831855f
    }
}
