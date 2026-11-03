// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * One app as the TV's PackageManager describes it. A banner-only app has no [iconPng] but a letter
 * tile: [tileColor] behind, [tileAccent] for the letter, both ARGB.
 */
class AppLabel(
    val packageName: String,
    val label: String,
    val versionCode: Long,
    val iconPng: ByteArray?,
    val tileColor: Long? = null,
    val tileAccent: Long? = null,
)

/**
 * Real app names and icons, read on the TV by the helper dex (`app/src/helper`): push it, run it
 * once with `app_process` as the shell user, delete it whatever happens. The helper only reads
 * through PackageManager. [dex] is the helper jar bundled in the app's assets.
 */
class LabelHelper(private val session: AdbSession, private val dex: ByteArray) {

    /**
     * Describes [packages], or every installed app past [MAX_ARGS] so the shell line stays short
     * on old adbd. Null when the helper couldn't run at all (push refused, `app_process` blocked by
     * the OEM, a dropped link, a run past [TIMEOUT], no usable output): the caller falls back to its
     * name table.
     */
    suspend fun describe(packages: Collection<String>): List<AppLabel>? {
        val names = packages.filter { Actions.PACKAGE.matches(it) } // they go into a shell line
        if (names.isEmpty()) return emptyList()
        val args = if (names.size > MAX_ARGS) "--all" else names.joinToString(" ")
        return try {
            session.push(dex, PATH)
            // A hung helper must not hold up the app list; a timeout counts as "couldn't run".
            val out = withTimeoutOrNull(TIMEOUT) {
                session.shell("CLASSPATH=$PATH app_process / $MAIN $args 2>/dev/null; rm -f $PATH").output
            }
            out?.let(::parse)?.ifEmpty { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            // The shell line removes it too; this covers a run that never got that far.
            withContext(NonCancellable) { runCatching { session.shell("rm -f $PATH") } }
        }
    }

    companion object {
        const val PATH = "/data/local/tmp/tellyscope-label-helper.jar"
        const val MAIN = "app.tellyscope.helper.Labels"
        const val MAX_ARGS = 40
        /** Every icon on a slow TV fits in this; past it, the fallback names are better than a spinner. */
        val TIMEOUT = 60.seconds

        /** The helper's output: one flat JSON object per line. Lines that don't parse are skipped. */
        fun parse(output: String): List<AppLabel> = output.lineSequence().mapNotNull { line ->
            val o = FlatJson.parse(line.trim()) ?: return@mapNotNull null
            val pkg = o["p"] as? String ?: return@mapNotNull null
            val label = (o["l"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val version = (o["v"] as? Long) ?: return@mapNotNull null
            val icon = (o["i"] as? String)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            // Both or neither: a tile needs its letter colour.
            val tile = (o["b"] as? Long)?.let { b -> (o["a"] as? Long)?.let { a -> b to a } }
            AppLabel(pkg, label, version, icon, tile?.first, tile?.second)
        }.toList()
    }
}

/** Just enough JSON for the helper's lines: one object, string and integer values, no nesting. */
internal object FlatJson {
    fun parse(s: String): Map<String, Any>? = runCatching { Reader(s).obj() }.getOrNull()

    private class Reader(val s: String) {
        var i = 0

        fun obj(): Map<String, Any> {
            val out = mutableMapOf<String, Any>()
            expect('{')
            ws()
            if (peek() == '}') { i++; return end(out) }
            while (true) {
                ws()
                val key = string()
                ws(); expect(':'); ws()
                out[key] = if (peek() == '"') string() else number()
                ws()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return end(out)
                    else -> error("bad separator at $i")
                }
            }
        }

        private fun end(out: Map<String, Any>): Map<String, Any> {
            ws()
            require(i == s.length) { "trailing data" }
            return out
        }

        private fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                when (val c = s[i++]) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        'n' -> b.append('\n')
                        't' -> b.append('\t')
                        'r' -> b.append('\r')
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000c')
                        '"', '\\', '/' -> b.append(e)
                        else -> error("bad escape \\$e")
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun number(): Long {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toLong()
        }

        private fun peek() = s[i]
        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun expect(c: Char) { require(s[i++] == c) { "expected $c at ${i - 1}" } }
    }
}
