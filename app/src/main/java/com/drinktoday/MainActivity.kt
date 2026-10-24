package com.drinktoday

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.drinktoday.data.DrinkStore
import com.drinktoday.databinding.ActivityMainBinding
import com.drinktoday.ui.HistoryPage
import com.drinktoday.ui.MePage
import com.drinktoday.ui.Palette
import com.drinktoday.ui.TodayPage
import com.drinktoday.util.dpInt
import com.drinktoday.widget.DrinkWidgetProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var todayPage: TodayPage
    private lateinit var historyPage: HistoryPage
    private lateinit var mePage: MePage
    private var pageIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val store = DrinkStore.get(this)
        todayPage = TodayPage(this, binding.pageToday, store)
        historyPage = HistoryPage(this, binding.pageHistory, store)
        mePage = MePage(this, binding.pageMe, store) { onDataChanged() }

        applyWindowInsets()
        setupNavigation()
        selectPage(0, animate = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageIndex != 0) {
                    selectPage(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        todayPage.onResume()
        todayPage.setPaused(pageIndex != 0)
        when (pageIndex) {
            1 -> historyPage.refresh(animate = false)
            2 -> mePage.refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        todayPage.setPaused(true)
    }

    override fun onStop() {
        super.onStop()
        // 离开 App 时同步桌面小部件，避免桌面数据滞后
        DrinkWidgetProvider.refreshAll(applicationContext)
    }

    /** 导入、同步、改设置后统一刷新其它页面。 */
    private fun onDataChanged() {
        todayPage.refresh(animate = false)
        historyPage.refresh(animate = false)
    }

    // ------------------------------------------------------------ 系统栏

    private fun applyWindowInsets() {
        val navHeight = dpInt(NAV_BAR_HEIGHT_DP)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, bars.top, 0, 0)
            binding.bottomNav.layoutParams = binding.bottomNav.layoutParams.apply {
                height = navHeight + bars.bottom
            }
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom)
            binding.pageContainer.setPadding(0, 0, 0, navHeight + bars.bottom)
            insets
        }
    }

    // ------------------------------------------------------------ 底部导航

    private fun setupNavigation() {
        binding.navToday.setOnClickListener { selectPage(0) }
        binding.navHistory.setOnClickListener { selectPage(1) }
        binding.navMe.setOnClickListener { selectPage(2) }
        binding.pageHistory.root.visibility = View.GONE
        binding.pageMe.root.visibility = View.GONE
    }

    private fun pageRoots(): Array<View> = arrayOf(
        binding.pageToday.root,
        binding.pageHistory.root,
        binding.pageMe.root
    )

    private fun selectPage(index: Int, animate: Boolean = true) {
        if (index == pageIndex) return
        val pages = pageRoots()

        for (i in pages.indices) {
            val view = pages[i]
            if (i == index) {
                view.visibility = View.VISIBLE
                if (animate) {
                    view.alpha = 0f
                    view.translationY = dpInt(10f).toFloat()
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(260L)
                        .setInterpolator(EMPHASIS)
                        .start()
                } else {
                    view.alpha = 1f
                    view.translationY = 0f
                }
            } else if (view.visibility == View.VISIBLE) {
                if (animate) {
                    view.animate()
                        .alpha(0f)
                        .translationY(-dpInt(6f).toFloat())
                        .setDuration(150L)
                        .withEndAction {
                            view.visibility = View.GONE
                            view.alpha = 1f
                            view.translationY = 0f
                        }
                        .start()
                } else {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    view.translationY = 0f
                }
            }
        }

        pageIndex = index
        todayPage.setPaused(index != 0)
        when (index) {
            0 -> todayPage.refresh(animate = false)
            1 -> historyPage.refresh(animate = true)
            2 -> mePage.refresh()
        }
        updateNavIndicator(index, animate)
    }

    private fun updateNavIndicator(index: Int, animate: Boolean) {
        val width = binding.bottomNav.width
        if (width == 0) {
            binding.bottomNav.post { updateNavIndicator(index, false) }
            return
        }
        val slot = width / 3f
        val target = slot * (index + 0.5f) - dpInt(31f)
        if (animate) {
            ValueAnimator.ofFloat(binding.navIndicator.translationX, target).apply {
                duration = 280L
                interpolator = EMPHASIS
                addUpdateListener { binding.navIndicator.translationX = it.animatedValue as Float }
                start()
            }
        } else {
            binding.navIndicator.translationX = target
        }

        val active = Palette.ACCENT
        val idle = Palette.TEXT_3
        val icons = arrayOf(binding.navTodayIcon, binding.navHistoryIcon, binding.navMeIcon)
        val labels = arrayOf(binding.navTodayLabel, binding.navHistoryLabel, binding.navMeLabel)
        for (i in icons.indices) {
            icons[i].setColorFilter(if (i == index) active else idle)
            labels[i].setTextColor(if (i == index) active else idle)
            labels[i].typeface = typeface(i == index)
        }
    }

    private fun typeface(active: Boolean): Typeface =
        Typeface.create("sans-serif", if (active) Typeface.BOLD else Typeface.NORMAL)

    private companion object {
        const val NAV_BAR_HEIGHT_DP = 64f
        val EMPHASIS = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    }
}
