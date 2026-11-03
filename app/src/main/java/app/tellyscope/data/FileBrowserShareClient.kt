// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * File Browser public share client.
 *
 * Protocol (as tested):
 * 1. List: GET {origin}/api/public/share/{hash} — no auth, returns items[] (name/path/size);
 * 2. Download: GET {origin}/api/public/dl/{hash}/{path} — no auth, raw bytes.
 * A share link looks like {origin}/share/{hash}.
 */
class FileBrowserShareClient(shareUrl: String) {

    private val origin: String
    private val hash: String

    init {
        val base = shareUrl.trim().trimEnd('/')
        origin = base.substringBefore("/share/").trimEnd('/')
        hash = base.substringAfterLast("/share/").trim()
        require(origin.isNotBlank() && hash.isNotBlank()) { "Invalid File Browser share URL: $shareUrl" }
    }

    /** Every `.apk` in the share, or why they couldn't be read. */
    suspend fun fetchApkFiles(): NasFetchResult = withContext(Dispatchers.IO) {
        try {
            val connection = open("$origin/api/public/share/$hash")
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext NasFetchResult.Failure(
                        message = "File Browser 分享不可访问（HTTP $code）",
                        suggestion = "外网分享暂时不可用，将回退到内网 NAS 来源。",
                    )
                }
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val json = runCatching { JSONObject(payload) }.getOrNull()
                if (json == null) {
                    return@withContext NasFetchResult.Failure(
                        message = "无法解析 File Browser 分享数据",
                        suggestion = "分享链接可能已失效，将回退到内网 NAS 来源。",
                    )
                }
                val items = json.optJSONArray("items")
                val files = buildList {
                    if (items != null) {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            if (item.optBoolean("isDir")) continue
                            val name = item.optString("name")
                            val path = item.optString("path")
                            if (name.isBlank() || path.isBlank()) continue
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                add(NasShareFile(fileName = name, fileId = 0, path = path, size = item.optLong("size")))
                            }
                        }
                    }
                }
                if (files.isEmpty()) {
                    return@withContext NasFetchResult.Failure(
                        message = "File Browser 分享中未找到 APK 文件",
                        suggestion = "请确认分享目录中已放入 .apk 文件。",
                    )
                }
                NasFetchResult.Success(files = files.sortedBy { it.fileName }, shareId = hash, token = "", cookie = "")
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            NasFetchResult.Failure(
                message = "获取 File Browser 文件列表失败",
                suggestion = rootMessage(e) ?: "将回退到内网 NAS 来源。",
            )
        }
    }

    /** The no-auth download address for [file] (each path segment URL-encoded). */
    fun downloadUrl(file: NasShareFile): String {
        val encodedPath = file.path
            .split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment -> URLEncoder.encode(segment, "UTF-8").replace("+", "%20") }
        return "$origin/api/public/dl/$hash/$encodedPath"
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    private fun rootMessage(error: Throwable): String? =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
