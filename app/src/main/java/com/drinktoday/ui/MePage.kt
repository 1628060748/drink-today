package com.drinktoday.ui

import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drinktoday.R
import com.drinktoday.data.DrinkStore
import com.drinktoday.data.WebDavClient
import com.drinktoday.data.WebDavConfig
import com.drinktoday.databinding.PageMeBinding
import com.drinktoday.util.dpInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的」页：设置、数据导入导出、WebDAV 同步与关于。
 *
 * 行式布局在代码里生成，避免 8 组几乎相同的 XML 重复。
 */
class MePage(
    private val activity: AppCompatActivity,
    private val binding: PageMeBinding,
    private val store: DrinkStore,
    private val onDataChanged: () -> Unit
) {

    private val webDav = WebDavConfig.get(activity)
    private var syncing = false

    private lateinit var tvGoalValue: TextView
    private lateinit var tvQuickValue: TextView
    private lateinit var tvWebDavValue: TextView

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(::exportTo) }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::importFrom) }

    init {
        buildRows()
        refresh()
    }

    // ------------------------------------------------------------ 行构建

    private fun buildRows() {
        tvGoalValue = addRow(
            binding.cardSettings,
            activity.getString(R.string.me_goal),
            null,
            ""
        ) { openSettings() }

        tvQuickValue = addRow(
            binding.cardSettings,
            activity.getString(R.string.me_quick),
            null,
            ""
        ) { openSettings() }

        addRow(
            binding.cardData,
            activity.getString(R.string.me_export),
            activity.getString(R.string.me_export_sub),
            null
        ) { exportLauncher.launch(exportFileName()) }

        addRow(
            binding.cardData,
            activity.getString(R.string.me_import),
            activity.getString(R.string.me_import_sub),
            null
        ) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

        tvWebDavValue = addRow(
            binding.cardSync,
            activity.getString(R.string.me_webdav),
            activity.getString(R.string.me_webdav_sub),
            ""
        ) { openWebDav() }

        addRow(
            binding.cardSync,
            activity.getString(R.string.me_sync),
            activity.getString(R.string.me_sync_sub),
            null
        ) { startSync() }

        addRow(
            binding.cardAbout,
            activity.getString(R.string.me_version),
            null,
            appVersion()
        ) { }

        addRow(
            binding.cardAbout,
            activity.getString(R.string.me_github),
            activity.getString(R.string.me_github_sub),
            null
        ) { openGithub() }
    }

    private fun addRow(
        card: LinearLayout,
        title: String,
        subtitle: String?,
        value: String?,
        onClick: (() -> Unit)?
    ): TextView {
        if (card.childCount > 0) {
            card.addView(
                View(activity).apply { setBackgroundColor(Palette.DIVIDER) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpInt(1f))
            )
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpInt(14f), 0, dpInt(14f))
        }

        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            TextView(activity).apply {
                text = title
                textSize = 15f
                setTextColor(Palette.TEXT)
                includeFontPadding = false
            }
        )
        if (!subtitle.isNullOrEmpty()) {
            texts.addView(
                TextView(activity).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(Palette.TEXT_3)
                    includeFontPadding = false
                    setPadding(0, dpInt(3f), 0, 0)
                }
            )
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val valueView = TextView(activity).apply {
            text = value.orEmpty()
            textSize = 13f
            setTextColor(Palette.TEXT_2)
            includeFontPadding = false
            gravity = Gravity.END
            maxLines = 1
        }
        row.addView(
            valueView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpInt(10f) }
        )

        if (onClick != null) {
            row.addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(Palette.TEXT_3)
                },
                LinearLayout.LayoutParams(dpInt(16f), dpInt(16f)).apply { marginStart = dpInt(8f) }
            )
            row.isClickable = true
            row.setOnClickListener { onClick() }
            applyRipple(row)
        }

        card.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return valueView
    }

    private fun applyRipple(view: View) {
        val out = TypedValue()
        if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            view.foreground = ContextCompat.getDrawable(activity, out.resourceId)
        }
    }

    // ------------------------------------------------------------ 刷新

    fun refresh() {
        tvGoalValue.text = activity.getString(R.string.stat_value_ml, store.goalMl)
        tvQuickValue.text = store.quickAmounts.joinToString(" / ")
        tvWebDavValue.text = activity.getString(
            if (webDav.isConfigured) R.string.me_webdav_set else R.string.me_webdav_unset
        )
        renderSyncState()
    }

    private fun renderSyncState() {
        binding.tvSyncState.text = when {
            syncing -> activity.getString(R.string.me_syncing)
            webDav.lastSyncAt <= 0L -> activity.getString(R.string.me_never_synced)
            else -> activity.getString(
                R.string.me_last_sync,
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(webDav.lastSyncAt))
            )
        }
    }

    // ------------------------------------------------------------ 设置 / WebDAV

    private fun openSettings() {
        SettingsSheet(activity, store) {
            refresh()
            onDataChanged()
        }.show()
    }

    private fun openWebDav() {
        WebDavSheet(activity, webDav) { refresh() }.show()
    }

    // ------------------------------------------------------------ 导入 / 导出

    private fun exportFileName(): String =
        "drink-today-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"

    private fun exportTo(uri: Uri) {
        // Store 非线程安全：JSON 在主线程生成，只有文件写入放到后台
        val json = store.exportJson()
        val count = store.recordCount()
        Thread {
            try {
                activity.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
                ui { toast(activity.getString(R.string.export_success, count)) }
            } catch (e: Exception) {
                ui { toast(activity.getString(R.string.export_failed, e.message.orEmpty())) }
            }
        }.start()
    }

    private fun importFrom(uri: Uri) {
        Thread {
            try {
                val text = activity.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text.isNullOrBlank()) {
                    ui { toast(activity.getString(R.string.import_failed, "文件为空")) }
                    return@Thread
                }
                ui {
                    // 合并放在主线程，避免与 UI 同时读写 Store
                    val result = store.importJson(text, applySettings = true)
                    val message = if (result.addedRecords > 0) {
                        activity.getString(R.string.import_success, result.addedRecords)
                    } else {
                        activity.getString(R.string.import_empty)
                    }
                    toast(message)
                    refresh()
                    onDataChanged()
                }
            } catch (e: Exception) {
                ui { toast(activity.getString(R.string.import_failed, e.message.orEmpty())) }
            }
        }.start()
    }

    // ------------------------------------------------------------ 同步

    private fun startSync() {
        if (syncing) return
        val endpoint = webDav.endpoint()
        if (!endpoint.isConfigured) {
            toast(activity.getString(R.string.sync_need_config))
            openWebDav()
            return
        }

        syncing = true
        renderSyncState()
        Thread {
            val remote = try {
                WebDavClient.download(endpoint)
            } catch (e: Exception) {
                ui { finishSyncWithError(e) }
                return@Thread
            }

            ui {
                // 云端只合并记录，不覆盖本机的目标与快捷水量
                val added = if (remote.isNullOrBlank()) {
                    0
                } else {
                    store.importJson(remote, applySettings = false).addedRecords
                }
                val merged = store.exportJson()
                refresh()
                onDataChanged()

                Thread {
                    try {
                        WebDavClient.upload(endpoint, merged)
                        webDav.lastSyncAt = System.currentTimeMillis()
                        ui {
                            syncing = false
                            refresh()
                            toast(activity.getString(R.string.sync_success, added))
                        }
                    } catch (e: Exception) {
                        ui { finishSyncWithError(e) }
                    }
                }.start()
            }
        }.start()
    }

    private fun finishSyncWithError(e: Exception) {
        syncing = false
        renderSyncState()
        toast(activity.getString(R.string.sync_failed, e.message.orEmpty()))
    }

    // ------------------------------------------------------------ 其它

    private fun appVersion(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    private fun openGithub() {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/1628060748/drink-today"))
            )
        } catch (e: Exception) {
            toast(activity.getString(R.string.me_no_browser))
        }
    }

    private fun toast(message: String) {
        if (activity.isFinishing) return
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun ui(block: () -> Unit) {
        activity.runOnUiThread {
            if (!activity.isFinishing) block()
        }
    }
}
