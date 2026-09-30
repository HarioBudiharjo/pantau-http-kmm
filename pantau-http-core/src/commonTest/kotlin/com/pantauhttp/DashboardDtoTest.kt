package com.pantauhttp

import com.pantauhttp.internal.remote.DashboardTransactionDto
import com.pantauhttp.internal.remote.DeviceIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardDtoTest {

    private val device = DeviceIdentity(
        id = "abc", name = "Test Device", model = "iPhone17,3", systemName = "iOS", systemVersion = "18.0",
        appName = "Sample", appVersion = "1.0 (1)", bundleId = "com.pantauhttp.sample", isSimulator = true,
    )

    private fun completed() = sampleTransaction(
        url = "https://api.example.com/users?page=1",
        method = "POST",
        headers = mapOf("Content-Type" to "application/json"),
        body = """{"name":"pantau"}""".encodeToByteArray(),
        requestTimeMs = 1_753_776_000_000L,
    ).copy(
        state = TransactionState.Completed,
        statusCode = 201,
        responseHeaders = mapOf("Content-Type" to "application/json"),
        responseBody = """{"ok":true}""".encodeToByteArray(),
        responseBodySize = 11,
        responseTimeMs = 1_753_776_000_500L,
        redirects = listOf(Redirect("https://a.test/1", "https://a.test/2", 302)),
    )

    @Test
    fun wireFormatMatchesSwiftContract() {
        val tx = completed()
        val json = Json.parseToJsonElement(DashboardTransactionDto.from(tx, device).encode()).jsonObject
        val expectedKeys = setOf(
            "id", "state", "method", "url", "host", "path",
            "requestHeaders", "requestBodyBase64", "requestBodySize", "isRequestBodyTruncated", "requestDate",
            "statusCode", "responseHeaders", "responseBodyBase64", "responseBodySize", "isResponseBodyTruncated",
            "responseDate", "errorDescription", "redirects", "durationMs", "device",
        )
        assertTrue(json.keys.all { it in expectedKeys }, "unexpected keys: ${json.keys - expectedKeys}")
        assertFalse("errorDescription" in json.keys, "null keys are omitted like JSONEncoder")
        assertEquals(tx.id.lowercase(), json["id"]!!.jsonPrimitive.content)
        assertEquals("completed", json["state"]!!.jsonPrimitive.content)
        assertEquals("POST", json["method"]!!.jsonPrimitive.content)
        assertEquals("https://api.example.com/users?page=1", json["url"]!!.jsonPrimitive.content)
        assertEquals("api.example.com", json["host"]!!.jsonPrimitive.content)
        assertEquals("/users?page=1", json["path"]!!.jsonPrimitive.content)
        assertEquals(1_753_776_000_000L, json["requestDate"]!!.jsonPrimitive.long)
        assertEquals(1_753_776_000_500L, json["responseDate"]!!.jsonPrimitive.long)
        assertEquals(201, json["statusCode"]!!.jsonPrimitive.int)
        assertEquals(Base64.encode("""{"name":"pantau"}""".encodeToByteArray()), json["requestBodyBase64"]!!.jsonPrimitive.content)
        assertEquals(Base64.encode("""{"ok":true}""".encodeToByteArray()), json["responseBodyBase64"]!!.jsonPrimitive.content)
        assertEquals(500.0, json["durationMs"]!!.jsonPrimitive.double)

        val redirects = json["redirects"]!!.jsonArray
        assertEquals(1, redirects.size)
        assertEquals("https://a.test/1", redirects[0].jsonObject["fromURL"]!!.jsonPrimitive.content)
        assertEquals("https://a.test/2", redirects[0].jsonObject["toURL"]!!.jsonPrimitive.content)
        assertEquals(302, redirects[0].jsonObject["statusCode"]!!.jsonPrimitive.int)

        val dev = json["device"]!!.jsonObject
        assertEquals(setOf("id", "name", "model", "systemName", "systemVersion", "appName", "appVersion", "bundleId", "isSimulator"), dev.keys)
        assertEquals("Test Device", dev["name"]!!.jsonPrimitive.content)
        assertTrue(dev["isSimulator"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun inProgressOmitsResponseFields() {
        val json = Json.parseToJsonElement(DashboardTransactionDto.from(sampleTransaction(), device).encode()).jsonObject
        assertEquals("inProgress", json["state"]!!.jsonPrimitive.content)
        assertFalse("statusCode" in json.keys)
        assertFalse("responseDate" in json.keys)
        assertFalse("durationMs" in json.keys)
    }
}
