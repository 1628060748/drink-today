package com.drinktoday.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.drinktoday.MainActivity
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.ui.CupView

/**
 * 小部件内容构建。
 *
 * 水杯没有第二套画法：直接把真实的 [CupView] 离屏 measure/layout 后画进 Bitmap，
 * 保证桌面与 App 内的杯型、水位、配色完全一致；改 App 的绘制代码时小部件自动跟随。
 */
object WidgetRenderer {

    // 比实际显示区域略大，交给 ImageView 的 fitCenter 缩放，兼顾清晰度与自适应
    private const val CUP_WIDTH_DP = 150f
    private const val CUP_HEIGHT_DP = 120f

    private val BUTTON_IDS = intArrayOf(
        R.id.widgetBtn1,
        R.id.widgetBtn2,
        R.id.widgetBtn3,
        R.id.widgetBtn4
    )

    fun build(context: Context, goalMl: Int, totalMl: Int, amounts: IntArray): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_drink)
        val density = context.resources.displayMetrics.density

        views.setImageViewBitmap(
            R.id.widgetCup,
            renderCup(
                context,
                (CUP_WIDTH_DP * density).toInt(),
                (CUP_HEIGHT_DP * density).toInt(),
                goalMl,
                totalMl
            )
        )
        views.setTextViewText(R.id.widgetTotal, totalMl.toString())
        views.setTextViewText(R.id.widgetGoal, context.getString(R.string.widget_goal, goalMl))
        views.setTextViewText(R.id.widgetHint, hintText(context, goalMl, totalMl))

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
