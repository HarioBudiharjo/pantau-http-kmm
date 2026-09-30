package com.pantauhttp.internal.android

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.GzipSource
import okio.buffer
import java.io.IOException

/**
 * Passes the body through to the app untouched while keeping up to [limit]
 * bytes and counting the total. Completes once on EOF, close or read error.
 */
internal class TeeResponseBody(
    private val delegate: ResponseBody,
    private val limit: Int,
    private val gzipped: Boolean,
    private val onComplete: (bytes: ByteArray?, totalBytes: Long, truncated: Boolean) -> Unit,
) : ResponseBody() {

    private val captured = Buffer()
    private var total = 0L
    private var completed = false

    private val source: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = try {
                    super.read(sink, byteCount)
                } catch (e: IOException) {
                    finish()
                    throw e
                }
                if (read == -1L) {
                    finish()
                    return -1L
                }
                total += read
                val room = limit - captured.size
                if (room > 0) {
                    val take = minOf(read, room)
                    sink.copyTo(captured, sink.size - read, take)
                }
                return read
            }

            override fun close() {
                finish()
                super.close()
            }
        }.buffer()
    }

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource = source

    @Synchronized
    private fun finish() {
        if (completed) return
        completed = true
        val truncated = total > limit
        var bytes = captured.readByteArray()
        if (gzipped && !truncated) {
            bytes = runCatching { GzipSource(Buffer().write(bytes)).buffer().readByteArray() }.getOrDefault(bytes)
        }
        onComplete(bytes.takeIf { it.isNotEmpty() }, total, truncated)
    }
}
