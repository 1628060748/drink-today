package com.drinktoday.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.drinktoday.R
import kotlin.math.min

/**
 * 底部弹层基类。
 *
 * 实现方式：窗口铺满全屏且透明，卡片贴在窗口底部；这样不依赖 window gravity，
 * 在各厂商 ROM 上的定位都稳定。内容高度自适应，超过屏幕 90% 时卡片内部滚动。
 */
abstract class BaseSheet(context: Context, layoutRes: Int) : Dialog(context, R.style.Theme_DrinkToday_Sheet) {

    protected val root: View = LayoutInflater.from(context).inflate(layoutRes, null)
    protected val card: View = root.findViewById(R.id.sheetCard)
    private val scroll: ScrollView = root.findViewById(R.id.sheetScroll)
    private var basePaddingBottom = -1

    init {
        setContentView(root)
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            setWindowAnimations(R.style.Anim_DrinkToday_Sheet)
        }
        setCanceledOnTouchOutside(true)
        // 点击卡片外的空白区域收起
        root.setOnClickListener { dismiss() }
        card.isClickable = true
        scroll.isClickable = true
    }

    override fun onStart() {
        super.onStart()
        applyNavigationInset()
        resizeToContent()
    }

    /** 内容变化（列表增删等）后重新计算高度。 */
    protected fun resizeToContent() {
        val metrics = context.resources.displayMetrics
        val maxHeight = (metrics.heightPixels * 0.90f).toInt()
        card.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val target = min(card.measuredHeight, maxHeight)
        val params = scroll.layoutParams ?: return
        if (params.height != target) {
            params.height = target
            scroll.layoutParams = params
        }
    }

    private fun applyNavigationInset() {
        val inset = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
            ?: root.rootWindowInsets?.let { insets ->
                WindowInsetsCompat.toWindowInsetsCompat(insets)
                    .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            ?: 0
        if (basePaddingBottom < 0) basePaddingBottom = card.paddingBottom
        card.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, basePaddingBottom + inset)
    }
}
