// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.pm.PackageInfoCompat
import app.tellyscope.adb.ApkBundle
import app.tellyscope.adb.ApkInfo
import app.tellyscope.adb.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

/** An app the user picked on the phone, copied into the cache and read, ready to install. */
data class PickedApp(
    /** Base APK first, then any split parts. */
    val files: List<File>,
    val info: ApkInfo,
    /** The APK's own label; the package name when it has none. */
    val label: String,
) {
    val sizeBytes: Long get() = files.sumOf { it.length() }
}

/** The picked file wasn't an APK or a bundle of them. */
class NotAnApp : IOException("not an Android app")

class PhoneFile(val name: String, val size: Long?, val source: Source)

/** The phone's side of a copy, through the system file picker. Faked in screenshots. */
interface PhoneStorage {
    /** One .apk, several split .apk files, or one .apks/.xapk/.apkm bundle. Throws [NotAnApp]. */
    suspend fun readApp(uris: List<Uri>): PickedApp

    /** Reads [file], an APK or a bundle, without copying it; for files the tools downloaded. Throws [NotAnApp]. */
    suspend fun readAppFile(file: File): PickedApp =
        throw UnsupportedOperationException("readAppFile is not supported")

    fun open(uri: Uri): PhoneFile
    fun create(uri: Uri): Sink
    /** Removes a half-written file after a cancelled save. */
    fun delete(uri: Uri)
    /** Drops the cached copy of the last picked app. */
    fun clearApp()
}

class AndroidPhoneStorage(context: Context) : PhoneStorage {
    private val ctx = context.applicationContext
    private val dir get() = File(ctx.cacheDir, "sideload")

    override suspend fun readApp(uris: List<Uri>): PickedApp = withContext(Dispatchers.IO) {
        clearApp()
        dir.mkdirs()
        val copies = uris.map { uri ->
            val f = File(dir, Files.safeName(displayName(uri) ?: "app.apk"))
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use(i::copyTo) } ?: throw NotAnApp()
            f
        }
        readPicked(copies)
    }

    override suspend fun readAppFile(file: File): PickedApp = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) throw NotAnApp()
        clearApp()
        dir.mkdirs()
        val apks = if (ApkBundle.isApk(file)) listOf(file)
        else try {
            ApkBundle.unpack(file, File(dir, "parts"))
        } catch (_: IOException) {
            throw NotAnApp()
        }
        readPicked(ApkBundle.baseFirst(apks))
    }

    private fun readPicked(apks: List<File>): PickedApp {
        val base = apks.first()
        @Suppress("DEPRECATION") // the flags overload is API 33+
        val pkg = ctx.packageManager.getPackageArchiveInfo(base.path, 0) ?: throw NotAnApp()
        val app = pkg.applicationInfo
        // An archive's label resolves only once its source path points at the file.
        val label = app?.let {
            it.sourceDir = base.path
            it.publicSourceDir = base.path
            it.loadLabel(ctx.packageManager).toString()
        }
        return PickedApp(
            files = apks,
            info = ApkInfo(pkg.packageName, PackageInfoCompat.getLongVersionCode(pkg), pkg.versionName),
            label = label?.takeIf { it.isNotBlank() && it != pkg.packageName } ?: pkg.packageName,
        )
    }

    override fun open(uri: Uri): PhoneFile {
        var name: String? = null
        var size: Long? = null
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                size = if (c.isNull(1)) null else c.getLong(1)
            }
        }
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("can't open the file")
        return PhoneFile(name ?: uri.lastPathSegment ?: "file", size, input.source())
    }

    override fun create(uri: Uri): Sink =
        ctx.contentResolver.openOutputStream(uri, "wt")?.sink() ?: throw IOException("can't write there")

    override fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
    }

    override fun clearApp() {
        dir.deleteRecursively()
    }

    private fun displayName(uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
