package com.drinktoday.util

import android.content.res.Resources

/** 统一的 dp 换算，避免各处重复取 density。 */
fun dp(value: Float): Float = value * Resources.getSystem().displayMetrics.density

fun dpInt(value: Float): Int = (value * Resources.getSystem().displayMetrics.density).toInt()
