// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 飞牛 fnOS share page client.
 *
 * Protocol (as tested):
 * 1. GET the share page HTML, read the embedded `share-data` JSON, take `data.token` (changes on
 *    every refresh);
 * 2. The download GET must carry Cookie `{shareId}={token}`, or the server answers 400;
 * 3. Every POST needs `Auth: token` and an `authx` signature (MD5), with a fixed string to sign.
 */
class FnosShareClient(shareUrl: String) {

    private val origin: String
    private val shareId: String

    init {
        val base = shareUrl.trim().trimEnd('/')
        origin = base.substringBefore("/s/").trimEnd('/')
        shareId = base.substringAfterLast("/s/").trim()
        require(origin.isNotBlank() && shareId.isNotBlank()) { "Invalid NAS share URL: $shareUrl" }
    }

    /** Recursively collects every `.apk` in the share, returning the token/cookie for downloads. */
    suspend fun fetchApkFiles(): NasFetchResult = withContext(Dispatchers.IO) {
        try {
            val page = open("$origin/s/$shareId")
            val code = page.responseCode
            if (code !in 200..299) {
                page.disconnect()
                return@withContext NasFetchResult.Failure(
                    message = "NAS 分享页不可访问（HTTP $code）",
                    suggestion = "请检查分享链接是否正确、NAS 是否在线。",
                )
            }
            val html = page.inputStream.bufferedReader().use { it.readText() }
            page.disconnect()
            val token = parseShareToken(html)
            if (token.isNullOrBlank()) {
                return@withContext NasFetchResult.Failure(
                    message = "无法解析 NAS 分享页令牌",
                    suggestion = "分享链接可能已失效，请重新获取。",
                )
            }
            val cookie = "$shareId=$token"

            val files = mutableListOf<NasShareFile>()
            collectApkFiles(path = "/", fileId = null, depth = 0, token = token, cookie = cookie, out = files)
            if (files.isEmpty()) {
                return@withContext NasFetchResult.Failure(
                    message = "NAS 分享页中未找到 APK 文件",
                    suggestion = "请确认分享目录中已放入 .apk 文件。",
                )
            }
            NasFetchResult.Success(files = files.sortedBy { it.fileName }, shareId = shareId, token = token, cookie = cookie)
        } catch (e: Exception) {
            NasFetchResult.Failure(
                message = "获取 NAS 文件列表失败",
                suggestion = rootMessage(e) ?: "请检查网络与 NAS 地址是否可达。",
            )
        }
    }

    /** The real download address for [file] (relative path → full URL). */
    suspend fun fetchDownloadUrl(file: NasShareFile, token: String, cookie: String): NasDownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("files", JSONArray().put(JSONObject().put("path", file.path).put("fileId", file.fileId)))
                    .put("shareId", shareId)
                    .put("downloadFilename", file.fileName)
                val json = postJson(
                    apiPath = "/s/$shareId/api/v1/share/download",
                    body = body,
                    token = token,
                    cookie = cookie,
                )
                val path = json?.optJSONObject("data")?.optString("path").orEmpty()
                if (path.isBlank()) {
                    NasDownloadResult.Failure(
                        message = "NAS 返回了空的下载地址",
                        suggestion = "分享可能已失效，请重新获取列表。",
                    )
                } else {
                    NasDownloadResult.Success(downloadUrl = origin + path, cookie = cookie)
                }
            } catch (e: Exception) {
                NasDownloadResult.Failure(
                    message = "获取 NAS 下载地址失败",
                    suggestion = rootMessage(e) ?: "请稍后重试。",
                )
            }
        }

    /** Walks the share, collecting `.apk` entries; the fnOS API lists directories as `isDir`. */
    private fun collectApkFiles(
        path: String,
        fileId: Long?,
        depth: Int,
        token: String,
        cookie: String,
        out: MutableList<NasShareFile>,
    ) {
        if (depth > MAX_DIRECTORY_DEPTH) return
        val body = JSONObject().put("shareId", shareId).put("path", path).apply {
            fileId?.let { put("fileId", it) }
        }
        val json = postJson(apiPath = "/s/$shareId/api/v1/share/list", body = body, token = token, cookie = cookie) ?: return
        val files = json.optJSONObject("data")?.optJSONArray("files") ?: return
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            val name = entry.optString("file").trim()
            if (name.isEmpty()) continue
            val entryId = entry.optLong("fileId", -1L)
            if (entry.optInt("isDir", 0) == 1) {
                val childPath = if (path == "/") "/$name" else "$path/$name"
                collectApkFiles(childPath, entryId, depth + 1, token, cookie, out)
            } else if (name.endsWith(".apk", ignoreCase = true)) {
                val filePath = when {
                    entry.optString("path").isNullOrBlank() || entry.optString("path") == "/" -> "/$name"
                    else -> entry.optString("path").trimEnd('/') + "/" + name
                }
                out += NasShareFile(fileName = name, fileId = entryId, path = filePath, size = entry.optLong("size", 0L))
            }
        }
    }

    /** POSTs JSON, parses the response; null on network failure or `code != 0`. */
    private fun postJson(apiPath: String, body: JSONObject, token: String, cookie: String): JSONObject? {
        val bodyString = body.toString()
        val authx = buildAuthx(apiPath, bodyString)
        val connection = open(origin + apiPath).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Auth", token)
            setRequestProperty("authx", authx)
            setRequestProperty("Cookie", cookie)
        }
        try {
            connection.outputStream.use { it.write(bodyString.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) return null
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            return if (json.optInt("code", -1) == 0) json else null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    /** The token from the share page's embedded `share-data` JSON. */
    private fun parseShareToken(html: String): String? {
        val match = SHARE_DATA_REGEX.find(html) ?: return null
        return runCatching {
            JSONObject(match.groupValues[1]).optJSONObject("data")?.optString("token")
        }.getOrNull()
    }

    /** The `authx` signature header (MD5, verified against the real server). */
    private fun buildAuthx(apiPath: String, bodyString: String): String {
        val nonce = (Random().nextInt(900_000) + 100_000).toString()
        val timestamp = System.currentTimeMillis().toString()
        val payloadHash = md5Hex(bodyString)
        val signInput = "${SIGN_SECRET}_${apiPath}_${nonce}_${timestamp}_${payloadHash}_$API_KEY"
        val sign = md5Hex(signInput)
        return "nonce=$nonce&timestamp=$timestamp&sign=$sign"
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun rootMessage(error: Throwable): String? =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()

    private companion object {
        const val MAX_DIRECTORY_DEPTH = 3
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val SIGN_SECRET = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
        const val API_KEY = "814&d6470861a4cfbbb4fe2fd3f\$6581f6"
        val SHARE_DATA_REGEX = Regex(
            """<script[^>]*id=["']share-data["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    }
}
