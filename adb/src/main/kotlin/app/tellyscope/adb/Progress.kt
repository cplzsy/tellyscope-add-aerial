// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Sink
import okio.Source
import java.io.InterruptedIOException

/** Bytes moved so far out of [total]. [total] is null when the size isn't known up front. */
data class Progress(val done: Long, val total: Long?) {
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (done.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

/**
 * Runs a blocking Kadb [transfer] on the IO pool. Cancelling the coroutine doesn't interrupt the
 * thread (that would close the socket the stats share); the next chunk read or written through
 * [counting] sees it and stops the stream instead. [cleanUp] runs whenever the transfer doesn't
 * finish, cancelled or failed (a full disk, a dropped link), so nothing truncated is left behind.
 * A cancel then reaches the caller as [CancellationException], a failure as itself.
 */
internal suspend fun <T> runTransfer(cleanUp: suspend () -> Unit = {}, transfer: (stopped: () -> Boolean) -> T): T =
    withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        try {
            transfer { !job.isActive }
        } catch (e: Exception) {
            withContext(NonCancellable) { cleanUp() }
            // Kadb may wrap our stop in its own exception; what matters is whether we asked to stop.
            if (job.isActive) throw e
            throw CancellationException("transfer cancelled").apply { initCause(e) }
        }
    }

/** Reports at most once per percent, so a 2 GB file doesn't flood the UI with updates. */
private class Reporter(private val total: Long?, private val onProgress: (Progress) -> Unit) {
    private var done = 0L
    private var last = -1L

    fun add(n: Long) {
        done += n
        val step = total?.let { if (it > 0) done * 100 / it else 100 } ?: (done shr 20)
        if (step != last) {
            last = step
            onProgress(Progress(done, total))
        }
    }
}

internal fun Source.counting(total: Long?, stopped: () -> Boolean, onProgress: (Progress) -> Unit): Source {
    val r = Reporter(total, onProgress)
    return object : ForwardingSource(this) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (stopped()) throw InterruptedIOException("cancelled")
            return super.read(sink, byteCount).also { if (it > 0) r.add(it) }
        }
    }
}

internal fun Sink.counting(total: Long?, stopped: () -> Boolean, onProgress: (Progress) -> Unit): Sink {
    val r = Reporter(total, onProgress)
    return object : ForwardingSink(this) {
        override fun write(source: Buffer, byteCount: Long) {
            if (stopped()) throw InterruptedIOException("cancelled")
            super.write(source, byteCount)
            r.add(byteCount)
        }
    }
}

/** One shell word, single-quoted the way adb quotes arguments. */
internal fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
