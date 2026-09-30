package com.pantauhttp

import com.pantauhttp.internal.store.TransactionRecorder
import com.pantauhttp.internal.store.TransactionStore

internal fun sampleTransaction(
    url: String = "https://api.example.com/v1/users?page=2",
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: ByteArray? = null,
    requestTimeMs: Long = 1_753_776_000_000L,
    id: String = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0",
): HttpTransaction = HttpTransaction(
    id = id,
    method = method,
    url = url,
    requestHeaders = headers,
    requestBody = body,
    requestBodySize = body?.size?.toLong() ?: 0L,
    requestTimeMs = requestTimeMs,
)

internal fun recorderFor(
    store: TransactionStore,
    configuration: PantauHttpConfiguration = PantauHttpConfiguration(),
    clock: () -> Long = { 1_753_776_000_000L },
    onComplete: (HttpTransaction) -> Unit = {},
): TransactionRecorder = TransactionRecorder(store, configuration, clock, onComplete)
