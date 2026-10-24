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
        var (code, body) = execute(endpoint, url, "PUT", json)

        // 目录不存在（404/409）时尝试 MKCOL 建目录后重试一次
        if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_CONFLICT) {
            if (ensureCollection(endpoint)) {
                val retry = execute(endpoint, url, "PUT", json)
                code = retry.first
                body = retry.second
            }
        }
        if (code !in 200..299) throw failure(code, url, body)
    }

    /**
     * 确保目标目录存在：HEAD 检查，缺失则用 MKCOL 创建。
     * 返回 true 表示目录可用（已存在或创建成功）。
     */
    fun ensureCollection(endpoint: WebDavEndpoint): Boolean {
        val dirUrl = collectionUrl(endpoint.server)
        val (code, _) = execute(endpoint, dirUrl, "HEAD", null)
        if (code in 200..299 || code == 207) return true
        if (code != HttpURLConnection.HTTP_NOT_FOUND) return false
        return mkcol(endpoint, dirUrl)
    }

    /** 用 MKCOL 创建集合；HttpURLConnection 只放行标准方法，这里用反射改名。 */
    private fun mkcol(endpoint: WebDavEndpoint, dirUrl: String): Boolean {
        return try {
            val connection = URL(dirUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", basicAuth(endpoint.user, endpoint.password))
            connection.setRequestProperty("User-Agent", "drink-today")
            if (!forceMethod(connection, "MKCOL")) return false
            val code = connection.responseCode
            connection.disconnect()
            // 405 表示已存在（部分服务器的响应）
            code in 200..299 || code == 405
        } catch (e: Exception) {
            false
        }
    }

    private fun forceMethod(connection: HttpURLConnection, method: String): Boolean {
        var clazz: Class<*>? = connection.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField("method")
                field.isAccessible = true
                field.set(connection, method)
                return true
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (e: Exception) {
                return false
            }
        }
        return false
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
        if (dirCode in 200..299 || dirCode == 207) {
            // 目录存在但未必可写（坚果云根目录即如此），做一次写入探针
            if (!writeTest(endpoint, dirUrl)) {
                throw IOException(
                    "该目录不可写入。多数网盘不允许直接写根目录，\n" +
                        "请把地址指到一个子文件夹，如 https://dav.jianguoyun.com/dav/drink-today/"
                )
            }
            return ProbeResult.READY_TO_CREATE
        }

        if (dirCode == HttpURLConnection.HTTP_NOT_FOUND) {
            if (mkcol(endpoint, dirUrl) && writeTest(endpoint, dirUrl)) {
                return ProbeResult.READY_TO_CREATE
            }
            throw IOException(
                "目录不存在且无法自动创建。\n" +
                    "请在网盘中新建文件夹（如 drink-today），地址填成\n" +
                    "https://dav.jianguoyun.com/dav/drink-today/"
            )
        }
        if (dirCode == 405 || dirCode == 501) return ProbeResult.READY_TO_CREATE
        throw failure(dirCode, dirUrl, dirBody)
    }

    /**
     * 写权限探针：PUT 一个临时文件再删除。
     * 只靠 HEAD 无法判断「目录可读但不可写」（坚果云根目录就是这种情况）。
     */
    private fun writeTest(endpoint: WebDavEndpoint, dirUrl: String): Boolean {
        val probeUrl = dirUrl + ".drink-today-probe"
        return try {
            val payload = "ok".toByteArray(Charsets.UTF_8)
            val put = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", basicAuth(endpoint.user, endpoint.password))
                setRequestProperty("User-Agent", "drink-today")
                setFixedLengthStreamingMode(payload.size)
            }
            put.outputStream.use { it.write(payload) }
            val putCode = put.responseCode
            put.disconnect()
            if (putCode !in 200..299) return false

            // 清理探针文件；失败也不影响结论
            try {
                val delete = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Authorization", basicAuth(endpoint.user, endpoint.password))
                }
                delete.responseCode
                delete.disconnect()
            } catch (ignored: Exception) {
            }
            true
        } catch (e: Exception) {
            false
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
                "路径不存在（404）。网盘通常不允许直接写入根目录，" +
                    "请把地址指到一个文件夹，如 https://dav.jianguoyun.com/dav/drink-today/"

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
