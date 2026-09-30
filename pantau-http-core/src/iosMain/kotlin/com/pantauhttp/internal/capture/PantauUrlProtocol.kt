package com.pantauhttp.internal.capture

import com.pantauhttp.PantauHttpCore
import com.pantauhttp.internal.platform.headerMap
import com.pantauhttp.internal.platform.toByteArray
import com.pantauhttp.internal.store.TransactionRecorder
import com.pantauhttp.ktor.PANTAU_INTERNAL_HEADER
import com.pantauhttp.ktor.PANTAU_TRACE_HEADER
import com.pantauhttp.shim.PantauInstallProtocolGate
import com.pantauhttp.shouldCapture
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachedURLResponse
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSInputStream
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLCacheStoragePolicy
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.NSURLProtocolClientProtocol
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPBodyStream
import platform.Foundation.HTTPMethod
import platform.Foundation.valueForHTTPHeaderField
import platform.Foundation.setValue


import kotlinx.cinterop.ExportObjCClass

/**
 * The capture engine: an NSURLProtocol that intercepts HTTP(S) requests,
 * records them, and forwards them through an internal session.
 * Class-method gating (+canInitWithRequest:) is attached to this class's metaclass
 * by the C shim at start-up, see `PantauInstallProtocolGate`.
 */
@ExportObjCClass("PantauHTTPProtocol")
internal class PantauUrlProtocol
@OverrideInit constructor(
    request: NSURLRequest,
    cachedResponse: NSCachedURLResponse?,
    client: NSURLProtocolClientProtocol?,
) : NSURLProtocol(request, cachedResponse, client) {

    // Companions of ObjC subclasses cannot hold fields; shared state lives at file level.
    companion object : NSURLProtocolMeta()

    private var task: NSURLSessionDataTask? = null
    private var recorder: TransactionRecorder? = null

    /** Null in pass-through mode (the Ktor plugin already recorded this request). */
    private var transactionId: String? = null

    override fun startLoading() {
        val mutable = request.mutableCopy() as NSMutableURLRequest
        NSURLProtocol.setProperty(true, forKey = PANTAU_MARKER_KEY, inRequest = mutable)

        val tracedByKtor = request.valueForHTTPHeaderField(PANTAU_TRACE_HEADER) != null
        if (tracedByKtor) {
            mutable.setValue(null, forHTTPHeaderField = PANTAU_TRACE_HEADER)
        } else {
            val recorder = PantauHttpCore.recorder
            this.recorder = recorder
            val limit = PantauHttpCore.configuration.bodySizeLimit
            val body = request.HTTPBody?.toByteArray() ?: request.HTTPBodyStream?.let { drain(it, limit) }
            transactionId = recorder.begin(
                method = request.HTTPMethod ?: "GET",
                url = request.URL?.absoluteString ?: "",
                headers = request.headerMap(),
                body = body,
                bodySize = body?.size?.toLong() ?: 0L,
                truncated = body != null && body.size > limit,
            )
        }

        val dataTask = pantauInternalSession.dataTaskWithRequest(mutable)
        task = dataTask
        internalSessionDelegate.register(dataTask, this)
        dataTask.resume()
    }

    override fun stopLoading() {
        task?.cancel()
        task = null
    }

    // MARK: events from the internal session delegate

    internal fun didReceive(response: NSURLResponse) {
        val id = transactionId
        if (id != null && response is NSHTTPURLResponse) {
            recorder?.response(id, response.statusCode.toInt(), response.headerMap())
        }
        client?.URLProtocol(this, didReceiveResponse = response, cacheStoragePolicy = NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed)
    }

    internal fun didReceive(data: NSData) {
        transactionId?.let { recorder?.appendBody(it, data.toByteArray()) }
        client?.URLProtocol(this, didLoadData = data)
    }

    internal fun didComplete(error: NSError?) {
        transactionId?.let { id -> recorder?.complete(id, error?.localizedDescription) }
        if (error != null) {
            client?.URLProtocol(this, didFailWithError = error)
        } else {
            client?.URLProtocolDidFinishLoading(this)
        }
    }

    /**
     * Redirect contract: record the hop, hand the un-marked redirected request
     * back to the client, and cancel our leg. The client re-issues the new
     * request, which a fresh protocol instance captures as its own transaction.
     */
    internal fun wasRedirected(newRequest: NSURLRequest, response: NSHTTPURLResponse) {
        val id = transactionId
        if (id != null) {
            val from = request.URL?.absoluteString ?: ""
            val to = newRequest.URL?.absoluteString ?: ""
            recorder?.redirect(id, from, to, response.statusCode.toInt())
            recorder?.response(id, response.statusCode.toInt(), response.headerMap())
            recorder?.complete(id, null)
        }
        val mutable = newRequest.mutableCopy() as NSMutableURLRequest
        NSURLProtocol.removePropertyForKey(PANTAU_MARKER_KEY, inRequest = mutable)
        client?.URLProtocol(this, wasRedirectedToRequest = mutable, redirectResponse = response)
        task?.cancel()
    }

    /** Drains a body stream copy for capture, up to [limit] + one chunk. */
    private fun drain(stream: NSInputStream, limit: Int): ByteArray? {
        stream.open()
        try {
            val chunk = ByteArray(16 * 1024)
            var out = ByteArray(0)
            while (stream.hasBytesAvailable && out.size <= limit) {
                val read = chunk.usePinned { pinned -> stream.read(pinned.addressOf(0).reinterpret(), chunk.size.toULong()) }
                if (read <= 0L) break
                out += chunk.copyOf(read.toInt())
            }
            return out.takeIf { it.isNotEmpty() }
        } finally {
            stream.close()
        }
    }
}

internal const val PANTAU_MARKER_KEY: String = "PantauHTTPHandled"

/** One internal session shared by all protocol instances; its configuration carries no custom protocols. */
internal val pantauInternalSession: NSURLSession by lazy {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
        setProtocolClasses(emptyList<Any?>())
    }
    NSURLSession.sessionWithConfiguration(configuration, delegate = internalSessionDelegate, delegateQueue = null)
}

/** Attaches +canInitWithRequest: to the Kotlin class through the C shim. Idempotent. */
internal fun installProtocolGate() {
    PantauInstallProtocolGate(PantauUrlProtocol) { request ->
        request != null &&
            PantauHttpCore.isStarted &&
            NSURLProtocol.propertyForKey(PANTAU_MARKER_KEY, inRequest = request) == null &&
            request.valueForHTTPHeaderField(PANTAU_INTERNAL_HEADER) == null &&
            request.URL?.scheme?.lowercase().let { it == "http" || it == "https" } &&
            PantauHttpCore.configuration.shouldCapture(request.URL?.host)
    }
}
