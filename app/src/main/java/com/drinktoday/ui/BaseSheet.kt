package com.drinktoday.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.drinktoday.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 底部弹层基类。
 *
 * 实现方式：窗口铺满全屏且透明，卡片贴在窗口底部；这样不依赖 window gravity，
 * 在各厂商 ROM 上的定位都稳定。内容高度自适应，超过屏幕 90% 时卡片内部滚动。
 *
 * 交互：按住顶部小白条可跟手下滑关闭，未过阈值则回弹。
 */
abstract class BaseSheet(context: Context, layoutRes: Int) : Dialog(context, R.style.Theme_DrinkToday_Sheet) {

    protected val root: View = LayoutInflater.from(context).inflate(layoutRes, null)
    protected val card: View = root.findViewById(R.id.sheetCard)
    private val scroll: ScrollView = root.findViewById(R.id.sheetScroll)
    private var basePaddingBottom = -1

    private var dragStartY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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
        setupDragToDismiss()
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

    // ------------------------------------------------------------ 拖动关闭

    @Suppress("ClickableViewAccessibility")
    private fun setupDragToDismiss() {
        val handle = root.findViewById<View>(R.id.dragHandle) ?: return
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scroll.animate().cancel()
                    dragStartY = event.rawY
                    dragging = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    scroll.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dy = event.rawY - dragStartY
                    if (!dragging && abs(dy) > touchSlop) {
                        dragging = true
                        scroll.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        // 向下跟手；向上给一点阻尼，避免整块被拉走
                        scroll.translationY = if (dy >= 0f) dy else dy * 0.18f
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val tracker = velocityTracker
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val velocityY = tracker?.yVelocity ?: 0f
                    tracker?.recycle()
                    velocityTracker = null

                    val offset = scroll.translationY
                    val threshold = (card.height * 0.28f).coerceAtLeast(dp(88f))
                    if (dragging && (offset > threshold || velocityY > 900f)) {
                        slideOut()
                    } else if (dragging) {
                        springBack()
                    }
                    dragging = false
                }
            }
            true
        }
    }

    private fun springBack() {
        scroll.animate()
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()
    }

    /** 继续滑出屏幕后关闭；关闭时禁用窗口动画，避免二次滑动。 */
    private fun slideOut() {
        scroll.animate()
            .translationY(scroll.height.toFloat())
            .setDuration(200L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                window?.apply {
                    setWindowAnimations(0)
                    attributes = attributes.apply { windowAnimations = 0 }
                }
                dismiss()
            }
            .start()
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
