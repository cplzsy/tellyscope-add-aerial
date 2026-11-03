// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File
import java.util.Properties

/**
 * The Remote's own settings, kept in [file]: whether a key press vibrates (on until the user turns
 * it off), and whether the Screen off tip has been shown.
 */
class KeyHaptics(private val file: File) {
    @get:Synchronized @set:Synchronized
    var on: Boolean
        get() = props().getProperty("off") != "true"
        set(on) = store { if (on) remove("off") else setProperty("off", "true") }

    @get:Synchronized @set:Synchronized
    var screenOffTipSeen: Boolean
        get() = props().getProperty("screenOffTip") == "seen"
        set(seen) = store { if (seen) setProperty("screenOffTip", "seen") else remove("screenOffTip") }

    private fun store(change: Properties.() -> Unit) {
        val p = props().apply(change)
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, null) }
        }
    }

    private fun props() = Properties().apply {
        runCatching { file.takeIf { it.exists() }?.inputStream()?.use(::load) }
    }
}
