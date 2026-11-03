// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File
import java.util.Properties

/**
 * Which Apps tabs have their System section folded, kept in [file] so a fold the user chose
 * stays across restarts until they open it again. Unfolded until told otherwise. The same book
 * keeps, in its own file, which TVs put away the card about a better home screen.
 */
class FoldBook(private val file: File) {
    @Synchronized
    fun folded(): Set<String> = props().let { p -> p.stringPropertyNames().filter { p.getProperty(it) == "true" }.toSet() }

    @Synchronized
    fun set(tab: String, folded: Boolean) {
        val p = props().apply { if (folded) setProperty(tab, "true") else remove(tab) }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, null) }
        }
    }

    private fun props() = Properties().apply {
        runCatching { file.takeIf { it.exists() }?.inputStream()?.use(::load) }
    }
}
