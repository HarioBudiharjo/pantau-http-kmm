package com.pantauhttp.internal.store

import com.pantauhttp.HttpTransaction
import com.pantauhttp.PantauHttpConfiguration
import com.pantauhttp.Redirect
import com.pantauhttp.TransactionState
import com.pantauhttp.internal.platform.epochMillis
import com.pantauhttp.redact
import kotlin.uuid.Uuid

/**
 * Translates capture-engine events into store mutations, applying header
 * redaction and body size caps from the active configuration.
 */
internal class TransactionRecorder(
    private val store: TransactionStore,
    private val configuration: PantauHttpConfiguration,
    private val clock: () -> Long = ::epochMillis,
    private val onComplete: (HttpTransaction) -> Unit = {},
) {

    /** Inserts a new in-progress transaction and returns its id. */
    fun begin(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        bodySize: Long = body?.size?.toLong() ?: 0L,
        truncated: Boolean = false,
    ): String {
        val limit = configuration.bodySizeLimit
        val cappedBody = body?.let { if (it.size > limit) it.copyOf(limit) else it }
        val transaction = HttpTransaction(
            id = Uuid.random().toString().lowercase(),
            method = method,
            url = url,
            requestHeaders = configuration.redact(headers),
            requestBody = cappedBody,
            requestBodySize = bodySize,
            isRequestBodyTruncated = truncated || (body != null && body.size > limit),
            requestTimeMs = clock(),
        )
        store.insert(transaction)
        return transaction.id
    }

    fun response(
        id: String,
        statusCode: Int,
        headers: Map<String, String>,
        finalRequestHeaders: Map<String, String>? = null,
    ) {
        store.upsert(id) {
            it.copy(
                statusCode = statusCode,
                responseHeaders = configuration.redact(headers),
                requestHeaders = finalRequestHeaders?.let(configuration::redact) ?: it.requestHeaders,
            )
        }
    }

    /** Appends a streamed chunk, keeping at most [PantauHttpConfiguration.bodySizeLimit] bytes. */
    fun appendBody(id: String, chunk: ByteArray) {
        if (chunk.isEmpty()) return
        val limit = configuration.bodySizeLimit
        store.upsert(id) { tx ->
            val existing = tx.responseBody ?: ByteArray(0)
            val remaining = limit - existing.size
            val body = if (remaining > 0) existing + chunk.copyOf(minOf(remaining, chunk.size)) else existing
            val total = tx.responseBodySize + chunk.size
            tx.copy(responseBody = body, responseBodySize = total, isResponseBodyTruncated = total > limit)
        }
    }

    /** Counts bytes that were streamed past the cap without storing them. */
    fun countBody(id: String, byteCount: Int) {
        if (byteCount <= 0) return
        val limit = configuration.bodySizeLimit
        store.upsert(id) { tx ->
            val total = tx.responseBodySize + byteCount
            tx.copy(responseBodySize = total, isResponseBodyTruncated = total > limit)
        }
    }

    /** Sets the whole response body at once (used by engines that buffer). */
    fun setResponseBody(id: String, body: ByteArray?, totalSize: Long, truncated: Boolean) {
        val limit = configuration.bodySizeLimit
        val capped = body?.let { if (it.size > limit) it.copyOf(limit) else it }
        store.upsert(id) {
            it.copy(
                responseBody = capped,
                responseBodySize = totalSize,
                isResponseBodyTruncated = truncated || totalSize > limit,
            )
        }
    }

    fun redirect(id: String, fromUrl: String, toUrl: String, statusCode: Int) {
        store.upsert(id) { it.copy(redirects = it.redirects + Redirect(fromUrl, toUrl, statusCode)) }
    }

    fun complete(id: String, error: String?) {
        if (store.get(id)?.state != TransactionState.InProgress) return
        var changed = false
        val updated = store.upsert(id) { tx ->
            if (tx.state != TransactionState.InProgress) {
                changed = false
                tx
            } else {
                changed = true
                tx.copy(
                    responseTimeMs = clock(),
                    state = if (error != null) TransactionState.Failed else TransactionState.Completed,
                    errorDescription = error ?: tx.errorDescription,
                )
            }
        } ?: return
        if (changed) onComplete(updated)
    }

    fun isInProgress(id: String): Boolean = store.get(id)?.state == TransactionState.InProgress
}
