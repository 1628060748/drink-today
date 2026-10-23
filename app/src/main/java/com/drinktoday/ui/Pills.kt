package com.drinktoday.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.drinktoday.R
import com.drinktoday.util.dp

/** 统一样式的胶囊标签，避免在多处重复构造。 */
object Pills {

    fun create(context: Context, text: CharSequence, selected: Boolean = false): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(16f).toInt(), dp(9f).toInt(), dp(16f).toInt(), dp(9f).toInt())
        view.isClickable = true
        view.includeFontPadding = false
        setSelectedState(context, view, selected)
        return view
    }

    fun setSelectedState(context: Context, view: TextView, selected: Boolean) {
        view.background = ContextCompat.getDrawable(
            context,
            if (selected) R.drawable.bg_pill_soft else R.drawable.bg_pill_gray
        )
        view.setTextColor(if (selected) Palette.ACCENT else Palette.TEXT_2)
        view.typeface = Typeface.create(
            "sans-serif",
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
    }
}
