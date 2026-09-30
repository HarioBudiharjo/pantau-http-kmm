package com.pantauhttp

import com.pantauhttp.android.PantauHttpInterceptor
import com.pantauhttp.ktor.PANTAU_TRACE_HEADER
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PantauHttpInterceptorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(PantauHttpInterceptor())
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()

    @BeforeTest
    fun setUp() {
        server = MockWebServer().also { it.start() }
        PantauHttpCore.clearTransactions()
        PantauHttpCore.start(PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never, bodySizeLimit = 32))
    }

    @AfterTest
    fun tearDown() {
        PantauHttpCore.stop()
        PantauHttpCore.clearTransactions()
        server.shutdown()
    }

    private fun awaitCompleted(count: Int = 1): List<HttpTransaction> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val txs = PantauHttpCore.transactions.value
            if (txs.count { it.state != TransactionState.InProgress } >= count) return txs
            Thread.sleep(5)
        }
        return PantauHttpCore.transactions.value
    }

    @Test
    fun recordsPostWithRedactionAndBodies() {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""{"ok":true}"""))
        val request = Request.Builder()
            .url(server.url("/users?page=1"))
            .header("Authorization", "Bearer secret")
            .post("""{"name":"pantau"}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { assertEquals("""{"ok":true}""", it.body!!.string()) }

        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Completed, tx.state)
        assertEquals("POST", tx.method)
        assertEquals("/users?page=1", tx.path)
        assertEquals(201, tx.statusCode)
        assertEquals("••••••••", tx.requestHeaders["Authorization"])
        assertTrue(tx.requestContentType!!.startsWith("application/json"))
        assertContentEquals("""{"name":"pantau"}""".encodeToByteArray(), tx.requestBody)
        assertContentEquals("""{"ok":true}""".encodeToByteArray(), tx.responseBody)
        assertEquals(11L, tx.responseBodySize)
    }

    @Test
    fun unreadBodyStillCompletesAndIsPatchedWhenRead() {
        server.enqueue(MockResponse().setBody("lazy body").setHeader("Content-Type", "text/plain"))
        val response = client.newCall(Request.Builder().url(server.url("/lazy")).build()).execute()
        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Completed, tx.state, "completes at headers even though nobody read the body")
        assertEquals(200, tx.statusCode)
        assertEquals(9L, tx.responseBodySize, "announced Content-Length")
        assertNull(tx.responseBody)

        assertEquals("lazy body", response.body!!.string())
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && PantauHttpCore.transaction(tx.id)?.responseBody == null) Thread.sleep(5)
        assertContentEquals("lazy body".encodeToByteArray(), PantauHttpCore.transaction(tx.id)!!.responseBody)
    }

    @Test
    fun truncatesLargeResponseBodyButDeliversItWhole() {
        val big = "y".repeat(1000)
        server.enqueue(MockResponse().setBody(big))
        client.newCall(Request.Builder().url(server.url("/big")).build()).execute().use { assertEquals(big, it.body!!.string()) }
        val tx = awaitCompleted().single()
        assertEquals(1000L, tx.responseBodySize)
        assertTrue(tx.isResponseBodyTruncated)
        assertEquals(32, tx.responseBody!!.size)
    }

    @Test
    fun gunzipsCapturedBodyWhenAppHandlesEncoding() {
        val plain = """{"gz":1}"""
        val gz = Buffer().also { GzipSink(it).buffer().use { s -> s.writeUtf8(plain) } }
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(gz))
        val request = Request.Builder().url(server.url("/gz")).header("Accept-Encoding", "gzip").build()
        client.newCall(request).execute().use { it.body!!.bytes() }
        val tx = awaitCompleted().single()
        assertContentEquals(plain.encodeToByteArray(), tx.responseBody)
    }

    @Test
    fun followedRedirectsAreListed() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/new"))
        server.enqueue(MockResponse().setBody("done"))
        client.newCall(Request.Builder().url(server.url("/old")).build()).execute().use { assertEquals("done", it.body!!.string()) }
        val tx = awaitCompleted().single()
        assertEquals(200, tx.statusCode)
        assertEquals(1, tx.redirects.size)
        assertEquals(302, tx.redirects[0].statusCode)
        assertTrue(tx.redirects[0].toUrl.endsWith("/new"))
    }

    @Test
    fun connectionFailureIsRecordedAsFailed() {
        val port = server.port
        server.shutdown()
        assertFailsWith<IOException> {
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/down").build()).execute()
        }
        val tx = awaitCompleted().single()
        assertEquals(TransactionState.Failed, tx.state)
        assertTrue(!tx.errorDescription.isNullOrBlank())
        server = MockWebServer().also { it.start() }
    }

    @Test
    fun traceHeaderMeansPassThrough() {
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/traced")).header(PANTAU_TRACE_HEADER, "abc").build()).execute().close()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader(PANTAU_TRACE_HEADER), "marker is stripped before it reaches the network")
        Thread.sleep(50)
        assertTrue(PantauHttpCore.transactions.value.isEmpty(), "Ktor plugin already recorded it")
    }

    @Test
    fun ignoredHostAndInternalHeaderAreSkipped() {
        PantauHttpCore.start(PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never, ignoredHosts = setOf(server.hostName)))
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/skip")).build()).execute().close()
        Thread.sleep(50)
        assertTrue(PantauHttpCore.transactions.value.isEmpty())
    }
}
