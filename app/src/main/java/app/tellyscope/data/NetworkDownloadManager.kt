// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Downloads over plain [HttpURLConnection]: an APK from a URL into the cache (progress in
 * [DownloadTask]s), and a GitHub release's latest `.apk` asset list. No OkHttp, so the app stays
 * small. One download at a time; [cancelCurrentDownload] stops it.
 */
class NetworkDownloadManager(private val context: Context? = null) {

    private val active = AtomicReference<HttpURLConnection?>(null)

    fun validateUrl(url: String): Boolean = DownloadInputValidator.isHttpUrl(url)

    fun cancelCurrentDownload() {
        active.getAndSet(null)?.disconnect()
    }

    suspend fun download(
        url: String,
        preferredFileName: String? = null,
        headers: Map<String, String> = emptyMap(),
        onProgress: (DownloadTask) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!validateUrl(url)) {
            return@withContext DownloadResult.Failure(
                message = "不是有效的 HTTP/HTTPS 地址",
                suggestion = "请检查链接是否以 http:// 或 https:// 开头。",
            )
        }

        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
        } catch (e: Exception) {
            return@withContext DownloadResult.Failure(
                message = "无法解析下载地址",
                suggestion = e.message ?: "请检查链接格式。",
                cause = e,
            )
        }
        check(active.compareAndSet(null, connection)) { "A download is already running" }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext DownloadResult.Failure(
                    message = "服务器拒绝了下载（HTTP $code）",
                    suggestion = "请稍后再试，或确认链接未失效。",
                )
            }
            val contentDisposition = connection.getHeaderField("Content-Disposition")
            val fileName = sanitizeFileName(
                preferredFileName?.takeIf { it.isNotBlank() }
                    ?: fileNameFromContentDisposition(contentDisposition)
                    ?: fileNameFromUrl(url)
                    ?: "download-${System.currentTimeMillis()}"
            )

            val downloadDir = downloadDir()
            cleanupDownloadDir(downloadDir)
            val taskDir = File(downloadDir, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
            val targetFile = File(taskDir, fileName)
            targetFile.parentFile?.mkdirs()

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            var downloadedBytes = 0L
            var lastProgressAt = 0L

            try {
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            if (active.get() !== connection) {
                                targetFile.delete()
                                return@withContext DownloadResult.Canceled
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                                lastProgressAt = now
                                val progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                                onProgress(
                                    DownloadTask(
                                        url = url,
                                        fileName = fileName,
                                        targetPath = "",
                                        localPath = targetFile.absolutePath,
                                        progress = progress,
                                        state = DownloadState.Downloading,
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                targetFile.delete()
                throw error
            }
            onProgress(
                DownloadTask(
                    url = url,
                    fileName = fileName,
                    targetPath = "",
                    localPath = targetFile.absolutePath,
                    progress = 1f,
                    state = DownloadState.Downloading,
                )
            )
            DownloadResult.Success(fileName = fileName, localPath = targetFile.absolutePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (active.get() !== connection) DownloadResult.Canceled
            else DownloadResult.Failure(
                message = "下载失败",
                suggestion = rootMessage(e) ?: "请检查网络后重试。",
                cause = e,
            )
        } finally {
            active.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    /**
     * The latest release's `.apk` assets from a GitHub release web page, e.g.
     * https://github.com/{owner}/{repo}/releases/latest (turned into the API call).
     */
    suspend fun fetchLatestReleaseApks(releaseWebUrl: String): ReleaseFetchResult =
        withContext(Dispatchers.IO) {
            val apiUrl = toGitHubLatestReleaseApiUrl(releaseWebUrl)
                ?: return@withContext ReleaseFetchResult.Failure(
                    message = "不是有效的 GitHub Release 链接",
                    suggestion = "请检查链接格式。",
                )
            try {
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "tellyscope")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                try {
                    connection.connect()
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        return@withContext ReleaseFetchResult.Failure(
                            message = "服务器拒绝了请求（HTTP $code）",
                            suggestion = "请稍后再试，或确认仓库地址正确。",
                        )
                    }
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = JSONObject(payload)
                    val assets = release.getJSONArray("assets")
                    val apks = buildList {
                        for (index in 0 until assets.length()) {
                            val asset = assets.getJSONObject(index)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                add(
                                    ReleaseAsset(
                                        fileName = name,
                                        downloadUrl = asset.getString("browser_download_url"),
                                    )
                                )
                            }
                        }
                    }
                    ReleaseFetchResult.Success(apks)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReleaseFetchResult.Failure(
                    message = "获取 Release 列表失败",
                    suggestion = rootMessage(e) ?: "请检查网络后重试。",
                )
            }
        }

    private fun toGitHubLatestReleaseApiUrl(webUrl: String): String? {
        val match = GITHUB_RELEASE_REGEX.find(webUrl.trim()) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    private fun downloadDir(): File {
        val base = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        return File(base, "downloads")
    }

    private fun fileNameFromUrl(url: String): String? =
        url.substringBefore("?")
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }

    private fun fileNameFromContentDisposition(value: String): String? =
        value.split(";")
            .map(String::trim)
            .firstOrNull { it.startsWith("filename=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }

    /** One path segment: no separators or control characters, never "." or ".." (like Files.safeName). */
    private fun sanitizeFileName(name: String): String {
        val cleaned = name.map { if (it == '/' || it == '\\' || it.code < 0x20) '_' else it }.joinToString("").trim()
        return cleaned.takeUnless { it.isEmpty() || it.all { c -> c == '.' } } ?: "file"
    }

    private fun cleanupDownloadDir(directory: File) {
        directory.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_CACHED_DOWNLOADS)
            ?.forEach { file -> runCatching { file.deleteRecursively() } }
    }

    private fun rootMessage(error: Throwable): String? =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
        const val PROGRESS_INTERVAL_MS = 150L
        const val MAX_CACHED_DOWNLOADS = 3
        const val DEFAULT_BUFFER_SIZE = 8192
        val GITHUB_RELEASE_REGEX = Regex("^https?://github\\.com/([^/]+)/([^/]+)/releases(?:/.*)?$")
    }
}
