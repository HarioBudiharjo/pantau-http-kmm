package com.pantauhttp.internal.remote

import com.pantauhttp.HttpTransaction
import com.pantauhttp.TransactionState
import com.pantauhttp.internal.platform.logWarning
import com.pantauhttp.ktor.PANTAU_INTERNAL_HEADER
import com.pantauhttp.ktor.PantauInternalKey
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pushes captured transactions to a PantauHTTP dashboard server over the local
 * network. Fire-and-forget: unreachable-server failures are logged once and
 * dropped. No retry queue, no memory growth.
 */
internal class DashboardPusher(
    endpoint: String,
    private val device: DeviceIdentity,
    private val client: HttpClient,
    private val scope: CoroutineScope,
    /** Trailing debounce for in-progress updates; engines fire one per received chunk. */
    private val debounce: Duration = 250.milliseconds,
    /** Always re-read the freshest snapshot at send time. */
    private val lookup: (String) -> HttpTransaction?,
) {
    internal val ingestUrl: String = endpoint.trimEnd('/') + "/api/ingest"

    private val mutex = Mutex()
    private val pending = HashMap<String, Job>()
    private var collectJob: Job? = null
    private var didLogFailure = false

    fun start(upserts: SharedFlow<HttpTransaction>) {
        // UNDISPATCHED subscribes synchronously, so nothing emitted right after start() is lost.
        collectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { upserts.collect { onChange(it) } }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        scope.launch {
            mutex.withLock {
                pending.values.forEach { it.cancel() }
                pending.clear()
            }
            client.close()
        }
    }

    suspend fun onChange(tx: HttpTransaction) {
        mutex.withLock {
            pending.remove(tx.id)?.cancel()
            if (tx.state != TransactionState.InProgress) {
                scope.launch { send(tx.id) }
            } else {
                pending[tx.id] = scope.launch {
                    delay(debounce)
                    mutex.withLock { pending.remove(tx.id) }
                    send(tx.id)
                }
            }
        }
    }

    private suspend fun send(id: String) {
        val tx = lookup(id) ?: return
        val body = DashboardTransactionDto.from(tx, device).encode()
        try {
            client.post(ingestUrl) {
                attributes.put(PantauInternalKey, true)
                header(PANTAU_INTERNAL_HEADER, "1")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            didLogFailure = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!didLogFailure) {
                didLogFailure = true
                logWarning(
                    "PantauHTTP",
                    "Dashboard unreachable at $ingestUrl — transactions will be dropped until it responds again. (${e.message})",
                )
            }
        }
    }
}
