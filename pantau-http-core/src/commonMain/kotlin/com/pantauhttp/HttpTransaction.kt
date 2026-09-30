package com.pantauhttp

import com.pantauhttp.format.ByteCount
import com.pantauhttp.internal.platform.formatClockTime
import com.pantauhttp.internal.platform.formatMediumDateTime
import io.ktor.http.Url

/** Lifecycle of a recorded transaction. */
public enum class TransactionState(public val wireName: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
}

/** One redirect hop observed while loading a transaction. */
public data class Redirect(
    val fromUrl: String,
    val toUrl: String,
    val statusCode: Int,
)

/**
 * A single recorded HTTP request/response pair.
 *
 * Immutable on purpose: the store is the single owner and publishes a new copy
 * for every change, so readers on any thread always see a consistent snapshot.
 */
public data class HttpTransaction(
    /** Lowercase UUID string, stable for the life of the transaction. */
    val id: String,
    val state: TransactionState = TransactionState.InProgress,

    // Request
    val method: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: ByteArray? = null,
    val requestBodySize: Long = 0,
    val isRequestBodyTruncated: Boolean = false,
    /** Epoch milliseconds when the request was issued. */
    val requestTimeMs: Long,

    // Response
    val statusCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: ByteArray? = null,
    val responseBodySize: Long = 0,
    val isResponseBodyTruncated: Boolean = false,
    /** Epoch milliseconds when the response completed (or failed). */
    val responseTimeMs: Long? = null,

    val errorDescription: String? = null,
    val redirects: List<Redirect> = emptyList(),
) {
    private val parsedUrl: Url? by lazy { runCatching { Url(url) }.getOrNull() }

    public val host: String? get() = parsedUrl?.host?.takeIf { it.isNotEmpty() }

    /** Path plus query string, "/" when the URL has an empty path. */
    public val path: String
        get() {
            val parsed = parsedUrl ?: return ""
            val rawPath = parsed.encodedPath.ifEmpty { "/" }
            val query = parsed.encodedQuery
            return if (query.isNotEmpty()) "$rawPath?$query" else rawPath
        }

    public val isSecure: Boolean get() = parsedUrl?.protocol?.name == "https"

    public val durationMs: Long? get() = responseTimeMs?.let { it - requestTimeMs }

    public val requestContentType: String? get() = requestHeaders.contentType()

    public val responseContentType: String? get() = responseHeaders.contentType()

    public val statusText: String
        get() = when (state) {
            TransactionState.InProgress -> "…"
            TransactionState.Failed -> "!!!"
            TransactionState.Completed -> statusCode?.toString() ?: "?"
        }

    /** "250 ms" under one second, otherwise "1.25 s". Empty while in progress. */
    public val durationText: String
        get() {
            val ms = durationMs ?: return ""
            if (ms < 1000) return "$ms ms"
            val hundredths = (ms + 5) / 10
            val whole = hundredths / 100
            val frac = (hundredths % 100).toString().padStart(2, '0')
            return "$whole.$frac s"
        }

    public val sizeText: String get() = ByteCount.binary(responseBodySize)

    /** Local wall-clock time of the request, "HH:mm:ss". */
    public val timeText: String get() = formatClockTime(requestTimeMs)

    /** Localized medium date and time of the request. */
    public val requestDateText: String get() = formatMediumDateTime(requestTimeMs)

    /** Localized medium date and time of the response, or null while in progress. */
    public val responseDateText: String? get() = responseTimeMs?.let(::formatMediumDateTime)

    /** Case-insensitive match on method or URL, substring match on the status code. */
    public fun matches(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (method.contains(trimmed, ignoreCase = true)) return true
        if (url.contains(trimmed, ignoreCase = true)) return true
        val code = statusCode ?: return false
        return code.toString().contains(trimmed)
    }

    private fun Map<String, String>.contentType(): String? =
        entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
}
