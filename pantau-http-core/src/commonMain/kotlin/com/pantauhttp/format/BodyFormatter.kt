package com.pantauhttp.format

import com.pantauhttp.internal.format.JsonPretty

/** Turns raw body bytes into a human-readable string for display and export. */
public object BodyFormatter {

    /** Pretty-prints JSON bodies, falls back to UTF-8 text, then to a binary placeholder. */
    public fun prettyPrinted(body: ByteArray?, contentType: String?): String {
        if (body == null || body.isEmpty()) return ""
        if (isJson(contentType) || looksLikeJson(body)) {
            JsonPretty.prettySorted(body)?.let { return it }
        }
        utf8OrNull(body)?.let { return it }
        return "<binary body, ${ByteCount.binary(body.size.toLong())}>"
    }

    public fun isJson(contentType: String?): Boolean {
        val lowered = contentType?.lowercase() ?: return false
        return lowered.contains("application/json") || lowered.contains("+json")
    }

    public fun isImage(contentType: String?): Boolean =
        contentType?.lowercase()?.startsWith("image/") == true

    /** Decodes as UTF-8, or returns null when the bytes are not valid UTF-8. */
    public fun utf8OrNull(bytes: ByteArray): String? =
        runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    private fun looksLikeJson(body: ByteArray): Boolean {
        val first = body.firstOrNull { !isWhitespace(it) } ?: return false
        return first == '{'.code.toByte() || first == '['.code.toByte()
    }

    private fun isWhitespace(byte: Byte): Boolean =
        byte == 0x20.toByte() || byte == 0x09.toByte() || byte == 0x0A.toByte() || byte == 0x0D.toByte()
}
