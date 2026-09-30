package com.pantauhttp.android

import com.pantauhttp.PantauHttpCore
import com.pantauhttp.internal.android.TeeResponseBody
import com.pantauhttp.ktor.PANTAU_INTERNAL_HEADER
import com.pantauhttp.ktor.PANTAU_TRACE_HEADER
import com.pantauhttp.shouldCapture
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/**
 * OkHttp interceptor that records every request/response of the client it is
 * added to (Retrofit, Coil, plain OkHttp, or a Ktor client using the OkHttp
 * engine with a preconfigured client).
 *
 * Add it as an application interceptor to see the request as your code built
 * it and to capture connection failures:
 * ```kotlin
 * OkHttpClient.Builder().addInterceptor(PantauHttpInterceptor()).build()
 * ```
 * As a network interceptor each redirect hop becomes its own transaction instead.
 */
public class PantauHttpInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuration = PantauHttpCore.configuration
        if (!PantauHttpCore.isStarted ||
            original.header(PANTAU_INTERNAL_HEADER) != null ||
            !configuration.shouldCapture(original.url.host)
        ) {
            return chain.proceed(original)
        }
        if (original.header(PANTAU_TRACE_HEADER) != null) {
            // Already recorded by the Ktor plugin upstream; strip the marker and pass through.
            return chain.proceed(original.newBuilder().removeHeader(PANTAU_TRACE_HEADER).build())
        }

        val recorder = PantauHttpCore.recorder
        val limit = configuration.bodySizeLimit
        val snapshot = original.body.snapshot(limit)
        val requestHeaders = original.headers.toSingleValueMap().toMutableMap()
        original.body?.let { body ->
            body.contentType()?.let { requestHeaders.putIfAbsent("Content-Type", it.toString()) }
            snapshot?.size?.takeIf { it >= 0 }?.let { requestHeaders.putIfAbsent("Content-Length", it.toString()) }
        }
        val id = recorder.begin(
            method = original.method,
            url = original.url.toString(),
            headers = requestHeaders,
            body = snapshot?.bytes,
            bodySize = snapshot?.size ?: 0L,
            truncated = snapshot?.truncated ?: false,
        )

        val response = try {
            chain.proceed(original)
        } catch (e: IOException) {
            recorder.complete(id, e.message ?: e::class.java.simpleName)
            throw e
        } catch (e: RuntimeException) {
            recorder.complete(id, e.message ?: e::class.java.simpleName)
            throw e
        }

        // Redirects OkHttp followed internally are exposed through the priorResponse chain.
        generateSequence(response.priorResponse) { it.priorResponse }.toList().asReversed().forEach { prior ->
            if (prior.isRedirect) {
                val location = prior.header("Location")
                val target = location?.let { prior.request.url.resolve(it)?.toString() ?: it } ?: ""
                recorder.redirect(id, prior.request.url.toString(), target, prior.code)
            }
        }
        recorder.response(
            id,
            response.code,
            response.headers.toSingleValueMap(),
            finalRequestHeaders = response.request.headers.toSingleValueMap().let { final ->
                final.toMutableMap().also { m -> requestHeaders.forEach { (k, v) -> m.putIfAbsent(k, v) } }
            },
        )

        val body = response.body
        if (body == null || body.contentLength() == 0L || original.method.equals("HEAD", ignoreCase = true) ||
            response.code == 204 || response.code == 304
        ) {
            recorder.complete(id, null)
            return response
        }
        val gzipped = response.header("Content-Encoding").equals("gzip", ignoreCase = true)
        val tee = TeeResponseBody(body, limit, gzipped) { bytes, total, truncated ->
            recorder.setResponseBody(id, bytes, total, truncated)
            recorder.complete(id, null)
        }
        return response.newBuilder().body(tee).build()
    }

    private class BodySnapshot(val bytes: ByteArray?, val size: Long, val truncated: Boolean)

    private fun RequestBody?.snapshot(limit: Int): BodySnapshot? {
        this ?: return null
        if (isDuplex() || isOneShot()) return BodySnapshot(null, contentLength(), truncated = true)
        val buffer = Buffer()
        writeTo(buffer)
        val size = buffer.size
        val bytes = buffer.readByteArray(minOf(size, limit.toLong()))
        return BodySnapshot(bytes, size, truncated = size > limit)
    }

    private fun Headers.toSingleValueMap(): Map<String, String> =
        names().associateWith { name -> values(name).joinToString(", ") }

    private fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V) {
        if (keys.none { (it as? String)?.equals(key as? String, ignoreCase = true) == true }) put(key, value)
    }
}
