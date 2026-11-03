// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import app.tellyscope.R
import java.net.URI

/** URL and target-path checks for the online download form. */
object DownloadInputValidator {

    /** The reason [value] isn't a usable URL, or null when it is. */
    fun urlError(value: String, requireApk: Boolean = false): UiText? {
        val normalized = value.trim()
        return when {
            normalized.isBlank() -> null
            !isHttpUrl(normalized) -> UiText(R.string.tools_download_url_scheme)
            requireApk && !normalized.substringBefore("?").endsWith(".apk", ignoreCase = true) ->
                UiText(R.string.tools_download_url_apk_suffix)
            else -> null
        }
    }

    fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return (uri.scheme == "http" || uri.scheme == "https") && uri.host != null
    }
}
