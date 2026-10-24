package com.drinktoday.data

import android.content.Context

/**
 * WebDAV 配置（服务器地址 / 账号 / 应用密码）。
 *
 * 密码以明文保存在本应用的私有 SharedPreferences 中，仅本进程可读；
 * 界面上提示用户使用「应用专用密码」，避免暴露主账号密码。
 */
/** 一次 WebDAV 请求所需的全部信息，便于用「未保存的输入」做连通性测试。 */
data class WebDavEndpoint(
    val server: String,
    val user: String,
    val password: String,
    /** 服务器地址下的子文件夹；为空表示直接写入服务器地址 */
    val folder: String = WebDavConfig.DEFAULT_FOLDER
) {
    val isConfigured: Boolean
        get() = server.isNotBlank() && user.isNotBlank()

    fun endpoint(): WebDavEndpoint = WebDavEndpoint(server, user, password, folder)
}

class WebDavConfig private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim()).apply()

    var user: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var folder: String
        get() = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER).orEmpty()
        set(value) = prefs.edit().putString(KEY_FOLDER, value.trim().trim('/')).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** 最后一次云端操作："backup" 或 "restore" */
    var lastAction: String
        get() = prefs.getString(KEY_LAST_ACTION, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_ACTION, value).apply()

    val isConfigured: Boolean
        get() = server.isNotBlank() && user.isNotBlank()

    fun endpoint(): WebDavEndpoint = WebDavEndpoint(server, user, password, folder)

    companion object {
        /** 远端固定文件名，避免用户配置目录后还要填文件名。 */
        const val FILE_NAME = "drink-today-backup.json"

        /** 默认子文件夹 */
        const val DEFAULT_FOLDER = "drink-today"

        private const val PREF_NAME = "webdav"
        private const val KEY_SERVER = "server"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SYNC = "lastSync"
        private const val KEY_LAST_ACTION = "lastAction"
        private const val KEY_FOLDER = "folder"

        @Volatile
        private var instance: WebDavConfig? = null

        fun get(context: Context): WebDavConfig =
            instance ?: synchronized(this) {
                instance ?: WebDavConfig(context).also { instance = it }
            }
    }
}
