package com.drinktoday.ui

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.drinktoday.R
import com.drinktoday.data.DrinkRecord
import com.drinktoday.data.DrinkStore
import com.drinktoday.databinding.PageTodayBinding
import com.drinktoday.util.DateX
import com.drinktoday.util.dpInt
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 打卡首页：水杯水位、快捷记录、补录与设置。
 */
class TodayPage(
    private val activity: AppCompatActivity,
    private val binding: PageTodayBinding,
    private val store: DrinkStore
) {

    private var totalAnimator: ValueAnimator? = null
    private var displayedTotal = 0
    private var currentTotal = 0
    private var currentGoal = DrinkStore.DEFAULT_GOAL

    private var undoRecord: DrinkRecord? = null
    private var undoDay: Long = 0L
    private val restoreHintRunnable = Runnable { renderHint() }

    init {
        buildQuickChips()
        bindActions()
        refresh(animate = false)
    }

    // ------------------------------------------------------------ 初始化

    private fun bindActions() {
        binding.btnBackfill.setOnClickListener {
            AddRecordSheet(activity, store) { record -> onRecordAdded(record) }.show()
        }
        TouchFx.press(binding.btnBackfill, 0.975f)
    }

    private fun openSettings() {
        SettingsSheet(activity, store) {
            // 目标或快捷水量变更后重建按钮，并让水位跟随新的目标补间
            rebuildQuickChips()
            refresh(animate = true)
        }.show()
    }

    private fun rebuildQuickChips() {
        binding.quickRow.removeAllViews()
        buildQuickChips()
    }

    private fun buildQuickChips() {
        val amounts = store.quickAmounts
        for ((index, amount) in amounts.withIndex()) {
            val chip = activity.layoutInflater
                .inflate(R.layout.item_quick_chip, binding.quickRow, false) as TextView
            chip.text = chipText(amount)
            chip.contentDescription = activity.getString(R.string.record_amount_desc, amount)
            chip.setOnClickListener { addQuick(amount) }
            chip.setOnLongClickListener {
                openSettings()
                true
            }

            val lp = LinearLayout.LayoutParams(0, dpInt(56f), 1f)
            if (index > 0) lp.marginStart = dpInt(8f)
            binding.quickRow.addView(chip, lp)
            TouchFx.press(chip, 0.94f)
        }
    }

    /**
     * 单个 TextView 承载「数值 + 单位」两行，避免嵌套测量导致数字被裁切。
     */
    private fun chipText(amount: Int): CharSequence {
        val number = amount.toString()
        val builder = SpannableStringBuilder(number)
            .append('\n')
            .append(activity.getString(R.string.unit_ml))
        val unitStart = number.length + 1
        builder.setSpan(
            RelativeSizeSpan(0.6f),
            unitStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            ForegroundColorSpan(Palette.TEXT_3),
            unitStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    // ------------------------------------------------------------ 重力与摇晃

    private val sensorManager: SensorManager? by lazy {
        activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val gravityVec = FloatArray(3)
    private var sensorsOn = false
    private var lastShakeAt = 0L
    private var lastMotionNanos = 0L
    private var smoothTilt = 0f

    private val motionListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = handleMotion(event)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * 加速度计同时承担两件事：
     *  1. 低通滤出重力方向 → 手机左右倾斜时水面跟着倾斜；
     *  2. 合矢量减去重力 → 摇晃强度，映射成波幅与涟漪。
     */
    private fun handleMotion(event: SensorEvent) {
        val values = event.values
        val alpha = GRAVITY_SMOOTHING
        for (i in 0..2) gravityVec[i] = alpha * gravityVec[i] + (1f - alpha) * values[i]

        // 平放在桌面上时屏幕平面内没有重力分量，倾斜角没有意义，直接归零
        val planar = hypot(gravityVec[0], gravityVec[1])
        val roll = if (planar < FLAT_THRESHOLD) {
            0f
        } else {
            Math.toDegrees(atan2(gravityVec[0].toDouble(), gravityVec[1].toDouble())).toFloat()
        }

        // 按真实间隔做指数平滑，采样率变化时手感一致（不会再出现阶梯感）
        val dt = if (lastMotionNanos == 0L) {
            0.02f
        } else {
            ((event.timestamp - lastMotionNanos) / 1_000_000_000f).coerceIn(0.001f, 0.2f)
        }
        lastMotionNanos = event.timestamp
        val follow = 1f - exp(-dt / TILT_SMOOTHING_SECONDS)

        val target = (roll * TILT_GAIN).coerceIn(-MAX_TILT, MAX_TILT)
        smoothTilt += (target - smoothTilt) * follow
        binding.cupView.setWaterTilt(smoothTilt)

        val magnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        val impulse = magnitude - SensorManager.GRAVITY_EARTH
        if (impulse > SHAKE_THRESHOLD) {
            val now = SystemClock.uptimeMillis()
            if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                lastShakeAt = now
                binding.cupView.onShake(
                    ((impulse - SHAKE_THRESHOLD) / SHAKE_SCALE).coerceIn(0.12f, 1f)
                )
            }
        }
    }

    private fun startMotionSensors() {
        if (sensorsOn) return
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        // GAME 档约 50 Hz：UI 档只有约 16 Hz，倾斜会明显一顿一顿
        manager.registerListener(motionListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        sensorsOn = true
    }

    private fun stopMotionSensors() {
        if (!sensorsOn) return
        sensorManager?.unregisterListener(motionListener)
        sensorsOn = false
        gravityVec.fill(0f)
        smoothTilt = 0f
        lastMotionNanos = 0L
        binding.cupView.setWaterTilt(0f)
    }

    // ------------------------------------------------------------ 数据刷新

    fun refresh(animate: Boolean) {
        val today = DateX.todayEpochDay()
        currentGoal = store.goalMl
        currentTotal = store.total(today)
        val count = store.count(today)

        binding.cupView.setData(currentGoal, currentTotal, animate)

        binding.tvDate.text = buildString {
            append(DateX.dayTitle(today))
            if (count > 0) {
                append(" · ")
                append(activity.getString(R.string.records_count, count))
            }
        }

        binding.tvHint.removeCallbacks(restoreHintRunnable)
        binding.tvHint.movementMethod = null
        renderHint()
        animateTotal(currentTotal, animate)
    }

    fun onResume() {
        refresh(animate = false)
    }

    fun setPaused(paused: Boolean) {
        binding.cupView.setPaused(paused)
        // 传感器只在首页可见且应用在前台时工作，避免后台耗电
        if (paused) stopMotionSensors() else startMotionSensors()
    }

    private fun renderHint() {
        val goal = currentGoal
        val total = currentTotal
        binding.tvHint.text = when {
            total <= 0 -> activity.getString(R.string.today_empty_hint)
            total > goal -> activity.getString(R.string.goal_overflow, goal, total - goal)
            total == goal -> activity.getString(R.string.goal_reached, goal)
            else -> activity.getString(R.string.goal_remain, goal, goal - total)
        }
    }

    // ------------------------------------------------------------ 记录

    private fun addQuick(amount: Int) {
        val now = System.currentTimeMillis()
        val record = store.add(DateX.todayEpochDay(), now, amount)
        onRecordAdded(record)
    }

    private fun onRecordAdded(record: DrinkRecord) {
        undoRecord = record
        undoDay = DateX.epochDayOf(record.timeMillis)
        refresh(animate = true)
        showUndoHint(record.amountMl)
    }

    /** 记录后在提示行内嵌「撤销」，不打断当前操作，也不遮挡主按钮。 */
    private fun showUndoHint(amountMl: Int) {
        val undoLabel = activity.getString(R.string.undo)
        val text = activity.getString(R.string.added_toast, amountMl) + "   " + undoLabel
        val spannable = SpannableString(text)
        val start = text.length - undoLabel.length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                undoLast()
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = Palette.ACCENT
                ds.isUnderlineText = false
                ds.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvHint.text = spannable
        binding.tvHint.movementMethod = LinkMovementMethod.getInstance()
        binding.tvHint.removeCallbacks(restoreHintRunnable)
        binding.tvHint.postDelayed(restoreHintRunnable, UNDO_DURATION)
    }

    private fun undoLast() {
        val record = undoRecord
        binding.tvHint.removeCallbacks(restoreHintRunnable)
        binding.tvHint.movementMethod = null
        if (record != null) {
            store.remove(undoDay, record.id)
            undoRecord = null
            refresh(animate = true)
        }
    }

    // ------------------------------------------------------------ 数字补间

    private fun animateTotal(target: Int, animate: Boolean) {
        totalAnimator?.cancel()
        if (!animate) {
            displayedTotal = target
            binding.tvTotal.text = target.toString()
            return
        }
        if (displayedTotal == target) {
            binding.tvTotal.text = target.toString()
            return
        }
        totalAnimator = ValueAnimator.ofInt(displayedTotal, target).apply {
            duration = 720L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                displayedTotal = it.animatedValue as Int
                binding.tvTotal.text = displayedTotal.toString()
            }
            start()
        }
    }

    private companion object {
        const val UNDO_DURATION = 3600L

        /** 重力低通系数：越大越稳，越小越跟手 */
        const val GRAVITY_SMOOTHING = 0.85f

        /** 判定「手机平放」的平面重力阈值 */
        const val FLAT_THRESHOLD = 2.5f

        /** 倾斜增益与上限角度：1:1 跟随重力，20° 上限足够明显又不失真 */
        const val TILT_GAIN = 1f
        const val MAX_TILT = 20f

        /** 倾斜跟随的时间常数，越小越跟手 */
        const val TILT_SMOOTHING_SECONDS = 0.06f

        /** 摇晃判定：超出重力的加速度、映射到 0..1 的尺度、防抖间隔 */
        const val SHAKE_THRESHOLD = 2.2f
        const val SHAKE_SCALE = 6f
        const val SHAKE_COOLDOWN_MS = 260L
    }
}
