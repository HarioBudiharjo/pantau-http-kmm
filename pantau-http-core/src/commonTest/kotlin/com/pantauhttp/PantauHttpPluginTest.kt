package com.pantauhttp

import com.pantauhttp.ktor.PANTAU_TRACE_HEADER
import com.pantauhttp.ktor.PantauHttpPlugin
import com.pantauhttp.ktor.PantauInternalKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PantauHttpPluginTest {

    @BeforeTest
    fun start() {
        PantauHttpCore.clearTransactions()
        PantauHttpCore.start(
            PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never, bodySizeLimit = 64),
        )
    }

    @AfterTest
    fun stop() {
        PantauHttpCore.stop()
        PantauHttpCore.clearTransactions()
    }

    /** Real-time wait (runTest's virtual clock would skip straight to the timeout). */
    private suspend fun awaitCompleted(count: Int = 1): List<HttpTransaction> = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            while (true) {
                val done = PantauHttpCore.transactions.value.filter { it.state != TransactionState.InProgress }
                if (done.size >= count) break
                delay(5)
            }
        }
        PantauHttpCore.transactions.value
    }

    private suspend fun realDelay(ms: Long) = withContext(Dispatchers.Default) { delay(ms) }

    private fun client(handler: MockRequestHandler) =
        HttpClient(MockEngine(handler)) {
            install(PantauHttpPlugin)
            expectSuccess = false
        }

    @Test
    fun recordsRequestAndResponse() = runTest {
        val client = client { request ->
            assertNotNull(request.headers[PANTAU_TRACE_HEADER], "trace header marks recorded requests")
            respond("""{"ok":true}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val response = client.post("https://api.example.com/users?page=1") {
            header("Authorization", "Bearer secret")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"pantau"}""")
        }
        assertEquals("""{"ok":true}""", response.bodyAsText(), "app still receives the body")

        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Completed, tx.state)
        assertEquals("POST", tx.method)
        assertEquals("https://api.example.com/users?page=1", tx.url)
        assertEquals(201, tx.statusCode)
        assertEquals("••••••••", tx.requestHeaders["Authorization"])
        assertEquals("application/json", tx.requestContentType)
        assertNull(tx.requestHeaders.keys.firstOrNull { it.equals(PANTAU_TRACE_HEADER, true) }, "trace header is not stored")
        assertContentEquals("""{"name":"pantau"}""".encodeToByteArray(), tx.requestBody)
        assertContentEquals("""{"ok":true}""".encodeToByteArray(), tx.responseBody)
        assertEquals(11L, tx.responseBodySize)
        assertEquals("application/json", tx.responseContentType)
        assertFalse(tx.isResponseBodyTruncated)
        assertNotNull(tx.durationMs)
    }

    @Test
    fun capsLargeBodiesButDeliversThemWhole() = runTest {
        val big = ByteArray(10_000) { (it % 251).toByte() }
        val client = client { respond(ByteReadChannel(big), HttpStatusCode.OK) }
        val received: ByteArray = client.get("https://cdn.example.com/blob").body()
        assertContentEquals(big, received)

        val tx = awaitCompleted().single()
        assertEquals(10_000L, tx.responseBodySize)
        assertTrue(tx.isResponseBodyTruncated)
        assertEquals(64, tx.responseBody!!.size)
        assertContentEquals(big.copyOf(64), tx.responseBody)
    }

    @Test
    fun capsLargeRequestBodies() = runTest {
        val payload = "x".repeat(200)
        val client = client { request ->
            assertEquals(200, request.body.contentLength)
            respond("", HttpStatusCode.NoContent)
        }
        client.post("https://api.example.com/upload") { setBody(payload) }
        val tx = awaitCompleted().single()
        assertEquals(200L, tx.requestBodySize)
        assertTrue(tx.isRequestBodyTruncated)
        assertEquals(64, tx.requestBody!!.size)
    }

    @Test
    fun streamedWriteBodyOfUnknownLengthIsTeed() = runTest {
        val payload = ByteArray(5_000) { (it % 97).toByte() }
        var received: ByteArray? = null
        val client = client { request ->
            received = request.body.toByteArray()
            respond("", HttpStatusCode.NoContent)
        }
        client.post("https://api.example.com/stream") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long? get() = null
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    payload.toList().chunked(1_000).forEach { channel.writeFully(it.toByteArray()) }
                }
            })
        }
        assertContentEquals(payload, received, "engine receives the whole body untouched")
        val tx = awaitCompleted().single()
        withTimeout(2_000) { while (PantauHttpCore.transaction(tx.id)!!.requestBodySize == 0L) realDelay(5) }
        val patched = PantauHttpCore.transaction(tx.id)!!
        assertEquals(5_000L, patched.requestBodySize)
        assertTrue(patched.isRequestBodyTruncated)
        assertContentEquals(payload.copyOf(64), patched.requestBody)
    }

    @Test
    fun streamedReadBodyOfUnknownLengthIsTeed() = runTest {
        val payload = ByteArray(3_000) { (it % 89).toByte() }
        var received: ByteArray? = null
        val client = client { request ->
            received = request.body.toByteArray()
            respond("", HttpStatusCode.NoContent)
        }
        client.post("https://api.example.com/stream") {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom(): ByteReadChannel = ByteReadChannel(payload)
            })
        }
        assertContentEquals(payload, received)
        val tx = awaitCompleted().single()
        withTimeout(2_000) { while (PantauHttpCore.transaction(tx.id)!!.requestBodySize == 0L) realDelay(5) }
        val patched = PantauHttpCore.transaction(tx.id)!!
        assertEquals(3_000L, patched.requestBodySize)
        assertTrue(patched.isRequestBodyTruncated)
        assertEquals(64, patched.requestBody!!.size)
    }

    @Test
    fun failedRequestIsRecordedAsFailed() = runTest {
        val client = client { throw IllegalStateException("Could not resolve host") }
        assertFailsWith<IllegalStateException> { client.get("https://nope.invalid/") }
        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Failed, tx.state)
        assertEquals("Could not resolve host", tx.errorDescription)
        assertEquals("!!!", tx.statusText)
    }

    @Test
    fun expectSuccessFailureKeepsStatus() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound, "missing") }) {
            install(PantauHttpPlugin)
            expectSuccess = true
        }
        assertFailsWith<ClientRequestException> { client.get("https://api.example.com/missing") }
        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Completed, tx.state)
        assertEquals(404, tx.statusCode)
    }

    @Test
    fun redirectHopsAreSeparateTransactions() = runTest {
        var calls = 0
        val client = client { request ->
            calls++
            if (request.url.encodedPath == "/old") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/new"))
            } else {
                respond("done", HttpStatusCode.OK)
            }
        }
        assertEquals("done", client.get("https://api.example.com/old").bodyAsText())
        assertEquals(2, calls)

        val txs = awaitCompleted(2)
        assertEquals(2, txs.size)
        val first = txs.last()
        val second = txs.first()
        assertEquals(302, first.statusCode)
        assertEquals("/old", first.path)
        assertEquals(1, first.redirects.size)
        assertEquals("https://api.example.com/new", first.redirects.single().toUrl)
        assertEquals(200, second.statusCode)
        assertEquals("/new", second.path)
    }

    @Test
    fun internalRequestsAndIgnoredHostsAreSkipped() = runTest {
        PantauHttpCore.start(
            PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never, ignoredHosts = setOf("Analytics.Example.com")),
        )
        val client = client { request ->
            assertNull(request.headers[PANTAU_TRACE_HEADER])
            respond("ok")
        }
        client.get("https://analytics.example.com/track")
        client.get("https://dashboard.local/api/ingest") { attributes.put(PantauInternalKey, true) }
        realDelay(50)
        assertTrue(PantauHttpCore.transactions.value.isEmpty())
    }

    @Test
    fun nothingRecordedWhenStopped() = runTest {
        PantauHttpCore.stop()
        val client = client { respond("ok") }
        client.get("https://api.example.com/")
        realDelay(50)
        assertTrue(PantauHttpCore.transactions.value.isEmpty())
    }
}
