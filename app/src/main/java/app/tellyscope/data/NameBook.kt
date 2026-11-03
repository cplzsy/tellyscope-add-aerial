// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File

/**
 * Names the user gave their TVs, keyed like history ([historyKey]) so a name follows the TV
 * across DHCP changes and survives Forget + add again. `key \t name` per line; null file keeps
 * it in memory (tests, the fake source).
 */
class NameBook(private val file: File? = null) {
    private val names = linkedMapOf<String, String>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val (k, v) = line.split('\t').takeIf { it.size == 2 && it[0].isNotEmpty() && it[1].isNotBlank() } ?: return@forEach
                names[k] = v
            }
        }
    }

    @Synchronized
    operator fun get(key: String): String? = names[key]

    /** Blank clears the name, so the TV goes back to what the network calls it. */
    @Synchronized
    fun set(key: String, name: String?) {
        val clean = name.orEmpty().replace(Regex("[\\t\\r\\n]"), " ").trim().take(MAX_LENGTH)
        if (clean.isEmpty()) names.remove(key) else names[key] = clean
        save()
    }

    /** The address stood in until the TV said who it is; the name moves to the real key. */
    @Synchronized
    fun rekey(from: String, to: String) {
        if (from == to) return
        val name = names.remove(from) ?: return
        names.putIfAbsent(to, name)
        save()
    }

    private fun save() {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(names.entries.joinToString("") { (k, v) -> "$k\t$v\n" })
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    companion object {
        const val MAX_LENGTH = 40
    }
}
