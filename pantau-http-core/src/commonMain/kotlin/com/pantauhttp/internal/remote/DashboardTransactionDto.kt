package com.pantauhttp.internal.remote

import com.pantauhttp.HttpTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

@Serializable
internal data class RedirectDto(val fromURL: String, val toURL: String, val statusCode: Int)

/**
 * The wire format sent to the dashboard server. Explicit fields, never derived
 * from the model's serializer, so the JavaScript side has a stable contract.
 */
@Serializable
internal data class DashboardTransactionDto(
    val id: String,
    val state: String,
    val method: String,
    val url: String?,
    val host: String?,
    val path: String,
    val requestHeaders: Map<String, String>,
    val requestBodyBase64: String?,
    val requestBodySize: Long,
    val isRequestBodyTruncated: Boolean,
    val requestDate: Long,
    val statusCode: Int?,
    val responseHeaders: Map<String, String>,
    val responseBodyBase64: String?,
    val responseBodySize: Long,
    val isResponseBodyTruncated: Boolean,
    val responseDate: Long?,
    val errorDescription: String?,
    val redirects: List<RedirectDto>,
    val durationMs: Double?,
    val device: DeviceIdentity,
) {
    companion object {
        /** Omits null keys, like Foundation's JSONEncoder does for optionals. */
        val json: Json = Json { explicitNulls = false; encodeDefaults = true }

        fun from(tx: HttpTransaction, device: DeviceIdentity): DashboardTransactionDto = DashboardTransactionDto(
            id = tx.id.lowercase(),
            state = tx.state.wireName,
            method = tx.method,
            url = tx.url.ifEmpty { null },
            host = tx.host,
            path = tx.path,
            requestHeaders = tx.requestHeaders,
            requestBodyBase64 = tx.requestBody?.let(Base64::encode),
            requestBodySize = tx.requestBodySize,
            isRequestBodyTruncated = tx.isRequestBodyTruncated,
            requestDate = tx.requestTimeMs,
            statusCode = tx.statusCode,
            responseHeaders = tx.responseHeaders,
            responseBodyBase64 = tx.responseBody?.let(Base64::encode),
            responseBodySize = tx.responseBodySize,
            isResponseBodyTruncated = tx.isResponseBodyTruncated,
            responseDate = tx.responseTimeMs,
            errorDescription = tx.errorDescription,
            redirects = tx.redirects.map { RedirectDto(it.fromUrl, it.toUrl, it.statusCode) },
            durationMs = tx.durationMs?.toDouble(),
            device = device,
        )
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}
