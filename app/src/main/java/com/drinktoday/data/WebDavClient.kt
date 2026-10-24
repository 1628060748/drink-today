package com.drinktoday.data

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端：只需要 GET / PUT 一个 JSON 文件。
 *
 * 用 HttpURLConnection 而不是 OkHttp，是为了不给这个 0.7 MB 的应用再加一个网络库；
 * 需求只有「下载一份、覆盖一份」，连接复用与拦截器收益不大。
 */
object WebDavClient {

    private const val TIMEOUT_MS = 15000

    /**
     * 兼容两种填法：
     *  - 目录地址（以 / 结尾）：自动拼上固定文件名；
     *  - 完整文件地址（以 .json 结尾）：直接使用，避免拼成 xxx.json/xxx.json 导致 404。
     */
    fun fileUrl(server: String): String {
        val trimmed = server.trim()
        if (trimmed.endsWith(".json", ignoreCase = true)) return trimmed
        val base = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return base + WebDavConfig.FILE_NAME
    }

    /** 返回远端内容；文件不存在返回 null。 */
    fun download(endpoint: WebDavEndpoint): String? {
        val url = fileUrl(endpoint.server)
        val (code, body) = execute(endpoint, url, "GET", null)
        return when {
            code in 200..299 -> body
            code == HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw failure(code, url, body)
        }
    }

    fun upload(endpoint: WebDavEndpoint, json: String) {
        val url = fileUrl(endpoint.server)
        val (code, body) = execute(endpoint, url, "PUT", json)
        if (code !in 200..299) throw failure(code, url, body)
    }

    enum class ProbeResult { FILE_EXISTS, READY_TO_CREATE }

    /**
     * 连通性检查：
     *  1. GET 目标文件，200 说明云端已有备份；
     *  2. 404 时再 HEAD 一次所在目录——目录不存在才是真正的配置错误
     *     （此前只看文件 404，会把「路径填错」误判为「连接成功」）。
     */
    fun probe(endpoint: WebDavEndpoint): ProbeResult {
        val fileUrl = fileUrl(endpoint.server)
        val (code, body) = execute(endpoint, fileUrl, "GET", null)
        if (code in 200..299) return ProbeResult.FILE_EXISTS
        if (code != HttpURLConnection.HTTP_NOT_FOUND) throw failure(code, fileUrl, body)

        val dirUrl = collectionUrl(endpoint.server)
        val (dirCode, dirBody) = execute(endpoint, dirUrl, "HEAD", null)
        return when {
            dirCode in 200..299 || dirCode == 207 -> ProbeResult.READY_TO_CREATE
            dirCode == HttpURLConnection.HTTP_NOT_FOUND -> throw IOException(
                "目录不存在（404），请确认填写的是 WebDAV 目录\n" +
                    "坚果云示例：https://dav.jianguoyun.com/dav/"
            )
            dirCode == 405 || dirCode == 501 -> ProbeResult.READY_TO_CREATE
            else -> throw failure(dirCode, dirUrl, dirBody)
        }
    }

    /** 由文件地址反推所在目录；也兼容直接填完整文件地址的情况。 */
    fun collectionUrl(server: String): String {
        val trimmed = server.trim()
        if (trimmed.endsWith(".json", ignoreCase = true)) {
            val index = trimmed.lastIndexOf('/')
            return if (index > 0) trimmed.substring(0, index + 1) else trimmed
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun failure(code: Int, url: String, body: String): IOException {
        val detail = body.replace(Regex("\\s+"), " ").trim().take(120)
        val reason = when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN -> "认证失败，请检查账号或应用密码"

            HttpURLConnection.HTTP_NOT_FOUND ->
                "路径不存在（404）。请确认填写的是 WebDAV 目录，例如坚果云 https://dav.jianguoyun.com/dav/"

            405 -> "服务器不允许写入该地址（405）"
            409 -> "远端父目录不存在（409）"
            411 -> "服务器要求 Content-Length（411）"
            413 -> "文件超出服务器限制（413）"
            507 -> "云端空间不足（507）"
            else -> "HTTP $code"
        }
        val suffix = if (detail.isEmpty() || detail.startsWith("<?xml", true)) "" else "：$detail"
        return IOException("$reason$suffix\n写入地址：$url")
    }

    private fun execute(
        endpoint: WebDavEndpoint,
        urlText: String,
        method: String,
        body: String?
    ): Pair<Int, String> {
        val url = try {
            URL(urlText)
        } catch (e: Exception) {
            throw IOException("地址格式不正确：$urlText")
        }
        val payload = body?.toByteArray(Charsets.UTF_8)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Authorization", basicAuth(endpoint.user, endpoint.password))
            setRequestProperty("User-Agent", "drink-today")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // 必须显式给出长度：默认的 chunked 编码会被部分 WebDAV 服务器（含坚果云）拒绝
                setFixedLengthStreamingMode(payload.size)
            }
        }
        try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload) }
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
