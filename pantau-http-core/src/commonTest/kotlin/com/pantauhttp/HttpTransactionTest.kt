package com.pantauhttp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpTransactionTest {

    @Test
    fun capturesRequestFields() {
        val tx = sampleTransaction(method = "POST", headers = mapOf("Content-Type" to "application/json"), body = "hello".encodeToByteArray())
        assertEquals("POST", tx.method)
        assertEquals("api.example.com", tx.host)
        assertEquals(5L, tx.requestBodySize)
        assertEquals("application/json", tx.requestContentType)
        assertEquals(TransactionState.InProgress, tx.state)
        assertTrue(tx.isSecure)
    }

    @Test
    fun pathIncludesQuery() {
        assertEquals("/v1/users?page=2", sampleTransaction().path)
        assertEquals("/", sampleTransaction(url = "https://a.com").path)
    }

    @Test
    fun durationDerivedFromTimes() {
        val tx = sampleTransaction()
        assertNull(tx.durationMs)
        assertEquals("", tx.durationText)
        assertEquals("250 ms", tx.copy(responseTimeMs = tx.requestTimeMs + 250).durationText)
        assertEquals("1.25 s", tx.copy(responseTimeMs = tx.requestTimeMs + 1250).durationText)
        assertEquals("1.26 s", tx.copy(responseTimeMs = tx.requestTimeMs + 1256).durationText)
        assertEquals("2.00 s", tx.copy(responseTimeMs = tx.requestTimeMs + 1995).durationText)
    }

    @Test
    fun statusText() {
        val tx = sampleTransaction()
        assertEquals("…", tx.statusText)
        assertEquals("200", tx.copy(statusCode = 200, state = TransactionState.Completed).statusText)
        assertEquals("?", tx.copy(state = TransactionState.Completed).statusText)
        assertEquals("!!!", tx.copy(statusCode = 200, state = TransactionState.Failed).statusText)
    }

    @Test
    fun matchesQuery() {
        val tx = sampleTransaction().copy(statusCode = 404)
        assertTrue(tx.matches(""))
        assertTrue(tx.matches("get"))
        assertTrue(tx.matches("example.com"))
        assertTrue(tx.matches("users"))
        assertTrue(tx.matches("404"))
        assertFalse(tx.matches("delete"))
        assertFalse(tx.matches("500"))
    }

    @Test
    fun sizeText() {
        assertEquals("Zero KB", sampleTransaction().sizeText)
        assertEquals("2 KB", sampleTransaction().copy(responseBodySize = 1536).sizeText)
        assertEquals("1.5 MB", sampleTransaction().copy(responseBodySize = 1_572_864).sizeText)
    }
}
