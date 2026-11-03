// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

/**
 * Models for the overview's tools block: the online download, ADB privilege and Aerial install
 * flows. Kept together because they share the download/install pipeline.
 */

/** A download the tools know about: where it came from, what it landed as and how far it is. */
data class DownloadTask(
    val url: String,
    val fileName: String,
    val targetPath: String,
    val localPath: String? = null,
    val progress: Float,
    val state: DownloadState,
)

enum class DownloadState { Waiting, Downloading, Pushing, Installing, Success, Failed, Canceled }

sealed interface DownloadResult {
    data class Success(val fileName: String, val localPath: String) : DownloadResult

    data class Failure(val message: String, val suggestion: String, val cause: Throwable? = null) : DownloadResult

    data object Canceled : DownloadResult
}

/** A downloadable asset of a GitHub release. */
data class ReleaseAsset(val fileName: String, val downloadUrl: String)

/** The latest release's .apk assets, or why they couldn't be read. */
sealed interface ReleaseFetchResult {
    data class Success(val assets: List<ReleaseAsset>) : ReleaseFetchResult

    data class Failure(val message: String, val suggestion: String? = null) : ReleaseFetchResult
}

/** One file on a NAS share page (fnOS or File Browser). */
data class NasShareFile(
    val fileName: String,
    val fileId: Long,
    val path: String,
    val size: Long,
)

/** The NAS share's .apk files, plus what the download needs (token/cookie). */
sealed interface NasFetchResult {
    data class Success(
        val files: List<NasShareFile>,
        val shareId: String,
        val token: String,
        val cookie: String,
    ) : NasFetchResult

    data class Failure(val message: String, val suggestion: String? = null) : NasFetchResult
}

/** A NAS file's real download address, or why it couldn't be had. */
sealed interface NasDownloadResult {
    data class Success(val downloadUrl: String, val cookie: String) : NasDownloadResult

    data class Failure(val message: String, val suggestion: String? = null) : NasDownloadResult
}
