package com.drinktoday.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.util.dpInt

/**
 * 设置弹层：每日目标 + 4 个快捷按钮的水量。
 *
 * 快捷水量做成可配置项而不是写死，因为每个人的杯子容量差异很大。
 */
class SettingsSheet(
    context: Context,
    private val store: DrinkStore,
    private val onSaved: () -> Unit
) : BaseSheet(context, R.layout.sheet_settings) {

    private val tvGoal: TextView = root.findViewById(R.id.tvGoal)
    private val presetRow: LinearLayout = root.findViewById(R.id.presetRow)
    private val quickRows: LinearLayout = root.findViewById(R.id.quickRows)

    private var goalValue: Int = store.goalMl
    private val quickValues: IntArray = store.quickAmounts.copyOf()
    private val goalPresets = intArrayOf(1200, 1500, 2000, 2500, 3000)
    private val presetChips = ArrayList<Pair<Int, TextView>>()
    private val quickValueViews = ArrayList<TextView>()

    init {
        buildGoalPresets()
        buildQuickRows()
        renderGoal()

        root.findViewById<View>(R.id.btnGoalMinus).apply {
            setOnClickListener { updateGoal(goalValue - GOAL_STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnGoalPlus).apply {
            setOnClickListener { updateGoal(goalValue + GOAL_STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnSave).apply {
            setOnClickListener { save() }
            TouchFx.press(this, 0.97f)
        }
    }

    // ------------------------------------------------------------ 每日目标

    private fun buildGoalPresets() {
        for (preset in goalPresets) {
            // 5 个预设要在一行内放下，用更小的横向内边距
            val chip = Pills.create(context, preset.toString(), preset == goalValue, 11f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpInt(4f); marginEnd = dpInt(4f) }
            chip.setOnClickListener { updateGoal(preset) }
            TouchFx.press(chip, 0.93f)
            presetRow.addView(chip, lp)
            presetChips.add(preset to chip)
        }
    }

    private fun updateGoal(newValue: Int) {
        val clamped = newValue.coerceIn(DrinkStore.MIN_GOAL, DrinkStore.MAX_GOAL)
        if (clamped == goalValue) return
        goalValue = clamped
        renderGoal()
        tvGoal.animate()
            .scaleX(1.04f).scaleY(1.04f)
            .setDuration(90L)
            .withEndAction { tvGoal.animate().scaleX(1f).scaleY(1f).setDuration(140L).start() }
            .start()
    }

    private fun renderGoal() {
        tvGoal.text = goalValue.toString()
        for ((preset, chip) in presetChips) {
            Pills.setSelectedState(context, chip, preset == goalValue)
        }
    }

    // ------------------------------------------------------------ 快捷水量

    private fun buildQuickRows() {
        for (index in quickValues.indices) {
            quickRows.addView(buildQuickRow(index))
        }
    }

    private fun buildQuickRow(index: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpInt(50f)
            )
        }

        val label = TextView(context).apply {
            text = context.getString(R.string.quick_slot, index + 1)
            textSize = 13f
            setTextColor(Palette.TEXT_2)
            includeFontPadding = false
        }
        row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(stepButton(R.drawable.ic_minus, R.string.quick_decrease) {
            changeQuickAmount(index, -QUICK_STEP)
        })

        val value = TextView(context).apply {
            text = context.getString(R.string.stat_value_ml, quickValues[index])
            textSize = 15f
            setTextColor(Palette.TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = dpInt(64f)
        }
        row.addView(
            value,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpInt(8f); marginEnd = dpInt(8f) }
        )
        quickValueViews.add(value)

        row.addView(stepButton(R.drawable.ic_plus, R.string.quick_increase) {
            changeQuickAmount(index, QUICK_STEP)
        })
        return row
    }

    private fun stepButton(iconRes: Int, descRes: Int, action: () -> Unit): ImageView {
        val button = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Palette.TEXT)
            background = ContextCompat.getDrawable(context, R.drawable.bg_circle_gray)
            contentDescription = context.getString(descRes)
            setPadding(dpInt(9f), dpInt(9f), dpInt(9f), dpInt(9f))
            setOnClickListener { action() }
        }
        TouchFx.press(button, 0.9f)
        button.layoutParams = LinearLayout.LayoutParams(dpInt(32f), dpInt(32f))
        return button
    }

    private fun changeQuickAmount(index: Int, delta: Int) {
        val next = (quickValues[index] + delta)
            .coerceIn(DrinkStore.MIN_QUICK_AMOUNT, DrinkStore.MAX_QUICK_AMOUNT)
        if (next == quickValues[index]) return
        quickValues[index] = next
        quickValueViews[index].text = context.getString(R.string.stat_value_ml, next)
    }

    // ------------------------------------------------------------ 保存

    private fun save() {
        store.setGoal(goalValue)
        for (index in quickValues.indices) {
            store.setQuickAmount(index, quickValues[index])
        }
        onSaved()
        dismiss()
    }

    private companion object {
        const val GOAL_STEP = 100
        const val QUICK_STEP = 50
    }
}
