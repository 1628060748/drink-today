package com.drinktoday.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.drinktoday.R
import kotlin.math.min

/**
 * 底部弹层基类：负责窗口定位、圆角卡片尺寸与导航栏内边距。
 * 内容高度自适应，最高占屏幕 92%，超出时内部 ScrollView 滚动。
 */
abstract class BaseSheet(context: Context, layoutRes: Int) : Dialog(context, R.style.Theme_DrinkToday_Sheet) {

    protected val root: View = LayoutInflater.from(context).inflate(layoutRes, null)
    private var basePaddingBottom = -1

    init {
        setContentView(root)
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            setWindowAnimations(R.style.Anim_DrinkToday_Sheet)
        }
        setCanceledOnTouchOutside(true)
    }

    override fun onStart() {
        super.onStart()
        applyNavigationInset()
        resizeToContent()
    }

    /** 内容发生变化（如列表增删）后调用，保持弹层紧贴内容。 */
    protected fun resizeToContent() {
        val win = window ?: return
        val metrics = context.resources.displayMetrics
        val maxHeight = (metrics.heightPixels * 0.92f).toInt()
        val card = cardView()
        card.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, min(card.measuredHeight, maxHeight))
    }

    private fun cardView(): View {
        val container = root as? ViewGroup
        return if (container != null && container.childCount > 0) container.getChildAt(0) else root
    }

    private fun applyNavigationInset() {
        val inset = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val card = cardView()
        if (basePaddingBottom < 0) basePaddingBottom = card.paddingBottom
        card.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, basePaddingBottom + inset)
    }
}
