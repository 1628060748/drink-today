package com.drinktoday.ui

import androidx.appcompat.app.AppCompatActivity
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.databinding.PageHistoryBinding
import com.drinktoday.util.DateX
import com.drinktoday.util.dpInt
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * 统计历史页：日历三态标记 + 本月概览 + 每日水量柱状图。
 */
class HistoryPage(
    private val activity: AppCompatActivity,
    private val binding: PageHistoryBinding,
    private val store: DrinkStore
) {

    private var year: Int
    private var month: Int
    private var loadedYear = -1
    private var loadedMonth = -1

    init {
        val now = LocalDate.now()
        year = now.year
        month = now.monthValue

        binding.calendar.onDayClick = { day -> openDayDetail(day) }
        binding.btnPrev.setOnClickListener { shiftMonth(-1) }
        binding.btnNext.setOnClickListener { shiftMonth(1) }
        TouchFx.press(binding.btnPrev, 0.85f)
        TouchFx.press(binding.btnNext, 0.85f)
    }

    fun refresh(animate: Boolean = false) {
        val goal = store.goalMl
        val today = DateX.todayEpochDay()
        val totals = store.totalsOfMonth(year, month)

        if (loadedYear != year || loadedMonth != month) {
            binding.calendar.setMonth(year, month)
            loadedYear = year
            loadedMonth = month
        }
        binding.calendar.setData(totals, goal, today)
        binding.tvMonth.text = DateX.monthTitle(year, month)

        val reached = totals.values.count { it >= goal }
        binding.tvReachedDays.text = activity.getString(R.string.stat_value_days, reached)
        binding.tvRecordDays.text = activity.getString(R.string.stat_value_days, totals.size)
        binding.tvAverage.text = if (totals.isEmpty()) {
            activity.getString(R.string.stat_value_none)
        } else {
            activity.getString(R.string.stat_value_ml, totals.values.average().roundToInt())
        }
        binding.tvStreak.text = activity.getString(R.string.stat_value_days, store.currentStreak(today))

        val days = DateX.daysInMonth(year, month)
        val values = IntArray(days) { index -> totals[DateX.epochDayOf(year, month, index + 1)] ?: 0 }
        binding.chart.setData(values, goal, animate)
        binding.tvEmpty.visibility = if (totals.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val isLatest = YearMonth.of(year, month) == YearMonth.now()
        binding.btnNext.isEnabled = !isLatest
        binding.btnNext.alpha = if (isLatest) 0.25f else 1f
    }

    private fun shiftMonth(delta: Int) {
        val target = YearMonth.of(year, month).plusMonths(delta.toLong())
        if (target.isAfter(YearMonth.now())) return
        year = target.year
        month = target.monthValue
        refresh(animate = true)
        animateCards(if (delta > 0) 1f else -1f)
    }

    private fun animateCards(direction: Float) {
        val offset = dpInt(14f) * direction
        for (card in arrayOf(binding.calendarCard, binding.overviewCard)) {
            card.animate().cancel()
            card.alpha = 0f
            card.translationX = offset.toFloat()
            card.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(240L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun openDayDetail(day: Long) {
        val sheet = DayDetailSheet(activity, store, day)
        sheet.setOnDismissListener { refresh(animate = false) }
        sheet.show()
    }
}
