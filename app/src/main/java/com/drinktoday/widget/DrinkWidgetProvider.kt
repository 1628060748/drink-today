package com.drinktoday.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.util.DateX

/**
 * 桌面小部件：展示今日进度，并提供 4 个快捷打卡按钮。
 *
 * 数据读取与写入都走同一个 [DrinkStore]（同进程单例），因此小部件与 App 内容实时一致。
 */
class DrinkWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_QUICK_ADD -> {
                handleQuickAdd(context, intent.getIntExtra(EXTRA_AMOUNT, 0))
                return
            }

            // 跨天、改时间或换时区后立即刷新，避免小部件停在前一天
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                refreshAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun handleQuickAdd(context: Context, amountMl: Int) {
        if (amountMl <= 0) return
        DrinkStore.get(context).add(DateX.todayEpochDay(), System.currentTimeMillis(), amountMl)
        Toast.makeText(
            context,
            context.getString(R.string.added_toast, amountMl),
            Toast.LENGTH_SHORT
        ).show()
        refreshAll(context)
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.drinktoday.action.WIDGET_QUICK_ADD"
        const val EXTRA_AMOUNT = "extra_amount"

        /** App 内数据变化后调用，让桌面立即同步。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DrinkWidgetProvider::class.java)
            )
            for (id in ids) {
                updateOne(context, manager, id)
            }
        }

        /** 每个实例的尺寸可能不同（用户可拉伸），按各自 options 渲染水杯。 */
        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val store = DrinkStore.get(context)
            manager.updateAppWidget(
                widgetId,
                WidgetRenderer.build(
                    context = context,
                    options = manager.getAppWidgetOptions(widgetId),
                    goalMl = store.goalMl,
                    totalMl = store.total(DateX.todayEpochDay()),
                    amounts = store.quickAmounts
                )
            )
        }
    }
}
