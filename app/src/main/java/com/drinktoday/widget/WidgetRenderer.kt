package com.drinktoday.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.drinktoday.MainActivity
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.ui.CupView

/**
 * 小部件内容构建。
 *
 * 水杯没有第二套画法：把真实的 [CupView] 离屏 measure/layout 后画进 Bitmap，
 * 保证桌面与 App 内的杯型、水位、配色完全一致。
 */
object WidgetRenderer {

    /** 卡片内除水杯外的固定高度（上下内边距 + 数字 + 提示 + 按钮）。 */
    private const val CHROME_HEIGHT_DP = 114f
    private const val MIN_CUP_DP = 86f
    private const val MAX_CUP_DP = 200f
    private const val FALLBACK_CUP_DP = 150f

    private val BUTTON_IDS = intArrayOf(
        R.id.widgetBtn1,
        R.id.widgetBtn2,
        R.id.widgetBtn3,
        R.id.widgetBtn4
    )

    fun build(
        context: Context,
        options: Bundle?,
        goalMl: Int,
        totalMl: Int,
        amounts: IntArray
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_drink)

        val density = context.resources.displayMetrics.density
        val sidePx = (cupSideDp(options) * density).toInt()
        views.setImageViewBitmap(
            R.id.widgetCup,
            renderCup(context, sidePx, sidePx, goalMl, totalMl)
        )

        views.setTextViewText(R.id.widgetTotal, totalMl.toString())
        views.setTextViewText(R.id.widgetGoal, context.getString(R.string.widget_goal, goalMl))
        views.setTextViewText(R.id.widgetHint, hintText(context, goalMl, totalMl))
        views.setTextColor(
            R.id.widgetHint,
            ContextCompat.getColor(
                context,
                if (totalMl >= goalMl && totalMl > 0) R.color.accent else R.color.text_secondary
            )
        )

        for (slot in BUTTON_IDS.indices) {
            val amount = if (slot < amounts.size) {
                amounts[slot]
            } else {
                DrinkStore.DEFAULT_QUICK_AMOUNTS[slot]
            }
            views.setTextViewText(BUTTON_IDS[slot], context.getString(R.string.widget_add, amount))
            views.setOnClickPendingIntent(BUTTON_IDS[slot], quickAddIntent(context, slot, amount))
        }

        val openApp = openAppIntent(context)
        views.setOnClickPendingIntent(R.id.widgetCupArea, openApp)
        views.setOnClickPendingIntent(R.id.widgetInfo, openApp)
        return views
    }

    /**
     * 依据小部件实际高度决定水杯位图边长：杯子区域用 layout_weight 吃掉剩余高度，
     * 位图按相近尺寸渲染，既不会因放大而发虚，也不会因过小而显脏。
     */
    private fun cupSideDp(options: Bundle?): Float {
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        if (heightDp <= 0) return FALLBACK_CUP_DP
        return (heightDp - CHROME_HEIGHT_DP).coerceIn(MIN_CUP_DP, MAX_CUP_DP)
    }

    private fun hintText(context: Context, goalMl: Int, totalMl: Int): String = when {
        totalMl <= 0 -> context.getString(R.string.widget_hint_empty)
        totalMl > goalMl -> context.getString(R.string.widget_hint_over, totalMl - goalMl)
        totalMl == goalMl -> context.getString(R.string.widget_hint_reached)
        else -> context.getString(R.string.widget_hint_remain, goalMl - totalMl)
    }

    private fun renderCup(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        goalMl: Int,
        totalMl: Int
    ): Bitmap {
        val view = CupView(context)
        view.setData(goalMl, totalMl, animate = false)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun quickAddIntent(context: Context, slot: Int, amount: Int): PendingIntent {
        val intent = Intent(context, DrinkWidgetProvider::class.java).apply {
            action = DrinkWidgetProvider.ACTION_QUICK_ADD
            // data 保证 4 个按钮的 PendingIntent 互不覆盖
            data = Uri.parse("drinktoday://widget/quick/$slot")
            putExtra(DrinkWidgetProvider.EXTRA_AMOUNT, amount)
        }
        return PendingIntent.getBroadcast(
            context,
            slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
