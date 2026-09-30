package com.pantauhttp

import com.pantauhttp.export.HarExporter
import com.pantauhttp.export.PantauHttpExports
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportersTest {

    @Test
    fun simpleGetCurl() {
        val curl = PantauHttpExports.curl(sampleTransaction(url = "https://api.example.com/users"))
        assertTrue(curl.startsWith("curl -v"))
        assertFalse(curl.contains("-X GET"))
        assertTrue(curl.contains("'https://api.example.com/users'"))
        assertTrue(curl.contains("--compressed"))
    }

    @Test
    fun postWithHeadersAndBodyCurl() {
        val curl = PantauHttpExports.curl(
            sampleTransaction(method = "POST", headers = mapOf("Content-Type" to "application/json"), body = """{"name":"test"}""".encodeToByteArray()),
        )
        assertTrue(curl.contains("-X POST"))
        assertTrue(curl.contains("-H 'Content-Type: application/json'"))
        assertTrue(curl.contains("""--data '{"name":"test"}'"""))
    }

    @Test
    fun singleQuoteEscapingCurl() {
        val curl = PantauHttpExports.curl(sampleTransaction(method = "POST", body = """{"note":"it's fine"}""".encodeToByteArray()))
        assertTrue(curl.contains("""it'\''s fine"""))
    }

    @Test
    fun binaryBodyPlaceholderCurl() {
        val curl = PantauHttpExports.curl(sampleTransaction(method = "POST", body = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)))
        assertTrue(curl.contains("binary data"))
    }

    @Test
    fun textExport() {
        val tx = sampleTransaction(method = "POST", body = "{\"a\":1}".encodeToByteArray(), headers = mapOf("Content-Type" to "application/json"))
            .copy(statusCode = 201, state = TransactionState.Completed, responseTimeMs = 1_753_776_000_500L, isResponseBodyTruncated = true, responseBody = "x".encodeToByteArray())
        val text = PantauHttpExports.text(tx)
        assertTrue(text.startsWith("=== HTTP Transaction ==="))
        assertTrue(text.contains("Requested at: 2025-07-29T08:00:00Z"))
        assertTrue(text.contains("Duration: 500 ms"))
        assertTrue(text.contains("--- Request Body ---\n{\n  \"a\": 1\n}"))
        assertTrue(text.contains("x\n(body truncated)"))
        assertTrue(text.contains("State: completed"))
    }

    @Test
    fun harExport() {
        val tx = sampleTransaction(
            url = "https://api.example.com/users?page=2",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"a":1}""".encodeToByteArray(),
        ).copy(
            statusCode = 201,
            state = TransactionState.Completed,
            responseHeaders = mapOf("Content-Type" to "application/json"),
            responseBody = """{"id":7}""".encodeToByteArray(),
            responseBodySize = 8,
            responseTimeMs = 1_753_776_000_500L,
        )
        val har = HarExporter.decode(PantauHttpExports.har(tx))
        assertEquals("1.2", har.log.version)
        assertEquals(1, har.log.entries.size)
        val entry = har.log.entries.first()
        assertEquals("POST", entry.request.method)
        assertEquals("https://api.example.com/users?page=2", entry.request.url)
        assertEquals("page", entry.request.queryString.first().name)
        assertEquals("2", entry.request.queryString.first().value)
        assertEquals("""{"a":1}""", entry.request.postData?.text)
        assertEquals(201, entry.response.status)
        assertEquals("Created", entry.response.statusText)
        assertEquals("""{"id":7}""", entry.response.content.text)
        assertEquals(500.0, entry.time)
        assertEquals("2025-07-29T08:00:00Z", entry.startedDateTime)
    }

    @Test
    fun harExportMultiple() {
        val har = HarExporter.decode(PantauHttpExports.har(listOf(sampleTransaction(url = "https://a.com", id = "1"), sampleTransaction(url = "https://b.com", id = "2"))))
        assertEquals(2, har.log.entries.size)
    }

    @Test
    fun harJsonHasSortedKeysAndPrettyPrint() {
        val json = PantauHttpExports.har(sampleTransaction())
        assertTrue(json.startsWith("{\n  \"log\": {\n    \"creator\""))
    }
}
