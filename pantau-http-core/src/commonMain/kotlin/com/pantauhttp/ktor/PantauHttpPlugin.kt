package com.pantauhttp.ktor

import com.pantauhttp.PantauHttpCore
import com.pantauhttp.internal.store.TransactionRecorder
import com.pantauhttp.shouldCapture
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray

/** Options for [PantauHttpPlugin]. */
public class PantauHttpPluginConfig {
    /** Master switch, e.g. `enabled = BuildConfig.DEBUG`. */
    public var enabled: Boolean = true

    /**
     * Adds an `X-PantauHTTP-Trace` header so the native engines (OkHttp interceptor,
     * iOS URLProtocol) know this request is already recorded and pass it through.
     * Disable if the header must never reach a server; you may then see duplicates
     * when both a native engine and this plugin observe the same client.
     */
    public var markRequests: Boolean = true
}

/** Requests carrying this attribute are never recorded (used for the library's own traffic). */
public val PantauInternalKey: AttributeKey<Boolean> = AttributeKey("PantauHttpInternal")

/** Transaction id assigned by the plugin for the current request attempt. */
internal val PantauTransactionIdKey: AttributeKey<String> = AttributeKey("PantauHttpTransactionId")

public const val PANTAU_TRACE_HEADER: String = "X-PantauHTTP-Trace"
public const val PANTAU_INTERNAL_HEADER: String = "X-PantauHTTP-Internal"

/**
 * Ktor client plugin that records every request/response of the client it is
 * installed in. Each redirect hop and retry attempt becomes its own transaction.
 *
 * ```kotlin
 * val client = HttpClient { install(PantauHttpPlugin) }
 * ```
 */
public val PantauHttpPlugin: ClientPlugin<PantauHttpPluginConfig> =
    createClientPlugin("PantauHttp", ::PantauHttpPluginConfig) {
        val enabled = pluginConfig.enabled
        val markRequests = pluginConfig.markRequests

        on(Send) { request ->
            if (!enabled || !PantauHttpCore.isStarted ||
                request.attributes.contains(PantauInternalKey) ||
                !PantauHttpCore.configuration.shouldCapture(request.url.host)
            ) {
                return@on proceed(request)
            }

            val recorder = PantauHttpCore.recorder
            val id = recorder.beginFrom(request)
            request.attributes.put(PantauTransactionIdKey, id)
            if (markRequests) request.headers.append(PANTAU_TRACE_HEADER, id)

            val call = try {
                proceed(request)
            } catch (e: ResponseException) {
                // expectSuccess validation consumed the response; keep the status.
                recorder.recordResponse(id, e.response)
                recorder.complete(id, error = null)
                throw e
            } catch (e: CancellationException) {
                recorder.complete(id, error = "Cancelled")
                throw e
            } catch (t: Throwable) {
                recorder.complete(id, error = t.message ?: t::class.simpleName ?: "Unknown error")
                throw t
            }
            recorder.recordResponse(id, call.response)
            call
        }

        ResponseObserver.install(
            ResponseObserver.prepare {
                filter { call -> enabled && call.attributes.contains(PantauTransactionIdKey) }
                onResponse { response ->
                    val id = response.call.attributes[PantauTransactionIdKey]
                    val recorder = PantauHttpCore.recorder
                    val limit = PantauHttpCore.configuration.bodySizeLimit
                    try {
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(16 * 1024)
                        var captured = 0
                        while (true) {
                            val read = channel.readAvailable(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            if (captured < limit) {
                                val take = minOf(read, limit - captured)
                                recorder.appendBody(id, buffer.copyOf(take))
                                if (read > take) recorder.countBody(id, read - take)
                                captured += read
                            } else {
                                recorder.countBody(id, read)
                            }
                        }
                        recorder.complete(id, error = null)
                    } catch (e: CancellationException) {
                        recorder.complete(id, error = null)
                        throw e
                    } catch (t: Throwable) {
                        recorder.complete(id, error = t.message ?: t::class.simpleName)
                    }
                }
            },
            client,
        )
    }

// ---- helpers -------------------------------------------------------------

private suspend fun TransactionRecorder.beginFrom(request: HttpRequestBuilder): String {
    val content = request.body as? OutgoingContent
    val snapshot = content?.snapshot(PantauHttpCore.configuration.bodySizeLimit)
    snapshot?.replacement?.let { request.setBody<OutgoingContent>(it) }
    val headers = request.headers.build().flatten().toMutableMap()
    content?.let { headers.putAll(it.contentHeaders()) }
    return begin(
        method = request.method.value,
        url = request.url.buildString(),
        headers = headers,
        body = snapshot?.bytes,
        bodySize = snapshot?.totalSize ?: 0L,
        truncated = snapshot?.truncated ?: false,
    )
}

private fun TransactionRecorder.recordResponse(id: String, response: HttpResponse) {
    val finalHeaders = response.request.headers.flatten().toMutableMap()
    finalHeaders.keys.removeAll { it.equals(PANTAU_TRACE_HEADER, ignoreCase = true) }
    response.request.content.contentHeaders().forEach { (k, v) -> finalHeaders.putIfAbsent(k, v) }
    response(id, response.status.value, response.headers.flatten(), finalRequestHeaders = finalHeaders)
    if (response.status.value in 300..399) {
        response.headers[HttpHeaders.Location]?.let { location ->
            val from = response.request.url.toString()
            val to = runCatching { URLBuilder(response.request.url).takeFrom(location).buildString() }.getOrDefault(location)
            redirect(id, from, to, response.status.value)
        }
    }
}

internal fun Headers.flatten(): Map<String, String> =
    entries().associate { (name, values) -> name to values.joinToString(", ") }

private fun OutgoingContent.contentHeaders(): Map<String, String> {
    val result = headers.flatten().toMutableMap()
    contentType?.let { result.putIfAbsent(HttpHeaders.ContentType, it.toString()) }
    contentLength?.let { result.putIfAbsent(HttpHeaders.ContentLength, it.toString()) }
    return result
}

private fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V) {
    if (!containsKey(key)) put(key, value)
}

