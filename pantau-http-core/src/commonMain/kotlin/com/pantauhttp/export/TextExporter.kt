package com.pantauhttp.export

import com.pantauhttp.HttpTransaction
import com.pantauhttp.format.BodyFormatter
import com.pantauhttp.internal.format.Iso8601

internal object TextExporter {

    fun export(transaction: HttpTransaction): String {
        val lines = mutableListOf<String>()
        lines += "=== HTTP Transaction ==="
        lines += "URL: ${transaction.url.ifEmpty { "-" }}"
        lines += "Method: ${transaction.method}"
        lines += "Status: ${transaction.statusText}"
        lines += "State: ${transaction.state.wireName}"
        lines += "Requested at: ${Iso8601.format(transaction.requestTimeMs)}"
        transaction.responseTimeMs?.let { lines += "Responded at: ${Iso8601.format(it)}" }
        if (transaction.durationText.isNotEmpty()) lines += "Duration: ${transaction.durationText}"
        transaction.errorDescription?.let { lines += "Error: $it" }
        transaction.redirects.forEach {
            lines += "Redirect (${it.statusCode}): ${it.fromUrl} -> ${it.toUrl}"
        }

        lines += ""
        lines += "--- Request Headers ---"
        lines += headerLines(transaction.requestHeaders)
        lines += ""
        lines += "--- Request Body ---"
        lines += bodyText(transaction.requestBody, transaction.requestContentType, transaction.isRequestBodyTruncated)

        lines += ""
        lines += "--- Response Headers ---"
        lines += headerLines(transaction.responseHeaders)
        lines += ""
        lines += "--- Response Body ---"
        lines += bodyText(transaction.responseBody, transaction.responseContentType, transaction.isResponseBodyTruncated)

        return lines.joinToString("\n")
    }

    private fun headerLines(headers: Map<String, String>): List<String> =
        if (headers.isEmpty()) listOf("(none)")
        else headers.entries.sortedBy { it.key }.map { "${it.key}: ${it.value}" }

    private fun bodyText(body: ByteArray?, contentType: String?, truncated: Boolean): String {
        if (body == null || body.isEmpty()) return "(empty)"
        val text = BodyFormatter.prettyPrinted(body, contentType)
        return if (truncated) "$text\n(body truncated)" else text
    }
}
