package com.drinktoday.data

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端：只需要 GET / PUT 一个 JSON 文件。
 *
 * 用 HttpURLConnection 而不是 OkHttp，是为了不给这个 0.6 MB 的应用再加一个网络库；
 * 需求只有「下载一份、覆盖一份」，连接复用与拦截器收益不大。
 */
object WebDavClient {

    private const val TIMEOUT_MS = 15000

    fun fileUrl(server: String): String {
        val base = if (server.endsWith("/")) server else "$server/"
        return base + WebDavConfig.FILE_NAME
    }

    /** 返回远端内容；文件不存在返回 null。 */
    fun download(endpoint: WebDavEndpoint): String? {
        val (code, body) = execute(endpoint, "GET", null)
        return when {
            code in 200..299 -> body
            code == HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw failure(code)
        }
    }

    fun upload(endpoint: WebDavEndpoint, json: String) {
        val (code, _) = execute(endpoint, "PUT", json)
        if (code !in 200..299) throw failure(code)
    }

    /**
     * 连通性检查：用 GET 探测。
     * 404 说明认证通过但文件还不存在，同样视为成功。
     */
    fun probe(endpoint: WebDavEndpoint) {
        val (code, _) = execute(endpoint, "GET", null)
        if (code !in 200..299 && code != HttpURLConnection.HTTP_NOT_FOUND) throw failure(code)
    }

    private fun failure(code: Int): IOException = IOException(
        when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> "认证失败，请检查账号与密码"
            HttpURLConnection.HTTP_CONFLICT -> "远端目录不存在"
            else -> "HTTP $code"
        }
    )

    private fun execute(endpoint: WebDavEndpoint, method: String, body: String?): Pair<Int, String> {
        val url = try {
            URL(fileUrl(endpoint.server))
        } catch (e: Exception) {
            throw IOException("地址格式不正确")
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Authorization", basicAuth(endpoint.user, endpoint.password))
            setRequestProperty("User-Agent", "drink-today")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } catch (e: IOException) {
            throw IOException(e.message ?: "网络异常")
        } finally {
            connection.disconnect()
        }
    }

    private fun basicAuth(user: String, password: String): String {
        val raw = "$user:$password"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