private class BodySnapshot(
    val bytes: ByteArray?,
    val totalSize: Long,
    val truncated: Boolean,
    /** Non-null when the original body was consumed and must be replaced. */
    val replacement: OutgoingContent? = null,
)

/** Byte-array content that preserves the original content's metadata. */
private class ReplayableContent(
    private val bytes: ByteArray,
    private val original: OutgoingContent,
) : OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType? get() = original.contentType
    override val contentLength: Long get() = bytes.size.toLong()
    override val status: HttpStatusCode? get() = original.status
    override val headers: Headers get() = original.headers
    override fun bytes(): ByteArray = bytes
}

private suspend fun OutgoingContent.snapshot(limit: Int): BodySnapshot? = when (this) {
    is OutgoingContent.NoContent -> null
    is OutgoingContent.ProtocolUpgrade -> null
    is OutgoingContent.ByteArrayContent -> {
        val bytes = bytes()
        BodySnapshot(bytes, bytes.size.toLong(), truncated = false)
    }
    is OutgoingContent.ReadChannelContent -> {
        val length = contentLength
        if (length != null && length <= limit) {
            val bytes = readFrom().readRemaining().readByteArray()
            BodySnapshot(bytes, bytes.size.toLong(), truncated = false, replacement = ReplayableContent(bytes, this))
        } else {
            BodySnapshot(null, length ?: 0L, truncated = true)
        }
    }
    is OutgoingContent.WriteChannelContent -> {
        val length = contentLength
        if (length != null && length <= limit) {
            val bytes = drain()
            BodySnapshot(bytes, bytes.size.toLong(), truncated = false, replacement = ReplayableContent(bytes, this))
        } else {
            BodySnapshot(null, length ?: 0L, truncated = true)
        }
    }
    is OutgoingContent.ContentWrapper -> delegate().snapshot(limit)
}

private suspend fun OutgoingContent.WriteChannelContent.drain(): ByteArray = coroutineScope {
    val channel = ByteChannel(autoFlush = true)
    launch {
        try {
            writeTo(channel)
            channel.flushAndClose()
        } catch (t: Throwable) {
            channel.cancel(t)
        }
    }
    channel.readRemaining().readByteArray()
}
