package com.pantauhttp.export

import com.pantauhttp.HttpTransaction
import com.pantauhttp.format.BodyFormatter

internal object CurlExporter {

    /** Builds a runnable `curl` command. Headers arrive already redacted from the store. */
    fun export(transaction: HttpTransaction): String {
        val parts = mutableListOf("curl -v")
        val method = transaction.method.uppercase()
        if (method != "GET") parts += "-X $method"

        transaction.requestHeaders.entries.sortedBy { it.key }.forEach { (key, value) ->
            parts += "-H ${escaped("$key: $value")}"
        }

        val body = transaction.requestBody
        if (body != null && body.isNotEmpty()) {
            val text = BodyFormatter.utf8OrNull(body)
            parts += if (text != null) "--data ${escaped(text)}" else "--data-binary '<${body.size} bytes of binary data>'"
        }

        parts += "--compressed"
        parts += escaped(transaction.url)
        return parts.joinToString(" \\\n  ")
    }

    /** Single-quote shell escaping: ' -> '\'' inside a single-quoted string. */
    private fun escaped(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
