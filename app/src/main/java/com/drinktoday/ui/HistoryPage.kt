package com.drinktoday.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.appcompat.app.AppCompatActivity
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.databinding.PageHistoryBinding
import com.drinktoday.util.DateX
import com.drinktoday.util.Volume
import com.drinktoday.util.dpInt
import java.time.DayOfWeek
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
        renderTotals()

        val days = DateX.daysInMonth(year, month)
        val values = IntArray(days) { index -> totals[DateX.epochDayOf(year, month, index + 1)] ?: 0 }
        binding.chart.setData(values, goal, animate)
        binding.tvChartHint.text = activity.getString(R.string.chart_goal_hint, goal)
        binding.tvEmpty.visibility = if (totals.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val isLatest = YearMonth.of(year, month) == YearMonth.now()
        binding.btnNext.isEnabled = !isLatest
        binding.btnNext.alpha = if (isLatest) 0.25f else 1f
    }

    /** 周 / 月 / 年累计 + 一句具象类比，避免大数字失去体感。 */
    private fun renderTotals() {
        val todayEpoch = DateX.todayEpochDay()
        val today = DateX.toDate(todayEpoch)
        val weekTotal = store.totalBetween(today.with(DayOfWeek.MONDAY).toEpochDay(), todayEpoch)
        val monthTotal = store.totalBetween(today.withDayOfMonth(1).toEpochDay(), todayEpoch)
        val yearTotal = store.totalBetween(today.withDayOfYear(1).toEpochDay(), todayEpoch)

        binding.tvWeekTotal.text = volumeText(weekTotal)
        binding.tvMonthTotal.text = volumeText(monthTotal)
        binding.tvYearTotal.text = volumeText(yearTotal)
        binding.tvTotalCompare.text = comparisonText(weekTotal, monthTotal, yearTotal)
    }

    private fun volumeText(ml: Int): CharSequence {
        val (value, unit) = Volume.split(ml)
        val text = "$value $unit"
        val spannable = SpannableString(text)
        spannable.setSpan(
            RelativeSizeSpan(0.55f), value.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Palette.TEXT_3), value.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun comparisonText(week: Int, month: Int, year: Int): String {
        val (label, ml) = when {
            year > 0 -> activity.getString(R.string.period_year) to year
            month > 0 -> activity.getString(R.string.period_month) to month
            week > 0 -> activity.getString(R.string.period_week) to week
            else -> return activity.getString(R.string.total_empty)
        }
        val volume = Volume.plain(ml)
        return when {
            ml < 500 -> activity.getString(R.string.total_tiny, label, volume)
            ml < 20_000 -> activity.getString(
                R.string.total_bottles, label, volume, (ml / 500f).roundToInt().coerceAtLeast(1)
            )
            else -> activity.getString(
                R.string.total_barrels, label, volume, (ml / 18_900f).roundToInt().coerceAtLeast(1)
            )
        }
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
