package com.pantauhttp

import com.pantauhttp.internal.store.TransactionStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {

    @Test
    fun defaultRedactedHeaders() {
        val redacted = PantauHttpConfiguration().redact(
            mapOf("Authorization" to "Bearer secret-token", "Cookie" to "session=abc", "Content-Type" to "application/json"),
        )
        assertEquals("••••••••", redacted["Authorization"])
        assertEquals("••••••••", redacted["Cookie"])
        assertEquals("application/json", redacted["Content-Type"])
    }

    @Test
    fun redactionIsCaseInsensitive() {
        assertEquals("••••••••", PantauHttpConfiguration().redact(mapOf("authorization" to "Bearer secret"))["authorization"])
    }

    @Test
    fun customRedactedHeaders() {
        val configuration = PantauHttpConfiguration(redactedHeaders = setOf("X-Api-Key"))
        val redacted = configuration.redact(mapOf("X-API-KEY" to "12345", "Authorization" to "Bearer visible-now"))
        assertEquals("••••••••", redacted["X-API-KEY"])
        assertEquals("Bearer visible-now", redacted["Authorization"])
    }

    @Test
    fun ignoredHosts() {
        val configuration = PantauHttpConfiguration(ignoredHosts = setOf("Analytics.Example.com"))
        assertFalse(configuration.shouldCapture("analytics.example.com"))
        assertTrue(configuration.shouldCapture("api.example.com"))
        assertTrue(configuration.shouldCapture(null))
    }

    @Test
    fun recorderAppliesRedactionAndCaps() {
        val store = TransactionStore()
        val recorder = recorderFor(store, PantauHttpConfiguration(bodySizeLimit = 4))
        val id = recorder.begin(
            method = "POST",
            url = "https://example.com/login",
            headers = mapOf("Authorization" to "Bearer secret"),
            body = "123456789".encodeToByteArray(),
        )
        val tx = store.get(id)!!
        assertEquals("••••••••", tx.requestHeaders["Authorization"])
        assertContentEquals("1234".encodeToByteArray(), tx.requestBody)
        assertEquals(9L, tx.requestBodySize)
        assertTrue(tx.isRequestBodyTruncated)
    }

    @Test
    fun recorderCapsStreamedResponseBody() {
        val store = TransactionStore()
        val recorder = recorderFor(store, PantauHttpConfiguration(bodySizeLimit = 10))
        val id = recorder.begin("GET", "https://example.com/big", emptyMap())
        recorder.appendBody(id, "12345678".encodeToByteArray())
        recorder.appendBody(id, "ABCDEFGH".encodeToByteArray())
        val tx = store.get(id)!!
        assertContentEquals("12345678AB".encodeToByteArray(), tx.responseBody)
        assertEquals(16L, tx.responseBodySize)
        assertTrue(tx.isResponseBodyTruncated)
    }

    @Test
    fun recorderRedactsResponseHeadersAndCompletesOnce() {
        val store = TransactionStore()
        var completions = 0
        var now = 1_000L
        val recorder = recorderFor(store, clock = { now }, onComplete = { completions++ })
        val id = recorder.begin("GET", "https://example.com/", emptyMap())
        recorder.response(id, 200, mapOf("Set-Cookie" to "a=b", "Content-Type" to "text/plain"))
        now = 1_250L
        recorder.complete(id, null)
        recorder.complete(id, "late error")
        val tx = store.get(id)!!
        assertEquals("••••••••", tx.responseHeaders["Set-Cookie"])
        assertEquals(TransactionState.Completed, tx.state)
        assertEquals(250L, tx.durationMs)
        assertEquals(1, completions)
    }

    @Test
    fun recorderFailure() {
        val store = TransactionStore()
        val recorder = recorderFor(store)
        val id = recorder.begin("GET", "https://nope.invalid/", emptyMap())
        recorder.complete(id, "Could not resolve host")
        val tx = store.get(id)!!
        assertEquals(TransactionState.Failed, tx.state)
        assertEquals("!!!", tx.statusText)
        assertEquals("Could not resolve host", tx.errorDescription)
    }
}
