package com.drinktoday.util

import java.util.Locale

/**
 * 水量展示：小于 1000 ml 用毫升，超过则用升。
 * 超过 100 L 时省略小数位，避免「730.0 L」这种多余精度。
 */
object Volume {

    /** 拆成「数值 + 单位」，便于给单位单独缩小字号。 */
    fun split(ml: Int): Pair<String, String> = when {
        ml < 1000 -> ml.toString() to "ml"
        ml < 100_000 -> trim(String.format(Locale.US, "%.1f", ml / 1000f)) to "L"
        else -> String.format(Locale.US, "%.0f", ml / 1000f) to "L"
    }

    /** 括号内展示用，如「730 L」。 */
    fun plain(ml: Int): String = split(ml).let { (value, unit) -> "$value $unit" }

    private fun trim(text: String): String = text.removeSuffix(".0")
}
