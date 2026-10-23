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
import com.drinktoday.ui.Palette
import com.drinktoday.ui.TodayPage
import com.drinktoday.util.dpInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var todayPage: TodayPage
    private lateinit var historyPage: HistoryPage
    private var pageIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val store = DrinkStore.get(this)
        todayPage = TodayPage(this, binding.pageToday, store)
        historyPage = HistoryPage(this, binding.pageHistory, store)

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
        if (pageIndex == 1) historyPage.refresh(animate = false)
    }

    override fun onPause() {
        super.onPause()
        todayPage.setPaused(true)
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
        binding.pageHistory.root.visibility = View.GONE
    }

    private fun selectPage(index: Int, animate: Boolean = true) {
        if (index == pageIndex) return
        val show = if (index == 0) binding.pageToday.root else binding.pageHistory.root
        val hide = if (index == 0) binding.pageHistory.root else binding.pageToday.root
        pageIndex = index

        show.visibility = View.VISIBLE
        if (animate) {
            show.alpha = 0f
            show.translationY = dpInt(10f).toFloat()
            show.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(EMPHASIS)
                .start()
            hide.animate()
                .alpha(0f)
                .translationY(-dpInt(6f).toFloat())
                .setDuration(150L)
                .withEndAction {
                    hide.visibility = View.GONE
                    hide.alpha = 1f
                    hide.translationY = 0f
                }
                .start()
        } else {
            show.alpha = 1f
            show.translationY = 0f
            hide.visibility = View.GONE
            hide.alpha = 1f
            hide.translationY = 0f
        }

        todayPage.setPaused(index != 0)
        if (index == 0) todayPage.refresh(animate = false) else historyPage.refresh(animate = true)
        updateNavIndicator(index, animate)
    }

    private fun updateNavIndicator(index: Int, animate: Boolean) {
        val width = binding.bottomNav.width
        if (width == 0) {
            binding.bottomNav.post { updateNavIndicator(index, false) }
            return
        }
        val target = width * (index + 0.5f) / 2f - dpInt(31f)
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
        binding.navTodayIcon.setColorFilter(if (index == 0) active else idle)
        binding.navHistoryIcon.setColorFilter(if (index == 1) active else idle)
        binding.navTodayLabel.setTextColor(if (index == 0) active else idle)
        binding.navHistoryLabel.setTextColor(if (index == 1) active else idle)
        binding.navTodayLabel.typeface = typeface(index == 0)
        binding.navHistoryLabel.typeface = typeface(index == 1)
    }

    private fun typeface(active: Boolean): Typeface =
        Typeface.create("sans-serif", if (active) Typeface.BOLD else Typeface.NORMAL)

    private companion object {
        const val NAV_BAR_HEIGHT_DP = 64f
        val EMPHASIS = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    }
}
