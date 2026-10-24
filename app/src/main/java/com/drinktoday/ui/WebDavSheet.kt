package com.drinktoday.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.drinktoday.R
import com.drinktoday.data.WebDavClient
import com.drinktoday.data.WebDavConfig
import com.drinktoday.data.WebDavEndpoint

/**
 * WebDAV 账号配置：服务器地址、账号、应用密码，支持保存前测试连通性。
 */
@Suppress("DEPRECATION") // 全屏弹层用 SOFT_INPUT_ADJUST_RESIZE 让输入框避开键盘
class WebDavSheet(
    context: Context,
    private val config: WebDavConfig,
    private val onSaved: () -> Unit
) : BaseSheet(context, R.layout.sheet_webdav) {

    private val etServer: EditText = root.findViewById(R.id.etServer)
    private val etUser: EditText = root.findViewById(R.id.etUser)
    private val etPassword: EditText = root.findViewById(R.id.etPassword)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        etServer.setText(config.server)
        etUser.setText(config.user)
        etPassword.setText(config.password)
        root.findViewById<TextView>(R.id.tvFile).text =
            context.getString(R.string.webdav_file, WebDavConfig.FILE_NAME)

        root.findViewById<View>(R.id.btnTest).apply {
            setOnClickListener { testConnection() }
            TouchFx.press(this, 0.97f)
        }
        root.findViewById<View>(R.id.btnSave).apply {
            setOnClickListener { save() }
            TouchFx.press(this, 0.97f)
        }
    }

    private fun currentEndpoint(): WebDavEndpoint? {
        val server = etServer.text.toString().trim()
        val user = etUser.text.toString().trim()
        if (server.isEmpty() || user.isEmpty()) {
            toast(context.getString(R.string.webdav_server_empty))
            return null
        }
        if (!server.startsWith("https://")) {
            toast(context.getString(R.string.webdav_https_only))
            return null
        }
        return WebDavEndpoint(server, user, etPassword.text.toString())
    }

    private fun testConnection() {
        val endpoint = currentEndpoint() ?: return
        Thread {
            val message = try {
                WebDavClient.probe(endpoint)
                context.getString(R.string.webdav_test_ok)
            } catch (e: Exception) {
                context.getString(R.string.webdav_test_fail, e.message ?: "")
            }
            mainHandler.post { toast(message) }
        }.start()
    }

    private fun save() {
        val endpoint = currentEndpoint() ?: return
        config.server = endpoint.server
        config.user = endpoint.user
        config.password = endpoint.password
        onSaved()
        dismiss()
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
