package com.pantauhttp

import com.pantauhttp.format.BodyFormatter
import com.pantauhttp.format.ByteCount
import com.pantauhttp.internal.format.Iso8601
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormattingTest {

    @Test
    fun prettyPrintsJsonWithSortedKeys() {
        val output = BodyFormatter.prettyPrinted("""{"b":1,"a":{"c":true}}""".encodeToByteArray(), "application/json")
        assertTrue(output.contains("\n"))
        assertTrue(output.indexOf("\"a\"") < output.indexOf("\"b\""))
        assertEquals("{\n  \"a\": {\n    \"c\": true\n  },\n  \"b\": 1\n}", output)
    }

    @Test
    fun detectsJsonWithoutContentType() {
        assertTrue(BodyFormatter.prettyPrinted("  [1,2,3]".encodeToByteArray(), null).contains("\n"))
    }

    @Test
    fun fallsBackToPlainText() {
        assertEquals("hello world", BodyFormatter.prettyPrinted("hello world".encodeToByteArray(), "text/plain"))
    }

    @Test
    fun invalidJsonFallsBackToRawText() {
        assertEquals("{not json}", BodyFormatter.prettyPrinted("{not json}".encodeToByteArray(), "application/json"))
    }

    @Test
    fun binaryPlaceholder() {
        val output = BodyFormatter.prettyPrinted(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01), "application/octet-stream")
        assertTrue(output.startsWith("<binary body"), output)
    }

    @Test
    fun emptyBody() {
        assertEquals("", BodyFormatter.prettyPrinted(null, null))
        assertEquals("", BodyFormatter.prettyPrinted(ByteArray(0), null))
    }

    @Test
    fun contentTypeHelpers() {
        assertTrue(BodyFormatter.isJson("application/json; charset=utf-8"))
        assertTrue(BodyFormatter.isJson("application/vnd.api+json"))
        assertFalse(BodyFormatter.isJson("text/html"))
        assertTrue(BodyFormatter.isImage("image/png"))
        assertFalse(BodyFormatter.isImage("application/json"))
    }

    @Test
    fun byteCount() {
        assertEquals("Zero KB", ByteCount.binary(0))
        assertEquals("1 byte", ByteCount.binary(1))
        assertEquals("512 bytes", ByteCount.binary(512))
        assertEquals("1 KB", ByteCount.binary(1024))
        assertEquals("2 KB", ByteCount.binary(1536))
        assertEquals("1.5 MB", ByteCount.binary(1_572_864))
        assertEquals("1.00 GB", ByteCount.binary(1L shl 30))
    }

    @Test
    fun iso8601() {
        assertEquals("2025-07-29T08:00:00Z", Iso8601.format(1_753_776_000_000L))
        assertEquals("1970-01-01T00:00:00Z", Iso8601.format(0L))
        assertEquals("1969-12-31T23:59:59Z", Iso8601.format(-1L))
        assertEquals("2000-02-29T12:34:56Z", Iso8601.format(951_827_696_000L))
    }
}
