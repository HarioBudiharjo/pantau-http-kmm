package com.pantauhttp.export

import com.pantauhttp.HttpTransaction
import com.pantauhttp.format.BodyFormatter
import com.pantauhttp.internal.format.Iso8601
import com.pantauhttp.internal.format.JsonPretty
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/** Exports transactions in HTTP Archive (HAR) 1.2 format. */
internal object HarExporter {

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun export(transactions: List<HttpTransaction>): String {
        val har = Har(HarLog(entries = transactions.map(::entry)))
        val element = json.encodeToJsonElement(Har.serializer(), har)
        return JsonPretty.printer.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), JsonPretty.sortKeys(element))
    }

    fun export(transaction: HttpTransaction): String = export(listOf(transaction))

    fun decode(text: String): Har = Json { ignoreUnknownKeys = true }.decodeFromString(Har.serializer(), text)

    private fun entry(tx: HttpTransaction): HarEntry {
        val durationMs = (tx.durationMs ?: 0L).toDouble()
        val queryItems = runCatching { Url(tx.url) }.getOrNull()
            ?.parameters?.entries()
            ?.flatMap { (name, values) -> values.map { HarHeader(name, it) } }
            ?: emptyList()

        val postData = tx.requestBody?.let { body ->
            BodyFormatter.utf8OrNull(body)?.let { text ->
                HarPostData(mimeType = tx.requestContentType ?: "application/octet-stream", text = text)
            }
        }

        val request = HarRequest(
            method = tx.method,
            url = tx.url,
            headers = harHeaders(tx.requestHeaders),
            queryString = queryItems,
            postData = postData,
            headersSize = -1,
            bodySize = tx.requestBodySize.toInt(),
        )
        val status = tx.statusCode ?: 0
        val response = HarResponse(
            status = status,
            statusText = HttpStatusCode.fromValue(status).description,
            headers = harHeaders(tx.responseHeaders),
            content = HarContent(
                size = tx.responseBodySize.toInt(),
                mimeType = tx.responseContentType ?: "application/octet-stream",
                text = tx.responseBody?.let(BodyFormatter::utf8OrNull),
            ),
            redirectURL = tx.redirects.lastOrNull()?.toUrl ?: "",
            headersSize = -1,
            bodySize = tx.responseBodySize.toInt(),
        )
        return HarEntry(
            startedDateTime = Iso8601.format(tx.requestTimeMs),
            time = durationMs,
            request = request,
            response = response,
            timings = HarTimings(receive = durationMs),
        )
    }

    private fun harHeaders(headers: Map<String, String>): List<HarHeader> =
        headers.entries.sortedBy { it.key }.map { HarHeader(it.key, it.value) }
}
