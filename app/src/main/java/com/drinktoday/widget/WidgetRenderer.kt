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

    private const val H_PADDING_DP = 32f      // 卡片左右内边距
    private const val H_GAP_DP = 14f          // 左右两栏间距
    private const val LEFT_WEIGHT = 1.2f
    private const val RIGHT_WEIGHT = 1f
    private const val V_CHROME_DP = 58f       // 上下内边距 + 数字行 + 间距
    private const val FALLBACK_W_DP = 170f
    private const val FALLBACK_H_DP = 170f

    private val BUTTON_IDS = intArrayOf(
        R.id.widgetBtn1,
        R.id.widgetBtn2,
        R.id.widgetBtn3,
        R.id.widgetBtn4
    )

    private val BUTTON_VALUE_IDS = intArrayOf(
        R.id.widgetBtn1Value,
        R.id.widgetBtn2Value,
        R.id.widgetBtn3Value,
        R.id.widgetBtn4Value
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
        val (cupWidthDp, cupHeightDp) = cupSizeDp(options)
        views.setImageViewBitmap(
            R.id.widgetCup,
            renderCup(
                context,
                (cupWidthDp * density).toInt(),
                (cupHeightDp * density).toInt(),
                goalMl,
                totalMl
            )
        )

        views.setTextViewText(R.id.widgetTotal, totalMl.toString())
        views.setTextViewText(R.id.widgetGoal, context.getString(R.string.widget_goal, goalMl))
        // 达标后数字转为强调色，替代原来的提示行
        views.setTextColor(
            R.id.widgetTotal,
            ContextCompat.getColor(
                context,
                if (totalMl >= goalMl && totalMl > 0) R.color.accent else R.color.text_primary
            )
        )

        for (slot in BUTTON_IDS.indices) {
            val amount = if (slot < amounts.size) {
                amounts[slot]
            } else {
                DrinkStore.DEFAULT_QUICK_AMOUNTS[slot]
            }
            views.setTextViewText(
                BUTTON_VALUE_IDS[slot],
                context.getString(R.string.widget_add_value, amount)
            )
            views.setOnClickPendingIntent(BUTTON_IDS[slot], quickAddIntent(context, slot, amount))
        }

        val openApp = openAppIntent(context)
        views.setOnClickPendingIntent(R.id.widgetCupArea, openApp)
        views.setOnClickPendingIntent(R.id.widgetInfo, openApp)
        return views
    }

    /**
     * 依据小部件实际格子尺寸推算左栏杯区大小：位图与显示区域一致，
     * 既不会因放大而发虚，也不会因为固定尺寸而在大格子里显小。
     */
    private fun cupSizeDp(options: Bundle?): Pair<Float, Float> {
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

        val columnWidth = if (widthDp > 0) {
            (widthDp - H_PADDING_DP - H_GAP_DP) * (LEFT_WEIGHT / (LEFT_WEIGHT + RIGHT_WEIGHT))
        } else {
            FALLBACK_W_DP
        }
        val cupHeight = if (heightDp > 0) heightDp - V_CHROME_DP else FALLBACK_H_DP

        return columnWidth.coerceIn(90f, 210f) to cupHeight.coerceIn(84f, 230f)
    }

    private fun renderCup(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        goalMl: Int,
        totalMl: Int
    ): Bitmap {
        val view = CupView(context)
        view.setGroundShadowVisible(false)
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
