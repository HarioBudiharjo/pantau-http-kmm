package com.pantauhttp.internal.store

import com.pantauhttp.HttpTransaction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thread-safe, in-memory ring buffer of recorded transactions (newest first).
 *
 * All mutations go through lock-free compare-and-set updates on a [StateFlow], so
 * they can be called synchronously from OkHttp threads, NSURLSession delegate
 * queues and Ktor coroutines alike.
 */
internal class TransactionStore(maxTransactions: Int = 200) {

    @Volatile
    var maxTransactions: Int = maxTransactions

    private val _transactions = MutableStateFlow<List<HttpTransaction>>(emptyList())
    private val _unseen = MutableStateFlow(0)
    private val _upserts = MutableSharedFlow<HttpTransaction>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Newest first. */
    val transactions: StateFlow<List<HttpTransaction>> = _transactions.asStateFlow()
    val unseenCount: StateFlow<Int> = _unseen.asStateFlow()

    /** Every insert/update, in order. Consumers must re-read by id for the freshest snapshot. */
    val upserts: SharedFlow<HttpTransaction> = _upserts.asSharedFlow()

    /** Debounced view of [transactions] for UI consumers. */
    val changes: Flow<List<HttpTransaction>> = _transactions.debounce(100.milliseconds)

    fun insert(transaction: HttpTransaction) {
        _transactions.update { current ->
            val max = maxTransactions
            val merged = ArrayList<HttpTransaction>(current.size + 1)
            merged.add(transaction)
            merged.addAll(current)
            if (merged.size > max) merged.subList(0, max).toList() else merged
        }
        _unseen.update { it + 1 }
        _upserts.tryEmit(transaction)
    }

    /**
     * Applies [transform] to the transaction with [id] and publishes the result.
     * Returns null when the transaction was evicted (updates are silently dropped).
     * [transform] must be pure: it may run more than once under contention.
     */
    fun upsert(id: String, transform: (HttpTransaction) -> HttpTransaction): HttpTransaction? {
        var result: HttpTransaction? = null
        _transactions.update { current ->
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) {
                result = null
                current
            } else {
                val updated = transform(current[index])
                result = updated
                val copy = current.toMutableList()
                copy[index] = updated
                copy
            }
        }
        return result?.also { _upserts.tryEmit(it) }
    }

    fun get(id: String): HttpTransaction? = _transactions.value.firstOrNull { it.id == id }

    fun clear() {
        _transactions.value = emptyList()
        _unseen.value = 0
    }

    fun markAllSeen() {
        _unseen.value = 0
    }

    val count: Int get() = _transactions.value.size
}
