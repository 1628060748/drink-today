package com.drinktoday.ui

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
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
        binding.btnGoal.setOnClickListener {
            GoalSheet(activity, store) { refresh(animate = true) }.show()
        }
        binding.btnBackfill.setOnClickListener {
            AddRecordSheet(activity, store) { record -> onRecordAdded(record) }.show()
        }
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
        container.contentDescription = activity.getString(R.string.record_amount_desc, amount)

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
        inner.addView(
            unit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpInt(3f) }
        )
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
    }
}
