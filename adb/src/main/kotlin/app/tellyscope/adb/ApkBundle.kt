// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Split apps come as several .apk files, or as one bundle (.apks, .xapk, .apkm) that is a zip of
 * them. Both end up here as a list of APK files, base first, ready for [Sideload.install].
 */
object ApkBundle {

    /** An APK is itself a zip with a manifest at the root; a bundle is a zip of APKs. */
    fun isApk(file: File): Boolean = zipEntries(file)?.contains("AndroidManifest.xml") == true

    /**
     * The APKs inside [bundle], extracted into [dir]. Only top-level `*.apk` entries count; OBB
     * data and metadata are skipped. Throws [IOException] when there are none.
     */
    fun unpack(bundle: File, dir: File): List<File> {
        dir.mkdirs()
        val out = ZipFile(bundle).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && '/' !in it.name && it.name.endsWith(".apk", ignoreCase = true) }
                .map { e ->
                    // Names are flat (no '/' above), so nothing can land outside dir.
                    File(dir, e.name).also { f -> zip.getInputStream(e).use { i -> f.outputStream().use(i::copyTo) } }
                }
                .toList()
        }
        if (out.isEmpty()) throw IOException("no APKs in ${bundle.name}")
        return baseFirst(out)
    }

    /** `base.apk` if there is one, else the largest file (splits are small config slices). */
    fun baseFirst(apks: List<File>): List<File> {
        val base = apks.firstOrNull { it.name.equals("base.apk", ignoreCase = true) } ?: apks.maxBy { it.length() }
        return listOf(base) + (apks - base)
    }

    private fun zipEntries(file: File): Set<String>? =
        try {
            ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.toSet() }
        } catch (_: IOException) {
            null
        }
}
