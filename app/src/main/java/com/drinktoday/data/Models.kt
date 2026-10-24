package com.drinktoday.data

/** 单次饮水记录。 */
data class DrinkRecord(
    val id: Long,
    val timeMillis: Long,
    val amountMl: Int
)

/** 某一天的汇总状态。 */
enum class DayState {
    /** 已达目标 */
    REACHED,

    /** 有记录但未达目标 */
    PARTIAL,

    /** 无任何记录 */
    NONE,

    /** 未来日期 */
    FUTURE
}

class DaySummary(
    val epochDay: Long,
    val totalMl: Int,
    val goalMl: Int,
    val today: Long
) {
    val hasRecord: Boolean get() = totalMl > 0
    val reached: Boolean get() = goalMl > 0 && totalMl >= goalMl

    val state: DayState
        get() = when {
            epochDay > today -> DayState.FUTURE
            reached -> DayState.REACHED
            hasRecord -> DayState.PARTIAL
            else -> DayState.NONE
        }

    val remainingMl: Int get() = (goalMl - totalMl).coerceAtLeast(0)

    val ratio: Float get() = if (goalMl <= 0) 0f else totalMl.toFloat() / goalMl
}

/** 导入/同步的结果统计。 */
data class ImportResult(
    val addedRecords: Int,
    val skippedRecords: Int
)
