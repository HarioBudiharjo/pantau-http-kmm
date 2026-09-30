package com.pantauhttp

import com.pantauhttp.internal.remote.DashboardPusher
import com.pantauhttp.internal.remote.DeviceIdentity
import com.pantauhttp.internal.store.TransactionStore
import com.pantauhttp.ktor.PANTAU_INTERNAL_HEADER
import com.pantauhttp.ktor.PantauInternalKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashboardPusherTest {

    private val device = DeviceIdentity("dev-1", "Test", "Pixel", "Android", "15", "Sample", "1 (1)", "com.sample", true)

    private class Harness(scope: CoroutineScope) {
        val requests = ArrayList<Pair<HttpRequestData, ByteArray>>()
        val store = TransactionStore()
        val client = HttpClient(MockEngine { request ->
            requests += request to request.body.toByteArray()
            respond("", HttpStatusCode.NoContent)
        })
        val pusher: DashboardPusher

        init {
            pusher = DashboardPusher(
                endpoint = "http://dashboard.test:9435/",
                device = DeviceIdentity("dev-1", "Test", "Pixel", "Android", "15", "Sample", "1 (1)", "com.sample", true),
                client = client,
                scope = scope,
                lookup = store::get,
            )
            pusher.start(store.upserts)
        }

        suspend fun awaitRequests(count: Int) = withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (requests.size < count) delay(5) }
        }
    }

    @Test
    fun terminalStateIsPostedImmediatelyWithMarkers() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val h = Harness(scope)
        val tx = sampleTransaction().copy(state = TransactionState.Completed, statusCode = 201, responseTimeMs = 1_753_776_000_500L)
        h.store.insert(tx)
        h.awaitRequests(1)
        val (request, body) = h.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/ingest", request.url.encodedPath)
        assertEquals("dashboard.test", request.url.host)
        assertEquals("1", request.headers[PANTAU_INTERNAL_HEADER])
        assertTrue(request.attributes.contains(PantauInternalKey))
        val json = Json.parseToJsonElement(body.decodeToString()).jsonObject
        assertEquals("completed", json["state"]!!.jsonPrimitive.content)
        assertEquals("dev-1", json["device"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        h.pusher.stop()
        scope.cancel()
    }

    @Test
    fun inProgressUpdatesAreDebouncedAndSendFreshest() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val h = Harness(scope)
        val tx = sampleTransaction()
        h.store.insert(tx)
        repeat(9) { i -> h.store.upsert(tx.id) { it.copy(responseBodySize = (i + 1).toLong()) } }
        h.awaitRequests(1)
        withContext(Dispatchers.Default) { delay(400) }
        assertTrue(h.requests.size <= 2, "10 raw changes must not become 10 POSTs, got ${h.requests.size}")
        val json = Json.parseToJsonElement(h.requests.first().second.decodeToString()).jsonObject
        assertEquals("9", json["responseBodySize"]!!.jsonPrimitive.content, "debounced send uses the latest snapshot")
        h.pusher.stop()
        scope.cancel()
    }

    @Test
    fun unreachableDashboardIsSwallowed() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = TransactionStore()
        val client = HttpClient(MockEngine { throw IllegalStateException("connection refused") })
        val pusher = DashboardPusher("http://127.0.0.1:1", device, client, scope, lookup = store::get)
        pusher.start(store.upserts)
        store.insert(sampleTransaction().copy(state = TransactionState.Failed))
        withContext(Dispatchers.Default) { delay(200) }
        assertNotNull(store.get(sampleTransaction().id))
        pusher.stop()
        scope.cancel()
    }

    @Test
    fun coreLifecycleCreatesPusherOnlyWithDashboardUrl() {
        val base = PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never)
        PantauHttpCore.start(base)
        assertEquals(false, PantauHttpCore.isPushingToDashboard)
        PantauHttpCore.start(base.copy(dashboardUrl = "http://127.0.0.1:9435"))
        assertEquals(true, PantauHttpCore.isPushingToDashboard)
        assertTrue(PantauHttpCore.isStarted)
        PantauHttpCore.stop()
        assertEquals(false, PantauHttpCore.isPushingToDashboard)
        assertEquals(false, PantauHttpCore.isStarted)
    }
}
