package com.drinktoday.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.util.DateX
import com.drinktoday.util.dpInt

/**
 * 单日详情：列出当天每一条记录，可逐条删除。
 */
class DayDetailSheet(
    context: Context,
    private val store: DrinkStore,
    private val epochDay: Long
) : BaseSheet(context, R.layout.sheet_day_detail) {

    private val tvTitle: TextView = root.findViewById(R.id.tvTitle)
    private val tvTotal: TextView = root.findViewById(R.id.tvTotal)
    private val recordList: LinearLayout = root.findViewById(R.id.recordList)
    private val tvEmpty: TextView = root.findViewById(R.id.tvEmpty)

    private val timeFormat = android.text.format.DateFormat.getTimeFormat(context)

    init {
        val date = DateX.toDate(epochDay)
        tvTitle.text = context.getString(R.string.day_detail_title, date.monthValue, date.dayOfMonth)
        render()
    }

    private fun render() {
        recordList.removeAllViews()
        val records = store.records(epochDay)
        val total = store.total(epochDay)
        val goal = store.goalMl

        tvTotal.text = when {
            records.isEmpty() -> ""
            total >= goal -> context.getString(R.string.day_detail_total, total) +
                " · " + context.getString(R.string.day_detail_reached)
            else -> context.getString(R.string.day_detail_total, total) +
                " · " + context.getString(R.string.day_detail_not_reached, goal - total)
        }
        tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE

        // 存储按时间升序（便于汇总），展示改为最新在前
        records.asReversed().forEachIndexed { index, record ->
            if (index > 0) recordList.addView(divider())
            recordList.addView(buildRow(record.timeMillis, record.amountMl, record.id))
        }
        resizeToContent()
    }

    private fun buildRow(timeMillis: Long, amountMl: Int, id: Long): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpInt(14f), 0, dpInt(14f))
        }

        val time = TextView(context).apply {
            text = timeFormat.format(timeMillis)
            textSize = 15f
            setTextColor(Palette.TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        row.addView(
            time,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val amount = TextView(context).apply {
            text = context.getString(R.string.day_record_amount, amountMl)
            textSize = 14f
            setTextColor(Palette.ACCENT)
            includeFontPadding = false
        }
        row.addView(
            amount,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dpInt(16f) }
        )

        val delete = ImageView(context).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(Palette.TEXT_3)
            contentDescription = context.getString(R.string.delete_record)
            setPadding(dpInt(8f), dpInt(8f), dpInt(8f), dpInt(8f))
            setOnClickListener {
                store.remove(epochDay, id)
                Toast.makeText(context, R.string.record_deleted, Toast.LENGTH_SHORT).show()
                render()
            }
            TouchFx.press(this, 0.88f)
        }
        row.addView(delete, LinearLayout.LayoutParams(dpInt(32f), dpInt(32f)))

        return row
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Palette.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpInt(1f)
        )
    }
}
