// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File
import java.util.Properties

/**
 * When each TV's History was last opened, kept in [file]: ⋮ shows a dot for a change newer than
 * that. Never opened reads as 0, so a TV with History shows the dot once.
 */
class SeenBook(private val file: File) {
    @Synchronized
    fun seen(): Map<String, Long> = props().let { p -> p.stringPropertyNames().associateWith { p.getProperty(it).toLongOrNull() ?: 0L } }

    @Synchronized
    fun set(deviceId: String, at: Long) {
        val p = props().apply { setProperty(deviceId, at.toString()) }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, null) }
        }
    }

    private fun props() = Properties().apply {
        runCatching { file.takeIf { it.exists() }?.inputStream()?.use(::load) }
    }
}
