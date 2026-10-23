package com.drinktoday.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.util.dpInt

/**
 * 每日目标设置弹层：步进 + 预设，保存后立即刷新首页水位。
 */
class GoalSheet(
    context: Context,
    private val store: DrinkStore,
    private val onSaved: (Int) -> Unit
) : BaseSheet(context, R.layout.sheet_goal) {

    private val tvGoal: TextView = root.findViewById(R.id.tvGoal)
    private val presetRow: LinearLayout = root.findViewById(R.id.presetRow)
    private val presets = intArrayOf(1200, 1500, 2000, 2500, 3000)
    private val presetChips = ArrayList<Pair<Int, TextView>>()

    private var value: Int = store.goalMl

    init {
        for (preset in presets) {
            val chip = Pills.create(context, "$preset", preset == value)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpInt(4f); marginEnd = dpInt(4f) }
            chip.setOnClickListener { update(preset) }
            TouchFx.press(chip, 0.93f)
            presetRow.addView(chip, lp)
            presetChips.add(preset to chip)
        }
        render()

        root.findViewById<View>(R.id.btnMinus).apply {
            setOnClickListener { update(value - STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnPlus).apply {
            setOnClickListener { update(value + STEP) }
            TouchFx.press(this)
        }
        root.findViewById<View>(R.id.btnSave).apply {
            setOnClickListener {
                store.setGoal(value)
                onSaved(value)
                dismiss()
            }
            TouchFx.press(this, 0.97f)
        }
    }

    private fun update(newValue: Int) {
        val clamped = newValue.coerceIn(DrinkStore.MIN_GOAL, DrinkStore.MAX_GOAL)
        if (clamped == value) return
        value = clamped
        render()
        tvGoal.animate()
            .scaleX(1.04f).scaleY(1.04f)
            .setDuration(90L)
            .withEndAction {
                tvGoal.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }
            .start()
    }

    private fun render() {
        tvGoal.text = value.toString()
        for ((preset, chip) in presetChips) {
            Pills.setSelectedState(context, chip, preset == value)
        }
    }

    private companion object {
        const val STEP = 100
    }
}
