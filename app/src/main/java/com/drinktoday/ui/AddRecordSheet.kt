package com.drinktoday.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.drinktoday.R
import com.drinktoday.data.DrinkRecord
import com.drinktoday.data.DrinkStore
import com.drinktoday.util.DateX
import com.drinktoday.util.dpInt
import java.time.LocalTime
import java.util.Locale

/**
 * 补录弹层：选择日期、时间与水量，一次性写入记录。
 */
class AddRecordSheet(
    context: Context,
    private val store: DrinkStore,
    private val onSaved: (DrinkRecord) -> Unit
) : BaseSheet(context, R.layout.sheet_add_record) {

    private val hourPicker: WheelPicker = root.findViewById(R.id.hourPicker)
    private val minutePicker: WheelPicker = root.findViewById(R.id.minutePicker)
    private val tvAmount: TextView = root.findViewById(R.id.tvAmount)
    private val dateRow: LinearLayout = root.findViewById(R.id.dateRow)
    private val amountRow: LinearLayout = root.findViewById(R.id.amountRow)

    private var selectedDay: Long = DateX.todayEpochDay()
    private var amount: Int = ((store.lastAmountMl / STEP) * STEP).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
    private val dateChips = LinkedHashMap<Long, TextView>()
    private val amountChips = LinkedHashMap<Int, TextView>()

    init {
        val now = LocalTime.now()
        hourPicker.setItems((0..23).map { String.format(Locale.US, "%02d", it) }, now.hour)
        minutePicker.setItems((0..59).map { String.format(Locale.US, "%02d", it) }, now.minute)

        buildDateChips()
        buildAmountChips()
        renderAmount()

        root.findViewById<View>(R.id.btnMinus).apply {
            setOnClickListener { changeAmount(-STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnPlus).apply {
            setOnClickListener { changeAmount(STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnSave).apply {
            setOnClickListener { save() }
            TouchFx.press(this, 0.97f)
        }
    }

    private fun buildDateChips() {
        val today = DateX.todayEpochDay()
        for (offset in 0..6) {
            val day = today - offset
            val chip = Pills.create(context, DateX.relativeDay(day, today), day == selectedDay)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpInt(8f) }
            chip.setOnClickListener { selectDay(day) }
            TouchFx.press(chip, 0.93f)
            dateRow.addView(chip, lp)
            dateChips[day] = chip
        }
    }

    private fun buildAmountChips() {
        val presets = intArrayOf(100, 150, 200, 300, 500)
        for (preset in presets) {
            val chip = Pills.create(context, "$preset", preset == amount)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpInt(4f); marginEnd = dpInt(4f) }
            chip.setOnClickListener { setAmount(preset) }
            TouchFx.press(chip, 0.93f)
            amountRow.addView(chip, lp)
            amountChips[preset] = chip
        }
    }

    private fun selectDay(day: Long) {
        if (day == selectedDay) return
        selectedDay = day
        dateChips.forEach { (key, chip) -> Pills.setSelectedState(context, chip, key == day) }
    }

    private fun setAmount(value: Int) {
        val clamped = value.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        if (clamped == amount) return
        amount = clamped
        renderAmount()
    }

    private fun changeAmount(delta: Int) = setAmount(amount + delta)

    private fun renderAmount() {
        tvAmount.text = amount.toString()
        amountChips.forEach { (key, chip) -> Pills.setSelectedState(context, chip, key == amount) }
    }

    private fun save() {
        var timeMillis = DateX.timeMillisOf(
            selectedDay,
            hourPicker.selectedIndex(),
            minutePicker.selectedIndex()
        )
        val now = System.currentTimeMillis()
        if (timeMillis > now) {
            timeMillis = now
            Toast.makeText(context, R.string.time_adjusted, Toast.LENGTH_SHORT).show()
        }
        val record = store.add(selectedDay, timeMillis, amount)
        onSaved(record)
        dismiss()
    }

    private companion object {
        const val STEP = 50
        const val MIN_AMOUNT = 50
        const val MAX_AMOUNT = 2000
    }
}
