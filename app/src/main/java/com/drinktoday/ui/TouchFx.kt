package com.drinktoday.ui

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 统一的按压反馈：按下轻微缩小、松手带回弹复位。
 * 与 HyperOS 的控件手感一致，且不额外引入 Ripple 资源与波纹通道开销。
 */
object TouchFx {

    fun press(view: View, scale: Float = 0.955f) {
        view.isClickable = true
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                    v.animate().cancel()
                    v.animate()
                        .scaleX(scale).scaleY(scale)
                        .setDuration(100L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!moved) {
                        moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                            kotlin.math.abs(event.y - downY) > touchSlop
                        if (moved) springBack(v)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> springBack(v)
            }
            false
        }
    }

    private fun springBack(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
    }
}
