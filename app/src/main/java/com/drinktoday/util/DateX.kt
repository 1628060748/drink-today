package com.drinktoday.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日期与文案工具。统一使用 epochDay (1970-01-01 起的天数) 作为「一天」的键，
 * 避免时区/夏令时带来的边界问题。
 */
object DateX {

    private val WEEK_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    fun toDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun weekdayLabel(epochDay: Long): String = WEEK_LABELS[toDate(epochDay).dayOfWeek.value - 1]

    /** 形如 “10月23日 周五” */
    fun dayTitle(epochDay: Long): String {
        val d = toDate(epochDay)
        return "${d.monthValue}月${d.dayOfMonth}日 ${weekdayLabel(epochDay)}"
    }

    /** 形如 “2026年10月” */
    fun monthTitle(year: Int, month: Int): String = "${year}年${month}月"

    /** 形如 “10月21日” */
    fun monthDay(epochDay: Long): String {
        val d = toDate(epochDay)
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 今天 / 昨天 / 前天 / 10月20日 */
    fun relativeDay(epochDay: Long, today: Long = todayEpochDay()): String = when (today - epochDay) {
        0L -> "今天"
        1L -> "昨天"
        2L -> "前天"
        else -> monthDay(epochDay)
    }

    fun timeLabel(timeMillis: Long): String =
        java.time.Instant.ofEpochMilli(timeMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(TIME_FORMAT)

    fun startOfDayMillis(epochDay: Long): Long =
        toDate(epochDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun timeMillisOf(epochDay: Long, hour: Int, minute: Int): Long {
        val zone = java.time.ZoneId.systemDefault()
        return toDate(epochDay).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    fun daysInMonth(year: Int, month: Int): Int = toDate(LocalDate.of(year, month, 1).toEpochDay()).lengthOfMonth()

    fun epochDayOf(timeMillis: Long): Long =
        java.time.Instant.ofEpochMilli(timeMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()

    fun epochDayOf(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    /** 周一为 0 的月首偏移 */
    fun firstDayOffset(year: Int, month: Int): Int = LocalDate.of(year, month, 1).dayOfWeek.value - 1
}
