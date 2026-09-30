package com.pantauhttp.internal.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Deterministic pretty JSON: two-space indent, object keys sorted recursively. */
internal object JsonPretty {

    private val parser = Json { isLenient = false; ignoreUnknownKeys = true }
    internal val printer: Json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun prettySorted(raw: ByteArray): String? {
        val text = runCatching { raw.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
        val element = runCatching { parser.parseToJsonElement(text) }.getOrNull() ?: return null
        return printer.encodeToString(JsonElement.serializer(), sortKeys(element))
    }

    fun sortKeys(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sortKeys(it.value) })
        is JsonArray -> JsonArray(element.map(::sortKeys))
        else -> element
    }
}
