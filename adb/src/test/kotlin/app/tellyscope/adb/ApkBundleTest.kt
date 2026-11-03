// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkBundleTest {
    private val dir = createTempDirectory("bundle").toFile()

    private fun zip(name: String, entries: Map<String, Int>) = File(dir, name).also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (e, size) -> z.putNextEntry(ZipEntry(e)); z.write(ByteArray(size)); z.closeEntry() }
        }
    }

    @Test fun tellsAnApkFromABundle() {
        assertTrue(ApkBundle.isApk(zip("app.apk", mapOf("AndroidManifest.xml" to 10, "classes.dex" to 10))))
        assertFalse(ApkBundle.isApk(zip("app.apks", mapOf("base.apk" to 10))))
        assertFalse(ApkBundle.isApk(File(dir, "notes.txt").apply { writeText("hi") }))
    }

    @Test fun unpacksApksBaseFirstAndSkipsTheRest() {
        val b = zip("player.xapk", mapOf(
            "manifest.json" to 5,
            "config.arm64_v8a.apk" to 50,
            "com.example.player.apk" to 900,
            "Android/obb/com.example.player/main.1.obb" to 100,
            "icon.png" to 5,
        ))
        val out = ApkBundle.unpack(b, File(dir, "out"))
        assertEquals(listOf("com.example.player.apk", "config.arm64_v8a.apk"), out.map { it.name })
        assertEquals(900, out.first().length())
    }

    @Test fun baseApkWinsOverSize() {
        val b = zip("player.apks", mapOf("split_big.apk" to 900, "base.apk" to 100))
        assertEquals("base.apk", ApkBundle.unpack(b, File(dir, "o2")).first().name)
    }

    @Test fun nestedPathsNeverEscape() {
        val b = zip("evil.apks", mapOf("../../escape.apk" to 10))
        assertFailsWith<IOException> { ApkBundle.unpack(b, File(dir, "o3")) }
        assertFalse(File(dir, "escape.apk").exists())
    }
}
