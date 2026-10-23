package com.drinktoday.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drinktoday.R
import com.drinktoday.data.DrinkRecord
import com.drinktoday.data.DrinkStore
import com.drinktoday.databinding.PageTodayBinding
import com.drinktoday.util.DateX
import com.drinktoday.util.dpInt
import kotlin.math.min

/**
 * 打卡首页：水杯水位、快捷记录、补录与目标设置。
 */
class TodayPage(
    private val activity: AppCompatActivity,
    private val binding: PageTodayBinding,
    private val store: DrinkStore
) {

    private val quickAmounts = intArrayOf(150, 200, 300, 500)
    private var totalAnimator: ValueAnimator? = null
    private var displayedTotal = 0
    private var snackRecord: DrinkRecord? = null
    private var snackDay: Long = 0L

    private val hideSnackRunnable = Runnable { hideSnack() }

    init {
        buildQuickChips()
        bindActions()
        refresh(animate = false)
    }

    // ------------------------------------------------------------ 初始化

    private fun bindActions() {
        binding.btnGoal.setOnClickListener {
            GoalSheet(activity, store) { refresh(animate = true) }.show()
        }
        binding.btnBackfill.setOnClickListener {
            AddRecordSheet(activity, store) { record -> onRecordAdded(record) }.show()
        }
        binding.snackUndo.setOnClickListener { undoLast() }

        TouchFx.press(binding.btnGoal, 0.9f)
        TouchFx.press(binding.btnBackfill, 0.975f)
    }

    private fun buildQuickChips() {
        for ((index, amount) in quickAmounts.withIndex()) {
            val chip = createChip(amount)
            val lp = LinearLayout.LayoutParams(0, dpInt(58f), 1f)
            if (index > 0) lp.marginStart = dpInt(8f)
            binding.quickRow.addView(chip, lp)
        }
    }

    private fun createChip(amount: Int): View {
        val container = FrameLayout(activity)
        container.background = ContextCompat.getDrawable(activity, R.drawable.bg_pill_white)
        container.contentDescription = activity.getString(R.string.added_toast, amount)

        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val value = TextView(activity).apply {
            text = amount.toString()
            textSize = 16f
            setTextColor(Palette.TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        val unit = TextView(activity).apply {
            text = activity.getString(R.string.unit_ml)
            textSize = 10f
            setTextColor(Palette.TEXT_3)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        inner.addView(value)
        inner.addView(unit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpInt(3f) })
        container.addView(
            inner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        container.setOnClickListener { addQuick(amount) }
        TouchFx.press(container, 0.94f)
        return container
    }

    // ------------------------------------------------------------ 数据刷新

    fun refresh(animate: Boolean) {
        val today = DateX.todayEpochDay()
        val goal = store.goalMl
        val total = store.total(today)
        val count = store.count(today)

        binding.cupView.setData(goal, total, animate)

        val dateText = buildString {
            append(DateX.dayTitle(today))
            if (count > 0) {
                append(" · ")
                append(activity.getString(R.string.records_count, count))
            }
        }
        binding.tvDate.text = dateText

        binding.tvHint.text = when {
            total <= 0 -> activity.getString(R.string.today_empty_hint)
            total > goal -> activity.getString(R.string.goal_overflow, goal, total - goal)
            total == goal -> activity.getString(R.string.goal_reached, goal)
            else -> activity.getString(R.string.goal_remain, goal, goal - total)
        }

        animateTotal(total, animate)
    }

    fun onResume() {
        refresh(animate = false)
    }

    fun setPaused(paused: Boolean) {
        binding.cupView.setPaused(paused)
    }

    // ------------------------------------------------------------ 记录

    private fun addQuick(amount: Int) {
        val now = System.currentTimeMillis()
        val record = store.add(DateX.todayEpochDay(), now, amount)
        onRecordAdded(record)
    }

    private fun onRecordAdded(record: DrinkRecord) {
        snackRecord = record
        snackDay = DateX.epochDayOf(record.timeMillis)
        refresh(animate = true)
        showSnack(activity.getString(R.string.added_toast, record.amountMl))
    }

    private fun undoLast() {
        val record = snackRecord ?: return
        store.remove(snackDay, record.id)
        snackRecord = null
        hideSnack()
        refresh(animate = true)
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

    // ------------------------------------------------------------ 提示条

    private fun showSnack(message: String) {
        binding.snackText.text = message
        binding.snackBar.removeCallbacks(hideSnackRunnable)
        binding.snackBar.visibility = View.VISIBLE
        binding.snackBar.alpha = 0f
        binding.snackBar.translationY = dpInt(14f).toFloat()
        binding.snackBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        binding.snackBar.postDelayed(hideSnackRunnable, SNACK_DURATION)
    }

    private fun hideSnack() {
        binding.snackBar.removeCallbacks(hideSnackRunnable)
        if (binding.snackBar.visibility != View.VISIBLE) return
        binding.snackBar.animate()
            .alpha(0f)
            .translationY(dpInt(14f).toFloat())
            .setDuration(180L)
            .withEndAction { binding.snackBar.visibility = View.GONE }
            .start()
    }

    private companion object {
        const val SNACK_DURATION = 3200L
    }
}
